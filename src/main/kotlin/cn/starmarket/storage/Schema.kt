package cn.starmarket.storage

import java.io.File

/** Additive migration, backed up using SQLite itself (including committed WAL content). */
object Schema {
 fun migrate(db:Database,file:File) {
  val version=db.query("PRAGMA user_version"){it.getInt(1)}.single()
  require(version in 0..3){"market.db schema-version=$version unsupported"}
  if(version==3)return
  if(version>0){
   val backup=File(file.parentFile,"${file.name}.v$version-${System.currentTimeMillis()}.bak")
   db.c.prepareStatement("VACUUM INTO ?").use{it.setString(1,backup.absolutePath);it.execute()}
  }
  db.transaction {
   listOf(
    "CREATE TABLE IF NOT EXISTS markets(id TEXT PRIMARY KEY,price INTEGER,supply REAL,demand REAL,updated INTEGER,paused INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS orders(id TEXT PRIMARY KEY,owner TEXT,product TEXT,total INTEGER,remaining INTEGER,sold INTEGER DEFAULT 0,income INTEGER DEFAULT 0,minimum INTEGER,created INTEGER)",
    "CREATE TABLE IF NOT EXISTS operations(id TEXT PRIMARY KEY,owner TEXT,kind TEXT,product TEXT,quantity INTEGER,amount INTEGER,state TEXT,created INTEGER,note TEXT DEFAULT '')",
    "CREATE TABLE IF NOT EXISTS fills(tx TEXT,order_id TEXT,quantity INTEGER,amount INTEGER,PRIMARY KEY(tx,order_id))",
    "CREATE TABLE IF NOT EXISTS claims(id TEXT PRIMARY KEY,owner TEXT,product TEXT,quantity INTEGER,money INTEGER,state TEXT)",
    "CREATE TABLE IF NOT EXISTS history(tx TEXT,product TEXT,price INTEGER,stock INTEGER,quantity INTEGER,created INTEGER)",
    "CREATE TABLE IF NOT EXISTS audit(created INTEGER,actor TEXT,action TEXT)",
    "CREATE TABLE IF NOT EXISTS items(key TEXT PRIMARY KEY,product TEXT NOT NULL,data TEXT NOT NULL,label TEXT NOT NULL)",
    "CREATE TABLE IF NOT EXISTS inventory_history(tx TEXT,group_key TEXT,delta INTEGER,reason TEXT,created INTEGER)",
    "CREATE TABLE IF NOT EXISTS turnover(owner TEXT PRIMARY KEY,amount INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS listing_taxes(id TEXT PRIMARY KEY,owner TEXT NOT NULL,gross INTEGER NOT NULL,rate TEXT NOT NULL,amount INTEGER NOT NULL,state TEXT NOT NULL)"
   ).forEach{db.exec(it)}
   fun add(table:String,column:String,type:String){if(db.query("PRAGMA table_info($table)"){it.getString("name")}.none{it==column})db.exec("ALTER TABLE $table ADD COLUMN $column $type")}
   add("orders","group_key","TEXT");add("orders","expires","INTEGER NOT NULL DEFAULT 9223372036854775807")
   add("operations","group_key","TEXT");add("operations","unit_price","INTEGER NOT NULL DEFAULT 0")
   add("operations","fee_rate","TEXT NOT NULL DEFAULT '0.02'");add("operations","expires","INTEGER NOT NULL DEFAULT 9223372036854775807")
   add("claims","group_key","TEXT");add("fills","net","INTEGER")
   db.exec("UPDATE orders SET group_key=product WHERE group_key IS NULL")
   db.exec("UPDATE operations SET group_key=product WHERE group_key IS NULL")
   db.exec("UPDATE claims SET group_key=product WHERE group_key IS NULL")
   // Existing listings become fixed-price orders at max(their old minimum, last reference price).
   if(version==1)db.exec("UPDATE orders SET minimum=MAX(minimum,COALESCE((SELECT price FROM markets WHERE markets.id=orders.product),minimum))")
   listOf("CREATE INDEX IF NOT EXISTS orders_match ON orders(group_key,minimum,created,id)","CREATE INDEX IF NOT EXISTS orders_owner ON orders(owner,remaining)","CREATE INDEX IF NOT EXISTS claims_owner ON claims(owner,state)","CREATE INDEX IF NOT EXISTS fills_order ON fills(order_id)").forEach{db.exec(it)}
   db.exec("PRAGMA user_version=3")
  }
 }
}
