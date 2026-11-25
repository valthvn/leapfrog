package dev.leapfrog.baby;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leapfrog.Leapfrog;
import dev.leapfrog.Config;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 26.x baby mobs. Layers come from {@code baby_layers.json} (tools/LayerDump.java) and textures from
 * {@code baby_textures.json}. A 1.21.11 layer is only replaced when the new part tree still has every
 * part the old model class animates.
 */
public final class BabyModels {
    private static @Nullable Map<String, JsonObject> layers;
    private static final Map<Identifier, Identifier> TEXTURES = new HashMap<>();
    private static final ThreadLocal<Boolean> RENDERING_BABY = ThreadLocal.withInitial(() -> false);
    private static final Set<String> ARMOUR_LAYERS = Set.of("helmet", "chestplate", "leggings", "boots");
    // Rebuilt from scratch in 26.x, so the 1.21.11 animations can't drive them.
    private static final Set<String> RESTRUCTURED = Set.of("axolotl", "donkey", "mule", "rabbit");
    private static int replaced;
    private static volatile Map<Identifier, Identifier> activeTextures = Map.of();

    private BabyModels() {}

    public static boolean available() {
        return load() && !layers.isEmpty();
    }

    private static synchronized boolean load() {
        if (layers != null) return true;
        layers = new LinkedHashMap<>();
        try (InputStream in = BabyModels.class.getResourceAsStream("/assets/leapfrog/baby_layers.json");
             InputStream tex = BabyModels.class.getResourceAsStream("/assets/leapfrog/baby_textures.json")) {
            if (in == null || tex == null) return false;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                String layer = e.getKey().substring(e.getKey().indexOf('#') + 1);
                // These need the 26.x equipment textures.
                if (ARMOUR_LAYERS.contains(layer)) continue;
                layers.put(e.getKey(), e.getValue().getAsJsonObject());
            }
            JsonObject t = JsonParser.parseReader(new InputStreamReader(tex, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                TEXTURES.put(Identifier.withDefaultNamespace(e.getKey()), Identifier.withDefaultNamespace(e.getValue().getAsString()));
            }
        } catch (Exception ex) {
            Leapfrog.LOG.error("Could not load 26.x baby models", ex);
        }
        return true;
    }

    /** Called on every model reload. */
    public static Map<ModelLayerLocation, LayerDefinition> patch(Map<ModelLayerLocation, LayerDefinition> roots) {
        if (!Config.babyModels || !load()) return roots;
        Map<ModelLayerLocation, LayerDefinition> out = new HashMap<>(roots);
        replaced = 0;
        int skipped = 0;
        Set<String> families = new HashSet<>();
        for (Map.Entry<ModelLayerLocation, LayerDefinition> e : roots.entrySet()) {
            JsonObject json = layers.get(e.getKey().model() + "#" + e.getKey().layer());
            if (json == null) continue;
            try {
                String model = e.getKey().model().getPath();
                if (RESTRUCTURED.stream().anyMatch(model::startsWith)) {
                    skipped++;
                    continue;
                }
                Set<String> oldPaths = paths(e.getValue());
                MeshDefinition mesh = buildMesh(json);
                Set<String> missing = new TreeSet<>(oldPaths);
                missing.removeAll(paths(LayerDefinition.create(mesh, 64, 64)));
                // Parts 26.x dropped (horns, hat rim...) become empty so the old model class still finds them.
                for (String path : missing) addPlaceholder(mesh.getRoot(), path);
                LayerDefinition future = LayerDefinition.create(mesh, json.getAsJsonArray("tex").get(0).getAsInt(),
                        json.getAsJsonArray("tex").get(1).getAsInt());
                if (paths(future).containsAll(oldPaths)) {
                    out.put(e.getKey(), future);
                    replaced++;
                    String family = model.substring(0, model.indexOf("_baby"));
                    families.add(family);
                    if (family.contains("_")) families.add(family.substring(family.lastIndexOf('_') + 1));
                } else {
                    skipped++;
                    Leapfrog.LOG.info("Baby layer {} kept (incompatible part tree)", e.getKey());
                }
            } catch (Exception ex) {
                skipped++;
                Leapfrog.LOG.warn("Baby layer {} not applied", e.getKey(), ex);
            }
        }
        Map<Identifier, Identifier> active = new HashMap<>();
        for (Map.Entry<Identifier, Identifier> t : TEXTURES.entrySet()) {
            Identifier adult = t.getKey();
            if (!families.contains(family(adult))) continue;
            if (RESTRUCTURED.stream().anyMatch(r -> adult.getPath().contains(r))) continue;
            active.put(adult, t.getValue());
        }
        activeTextures = active;
        Leapfrog.LOG.info("26.x baby models: {} layers applied, {} kept (incompatible part tree)", replaced, skipped);
        return out;
    }

