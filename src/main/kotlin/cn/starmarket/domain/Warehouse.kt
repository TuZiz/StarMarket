package cn.starmarket.domain
import cn.starmarket.storage.Database
import java.util.UUID

/** Internal durable delivery outbox; there is no player warehouse or manual claim UI. */
class Warehouse(private val db:Database) {
 fun add(owner:String,group:String,n:Long,money:Long,id:String=UUID.randomUUID().toString()) {
  require(n>=0&&money>=0&&(n==0L||money==0L))
  if(n==0L&&money==0L)return
  val product=db.query("SELECT product FROM items WHERE key=?",group){it.getString(1)}.firstOrNull()?:group
  db.exec("INSERT INTO claims(id,owner,product,quantity,money,state,group_key) VALUES(?,?,?,?,?,'READY',?)",id,owner,product,n,money,group)
 }
 fun ready(owner:String)=db.query("SELECT * FROM claims WHERE owner=? AND state='READY' ORDER BY rowid",owner){Claim(it.getString("id"),it.getString("owner"),it.getString("group_key"),it.getLong("quantity"),it.getLong("money"))}
 fun batch(owner:String,online:Boolean)=db.query("SELECT * FROM claims WHERE owner=? AND state='READY' AND (? OR quantity=0) ORDER BY rowid LIMIT 8",owner,online){Claim(it.getString("id"),it.getString("owner"),it.getString("group_key"),it.getLong("quantity"),it.getLong("money"))}
 fun owners(online:List<String>,excluded:List<String>):List<String> {
  val eligible=if(online.isEmpty())"money>0" else "(money>0 OR owner IN (${online.joinToString(","){"?"}}))"
  val skipped=if(excluded.isEmpty())"" else " AND owner NOT IN (${excluded.joinToString(","){"?"}})"
  return db.query("SELECT owner FROM claims WHERE state='READY' AND $eligible$skipped GROUP BY owner ORDER BY MIN(rowid) LIMIT 32",*(online+excluded).toTypedArray()){it.getString(1)}
 }
 fun begin(owner:String,id:String,maximum:Long):Claim=db.transaction {
  val row=ready(owner).firstOrNull{it.id==id}?:throw MarketRejected("claim")
  val n=if(row.quantity>0)minOf(row.quantity,maximum)else 0
  if(row.quantity>0&&n<=0)throw MarketRejected("inventory")
  if(n<row.quantity){add(owner,row.group,row.quantity-n,0);db.exec("UPDATE claims SET quantity=? WHERE id=?",n,id)}
  check(db.exec("UPDATE claims SET state='EXTERNAL' WHERE id=? AND state='READY'",id)==1)
  row.copy(quantity=n)
 }
 fun complete(id:String){check(db.exec("UPDATE claims SET state='DONE' WHERE id=? AND state='EXTERNAL'",id)==1)}
 fun reject(id:String){db.exec("UPDATE claims SET state='READY' WHERE id=? AND state='EXTERNAL'",id)}
 fun freeze(id:String){db.exec("UPDATE claims SET state='FROZEN' WHERE id=? AND state='EXTERNAL'",id)}
}
