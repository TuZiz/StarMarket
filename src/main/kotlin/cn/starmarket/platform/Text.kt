package cn.starmarket.platform
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
object Text {
 fun render(s:String):Component=plain(MiniMessage.miniMessage().deserialize(s))
 fun plain(component:Component):Component {
  var result=component.decoration(TextDecoration.ITALIC,false).children(component.children().map{plain(it)})
  if(result is TranslatableComponent)result=result.arguments(result.arguments().map{argument->
   val value=argument.value();if(value is Component)net.kyori.adventure.text.TranslationArgument.component(plain(value))else argument
  })
  return result
 }
}