    private static MeshDefinition buildMesh(JsonObject json) {
        MeshDefinition mesh = new MeshDefinition();
        JsonObject root = json.getAsJsonObject("root");
        for (Map.Entry<String, JsonElement> c : root.getAsJsonObject("children").entrySet()) {
            addPart(mesh.getRoot(), c.getKey(), c.getValue().getAsJsonObject());
        }
        return mesh;
    }

    // children, cubes and the CubeDefinition constructor are opened by leapfrog.accesswidener.
    private static void addPlaceholder(PartDefinition root, String path) {
        PartDefinition current = root;
        for (String name : path.substring(1).split("/")) {
            PartDefinition next = current.children.get(name);
            if (next == null) next = current.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
            current = next;
        }
    }

    private static void addPart(PartDefinition parent, String name, JsonObject json) {
        CubeListBuilder builder = CubeListBuilder.create();
        List<CubeDefinition> cubes = builder.cubes;
        for (JsonElement el : json.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            float[] o = floats(c.getAsJsonArray("o")), d = floats(c.getAsJsonArray("d")), g = floats(c.getAsJsonArray("g"));
            float[] uv = floats(c.getAsJsonArray("uv")), ts = floats(c.getAsJsonArray("ts"));
            Set<Direction> faces = EnumSet.noneOf(Direction.class);
            for (JsonElement f : c.getAsJsonArray("faces")) faces.add(Direction.valueOf(f.getAsString().toUpperCase(Locale.ROOT)));
            cubes.add(new CubeDefinition(null, uv[0], uv[1], o[0], o[1], o[2], d[0], d[1], d[2],
                    new CubeDeformation(g[0], g[1], g[2]), c.get("m").getAsBoolean(), ts[0], ts[1], faces));
        }
        float[] p = floats(json.getAsJsonArray("pose"));
        PartDefinition part = parent.addOrReplaceChild(name, builder, new PartPose(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]));
        for (Map.Entry<String, JsonElement> c : json.getAsJsonObject("children").entrySet()) {
            addPart(part, c.getKey(), c.getValue().getAsJsonObject());
        }
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** Part paths of a layer, like "/body/head/ear". */
    private static Set<String> paths(LayerDefinition layer) {
        Set<String> out = new HashSet<>();
        collect(layer.bakeRoot(), "", out);
        return out;
    }

    private static void collect(ModelPart part, String prefix, Set<String> out) {
        for (Map.Entry<String, ModelPart> e : part.children.entrySet()) {
            String path = prefix + "/" + e.getKey();
            out.add(path);
            collect(e.getValue(), path, out);
        }
    }

    public static void beginEntity(boolean baby) {
        RENDERING_BABY.set(baby && Config.babyModels && replaced > 0);
    }

    public static void endEntity() {
        RENDERING_BABY.set(false);
    }

    /** Swaps an adult texture for the 26.x baby one while a baby is being rendered. */
    public static Identifier texture(Identifier id) {
        if (!RENDERING_BABY.get()) return id;
        Identifier baby = activeTextures.get(id);
        return baby != null ? baby : id;
    }

    /** "textures/entity/cow/cow_cold.png" -> "cow"; "textures/entity/armadillo.png" -> "armadillo". */
    private static String family(Identifier id) {
        String p = id.getPath().substring("textures/entity/".length());
        int slash = p.indexOf('/');
        if (slash > 0) return p.substring(0, slash);
        return p.endsWith(".png") ? p.substring(0, p.length() - 4) : p;
    }
}
