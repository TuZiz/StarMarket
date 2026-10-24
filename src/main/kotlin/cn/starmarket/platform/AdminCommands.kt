package cn.starmarket.platform
import cn.starmarket.StarMarket
import cn.starmarket.domain.MarketRejected
import org.bukkit.command.CommandSender
import java.util.concurrent.CompletableFuture

class AdminCommands(private val app:StarMarket) {
 fun execute(sender:CommandSender,args:List<String>){
  if(!sender.hasPermission("starmarket.admin")){app.message(sender,"permission");return}
  val actor=sender.name
  val actual:CompletableFuture<*> = if(args.firstOrNull() in listOf("reconcile","claim-resolve"))app.db{ex->
   require(args.size>=4){"Expected id, decision and evidence note"}
   if(args[0]=="reconcile")ex.reconcile(args[1],args[2])else ex.db.transaction{require(args[2] in listOf("delivered","not-delivered"));check(ex.db.exec("UPDATE claims SET state=? WHERE id=? AND state='FROZEN'",if(args[2]=="delivered")"DONE" else "READY",args[1])==1)}
   ex.db.exec("INSERT INTO audit VALUES(?,?,?)",System.currentTimeMillis(),actor,args.joinToString(" "));"OK"
  }else if(args.firstOrNull()=="reload")app.reload()else app.db{ex->ex.db.transaction{
   val result=when(args.firstOrNull()){
    "status"->ex.products.values.joinToString("\n"){"${it.id}: reference=${app.settings.money(ex.state(it).price)}, stock=${ex.stock(it)}"}
    "orders"->ex.orders(args.getOrNull(1)?:throw MarketRejected("arguments")).joinToString("\n")
    "pause","resume"->{val p=ex.products.getValue(args[1]);ex.state(p);ex.db.exec("UPDATE markets SET paused=? WHERE id=?",if(args[0]=="pause")1 else 0,p.id);"OK"}
    "price","reset"->{val p=ex.products.getValue(args[1]);val value=if(args[0]=="reset")p.base else app.settings.units(args[2]);require(value in p.floor..p.ceiling);ex.save(p,ex.state(p).copy(price=value));"OK"}
    "exceptions"->ex.db.query("SELECT id,note FROM operations WHERE state='FROZEN' UNION ALL SELECT id,'CLAIM' FROM claims WHERE state='FROZEN'"){it.getString(1)+" "+it.getString(2)}.joinToString("\n")
    "taxes"->ex.db.query("SELECT * FROM listing_taxes WHERE (? IS NULL OR owner=?) ORDER BY rowid DESC LIMIT 50",args.getOrNull(1),args.getOrNull(1)){it.getString("id")+" owner="+it.getString("owner")+" tax="+app.settings.money(it.getLong("amount"))+" rate="+it.getString("rate")+" state="+it.getString("state")}.joinToString("\n")
    "turnover"->ex.db.query("SELECT amount FROM turnover WHERE owner=?",args[1]){it.getLong(1)}.firstOrNull()?.let{app.settings.money(it)}?:"0"
    else->throw MarketRejected("arguments")
   }
   ex.db.exec("INSERT INTO audit VALUES(?,?,?)",System.currentTimeMillis(),actor,args.joinToString(" "));result
  }}
  actual.whenComplete{value,error->app.main{
   app.logger.info("Admin actor=$actor action=${args.joinToString(" ")} result=${error?:"OK"}")
   if(error!=null){app.logger.log(java.util.logging.Level.WARNING,"Admin operation failed",error);app.message(sender,"admin-failed")}else app.message(sender,"row","value" to (value?.toString()?:"OK"))
  }}
 }
}
