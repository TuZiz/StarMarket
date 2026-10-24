package cn.starmarket.domain
import java.math.BigDecimal
import java.math.RoundingMode

data class Product(val id:String,val material:String,val base:Long,val floor:Long,val ceiling:Long,val salvage:Long,val sensitivity:Double,val maxChange:Double,val target:Long,val name:String=id,val category:String="资源",val allowMetadata:Boolean=false,val recycle:Boolean=true)
data class MarketState(val price:Long,val supply:Double,val demand:Double,val updated:Long)
object Pricing {
 fun next(p:Product,s:MarketState,stock:Long,buy:Long=0,supply:Long=0,now:Long=System.currentTimeMillis()):MarketState {
  val decay=kotlin.math.exp(-(now-s.updated).coerceAtLeast(0)/3600000.0)
  val offer=s.supply*decay+supply; val demand=s.demand*decay+buy
  val signal=((demand-offer)/(p.target+offer+demand)+(p.target-stock).toDouble()/(p.target+stock)).coerceIn(-1.0,1.0)
  val desired=p.base*(1+p.sensitivity*signal)
  val step=(p.maxChange*(buy+supply).toDouble()/(p.target+buy+supply)).coerceIn(0.0,p.maxChange)
  val price=desired.coerceIn(s.price*(1-step),s.price*(1+step)).toLong().coerceIn(p.floor,p.ceiling)
  return MarketState(price,offer,demand,now)
 }
 // Whole listing basis, rounded up to currency unit: splitting cannot evade sub-unit tax.
 fun listingTax(amount:Long,rate:BigDecimal):Long {
  require(amount>=0&&rate>=BigDecimal.ZERO&&rate<BigDecimal.ONE)
  return BigDecimal(amount).multiply(rate).setScale(0,RoundingMode.CEILING).longValueExact()
 }
 fun fee(amount:Long,rate:BigDecimal)=BigDecimal(amount).multiply(rate).setScale(0,RoundingMode.DOWN).longValueExact()
}
