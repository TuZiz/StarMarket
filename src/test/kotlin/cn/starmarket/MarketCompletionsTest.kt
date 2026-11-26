package cn.starmarket

import cn.starmarket.platform.MarketCompletions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MarketCompletionsTest {
    private fun suggest(vararg args: String, player: Boolean = true, use: Boolean = true, admin: Boolean = false) =
        MarketCompletions.suggest(args.toList(), player, use, admin,
            { listOf("建筑材料", "矿物材料", "其他物品", "RPG Equipment") }, { listOf("oak_log", "minecraft:dirt") })
    @Test fun rootCommandsMatchPrefixesWithoutLeakingAdmin() {
        assertEquals(listOf("search", "sell"), suggest("S"))
        assertTrue("recycle" in suggest(""))
        assertFalse("admin" in suggest(""))
        assertTrue("admin" in suggest("", admin = true))
    }
    @Test fun permissionsAndConsoleAreIndependent() {
        assertTrue(suggest("", use = false).isEmpty())
        assertEquals(listOf("admin", "help"), suggest("", use = false, admin = true))
        assertEquals(listOf("help"), suggest("", player = false))
        assertEquals(listOf("admin", "help"), suggest("", player = false, admin = true))
        assertTrue(suggest("admin", "", admin = false).isEmpty())
    }
    @Test fun completesOnlySupportedAdminParameters() {
        assertEquals(listOf("reconcile", "reload", "reset", "resume"), suggest("admin", "re", admin = true))
        assertEquals(listOf("minecraft:dirt"), suggest("admin", "price", "mine", admin = true))
        assertEquals(listOf("not-delivered"), suggest("admin", "claim-resolve", "id", "not", admin = true))
        assertTrue("tax-paid" in suggest("admin", "reconcile", "id", "tax", admin = true))
    }
    @Test fun categoryNamesAndMultiwordSuffixUseCurrentConfig() {
        assertEquals(listOf("建筑材料"), suggest("category", "建"))
        assertEquals(listOf("Equipment"), suggest("category", "RPG", "E"))
        assertTrue(suggest("category", "", use = false).isEmpty())
    }
    @Test fun missingUnknownAndDynamicIdsReturnExplicitEmptyLists() {
        assertTrue(suggest().isEmpty())
        assertTrue(suggest("not-a-command", "").isEmpty())
        assertTrue(suggest("cancel", "").isEmpty())
        assertTrue(suggest("search", "").isEmpty())
        assertTrue(suggest("admin", "orders", "", admin = true).isEmpty())
        assertTrue(suggest("admin", "price", "oak_log", "", admin = true).isEmpty())
    }
    @Test fun rootCompletionDoesNotResolveUnneededData() {
        assertEquals(listOf("sell"), MarketCompletions.suggest(listOf("sel"), true, true, false,
            { error("categories must be lazy") }, { error("products must be lazy") }))
    }
}
