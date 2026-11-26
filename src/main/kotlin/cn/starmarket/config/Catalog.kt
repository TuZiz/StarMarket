package cn.starmarket.config
import cn.starmarket.domain.Product
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.math.BigDecimal

object Catalog {
 fun load(plugin:JavaPlugin,unit:BigDecimal):Map<String,Product> {
  val names=java.util.Properties().also { table ->
   plugin.getResource("lang/items_zh_CN.properties")!!.reader(Charsets.UTF_8).use { table.load(it) }
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
   val original=file.readText(Charsets.UTF_8)
   val before=org.bukkit.configuration.file.YamlConfiguration().also{yaml->
    try{yaml.loadFromString(original)}catch(e:Exception){throw IllegalArgumentException("${file.path}: invalid YAML: ${e.message}",e)}
   }
   require(before.getInt("schema-version",2) in 1..2){"${file.path}: schema-version=${before.get("schema-version")} unsupported"}
   val simplified=try{RecycleCatalogFormat.simplify(original)}catch(e:Exception){throw IllegalArgumentException("${file.path}: products migration schema-version 1 -> 2 failed: ${e.message}",e)}
   val yaml=org.bukkit.configuration.file.YamlConfiguration().also{it.loadFromString(simplified)};val section=yaml.getConfigurationSection("products")?:error("${file.path}: missing products")
   section.getKeys(false).forEach{id->
    val k="products.$id"
    fun value(key:String,legacyKey:String?=null,default:String?=null):String = yaml.getString("$k.$key")?:legacyKey?.let{yaml.getString("$k.$it")}?:default?:error("${file.path}: missing $k.$key")
    fun money(key:String,legacyKey:String?=null,default:String?=null):Long=try{BigDecimal(value(key,legacyKey,default)).divide(unit).longValueExact()}catch(e:Exception){throw IllegalArgumentException("${file.path}: $k.$key=${yaml.get("$k.$key")} invalid price or currency unit",e)}
    require(id.matches(Regex("[a-z0-9_-]+"))){"${file.path}: invalid id=$id"}
    val simple=!yaml.isConfigurationSection(k)
    val material=if(simple)id.uppercase(java.util.Locale.ROOT) else value("material")
    val type=Material.matchMaterial(material)
    require(type?.isItem==true&&!type.isAir&&!type.isLegacy){"${file.path}: $k=$material invalid item material"}
    require(materials.add(type!!.name)){"${file.path}: duplicate material=$material; use one rule per material"}
    val name=names.getProperty(type.translationKey())?:id
    val p=if(simple){
     val raw=yaml.get(k)
     val amount=try{require(raw is Number);BigDecimal(raw.toString()).divide(unit).longValueExact()}catch(e:Exception){throw IllegalArgumentException("${file.path}: $k=$raw invalid recycle price or currency unit",e)}
     require(amount>=0){"${file.path}: $k=$raw must be non-negative"}
     val maximum=OpenListingCatalog.maximum(unit)
     require(amount<=maximum){"${file.path}: $k=$raw exceeds supported economy amount"}
     Product(id,type.name,maxOf(1,amount),1,maximum,amount,0.0,0.0,4096,name,"资源",false,amount>0)
    }else Product(id,type.name,money("reference-price","base"),money("min-price","minimum"),money("max-price","maximum"),money("recycle-price","salvage","0"),yaml.getDouble("$k.sensitivity",0.3),yaml.getDouble("$k.max-change",0.05),yaml.getLong("$k.target-stock",4096),name,value("category",default="资源"),yaml.getBoolean("$k.allow-metadata",false),yaml.getBoolean("$k.recycle-enabled",true))
    require(p.salvage>=0&&p.floor>0&&p.base in p.floor..p.ceiling&&p.target>0&&p.sensitivity in 0.0..1.0&&p.maxChange in 0.0..0.25){"${file.path}: $k invalid range"}
    require(!result.containsKey(id)){"${file.path}: duplicate product=$id"};result[id]=p
   }
   RecycleCatalogFormat.save(file,original,simplified)
   if(original!=simplified)plugin.logger.info("Migrated ${file.path} to compact recycling prices; backup=${file.name}.pre-simple.bak")
  }
  return result
 }
}
