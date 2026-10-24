package cn.starmarket.domain
import java.math.BigDecimal

/** All mutations share the same SQLite transaction as their durable receipts. */
class Settlements(private val ex:Exchange) {
 private val db get()=ex.db
 private fun operation(id:String,state:String)=db.query("SELECT * FROM operations WHERE id=? AND state=?",id,state){mapOf("owner" to it.getString("owner"),"kind" to it.getString("kind"),"product" to it.getString("product"),"group" to it.getString("group_key"),"quantity" to it.getString("quantity"),"amount" to it.getString("amount"),"price" to it.getString("unit_price"),"expires" to it.getString("expires"),"fee" to it.getString("fee_rate"))}.singleOrNull()?:throw MarketRejected("session")
 fun depositWithin(id:String,state:String){val op=operation(id,state);require(op["kind"]=="DEPOSIT")
  val tax=ex.taxes.get(id)
  if(tax!=null&&tax.amount==0L&&tax.state=="PREPARED")db.exec("UPDATE listing_taxes SET state='EXEMPT' WHERE id=?",id)
  check(tax==null||tax.state in listOf("PAID","EXEMPT")||(tax.amount==0L&&tax.state=="PREPARED")){"Unpaid listing tax $id"}
  val n=op.getValue("quantity").toLong();val product=op.getValue("product");val price=op.getValue("price").toLong().takeIf{it>0}?:op.getValue("amount").toLong()
  db.exec("INSERT INTO orders(id,owner,product,total,remaining,minimum,created,group_key,expires) VALUES(?,?,?,?,?,?,?,?,?)",id,op["owner"],product,n,n,price,System.currentTimeMillis(),op["group"],op.getValue("expires").toLong())
  val p=ex.products.getValue(product);ex.save(p,Pricing.next(p,ex.state(p),ex.stock(p),supply=n));ex.inventoryRecord(id,op.getValue("group"),n,"LIST");ex.done(id)
 }
 fun completePurchase(id:String)=db.transaction{completeWithin(id,"EXTERNAL")}
 private fun completeWithin(id:String,state:String){val op=operation(id,state);require(op["kind"]=="BUY")
  val group=op.getValue("group");val product=op.getValue("product");val n=op.getValue("quantity").toLong();val amount=op.getValue("amount").toLong()
  val fills=db.query("SELECT fills.*,orders.owner FROM fills JOIN orders ON orders.id=fills.order_id WHERE tx=?",id){
   val gross=it.getLong("amount");val net=it.getObject("net")?.let{_->it.getLong("net")}?:gross-Pricing.fee(gross,BigDecimal(op.getValue("fee")))
   Fill(it.getString("order_id"),it.getString("owner"),it.getLong("quantity"),gross,net)}
  check(fills.sumOf{it.quantity}==n&&fills.sumOf{it.gross}==amount)
  fills.forEach{f->db.exec("UPDATE orders SET sold=sold+?,income=income+? WHERE id=?",f.quantity,f.net,f.order);ex.warehouse.add(f.owner,group,0,f.net);turnover(f.owner,f.net)}
  ex.warehouse.add(op.getValue("owner"),group,n,0,id);turnover(op.getValue("owner"),amount)
  val p=ex.products.getValue(product);ex.save(p,Pricing.next(p,ex.state(p),ex.stock(p),buy=n))
  db.exec("INSERT INTO history VALUES(?,?,?,?,?,?)",id,product,amount/n,ex.stock(p),n,System.currentTimeMillis());ex.done(id)
 }
 private fun turnover(owner:String,amount:Long){db.exec("INSERT INTO turnover(owner,amount) VALUES(?,?) ON CONFLICT(owner) DO UPDATE SET amount=amount+excluded.amount",owner,amount)}
 fun rejectPurchase(id:String)=db.transaction{rejectWithin(id,"EXTERNAL")}
 private fun rejectWithin(id:String,state:String){val op=operation(id,state)
  db.query("SELECT order_id,quantity FROM fills WHERE tx=?",id){it.getString(1) to it.getLong(2)}.forEach{check(db.exec("UPDATE orders SET remaining=remaining+? WHERE id=?",it.second,it.first)==1)}
  db.exec("UPDATE operations SET state='REJECTED' WHERE id=?",id);ex.inventoryRecord(id,op.getValue("group"),op.getValue("quantity").toLong(),"PAYMENT_REJECTED")
 }
 fun salvageComplete(id:String,paid:Boolean)=db.transaction{val op=operation(id,"EXTERNAL");require(op["kind"]=="SALVAGE")
  if(paid){turnover(op.getValue("owner"),op.getValue("amount").toLong());ex.done(id)}
  else {ex.warehouse.add(op.getValue("owner"),op.getValue("group"),op.getValue("quantity").toLong(),0);db.exec("UPDATE operations SET state='REJECTED' WHERE id=?",id)}
 }
 fun reconcile(id:String,decision:String)=db.transaction{val op=operation(id,"FROZEN")
  when(op["kind"]){
   "BUY"->when(decision){"paid"->completeWithin(id,"FROZEN");"not-paid"->rejectWithin(id,"FROZEN");else->throw MarketRejected("arguments")}
   "DEPOSIT"->if(ex.taxes.get(id)!=null)ex.taxes.reconcileWithin(id,decision)else when(decision){"removed"->depositWithin(id,"FROZEN");"not-removed"->ex.done(id);else->throw MarketRejected("arguments")}
   "SALVAGE"->when(decision){"paid","not-removed"->ex.done(id);"removed-unpaid"->{ex.warehouse.add(op.getValue("owner"),op.getValue("group"),0,op.getValue("amount").toLong());ex.done(id)};else->throw MarketRejected("arguments")}
  }
 }
}
