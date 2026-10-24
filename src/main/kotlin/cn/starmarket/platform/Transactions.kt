package cn.starmarket.platform
import cn.starmarket.StarMarket
import cn.starmarket.domain.*
import cn.starmarket.api.MarketEvent
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.*

/** Main-thread operation gate. Database stages and platform effects never share a thread. */
class Transactions(val app:StarMarket) {
 private val busy=hashSetOf<UUID>()
 val quotes=hashMapOf<UUID,Quote>()
 val codec=ItemCodec{app.settings}
 val delivery=DeliveryTransactions(this)
 val listing=ListingTransactions(this)
 val purchase=PurchaseTransactions(this)
 fun isBusy(p:Player)=p.uniqueId in busy
 fun active(p:Player){if(!p.isOnline||!p.hasPermission("starmarket.use"))throw MarketRejected("offline")}
 fun root(error:Throwable):Throwable {var cause=error;while((cause is CompletionException||cause is ExecutionException)&&cause.cause!=null)cause=cause.cause!!;return cause}
 fun failure(p:Player,error:Throwable){val cause=root(error)
  when(cause){
   is InsufficientStock->app.message(p,"stock-shortage","requested" to cause.requested.toString(),"available" to cause.available.toString())
   is MarketRejected->app.message(p,"reject-"+cause.code)
   else->{app.logger.log(java.util.logging.Level.SEVERE,"Transaction player=${p.uniqueId}",cause);app.message(p,"transaction-uncertain")}
  }
 }
 fun run(p:Player,success:String?="success",work:()->CompletableFuture<*>){
  if(!busy.add(p.uniqueId)){app.message(p,"busy");return}
  try{active(p);work().whenComplete{_,e->app.main{busy.remove(p.uniqueId);app.deliveries.wake(p.uniqueId);if(p.isOnline){if(e!=null)failure(p,e)else if(success!=null)app.message(p,success)}}}}
  catch(e:Throwable){busy.remove(p.uniqueId);failure(p,e)}
 }
 fun <T> recover(future:CompletableFuture<T>,repair:(Throwable)->CompletableFuture<*>):CompletableFuture<T> = future.handle{value,error->
  if(error==null)CompletableFuture.completedFuture(value)else repair(root(error)).thenApply<T>{throw CompletionException(root(error))}
 }.thenCompose{it}
 fun event(action:String,owner:UUID,product:String,n:Long,money:Long,id:String){app.server.pluginManager.callEvent(MarketEvent(action,owner,product,n,money,id))}
 fun clear(){quotes.clear()}
 fun cancel(p:Player,id:String,onSettled:(Throwable?)->Unit = {})=run(p,"cancelled-auto"){
  app.db{it.withdraw(p.uniqueId.toString(),id)}.handle{_,error->app.main{
   val cause=error?.let{root(it)};onSettled(cause)
   if(cause!=null)throw CompletionException(cause)
  }}.thenCompose{it}
 }
 fun quote(p:Player,item:ItemIdentity,n:Long)=purchase.quote(p,item,n)
 fun buy(p:Player)=purchase.buy(p)
}
