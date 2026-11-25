package dev.leapfrog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/** All user settings, stored in {@code config/leapfrog.json}. */
public final class Config {
    // Visuals
    public static boolean cushionModel = true;
    public static boolean cushionSeat = true;
    public static boolean futureItems = true;
    public static boolean redrawnTextures = true;
    public static boolean babyModels = true;
    public static boolean sulfurCube = true;
    // Sounds
    public static boolean cushionSounds = true;
    public static boolean placeSounds = true;
    public static boolean futureSounds = true;
    // Blocks, need a 26.3 connection through ViaFabric
    public static boolean restoreBlocks = true;
    public static boolean autoConnect26 = true;
    public static List<String> autoConnectHosts = new ArrayList<>(List.of("donutsmp.net", "*.donutsmp.net"));
    // When false, unlisted servers are joined with ViaFabric turned off.
    public static boolean viaOtherServers = false;
    public static boolean geyserEffects = true;
    public static boolean geyserLaunch = true;
    // Safety
    public static boolean safeMode = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("leapfrog.json");
    }

    /** Safe mode never lets the mod move the player. */
    public static boolean geyserLaunchActive() {
        return geyserLaunch && !safeMode;
    }

    public static void load() {
        Path file = file();
        // Settings saved before the mod was renamed to Leapfrog.
        Path old = file.resolveSibling("backtothefuture.json");
        if (!Files.exists(file) && Files.exists(old)) {
            try {
                Files.move(old, file);
            } catch (Exception e) {
                Leapfrog.LOG.warn("Could not move {} to {}", old, file, e);
            }
        }
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                JsonObject o = GSON.fromJson(r, JsonObject.class);
                if (o != null) read(o);
            } catch (Exception e) {
                Leapfrog.LOG.warn("Could not read {}, using defaults", file, e);
            }
        }
        save();
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }

    private static void read(JsonObject o) {
        cushionModel = bool(o, "cushion_model", cushionModel);
        cushionSeat = bool(o, "cushion_seat", cushionSeat);
        futureItems = bool(o, "future_items", futureItems);
        redrawnTextures = bool(o, "redrawn_textures", redrawnTextures);
        babyModels = bool(o, "baby_models", babyModels);
        sulfurCube = bool(o, "sulfur_cube", sulfurCube);
        cushionSounds = bool(o, "cushion_sounds", cushionSounds);
        placeSounds = bool(o, "place_sounds", placeSounds);
        futureSounds = bool(o, "future_sounds", futureSounds);
        restoreBlocks = bool(o, "restore_blocks", restoreBlocks);
        autoConnect26 = bool(o, "auto_connect_26", autoConnect26);
        viaOtherServers = bool(o, "via_other_servers", viaOtherServers);
        geyserEffects = bool(o, "geyser_effects", geyserEffects);
        geyserLaunch = bool(o, "geyser_launch", geyserLaunch);
        safeMode = bool(o, "safe_mode", safeMode);
        if (o.has("auto_connect_hosts") && o.get("auto_connect_hosts").isJsonArray()) {
            List<String> hosts = new ArrayList<>();
            o.getAsJsonArray("auto_connect_hosts").forEach(e -> hosts.add(e.getAsString().trim().toLowerCase(Locale.ROOT)));
            autoConnectHosts = hosts;
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("cushion_model", cushionModel);
        o.addProperty("cushion_seat", cushionSeat);
        o.addProperty("future_items", futureItems);
        o.addProperty("redrawn_textures", redrawnTextures);
        o.addProperty("baby_models", babyModels);
        o.addProperty("sulfur_cube", sulfurCube);
        o.addProperty("cushion_sounds", cushionSounds);
        o.addProperty("place_sounds", placeSounds);
        o.addProperty("future_sounds", futureSounds);
        o.addProperty("restore_blocks", restoreBlocks);
        o.addProperty("auto_connect_26", autoConnect26);
        o.add("auto_connect_hosts", GSON.toJsonTree(autoConnectHosts));
        o.addProperty("via_other_servers", viaOtherServers);
        o.addProperty("geyser_effects", geyserEffects);
        o.addProperty("geyser_launch", geyserLaunch);
        o.addProperty("safe_mode", safeMode);
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(o, w);
        } catch (IOException e) {
            Leapfrog.LOG.warn("Could not write {}", file(), e);
        }
    }

    public static boolean matchesAutoConnect(String host) {
        if (!autoConnect26 || host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(':') == colon) h = h.substring(0, colon);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        for (String pattern : autoConnectHosts) {
            if (pattern.startsWith("*.") ? h.endsWith(pattern.substring(1)) : h.equals(pattern)) return true;
        }
        return false;
    }
}
