package cn.starmarket.config
import java.io.File

object TradingMigration {
 fun upgrade(file:File){
  val yaml=ConfigFiles.load(file,setOf(1,2));if(yaml.getInt("schema-version")==2)return
  val backup=File(file.parentFile,"trading.yml.v1.bak");if(!backup.exists())file.copyTo(backup)
  ConfigFiles.atomic(file,transform(file.readText(),yaml.contains("listing-tax-rate")))
 }
 fun transform(text:String,hasTax:Boolean):String {
  var result=text.replace(Regex("(?m)^schema-version:.*$"),"schema-version: 2")
  if(!hasTax)result=result.trimEnd()+"\n# 上架时收取，撤单/到期不退；旧 fee-rate 字段已停用\nlisting-tax-rate: 0.03\n"
  return result
 }
}
