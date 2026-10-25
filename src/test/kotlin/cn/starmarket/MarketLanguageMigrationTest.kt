package cn.starmarket

import cn.starmarket.config.MarketLanguageMigration
import cn.starmarket.config.OpenListingCatalog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MarketLanguageMigrationTest {
    private val defaults = javaClass.getResource("/lang/zh_CN.yml")!!.readText()
    @Test fun replacesObsoleteHintsPreservesCommentsAndAddsWholeHelpLists() {
        val old = "# 中文备注\nschema-version: 2 # 版本\nreject-unsupported: '旧提示' # 保留注释\npermission: '自定义权限提示'\ncustom:\n  nested: '未知字段'\n"
        val next = MarketLanguageMigration.transform(old, defaults)
        assertTrue(next.contains("schema-version: 3 # 版本"))
        assertTrue(next.contains("重新选择。' # 保留注释"))
        assertTrue(next.contains("permission: '自定义权限提示'"))
        assertTrue(next.contains("nested: '未知字段'"))
        assertTrue(next.contains("help-player:\n  - '"))
        assertEquals(next, MarketLanguageMigration.transform(next, defaults))
    }
    @Test fun preservesWindowsNewlinesAndExistingHelpCustomization() {
        val old = "schema-version: 2\r\nhelp-player:\r\n  - '我的帮助'\r\n"
        val next = MarketLanguageMigration.transform(old, defaults)
        assertTrue(next.contains("  - '我的帮助'"))
        assertFalse(next.replace("\r\n", "").contains('\n'))
    }
    @Test fun refusesUnknownVersion() {
        assertThrows(IllegalArgumentException::class.java) { MarketLanguageMigration.transform("schema-version: 99\n", defaults) }
    }
    @Test fun economySafetyCeilingUsesExactConfiguredUnits() {
        assertEquals(100000000000L, OpenListingCatalog.maximum(BigDecimal("0.01")))
        assertEquals(333333333L, OpenListingCatalog.maximum(BigDecimal("3")))
        assertThrows(IllegalArgumentException::class.java) { OpenListingCatalog.maximum(BigDecimal("1000000001")) }
    }
}
