package cn.starmarket

import cn.starmarket.gui.MenuLayout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MenuLayoutTest {
 @Test fun expandedBrowseUsesAllFourRows(){
  val layout=MenuLayout(listOf("T#M###F#H","#       #","#       #","#       #","#       #","P#S#####N"))
  assertEquals(28,layout.pageSize());assertEquals(10,layout.contentSlots.first());assertEquals(43,layout.contentSlots.last())
  assertEquals(0,layout.page(-1,29));assertEquals(1,layout.page(9,29));assertEquals(0,layout.page(2,0))
 }
 @Test fun compactConfirmDoesNotOverwritePayOrCancel(){
  val layout=MenuLayout(listOf("####I####","###Y#R###","#########"))
  assertTrue(layout.contentSlots.isEmpty());assertEquals(listOf(4),layout.slots('I'))
  assertEquals(listOf(12),layout.slots('Y'));assertEquals(listOf(14),layout.slots('R'))
 }
 @Test fun movedPreviewCannotOverwritePriceControl(){
  val layout=MenuLayout(listOf("####I####","#   P   #","# Y S R #","#########"))
  assertEquals(listOf(4),layout.slots('I'));assertEquals(listOf(13),layout.slots('P'))
  assertFalse(layout.contentSlots.contains(13));assertFalse(layout.contentSlots.contains(20))
 }
 @Test fun navigationSpacesAreNotContent(){
  val layout=MenuLayout(listOf("#########","#       #","#       #","#       #","#########","#PR   N##"))
  assertEquals(21,layout.pageSize());assertTrue(layout.contentSlots.all{it<36})
 }
 @Test fun invalidShapesFailBeforeOpeningInventory(){
  assertThrows(IllegalArgumentException::class.java){MenuLayout(listOf("########"))}
  assertThrows(IllegalArgumentException::class.java){MenuLayout(List(7){"#########"})}
 }
}
