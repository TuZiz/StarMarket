package cn.starmarket.gui
import cn.starmarket.domain.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class SaleFlow(private val gui:MarketGui) {
 private val app get()=gui.app
 private data class Draft(val item:ItemIdentity,val slot:Int,val quantity:Long,var price:Long,var shownRate:java.math.BigDecimal=java.math.BigDecimal.ZERO)
 private val drafts=hashMapOf<UUID,Draft>()
 fun select(p:Player){drafts.remove(p.uniqueId);gui.open(p,"select-item",select=true){if(it=='R')({gui.home(p)})else null}}
 fun choose(p:Player,stack:ItemStack,slot:Int){try{val item=app.transactions.codec.identify(stack);val product=app.settings.products.getValue(item.product);drafts[p.uniqueId]=Draft(item,slot,stack.amount.toLong(),product.base);form(p)}catch(e:Throwable){app.transactions.failure(p,e)}}
 private fun form(p:Player){val draft=drafts[p.uniqueId]?:return;val product=app.settings.products.getValue(draft.item.product)
  val gross=Math.multiplyExact(draft.quantity,draft.price);val fee=Pricing.listingTax(gross,app.settings.listingTaxRate);draft.shownRate=app.settings.listingTaxRate
  val h=gui.open(p,"stack-sale",mapOf("name" to app.transactions.codec.label(draft.item),"quantity" to draft.quantity.toString(),"price" to app.settings.money(draft.price),"recycle" to app.settings.money(product.salvage),"recycle-total" to app.settings.money(Math.multiplyExact(draft.quantity,product.salvage)),"floor" to app.settings.money(product.floor),"ceiling" to app.settings.money(product.ceiling),"gross" to app.settings.money(gross),"fee" to app.settings.money(fee),"net" to app.settings.money(gross-fee),"hours" to (app.settings.lifetime/3600000).toString())){ch->when(ch){
   'P'->({gui.prompt(p,"price-input"){text->draft.price=app.settings.units(text);if(draft.price !in product.floor..product.ceiling)throw MarketRejected("price");form(p)}})
   'Y'->({submit(p,draft,false)});'S'->({submit(p,draft,true)});'R'->({select(p)});else->null}}
  gui.preview(h,gui.assets.icon("stack-sale","row",mapOf("name" to app.transactions.codec.label(draft.item)),draft.item).also{it.amount=draft.quantity.toInt()})
 }
 private fun submit(p:Player,draft:Draft,recycle:Boolean){if(drafts[p.uniqueId]!==draft)throw MarketRejected("session");drafts.remove(p.uniqueId);app.transactions.listing.submit(p,draft.item,draft.quantity,draft.price,recycle,draft.slot,draft.shownRate);p.closeInventory()}
 fun clear(id:UUID){drafts.remove(id)}
 fun clearAll(){drafts.clear()}
}
