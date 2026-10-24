package cn.starmarket.config
import cn.starmarket.domain.Product
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.math.BigDecimal

object Catalog {
 fun load(plugin:JavaPlugin,unit:BigDecimal):Map<String,Product> {
  val defaults=org.bukkit.configuration.file.YamlConfiguration().also{yaml->
   plugin.getResource("products/resources.yml")!!.bufferedReader(Charsets.UTF_8).use{yaml.loadFromString(it.readText())}
  }
  val names=defaults.getConfigurationSection("products")!!.getKeys(false).associate{id->
   defaults.getString("products.$id.material")!! to defaults.getString("products.$id.name")!!
  }
  val folder=File(plugin.dataFolder,"products");val legacy=File(plugin.dataFolder,"products.yml")
  if(!folder.exists()){
   if(legacy.exists()){
    val old=ConfigFiles.load(legacy)
    val ids=old.getConfigurationSection("products")?.getKeys(false)?:error("products.yml: missing products")
    CatalogMigration.migrate(legacy,folder,ids)
    plugin.logger.info("Migrated products.yml into products/*.yml; original preserved and no longer loaded")
   }else {folder.mkdirs();plugin.saveResource("products/resources.yml",false)}
  }
  val result=linkedMapOf<String,Product>();val materials=hashSetOf<String>()
  folder.walkTopDown().filter{it.isFile&&it.extension.lowercase() in setOf("yml","yaml")}.sortedBy{it.path}.forEach{file->
   val yaml=ConfigFiles.load(file);val section=yaml.getConfigurationSection("products")?:error("${file.path}: missing products")
   section.getKeys(false).forEach{id->
    val k="products.$id"
    fun value(key:String,legacyKey:String?=null,default:String?=null):String = yaml.getString("$k.$key")?:legacyKey?.let{yaml.getString("$k.$it")}?:default?:error("${file.path}: missing $k.$key")
    fun money(key:String,legacyKey:String?=null,default:String?=null):Long=try{BigDecimal(value(key,legacyKey,default)).divide(unit).longValueExact()}catch(e:Exception){throw IllegalArgumentException("${file.path}: $k.$key=${yaml.get("$k.$key")} invalid price or currency unit",e)}
    require(id.matches(Regex("[a-z0-9_-]+"))){"${file.path}: invalid id=$id"}
    val material=value("material");require(Material.matchMaterial(material)?.isItem==true){"${file.path}: $k.material=$material invalid"}
    require(materials.add(material)){"${file.path}: duplicate material=$material; use one rule per material, metadata variants are grouped automatically"}
    val p=Product(id,material,money("reference-price","base"),money("min-price","minimum"),money("max-price","maximum"),money("recycle-price","salvage","0"),yaml.getDouble("$k.sensitivity",0.3),yaml.getDouble("$k.max-change",0.05),yaml.getLong("$k.target-stock",4096),value("name",default=names[material]?:id),value("category",default="资源"),yaml.getBoolean("$k.allow-metadata",false),yaml.getBoolean("$k.recycle-enabled",true))
    require(p.salvage>=0&&p.floor>0&&p.base in p.floor..p.ceiling&&p.target>0&&p.sensitivity in 0.0..1.0&&p.maxChange in 0.0..0.25){"${file.path}: $k invalid range"}
    require(!result.containsKey(id)){"${file.path}: duplicate product=$id"};result[id]=p
   }
  }
  require(result.isNotEmpty()){"products/: no product rules found"};return result
 }
}
