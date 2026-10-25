import cn.starmarket.StarMarket;
import cn.starmarket.domain.*;
import cn.starmarket.gui.MarketGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Real plugin GUI callbacks and SQLite, with a player view fixture. Isolated server only. */
public final class ListingRefreshProbe {
 private final StarMarket app;private final JavaPlugin plugin;private final BiConsumer<Boolean,String> verify;
 private final UUID owner=UUID.randomUUID();private final Player player;private Inventory opened;private ItemIdentity item;
 private MarketGui.Screen before;private int slot;private String order;
 ListingRefreshProbe(StarMarket app,JavaPlugin plugin,BiConsumer<Boolean,String> verify){
  this.app=app;this.plugin=plugin;this.verify=verify;
  player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getUniqueId"->owner;case "isOnline","hasPermission"->true;case "openInventory"->{opened=(Inventory)a[0];yield null;}default->null;
  });
 }
 private CompletableFuture<Void> main(Runnable action){return app.main(()->{action.run();return null;});}
 private MarketGui.Screen screen(){return (MarketGui.Screen)opened.getHolder();}
 private long buttons(){return screen().getLayout().getContentSlots().stream().filter(i->screen().getActions().containsKey(i)).count();}
 private CompletableFuture<Void> until(BooleanSupplier condition){
  var result=new CompletableFuture<Void>();int[] ticks={0};
  app.getServer().getScheduler().runTaskTimer(plugin,task->{try{
   if(condition.getAsBoolean()){task.cancel();result.complete(null);}else if(++ticks[0]>200){task.cancel();result.completeExceptionally(new AssertionError("GUI refresh timed out"));}
  }catch(Throwable error){task.cancel();result.completeExceptionally(error);}},1,1);return result;
 }
 private CompletableFuture<Void> supply(int count){return app.db(ex->{
  int limit=ex.getOrderLimit();BigDecimal tax=ex.getListingTaxRate();ex.setOrderLimit(100);ex.setListingTaxRate(BigDecimal.ZERO);
  try{var p=ex.getProducts().get(item.getProduct());for(int i=0;i<count;i++){order=ex.deposit(owner.toString(),p,58,p.getBase(),item);ex.deposited(order);}}finally{ex.setOrderLimit(limit);ex.setListingTaxRate(tax);}return null;
 });}
 private CompletableFuture<Void> open(int page){return main(()->{before=opened==null?null:screen();app.getGui().mine(player,page);}).thenCompose(v->until(()->opened!=null&&screen()!=before&&screen().getMenu().equals("listings")));}
 private void click(){before=screen();slot=before.getLayout().getContentSlots().getFirst();var action=before.getActions().get(slot);action.invoke();action.invoke();}
 public CompletableFuture<Void> run(){
  item=app.getTransactions().getCodec().identify(new ItemStack(Material.COBBLESTONE));
  return supply(1).thenCompose(v->open(0)).thenCompose(v->main(()->{
   verify.accept(buttons()==1,"single active listing initially rendered");click();
   verify.accept(!before.getActions().containsKey(slot),"cancel click immediately disables old listing action");
  })).thenCompose(v->until(()->screen()!=before&&!app.getTransactions().isBusy(player))).thenCompose(v->main(()->{
   verify.accept(before.getInv().getItem(slot)==null&&!before.getActions().containsKey(slot),"successful withdrawal clears stale icon and action");
   verify.accept(buttons()==0,"last cancelled order disappears without reopening menu");
  })).thenCompose(v->app.db(ex->ex.getWarehouse().ready(owner.toString()).stream().mapToLong(Claim::getQuantity).sum()))
   .thenCompose(quantity->main(()->verify.accept(quantity==58,"repeated cancel clicks only return stock once")))
   .thenCompose(v->supply(1)).thenCompose(v->open(0)).thenCompose(v->app.db(ex->{ex.withdraw(owner.toString(),order);return null;}))
   .thenCompose(v->main(this::click)).thenCompose(v->until(()->screen()!=before&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->main(()->verify.accept(buttons()==0,"already withdrawn stale listing also removed after rejection")))
   .thenCompose(v->supply(29)).thenCompose(v->open(1)).thenCompose(v->main(()->{verify.accept(buttons()==1,"last page fixture contains one order");click();}))
   .thenCompose(v->until(()->screen()!=before&&!app.getTransactions().isBusy(player)))
   .thenCompose(v->main(()->verify.accept(buttons()==28,"empty last page automatically clamps to previous page")))
   .thenCompose(v->main(()->{click();app.getGui().getRecycle().open(player);}))
   .thenCompose(v->until(()->!app.getTransactions().isBusy(player)))
   .thenCompose(v->main(()->verify.accept(screen().getMenu().equals("recycle"),"late cancel callback does not replace newer menu")))
   .thenCompose(v->open(0)).thenCompose(v->main(()->{
    click();var view=(InventoryView)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()){
     case "getTopInventory"->opened;case "getPlayer"->player;default->null;
    });app.getGui().close(new InventoryCloseEvent(view));
   })).thenCompose(v->until(()->!app.getTransactions().isBusy(player)))
   .thenCompose(v->main(()->verify.accept(screen()==before,"late cancel callback does not reopen closed menu")));
 }
}
