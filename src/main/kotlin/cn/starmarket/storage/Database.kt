package cn.starmarket.storage
import java.sql.Connection
import java.sql.DriverManager
import java.io.File

/** Only the database executor owns this connection. */
class Database(file:File):AutoCloseable {
 val c:Connection=DriverManager.getConnection("jdbc:sqlite:"+file.absolutePath)
 init {
  try {
   c.createStatement().use{it.execute("PRAGMA journal_mode=WAL");it.execute("PRAGMA synchronous=FULL");it.execute("PRAGMA foreign_keys=ON");it.execute("PRAGMA busy_timeout=5000")}
   Schema.migrate(this,file)
   exec("CREATE INDEX IF NOT EXISTS claims_dispatch ON claims(state,owner)")
   exec("UPDATE operations SET state='FROZEN',note='Restart during external operation; reconcile manually' WHERE state IN ('EXTERNAL','ITEMS')")
   exec("UPDATE claims SET state='FROZEN' WHERE state='EXTERNAL'")
  }catch(e:Throwable){c.close();throw e}
 }
 fun exec(sql:String,vararg args:Any?):Int=c.prepareStatement(sql).use{s->args.forEachIndexed{i,v->s.setObject(i+1,v)};s.executeUpdate()}
 fun <T> query(sql:String,vararg args:Any?,read:(java.sql.ResultSet)->T):List<T> = c.prepareStatement(sql).use{s->args.forEachIndexed{i,v->s.setObject(i+1,v)};s.executeQuery().use{r->buildList{while(r.next())add(read(r))}}}
 fun <T> transaction(block:()->T):T {check(c.autoCommit);c.autoCommit=false;try{val result=block();c.commit();return result}catch(t:Throwable){c.rollback();throw t}finally{c.autoCommit=true}}
 override fun close(){c.close()}
}
