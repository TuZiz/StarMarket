package cn.starmarket.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Upgrades only the recycling layout and required controls; retains user text and unknown fields. */
object RecycleMenuMigration {
    /** Additive language entry; retain all user messages and comments in schema 3. */
    fun language(file: File, defaults: String) {
        val key = "reject-recycle-changed"
        if (ConfigFiles.load(file, setOf(3)).contains(key)) return
        try {
            val line = defaults.lineSequence().single { it.startsWith("$key:") }
            val old = file.readText()
            val newline = if ("\r\n" in old) "\r\n" else "\n"
            val next = old.trimEnd() + newline + line + newline
            YamlConfiguration().loadFromString(next)
            val backup = File(file.parentFile, "${file.name}.v3-recycle-ui.bak")
            if (!backup.exists()) file.copyTo(backup)
            ConfigFiles.atomic(file, next)
        } catch (error: Exception) {
            throw IllegalArgumentException("${file.path}: schema-version=3 additive $key migration failed: ${error.message}", error)
        }
    }

    fun load(plugin: JavaPlugin): YamlConfiguration {
        val file = File(plugin.dataFolder, "gui/recycle.yml")
        if (!file.exists()) plugin.saveResource("gui/recycle.yml", false)
        val current = ConfigFiles.load(file, setOf(1, 2))
        if (current.getInt("schema-version") == 2) return current
        try {
            val defaults = plugin.getResource("gui/recycle.yml")!!.bufferedReader().use { it.readText() }
            val next = merge(file.readText(), defaults)
            YamlConfiguration().loadFromString(next)
            file.copyTo(File(file.parentFile, "recycle.yml.v1-${System.currentTimeMillis()}.bak"))
            ConfigFiles.atomic(file, next)
            return ConfigFiles.load(file, setOf(2))
        } catch (error: Exception) {
            throw IllegalArgumentException("${file.path}: schema-version 1 -> 2 failed: ${error.message}", error)
        }
    }

    fun merge(original: String, defaults: String): String {
        val parsed = YamlConfiguration().also { it.loadFromString(original) }
        require(parsed.getInt("schema-version") in 1..2) { "unsupported recycling schema-version" }
        if (parsed.getInt("schema-version") == 2) return original
        val lines = original.replace("\r\n", "\n").split('\n').toMutableList()
        val source = defaults.replace("\r\n", "\n").split('\n')
        fun rootEnd(list: List<String>, start: Int) = (start + 1 until list.size)
            .firstOrNull { Regex("^[A-Za-z0-9_-]+:").containsMatchIn(list[it]) } ?: list.size
        val shape = lines.indexOfFirst { it.startsWith("shape:") }
        require(shape >= 0) { "shape missing" }
        val end = rootEnd(lines, shape)
        val oldComments = lines.subList(shape + 1, end).filter { it.trimStart().startsWith('#') }
        lines.subList(shape + 1, end).clear()
        lines.addAll(shape + 1, oldComments + listOf("  - '#########'", "  - '#I    E #'", "  - '#########'", "  - '#1234567#'", "  - '#########'", "  - 'RRRR SSSS'", ""))
        for (key in listOf("1", "2", "3", "4", "5", "6", "7", "E")) {
            if (parsed.isConfigurationSection("elements.$key")) continue
            val start = source.indexOfFirst { it == "  '$key':" }
            require(start >= 0) { "default elements.$key missing" }
            val stop = (start + 1 until source.size).firstOrNull {
                Regex("^  '[^']+':").containsMatchIn(source[it]) || Regex("^[A-Za-z0-9_-]+:").containsMatchIn(source[it])
            } ?: source.size
            val elements = lines.indexOfFirst { it.startsWith("elements:") }
            require(elements >= 0) { "elements missing" }
            lines.addAll(rootEnd(lines, elements), source.subList(start, stop))
        }
        for (key in listOf("bar-filled", "bar-empty", "bar-handle")) {
            if (parsed.isConfigurationSection(key)) continue
            val start = source.indexOfFirst { it == "$key:" }
            lines.addAll(source.subList(start, rootEnd(source, start)))
        }
        if (!parsed.contains("summary")) lines.add(source.first { it.startsWith("summary:") })
        val result = lines.joinToString("\n")
            .replaceFirst(Regex("(?m)^schema-version: *1"), "schema-version: 2")
            .replace("整叠估值", "所选数量收益").replace("该格整叠物品", "所选数量的物品")
            .replace("默认选中格子内的", "默认选中背包内同种物品的").replace("整叠数量", "全部数量")
        return if ("\r\n" in original) result.replace("\n", "\r\n") else result
    }
}
