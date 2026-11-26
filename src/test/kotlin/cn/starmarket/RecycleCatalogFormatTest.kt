package cn.starmarket

import cn.starmarket.config.RecycleCatalogFormat
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RecycleCatalogFormatTest {
    @TempDir lateinit var dir: Path

    @Test fun simpleFileNeedsNoSchemaAndIsIdempotent() {
        val source = "products:\n  cobblestone: 0.1\n  iron_ingot: 2\n"
        assertEquals(source, RecycleCatalogFormat.simplify(source))
    }

    @Test fun migrationDoesNotTouchOtherSectionsOrLoseVersionComment() {
        val source = "schema-version: 1 # 保留版本注释\nother:\n  stone:\n    note: 7\nproducts:\n  stone:\n    material: 'STONE'\n    salvage: 0.2 # 保留价格注释\ntrailing:\n  stone:\n    note: 8\n"
        val result = RecycleCatalogFormat.simplify(source)
        assertTrue(result.contains("schema-version: 2 # 保留版本注释"))
        assertTrue(result.contains("# 保留价格注释"))
        val yaml = YamlConfiguration().also { it.loadFromString(result) }
        assertEquals(7, yaml.getInt("other.stone.note"))
        assertEquals(8, yaml.getInt("trailing.stone.note"))
        assertEquals(0.2, yaml.getDouble("products.stone"))
    }

    @Test fun migrationPreservesPricesCommentsUnknownFieldsAndAliases() {
        val source = """
            schema-version: 1
            # 回收配置
            custom-root: 7
            products:
              cobblestone:
                name: '圆石'
                material: 'COBBLESTONE'
                # 自定义注释
                recycle-price: 0.1
              oak_log:
                material: 'OAK_LOG'
                salvage: 0.5
                recycle-enabled: false
              iron_ingot:
                material: 'IRON_INGOT'
                custom-field: 42
              stone:
                material: 'STONE'
                recycle-price: 1
              alias:
                material: 'DIAMOND'
                recycle-price: 2
        """.trimIndent()
        val result = RecycleCatalogFormat.simplify(source)
        val yaml = YamlConfiguration().also { it.loadFromString(result) }
        assertEquals(2, yaml.getInt("schema-version"))
        assertEquals(0.1, yaml.getDouble("products.cobblestone"))
        assertEquals(0, yaml.getInt("products.oak_log"))
        assertEquals(1, yaml.getInt("products.stone"))
        assertEquals(42, yaml.getInt("products.iron_ingot.custom-field"))
        assertEquals("DIAMOND", yaml.getString("products.alias.material"))
        assertEquals(7, yaml.getInt("custom-root"))
        assertTrue(result.contains("# 自定义注释"))
        assertEquals(result, RecycleCatalogFormat.simplify(result))
        val file = dir.resolve("resources.yml").toFile().also { it.writeText(source) }
        RecycleCatalogFormat.save(file, source, result)
        RecycleCatalogFormat.save(file, result, result)
        assertEquals(source, dir.resolve("resources.yml.pre-simple.bak").toFile().readText())
        assertEquals(result, file.readText())
    }
}
