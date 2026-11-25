import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.nio.file.*; import java.util.*;

public class BlockDump {
  static List<String> shapes = new ArrayList<>();
  static Map<String,Integer> shapeIdx = new HashMap<>();
  static int shape(VoxelShape s) {
    StringBuilder b = new StringBuilder("[");
    boolean f = true;
    for (AABB a : s.toAabbs()) { if (!f) b.append(','); f = false;
      b.append(String.format(Locale.ROOT, "[%s,%s,%s,%s,%s,%s]", r(a.minX), r(a.minY), r(a.minZ), r(a.maxX), r(a.maxY), r(a.maxZ))); }
    String k = b.append(']').toString();
    return shapeIdx.computeIfAbsent(k, kk -> { shapes.add(kk); return shapes.size() - 1; });
  }
  static String r(double d) { return Double.toString(Math.round(d * 1e6) / 1e6); }
  @SuppressWarnings({"rawtypes","unchecked"})
  static String val(BlockState s, Property p) { return p.getName(s.getValue(p)); }
  public static void main(String[] a) throws Exception {
    SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    Set<String> want = new HashSet<>(Files.readAllLines(Path.of(a[0])));
    StringBuilder out = new StringBuilder("{\"blocks\":{\n"); boolean firstBlock = true;
    for (Block b : BuiltInRegistries.BLOCK) {
      String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
      if (!want.contains(id)) continue;
      BlockState def = b.defaultBlockState();
      if (def.hasBlockEntity() && (id.contains("sign") || id.contains("shelf"))) { System.err.println("skip block entity " + id); continue; }
      if (!firstBlock) out.append(",\n"); firstBlock = false;
      out.append("\"").append(id).append("\":{\"offset\":").append(def.hasOffsetFunction()).append(",\"properties\":[");
      boolean fp = true;
      for (Property<?> p : b.getStateDefinition().getProperties()) {
        if (!fp) out.append(','); fp = false;
        out.append("[\"").append(p.getName()).append("\",[");
        boolean fv = true;
        for (Object v : p.getPossibleValues()) { if (!fv) out.append(','); fv = false; out.append('"').append(((Property) p).getName((Comparable) v)).append('"'); }
        out.append("],\"").append(val(def, p)).append("\"]");
      }
      out.append("],\"states\":[");
      boolean fs = true;
      for (BlockState s : b.getStateDefinition().getPossibleStates()) {
        if (!fs) out.append(','); fs = false;
        StringBuilder props = new StringBuilder();
        for (Property<?> p : b.getStateDefinition().getProperties()) { if (props.length() > 0) props.append(','); props.append(p.getName()).append('=').append(val(s, p)); }
        out.append(String.format(Locale.ROOT, "[%d,\"%s\",%d,%d,%d,%d,%d,%b,%b,%b]",
          Block.BLOCK_STATE_REGISTRY.getId(s), props,
          shape(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)),
          shape(s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)),
          shape(s.getOcclusionShape()),
          s.getLightEmission(), s.getLightDampening(), s.canOcclude(), s.propagatesSkylightDown(), s.useShapeForLightOcclusion()));
      }
      out.append("]}");
    }
    out.append("\n},\"shapes\":[\n").append(String.join(",\n", shapes)).append("\n],\"stateCount\":").append(Block.BLOCK_STATE_REGISTRY.size()).append("}\n");
    Files.writeString(Path.of(a[1]), out);
  }
}
