package cn.starmarket.api
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID
/** Fired on the server main thread after durable completion. No Quest dependency. */
class MarketEvent(val action:String,val player:UUID,val product:String,val quantity:Long,val minorUnits:Long,val transaction:String):Event() {
 override fun getHandlers()=HANDLERS
 companion object {private val HANDLERS=HandlerList();@JvmStatic fun getHandlerList()=HANDLERS}
}
interface MarketAccess { fun openMarket(player:org.bukkit.entity.Player) }
