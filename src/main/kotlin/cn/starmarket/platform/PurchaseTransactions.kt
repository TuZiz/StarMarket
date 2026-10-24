package cn.starmarket.platform
import cn.starmarket.domain.*
import org.bukkit.entity.Player
import java.util.UUID

class PurchaseTransactions(private val tx:Transactions) {
 private val app get()=tx.app
 fun quote(p:Player,item:ItemIdentity,n:Long)=tx.run(p,null){
  if(app.vault.available()==null)throw MarketRejected("economy")
  val token=app.gui.token(p)
  app.db{it.quote(p.uniqueId.toString(),it.products.getValue(item.product),n,item.key)}.thenCompose{q->app.main{
   tx.active(p);if(!app.gui.current(p,token))throw MarketRejected("session")
   tx.quotes[p.uniqueId]=q;app.gui.confirm(p,q,item)
  }}
 }
 fun buy(p:Player)=tx.run(p,null){
  val q=tx.quotes.remove(p.uniqueId)?:throw MarketRejected("quote")
  app.vault.check(p.uniqueId,q.total,true)
  app.db{it.reserve(q)}.thenCompose{id->
   val effect=app.main{tx.active(p);app.vault.transfer(p.uniqueId,q.total,true)}.thenCompose{paid->
    app.db{if(paid)it.bought(id,q)else {it.rejected(id);throw MarketRejected("balance")}}
   }
   tx.recover(effect){error->app.db{ex->if(error is MarketRejected) {
    val state=ex.db.query("SELECT state FROM operations WHERE id=?",id){it.getString(1)}.single()
    if(state=="EXTERNAL")ex.rejected(id)
   }else ex.freeze(id,error.toString())}}
   .thenCompose{app.main{
    tx.event("BUY",p.uniqueId,q.product,q.quantity,q.total,id)
    q.fills.forEach{f->tx.event("SOLD",UUID.fromString(f.owner),q.product,f.quantity,f.net,id)}
    tx.event("TURNOVER",p.uniqueId,q.product,q.quantity,q.total,id)
    (q.fills.map{UUID.fromString(it.owner)}+p.uniqueId).distinct().forEach{app.deliveries.wake(it)}
    if(p.isOnline){app.message(p,"purchased-auto");app.gui.home(p)}
   }}
  }
 }
}
