package cn.starmarket.domain

import java.math.BigDecimal

data class ItemIdentity(val key:String,val product:String,val data:String,val label:String)
data class Order(val id:String,val owner:String,val product:String,val remaining:Long,val minimum:Long,
 val sold:Long,val income:Long,val group:String=product,val expires:Long=Long.MAX_VALUE,val created:Long=0)
data class Fill(val order:String,val owner:String,val quantity:Long,val gross:Long,val net:Long)
data class PriceTier(val unit:Long,val quantity:Long)
data class Quote(val id:String,val buyer:String,val product:String,val group:String,val quantity:Long,
 val total:Long,val fills:List<Fill>,val tiers:List<PriceTier>,val expires:Long,val fee:BigDecimal)
data class MarketOffer(val item:ItemIdentity,val stock:Long,val minimum:Long)
data class Claim(val id:String,val owner:String,val group:String,val quantity:Long,val money:Long)
class MarketRejected(val code:String):IllegalStateException(code)
