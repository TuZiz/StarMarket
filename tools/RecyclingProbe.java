import cn.starmarket.StarMarket;
import cn.starmarket.gui.MarketGui;
import cn.starmarket.platform.RecyclingSelection;
import cn.starmarket.platform.Text;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import net.milkbowl.vault.economy.*;
import net.kyori.adventure.text.Component;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Isolated real-server inventory/SQLite/Vault tests; never load on a production instance. */
public class RecyclingProbe {
 final StarMarket app; final JavaPlugin plugin; final Consumer<Boolean> check;
 final ItemStack[] items=new ItemStack[36]; final UUID owner=UUID.randomUUID(); final Player player;
 Inventory opened; int opens,titles,deposits; long paid; boolean online=true; String mode="ok",lastTitle="";
 RecyclingSelection selection; Economy economy;
 RecyclingProbe(StarMarket app,JavaPlugin plugin,Consumer<Boolean> check){
  this.app=app;this.plugin=plugin;this.check=check;
  var inventory=(PlayerInventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PlayerInventory.class},(p,m,a)->switch(m.getName()){
   case "getItem"->items[(int)a[0]];case "setItem"->{items[(int)a[0]]=(ItemStack)a[1];yield null;}case "getStorageContents"->items;default->zero(m.getReturnType());
  });
  var view=(InventoryView)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()){
   case "getTopInventory"->opened;case "setTitle"->{titles++;lastTitle=(String)a[0];yield null;}default->zero(m.getReturnType());
  });
  player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getUniqueId"->owner;case "getName"->"RecyclingProbe";case "getInventory"->inventory;case "getOpenInventory"->view;
   case "isOnline"->online;case "hasPermission"->true;case "openInventory"->{opened=(Inventory)a[0];opens++;yield view;}
   case "closeInventory"->{opened=null;yield null;}default->zero(m.getReturnType());
  });
  economy=(Economy)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Economy.class},(p,m,a)->switch(m.getName()){
   case "getName"->"RecyclingProbeEconomy";case "isEnabled","has","hasAccount"->true;case "fractionalDigits"->2;
   case "withdrawPlayer"->throw new AssertionError("System recycling must not withdraw tax");
   case "depositPlayer"->{deposits++;if(mode.equals("throw"))throw new IllegalStateException("Simulated uncertain Vault result");boolean ok=mode.equals("ok");if(ok)paid+=Math.round((double)a[1]*100);yield new EconomyResponse((double)a[1],1000,ok?EconomyResponse.ResponseType.SUCCESS:EconomyResponse.ResponseType.FAILURE,"test");}
   default->zero(m.getReturnType());
  });
 }
 static Object zero(Class<?> c){if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==double.class)return 0.0;return null;}
 CompletableFuture<Void> main(Runnable r){return app.main(()->{r.run();return null;});}
 CompletableFuture<Void> idle(){var result=new CompletableFuture<Void>();int[] ticks={0};app.getServer().getScheduler().runTaskTimer(plugin,t->{if(!app.getTransactions().isBusy(player)){t.cancel();result.complete(null);}else if(++ticks[0]>160){t.cancel();result.completeExceptionally(new AssertionError("Recycling timeout"));}},1,1);return result;}
 MarketGui.Screen screen(){return (MarketGui.Screen)opened.getHolder();}
 kotlin.jvm.functions.Function0<kotlin.Unit> action(char c){return screen().getActions().get(screen().getLayout().slots(c).getFirst());}
 void reset(){Arrays.fill(items,null);items[0]=new ItemStack(Material.COBBLESTONE,64);items[1]=new ItemStack(Material.COBBLESTONE,32);}
 void rejected(Runnable r){boolean failed=false;try{r.run();}catch(cn.starmarket.domain.MarketRejected e){failed=true;}check.accept(failed);}
 public CompletableFuture<Void> run(){
  app.getServer().getServicesManager().register(Economy.class,economy,plugin,ServicePriority.Highest);
  return main(()->{
   reset();var renamed=new ItemStack(Material.COBBLESTONE,12);var meta=renamed.getItemMeta();meta.displayName(Component.text("Special"));renamed.setItemMeta(meta);items[2]=renamed;
   selection=RecyclingSelection.Companion.capture(player,new ItemStack(Material.COBBLESTONE));check.accept(selection.getAvailable()==96);
   selection.remove(player,70);check.accept(items[0]==null&&items[1].getAmount()==26&&items[2].getAmount()==12);
   reset();selection=RecyclingSelection.Companion.capture(player,new ItemStack(Material.COBBLESTONE));items[1].setAmount(31);
   rejected(()->selection.remove(player,70));check.accept(items[0].getAmount()==64&&items[1].getAmount()==31);
   reset();online=false;rejected(()->selection.remove(player,10));online=true;check.accept(items[0].getAmount()==64);
   app.getGui().getRecycle().open(player);check.accept(opened.getSize()==54);
   var chars=app.getGui().getAssets().getConfigs().get("recycle").getString("pixel-name-characters");
   for(var example:Map.of("圆石",197,"橡木原木",175,"选择物品",175).entrySet()){
    var rendered=cn.starmarket.gui.RecyclingSkin.INSTANCE.values(Map.of("name",example.getKey()),chars,"","","").get("pixel-name");
    int cursor=0,left=-1;
    for(int cp:rendered.codePoints().toArray()){
     if(cp>=0xFA980&&cp<0xFA98A)cursor+=2<<(cp-0xFA980);
     else if(cp>=0xFA990&&cp<0xFA99A)cursor-=2<<(cp-0xFA990);
     else if(cp==0xFA9A0)cursor++;
     else if(cp==0xFA9A1)cursor--;
     else if(cp>=0xFAA00){if(left<0)left=cursor;cursor+=22;}
    }
    check.accept(left==example.getValue()&&cursor==0);
   }
   String priceChars="?单价 金币0123456789.";
   for(String amount:List.of("0.1","123.45","123456789012345.67")){
    var pixels=cn.starmarket.gui.RecyclingSkin.INSTANCE.values(Map.of("price",amount),chars,"","单价 {price} 金币",priceChars);
    int min=Integer.MAX_VALUE,max=0;
    for(String key:List.of("pixel-price-prefix","pixel-price","pixel-price-suffix")){
     int cursor=0;
     for(int cp:pixels.get(key).codePoints().toArray()){
      if(cp>=0xFA980&&cp<0xFA98A)cursor+=2<<(cp-0xFA980);
      else if(cp>=0xFA990&&cp<0xFA99A)cursor-=2<<(cp-0xFA990);
      else if(cp==0xFA9A0)cursor++;
      else if(cp==0xFA9A1)cursor--;
      else if(cp>=0xFAB00){char c=priceChars.charAt(cp-0xFAB00);int advance=c==' '?4:c<128?5:9;min=Math.min(min,cursor);cursor+=advance*2;max=Math.max(max,cursor-2);}
     }
     check.accept(cursor==0);
    }
    check.accept(min+max==436);
   }
   check.accept(lastTitle.contains(new String(Character.toChars(0xFAA00+chars.indexOf('选')))));
   check.accept(opened.getItem(screen().getLayout().slots('I').getFirst()).getItemMeta().getItemModel().toString().equals("starmarket:preview/chest"));
   app.getGui().getRecycle().choose(player,items[0].clone(),0);check.accept(opens==1);
   check.accept(lastTitle.contains(new String(Character.toChars(0xFAA00+chars.indexOf('圆'))))&&!lastTitle.contains("{pixel-name}"));
   check.accept(opened.getItem(screen().getLayout().slots('I').getFirst()).getItemMeta().getItemModel().toString().equals("starmarket:preview/cobblestone"));
   check.accept(!items[0].getItemMeta().hasItemModel());
   var old=action('S');var bar=action('1');bar.invoke();bar.invoke();check.accept(opens==1&&titles>=3);
   rejected(()->old.invoke());
   var lore=opened.getItem(screen().getLayout().slots('I').getFirst()).getItemMeta().lore();check.accept(lore.stream().anyMatch(c->net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).contains("15 / 96")));
   var printer=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
   var percents=List.of(15,30,45,60,75,90,100);
   for(int i=1;i<=7;i++){
    var tile=opened.getItem(screen().getLayout().slots((char)('0'+i)).getFirst()).getItemMeta();
    check.accept(printer.serialize(tile.displayName()).endsWith(percents.get(i-1)+"%"));
   }
   items[0].setAmount(1);items[1]=null;
   app.getGui().getRecycle().choose(player,items[0].clone(),0);
   action('1').invoke();
   var lowSlot=screen().getLayout().slots('1').getFirst();
   var highSlot=screen().getLayout().slots('7').getFirst();
   check.accept(opened.getItem(lowSlot).getItemMeta().getItemModel().toString().equals("starmarket:bar_handle"));
   check.accept(opened.getItem(highSlot).getItemMeta().getItemModel().toString().equals("starmarket:bar_empty"));
   reset();
   app.getGui().getRecycle().choose(player,items[0].clone(),0);
   action('1').invoke();
   var confirm=action('S');confirm.invoke();rejected(()->confirm.invoke());
  }).thenCompose(v->idle()).thenCompose(v->main(()->{
   check.accept(items[0].getAmount()==49&&items[1].getAmount()==32&&deposits==1&&paid==150);
   reset();selection=RecyclingSelection.Companion.capture(player,new ItemStack(Material.COBBLESTONE));
   app.getTransactions().getListing().recycle(player,app.getTransactions().getCodec().identify(items[0]),70,selection);
   items[1].setAmount(31); // Changes while durable preparation is in flight.
  })).thenCompose(v->idle()).thenCompose(v->main(()->{
   check.accept(items[0].getAmount()==64&&items[1].getAmount()==31&&deposits==1);
   reset();selection=RecyclingSelection.Companion.capture(player,new ItemStack(Material.COBBLESTONE));
   app.getTransactions().getListing().recycle(player,app.getTransactions().getCodec().identify(items[0]),70,selection);online=false;
  })).thenCompose(v->idle()).thenCompose(v->main(()->{
   online=true;check.accept(items[0].getAmount()==64&&items[1].getAmount()==32&&deposits==1);
   reset();mode="reject";selection=RecyclingSelection.Companion.capture(player,new ItemStack(Material.COBBLESTONE));
   app.getTransactions().getListing().recycle(player,app.getTransactions().getCodec().identify(items[0]),70,selection);
  })).thenCompose(v->idle()).thenCompose(v->app.db(ex->{
   check.accept(ex.getWarehouse().ready(owner.toString()).stream().mapToLong(c->c.getQuantity()).sum()==70);
   return null;
  })).thenCompose(v->main(()->{
   check.accept(deposits==2&&items[0]==null&&items[1].getAmount()==26);reset();mode="throw";
   selection=RecyclingSelection.Companion.capture(player,new ItemStack(Material.COBBLESTONE));
   app.getTransactions().getListing().recycle(player,app.getTransactions().getCodec().identify(items[0]),70,selection);
  })).thenCompose(v->idle()).thenCompose(v->app.db(ex->{
   check.accept(ex.getDb().query("SELECT state FROM operations WHERE owner=? AND state='FROZEN'",new Object[]{owner.toString()},r->{try{return r.getString(1);}catch(Exception e){throw new RuntimeException(e);}}).size()==1);
   check.accept(ex.orders(owner.toString(),null).isEmpty());return null;
  })).thenCompose(v->main(()->check.accept(deposits==3)))
  .handle((v,e)->main(()->{app.getServer().getServicesManager().unregister(Economy.class,economy);app.getGui().clear();if(e!=null)throw new CompletionException(e);})).thenCompose(v->v);
 }
}

