package dev.leapfrog.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leapfrog.Leapfrog;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import org.jspecify.annotations.Nullable;

/** Sound types of the 26.x blocks, read from {@code block_sounds.json} (dumped from 26.3). */
public final class FutureSounds {
    public static final SoundEvent CUSHION_PLACE = event("entity.cushion.place");
    public static final SoundEvent CUSHION_BREAK = event("entity.cushion.break");
    public static final SoundEvent CUSHION_SIT = event("entity.cushion.sit");
    public static final SoundEvent CUSHION_GET_UP = event("entity.cushion.get_up");

    private static final Map<Identifier, SoundType> BY_BLOCK = load();

    private FutureSounds() {}

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }

    private static Map<Identifier, SoundType> load() {
        Map<Identifier, SoundType> map = new HashMap<>();
        try (InputStream in = FutureSounds.class.getResourceAsStream("/assets/leapfrog/block_sounds.json")) {
            if (in == null) return map;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                map.put(Identifier.withDefaultNamespace(e.getKey()), new SoundType(
                        o.get("volume").getAsFloat(), o.get("pitch").getAsFloat(),
                        sound(o, "break"), sound(o, "step"), sound(o, "place"), sound(o, "hit"), sound(o, "fall")));
            }
        } catch (Exception ex) {
            Leapfrog.LOG.error("Could not read future block sounds", ex);
        }
        return map;
    }

    private static SoundEvent sound(JsonObject o, String key) {
        return SoundEvent.createVariableRangeEvent(Identifier.parse(o.get(key).getAsString()));
    }

    /** Null when the block is unknown. */
    public static @Nullable SoundType forBlock(Identifier futureBlockId) {
        return BY_BLOCK.get(futureBlockId);
    }
}
