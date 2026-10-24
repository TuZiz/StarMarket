package cn.starmarket.config

import java.io.File
import java.nio.file.Files

/** Replace only obsolete player-facing warehouse hints; preserve comments and unknown fields. */
object DeliveryLanguageMigration {
 fun upgrade(file:File,defaults:String){
  val old=ConfigFiles.load(file,setOf(1,2));if(old.getInt("schema-version")==2)return
  try{
   val text=file.readText(Charsets.UTF_8)
   val backup=File(file.parentFile,file.name+".v1-auto-delivery.bak")
   if(!backup.exists())Files.copy(file.toPath(),backup.toPath())
   ConfigFiles.atomic(file,transform(text,defaults))
  }catch(error:Exception){throw IllegalStateException("${file.path}: language schema-version=1 -> 2 migration failed: ${error.message}",error)}
 }
 fun transform(original:String,defaults:String):String {
   val version=Regex("(?m)^schema-version:[ \\t]*([12])(?:[ \\t]+#[^\\r\\n]*)?\\r?$").find(original)?.groupValues?.get(1)
   if(version=="2")return original
   require(version=="1"){"language schema-version requires version 1 or 2"}
   var text=original
   val keys=setOf("success","listed","claimed","purchased","purchased-warehouse","market-help","reject-payment","reject-empty","reject-claim","reject-listing-tax")
   defaults.lines().filter{it.substringBefore(':') in keys}.forEach{line->
    val key=line.substringBefore(':')
    val value=Regex("(?m)^${Regex.escape(key)}:([^\\r\\n]*)").find(original)?.groupValues?.get(1).orEmpty()
    if(key=="market-help"||"仓库" in value||"领取" in value)text=text.replace(Regex("(?m)^${Regex.escape(key)}:[^\\r\\n]*")){line+comment(value)}
   }
   text=text.replace(Regex("(?m)^schema-version:[^\\r\\n]*")){"schema-version: 2"+comment(it.value)}
   return text
 }
 private fun comment(line:String):String {
  var quote:Char?=null;var i=0
  while(i<line.length){val ch=line[i]
   if(quote=='\''&&ch=='\''&&i+1<line.length&&line[i+1]=='\''){i+=2;continue}
   if(quote=='"'&&ch=='\\'){i+=2;continue}
   if(quote==null&&(ch=='\''||ch=='"'))quote=ch
   else if(ch==quote)quote=null
   else if(quote==null&&ch=='#'&&i>0&&line[i-1].isWhitespace())return " "+line.substring(i)
   i++
  }
  return ""
 }
}
