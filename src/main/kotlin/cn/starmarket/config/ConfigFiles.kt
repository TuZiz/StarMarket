package cn.starmarket.config
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ConfigFiles {
 fun atomic(file:File,text:String){file.parentFile.mkdirs();val temp=File(file.parentFile,file.name+".tmp")
  temp.writeText(text,Charsets.UTF_8)
  Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
 }
 fun load(file:File,versions:Set<Int> = setOf(1)):YamlConfiguration=YamlConfiguration().also{
  try{it.load(file)}catch(e:Exception){throw IllegalArgumentException("${file.path}: invalid YAML: ${e.message}",e)}
  require(it.getInt("schema-version") in versions){"${file.path}: schema-version=${it.get("schema-version")} requires migration"}
 }
 fun defaults(plugin:JavaPlugin,path:String):YamlConfiguration {
  val file=File(plugin.dataFolder,path)
  if(!file.exists())plugin.saveResource(path,false)
  return load(file)
 }
 /** Migrate language entries, retaining all existing comments/unknown fields. */
 fun language(plugin:JavaPlugin):YamlConfiguration {
  val file=File(plugin.dataFolder,"lang/zh_CN.yml")
  if(!file.exists())plugin.saveResource("lang/zh_CN.yml",false)
  val defaults=plugin.getResource("lang/zh_CN.yml")!!.bufferedReader().use{it.readText()}
  DeliveryLanguageMigration.upgrade(file,defaults)
  MarketLanguageMigration.upgrade(file,defaults)
  return load(file,setOf(3))
 }
}
