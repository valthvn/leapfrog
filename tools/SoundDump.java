import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import java.nio.file.*; import java.util.*;
public class SoundDump {
  public static void main(String[] a) throws Exception {
    SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    Set<String> want = new HashSet<>(Files.readAllLines(Path.of(a[0])));
    StringBuilder sb = new StringBuilder("{\n"); boolean first = true;
    for (var b : BuiltInRegistries.BLOCK) {
      String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
      if (!want.contains(id)) continue;
      SoundType s = b.defaultBlockState().getSoundType();
      if (!first) sb.append(",\n"); first = false;
      sb.append(String.format(Locale.ROOT, "  \"%s\": {\"volume\": %s, \"pitch\": %s, \"break\": \"%s\", \"step\": \"%s\", \"place\": \"%s\", \"hit\": \"%s\", \"fall\": \"%s\"}",
        id, s.getVolume(), s.getPitch(), s.getBreakSound().location(), s.getStepSound().location(), s.getPlaceSound().location(), s.getHitSound().location(), s.getFallSound().location()));
    }
    System.out.println(sb.append("\n}"));
  }
}
