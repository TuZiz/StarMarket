package cn.starmarket.platform

/** Pure suggestions; never read inventory, economy, player files or SQLite here. */
object MarketCompletions {
    private val playerCommands = listOf("help", "sell", "list", "recycle", "mine", "search", "category", "history", "confirm", "cancel")
    private val adminCommands = listOf("help", "reload", "status", "orders", "pause", "resume", "price", "reset", "exceptions", "taxes", "turnover", "reconcile", "claim-resolve")
    fun suggest(args: List<String>, player: Boolean, use: Boolean, admin: Boolean,
                categories: () -> Collection<String>, products: () -> Collection<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        val candidates = when {
            args.size == 1 -> (if (player && use) playerCommands else if (use || admin) listOf("help") else emptyList()) + if (admin) listOf("admin") else emptyList()
            args[0] == "admin" && admin -> when {
                args.size == 2 -> adminCommands
                args.size == 3 && args[1] in listOf("pause", "resume", "price", "reset") -> products()
                args.size == 4 && args[1] == "claim-resolve" -> listOf("delivered", "not-delivered")
                args.size == 4 && args[1] == "reconcile" -> listOf("paid", "not-paid", "removed", "not-removed", "removed-unpaid", "tax-paid", "tax-unpaid", "tax-paid-not-removed")
                else -> emptyList()
            }
            player && use && args[0] == "category" -> {
                // Command execution joins category words; complete only the remaining suffix.
                val prefix = args.drop(1).dropLast(1).joinToString(" ").let { if (it.isEmpty()) "" else "$it " }
                categories().filter { it.startsWith(prefix, ignoreCase = true) }.map { it.substring(prefix.length) }
            }
            else -> emptyList()
        }
        return candidates.distinct().filter { it.startsWith(args.last(), ignoreCase = true) }.sorted()
    }
}
