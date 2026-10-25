package cn.starmarket.config

import cn.starmarket.domain.Product
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Properties

/** Catalog rules define recycling/names/categories; listing itself covers every real item material. */
object OpenListingCatalog {
    fun maximum(unit: BigDecimal): Long = BigDecimal("1000000000").divide(unit, 0, RoundingMode.DOWN)
        .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact().also {
            require(it >= 1) { "config.yml: money-unit=$unit exceeds the supported economy amount" }
        }
    fun expand(plugin: JavaPlugin, configured: Map<String, Product>, unit: BigDecimal, category: String): Map<String, Product> {
        val names = Properties().also { table ->
            plugin.getResource("lang/items_zh_CN.properties")!!.reader(Charsets.UTF_8).use { table.load(it) }
        }
        require(names.getProperty("schema-version") == "1") { "lang/items_zh_CN.properties: unsupported schema-version" }
        val ceiling = maximum(unit)
        val result = configured.mapValues { (_, p) -> p.copy(floor = 1, ceiling = ceiling, allowMetadata = true) }.toMutableMap()
        val byMaterial = configured.values.associateBy { it.material }
        Material.entries.filter { !it.isLegacy && it.isItem && !it.isAir }.forEach { material ->
            val id = material.key.toString()
            val name = names.getProperty(material.translationKey()) ?: material.name.lowercase().replace('_', ' ')
            result[id] = byMaterial[material.name]?.copy(id = id, floor = 1, ceiling = ceiling, allowMetadata = true)
                ?: Product(id, material.name, 1, 1, ceiling, 0, 0.0, 0.0, 4096, name, category, true, false)
        }
        return result.toMap()
    }
}
