import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;

/**
 * Mining data of the 26.x blocks, from the official 26.3 game: hardness, whether the correct tool is
 * needed for drops, and every block tag that contains the block (mineable/pickaxe, needs_stone_tool,
 * wool, leaves...). Tags are read from the jar's data pack, resolving nested tags.
 * Usage: java -cp <26.3 classpath> MiningDump.java want.txt client-26.3.jar future_block_mining.json
 */
public class MiningDump {
    public static void main(String[] a) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Set<String> want = new HashSet<>(Files.readAllLines(Path.of(a[0])));

        Map<String, List<String>> raw = new HashMap<>();
        try (JarFile jar = new JarFile(a[1])) {
            String prefix = "data/minecraft/tags/block/";
            for (var e : Collections.list(jar.entries())) {
                if (!e.getName().startsWith(prefix) || !e.getName().endsWith(".json")) continue;
                String tag = "minecraft:" + e.getName().substring(prefix.length(), e.getName().length() - 5);
                JsonObject o = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8)).getAsJsonObject();
                List<String> values = new ArrayList<>();
                for (JsonElement v : o.getAsJsonArray("values")) values.add(v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString());
                raw.put(tag, values);
            }
        }
        Map<String, Set<String>> resolved = new HashMap<>();
        for (String tag : raw.keySet()) resolved.put(tag, resolve(tag, raw, new HashSet<>()));

        StringBuilder out = new StringBuilder("{\n");
        boolean first = true;
        for (Block b : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (!want.contains(id)) continue;
            List<String> tags = new ArrayList<>();
            for (var e : resolved.entrySet()) if (e.getValue().contains("minecraft:" + id)) tags.add(e.getKey());
            Collections.sort(tags);
            if (!first) out.append(",\n");
            first = false;
            out.append(String.format(Locale.ROOT, "\"%s\":{\"hardness\":%s,\"requiresTool\":%b,\"tags\":[%s]}", id,
                    Float.toString(b.defaultDestroyTime()), b.defaultBlockState().requiresCorrectToolForDrops(),
                    String.join(",", tags.stream().map(t -> "\"" + t + "\"").toList())));
        }
        out.append("\n}\n");
        Files.writeString(Path.of(a[2]), out);
    }

    private static Set<String> resolve(String tag, Map<String, List<String>> raw, Set<String> seen) {
        Set<String> out = new HashSet<>();
        if (!seen.add(tag)) return out;
        for (String v : raw.getOrDefault(tag, List.of())) {
            if (v.startsWith("#")) out.addAll(resolve(v.substring(1), raw, seen));
            else out.add(v.contains(":") ? v : "minecraft:" + v);
        }
        return out;
    }
}
