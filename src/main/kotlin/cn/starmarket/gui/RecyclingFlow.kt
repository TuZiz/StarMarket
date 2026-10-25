package cn.starmarket.gui

import cn.starmarket.domain.ItemIdentity
import cn.starmarket.domain.MarketRejected
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Dedicated tax-free system recycling screen; never creates player-market stock. */
class RecyclingFlow(private val gui: MarketGui) {
    private val app get() = gui.app
    private data class Draft(val item: ItemIdentity, val slot: Int, val quantity: Long)
    private class Session(var draft: Draft? = null, var screen: MarketGui.Screen? = null)
    private val sessions = hashMapOf<UUID, Session>()
    fun open(p: Player) { val session = Session(); sessions[p.uniqueId] = session; form(p, session) }
    fun choose(p: Player, stack: ItemStack, slot: Int) {
        val session = sessions[p.uniqueId] ?: return
        session.draft = null
        try {
            val item = app.transactions.codec.identify(stack)
            if (!app.transactions.codec.recyclable(item)) throw MarketRejected("recycle")
            app.transactions.codec.checkSlot(p, item, stack.amount.toLong(), slot)
            session.draft = Draft(item, slot, stack.amount.toLong())
        } catch (error: Throwable) { app.transactions.failure(p, error) }
        form(p, session)
    }
    private fun form(p: Player, session: Session) {
        val draft = session.draft
        val values = draft?.let {
            val product = app.settings.products.getValue(it.item.product)
            mapOf("name" to app.transactions.codec.label(it.item), "quantity" to it.quantity.toString(),
                "price" to app.settings.money(product.salvage), "total" to app.settings.money(Math.multiplyExact(it.quantity, product.salvage)))
        }.orEmpty()
        fun action(symbol: Char): (() -> Unit)? = when (symbol) {
            'R' -> ({ clear(p.uniqueId); gui.home(p) })
            'S' -> if (draft == null) null else ({
                if (sessions[p.uniqueId] !== session || session.draft !== draft) throw MarketRejected("session")
                sessions.remove(p.uniqueId)
                app.transactions.listing.submit(p, draft.item, draft.quantity, 0, true, draft.slot)
                p.closeInventory()
            })
            else -> null
        }
        val previous = session.screen
        val h = if (previous != null && gui.current(p, previous.token)) previous
            else gui.open(p, MENU, values, select = true, action = ::action).also { session.screen = it }
        gui.refresh(h, values, visible = { it != 'S' || draft != null }, action = ::action)
        gui.preview(h, if (draft == null) gui.assets.icon(MENU, "empty", emptyMap())
            else gui.assets.icon(MENU, "elements.I", values, draft.item).also { it.amount = draft.quantity.toInt() })
    }
    fun closed(id: UUID, screen: MarketGui.Screen) { if (sessions[id]?.screen === screen) clear(id) }
    fun clear(id: UUID) { sessions.remove(id) }
    fun clearAll() { sessions.clear() }
    companion object { const val MENU = "recycle" }
}
