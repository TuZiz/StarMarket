package cn.starmarket.gui

import cn.starmarket.domain.MarketRejected
import cn.starmarket.domain.Pricing
import org.bukkit.entity.Player
import java.math.BigDecimal

/** Main thread only: the item is selected from the main hand when the price is submitted. */
class ChatListingFlow(private val gui: MarketGui) {
    private val app get() = gui.app
    fun start(p: Player) {
        prompt(p, true)
    }
    private fun prompt(p: Player, first: Boolean) {
        if (app.transactions.isBusy(p)) { app.message(p, "busy"); return }
        val rate = app.settings.listingTaxRate.multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()
        gui.prompt(p, if(first) "listing-hand-input" else "listing-retry-input") { text -> submit(p, text) }
        if(first)app.message(p,"listing-hand-tax","tax-rate" to rate)
    }
    private fun submit(p: Player, text: String) {
        try {
            app.transactions.active(p)
            val price = app.settings.units(text)
            val slot = p.inventory.heldItemSlot
            val stack = p.inventory.itemInMainHand.clone()
            if (stack.type.isAir) { app.message(p, "listing-empty-hand"); prompt(p, false); return }
            val item = app.transactions.codec.identify(stack)
            val product = app.settings.products.getValue(item.product)
            if (price !in product.floor..product.ceiling) {
                app.message(p, "listing-price-range", "floor" to app.settings.money(product.floor), "ceiling" to app.settings.money(product.ceiling))
                prompt(p, false); return
            }
            val quantity = stack.amount.toLong()
            app.transactions.codec.checkSlot(p, item, quantity, slot)
            val gross = try { Math.multiplyExact(quantity, price) } catch (_: ArithmeticException) { throw MarketRejected("price") }
            val rate = app.settings.listingTaxRate
            val fee = Pricing.listingTax(gross, rate)
            app.message(p, "listing-hand-summary", "name" to app.transactions.codec.label(item),
                "quantity" to quantity.toString(), "price" to app.settings.money(price),
                "gross" to app.settings.money(gross), "fee" to app.settings.money(fee))
            app.transactions.listing.submit(p, item, quantity, price, false, slot, rate)
        } catch (error: MarketRejected) {
            app.transactions.failure(p, error)
            if (p.isOnline && p.hasPermission("starmarket.use")) prompt(p, false)
        }
    }
}
