import cn.starmarket.platform.Text;
import com.google.gson.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
public class RenderProbe {
 public static void main(String[] args) throws Exception {
  var input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8));
  String line;while((line=input.readLine())!=null)System.out.println(GsonComponentSerializer.gson().serialize(Text.INSTANCE.render(JsonParser.parseString(line).getAsString())));
 }
}
