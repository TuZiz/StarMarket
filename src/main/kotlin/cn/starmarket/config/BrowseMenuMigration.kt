package cn.starmarket.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Adds the recycling entry in a navigation filler slot, retaining custom layouts and comments. */
object BrowseMenuMigration {
    fun load(plugin: JavaPlugin): YamlConfiguration {
        val file = File(plugin.dataFolder, "gui/browse.yml")
        if (!file.exists()) plugin.saveResource("gui/browse.yml", false)
        val current = ConfigFiles.load(file, setOf(1, 2))
        if (current.getInt("schema-version") == 2) return current
        try {
            val defaults = plugin.getResource("gui/browse.yml")!!.bufferedReader().use { it.readText() }
            val next = merge(file.readText(), defaults)
            YamlConfiguration().loadFromString(next)
            file.copyTo(File(file.parentFile, file.name + ".v1-chat-sale-${System.currentTimeMillis()}.bak"), false)
            ConfigFiles.atomic(file, next)
            return ConfigFiles.load(file, setOf(2))
        } catch (error: Exception) {
            throw IllegalArgumentException("${file.path}: schema-version 1 -> 2 failed: ${error.message}", error)
        }
    }
    fun merge(original: String, defaults: String): String {
        val newline = if ("\r\n" in original) "\r\n" else "\n"
        val lines = original.replace("\r\n", "\n").split('\n').toMutableList()
        val version = lines.indexOfFirst { it.matches(Regex("schema-version: *[12] *(?:#.*)?")) }
        require(version >= 0) { "schema-version must be 1 or 2" }
        if (lines[version].matches(Regex("schema-version: *2 *(?:#.*)?"))) return original
        val shape = lines.indexOfFirst { it.matches(Regex("shape: *(?:#.*)?")) }
        require(shape >= 0) { "shape missing" }
        val end = rootEnd(lines, shape)
        val rowPattern = Regex("^(\\s*-\\s*)(['\"])([^'\"\\r\\n]{9})\\2(\\s*(?:#.*)?)$")
        val rows = (shape + 1 until end).filter { rowPattern.matches(lines[it]) }
        require(rows.isNotEmpty()) { "shape must contain quoted nine-character rows" }
        if (rows.none { 'B' in rowPattern.matchEntire(lines[it])!!.groupValues[3] }) {
            val index = rows.last()
            val match = rowPattern.matchEntire(lines[index])!!
            val row = match.groupValues[3]
            val slot = row.indices.filter { row[it] == '#' || row[it] == ' ' }.minByOrNull { kotlin.math.abs(it - 4) }
                ?: throw IllegalArgumentException("shape navigation row '$row' has no # or blank filler for recycling; add B explicitly")
            val updated = row.toCharArray().also { it[slot] = 'B' }.concatToString()
            lines[index] = match.groupValues[1] + match.groupValues[2] + updated + match.groupValues[2] + match.groupValues[4]
        }
        val elements = lines.indexOfFirst { it.matches(Regex("elements: *(?:#.*)?")) }
        require(elements >= 0) { "elements missing" }
        val hasB = (elements + 1 until rootEnd(lines, elements)).any { element(it, lines, "B") }
        if (!hasB) {
            val defaultLines = defaults.replace("\r\n", "\n").split('\n')
            val start = defaultLines.indices.firstOrNull { element(it, defaultLines, "B") } ?: error("default elements.B missing")
            val stop = (start + 1 until defaultLines.size).firstOrNull { isElement(defaultLines[it]) || isRoot(defaultLines[it]) } ?: defaultLines.size
            lines.addAll(rootEnd(lines, elements), defaultLines.subList(start, stop))
        }
        val sale = lines.indices.firstOrNull { element(it, lines, "S") }
        if (sale != null) {
            val stop = (sale + 1 until lines.size).firstOrNull { isElement(lines[it]) || isRoot(lines[it]) } ?: lines.size
            for (i in sale + 1 until stop) lines[i] = lines[i]
                .replace("点击后从背包选择物品", "点击后手持物品，在聊天输入单价")
                .replace("将背包中的物品上架至星辰交易所", "关闭菜单后，手持需要上架的整叠物品")
        }
        lines[version] = lines[version].replaceFirst(Regex("schema-version: *1"), "schema-version: 2")
        return lines.joinToString(newline)
    }
    private fun isRoot(line: String) = Regex("^[A-Za-z0-9_-]+:").containsMatchIn(line)
    private fun rootEnd(lines: List<String>, start: Int) = (start + 1 until lines.size).firstOrNull { isRoot(lines[it]) } ?: lines.size
    private fun element(index: Int, lines: List<String>, key: String) = Regex("^  (?:'$key'|\"$key\"|$key):.*$").matches(lines[index])
    private fun isElement(line: String) = Regex("^  (?:'[^']+'|\"[^\"]+\"|[A-Za-z0-9#]+):.*$").matches(line)
}
