package cn.starmarket
import cn.starmarket.storage.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
class MigrationTest {
 @TempDir lateinit var dir:Path
 @Test fun v1MigrationPreservesStockAndSnapshotsFixedPrice(){
  val file=dir.resolve("old.db").toFile()
  DriverManager.getConnection("jdbc:sqlite:"+file.absolutePath).use{c->c.createStatement().use{s->
   s.execute("CREATE TABLE orders(id TEXT PRIMARY KEY,owner TEXT,product TEXT,total INTEGER,remaining INTEGER,sold INTEGER DEFAULT 0,income INTEGER DEFAULT 0,minimum INTEGER,created INTEGER)")
   s.execute("CREATE TABLE markets(id TEXT PRIMARY KEY,price INTEGER,supply REAL,demand REAL,updated INTEGER,paused INTEGER DEFAULT 0)")
   s.execute("INSERT INTO markets VALUES('stone',100,0,0,0,0)");s.execute("INSERT INTO orders VALUES('id','seller','stone',64,64,0,0,20,1)");s.execute("PRAGMA user_version=1")
  }}
  Database(file).use{db->assertEquals(3,db.query("PRAGMA user_version"){it.getInt(1)}.single());assertEquals(64,db.query("SELECT remaining FROM orders"){it.getLong(1)}.single());assertEquals(100,db.query("SELECT minimum FROM orders"){it.getLong(1)}.single());assertEquals("stone",db.query("SELECT group_key FROM orders"){it.getString(1)}.single())}
  Database(file).close();assertEquals(1,dir.toFile().listFiles()!!.count{it.name.endsWith(".bak")})
 }
 @Test fun catalogSplitPreservesCommentsUnknownFieldsAndPrices(){
  val text="schema-version: 1\n# 中文注释\nproducts:\n  stone:\n    material: 'COBBLESTONE'\n    base: 1\n    minimum: 0.2\n    maximum: 5\n    salvage: 0.1\n    custom-field: 42\n  iron:\n    material: 'IRON_INGOT'\n    base: 10\n"
  val result=cn.starmarket.config.CatalogMigration.split(text,setOf("stone","iron"))
  assertEquals(setOf("stone.yml","iron.yml"),result.keys)
  assertTrue(result.getValue("stone.yml").contains("reference-price: 1"))
  assertTrue(result.getValue("stone.yml").contains("custom-field: 42"))
  assertTrue(result.values.all{it.contains("# 中文注释")})
  assertFalse(result.getValue("stone.yml").contains("  iron:"))
 }
}
