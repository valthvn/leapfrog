package dev.leapfrog.item;

import dev.leapfrog.Leapfrog;
import dev.leapfrog.Config;
import dev.leapfrog.assets.FutureAssets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

/**
 * Finds the 26.x item behind a ViaBackwards replacement item. ViaBackwards leaves the original id in
 * the custom_model_data strings and in a {@code vb.item.*} custom name. Only ids listed in
 * the downloaded 26.x item list are accepted.
 */
public final class FutureItems {
    private static final String NAME_PREFIX = "vb.item.";
    // Parsed once so the per-frame model lookup doesn't allocate.
    private static final Map<String, Identifier> KNOWN = load();

    private FutureItems() {}

    private static Map<String, Identifier> load() {
        Map<String, Identifier> ids = new HashMap<>();
        for (String id : FutureAssets.futureItems()) ids.put(id, Identifier.parse(id));
        Leapfrog.LOG.info("Knows {} future items", ids.size());
        return ids;
    }

    /** Null for normal items. */
    public static @Nullable Identifier originalId(ItemStack stack) {
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            for (String s : cmd.strings()) {
                Identifier id = KNOWN.get(s);
                if (id != null) return id;
            }
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && name.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key.startsWith(NAME_PREFIX)) {
                return KNOWN.get("minecraft:" + key.substring(NAME_PREFIX.length()));
            }
        }
        return null;
    }

    public static @Nullable Identifier remapModel(ItemStack stack, @Nullable Identifier original) {
        if (!Config.futureItems) return original;
        Identifier future = originalId(stack);
        return future != null ? future : original;
    }
}
