package cn.starmarket.gui

import cn.starmarket.domain.ItemIdentity
import cn.starmarket.domain.MarketRejected
import cn.starmarket.platform.RecyclingSelection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Dedicated tax-free system recycling screen; never creates player-market stock. */
class RecyclingFlow(private val gui: MarketGui) {
    private val app get() = gui.app
    private data class Draft(
        val item: ItemIdentity,
        val selection: RecyclingSelection,
        val step: Int = RecyclingSelection.LAST_STEP
    ) {
        val quantity = RecyclingSelection.proportion(selection.available, step)
    }
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
            val selection = RecyclingSelection.capture(p, app.transactions.codec.decode(item))
            session.draft = Draft(item, selection)
        } catch (error: Throwable) { app.transactions.failure(p, error) }
        form(p, session)
    }
    private fun form(p: Player, session: Session) {
        val draft = session.draft
        val baseValues = draft?.let {
            val product = app.settings.products.getValue(it.item.product)
            mapOf("name" to app.transactions.codec.label(it.item), "quantity" to it.quantity.toString(),
                "available" to it.selection.available.toString(), "percent" to RecyclingSelection.percent(it.step).toString(),
                "price" to app.settings.money(product.salvage), "total" to app.settings.money(Math.multiplyExact(it.quantity, product.salvage)))
        }.orEmpty()
        val menu = gui.assets.configs.getValue(MENU)
        val values = baseValues + RecyclingSkin.values(baseValues,
            menu.getString("pixel-name-characters", "")!!, menu.getString("pixel-empty-name", "")!!,
            menu.getString("pixel-price-format", "")!!, menu.getString("pixel-price-characters", "")!!)
        fun action(symbol: Char): (() -> Unit)? = when (symbol) {
            'R' -> ({ clear(p.uniqueId); gui.home(p) })
            in '1'..'7' -> if (draft == null) null else ({
                if (sessions[p.uniqueId] === session && session.draft === draft) {
                    session.draft = draft.copy(step = symbol.digitToInt())
                    form(p, session)
                }
            })
            'S' -> if (draft == null) null else ({
                if (sessions[p.uniqueId] !== session || session.draft !== draft) throw MarketRejected("session")
                sessions.remove(p.uniqueId)
                app.transactions.listing.recycle(p, draft.item, draft.quantity, draft.selection)
                p.closeInventory()
            })
            else -> null
        }
        val previous = session.screen
        val h = if (previous != null && gui.current(p, previous.token)) previous
            else gui.open(p, MENU, values, select = true, action = ::action).also { session.screen = it }
        gui.refresh(h, values, visible = { it !in "S1234567E" || draft != null }, action = ::action)
        gui.preview(h, if (draft == null) gui.assets.icon(MENU, "empty", emptyMap())
            else gui.assets.icon(MENU, "elements.I", values, draft.item).also { it.amount = 1 })
        if (gui.assets.configs.getValue(MENU).getBoolean("pixel-title") && p.openInventory.topInventory === h.inv) {
            RecyclingSkin.refreshTitle(p, gui.assets.format(gui.assets.configs.getValue(MENU).getString("title")!!, values))
        }
        if (draft != null) {
            gui.assets.configs.getValue(MENU).getString("summary")?.let {
                p.sendActionBar(cn.starmarket.platform.Text.render(gui.assets.format(it, values)))
            }
            for (step in 1..RecyclingSelection.LAST_STEP) {
                val target = RecyclingSelection.proportion(draft.selection.available, step)
                val barValues = values + mapOf("target" to target.toString(), "target-percent" to RecyclingSelection.percent(step).toString())
                val style = when {
                    step == draft.step -> "bar-handle"
                    step < draft.step -> "bar-filled"
                    else -> "bar-empty"
                }
                h.layout.slots(('0'.code + step).toChar()).forEach { slot ->
                    h.inv.setItem(slot, gui.assets.icon(MENU, style, barValues))
                }
            }
        }
    }
    fun closed(id: UUID, screen: MarketGui.Screen) { if (sessions[id]?.screen === screen) clear(id) }
    fun clear(id: UUID) { sessions.remove(id) }
    fun clearAll() { sessions.clear() }
    companion object { const val MENU = "recycle" }
}
