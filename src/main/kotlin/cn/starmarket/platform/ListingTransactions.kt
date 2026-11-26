package cn.starmarket.platform
import cn.starmarket.domain.*
import org.bukkit.entity.Player
import java.math.BigDecimal

class ListingTransactions(private val tx:Transactions) {
 private val app get()=tx.app
 fun submit(p:Player,item:ItemIdentity,n:Long,price:Long,recycle:Boolean,slot:Int,shownRate:BigDecimal=app.settings.listingTaxRate,onListed:()->Unit = {}) = execute(p,item,n,price,recycle,slot,shownRate,null,onListed)
 fun recycle(p:Player,item:ItemIdentity,n:Long,selection:RecyclingSelection) = execute(p,item,n,0,true,-1,app.settings.listingTaxRate,selection,{})
 private fun execute(p:Player,item:ItemIdentity,n:Long,price:Long,recycle:Boolean,slot:Int,shownRate:BigDecimal,selection:RecyclingSelection?,onListed:()->Unit)=tx.run(p,if(recycle)"recycled" else "listed-taxed",onSuccess={if(!recycle)onListed()}){
  tx.active(p);val product=app.settings.products.getValue(item.product)
  if(selection==null)tx.codec.checkSlot(p,item,n,slot)else selection.check(p,n)
  if(recycle&&!tx.codec.recyclable(item))throw MarketRejected("recycle")
  if(!recycle&&shownRate!=app.settings.listingTaxRate)throw MarketRejected("changed")
  val amount=if(recycle)Math.multiplyExact(n,product.salvage)else Pricing.listingTax(Math.multiplyExact(n,price),shownRate)
  if(amount>0){try{app.vault.check(p.uniqueId,amount,!recycle)}catch(error:MarketRejected){
   if(!recycle&&error.code=="balance")throw MarketRejected("listing-balance");throw error
  }}else if(app.vault.available()==null)throw MarketRejected("economy")
  app.db{ex->
   if(recycle)ex.db.transaction{ex.register(item);ex.prepare(p.uniqueId.toString(),"SALVAGE",product,n,amount,item.key)}
   else {if(ex.listingTaxRate!=shownRate)throw MarketRejected("changed");ex.deposit(p.uniqueId.toString(),product,n,price,item)}
  }.thenCompose{id->
   var removed=false
   val effect=app.main{tx.active(p);if(selection==null)tx.codec.removeSlot(p,item,n,slot)else selection.remove(p,n);removed=true}.thenCompose{
    if(!recycle)app.db{it.taxes.removed(id);it.taxes.begin(id)}.thenCompose{tax->app.main{tx.active(p);tax==0L||app.vault.transfer(p.uniqueId,tax,true)}}.thenCompose{paid->app.db{
     if(paid)it.taxes.paid(id)else {it.taxes.reject(id,true);throw MarketRejected("listing-tax")}
    }}
    else app.db{it.db.exec("UPDATE operations SET state='EXTERNAL' WHERE id=? AND state='ITEMS'",id)}
     .thenCompose{app.main{amount==0L||app.vault.transfer(p.uniqueId,amount,false)}}.thenCompose{paid->app.db{it.settlements.salvageComplete(id,paid);if(!paid)throw MarketRejected("payment")}}
   }.thenCompose{app.main{tx.event(if(recycle)"SALVAGE" else "CONSIGN",p.uniqueId,item.product,n,if(recycle)amount else 0,id)}}
   tx.recover(effect){error->app.db{ex->
    val state=ex.db.query("SELECT state FROM operations WHERE id=?",id){it.getString(1)}.single()
    if(state in listOf("ITEMS","EXTERNAL")){
     if(!recycle&&error is MarketRejected)ex.taxes.reject(id,removed)
     else if(!removed&&error is MarketRejected)ex.db.exec("UPDATE operations SET state='REJECTED' WHERE id=? AND state='ITEMS'",id)
     else ex.freeze(id,error.toString())
    }
   }}
  }
 }
}
