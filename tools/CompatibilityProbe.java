import cn.starmarket.StarMarket;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.nio.file.*;
public class CompatibilityProbe extends JavaPlugin {
 int count; StarMarket app;
 void check(boolean ok){if(!ok)throw new AssertionError("Check "+count);count++;}
 public void onEnable(){app=(StarMarket)getServer().getPluginManager().getPlugin("StarMarket");
 getServer().getScheduler().runTaskTimer(this,t->{if(!app.getReady())return;t.cancel();try{
 var codec=app.getTransactions().getCodec();
 var plain=codec.identify(new ItemStack(Material.DIRT,4));check(codec.label(plain).equals("泥土"));check(!codec.recyclable(plain));
 var special=new ItemStack(Material.DIAMOND_SWORD);var meta=special.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(this,"variant"),PersistentDataType.STRING,"unique");special.setItemMeta(meta);
 var item=codec.identify(special);check(codec.decode(item).isSimilar(special));check(!item.getKey().equals(codec.identify(new ItemStack(Material.DIAMOND_SWORD)).getKey()));
 check(app.getGui().getAssets().getConfigs().size()==7);
 var sender=getServer().getConsoleSender();check(app.getCommand("market").tabComplete(sender,"market",new String[]{"ad"}).equals(List.of("admin")));
 check(app.getCommand("market").tabComplete(sender,"market",new String[]{"cancel",""}).isEmpty());
 var seller=UUID.randomUUID().toString();var buyer=UUID.randomUUID().toString();
 app.db(ex->{ex.setListingTaxRate(java.math.BigDecimal.ZERO);var product=app.getSettings().getProducts().get(plain.getProduct());var id=ex.deposit(seller,product,4,200,plain);ex.deposited(id);check(ex.stock(product,null)==4);var q=ex.quote(buyer,product,1,plain.getKey());check(q.getTotal()==200);var purchase=ex.reserve(q);ex.bought(purchase,q);check(ex.stock(product,null)==3);ex.withdraw(seller,id);check(ex.stock(product,null)==0);return null;}).whenComplete((v,e)->app.main(()->{finish(e);return null;}));
 }catch(Throwable e){finish(e);}},20,20);}
 void finish(Throwable error){try{Files.writeString(Path.of("compat-result.txt"),error==null?"PASSED "+count+" "+Bukkit.getVersion():"FAILED "+error);if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Compatibility failed",error);}catch(Exception e){throw new RuntimeException(e);}getServer().shutdown();}
}
