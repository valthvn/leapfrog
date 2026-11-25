import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class LayerDump {
  static Object get(Object o, String name) throws Exception {
    for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
      try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException e) {}
    }
    throw new NoSuchFieldException(o.getClass() + "." + name);
  }
  static String f(float v) { return Float.toString(v); }
  static void pose(StringBuilder sb, PartPose p) {
    sb.append("[").append(f(p.x())).append(',').append(f(p.y())).append(',').append(f(p.z())).append(',')
      .append(f(p.xRot())).append(',').append(f(p.yRot())).append(',').append(f(p.zRot())).append(',')
      .append(f(p.xScale())).append(',').append(f(p.yScale())).append(',').append(f(p.zScale())).append("]");
  }
  static void part(StringBuilder sb, PartDefinition pd) throws Exception {
    sb.append("{\"pose\":"); pose(sb, (PartPose) get(pd, "partPose"));
    sb.append(",\"cubes\":[");
    boolean first = true;
    for (Object cube : (List<?>) get(pd, "cubes")) {
      if (!first) sb.append(','); first = false;
      org.joml.Vector3fc o = (org.joml.Vector3fc) get(cube, "origin");
      org.joml.Vector3fc d = (org.joml.Vector3fc) get(cube, "dimensions");
      CubeDeformation g = (CubeDeformation) get(cube, "grow");
      Object uv = get(cube, "texCoord"); Object ts = get(cube, "texScale");
      Set<?> faces = (Set<?>) get(cube, "visibleFaces");
      sb.append("{\"o\":[").append(o.x()).append(',').append(o.y()).append(',').append(o.z()).append("],\"d\":[")
        .append(d.x()).append(',').append(d.y()).append(',').append(d.z()).append("],\"g\":[")
        .append(get(g, "growX")).append(',').append(get(g, "growY")).append(',').append(get(g, "growZ")).append("],\"m\":")
        .append(get(cube, "mirror")).append(",\"uv\":[").append(get(uv, "u")).append(',').append(get(uv, "v")).append("],\"ts\":[")
        .append(get(ts, "u")).append(',').append(get(ts, "v")).append("],\"faces\":[");
      boolean ff = true;
      for (Object face : faces) { if (!ff) sb.append(','); ff = false; sb.append('"').append(face.toString().toLowerCase()).append('"'); }
      sb.append("]}");
    }
    sb.append("],\"children\":{");
    first = true;
    for (Map.Entry<?, ?> e : ((Map<?, ?>) get(pd, "children")).entrySet()) {
      if (!first) sb.append(','); first = false;
      sb.append('"').append(e.getKey()).append("\":");
      part(sb, (PartDefinition) e.getValue());
    }
    sb.append("}}");
  }
  public static void main(String[] a) throws Exception {
    SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    Map<ModelLayerLocation, LayerDefinition> roots = LayerDefinitions.createRoots();
    StringBuilder sb = new StringBuilder("{\n"); boolean first = true; int n = 0;
    for (Map.Entry<ModelLayerLocation, LayerDefinition> e : roots.entrySet()) {
      String key = e.getKey().model().toString() + "#" + e.getKey().layer();
      if (!key.contains("baby")) continue;
      LayerDefinition ld = e.getValue();
      MeshDefinition mesh = (MeshDefinition) get(ld, "mesh");
      Object mat = get(ld, "material");
      if (!first) sb.append(",\n"); first = false;
      sb.append('"').append(key).append("\":{\"tex\":[").append(get(mat, "xTexSize")).append(',').append(get(mat, "yTexSize")).append("],\"root\":");
      part(sb, mesh.getRoot());
      sb.append('}'); n++;
    }
    Files.writeString(Path.of(a[0]), sb.append("\n}\n"));
    System.out.println("DUMPED " + n);
  }
}
