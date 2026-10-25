import cn.starmarket.StarMarket;
import cn.starmarket.gui.MarketGui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.*;
import kotlin.jvm.functions.Function1;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Real Leaf chat events, inventories, SQLite and temporary Vault provider. Isolated server only. */
public final class HandListingProbe {
 private final StarMarket app;private final JavaPlugin plugin;private final MarketGui gui;private final BiConsumer<Boolean,String> verify;
 private final UUID owner=UUID.randomUUID();private final Player player;private final Economy economy;
 private final ItemStack[] storage=new ItemStack[36];private final List<String> messages=new ArrayList<>();
 private Inventory opened;private int held=0,opens=0,withdrawals=0,deposits=0;private boolean online=true,reject=false;private long balance=100000;
 HandListingProbe(StarMarket app,JavaPlugin plugin,BiConsumer<Boolean,String> verify){
  this.app=app;this.plugin=plugin;this.gui=app.getGui();this.verify=verify;
  var inventory=(PlayerInventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PlayerInventory.class},(p,m,a)->switch(m.getName()){
   case "getHeldItemSlot"->held;case "getItemInMainHand"->storage[held]==null?new ItemStack(Material.AIR):storage[held];
   case "getItem"->storage[(int)a[0]];case "setItem"->{storage[(int)a[0]]=(ItemStack)a[1];yield null;}case "getStorageContents"->storage;default->null;
  });
  player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getUniqueId"->owner;case "getInventory"->inventory;case "isOnline"->online;case "hasPermission"->true;
   case "getName"->"HandSaleProbe";case "openInventory"->{close();opened=(Inventory)a[0];opens++;yield null;}
   case "closeInventory"->{close();yield null;}case "sendMessage"->{if(a[0] instanceof Component c)messages.add(PlainTextComponentSerializer.plainText().serialize(c));yield null;}default->null;
  });
  economy=(Economy)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Economy.class},(p,m,a)->switch(m.getName()){
   case "getName"->"HandSaleTestEconomy";case "isEnabled","hasAccount"->true;case "fractionalDigits"->2;case "has"->true;
   case "withdrawPlayer","depositPlayer"->{
    boolean own=a[0] instanceof OfflinePlayer account&&account.getUniqueId().equals(owner);boolean out=m.getName().equals("withdrawPlayer");
    long value=java.math.BigDecimal.valueOf((double)a[1]).movePointRight(2).longValueExact();
    if(own){if(out)withdrawals++;else deposits++;if(!(out&&reject))balance+=out?-value:value;}
    yield new EconomyResponse((double)a[1],balance/100.0,out&&reject?EconomyResponse.ResponseType.FAILURE:EconomyResponse.ResponseType.SUCCESS,"test");
   }
   default->m.getReturnType()==boolean.class?false:m.getReturnType()==double.class?1000.0:m.getReturnType()==String.class?"":null;
  });
 }
 private CompletableFuture<Void> main(Runnable action){return app.main(()->{action.run();return null;});}
 private MarketGui.Screen screen(){return (MarketGui.Screen)opened.getHolder();}
 private void click(char symbol){screen().getActions().get(screen().getLayout().slots(symbol).getFirst()).invoke();}
 private void close(){if(opened==null)return;var old=opened;opened=null;
  var view=(InventoryView)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()){
   case "getTopInventory"->old;case "getPlayer"->player;default->null;
  });gui.close(new InventoryCloseEvent(view));
 }
 private Object input(){try{var f=MarketGui.class.getDeclaredField("inputs");f.setAccessible(true);return ((Map<?,?>)f.get(gui)).get(owner);}catch(Exception e){throw new RuntimeException(e);}}
 @SuppressWarnings("unchecked") private Function1<String,?> callback(){try{Object value=input();var m=value.getClass().getDeclaredMethod("getConsume");m.setAccessible(true);return (Function1<String,?>)m.invoke(value);}catch(Exception e){throw new RuntimeException(e);}}
 private void rejected(Function1<String,?> callback,String note){boolean failed=false;try{callback.invoke("2");}catch(RuntimeException e){failed=true;}verify.accept(failed,note);}
 private AsyncChatEvent send(String text){var message=Component.text(text);var e=new AsyncChatEvent(false,player,new HashSet<>(),ChatRenderer.defaultRenderer(),message,message,SignedMessage.system(text,message));gui.chat(e);return e;}
 private CompletableFuture<Void> until(BooleanSupplier condition){var future=new CompletableFuture<Void>();int[] ticks={0};
  app.getServer().getScheduler().runTaskTimer(plugin,task->{try{if(condition.getAsBoolean()){task.cancel();future.complete(null);}else if(++ticks[0]>200){task.cancel();future.completeExceptionally(new AssertionError("hand listing timed out"));}}catch(Throwable e){task.cancel();future.completeExceptionally(e);}},1,1);return future;
 }
 private CompletableFuture<Void> invalid(String text,String hint){Object previous=input();int count=messages.size();
  return main(()->verify.accept(send(text).isCancelled(),"invalid price/item input kept out of public chat"))
   .thenCompose(v->until(()->input()!=null&&input()!=previous))
   .thenCompose(v->main(()->verify.accept(messages.subList(count,messages.size()).stream().anyMatch(s->s.contains(hint)),"localized retry: "+hint)));
 }
 public CompletableFuture<Void> run(){
  app.getServer().getServicesManager().register(Economy.class,economy,plugin,ServicePriority.Highest);
  return scenario().handle((v,error)->main(()->{app.getServer().getServicesManager().unregister(Economy.class,economy);gui.clear();if(error!=null)throw new CompletionException(error);})).thenCompose(f->f);
 }
 private CompletableFuture<Void> scenario(){
  final Function1<String,?>[] old=new Function1[1];
  return main(()->gui.home(player,"","",0)).thenCompose(v->until(()->opened!=null&&screen().getMenu().equals("browse")))
   .thenCompose(v->main(()->{
    verify.accept(screen().getActions().containsKey(screen().getLayout().slots('B').getFirst()),"home has independent system recycling button");
    storage[2]=new ItemStack(Material.COBBLESTONE,32);storage[3]=new ItemStack(Material.OAK_LOG,64);click('S');
    verify.accept(opened==null&&input()!=null,"sell button closes GUI and starts chat input");
    verify.accept(messages.stream().anyMatch(s->s.contains("主手")&&s.contains("即确认")),"prompt explains main hand and immediate confirmation");
    held=3;old[0]=callback();var first=send("2");var duplicate=send("2");
    verify.accept(first.isCancelled()&&duplicate.isCancelled(),"rapid duplicate price messages both suppressed from public chat");
   })).thenCompose(v->until(()->withdrawals==1&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->app.db(ex->ex.orders(owner.toString(),null)))
   .thenCompose(orders->main(()->{
    verify.accept(orders.size()==1&&orders.getFirst().getRemaining()==64&&orders.getFirst().getMinimum()==200,"chat creates exactly one full-stack order at supplied unit price");
    verify.accept(orders.getFirst().getProduct().equals("oak_log")&&storage[3]==null&&storage[2].getAmount()==32,"item is chosen from main hand after GUI closes, other stack untouched");
    verify.accept(balance==99616&&withdrawals==1,"3 percent listing tax withdrawn once and no immediate sale income");
    verify.accept(opened==null&&input()==null,"successful listing never opens sale confirmation GUI");
    rejected(old[0],"consumed chat callback cannot replay listing");gui.getSale().start(player);
   })).thenCompose(v->invalid("not-a-price","售价无效"))
   .thenCompose(v->invalid("0.001","售价无效"))
   .thenCompose(v->invalid("2","主手没有物品"))
   .thenCompose(v->main(()->held=2)).thenCompose(v->invalid("0","0.01 至"))
   .thenCompose(v->main(()->{
    verify.accept(withdrawals==1&&storage[2].getAmount()==32,"invalid input never removes items or taxes");send("cancel");
   })).thenCompose(v->until(()->opened!=null&&screen().getMenu().equals("browse")))
   .thenCompose(v->main(()->{
    verify.accept(input()==null,"cancel exits chat without listing");click('B');
    verify.accept(screen().getMenu().equals("recycle")&&opened.getSize()==27,"system recycling opens dedicated ordinary chest");
    verify.accept(screen().getActions().size()==1,"empty recycling screen has no listing or price controls");
    gui.getRecycle().choose(player,storage[2].clone(),2);var stale=screen().getActions().get(13);
    storage[5]=new ItemStack(Material.DIAMOND_SWORD);gui.getRecycle().choose(player,storage[5].clone(),5);
    verify.accept(opened.getItem(13)==null&&!screen().getActions().containsKey(13),"non-recyclable item clears old recycling action");
    boolean failed=false;try{stale.invoke();}catch(RuntimeException e){failed=true;}verify.accept(failed,"old recycling confirmation rejected after selecting invalid item");
    storage[2]=new ItemStack(Material.COBBLESTONE,12);gui.getRecycle().choose(player,storage[2].clone(),2);click('S');
   })).thenCompose(v->until(()->deposits==1&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->app.db(ex->ex.orders(owner.toString(),null)))
   .thenCompose(orders->main(()->{
    verify.accept(storage[2]==null&&balance==99736&&withdrawals==1,"system recycling pays fixed1.2 without tax");
    verify.accept(orders.size()==1,"recycled items never become market stock");
    storage[2]=new ItemStack(Material.COBBLESTONE,8);held=2;reject=true;gui.getSale().start(player);send("2");
   })).thenCompose(v->until(()->withdrawals==2&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->app.db(ex->ex.getWarehouse().ready(owner.toString()).stream().mapToLong(c->c.getQuantity()).sum()))
   .thenCompose(quantity->main(()->{
    verify.accept(quantity==8&&balance==99736,"explicit tax refusal preserves all items as reliable return receipt");
    reject=false;storage[2]=new ItemStack(Material.COBBLESTONE,7);gui.getSale().start(player);old[0]=callback();gui.clear();
    rejected(old[0],"reload cleanup rejects late chat callback");gui.getSale().start(player);online=false;send("2");
   })).thenCompose(v->until(()->input()==null))
   .thenCompose(v->main(()->{
    verify.accept(storage[2].getAmount()==7&&withdrawals==2,"disconnect before chat application causes no transaction");online=true;
    app.getServer().getServicesManager().unregister(Economy.class,economy);gui.getSale().start(player);send("2");
   })).thenCompose(v->until(()->input()==null&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->main(()->{
    verify.accept(storage[2].getAmount()==7&&withdrawals==2,"unavailable economy rejects chat listing before item removal");
    app.getServer().getServicesManager().register(Economy.class,economy,plugin,ServicePriority.Highest);
    storage[2]=new ItemStack(Material.DIRT,4);gui.getSale().start(player);send("99");
   })).thenCompose(v->until(()->withdrawals==3&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->app.db(ex->ex.orders(owner.toString(),null)))
   .thenCompose(orders->main(()->{
    verify.accept(orders.stream().anyMatch(o->o.getProduct().equals("minecraft:dirt")&&o.getRemaining()==4&&o.getMinimum()==9900),"unconfigured item actually consigned at freely chosen price");
    verify.accept(storage[2]==null&&balance==98548,"unconfigured item removed once and 3 percent tax paid");
    verify.accept(messages.stream().anyMatch(m->m.contains("3%")&&m.contains("不退")),"separate concise tax notice retained");
    int before=messages.size();app.onCommand(player,app.getCommand("market"),"market",new String[]{"help"});
    verify.accept(messages.subList(before,messages.size()).stream().anyMatch(m->m.contains("/market recycle")&&m.contains("回收")),"actual help command describes separate system recycling");
    verify.accept(messages.subList(before,messages.size()).stream().anyMatch(m->m.contains("/market sell")&&m.contains("单价")),"actual help command describes hand listing");
    verify.accept(app.getCommand("market").getDescription().contains("help"),"registered command metadata advertises help");
   }));
 }
}
