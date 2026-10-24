import cn.starmarket.StarMarket;
import cn.starmarket.domain.*;
import cn.starmarket.platform.*;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
public class RuntimeProbe extends JavaPlugin {
 private int checked=0; private StarMarket app; private String mode="ok";private int calls=0;
 private void verify(boolean value,String message){if(!value)throw new AssertionError(message);checked++;getLogger().info("PASS "+message);}
 private void reject(Runnable code,String message){boolean rejected=false;try{code.run();}catch(RuntimeException e){rejected=true;}verify(rejected,message);}
 public void onEnable(){app=(StarMarket)getServer().getPluginManager().getPlugin("StarMarket");getServer().getScheduler().runTaskTimer(this,task->{if(!app.getReady())return;task.cancel();prepare();},20,20);}
 private void prepare(){java.util.concurrent.CompletableFuture.runAsync(()->{try{
  Path path=app.getDataFolder().toPath().resolve("products/equipment/swords.yml");Files.createDirectories(path.getParent());Files.writeString(path,"schema-version: 1\nproducts:\n  sword:\n    name: '<#88CCFF>测试装备'\n    material: 'DIAMOND_SWORD'\n    category: '装备'\n    reference-price: 100\n    min-price: 1\n    max-price: 10000\n    recycle-price: 0\n    recycle-enabled: false\n    allow-metadata: true\n");
 }catch(Exception e){throw new RuntimeException(e);}}).thenCompose(x->app.reload()).whenComplete((x,error)->getServer().getScheduler().runTask(this,()->{try{if(error!=null)throw new RuntimeException(error);runChecks();new ListingRefreshProbe(app,this,this::verify).run().thenCompose(v->app.main(()->new AutomaticDeliveryProbe(app,this,this::verify).run()).thenCompose(f->f)).whenComplete((v,e)->getServer().getScheduler().runTask(this,()->finish(e)));}catch(Throwable e){finish(e);}}));}
 private void runChecks(){
  var codec=app.getTransactions().getCodec();
  verify(app.getSettings().getFee().signum()==0,"no settlement tax configured");
  verify(app.getSettings().getListingTaxRate().compareTo(new java.math.BigDecimal("0.03"))==0,"upfront listing tax rate migrated to 3 percent");
  verify(app.getSettings().getTrading().getInt("schema-version")==2,"trading configuration migrated to schema2");
  verify(app.getSettings().getLang().getInt("schema-version")==2,"automatic delivery language migrated to schema2");
  verify(Files.exists(app.getDataFolder().toPath().resolve("lang/zh_CN.yml.v1-auto-delivery.bak")),"automatic delivery language original preserved in backup");
  verify(Files.exists(app.getDataFolder().toPath().resolve("products/cobblestone.yml")),"legacy products migrated");
  verify(Files.exists(app.getDataFolder().toPath().resolve("products.yml.pre-catalog.bak")),"legacy configuration backup exists");
  verify(app.getSettings().getProducts().containsKey("sword"),"nested YAML catalog loaded on reload");
  ItemStack[] selectedStorage=new ItemStack[36];selectedStorage[3]=new ItemStack(Material.COBBLESTONE,32);selectedStorage[4]=new ItemStack(Material.COBBLESTONE,64);
  var inventory=(org.bukkit.inventory.PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.inventory.PlayerInventory.class},(proxy,method,args)->switch(method.getName()){
   case "getItem"->selectedStorage[(int)args[0]];case "setItem"->{selectedStorage[(int)args[0]]=(ItemStack)args[1];yield null;}case "getStorageContents"->selectedStorage;default->null;
  });
  var selectedPlayer=(org.bukkit.entity.Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)->switch(method.getName()){
   case "getInventory"->inventory;case "isOnline","hasPermission"->true;default->null;
  });
  var selectedIdentity=codec.identify(selectedStorage[3]);codec.checkSlot(selectedPlayer,selectedIdentity,32,3);codec.removeSlot(selectedPlayer,selectedIdentity,32,3);
  verify(selectedStorage[3]==null,"selected stack fully removed");verify(selectedStorage[4].getAmount()==64,"matching other slot untouched");
  selectedStorage[3]=new ItemStack(Material.COBBLESTONE,12);reject(()->codec.removeSlot(selectedPlayer,selectedIdentity,32,3),"changed selected quantity rejected");verify(selectedStorage[3].getAmount()==12&&selectedStorage[4].getAmount()==64,"rejection never combines inventory stacks");
  selectedStorage[3]=new ItemStack(Material.DIRT,32);reject(()->codec.removeSlot(selectedPlayer,selectedIdentity,32,3),"changed selected identity rejected");
  selectedStorage[3]=null;selectedStorage[5]=new ItemStack(Material.COBBLESTONE,32);reject(()->codec.removeSlot(selectedPlayer,selectedIdentity,32,3),"moved selected stack rejected");verify(selectedStorage[5].getAmount()==32,"moved item never silently consumed");
  verify(app.getGui().getAssets().getConfigs().get("stack-sale").getConfigurationSection("elements").getKeys(false).equals(Set.of("#","I","P","Y","S","R")),"sale GUI has no quantity controls");
  var a=codec.identify(new ItemStack(Material.COBBLESTONE,64));var b=codec.identify(new ItemStack(Material.COBBLESTONE,1));
  verify(a.equals(b),"normal stacks merge regardless of amount");
  verify(codec.label(a).equals("圆石"),"missing legacy cobblestone name falls back to Chinese resource name");
  verify(codec.label(codec.identify(new ItemStack(Material.OAK_LOG))).equals("橡木原木"),"missing legacy oak log name falls back to Chinese resource name");
  verify(codec.label(codec.identify(new ItemStack(Material.IRON_INGOT))).equals("铁锭"),"missing legacy iron ingot name falls back to Chinese resource name");
  var oldLabel=new ItemIdentity(a.getKey(),a.getProduct(),a.getData(),"cobblestone");
  verify(codec.label(oldLabel).equals("圆石"),"historical English item labels render current Chinese product name");
  verify(oldLabel.getKey().equals(a.getKey())&&oldLabel.getData().equals(a.getData()),"display label resolution preserves stock key and serialized data");
  verify(codec.label(codec.identify(new ItemStack(Material.DIAMOND_SWORD))).equals("测试装备"),"explicit configured product name overrides built-in fallback");
  ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);var meta=sword.getItemMeta();meta.displayName(Component.text("RPG Sword").decorate(TextDecoration.ITALIC));meta.lore(List.of(Component.text("Attributes").decorate(TextDecoration.ITALIC)));meta.getPersistentDataContainer().set(new NamespacedKey(this,"item"),PersistentDataType.STRING,"variant-A");sword.setItemMeta(meta);sword.addUnsafeEnchantment(Enchantment.SHARPNESS,5);
  var identity=codec.identify(sword);verify(codec.decode(identity).isSimilar(sword),"full enchantment/name/lore/PDC roundtrip");
  verify(codec.label(identity).equals("RPG Sword"),"custom RPG display name preserved instead of product name");
  ItemStack customNameSword=new ItemStack(Material.DIAMOND_SWORD);var customMeta=customNameSword.getItemMeta();customMeta.itemName(Component.text("星陨长剑"));customNameSword.setItemMeta(customMeta);
  var customNameIdentity=codec.identify(customNameSword);
  verify(codec.label(customNameIdentity).equals("星陨长剑")&&customNameIdentity.getLabel().equals("星陨长剑"),"custom item_name component preserved in GUI and new identity label");
  ItemStack other=sword.clone();var changed=other.getItemMeta();changed.getPersistentDataContainer().set(new NamespacedKey(this,"item"),PersistentDataType.STRING,"variant-B");other.setItemMeta(changed);
  verify(!codec.identify(other).getKey().equals(identity.getKey()),"different custom data never merged");
  verify(!codec.recyclable(identity),"RPG equipment cannot system recycle");
  ItemStack forged=new ItemStack(Material.COBBLESTONE);var renamed=forged.getItemMeta();renamed.displayName(Component.text("Fake"));forged.setItemMeta(renamed);reject(()->codec.identify(forged),"modified ordinary resource rejected");
  verify(Text.INSTANCE.render("<#88CCFF>A<italic>B</italic>").children().stream().allMatch(c->c.decoration(TextDecoration.ITALIC)==TextDecoration.State.FALSE),"rendered children non italic");
  var translated=(net.kyori.adventure.text.TranslatableComponent)Text.INSTANCE.plain(Component.translatable("test",Component.text("Arg").decorate(TextDecoration.ITALIC)));
  verify(((Component)translated.arguments().getFirst().value()).decoration(TextDecoration.ITALIC)==TextDecoration.State.FALSE,"translation arguments non italic");
  Economy economy=(Economy)Proxy.newProxyInstance(getClassLoader(),new Class[]{Economy.class},(proxy,method,args)->switch(method.getName()){
   case "getName"->"StarMarketTestEconomy";case "isEnabled"->true;case "fractionalDigits"->2;case "has"->!mode.equals("poor");
   case "withdrawPlayer","depositPlayer"->{calls++;if(mode.equals("throw"))throw new IllegalStateException("simulated uncertain provider");yield new EconomyResponse((double)args[1],1000,mode.equals("reject")?EconomyResponse.ResponseType.FAILURE:EconomyResponse.ResponseType.SUCCESS,"test");}
   default->method.getReturnType()==boolean.class?false:method.getReturnType()==double.class?1000.0:method.getReturnType()==String.class?"":null;
  });
  getServer().getServicesManager().register(Economy.class,economy,this,ServicePriority.Highest);var uuid=UUID.randomUUID();
  app.getVault().check(uuid,100,true);verify(app.getVault().transfer(uuid,100,true),"Vault withdrawal success result");
  mode="poor";int prior=calls;reject(()->app.getVault().check(uuid,100,true),"balance failure before economic effect");verify(calls==prior,"balance failure made no withdrawal call");
  mode="reject";verify(!app.getVault().transfer(uuid,100,false),"Vault explicit failure preserved");mode="throw";reject(()->app.getVault().transfer(uuid,100,true),"Vault uncertain exception not swallowed");getServer().getServicesManager().unregisterAll(this);
  verify(app.getGui().getAssets().getConfigs().size()==8&&!app.getGui().getAssets().getConfigs().containsKey("warehouse"),"eight GUI shapes loaded without warehouse");
  var gui=app.getGui();var guiId=UUID.randomUUID();var opened=new org.bukkit.inventory.Inventory[1];
  var viewer=(org.bukkit.entity.Player)Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)->switch(method.getName()){
   case "getUniqueId"->guiId;case "getInventory"->inventory;case "isOnline","hasPermission"->true;
   case "openInventory"->{opened[0]=(org.bukkit.inventory.Inventory)args[0];yield null;}
   default->null;
  });
  selectedStorage[3]=new ItemStack(Material.COBBLESTONE,32);gui.getSale().choose(viewer,selectedStorage[3],3);
  var saleHolder=(cn.starmarket.gui.MarketGui.Screen)opened[0].getHolder();
  verify(opened[0].getItem(4).getType()==Material.COBBLESTONE,"sale preview follows moved I symbol");
  verify(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(opened[0].getItem(4).getItemMeta().displayName()).contains("圆石"),"sale confirmation preview displays Chinese item name");
  verify(opened[0].getItem(13).getType()==Material.NAME_TAG&&saleHolder.getActions().containsKey(13),"price button survives moved preview");
  var quote=new Quote(UUID.randomUUID().toString(),guiId.toString(),a.getProduct(),a.getKey(),2,300,List.of(),List.of(new PriceTier(100,1),new PriceTier(200,1)),System.currentTimeMillis()+15000,java.math.BigDecimal.ZERO);
  gui.confirm(viewer,quote,oldLabel,0);var confirmHolder=(cn.starmarket.gui.MarketGui.Screen)opened[0].getHolder();
  verify(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(opened[0].getItem(4).getItemMeta().displayName()).contains("圆石"),"purchase summary translates historical English labels");
  verify(opened[0].getSize()==27,"edited compact purchase shape retained");
  verify(opened[0].getItem(12).getType()==Material.LIME_CONCRETE&&confirmHolder.getActions().containsKey(12),"purchase button not overwritten by tier rows");
  verify(opened[0].getItem(14).getType()==Material.RED_CONCRETE&&confirmHolder.getActions().containsKey(14),"cancel button not overwritten by tier rows");
  verify(confirmHolder.getActions().size()==2,"removed purchase page controls never regenerated");
  var lines=opened[0].getItem(4).getItemMeta().lore();
  verify(lines!=null&&lines.size()==app.getGui().getAssets().getConfigs().get("purchase").getStringList("elements.I.lore").size()+2,"compact summary retains all price tiers");
  verify(gui.getAssets().getLayouts().get("browse").pageSize()==28,"browse honors expanded 28 content cells");
  gui.clear();
 }
 private void finish(Throwable error){try{String result=error==null?"PASSED "+checked:"FAILED "+error;Files.writeString(Path.of("runtime-result.txt"),result);if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"Runtime validation failed",error);else getLogger().info(result);}catch(Exception e){e.printStackTrace();}getServer().shutdown();}
}
