package dev.leapfrog.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * End-to-end check against a real 26.x server (set LEAPFROG_REMOTE=host:port). The client joins through
 * ViaFabric + ViaBackwards; the server is expected to have been prepared by {@code tools/remote-test.py}.
 */
public class RemoteServerTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("LEAPFROG_AUDIT") != null) return;
        String address = System.getenv("LEAPFROG_REMOTE");
        if (address == null || address.isBlank()) return;
        enableViaFabricClientSide();
        // Go through the real auto-connect path: the test server is a "26.3 server" like DonutSMP.
        String host = ServerAddress.parseString(address).getHost();
        if (!dev.leapfrog.Config.autoConnectHosts.contains(host)) {
            dev.leapfrog.Config.autoConnectHosts = new java.util.ArrayList<>(dev.leapfrog.Config.autoConnectHosts);
            dev.leapfrog.Config.autoConnectHosts.add(host);
        }

        java.util.Set<String> heard = java.util.concurrent.ConcurrentHashMap.newKeySet();
        context.runOnClient(mc -> mc.getSoundManager().addListener((instance, events, range) -> heard.add(instance.getIdentifier().toString())));
        context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                new ServerData("leapfrog", address, ServerData.Type.OTHER), false, null));
        context.waitFor(mc -> mc.player != null && mc.level != null, 20 * 60);
        context.waitTicks(20);
        String marker = System.getenv("LEAPFROG_READY_FILE");
        if (marker != null) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(marker), "joined");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        // The driver script teleports the player and arranges the scene, then we capture it.
        context.waitTicks(20 * 8);
        context.takeScreenshot("remote_overview");
        context.waitTicks(20 * 9);
        context.takeScreenshot("remote_closeup");
        context.runOnClient(mc -> {
            String report = dev.leapfrog.test.RemoteServerTest.describeBlocksAround(mc);
            System.out.println("[Leapfrog remote] " + report);
        });
        System.out.println("[Leapfrog remote] heard sounds: " + new java.util.TreeSet<>(heard));
        context.runOnClient(mc -> {
            java.util.TreeMap<String, String> cushions = new java.util.TreeMap<>();
            for (var e : mc.level.entitiesForRendering()) {
                var c = dev.leapfrog.cushion.CushionDetector.cushionColor(e);
                if (c != null) cushions.put(e.getStringUUID().substring(0, 8), c.getName() + " h=" + e.getBbHeight());
            }
            System.out.println("[Leapfrog remote] cushions: " + cushions);
            long sulfur = 0;
            for (var e : mc.level.entitiesForRendering()) if (dev.leapfrog.sulfur.SulfurCubeRenderer.isSulfurCube(e)) sulfur++;
            System.out.println("[Leapfrog remote] sulfur cubes recognised: " + sulfur);
        });
        context.runOnClient(mc -> mc.getConnection().getConnection().disconnect(Component.literal("done")));
        context.waitTicks(20);
        context.setScreen(TitleScreen::new);
        context.waitTicks(5);
    }

    /** The gametest run directory is wiped each run, so configure ViaFabric (client side, 26.3) in code. */
    private static void enableViaFabricClientSide() {
        try {
            Class<?> viaFabric = Class.forName("com.viaversion.fabric.mc12111.ViaFabric");
            ((java.util.concurrent.CompletableFuture<?>) viaFabric.getField("INIT_FUTURE").get(null)).get(60, java.util.concurrent.TimeUnit.SECONDS);
            Object config = viaFabric.getField("config").get(null);
            Class<?> pv = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            Object v263 = pv.getMethod("getClosest", String.class).invoke(null, "26.3");
            int protocol = (int) pv.getMethod("getVersion").invoke(v263);
            config.getClass().getMethod("setClientSideEnabled", boolean.class).invoke(config, true);
            config.getClass().getMethod("setClientSideVersion", int.class).invoke(config, protocol);
            System.out.println("[Leapfrog remote] ViaFabric client-side enabled, protocol " + protocol);
        } catch (Exception e) {
            throw new RuntimeException("ViaFabric is required for the remote test (run with -Pvia_test)", e);
        }
    }

    /** Lists the distinct block ids in a box around the test area, to prove which blocks arrived. */
    static String describeBlocksAround(net.minecraft.client.Minecraft mc) {
        java.util.TreeMap<String, Integer> counts = new java.util.TreeMap<>();
        for (int x = -8; x <= 8; x++)
            for (int y = -64; y <= -55; y++)
                for (int z = -8; z <= 8; z++) {
                    var state = mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    if (state.isAir()) continue;
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    counts.merge(id, 1, Integer::sum);
                }
        return counts.toString();
    }
}
