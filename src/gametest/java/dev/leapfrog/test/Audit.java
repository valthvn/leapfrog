package dev.leapfrog.test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;

/** Shared helpers for the audit tests: soft assertions that log PASS/FAIL and never stop the run. */
final class Audit {
    static final List<String> FAILS = new CopyOnWriteArrayList<>();
    static int passes;

    private Audit() {}

    static void check(String name, boolean ok, Object detail) {
        String d = detail == null ? "" : detail.toString();
        System.out.println("[AUDIT] " + (ok ? "PASS " : "FAIL ") + name + (d.isEmpty() ? "" : "  -- " + d));
        if (ok) passes++;
        else FAILS.add(name + (d.isEmpty() ? "" : " (" + d + ")"));
    }

    static void summary(String suite) {
        System.out.println("[AUDIT] ===== " + suite + ": " + passes + " passed, " + FAILS.size() + " failed");
        for (String f : FAILS) System.out.println("[AUDIT]   FAILED: " + f);
    }

    /** Live particles by class name. */
    static Map<String, Integer> particles(Minecraft mc) {
        TreeMap<String, Integer> byClass = new TreeMap<>();
        try {
            var f = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
            f.setAccessible(true);
            for (Object g : ((Map<?, ?>) f.get(mc.particleEngine)).values()) {
                for (Object p : ((net.minecraft.client.particle.ParticleGroup<?>) g).getAll()) {
                    byClass.merge(p.getClass().getSimpleName(), 1, Integer::sum);
                }
            }
        } catch (ReflectiveOperationException e) {
            byClass.put(e.toString(), -1);
        }
        return byClass;
    }

    @SuppressWarnings("unchecked")
    static <T> T staticField(Class<?> owner, String name) {
        try {
            var f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name) {
        try {
            var f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
