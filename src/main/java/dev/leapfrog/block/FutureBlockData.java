package dev.leapfrog.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Reads {@code future_blocks.json}: properties, network ids, shapes and light of every 26.x block state. */
public final class FutureBlockData {
    public record StateInfo(int futureId, String key, VoxelShape collision, VoxelShape outline, VoxelShape occlusion,
                            int lightEmission, int lightDampening, boolean canOcclude, boolean propagatesSkylight,
                            boolean useShapeForLightOcclusion) {}

    public record BlockInfo(String name, boolean offset, List<StringProperty> properties, Map<String, String> defaults,
                            Map<String, StateInfo> states) {
        public StateInfo state(String key) {
            return states.get(key);
        }
    }

    private final Map<String, BlockInfo> blocks;

    private FutureBlockData(Map<String, BlockInfo> blocks) {
        this.blocks = blocks;
    }

    public Map<String, BlockInfo> blocks() {
        return blocks;
    }

    public static FutureBlockData load() {
        try (InputStream in = FutureBlockData.class.getResourceAsStream("/assets/leapfrog/future_blocks.json")) {
            if (in == null) throw new IllegalStateException("future_blocks.json missing");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<VoxelShape> shapes = new ArrayList<>();
            for (JsonElement s : root.getAsJsonArray("shapes")) shapes.add(shape(s.getAsJsonArray()));

            Map<String, BlockInfo> blocks = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("blocks").entrySet()) {
                JsonObject b = e.getValue().getAsJsonObject();
                List<StringProperty> props = new ArrayList<>();
                Map<String, String> defaults = new HashMap<>();
                for (JsonElement p : b.getAsJsonArray("properties")) {
                    JsonArray arr = p.getAsJsonArray();
                    List<String> values = new ArrayList<>();
                    for (JsonElement v : arr.get(1).getAsJsonArray()) values.add(v.getAsString());
                    props.add(new StringProperty(arr.get(0).getAsString(), values));
                    defaults.put(arr.get(0).getAsString(), arr.get(2).getAsString());
                }
                Map<String, StateInfo> states = new HashMap<>();
                for (JsonElement s : b.getAsJsonArray("states")) {
                    JsonArray a = s.getAsJsonArray();
                    StateInfo info = new StateInfo(a.get(0).getAsInt(), a.get(1).getAsString(),
                            shapes.get(a.get(2).getAsInt()), shapes.get(a.get(3).getAsInt()), shapes.get(a.get(4).getAsInt()),
                            a.get(5).getAsInt(), a.get(6).getAsInt(), a.get(7).getAsBoolean(), a.get(8).getAsBoolean(), a.get(9).getAsBoolean());
                    states.put(info.key(), info);
                }
                blocks.put(e.getKey(), new BlockInfo(e.getKey(), b.get("offset").getAsBoolean(), props, defaults, states));
            }
            return new FutureBlockData(blocks);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load future block data", ex);
        }
    }

    private static VoxelShape shape(JsonArray boxes) {
        VoxelShape shape = Shapes.empty();
        for (JsonElement b : boxes) {
            JsonArray a = b.getAsJsonArray();
            shape = Shapes.or(shape, Shapes.box(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                    a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble()));
        }
        return shape.optimize();
    }
}
