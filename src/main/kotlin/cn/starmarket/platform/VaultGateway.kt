package cn.starmarket.platform
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import cn.starmarket.domain.MarketRejected
import java.util.UUID
import java.math.BigDecimal

/** Called on the server main thread; uncertain exceptions are deliberately not retried. */
class VaultGateway(val unit:BigDecimal) {
 private fun provider():Economy=Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider?.takeIf{it.isEnabled}?:throw MarketRejected("economy")
 fun available()=runCatching{provider().name}.getOrNull()
 private fun amount(api:Economy,n:Long):Double {
  val amount=unit.multiply(BigDecimal(n));if(n<=0||amount>BigDecimal("1000000000"))throw MarketRejected("price")
  val digits=api.fractionalDigits();if(digits>=0&&amount.stripTrailingZeros().scale()>digits)throw MarketRejected("precision")
  return amount.toDouble()
 }
 fun check(owner:UUID,n:Long,withdraw:Boolean){val api=provider();val value=amount(api,n);if(withdraw&&!api.has(Bukkit.getOfflinePlayer(owner),value))throw MarketRejected("balance")}
 fun transfer(owner:UUID,n:Long,withdraw:Boolean):Boolean {
  val api=provider();val value=amount(api,n);val player=Bukkit.getOfflinePlayer(owner)
  if(withdraw&&!api.has(player,value))return false
  val started=System.nanoTime()
  val result=if(withdraw)api.withdrawPlayer(player,value)else api.depositPlayer(player,value)
  if(System.nanoTime()-started>5_000_000_000L)throw IllegalStateException("Vault response exceeded 5s; result uncertain; reconcile provider ledger")
  return result.transactionSuccess()
 }
}
