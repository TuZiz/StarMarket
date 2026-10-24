import cn.starmarket.StarMarket;
import cn.starmarket.domain.*;
import cn.starmarket.platform.AutomaticDeliveries;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Isolated-server checks only; never included in the production JAR. */
public final class AutomaticDeliveryProbe {
 private final StarMarket app;private final JavaPlugin plugin;private final BiConsumer<Boolean,String> verify;
 private final UUID owner=UUID.randomUUID();private final Inventory bag=Bukkit.createInventory(null,36);
 private final Location feet;private final Player player;private ItemIdentity item;private Claim row;
 private boolean online=true,throwAfterAdd=false,disconnectOnSecondRead=false;private int onlineReads=0,calls=0;private String mode="ok";
 AutomaticDeliveryProbe(StarMarket app,JavaPlugin plugin,BiConsumer<Boolean,String> verify){
  this.app=app;this.plugin=plugin;this.verify=verify;feet=app.getServer().getWorlds().getFirst().getSpawnLocation().clone().add(0,1,0);
  feet.getChunk().addPluginChunkTicket(plugin);
  var inventory=(PlayerInventory)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PlayerInventory.class},(p,m,a)->switch(m.getName()){
   case "getStorageContents"->bag.getContents();case "addItem"->{var result=bag.addItem((ItemStack[])a[0]);if(throwAfterAdd)throw new IllegalStateException("simulated after inventory mutation");yield result;}
   default->null;
  });
  player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getUniqueId"->owner;case "getInventory"->inventory;case "getLocation"->feet.clone();case "hasPermission"->true;
   case "isOnline"->{onlineReads++;yield online&&(!disconnectOnSecondRead||onlineReads<2);}
   default->null;
  });
 }
 private CompletableFuture<Void> main(Runnable code){return app.main(()->{code.run();return null;});}
 private CompletableFuture<Claim> add(long quantity,long money){return app.db(ex->{
  ex.register(item);String id=UUID.randomUUID().toString();ex.getWarehouse().add(owner.toString(),item.getKey(),quantity,money,id);
  return ex.getWarehouse().ready(owner.toString()).stream().filter(c->c.getId().equals(id)).findFirst().orElseThrow();
 }).thenApply(c->{row=c;return c;});}
 private CompletableFuture<String> state(Claim claim){return app.db(ex->ex.getDb().query("SELECT state FROM claims WHERE id=?",new Object[]{claim.getId()},r->{try{return r.getString(1);}catch(java.sql.SQLException e){throw new RuntimeException(e);}}).getFirst());}
 private List<Item> drops(){return feet.getWorld().getNearbyEntities(feet,3,3,3).stream().filter(e->e instanceof Item i&&owner.equals(i.getOwner())).map(e->(Item)e).toList();}
 private void clean(){bag.clear();drops().forEach(Entity::remove);}
 public CompletableFuture<Void> run(){
  app.getDeliveries().stop();item=app.getTransactions().getCodec().identify(new ItemStack(Material.COBBLESTONE));
  Economy economy=(Economy)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Economy.class},(p,m,a)->switch(m.getName()){
   case "getName"->"AutoDeliveryTestEconomy";case "isEnabled","has"->true;case "fractionalDigits"->2;
   case "depositPlayer","withdrawPlayer"->{calls++;if(mode.equals("throw"))throw new IllegalStateException("simulated uncertain deposit");yield new EconomyResponse((double)a[1],1000,mode.equals("reject")?EconomyResponse.ResponseType.FAILURE:EconomyResponse.ResponseType.SUCCESS,"test");}
   default->m.getReturnType()==boolean.class?false:m.getReturnType()==double.class?1000.0:m.getReturnType()==String.class?"":null;
  });
  app.getServer().getServicesManager().register(Economy.class,economy,plugin,ServicePriority.Highest);
  return add(64,0).thenCompose(c->app.getTransactions().getDelivery().send(c,player)).thenCompose(v->main(()->{
   verify.accept(bag.getItem(0).getAmount()==64&&drops().isEmpty(),"automatic items fit backpack without drops");
  })).thenCompose(v->state(row)).thenCompose(s->main(()->{verify.accept(s.equals("DONE"),"inventory delivery receipt completed");clean();
   for(int i=0;i<36;i++)bag.setItem(i,new ItemStack(Material.DIRT,64));
  })).thenCompose(v->add(64,0)).thenCompose(c->app.getTransactions().getDelivery().send(c,player)).thenCompose(v->main(()->{
   verify.accept(drops().stream().mapToInt(e->e.getItemStack().getAmount()).sum()==64,"full backpack drops exact quantity at feet");
   verify.accept(drops().stream().allMatch(e->e.getItemStack().getType()==Material.COBBLESTONE),"overflow drops original material");
   clean();for(int i=0;i<36;i++)bag.setItem(i,new ItemStack(Material.DIRT,64));bag.setItem(0,new ItemStack(Material.COBBLESTONE,60));
  })).thenCompose(v->add(16,0)).thenCompose(c->app.getTransactions().getDelivery().send(c,player)).thenCompose(v->main(()->{
   verify.accept(bag.getItem(0).getAmount()==64&&drops().stream().mapToInt(e->e.getItemStack().getAmount()).sum()==12,"partial backpack space only drops overflow");clean();online=false;
  })).thenCompose(v->add(16,0)).thenCompose(c->app.getTransactions().getDelivery().send(c,player).handle((x,e)->{
   verify.accept(e!=null,"offline item effect rejected before reservation");return null;
  })).thenCompose(v->state(row)).thenCompose(s->main(()->{
   verify.accept(s.equals("READY")&&bag.isEmpty()&&drops().isEmpty(),"offline delivery remains queued without ground drops");online=true;onlineReads=0;disconnectOnSecondRead=true;
  })).thenCompose(v->app.getTransactions().getDelivery().send(row,player).handle((x,e)->{verify.accept(e!=null,"disconnect between reserve and effect rejected");return null;}))
   .thenCompose(v->state(row)).thenCompose(s->main(()->{
    verify.accept(s.equals("READY")&&bag.isEmpty(),"disconnect safely releases receipt without item loss");disconnectOnSecondRead=false;
   })).thenCompose(v->app.getTransactions().getDelivery().send(row,player)).thenCompose(v->main(()->{
    verify.accept(bag.getItem(0).getAmount()==16,"queued offline item delivered after reconnect");clean();
   })).thenCompose(v->add(0,100)).thenCompose(c->app.getTransactions().getDelivery().send(c,null)).thenCompose(v->state(row)).thenCompose(s->main(()->{
    verify.accept(s.equals("DONE")&&calls==1,"offline Vault proceeds paid once automatically");mode="reject";
   })).thenCompose(v->add(0,100)).thenCompose(c->app.getTransactions().getDelivery().send(c,null).handle((x,e)->{verify.accept(e!=null,"explicit Vault refusal reported");return null;}))
   .thenCompose(v->state(row)).thenCompose(s->main(()->{verify.accept(s.equals("READY"),"explicit Vault refusal restores pending receipt");mode="throw";}))
   .thenCompose(v->app.getTransactions().getDelivery().send(row,null).handle((x,e)->{verify.accept(e!=null,"uncertain Vault deposit reported");return null;}))
   .thenCompose(v->state(row)).thenCompose(s->main(()->{verify.accept(s.equals("FROZEN"),"uncertain Vault deposit frozen");}))
   .thenCompose(v->{int before=calls;return app.getTransactions().getDelivery().send(row,null).handle((x,e)->{verify.accept(e!=null&&calls==before,"frozen Vault receipt never reissued");return null;});})
   .thenCompose(v->main(()->{mode="ok";throwAfterAdd=true;})).thenCompose(v->add(16,0))
   .thenCompose(c->app.getTransactions().getDelivery().send(c,player).handle((x,e)->{verify.accept(e!=null,"post-inventory exception not ignored");return null;}))
   .thenCompose(v->state(row)).thenCompose(s->main(()->{
    verify.accept(s.equals("FROZEN")&&bag.getItem(0).getAmount()==16,"partial external mutation freezes instead of retrying items");throwAfterAdd=false;clean();
   })).thenCompose(v->add(16,0)).thenCompose(c->{
    var a=app.getTransactions().getDelivery().send(c,player);var b=app.getTransactions().getDelivery().send(c,player);
    return CompletableFuture.allOf(a.handle((x,e)->null),b.handle((x,e)->null));
   }).thenCompose(v->state(row)).thenCompose(s->main(()->{
    verify.accept(s.equals("DONE")&&bag.getItem(0).getAmount()==16,"concurrent dispatch produces only one item delivery");clean();
   })).thenCompose(v->customOverflow()).thenCompose(v->cancelledDrop()).thenCompose(v->automaticOfflineMoney())
   .whenComplete((v,e)->app.main(()->{clean();feet.getChunk().removePluginChunkTicket(plugin);app.getServer().getServicesManager().unregisterAll(plugin);return null;}));
 }
 private CompletableFuture<Void> customOverflow(){
  final ItemStack[] original={null};
  return main(()->{
   ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);var meta=sword.getItemMeta();
   meta.displayName(net.kyori.adventure.text.Component.text("Custom sword"));
   meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"delivery-variant"),org.bukkit.persistence.PersistentDataType.STRING,"RPG-A");
   sword.setItemMeta(meta);sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS,5);
   original[0]=sword;item=app.getTransactions().getCodec().identify(sword);
   for(int i=0;i<36;i++)bag.setItem(i,new ItemStack(Material.DIRT,64));
  }).thenCompose(v->add(10,0)).thenCompose(c->app.getTransactions().getDelivery().send(c,player))
   .thenCompose(v->main(()->{
    verify.accept(drops().stream().mapToInt(e->e.getItemStack().getAmount()).sum()==4,"nonstackable automatic delivery bounded to four stacks per receipt");
    verify.accept(drops().stream().allMatch(e->e.getItemStack().isSimilar(original[0])),"overflow preserves RPG name enchantment and PDC");
   })).thenCompose(v->app.db(ex->ex.getWarehouse().ready(owner.toString()).stream().filter(c->c.getGroup().equals(item.getKey())).mapToLong(Claim::getQuantity).sum()))
   .thenCompose(n->main(()->{verify.accept(n==6,"large RPG order remainder queued for bounded continuation");clean();item=app.getTransactions().getCodec().identify(new ItemStack(Material.COBBLESTONE));}));
 }
 private CompletableFuture<Void> cancelledDrop(){
  Listener cancel=new Listener(){@EventHandler public void spawn(ItemSpawnEvent event){if(event.getEntity().getWorld()==feet.getWorld()&&event.getEntity().getLocation().distanceSquared(feet)<4)event.setCancelled(true);}};
  return main(()->{for(int i=0;i<36;i++)bag.setItem(i,new ItemStack(Material.DIRT,64));app.getServer().getPluginManager().registerEvents(cancel,plugin);})
   .thenCompose(v->add(16,0)).thenCompose(c->app.getTransactions().getDelivery().send(c,player).handle((x,e)->{verify.accept(e!=null,"cancelled item spawn is a failed delivery");return null;}))
   .thenCompose(v->state(row)).thenCompose(s->main(()->{
    verify.accept(s.equals("FROZEN")&&drops().isEmpty(),"cancelled spawn frozen for reconciliation without blind item retry");HandlerList.unregisterAll(cancel);clean();
   }));
 }
 private CompletableFuture<Void> automaticOfflineMoney(){
  var dispatcher=new AutomaticDeliveries(app);final int[] before={0};
  return main(()->{before[0]=calls;dispatcher.start();})
   .thenCompose(v->add(0,100)).thenCompose(v->main(()->{dispatcher.wake(owner);dispatcher.wake(owner);}))
   .thenCompose(v->{var result=new CompletableFuture<Void>();app.getServer().getScheduler().runTaskLater(plugin,()->{
    state(row).whenComplete((s,e)->app.main(()->{
     try{if(e!=null)throw new RuntimeException(e);verify.accept(s.equals("DONE")&&calls==before[0]+1,"automatic offline payout dispatcher deduplicates repeated wakeups");result.complete(null);}catch(Throwable t){result.completeExceptionally(t);}finally{dispatcher.stop();}return null;
    }));
   },40);return result;});
 }
}
