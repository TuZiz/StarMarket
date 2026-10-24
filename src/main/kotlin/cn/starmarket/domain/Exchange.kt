package cn.starmarket.domain

import cn.starmarket.storage.Database
import java.math.BigDecimal
import java.util.UUID

/** Serial database owner; contains no platform objects or external calls. */
class Exchange(val db:Database,var products:Map<String,Product>,var fee:BigDecimal,
 var orderLimit:Int,var stockLimit:Long,var lifetimeMillis:Long=604800000,var listingTaxRate:BigDecimal=BigDecimal.ZERO) {
 val settlements=Settlements(this)
 val warehouse=Warehouse(db)
 val taxes=ListingTaxes(this)
 fun state(p:Product)=db.query("SELECT * FROM markets WHERE id=?",p.id){MarketState(it.getLong("price"),it.getDouble("supply"),it.getDouble("demand"),it.getLong("updated"))}.firstOrNull()
  ?:MarketState(p.base,0.0,0.0,System.currentTimeMillis()).also{save(p,it)}
 fun save(p:Product,s:MarketState){db.exec("INSERT INTO markets(id,price,supply,demand,updated) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET price=excluded.price,supply=excluded.supply,demand=excluded.demand,updated=excluded.updated",p.id,s.price,s.supply,s.demand,s.updated)}
 fun orders(owner:String?=null,product:String?=null)=db.query("SELECT * FROM orders WHERE (? IS NULL OR owner=?) AND (? IS NULL OR product=?) ORDER BY created,id",owner,owner,product,product){Order(it.getString("id"),it.getString("owner"),it.getString("product"),it.getLong("remaining"),it.getLong("minimum"),it.getLong("sold"),it.getLong("income"),it.getString("group_key"),it.getLong("expires"),it.getLong("created"))}
 fun register(item:ItemIdentity){
  val existing=item(item.key)
  require(existing==null||(existing.product==item.product&&existing.data==item.data)){"Item identity collision ${item.key}"}
  db.exec("INSERT OR IGNORE INTO items VALUES(?,?,?,?)",item.key,item.product,item.data,item.label)
 }
 fun item(key:String)=db.query("SELECT * FROM items WHERE key=?",key){ItemIdentity(it.getString("key"),it.getString("product"),it.getString("data"),it.getString("label"))}.firstOrNull()
 fun inventoryRecord(id:String,group:String,n:Long,reason:String){db.exec("INSERT INTO inventory_history VALUES(?,?,?,?,?)",id,group,n,reason,System.currentTimeMillis())}
 fun checkMarket(p:Product){if(db.query("SELECT paused FROM markets WHERE id=?",p.id){it.getInt(1)}.firstOrNull()==1)throw MarketRejected("paused")}
 fun stock(p:Product,buyer:String?=null)=orders(product=p.id).filter{it.owner!=buyer&&it.expires>System.currentTimeMillis()}.sumOf{it.remaining}
 fun offers(buyer:String,search:String="",category:String=""):List<MarketOffer> {
  expire()
  return db.query("SELECT items.*,SUM(orders.remaining) AS stock,MIN(orders.minimum) AS price FROM orders JOIN items ON items.key=orders.group_key WHERE orders.remaining>0 AND orders.owner<>? AND orders.expires>? GROUP BY items.key",buyer,System.currentTimeMillis()){
   MarketOffer(ItemIdentity(it.getString("key"),it.getString("product"),it.getString("data"),it.getString("label")),it.getLong("stock"),it.getLong("price"))
  }.filter{val p=products[it.item.product];p!=null&&(category.isEmpty()||p.category==category)&&(search.isEmpty()||it.item.label.contains(search,true)||p.name.contains(search,true)||p.material.contains(search,true))}.sortedWith(compareBy({it.item.product},{it.item.key}))
 }
 fun prepare(owner:String,kind:String,p:Product,n:Long,amount:Long=0,group:String=p.id):String {
  checkMarket(p)
  if(n !in 1..stockLimit)throw MarketRejected("quantity")
  if(db.query("SELECT id FROM operations WHERE owner=? AND state IN ('FROZEN','EXTERNAL','ITEMS')",owner){it.getString(1)}.isNotEmpty())throw MarketRejected("frozen")
  val id=UUID.randomUUID().toString()
  db.exec("INSERT INTO operations(id,owner,kind,product,quantity,amount,state,created,group_key,fee_rate) VALUES(?,?,?,?,?,?,'ITEMS',?,?,?)",id,owner,kind,p.id,n,amount,System.currentTimeMillis(),group,fee.toPlainString());return id
 }
 fun deposit(owner:String,p:Product,n:Long,price:Long,item:ItemIdentity):String=db.transaction {
  expireWithinTransaction()
  val active=orders(owner).filter{it.remaining>0}
  if(active.size>=orderLimit||active.sumOf{it.remaining}+n>stockLimit)throw MarketRejected("capacity")
  if(price !in p.floor..p.ceiling)throw MarketRejected("price")
  val gross=Math.multiplyExact(n,price);require(item.product==p.id);register(item)
  val id=prepare(owner,"DEPOSIT",p,n,price,item.key)
  db.exec("UPDATE operations SET unit_price=?,expires=? WHERE id=?",price,Math.addExact(System.currentTimeMillis(),lifetimeMillis),id)
  taxes.prepareWithin(id,owner,gross,listingTaxRate);id
 }
 fun deposited(id:String)=db.transaction {settlements.depositWithin(id,"ITEMS")}
 fun quote(buyer:String,p:Product,n:Long,group:String=p.id):Quote {
  expire();checkMarket(p);if(n !in 1..stockLimit)throw MarketRejected("quantity")
  val candidates=orders(product=p.id).filter{it.group==group&&it.owner!=buyer&&it.remaining>0&&it.expires>System.currentTimeMillis()}.sortedWith(compareBy({it.minimum},{it.created},{it.id}))
  var remaining=n;var total=0L;val fills=mutableListOf<Fill>();val tiers=linkedMapOf<Long,Long>()
  for(o in candidates){val take=minOf(o.remaining,remaining);if(take==0L)break
   val gross=Math.multiplyExact(o.minimum,take);total=Math.addExact(total,gross)
   fills+=Fill(o.id,o.owner,take,gross,gross-Pricing.fee(gross,fee));tiers[o.minimum]=(tiers[o.minimum]?:0)+take;remaining-=take
  }
  if(remaining>0)throw InsufficientStock(n,n-remaining)
  return Quote(UUID.randomUUID().toString(),buyer,p.id,group,n,total,fills,tiers.map{PriceTier(it.key,it.value)},System.currentTimeMillis()+15000,fee)
 }
 fun reserve(q:Quote):String=db.transaction {
  if(System.currentTimeMillis()>q.expires)throw MarketRejected("expired")
  // quote() cannot open an expiry transaction inside this transaction.
  val fresh=quoteWithoutExpiry(q)
  if(fresh!=q.fills||q.fee!=fee)throw MarketRejected("changed")
  val id=prepare(q.buyer,"BUY",products.getValue(q.product),q.quantity,q.total,q.group)
  q.fills.forEach{f->check(db.exec("UPDATE orders SET remaining=remaining-? WHERE id=? AND remaining>=? AND expires>?",f.quantity,f.order,f.quantity,System.currentTimeMillis())==1);db.exec("INSERT INTO fills(tx,order_id,quantity,amount,net) VALUES(?,?,?,?,?)",id,f.order,f.quantity,f.gross,f.net)}
  db.exec("UPDATE operations SET state='EXTERNAL' WHERE id=?",id);inventoryRecord(id,q.group,-q.quantity,"RESERVE");id
 }
 private fun quoteWithoutExpiry(q:Quote):List<Fill> {
  checkMarket(products.getValue(q.product));var left=q.quantity;val result=mutableListOf<Fill>()
  orders(product=q.product).filter{it.group==q.group&&it.owner!=q.buyer&&it.remaining>0&&it.expires>System.currentTimeMillis()}.sortedWith(compareBy({it.minimum},{it.created},{it.id})).forEach{o->
   val n=minOf(left,o.remaining);if(n>0){val gross=Math.multiplyExact(n,o.minimum);result+=Fill(o.id,o.owner,n,gross,gross-Pricing.fee(gross,fee));left-=n}}
  if(left>0)throw InsufficientStock(q.quantity,q.quantity-left);return result
 }
 fun bought(id:String,q:Quote)=settlements.completePurchase(id)
 fun rejected(id:String)=settlements.rejectPurchase(id)
 fun withdraw(owner:String,id:String)=db.transaction {
  val o=orders(owner).firstOrNull{it.id==id}?:throw MarketRejected("order")
  if(o.remaining<=0)throw MarketRejected("order")
  if(locked(o.id))throw MarketRejected("frozen")
  returnOrder(o,"CANCEL")
 }
 private fun locked(id:String)=db.query("SELECT tx FROM fills JOIN operations ON operations.id=fills.tx WHERE order_id=? AND state IN ('FROZEN','EXTERNAL')",id){it.getString(1)}.isNotEmpty()
 private fun returnOrder(o:Order,reason:String){db.exec("UPDATE orders SET remaining=0 WHERE id=?",o.id);warehouse.add(o.owner,o.group,o.remaining,0);inventoryRecord(o.id,o.group,-o.remaining,reason)}
 private fun expireWithinTransaction(){orders().filter{it.remaining>0&&it.expires<=System.currentTimeMillis()&&!locked(it.id)}.forEach{returnOrder(it,"EXPIRE")}}
 fun expire(){db.transaction{expireWithinTransaction()}}
 fun freeze(id:String,note:String){db.exec("UPDATE operations SET state='FROZEN',note=? WHERE id=? AND state IN ('EXTERNAL','ITEMS')",note,id)}
 fun done(id:String){check(db.exec("UPDATE operations SET state='DONE' WHERE id=? AND state IN ('EXTERNAL','ITEMS','FROZEN')",id)==1)}
 fun reconcile(id:String,decision:String)=settlements.reconcile(id,decision)
}
