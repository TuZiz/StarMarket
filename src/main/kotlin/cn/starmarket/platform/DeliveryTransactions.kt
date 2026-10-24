package cn.starmarket.platform

import cn.starmarket.domain.*
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/** Durable delivery receipt before any inventory, world or Vault effect. */
class DeliveryTransactions(private val tx:Transactions) {
 private val app get()=tx.app
 fun send(row:Claim,player:Player?):CompletableFuture<Unit> = app.db{it.item(row.group)}.thenCompose{item->
  app.main{
   if(row.quantity>0){
    if(player==null||!player.isOnline)throw MarketRejected("offline")
    require(player.uniqueId.toString()==row.owner){"Delivery owner mismatch ${row.id}"}
    requireNotNull(item){"Missing serialized item ${row.group} for delivery ${row.id}"}
    minOf(256L,tx.codec.decode(item).maxStackSize.toLong()*4)
   }else {app.vault.check(java.util.UUID.fromString(row.owner),row.money,false);Long.MAX_VALUE}
  }.thenCompose{maximum->app.db{it.warehouse.begin(row.owner,row.id,maximum)}}.thenCompose{reserved->
   var effectStarted=false
   val effect=app.main{
    if(reserved.quantity>0){
     if(player==null||!player.isOnline)throw MarketRejected("offline")
     effectStarted=true;tx.codec.give(player,requireNotNull(item),reserved.quantity)
    }else{
     val owner=java.util.UUID.fromString(row.owner);app.vault.check(owner,reserved.money,false)
     effectStarted=true
     if(!app.vault.transfer(owner,reserved.money,false)){effectStarted=false;throw MarketRejected("payment")}
     0L
    }
   }.thenCompose{dropped->app.db{it.warehouse.complete(row.id)}.thenApply{dropped}}
   tx.recover(effect){error->app.db{
    if(!effectStarted&&error is MarketRejected)it.warehouse.reject(row.id)else it.db.transaction{
     it.warehouse.freeze(row.id)
     it.db.exec("INSERT INTO audit VALUES(?,?,?)",System.currentTimeMillis(),"AUTO_DELIVERY","FROZEN receipt=${row.id} owner=${row.owner} cause=$error")
    }
   }}.thenCompose{dropped->app.main{
    if(player?.isOnline==true){
     if(reserved.money>0)app.message(player,"delivery-money","money" to app.settings.money(reserved.money))
     else app.message(player,if(dropped>0)"delivery-dropped" else "delivery-items","quantity" to reserved.quantity.toString(),"dropped" to dropped.toString())
    }
    Unit
   }}
  }
 }
}
