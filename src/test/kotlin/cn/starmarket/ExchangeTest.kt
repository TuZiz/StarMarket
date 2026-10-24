package cn.starmarket
import cn.starmarket.domain.*
import cn.starmarket.storage.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors

class ExchangeTest {
 @TempDir lateinit var dir:Path
 private val p=Product("stone","COBBLESTONE",100,20,500,10,0.3,0.05,4096)
 private val normal=ItemIdentity("stone","stone","normal-bytes","Stone")
 private fun exchange()=Exchange(Database(dir.resolve("market.db").toFile()),mapOf(p.id to p),BigDecimal("0.02"),20,100000)
 private fun supply(ex:Exchange,n:Long=64,price:Long=100,owner:String="seller",item:ItemIdentity=normal):String {
  val id=ex.deposit(owner,p,n,price,item);ex.deposited(id);return id
 }
 @Test fun noListingsMeansNoSystemOffers(){val ex=exchange();ex.register(normal);assertTrue(ex.offers("buyer").isEmpty());assertThrows(InsufficientStock::class.java){ex.quote("buyer",p,1)};ex.db.close()}
 @Test fun selfPurchaseIsExcluded(){val ex=exchange();supply(ex);assertTrue(ex.offers("seller").isEmpty());assertThrows(InsufficientStock::class.java){ex.quote("seller",p,1)};assertEquals(0,ex.stock(p,"seller"));ex.db.close()}
 @Test fun pricePriorityThenTimePriority(){val ex=exchange();val high=supply(ex,10,150,"high");val a=supply(ex,10,100,"first");val b=supply(ex,10,100,"second")
  ex.db.exec("UPDATE orders SET created=1 WHERE id=?",a);ex.db.exec("UPDATE orders SET created=2 WHERE id=?",b)
  val q=ex.quote("buyer",p,25);assertEquals(listOf(a,b,high),q.fills.map{it.order});assertEquals(listOf(PriceTier(100,20),PriceTier(150,5)),q.tiers);assertEquals(2750,q.total);ex.db.close()}
 @Test fun referenceNeverChangesListingPrice(){val ex=exchange();supply(ex,64,150);ex.save(p,MarketState(21,0.0,0.0,1));assertEquals(150,ex.quote("buyer",p,1).total);assertEquals(64,ex.offers("buyer").single().stock);ex.db.close()}
 @Test fun metadataVariantsNeverMerge(){val ex=exchange();supply(ex,10,100);val sword=normal.copy(key="stone:enchanted",data="other-bytes",label="Enchanted");supply(ex,1,300,"rpg",sword)
  assertEquals(2,ex.offers("buyer").size);assertThrows(InsufficientStock::class.java){ex.quote("buyer",p,11)};assertEquals(300,ex.quote("buyer",p,1,sword.key).total);ex.db.close()}
 @Test fun dataCollisionIsRejected(){val ex=exchange();ex.register(normal);assertThrows(IllegalArgumentException::class.java){ex.register(normal.copy(data="fake"))};ex.db.close()}
 @Test fun shortageNeverReservesOrCreatesOperation(){val ex=exchange();supply(ex,16);val before=ex.db.query("SELECT count(*) FROM operations"){it.getInt(1)}.single()
  val e=assertThrows(InsufficientStock::class.java){ex.quote("buyer",p,64)};assertEquals(16,e.available);assertEquals(before,ex.db.query("SELECT count(*) FROM operations"){it.getInt(1)}.single());assertEquals(16,ex.stock(p));ex.db.close()}
 @Test fun competingQuotesAndDuplicateSubmission(){val ex=exchange();supply(ex);val a=ex.quote("a",p,64);val b=ex.quote("b",p,64);val id=ex.reserve(a)
  assertThrows(InsufficientStock::class.java){ex.reserve(b)};assertThrows(InsufficientStock::class.java){ex.reserve(a)};ex.bought(id,a);assertThrows(IllegalStateException::class.java){ex.bought(id,a)};assertEquals(64,ex.orders("seller").single().sold);ex.db.close()}
 @Test fun cheaperSupplyRequiresReconfirmation(){val ex=exchange();supply(ex,64,150);val q=ex.quote("buyer",p,16);supply(ex,16,100,"cheaper");assertEquals("changed",assertThrows(MarketRejected::class.java){ex.reserve(q)}.code);assertEquals(80,ex.stock(p));ex.db.close()}
 @Test fun feeChangesRequireReconfirmation(){val ex=exchange();supply(ex);val q=ex.quote("buyer",p,1);ex.fee=BigDecimal("0.1");assertThrows(MarketRejected::class.java){ex.reserve(q)};ex.db.close()}
 @Test fun expiredQuoteRejected(){val ex=exchange();supply(ex);val q=ex.quote("buyer",p,1).copy(expires=0);assertEquals("expired",assertThrows(MarketRejected::class.java){ex.reserve(q)}.code);ex.db.close()}
 @Test fun rejectionRestoresExactlyOnce(){val ex=exchange();supply(ex);val q=ex.quote("buyer",p,16);val id=ex.reserve(q);ex.rejected(id);assertThrows(IllegalStateException::class.java){ex.rejected(id)};assertEquals(64,ex.stock(p));assertTrue(ex.warehouse.ready("buyer").isEmpty());ex.db.close()}
 @Test fun cancelReturnsOriginalMetadata(){val ex=exchange();val variant=normal.copy(key="stone:meta",data="full-nbt");val id=supply(ex,64,item=variant);ex.withdraw("seller",id)
  assertEquals(variant,ex.item(ex.warehouse.ready("seller").single().group));assertEquals(64,ex.warehouse.ready("seller").single().quantity);assertThrows(MarketRejected::class.java){ex.withdraw("seller",id)};ex.db.close()}
 @Test fun expiryReturnsUnreservedStockOnce(){val ex=exchange();val id=supply(ex);ex.db.exec("UPDATE orders SET expires=0 WHERE id=?",id);ex.expire();ex.expire();assertEquals(0,ex.stock(p));assertEquals(64,ex.warehouse.ready("seller").single().quantity);ex.db.close()}
 @Test fun expiryDoesNotInterfereWithUnknownPayment(){val ex=exchange();val order=supply(ex);ex.reserve(ex.quote("buyer",p,16));ex.db.exec("UPDATE orders SET expires=0 WHERE id=?",order);ex.expire();assertTrue(ex.warehouse.ready("seller").isEmpty());assertThrows(MarketRejected::class.java){ex.withdraw("seller",order)};ex.db.close()}
 @Test fun restartFreezesPaymentAndManualReconcileIsIdempotent(){var ex=exchange();supply(ex);val id=ex.reserve(ex.quote("buyer",p,16));ex.db.close();ex=exchange()
  assertEquals("FROZEN",ex.db.query("SELECT state FROM operations WHERE id=?",id){it.getString(1)}.single());ex.reconcile(id,"paid");assertEquals(16,ex.warehouse.ready("buyer").single().quantity);assertEquals(1568,ex.warehouse.ready("seller").single().money);assertThrows(IllegalStateException::class.java){ex.reconcile(id,"paid")};ex.db.close()}
 @Test fun restartFreezesRemovedItems(){var ex=exchange();val id=ex.deposit("seller",p,64,100,normal);ex.db.close();ex=exchange();ex.reconcile(id,"removed");assertEquals(64,ex.stock(p));ex.db.close()}
 @Test fun warehouseSupportsPartialClaimsAndRestartFreeze(){var ex=exchange();ex.register(normal);ex.warehouse.add("owner",normal.key,1000,0);val row=ex.warehouse.ready("owner").single();val part=ex.warehouse.begin("owner",row.id,64);assertEquals(64,part.quantity);assertEquals(936,ex.warehouse.ready("owner").single().quantity);ex.db.close();ex=exchange()
  assertEquals("FROZEN",ex.db.query("SELECT state FROM claims WHERE id=?",row.id){it.getString(1)}.single());assertEquals(936,ex.warehouse.ready("owner").single().quantity);ex.db.close()}
 @Test fun claimCannotBeDoubleSubmitted(){val ex=exchange();ex.register(normal);ex.warehouse.add("owner",normal.key,64,0);val row=ex.warehouse.ready("owner").single();ex.warehouse.begin("owner",row.id,64);assertThrows(MarketRejected::class.java){ex.warehouse.begin("owner",row.id,64)};ex.warehouse.complete(row.id);assertThrows(IllegalStateException::class.java){ex.warehouse.complete(row.id)};ex.db.close()}
 @Test fun safeDeliveryFailureRetainsClaim(){val ex=exchange();ex.register(normal);ex.warehouse.add("owner",normal.key,64,0);val row=ex.warehouse.ready("owner").single();assertThrows(MarketRejected::class.java){ex.warehouse.begin("owner",row.id,0)};ex.warehouse.begin("owner",row.id,64);ex.warehouse.reject(row.id);assertEquals(64,ex.warehouse.ready("owner").single().quantity);ex.db.close()}
 @Test fun sellerFeeIsFrozenAtReservation(){val ex=exchange();supply(ex);val q=ex.quote("buyer",p,1);val id=ex.reserve(q);ex.fee=BigDecimal("0.5");ex.bought(id,q);assertEquals(98,ex.warehouse.ready("seller").single().money);assertEquals(100,ex.db.query("SELECT amount FROM turnover WHERE owner='buyer'"){it.getLong(1)}.single());ex.db.close()}
 @Test fun listingLimitsAndPriceBounds(){val ex=exchange();ex.orderLimit=1;supply(ex);assertEquals("capacity",assertThrows(MarketRejected::class.java){supply(ex)}.code);assertThrows(MarketRejected::class.java){ex.deposit("other",p,64,1,normal)};ex.db.close()}
 @Test fun serialExecutorPreventsConcurrentOversell(){
  val worker=Executors.newSingleThreadExecutor();val ex=worker.submit<Exchange>{exchange().also{supply(it)}}.get()
  val buyers=Executors.newFixedThreadPool(8)
  val attempts=(1..32).map{i->buyers.submit<Boolean>{worker.submit<Boolean>{runCatching{val q=ex.quote("buyer$i",p,4);ex.reserve(q);true}.getOrDefault(false)}.get()}}
  assertEquals(16,attempts.count{it.get()});worker.submit{assertEquals(0,ex.stock(p));ex.db.close()}.get();buyers.shutdown();worker.shutdown()
 }
}
