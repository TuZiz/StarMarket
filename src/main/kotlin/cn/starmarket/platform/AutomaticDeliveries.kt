package cn.starmarket.platform

import cn.starmarket.StarMarket
import cn.starmarket.domain.MarketRejected
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

/** Main-thread owner of bounded automatic delivery dispatch and retry cooldowns. */
class AutomaticDeliveries(private val app:StarMarket):Listener {
 private val active=hashSetOf<UUID>()
 private val retryAfter=hashMapOf<UUID,Long>()
 private var scanning=false
 private var stopped=false
 private var task:BukkitTask?=null
 fun start(){
  app.server.pluginManager.registerEvents(this,app)
  task=app.server.scheduler.runTaskTimer(app,Runnable{scan()},20,20)
 }
 @EventHandler fun join(event:PlayerJoinEvent){if(app.ready)wake(event.player.uniqueId)}
 fun scan(){
  if(stopped||!app.ready||scanning)return
  scanning=true;val now=System.currentTimeMillis();retryAfter.entries.removeIf{it.value<=now}
  val online=app.server.onlinePlayers.map{it.uniqueId.toString()}
  val excluded=(active+retryAfter.keys).map{it.toString()}
  app.db{it.warehouse.owners(online,excluded)}.whenComplete{owners,error->app.main{
   scanning=false;if(stopped)return@main
   if(error!=null)app.logger.log(Level.SEVERE,"Automatic delivery queue scan failed",app.transactions.root(error))
   else owners.forEach{wake(UUID.fromString(it))}
  }}
 }
 fun wake(owner:UUID){
  if(stopped||!app.ready||owner in active||(retryAfter[owner]?:0)>System.currentTimeMillis())return
  active.add(owner);val player=app.server.getPlayer(owner)
  app.db{it.warehouse.batch(owner.toString(),player?.isOnline==true)}.thenCompose{rows->
   rows.fold(CompletableFuture.completedFuture(Unit)){flow,row->flow.thenCompose{
    app.main{!stopped&&app.ready}.thenCompose{allowed->
     if(allowed)app.transactions.delivery.send(row,player)else CompletableFuture.completedFuture(Unit)
    }
   }}
  }.whenComplete{_,error->app.main{
   active.remove(owner);if(stopped||error==null)return@main
   val cause=app.transactions.root(error);retryAfter[owner]=System.currentTimeMillis()+60000
   if(cause is MarketRejected){
    if(cause.code!="offline"&&player?.isOnline==true)app.message(player,"delivery-pending")
   }else{
    app.logger.log(Level.SEVERE,"Automatic delivery owner=$owner failed; uncertain receipts require reconciliation",cause)
    if(player?.isOnline==true)app.message(player,"transaction-uncertain")
   }
  }}
 }
 fun stop(){stopped=true;task?.cancel();task=null;org.bukkit.event.HandlerList.unregisterAll(this);active.clear();retryAfter.clear()}
}
