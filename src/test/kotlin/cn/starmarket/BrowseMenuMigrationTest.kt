package cn.starmarket

import cn.starmarket.config.BrowseMenuMigration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BrowseMenuMigrationTest {
    private val defaults = javaClass.getResource("/gui/browse.yml")!!.readText()
    @Test fun preservesCustomLayoutCommentsAndUnknownFieldsAndIsIdempotent() {
        val original = "# 我的首页\nschema-version: 1 # 保留版本注释\ntitle: '<#88CCFF>我的市场'\nshape:\n  - 'T#M###F#H'\n  - '#       #'\n  - 'P#S#####N' # 导航\nelements:\n  S:\n    name: '<#77DD99>出售'\n    lore:\n      - '<#77DD99>点击后从背包选择物品'\n  '#':\n    material: 'STONE'\ncustom: '用户字段'\n"
        val next = BrowseMenuMigration.merge(original, defaults)
        assertTrue(next.contains("schema-version: 2 # 保留版本注释"))
        assertTrue(next.contains("'P#S#B###N' # 导航"))
        assertTrue(next.contains("material: 'STONE'"))
        assertTrue(next.contains("custom: '用户字段'"))
        assertTrue(next.contains("  'B':"))
        assertTrue(next.contains("点击后手持物品，在聊天输入单价"))
        assertEquals(next, BrowseMenuMigration.merge(next, defaults))
    }
    @Test fun existingRecyclingButtonAndWindowsLineEndingsStayUnchanged() {
        val original = "schema-version: 1\r\nshape:\r\n  - 'PSB######'\r\nelements:\r\n  B:\r\n    name: '<#FFCC66>我的回收按钮'\r\n"
        val next = BrowseMenuMigration.merge(original, defaults)
        assertTrue(next.contains("'PSB######'"))
        assertTrue(next.contains("我的回收按钮"))
        assertFalse(next.contains("  'B':"))
        assertFalse(next.replace("\r\n", "").contains('\n'))
        assertEquals(next, BrowseMenuMigration.merge(next, defaults))
    }
    @Test fun refusesToOverwriteAFullNavigationRow() {
        val original = "schema-version: 1\nshape:\n  - 'PSTUVWXYZ'\nelements:\n  S:\n    name: '出售'\n"
        assertThrows(IllegalArgumentException::class.java) { BrowseMenuMigration.merge(original, defaults) }
    }
    @Test fun rejectsUnknownVersionsWithoutReplacingThem() {
        assertThrows(IllegalArgumentException::class.java) { BrowseMenuMigration.merge("schema-version: 99\n", defaults) }
    }
    @Test fun blankNavigationCellsAreSafeForTheNewButton() {
        val next = BrowseMenuMigration.merge("schema-version: 1\nshape:\n  - 'P S     N'\nelements:\n  S:\n    name: '<#88CCFF>出售'\n", defaults)
        assertTrue(next.contains("'P S B   N'"))
    }
}
