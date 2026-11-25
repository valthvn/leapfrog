package dev.leapfrog.sound;

import dev.leapfrog.Leapfrog;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Registers the sound events added after 1.21.11 so the Via hook can send them by id. */
public final class FutureSoundRegistry {
    private static final Int2ObjectMap<SoundEvent> BY_FUTURE_ID = new Int2ObjectOpenHashMap<>();
    private static final Set<String> NAMES = new HashSet<>();

    private FutureSoundRegistry() {}

    public static void register() {
        try (InputStream in = FutureSoundRegistry.class.getResourceAsStream("/assets/leapfrog/future_sounds.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int space = line.indexOf(' ');
                int futureId = Integer.parseInt(line.substring(0, space));
                Identifier id = Identifier.withDefaultNamespace(line.substring(space + 1));
                SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
                if (event == null) event = Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
                BY_FUTURE_ID.put(futureId, event);
                NAMES.add(id.getPath());
            }
        } catch (Exception e) {
            Leapfrog.LOG.error("Could not register 26.x sound events", e);
        }
        Leapfrog.LOG.info("Registered {} 26.x sound events", BY_FUTURE_ID.size());
    }

    /** "minecraft:entity.baby_cat.ambient" or "entity.baby_cat.ambient". */
    public static boolean isFutureName(String identifier) {
        if (identifier == null) return false;
        return NAMES.contains(identifier.startsWith("minecraft:") ? identifier.substring(10) : identifier);
    }

    public static boolean isFuture(int futureId) {
        return BY_FUTURE_ID.containsKey(futureId);
    }

    /** Client registry id of a 26.3 sound id, or -1. */
    public static int clientId(int futureId) {
        SoundEvent e = BY_FUTURE_ID.get(futureId);
        return e == null ? -1 : BuiltInRegistries.SOUND_EVENT.getId(e);
    }
}
