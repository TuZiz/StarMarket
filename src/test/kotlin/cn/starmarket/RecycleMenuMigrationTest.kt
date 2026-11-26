package cn.starmarket

import cn.starmarket.config.RecycleMenuMigration
import cn.starmarket.platform.RecyclingSelection
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecycleMenuMigrationTest {
    @Test fun migratesCustomTextWithoutRemovingUnknownFields() {
        val defaults = javaClass.getResource("/gui/recycle.yml")!!.readText()
        val original = "# 保留注释\nschema-version: 1\ntitle: '我的回收'\nshape:\n  - '####I####'\n  - '#   S   #'\n  - '####R####'\nelements:\n  '#':\n    material: 'STONE'\n    name: ''\n  'I':\n    name: '整叠估值 {total}'\n  'S':\n    name: '自定义确认'\n  'R':\n    name: '自定义返回'\ncustom: '未知字段'\n"
        val next = RecycleMenuMigration.merge(original, defaults)
        val c = YamlConfiguration().also { it.loadFromString(next) }
        assertEquals(2, c.getInt("schema-version"))
        assertEquals(6, c.getStringList("shape").size)
        assertEquals("自定义确认", c.getString("elements.S.name"))
        assertEquals("未知字段", c.getString("custom"))
        assertTrue(next.contains("# 保留注释"))
        assertEquals("STONE", c.getString("elements.#.material"))
        for (symbol in "1234567E") assertTrue(c.isConfigurationSection("elements.$symbol"))
        assertNotNull(c.getString("bar-filled.name"))
        assertNotNull(c.getString("summary"))
        assertEquals(next, RecycleMenuMigration.merge(next, defaults))
    }

    @Test fun proportionsAreMonotonicAndIncludeAllWithoutRoundingToZero() {
        for (total in listOf(1L, 2L, 7L, 64L, 96L, 2304L)) {
            val steps = (1..7).map { RecyclingSelection.proportion(total, it) }
            assertTrue(steps.all { it in 1..total })
            assertEquals(total, steps.last())
            assertEquals(steps.sorted(), steps)
        }
        assertEquals(listOf(15, 30, 45, 60, 75, 90, 100), (1..7).map(RecyclingSelection::percent))
        assertEquals(15, RecyclingSelection.proportion(96, 1))
        assertEquals(308, RecyclingSelection.proportion(512, 4))
    }
}
