package dev.leapfrog.via;

import dev.leapfrog.Leapfrog;
import dev.leapfrog.Config;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Switches ViaFabric's client setting per server through reflection. Listed servers are joined as
 * 26.3, other servers get ViaFabric turned off unless the user allowed it. The user's own setting is
 * put back on shutdown.
 */
public final class ViaFabricControl {
    private static final String VIA_FABRIC = "com.viaversion.fabric.mc12111.ViaFabric";
    private static @Nullable Boolean savedEnabled;
    private static int savedVersion;

    private ViaFabricControl() {}

    public static boolean isViaFabricPresent() {
        try {
            Class.forName(VIA_FABRIC, false, ViaFabricControl.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void beforeConnect(String host) {
        if (!isViaFabricPresent()) return;
        try {
            Object config = Class.forName(VIA_FABRIC).getField("config").get(null);
            Method isEnabled = config.getClass().getMethod("isClientSideEnabled");
            Method setEnabled = config.getClass().getMethod("setClientSideEnabled", boolean.class);
            Method getVersion = config.getClass().getMethod("getClientSideVersion");
            Method setVersion = config.getClass().getMethod("setClientSideVersion", int.class);
            boolean join26 = Config.matchesAutoConnect(host);
            if (join26 || !Config.viaOtherServers) {
                if (savedEnabled == null) {
                    savedEnabled = (Boolean) isEnabled.invoke(config);
                    savedVersion = (int) getVersion.invoke(config);
                }
            }
            if (join26) {
                int protocol = protocol26_3();
                setEnabled.invoke(config, true);
                setVersion.invoke(config, protocol);
                Leapfrog.LOG.info("Joining {} as 26.3 (protocol {}) through ViaFabric", host, protocol);
            } else if (!Config.viaOtherServers) {
                setEnabled.invoke(config, false);
                Leapfrog.LOG.info("Joining {} without ViaFabric", host);
            } else if (savedEnabled != null) {
                setEnabled.invoke(config, savedEnabled);
                setVersion.invoke(config, savedVersion);
                savedEnabled = null;
            }
        } catch (Throwable t) {
            Leapfrog.LOG.warn("Could not configure ViaFabric", t);
        }
    }

    /** Puts the user's ViaFabric setting back so our changes don't stay in its config. */
    public static void restore() {
        if (savedEnabled == null || !isViaFabricPresent()) return;
        try {
            Object config = Class.forName(VIA_FABRIC).getField("config").get(null);
            config.getClass().getMethod("setClientSideEnabled", boolean.class).invoke(config, savedEnabled);
            config.getClass().getMethod("setClientSideVersion", int.class).invoke(config, savedVersion);
            savedEnabled = null;
        } catch (Throwable t) {
            Leapfrog.LOG.warn("Could not restore ViaFabric settings", t);
        }
    }

    private static int protocol26_3() throws ReflectiveOperationException {
        Class<?> pv = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
        Object v = pv.getMethod("getClosest", String.class).invoke(null, "26.3");
        return (int) pv.getMethod("getVersion").invoke(v);
    }
}
