package cn.starmarket.gui
import cn.starmarket.StarMarket
import cn.starmarket.config.ConfigFiles
import cn.starmarket.domain.ItemIdentity
import cn.starmarket.platform.Text
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.minimessage.MiniMessage

class MenuAssets(private val app:StarMarket) {
 private val names=listOf("browse","item","select-item","stack-sale","purchase","listings","records","categories")
 val configs=names.associateWith{name->ConfigFiles.defaults(app,"gui/$name.yml").also{c->
  if(name in listOf("browse","listings")){
   c.set("shape",c.getStringList("shape").map{it.replace('C','#')});c.set("elements.C",null)
  }
 }}
 val layouts=configs.mapValues{(_,c)->MenuLayout(c.getStringList("shape"))}
 fun pageSize(menu:String)=layouts.getValue(menu).pageSize()
 init {configs.forEach{(name,c)->val shape=c.getStringList("shape");require(shape.size in 1..6&&shape.all{it.length==9}){"gui/$name.yml invalid shape"};shape.joinToString("").filter{it!=' '}.forEach{require(c.isConfigurationSection("elements.$it")){"gui/$name.yml missing $it"}}}}
 fun format(text:String,values:Map<String,String>):String {var result=text;values.forEach{(k,v)->result=result.replace("{$k}",MiniMessage.miniMessage().escapeTags(v))};return result}
 fun icon(menu:String,path:String,values:Map<String,String>,item:ItemIdentity?=null):ItemStack {
  val c=configs.getValue(menu)
  val stack=if(item==null)ItemStack(Material.valueOf(c.getString("$path.material","PAPER")!!))else app.transactions.codec.decode(item)
  val meta=stack.itemMeta
  val existing=meta.lore().orEmpty().map{Text.plain(it)}
  val name=c.getString("$path.name")
  if(name!=null)meta.displayName(Text.render(format(name,values)))else meta.displayName()?.let{meta.displayName(Text.plain(it))}
  meta.lore(existing+c.getStringList("$path.lore").map{Text.render(format(it,values))});stack.itemMeta=meta;return stack
 }
}
