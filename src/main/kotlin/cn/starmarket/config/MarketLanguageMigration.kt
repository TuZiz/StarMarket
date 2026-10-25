package cn.starmarket.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files

/** Version 3 separates listing permissions from recycling rules and adds multiline help. */
object MarketLanguageMigration {
    private val replaced = setOf("listing-hand-input", "listing-empty-hand", "listing-price-range",
        "reject-price", "reject-unsupported", "reject-metadata", "reject-arguments", "market-help")
    fun upgrade(file: File, defaults: String) {
        val old = ConfigFiles.load(file, setOf(2, 3))
        if (old.getInt("schema-version") == 3) return
        try {
            val next = transform(file.readText(Charsets.UTF_8), defaults)
            YamlConfiguration().loadFromString(next)
            val backup = File(file.parentFile, file.name + ".v2-open-listing.bak")
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath())
            ConfigFiles.atomic(file, next)
        } catch (error: Exception) {
            throw IllegalStateException("${file.path}: schema-version=2 -> 3 migration failed: ${error.message}", error)
        }
    }
    fun transform(original: String, defaults: String): String {
        val version = Regex("(?m)^schema-version: *([0-9]+)").find(original)?.groupValues?.get(1)?.toIntOrNull()
        val keys = Regex("(?m)^([a-zA-Z][a-zA-Z0-9_-]*):").findAll(original).map { it.groupValues[1] }.toSet()
        if (version == 3) return original
        require(version == 2) { "lang/zh_CN.yml: expected schema-version=2 or 3, got $version" }
        val newline = if ("\r\n" in original) "\r\n" else "\n"
        var text = original.replace("\r\n", "\n")
        val normalizedDefaults = defaults.replace("\r\n", "\n")
        val entries = Regex("(?m)^([a-zA-Z][a-zA-Z0-9_-]*):").findAll(normalizedDefaults).toList()
        entries.forEachIndexed { index, entry ->
            val key = entry.groupValues[1]
            val block = normalizedDefaults.substring(entry.range.first, entries.getOrNull(index + 1)?.range?.first ?: normalizedDefaults.length).trimEnd()
            if (key !in keys) text = text.trimEnd() + "\n" + block + "\n"
            else if (key in replaced || key == "schema-version") {
                val pattern = Regex("(?m)^${Regex.escape(key)}:[^\n]*")
                text = text.replace(pattern) { block + DeliveryLanguageMigration.comment(it.value) }
            }
        }
        return text.replace("\n", newline)
    }
}
