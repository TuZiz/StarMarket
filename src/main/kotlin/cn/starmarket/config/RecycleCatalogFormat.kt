package cn.starmarket.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.math.BigDecimal

/** Text-preserving migration: custom aliases/unknown fields remain readable legacy nodes. */
object RecycleCatalogFormat {
    private val fields = setOf("name", "material", "category", "reference-price", "base", "min-price", "minimum",
        "max-price", "maximum", "recycle-price", "salvage", "sensitivity", "max-change", "target-stock",
        "allow-metadata", "recycle-enabled")

    fun simplify(source: String): String {
        val yaml = YamlConfiguration().also { it.loadFromString(source) }
        val lines = source.lines().toMutableList()
        val root = lines.indexOfFirst { it.matches(Regex("products: *(#.*)?")) }
        val limit = (root + 1 until lines.size).firstOrNull {
            lines[it].isNotBlank() && !lines[it].startsWith(" ") && !lines[it].startsWith("#")
        } ?: lines.size
        val starts = if (root < 0) emptyList() else (root + 1 until limit).filter {
            lines[it].matches(Regex("  [a-z0-9_-]+: *"))
        }
        for (start in starts.asReversed()) {
            val id = lines[start].trim().removeSuffix(":")
            val node = yaml.getConfigurationSection("products.$id") ?: continue
            if (node.getKeys(false).any { it !in fields }) continue
            if (!node.getString("material", id)!!.equals(id, true)) continue
            val raw = node.get("recycle-price") ?: node.get("salvage") ?: 0
            val price = if (node.getBoolean("recycle-enabled", true)) BigDecimal(raw.toString()).toPlainString() else "0"
            // Validation (amount/unit/material) belongs to Catalog before any disk mutation.
            val end = (start + 1 until lines.size).firstOrNull {
                lines[it].isNotBlank() && !lines[it].trimStart().startsWith("#") &&
                    lines[it].takeWhile { c -> c == ' ' }.length <= 2
            } ?: lines.size
            val comments = lines.subList(start + 1, end).filter { it.trimStart().startsWith("#") }
            val inline = lines.subList(start + 1, end).mapNotNull {
                Regex("^    [a-z-]+:.*? (#[^\\r\\n]*)$").find(it)?.groupValues?.get(1)
            }.map { "    $it" }
            lines.subList(start, end).clear()
            lines.addAll(start, listOf("  $id: $price") + comments + inline)
        }
        val text = lines.joinToString("\n")
        return if (yaml.contains("schema-version")) text.replace(Regex("(?m)^(schema-version: *)[12](?= *(?:#|$))")) {
            it.groupValues[1] + "2"
        } else text
    }

    fun save(file: File, before: String, after: String) {
        if (before == after) return
        try {
            val backup = File(file.parentFile, file.name + ".pre-simple.bak")
            if (!backup.exists()) file.copyTo(backup)
            ConfigFiles.atomic(file, after)
        } catch (e: Exception) {
            throw IllegalStateException("${file.path}: schema-version 1 -> 2 migration failed: ${e.message}", e)
        }
    }
}
