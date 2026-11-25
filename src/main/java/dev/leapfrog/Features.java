package dev.leapfrog;

import dev.leapfrog.baby.BabyModels;
import dev.leapfrog.via.ViaBridge;
import dev.leapfrog.via.ViaFabricControl;
import net.minecraft.client.Minecraft;

/** What the settings screen needs to know about the running game, and the actions its toggles trigger. */
public final class Features {
    private static final boolean VIA_BACKWARDS = classExists("com.viaversion.viabackwards.protocol.v26_3to26_2.Protocol26_3To26_2");

    private Features() {}

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Features.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // The Via classes below must only be touched once ViaBackwards is known to be on the classpath.
    public static boolean viaBackwardsPresent() {
        return VIA_BACKWARDS;
    }

    public static boolean viaFabricPresent() {
        return VIA_BACKWARDS && ViaFabricControl.isViaFabricPresent();
    }

    public static boolean viaHooked() {
        return VIA_BACKWARDS && ViaBridge.isHooked();
    }

    public static boolean connectedAs26() {
        return VIA_BACKWARDS && Minecraft.getInstance().level != null && ViaBridge.recentlyActive();
    }

    public static void reloadModels() {
        Minecraft.getInstance().reloadResourcePacks();
    }

    public static boolean babyModelsAvailable() {
        return BabyModels.available();
    }

    /** The redrawn textures are part of the 26.x asset pack, which reads the setting on reload. */
    public static void applyRedrawnTextures() {
        Minecraft.getInstance().reloadResourcePacks();
    }
}
