package cn.starmarket.config
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object CatalogMigration {
 /** Keep each original node/comment verbatim apart from documented key aliases. */
 fun split(source:String,ids:Set<String>):Map<String,String> {
  val lines=source.lines();val starts=lines.indices.filter{lines[it].matches(Regex("  [a-z0-9_-]+: *"))}
  if(starts.map{lines[it].trim().removeSuffix(":")}.toSet()!=ids)return mapOf("legacy.yml" to source)
  val comments=lines.take(starts.firstOrNull()?:0).filter{it.trimStart().startsWith("#")}.joinToString("\n")
  val aliases=mapOf("base" to "reference-price","minimum" to "min-price","maximum" to "max-price","salvage" to "recycle-price")
  return starts.mapIndexed{index,start->
   val id=lines[start].trim().removeSuffix(":")
   var block=lines.subList(start,starts.getOrElse(index+1){lines.size}).joinToString("\n")
   aliases.forEach{(old,new)->block=block.replace(Regex("(?m)^    $old:"),"    $new:")}
   "$id.yml" to ("schema-version: 1\n"+(if(comments.isEmpty())"" else "$comments\n")+"products:\n"+block.trimEnd()+"\n")
  }.toMap()
 }
 fun migrate(legacy:File,folder:File,ids:Set<String>) {
  val backup=File(legacy.parentFile,"products.yml.pre-catalog.bak")
  if(!backup.exists())legacy.copyTo(backup)
  val staging=File(folder.parentFile,"products-migration-${System.nanoTime()}");staging.mkdirs()
  split(legacy.readText(),ids).forEach{(name,content)->ConfigFiles.atomic(File(staging,name),content)}
  Files.move(staging.toPath(),folder.toPath(),StandardCopyOption.ATOMIC_MOVE)
 }
}
