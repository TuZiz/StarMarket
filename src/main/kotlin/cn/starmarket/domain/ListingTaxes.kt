package cn.starmarket.domain
import java.math.BigDecimal

/** Durable up-front charge. No automatic retry of an external withdrawal. */
class ListingTaxes(private val ex:Exchange) {
 private val db get()=ex.db
 data class Tax(val id:String,val owner:String,val gross:Long,val rate:BigDecimal,val amount:Long,val state:String)
 fun get(id:String)=db.query("SELECT * FROM listing_taxes WHERE id=?",id){Tax(it.getString("id"),it.getString("owner"),it.getLong("gross"),BigDecimal(it.getString("rate")),it.getLong("amount"),it.getString("state"))}.firstOrNull()
 fun prepareWithin(id:String,owner:String,gross:Long,rate:BigDecimal){db.exec("INSERT INTO listing_taxes VALUES(?,?,?,?,?,'PREPARED')",id,owner,gross,rate.toPlainString(),Pricing.listingTax(gross,rate))}
 private fun op(id:String,state:String)=db.query("SELECT owner,group_key,quantity FROM operations WHERE id=? AND kind='DEPOSIT' AND state=?",id,state){Triple(it.getString(1),it.getString(2),it.getLong(3))}.singleOrNull()?:throw MarketRejected("session")
 fun removed(id:String)=db.transaction {
  op(id,"ITEMS");check(db.exec("UPDATE listing_taxes SET state='REMOVED' WHERE id=? AND state='PREPARED'",id)==1)
 }
 fun begin(id:String):Long=db.transaction {
  op(id,"ITEMS");val tax=get(id)?:error("Missing listing tax $id");check(tax.state=="REMOVED")
  check(db.exec("UPDATE listing_taxes SET state='EXTERNAL' WHERE id=? AND state='REMOVED'",id)==1)
  db.exec("UPDATE operations SET state='EXTERNAL' WHERE id=? AND state='ITEMS'",id);tax.amount
 }
 fun paid(id:String)=db.transaction {
  op(id,"EXTERNAL");val tax=get(id)?:error("Missing listing tax $id");check(tax.state=="EXTERNAL")
  db.exec("UPDATE listing_taxes SET state=? WHERE id=?",if(tax.amount==0L)"EXEMPT" else "PAID",id)
  ex.settlements.depositWithin(id,"EXTERNAL")
 }
 fun reject(id:String,itemsRemoved:Boolean)=db.transaction {
  val tax=get(id)?:error("Missing listing tax $id");check(tax.state in listOf("PREPARED","REMOVED","EXTERNAL"))
  val state=db.query("SELECT state FROM operations WHERE id=?",id){it.getString(1)}.single();check(state in listOf("ITEMS","EXTERNAL"))
  val row=op(id,state)
  if(itemsRemoved)ex.warehouse.add(row.first,row.second,row.third,0)
  db.exec("UPDATE listing_taxes SET state='REJECTED' WHERE id=?",id);db.exec("UPDATE operations SET state='REJECTED' WHERE id=?",id)
 }
 /** Caller owns transaction and has explicitly verified the external ledger/items. */
 fun reconcileWithin(id:String,decision:String){
  val row=op(id,"FROZEN");val tax=get(id)?:error("Missing listing tax $id")
  when(decision){
   "removed"->{require(tax.amount==0L);db.exec("UPDATE listing_taxes SET state='EXEMPT' WHERE id=?",id);ex.settlements.depositWithin(id,"FROZEN")}
   "tax-paid"->{db.exec("UPDATE listing_taxes SET state='PAID' WHERE id=?",id);ex.settlements.depositWithin(id,"FROZEN")}
   "tax-unpaid"->{ex.warehouse.add(row.first,row.second,row.third,0);rejectFrozen(id)}
   "not-removed"->rejectFrozen(id)
   "tax-paid-not-removed"->{ex.warehouse.add(row.first,row.second,0,tax.amount);db.exec("UPDATE listing_taxes SET state='REFUND_DUE' WHERE id=?",id);db.exec("UPDATE operations SET state='REJECTED' WHERE id=?",id)}
   else->throw MarketRejected("arguments")
  }
 }
 private fun rejectFrozen(id:String){db.exec("UPDATE listing_taxes SET state='REJECTED' WHERE id=?",id);db.exec("UPDATE operations SET state='REJECTED' WHERE id=?",id)}
}
