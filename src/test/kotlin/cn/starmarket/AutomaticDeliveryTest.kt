package cn.starmarket

import cn.starmarket.config.DeliveryLanguageMigration
import cn.starmarket.domain.*
import cn.starmarket.storage.Database
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path

class AutomaticDeliveryTest {
 @TempDir lateinit var dir:Path
 private val product=Product("stone","COBBLESTONE",100,20,500,10,0.3,0.05,4096)
 private val item=ItemIdentity("stone","stone","serialized","Stone")
 private fun exchange()=Exchange(Database(dir.resolve("market.db").toFile()),mapOf(product.id to product),BigDecimal.ZERO,20,100000).also{it.register(item)}
 @Test fun offlineItemsWaitButOfflineMoneyIsPayable(){
  val ex=exchange();ex.warehouse.add("offline",item.key,64,0);ex.warehouse.add("seller",item.key,0,100)
  assertEquals(listOf("seller"),ex.warehouse.owners(emptyList(),emptyList()))
  assertTrue(ex.warehouse.batch("offline",false).isEmpty());assertEquals(100,ex.warehouse.batch("seller",false).single().money)
  assertEquals(64,ex.warehouse.batch("offline",true).single().quantity);ex.db.close()
 }
 @Test fun cooldownAndActiveOwnersCannotStarveOtherOwners(){
  val ex=exchange();repeat(40){ex.warehouse.add("owner$it",item.key,0,100)}
  assertEquals(32,ex.warehouse.owners(emptyList(),emptyList()).size)
  assertEquals((32..39).map{"owner$it"},ex.warehouse.owners(emptyList(),(0..31).map{"owner$it"}));ex.db.close()
 }
 @Test fun automaticBatchesAndItemEffectsAreBounded(){
  val ex=exchange();repeat(20){ex.warehouse.add("online",item.key,1024,0)}
  val batch=ex.warehouse.batch("online",true);assertEquals(8,batch.size)
  val part=ex.warehouse.begin("online",batch.first().id,256);assertEquals(256,part.quantity)
  assertEquals(20*1024-256,ex.warehouse.ready("online").sumOf{it.quantity});ex.warehouse.complete(part.id);ex.db.close()
 }
 @Test fun restartPreservesUnsentButNeverRetriesUncertainDelivery(){
  var ex=exchange();ex.warehouse.add("online",item.key,64,0,"ready");ex.warehouse.add("online",item.key,64,0,"external")
  ex.warehouse.begin("online","external",256);ex.db.close();ex=exchange()
  assertEquals(listOf("ready"),ex.warehouse.batch("online",true).map{it.id})
  assertEquals("FROZEN",ex.db.query("SELECT state FROM claims WHERE id='external'"){it.getString(1)}.single());ex.db.close()
 }
 @Test fun languageMigrationIsIdempotentAndPreservesUnknownFields(){
  val original="schema-version: 1 # 版本说明\r\n# 保留中文说明\r\nlisted: '<#88CCFF>成交后领取收益' # 收益说明\r\ncustom: '自定义字段'\r\nmarket-help: '/market warehouse'\r\n"
  val defaults="schema-version: 2\nlisted: '<#88CCFF>成交后自动结算'\nmarket-help: '<#88CCFF>/market'\n"
  val migrated=DeliveryLanguageMigration.transform(original,defaults)
  assertEquals(migrated,DeliveryLanguageMigration.transform(migrated,defaults));assertTrue(migrated.contains("# 保留中文说明"));assertTrue(migrated.contains("custom: '自定义字段'"))
  assertTrue(migrated.contains("成交后自动结算"));assertFalse(migrated.contains("/market warehouse"))
  assertTrue(migrated.contains("schema-version: 2 # 版本说明\r\n"));assertTrue(migrated.contains("# 收益说明\r\n"))
 }
}
