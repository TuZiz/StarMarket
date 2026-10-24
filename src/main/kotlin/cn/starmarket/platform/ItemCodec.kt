package cn.starmarket.platform
import cn.starmarket.config.Settings
import cn.starmarket.domain.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.Base64
import java.security.MessageDigest

/** All methods run on the Leaf/Purpur main thread, including Bukkit serialization. */
class ItemCodec(private val settings:()->Settings) {
 fun product(stack:ItemStack):Product=settings().products.values.firstOrNull{it.material==stack.type.name}?:throw MarketRejected("unsupported")
 fun identify(stack:ItemStack):ItemIdentity {
  if(stack.type.isAir||stack.amount !in 1..stack.maxStackSize)throw MarketRejected("unsupported")
  val p=product(stack);val clean=stack.isSimilar(ItemStack(stack.type))
  if(!clean&&!p.allowMetadata)throw MarketRejected("metadata")
  val one=stack.clone().also{it.amount=1};val data=one.serializeAsBytes()
  val hash=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
  val name=one.itemMeta?.let{it.displayName()?:it.itemName()}?.let{PlainTextComponentSerializer.plainText().serialize(it)}?:MiniMessage.miniMessage().stripTags(p.name)
  return ItemIdentity(if(clean)p.id else p.id+":"+hash,p.id,Base64.getEncoder().encodeToString(data),name)
 }
 fun legacy(p:Product)=ItemIdentity(p.id,p.id,Base64.getEncoder().encodeToString(ItemStack(Material.valueOf(p.material)).serializeAsBytes()),MiniMessage.miniMessage().stripTags(p.name))
 /** Render from current rules instead of the immutable historical label stored with stock. */
 fun label(item:ItemIdentity):String {
  if(item.key!=item.product){
   val meta=decode(item).itemMeta
   (meta.displayName()?:meta.itemName())?.let{return PlainTextComponentSerializer.plainText().serialize(it)}
  }
  return settings().products[item.product]?.name?.let{MiniMessage.miniMessage().stripTags(it)}?:item.label
 }
 fun decode(item:ItemIdentity):ItemStack=ItemStack.deserializeBytes(Base64.getDecoder().decode(item.data)).also{check(it.amount==1)}
 fun capacity(player:Player,item:ItemIdentity):Long {val sample=decode(item);return player.inventory.storageContents.sumOf{if(it==null||it.type.isAir)sample.maxStackSize.toLong() else if(it.isSimilar(sample))(it.maxStackSize-it.amount).coerceAtLeast(0).toLong()else 0}}
 fun checkSlot(player:Player,item:ItemIdentity,n:Long,slot:Int){
  if(slot !in 0..35)throw MarketRejected("selected-stack")
  val stack=player.inventory.getItem(slot)?:throw MarketRejected("selected-stack")
  if(n<=0||stack.amount.toLong()!=n||stack.amount>stack.maxStackSize||!stack.isSimilar(decode(item)))throw MarketRejected("selected-stack")
 }
 fun removeSlot(player:Player,item:ItemIdentity,n:Long,slot:Int){
  if(!player.isOnline||!player.hasPermission("starmarket.use"))throw MarketRejected("offline")
  checkSlot(player,item,n,slot)
  player.inventory.setItem(slot,null)
 }
 /** Main thread; any failure after the first effect must freeze the delivery receipt. */
 fun give(player:Player,item:ItemIdentity,n:Long):Long {
  if(!player.isOnline)throw MarketRejected("offline")
  require(n>0)
  val sample=decode(item);val location=player.location.clone();var left=n;var dropped=0L
  while(left>0){
   val take=minOf(left,sample.maxStackSize.toLong()).toInt()
   val overflow=player.inventory.addItem(sample.clone().also{it.amount=take})
   overflow.values.forEach{stack->
    val entity=location.world.dropItem(location,stack){it.owner=player.uniqueId;it.pickupDelay=0}
    check(entity.isValid&&!entity.isDead){"Item spawn rejected for owner=${player.uniqueId}"}
    dropped+=stack.amount
   }
   left-=take
  }
  return dropped
 }
 fun recyclable(item:ItemIdentity)=decode(item).isSimilar(ItemStack(Material.valueOf(settings().products.getValue(item.product).material)))&&settings().products.getValue(item.product).recycle
}
