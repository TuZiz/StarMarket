package cn.starmarket.gui
import cn.starmarket.StarMarket
import cn.starmarket.domain.*
import cn.starmarket.platform.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.*
import org.bukkit.event.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerQuitEvent
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Session tokens guard every late asynchronous GUI result and chat callback. */
class MarketGui(val app:StarMarket):Listener {
 class Screen(val token:UUID,val menu:String,val layout:MenuLayout,val select:Boolean=false):InventoryHolder {
  lateinit var inv:Inventory;val actions=hashMapOf<Int,()->Unit>();override fun getInventory()=inv
 }
 val assets=MenuAssets(app)
 val sale=SaleFlow(this)
 private val sessions=hashMapOf<UUID,UUID>()
 private data class Input(val token:UUID,val consume:(String)->Unit)
 private val inputs=ConcurrentHashMap<UUID,Input>()
 fun token(p:Player)=sessions.getOrPut(p.uniqueId){UUID.randomUUID()}
 fun current(p:Player,token:UUID)=p.isOnline&&sessions[p.uniqueId]==token&&p.hasPermission("starmarket.use")
 fun request(p:Player):UUID {val id=UUID.randomUUID();sessions[p.uniqueId]=id;inputs.remove(p.uniqueId);return id}
 fun open(p:Player,name:String,values:Map<String,String> = emptyMap(),select:Boolean=false,action:(Char)->(()->Unit)?):Screen {
  val c=assets.configs.getValue(name);val h=Screen(request(p),name,assets.layouts.getValue(name),select)
  h.inv=Bukkit.createInventory(h,c.getStringList("shape").size*9,Text.render(assets.format(c.getString("title")!!,values)))
  c.getStringList("shape").joinToString("").forEachIndexed{i,ch->if(ch!=' '){h.inv.setItem(i,assets.icon(name,"elements.$ch",values));action(ch)?.let{h.actions[i]=it}}}
  p.openInventory(h.inv);return h
 }
 fun content(h:Screen,index:Int,item:org.bukkit.inventory.ItemStack,action:()->Unit){val slot=h.layout.contentSlots.getOrNull(index)?:return;h.inv.setItem(slot,item);h.actions[slot]=action}
 fun preview(h:Screen,item:org.bukkit.inventory.ItemStack){h.layout.slots('I').forEach{h.inv.setItem(it,item)}}
 fun home(p:Player,search:String="",category:String="",page:Int=0){val id=request(p)
  app.db{it.offers(p.uniqueId.toString(),search,category)}.whenComplete{rows,error->app.main{
   if(!current(p,id))return@main
   if(error!=null){app.transactions.failure(p,error);return@main}
   val size=assets.pageSize("browse");val selected=assets.layouts.getValue("browse").page(page,rows.size)
   val h=open(p,"browse",mapOf("page" to (selected+1).toString())){ch->when(ch){
    'S'->({sale.select(p)});'M'->({mine(p)});'H'->({history(p)});'F'->({prompt(p,"search-input"){home(p,it)}});'T'->({categories(p)});'P'->({home(p,search,category,selected-1)});'N'->({home(p,search,category,selected+1)});else->null}}
   if(rows.isEmpty())app.message(p,"market-empty")
   rows.drop(selected*size).take(size).forEachIndexed{i,offer->content(h,i,assets.icon("browse","row",mapOf("name" to app.transactions.codec.label(offer.item),"price" to app.settings.money(offer.minimum),"stock" to offer.stock.toString()),offer.item)){detail(p,offer.item)}}
  }}
 }
 fun detail(p:Player,item:ItemIdentity){val id=request(p)
  app.db{it.offers(p.uniqueId.toString()).firstOrNull{it.item.key==item.key}}.whenComplete{offer,error->app.main{
   if(!current(p,id))return@main
   if(error!=null){app.transactions.failure(p,error);return@main}
   if(offer==null){app.message(p,"market-empty");home(p);return@main}
   val h=open(p,"item",mapOf("name" to app.transactions.codec.label(item),"price" to app.settings.money(offer.minimum),"stock" to offer.stock.toString())){ch->when(ch){
    'A'->({app.transactions.quote(p,item,1)});'B'->({app.transactions.quote(p,item,16)});'C'->({app.transactions.quote(p,item,64)});'D'->({app.transactions.quote(p,item,offer.stock)});'Q'->({prompt(p,"quantity-input"){text->number(p,text){app.transactions.quote(p,item,it)}}});'R'->({home(p)});else->null}}
   preview(h,assets.icon("item","elements.I",mapOf("name" to app.transactions.codec.label(item),"price" to app.settings.money(offer.minimum),"stock" to offer.stock.toString()),item))
  }}
 }
 fun confirm(p:Player,q:Quote,item:ItemIdentity,page:Int=0){
  val average=app.settings.unit.multiply(java.math.BigDecimal(q.total)).divide(java.math.BigDecimal(q.quantity),6,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
  val layout=assets.layouts.getValue("purchase");val size=layout.contentSlots.size
  val paged=size>0&&layout.slots('P').isNotEmpty()&&layout.slots('N').isNotEmpty()
  val selected=if(paged)layout.page(page,q.tiers.size)else 0
  val values=mapOf("name" to app.transactions.codec.label(item),"quantity" to q.quantity.toString(),"average" to average,"total" to app.settings.money(q.total))
  val h=open(p,"purchase",values){ch->when(ch){'P'->({confirm(p,q,item,selected-1)});'N'->({confirm(p,q,item,selected+1)});'Y'->({app.transactions.buy(p);p.closeInventory()});'R'->({app.transactions.quotes.remove(p.uniqueId);detail(p,item)});else->null}}
  if(paged||q.tiers.size<=size){
   q.tiers.drop(selected*size).take(size).forEachIndexed{i,tier->content(h,i,assets.icon("purchase","row",mapOf("price" to app.settings.money(tier.unit),"quantity" to tier.quantity.toString()))){}}
  }else{
   // Compact confirmations keep every tier in the configured summary Lore.
   val summary=assets.icon("purchase","elements.I",values,item);val meta=summary.itemMeta
   meta.lore(meta.lore().orEmpty()+q.tiers.map{tier->Text.render(assets.format(assets.configs.getValue("purchase").getString("row.name")!!,mapOf("price" to app.settings.money(tier.unit),"quantity" to tier.quantity.toString())))})
   summary.itemMeta=meta;preview(h,summary)
  }
 }
 fun mine(p:Player,page:Int=0){val id=request(p);app.db{it.expire();it.orders(p.uniqueId.toString()).filter{it.remaining>0}.map{o->o to it.item(o.group)}}.whenComplete{rows,error->app.main{
  if(!current(p,id))return@main;if(error!=null){app.transactions.failure(p,error);return@main}
  val size=assets.pageSize("listings");val selected=assets.layouts.getValue("listings").page(page,rows.size)
  val h=open(p,"listings"){ch->when(ch){'R'->({home(p)});'P'->({mine(p,selected-1)});'N'->({mine(p,selected+1)});else->null}}
  rows.drop(selected*size).take(size).forEachIndexed{i,(o,item)->
   val values=mapOf("name" to (item?.let{app.transactions.codec.label(it)}?:o.product),"order" to o.id,"quantity" to o.remaining.toString(),
    "price" to app.settings.money(o.minimum),"sold" to o.sold.toString(),"income" to app.settings.money(o.income),
    "hours" to ((o.expires-System.currentTimeMillis()).coerceAtLeast(0)/3600000).toString())
   content(h,i,assets.icon("listings","row",values,item)){cancelListing(p,h,h.layout.contentSlots[i],o.id,selected)}
  }

 }}}
 private fun cancelListing(p:Player,h:Screen,slot:Int,order:String,page:Int){
  val action=h.actions.remove(slot)?:return
  app.transactions.cancel(p,order){error->
   if(current(p,h.token)){
    if(error==null||(error is MarketRejected&&error.code=="order")){
     h.inv.setItem(slot,null);mine(p,page)
    }else h.actions[slot]=action
   }
  }
 }
 fun categories(p:Player,page:Int=0){
  val names=app.settings.products.values.map{it.category}.distinct()
  val size=assets.pageSize("categories");val selected=assets.layouts.getValue("categories").page(page,names.size)
  val h=open(p,"categories"){ch->when(ch){'R'->({home(p)});'P'->({categories(p,selected-1)});'N'->({categories(p,selected+1)});else->null}}
  names.drop(selected*size).take(size).forEachIndexed{i,name->content(h,i,assets.icon("categories","row",mapOf("name" to name))){home(p,category=name)}}
 }
 fun history(p:Player){val id=request(p);app.db{it.db.query("SELECT kind,quantity,state FROM operations WHERE owner=? ORDER BY created DESC LIMIT 21",p.uniqueId.toString()){r->r.getString(1)+" / "+r.getLong(2)+" / "+r.getString(3)}}.whenComplete{rows,error->app.main{if(!current(p,id))return@main;if(error!=null){app.transactions.failure(p,error);return@main};val h=open(p,"records"){if(it=='R')({home(p)})else null};rows.forEachIndexed{i,row->content(h,i,assets.icon("records","row",mapOf("record" to row))) {}}}}}
 fun prompt(p:Player,key:String,consume:(String)->Unit){p.closeInventory();val id=request(p);inputs[p.uniqueId]=Input(id,consume);app.message(p,key)}
 fun number(p:Player,text:String,consume:(Long)->Unit){val n=text.toLongOrNull();if(n==null||n<=0)app.message(p,"reject-quantity")else consume(n)}
 @EventHandler(priority=EventPriority.LOWEST) fun chat(e:AsyncChatEvent){val input=inputs.remove(e.player.uniqueId)?:return;e.isCancelled=true;val text=PlainTextComponentSerializer.plainText().serialize(e.message()).trim();app.main{if(current(e.player,input.token)){if(text.equals("cancel",true)){home(e.player)}else try{input.consume(text)}catch(error:Throwable){app.transactions.failure(e.player,error)}}}}
 @EventHandler fun click(e:InventoryClickEvent){val h=e.view.topInventory.holder as? Screen?:return;e.isCancelled=true;val p=e.whoClicked as? Player?:return;if(!current(p,h.token)||app.transactions.isBusy(p))return
  if(e.clickedInventory==p.inventory&&h.select&&e.slot in 0..35){e.currentItem?.let{stack->val selected=stack.clone();val slot=e.slot;app.main{if(current(p,h.token))sale.choose(p,selected,slot)}};return}
  if(e.rawSlot in 0 until h.inv.size){val action=h.actions[e.rawSlot]?:return;app.main{if(current(p,h.token)&&!app.transactions.isBusy(p))try{action()}catch(error:Throwable){app.transactions.failure(p,error)}}}
 }
 @EventHandler fun drag(e:InventoryDragEvent){if(e.view.topInventory.holder is Screen)e.isCancelled=true}
 @EventHandler fun close(e:InventoryCloseEvent){val h=e.inventory.holder as? Screen?:return;if(sessions[e.player.uniqueId]==h.token){sessions.remove(e.player.uniqueId);app.transactions.quotes.remove(e.player.uniqueId)}}
 @EventHandler fun quit(e:PlayerQuitEvent){sessions.remove(e.player.uniqueId);inputs.remove(e.player.uniqueId);sale.clear(e.player.uniqueId);app.transactions.quotes.remove(e.player.uniqueId)}
 fun clear(){sessions.clear();inputs.clear();sale.clearAll()}
}
