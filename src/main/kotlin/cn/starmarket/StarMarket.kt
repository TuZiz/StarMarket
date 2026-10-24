package cn.starmarket
import cn.starmarket.config.Settings
import cn.starmarket.domain.*
import cn.starmarket.storage.Database
import cn.starmarket.platform.*
import cn.starmarket.gui.MarketGui
import cn.starmarket.api.MarketAccess
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import java.util.concurrent.*
import java.io.File
import java.util.logging.Level

class StarMarket:JavaPlugin(),MarketAccess {
 @Volatile lateinit var settings:Settings;private set
 lateinit var vault:VaultGateway;private set
 lateinit var transactions:Transactions;private set
 lateinit var gui:MarketGui;private set
 lateinit var deliveries:AutomaticDeliveries;private set
 private lateinit var admin:AdminCommands
 private val executor=Executors.newSingleThreadExecutor{r->Thread(r,"StarMarket-database")}
 private var exchange:Exchange?=null
 @Volatile var ready=false;private set
 private val scheduled=java.util.concurrent.ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
 override fun onEnable(){try{
  check(!runCatching{Class.forName("io.papermc.paper.threadedregions.RegionizedServer")}.isSuccess){"Folia not supported; Leaf/Purpur scheduler required"}
  settings=Settings(this);vault=VaultGateway(settings.unit);transactions=Transactions(this);gui=MarketGui(this);admin=AdminCommands(this)
  deliveries=AutomaticDeliveries(this);deliveries.start()
  server.pluginManager.registerEvents(gui,this)
  val legacy=settings.products.values.map{transactions.codec.legacy(it)}
  CompletableFuture.runAsync({
   dataFolder.mkdirs();val database=Database(File(dataFolder,"market.db"))
   val ex=Exchange(database,settings.products,settings.fee,settings.orderLimit,settings.stockLimit,settings.lifetime,settings.listingTaxRate);exchange=ex
   val missing=(ex.orders().filter{it.remaining>0}.map{it.product}+ex.db.query("SELECT product FROM operations WHERE state IN ('FROZEN','EXTERNAL','ITEMS')"){it.getString(1)}).toSet()-settings.products.keys
   require(missing.isEmpty()){"products/: active orders require missing product rules $missing"}
   legacy.forEach{if(ex.item(it.key)==null)ex.register(it)};ex.expire()
  },executor).whenComplete{_,error->main{
   if(error!=null){logger.log(Level.SEVERE,"StarMarket database initialization failed",error);server.pluginManager.disablePlugin(this)}
   else{ready=true;deliveries.scan();logger.info("StarMarket ${description.version}; ${Bukkit.getVersion()}; Java ${System.getProperty("java.version")}; Vault=${vault.available()}; database schema=3; automatic inventory/ground delivery enabled; no player warehouse")}
  }}
  server.scheduler.runTaskTimer(this,Runnable{if(ready)db{it.expire()}.exceptionally{logger.log(Level.SEVERE,"Expiry task failed",it);null}},1200,1200)
 }catch(e:Throwable){logger.log(Level.SEVERE,"Startup failed; configuration was not replaced",e);server.pluginManager.disablePlugin(this)}}
 fun <T> db(work:(Exchange)->T):CompletableFuture<T> {
  if(executor.isShutdown)return CompletableFuture.failedFuture(IllegalStateException("StarMarket database stopped"))
  return CompletableFuture.supplyAsync({work(exchange?:error("Database not initialized"))},executor)
 }
 fun <T> main(work:()->T):CompletableFuture<T> {
  if(!isEnabled)return CompletableFuture.failedFuture(IllegalStateException("StarMarket disabled"))
  val future=CompletableFuture<T>();scheduled.add(future)
  try{server.scheduler.runTask(this,Runnable{try{if(!isEnabled)throw IllegalStateException("StarMarket disabled");future.complete(work())}catch(e:Throwable){future.completeExceptionally(e)}finally{scheduled.remove(future)}})}catch(e:Throwable){scheduled.remove(future);future.completeExceptionally(e)}
  return future
 }
 fun message(sender:CommandSender,key:String,vararg values:Pair<String,String>){val text=settings.lang.getString(key)?:settings.lang.getString("transaction-uncertain")!!
  var result=text;values.forEach{(k,v)->result=result.replace("{$k}",net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(v))};sender.sendMessage(Text.render(result))
 }
 override fun openMarket(player:Player){if(!ready)message(player,"busy")else if(!player.hasPermission("starmarket.use"))message(player,"permission")else gui.home(player)}
 override fun onCommand(sender:CommandSender,command:Command,label:String,args:Array<out String>):Boolean {
  if(!ready){message(sender,"busy");return true}
  if(args.firstOrNull()=="admin"){admin.execute(sender,args.drop(1));return true}
  val p=sender as? Player?:return true
  if(!p.hasPermission("starmarket.use")){message(p,"permission");return true}
  try{when(args.firstOrNull()){
   null->openMarket(p)
   "sell","list"->gui.sale.select(p)
   "search"->gui.home(p,args.drop(1).joinToString(" "))
   "category"->gui.home(p,category=args.drop(1).joinToString(" "))
   "mine"->gui.mine(p)
   "history"->gui.history(p)
   "confirm"->transactions.buy(p)
   "cancel"->transactions.cancel(p,args.getOrNull(1)?:throw MarketRejected("arguments"))
   else->message(p,"market-help")
  }}catch(error:Throwable){transactions.failure(p,error)};return true
 }
 fun reload():CompletableFuture<*> = db{ex->
  val next=Settings(this);require(next.unit==settings.unit){"config.yml money-unit cannot change with existing database"}
  require(next.products.keys.containsAll(ex.products.keys)){"products/: removing rules requires restarting after clearing related orders"}
  ex.products.forEach{(id,p)->require(next.products.getValue(id).material==p.material){"products/$id material cannot change"}}
  ex.products=next.products;ex.fee=next.fee;ex.orderLimit=next.orderLimit;ex.stockLimit=next.stockLimit;ex.lifetimeMillis=next.lifetime;ex.listingTaxRate=next.listingTaxRate;next
 }.thenCompose{next->main{
  server.onlinePlayers.filter{it.openInventory.topInventory.holder is MarketGui.Screen}.forEach{it.closeInventory()}
  gui.clear();org.bukkit.event.HandlerList.unregisterAll(gui);transactions.clear();settings=next;gui=MarketGui(this);server.pluginManager.registerEvents(gui,this)
 }}
 override fun onDisable(){
  ready=false;server.onlinePlayers.filter{it.openInventory.topInventory.holder is MarketGui.Screen}.forEach{it.closeInventory()}
  if(::deliveries.isInitialized)deliveries.stop()
  if(::gui.isInitialized)gui.clear();if(::transactions.isInitialized)transactions.clear()
  server.scheduler.cancelTasks(this);scheduled.toList().forEach{it.completeExceptionally(IllegalStateException("StarMarket disabled"))};scheduled.clear()
  if(!executor.isShutdown){executor.execute{exchange?.db?.close()};executor.shutdown()}
 }
}
