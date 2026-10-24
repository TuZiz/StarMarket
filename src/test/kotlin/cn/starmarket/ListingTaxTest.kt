package cn.starmarket
import cn.starmarket.domain.*
import cn.starmarket.storage.Database
import cn.starmarket.config.TradingMigration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.math.BigDecimal

class ListingTaxTest {
 @TempDir lateinit var dir:Path
 private val p=Product("iron","IRON_INGOT",1000,300,5000,200,0.3,0.05,4096)
 private val item=ItemIdentity("iron","iron","bytes","Iron")
 private fun ex()=Exchange(Database(dir.resolve("market.db").toFile()),mapOf(p.id to p),BigDecimal.ZERO,20,100000,604800000,BigDecimal("0.03"))
 private fun prepare(ex:Exchange)=ex.deposit("seller",p,64,1000,item)
 private fun paid(ex:Exchange,id:String){ex.taxes.removed(id);assertEquals(1920,ex.taxes.begin(id));ex.taxes.paid(id)}
 @Test fun chargeBeforeListingAndNoDoubleTaxOnSale(){val ex=ex();val id=prepare(ex)
  assertEquals(1920,ex.taxes.get(id)!!.amount);assertEquals(0,ex.stock(p))
  assertThrows(IllegalStateException::class.java){ex.deposited(id)}
  paid(ex,id);val q=ex.quote("buyer",p,64);assertEquals(64000,q.total);assertEquals(64000,q.fills.single().net)
  ex.bought(ex.reserve(q),q);assertEquals(64000,ex.warehouse.ready("seller").single().money)
  assertEquals("PAID",ex.taxes.get(id)!!.state);ex.db.close()
 }
 @Test fun cancellationNeverRefundsTax(){val ex=ex();val id=prepare(ex);paid(ex,id);ex.withdraw("seller",id)
  assertEquals(64,ex.warehouse.ready("seller").single().quantity);assertEquals(0,ex.warehouse.ready("seller").sumOf{it.money});assertEquals("PAID",ex.taxes.get(id)!!.state);ex.db.close()
 }
 @Test fun expiryNeverRefundsTax(){val ex=ex();val id=prepare(ex);paid(ex,id);ex.db.exec("UPDATE orders SET expires=0 WHERE id=?",id);ex.expire();ex.expire()
  assertEquals(64,ex.warehouse.ready("seller").single().quantity);assertEquals(0,ex.warehouse.ready("seller").sumOf{it.money});assertEquals("PAID",ex.taxes.get(id)!!.state);ex.db.close()
 }
 @Test fun definitePaymentRejectionReturnsItemsOnce(){val ex=ex();val id=prepare(ex);ex.taxes.removed(id);ex.taxes.begin(id);ex.taxes.reject(id,true)
  assertEquals(0,ex.stock(p));assertEquals(64,ex.warehouse.ready("seller").single().quantity);assertThrows(IllegalStateException::class.java){ex.taxes.reject(id,true)};ex.db.close()
 }
 @Test fun knownItemFailureCreatesNoRefundOrListing(){val ex=ex();val id=prepare(ex);ex.taxes.reject(id,false);assertEquals(0,ex.stock(p));assertTrue(ex.warehouse.ready("seller").isEmpty());ex.db.close()}
 @Test fun uncertainTaxIsFrozenAndNeverRetried(){var ex=ex();val id=prepare(ex);ex.taxes.removed(id);ex.taxes.begin(id);ex.db.close();ex=ex()
  assertEquals("FROZEN",ex.db.query("SELECT state FROM operations WHERE id=?",id){it.getString(1)}.single());assertEquals("EXTERNAL",ex.taxes.get(id)!!.state)
  assertThrows(IllegalStateException::class.java){ex.taxes.begin(id)};ex.reconcile(id,"tax-paid");assertEquals(64,ex.stock(p));assertThrows(IllegalStateException::class.java){ex.reconcile(id,"tax-paid")};ex.db.close()
 }
 @Test fun unpaidReconciliationReturnsItems(){var ex=ex();val id=prepare(ex);ex.taxes.removed(id);ex.taxes.begin(id);ex.db.close();ex=ex();ex.reconcile(id,"tax-unpaid");assertEquals(64,ex.warehouse.ready("seller").single().quantity);assertEquals(0,ex.stock(p));ex.db.close()}
 @Test fun confirmedRollbackOfInventoryWithPaidTaxCreatesRefundOnly(){var ex=ex();val id=prepare(ex);ex.taxes.removed(id);ex.taxes.begin(id);ex.db.close();ex=ex();ex.reconcile(id,"tax-paid-not-removed");assertEquals(1920,ex.warehouse.ready("seller").single().money);assertEquals(0,ex.stock(p));ex.db.close()}
 @Test fun systemRecyclingPaysFullAmountWithNoTaxRow(){val ex=ex();ex.register(item);val id=ex.prepare("seller","SALVAGE",p,64,12800,item.key);ex.db.exec("UPDATE operations SET state='EXTERNAL' WHERE id=?",id);ex.settlements.salvageComplete(id,true)
  assertNull(ex.taxes.get(id));assertEquals(12800,ex.db.query("SELECT amount FROM turnover WHERE owner='seller'"){it.getLong(1)}.single());ex.db.close()
 }
 @Test fun subUnitTaxRoundsUpAndZeroRateIsExempt(){assertEquals(1,Pricing.listingTax(1,BigDecimal("0.03")));assertEquals(0,Pricing.listingTax(1,BigDecimal.ZERO));assertEquals(1920,Pricing.listingTax(64000,BigDecimal("0.03")))
  val ex=ex();ex.listingTaxRate=BigDecimal.ZERO;val id=prepare(ex);ex.taxes.removed(id);assertEquals(0,ex.taxes.begin(id));ex.taxes.paid(id);assertEquals("EXEMPT",ex.taxes.get(id)!!.state);ex.db.close()
 }
 @Test fun tradingMigrationPreservesUnknownFieldsAndDoesNotReuseOldFee(){val old="schema-version: 1\n# 原注释\nfee-rate: 0.02\norder-limit: 20\ncustom-option: 42\n";val upgraded=TradingMigration.transform(old,false)
  assertTrue(upgraded.contains("schema-version: 2"));assertTrue(upgraded.contains("listing-tax-rate: 0.03"));assertTrue(upgraded.contains("custom-option: 42"));assertTrue(upgraded.contains("# 原注释"))
 }
}
