package cn.starmarket.gui

/** Shape is authoritative: content never overwrites configured controls or borders. */
class MenuLayout(val shape:List<String>) {
 init {require(shape.size in 1..6&&shape.all{it.length==9}){"Shape requires 1-6 rows of 9 characters"}}
 private val cells=shape.joinToString("")
 val contentSlots=cells.indices.filter{it/9 in 1 until shape.lastIndex&&cells[it]==' '}
 fun slots(symbol:Char)=cells.indices.filter{cells[it]==symbol}
 fun pageSize()=contentSlots.size.also{require(it>0){"List menu requires empty content cells between first and last rows"}}
 fun page(requested:Int,count:Int)=requested.coerceIn(0,((count-1)/pageSize()).coerceAtLeast(0))
}
