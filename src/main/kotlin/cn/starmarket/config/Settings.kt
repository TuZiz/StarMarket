package cn.starmarket.config
import cn.starmarket.domain.Product
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.math.BigDecimal

class Settings(val plugin:JavaPlugin) {
 val main=ConfigFiles.defaults(plugin,"config.yml")
 val lang=ConfigFiles.language(plugin)
 val unit=BigDecimal(main.getString("money-unit")!!).also{require(it>BigDecimal.ZERO&&it.scale()<=6){"config.yml money-unit invalid"}}
 val trading:YamlConfiguration
 val products:Map<String,Product>
 val configuredProducts:Map<String,Product>
 val fee:BigDecimal
 val listingTaxRate:BigDecimal
 val orderLimit:Int
 val stockLimit:Long
 val lifetime:Long
 init {
  val file=File(plugin.dataFolder,"trading.yml")
  if(!file.exists()){
   plugin.saveResource("trading.yml",false)
   // Honor old limits and fee without rewriting the user's original config/comments.
   var text=file.readText()
   listOf("order-limit","stock-limit").forEach{key->if(main.contains(key))text=text.replace(Regex("(?m)^$key:.*$"),"$key: "+main.get(key).toString())}
   ConfigFiles.atomic(file,text)
  }
  TradingMigration.upgrade(file)
  trading=ConfigFiles.load(file,setOf(2))
  fee=BigDecimal.ZERO // No transaction tax; all new listing tax is charged up front.
  listingTaxRate=BigDecimal(trading.getString("listing-tax-rate")!!)
  require(listingTaxRate>=BigDecimal.ZERO&&listingTaxRate<BigDecimal.ONE){"trading.yml listing-tax-rate invalid"}
  orderLimit=trading.getInt("order-limit");stockLimit=trading.getLong("stock-limit")
  val hours=trading.getLong("listing-hours");require(orderLimit in 1..1000&&stockLimit in 1..1000000&&hours in 1..8760){"trading.yml limits/listing-hours invalid"};lifetime=Math.multiplyExact(hours,3600000)
  configuredProducts=Catalog.load(plugin,unit)
  products=OpenListingCatalog.expand(plugin,configuredProducts,unit,net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().stripTags(lang.getString("category-other")!!))
 }
 fun money(n:Long)=unit.multiply(BigDecimal(n)).stripTrailingZeros().toPlainString()
 fun units(text:String):Long=try{BigDecimal(text).divide(unit).longValueExact().also{require(it>=0)}}catch(e:Exception){throw cn.starmarket.domain.MarketRejected("price")}
}
