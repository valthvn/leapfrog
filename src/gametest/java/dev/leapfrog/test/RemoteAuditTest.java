package dev.leapfrog.test;

import static dev.leapfrog.test.Audit.check;

import dev.leapfrog.Config;
import dev.leapfrog.cushion.CushionDetector;
import dev.leapfrog.sulfur.SulfurCubeData;
import dev.leapfrog.sulfur.SulfurCubeRenderer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Audit against a real 26.3 server (LEAPFROG_REMOTE=host:port, LEAPFROG_AUDIT=1, RCON 25598 "bttf"): the
 * ViaFabric connection rules, then real interactions with restored blocks, cushions, sulfur cubes,
 * chunk reloads, dimensions, death and reconnections. The server log is scanned for kicks/rubber-banding.
 */
public class RemoteAuditTest implements FabricClientGameTest {
    private static final int X = 50, Y = -60, Z = 50;

    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getenv("LEAPFROG_REMOTE");
        if (address == null || System.getenv("LEAPFROG_AUDIT") == null) return;
        String host = ServerAddress.parseString(address).getHost();
        Path serverLog = System.getenv("LEAPFROG_SERVER_LOG") == null ? null : Path.of(System.getenv("LEAPFROG_SERVER_LOG"));
        long logStart = size(serverLog);
        Set<String> heard = ConcurrentHashMap.newKeySet();
        context.runOnClient(mc -> mc.getSoundManager().addListener((instance, events, range) -> heard.add(instance.getIdentifier().toString())));

        try (Rcon rcon = new Rcon(host, 25598, "bttf")) {
            rcon.cmd("whitelist off");
            rcon.cmd("difficulty peaceful");
            rcon.cmd("gamerule doDaylightCycle false");
            rcon.cmd("time set noon");

            // 1. The user's ViaFabric is set to translate (as if configured by hand); the server is not listed.
            setViaFabric(true);
            Config.autoConnectHosts = new ArrayList<>(List.of("donutsmp.net", "*.donutsmp.net"));
            Config.viaOtherServers = false;
            String r1 = connect(context, address);
            check("via: unlisted server joined without ViaFabric (26.3 server refuses a vanilla 1.21.11 client)", r1.startsWith("refused"), r1);
            check("via: ViaFabric translation was switched off for it", !viaFabricEnabled(), "");
            backToTitle(context);

            // 2. Same server with "ViaFabric on other servers" ON: the user's own ViaFabric settings apply.
            Config.viaOtherServers = true;
            String r2 = connect(context, address);
            check("via: 'ViaFabric sur les autres serveurs' ON restores the user's ViaFabric settings", r2.equals("joined"), r2);
            disconnect(context);
            Config.viaOtherServers = false;

            // 3. Listed server: auto 26.3.
            Config.autoConnectHosts.add(host);
            String r3 = connect(context, address);
            check("via: listed server joined as 26.3", r3.equals("joined"), r3);
            if (!r3.equals("joined")) return;

            scene(rcon);
            rcon.cmd("gamemode creative @a");
            rcon.cmd("tp @a " + (X + 0.5) + " " + Y + " " + (Z - 4.5) + " 0 30");
            context.waitTicks(60);
            context.takeScreenshot("audit_remote_scene");

            restoredBlocks(context);
            standOnSlab(context, rcon);
            breakInCreative(context, rcon);
            breakInSurvival(context, rcon);
            placeStandIn(context, rcon, heard);
            sulfurBucket(context, rcon);
            cushionSit(context, rcon, heard);
            sulfurCubes(context, rcon);
            futureSounds(context, rcon, heard);
            restoreToggle(context, rcon);
            dimension(context, rcon);
            death(context, rcon);

            // Reconnection cycles.
            for (int i = 1; i <= 2; i++) {
                disconnect(context);
                String r = connect(context, address);
                context.waitTicks(40);
                String id = context.computeOnClient(mc -> blockId(mc, new BlockPos(X - 2, Y, Z)));
                check("via: reconnection " + i + " restores blocks again", r.equals("joined") && id.startsWith("leapfrog:"), r + " " + id);
            }
            disconnect(context);
            dev.leapfrog.via.ViaFabricControl.restore();
            check("via: user's ViaFabric setting restored on shutdown", viaFabricEnabled(), "");
        } catch (Exception e) {
            e.printStackTrace();
            check("remote audit crashed", false, e);
        } finally {
            context.setScreen(TitleScreen::new);
        }

        if (serverLog != null) {
            String log = tail(serverLog, logStart);
            for (String needle : new String[]{"moved wrongly", "moved too quickly", "Exception", "lost connection: Internal"}) {
                List<String> lines = log.lines().filter(l -> l.contains(needle)).toList();
                check("server log: no '" + needle + "'", lines.isEmpty(), lines.isEmpty() ? "" : lines.get(0) + (lines.size() > 1 ? " (+" + (lines.size() - 1) + ")" : ""));
            }
        }
        Audit.summary("26.3 server audit");
    }

    // scenarios

    private static void scene(Rcon rcon) {
        rcon.cmd("fill " + (X - 10) + " " + (Y - 1) + " " + (Z - 10) + " " + (X + 14) + " " + (Y - 1) + " " + (Z + 24) + " minecraft:stone");
        rcon.cmd("fill " + (X - 10) + " " + Y + " " + (Z - 10) + " " + (X + 14) + " " + (Y + 10) + " " + (Z + 24) + " minecraft:air");
        rcon.cmd("kill @e[type=!minecraft:player]");
        String[] blocks = {"white_wool_stairs[facing=north]", "lime_wool_slab", "red_concrete_slab", "poplar_planks", "poplar_log",
                "cinnabar", "sulfur", "sulfur_bricks", "golden_dandelion", "red_poplar_leaves[persistent=true]"};
        rcon.cmd("setblock " + (X + 6) + " " + (Y - 1) + " " + Z + " minecraft:grass_block");
        for (int i = 0; i < blocks.length; i++) rcon.cmd("setblock " + (X - 2 + i) + " " + Y + " " + Z + " minecraft:" + blocks[i]);
    }

    private static void restoredBlocks(ClientGameTestContext context) {
        String row = context.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(blockId(mc, new BlockPos(X - 2 + i, Y, Z))).append(' ');
            return sb.toString();
        });
        check("blocks: the 10 test blocks arrive as restored 26.x blocks", countOf(row, "leapfrog:") == 10, row);
    }

    private static int countOf(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static void standOnSlab(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("gamemode survival @a");
        rcon.cmd("effect give @a minecraft:resistance 1000 4 true");
        rcon.cmd("setblock " + (X + 4) + " " + Y + " " + (Z + 4) + " minecraft:white_wool_slab");
        context.waitTicks(10);
        rcon.cmd("tp @a " + (X + 4.5) + " " + (Y + 2) + " " + (Z + 4.5));
        context.waitTicks(40);
        double y = context.computeOnClient(mc -> mc.player.getY());
        boolean ground = context.computeOnClient(mc -> mc.player.onGround());
        String server = rcon.cmd("data get entity @p Pos[1]");
        check("blocks: standing on a restored wool slab (client)", Math.abs(y - (Y + 0.5)) < 0.01 && ground, y);
        check("blocks: server agrees on the position", server.contains("-59.5"), server);
        // Walk across a row of restored stairs and slabs.
        rcon.cmd("fill " + (X + 2) + " " + Y + " " + (Z + 6) + " " + (X + 8) + " " + Y + " " + (Z + 6) + " minecraft:lime_wool_slab");
        rcon.cmd("tp @a " + (X + 2.5) + " " + (Y + 0.5) + " " + (Z + 6.5) + " -90 0");
        context.waitTicks(20);
        context.getInput().holdKeyFor(options -> options.keyUp, 30);
        context.waitTicks(10);
        double x = context.computeOnClient(mc -> mc.player.getX());
        check("blocks: walking on restored slabs", x > X + 5, x);
    }

    private static void breakInCreative(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("gamemode creative @a");
        BlockPos pos = new BlockPos(X + 6, Y, Z + 2);
        rcon.cmd("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:cinnabar");
        rcon.cmd("tp @a " + (X + 6.5) + " " + Y + " " + (Z + 0.5) + " 0 30");
        context.waitTicks(20);
        context.runOnClient(mc -> mc.gameMode.startDestroyBlock(pos, Direction.UP));
        context.waitTicks(20);
        String client = context.computeOnClient(mc -> blockId(mc, pos));
        String server = rcon.cmd("execute if block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:air");
        check("blocks: creative break of a restored block", client.equals("minecraft:air") && server.contains("passed"), client + " / " + server);
    }

    /** Survival mining: the client predicts the break time from the block hardness; it must match the server. */
    /**
     * Survival mining: the client predicts the break time from hardness and tool rules. It must match the
     * 26.3 value, and the server must already have broken the block when the client shows it broken
     * (otherwise: block flickering back, and "fast break" for an anticheat).
     */
    private static void breakInSurvival(ClientGameTestContext context, Rcon rcon) {
        // block, held item, expected ticks (26.3 formula: hardness * 30 by hand, * 100 if a tool is required, / tool speed)
        Object[][] cases = {
                {"poplar_planks", "air", 60}, {"cinnabar", "air", 150}, {"white_wool_slab", "air", 24}, {"sulfur", "air", 150},
                {"cinnabar", "iron_pickaxe", 8}, {"poplar_log", "iron_axe", 10}, {"white_wool_stairs", "shears", 5}};
        rcon.cmd("gamemode survival @a");
        for (int i = 0; i < cases.length; i++) {
            String block = (String) cases[i][0], tool = (String) cases[i][1];
            int expected = (int) cases[i][2];
            BlockPos pos = new BlockPos(X + 8, Y, Z + 4 + i * 2);
            rcon.cmd("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
            rcon.cmd("clear @a");
            if (!tool.equals("air")) rcon.cmd("item replace entity @a hotbar.0 with minecraft:" + tool);
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            rcon.cmd("tp @a " + (X + 6.5) + " " + Y + " " + (pos.getZ() + 0.5) + " -90 35");
            context.waitTicks(20);
            String diag = context.computeOnClient(mc -> {
                var stack = mc.player.getMainHandItem();
                var state = mc.level.getBlockState(pos);
                var toolComp = stack.get(net.minecraft.core.component.DataComponents.TOOL);
                StringBuilder sb = new StringBuilder("speed=" + stack.getDestroySpeed(state) + " onGround=" + mc.player.onGround() + " state=" + state);
                if (toolComp != null) for (var r : toolComp.rules()) sb.append(" rule[").append(r.blocks().unwrapKey().map(k -> k.location().toString()).orElse("direct")).append(']');
                return sb.toString();
            });
            System.out.println("[AUDIT] diag " + block + "/" + tool + ": " + diag);
            context.getInput().holdKey(options -> options.keyAttack);
            int ticks = 0;
            while (ticks < 400 && !context.computeOnClient(mc -> mc.level.getBlockState(pos).isAir())) {
                context.waitTick();
                ticks++;
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            String serverNow = rcon.cmd("execute if block " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:air");
            context.waitTicks(10);
            boolean stillAir = context.computeOnClient(mc -> mc.level.getBlockState(pos).isAir());
            // The measurement itself adds ~5 ticks (key press to first attack + polling).
            check("mining: " + block + " with " + tool + " takes the 26.3 time (" + expected + " ticks + 5 measuring)", Math.abs(ticks - expected - 5) <= 2, ticks + " ticks");
            check("mining: " + block + " with " + tool + " is broken on the server at the same moment", serverNow.contains("passed") && stillAir,
                    "server=" + serverNow.trim() + " clientStillAir=" + stillAir);
        }
        rcon.cmd("clear @a");
        rcon.cmd("gamemode creative @a");
    }

    private static void placeStandIn(ClientGameTestContext context, Rcon rcon, Set<String> heard) {
        rcon.cmd("clear @a");
        rcon.cmd("item replace entity @a hotbar.0 with minecraft:white_wool_stairs");
        rcon.cmd("tp @a " + (X + 10.5) + " " + Y + " " + (Z - 2.5) + " 0 50");
        context.waitTicks(20);
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        String held = context.computeOnClient(mc -> BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()) + " model=" + mc.player.getMainHandItem().get(net.minecraft.core.component.DataComponents.ITEM_MODEL));
        heard.clear();
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(20);
        String placed = context.computeOnClient(mc -> {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos p = new BlockPos(X + 10, Y, Z - 2 + dz);
                String id = blockId(mc, p);
                if (!id.equals("minecraft:air")) return id + "@" + p.toShortString();
            }
            return "nothing";
        });
        check("items: placing a held 26.x block (stand-in item) gives the restored block", placed.startsWith("leapfrog:white_wool_stairs"), placed + " held=" + held);
        check("items: its place sound is wool", heard.contains("minecraft:block.wool.place"), heard);
    }

    private static void sulfurBucket(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("kill @e[type=minecraft:sulfur_cube]");
        rcon.cmd("clear @a");
        rcon.cmd("gamemode survival @a");
        rcon.cmd("item replace entity @a hotbar.0 with minecraft:sulfur_cube_bucket");
        rcon.cmd("tp @a " + (X + 12.5) + " " + Y + " " + (Z + 8.5) + " 0 55");
        context.waitTicks(20);
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(30);
        String count = rcon.cmd("execute if entity @e[type=minecraft:sulfur_cube]");
        String clientHand = context.computeOnClient(mc -> BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
        String serverHand = rcon.cmd("execute if items entity @p weapon.mainhand minecraft:bucket");
        check("sulfur: one click with the bucket releases the cube", count.contains("passed"), count.trim());
        check("sulfur: client and server agree the bucket is now empty", clientHand.equals("minecraft:bucket") && serverHand.contains("passed"),
                "client=" + clientHand + " server=" + serverHand.trim());
        rcon.cmd("kill @e[type=minecraft:sulfur_cube]");
        rcon.cmd("gamemode creative @a");
    }

    private static void cushionSit(ClientGameTestContext context, Rcon rcon, Set<String> heard) {
        rcon.cmd("kill @e[type=minecraft:cushion]");
        rcon.cmd("summon minecraft:cushion " + (X + 2.5) + " " + Y + " " + (Z + 10.5) + " {color:\"blue\"}");
        rcon.cmd("tp @a " + (X + 2.5) + " " + Y + " " + (Z + 8.5) + " 0 35");
        context.waitTicks(30);
        String color = context.computeOnClient(mc -> {
            for (Entity e : mc.level.entitiesForRendering()) {
                var c = CushionDetector.cushionColor(e);
                if (c != null && e.distanceToSqr(X + 2.5, Y, Z + 10.5) < 1) return c.getName();
            }
            return "none";
        });
        check("cushion: colour restored from the 26.3 stream", color.equals("blue"), color);
        heard.clear();
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(20);
        double[] seat = context.computeOnClient(mc -> mc.player.getVehicle() == null ? null
                : new double[]{mc.player.getY() - mc.player.getVehicle().getY()});
        check("cushion: right-click sits down", seat != null, "");
        if (seat != null) check("cushion: seated at the 26.3 height", Math.abs(seat[0] - (0.25 - 0.6)) < 0.03, seat[0]);
        check("cushion: sit sound", heard.contains("minecraft:entity.cushion.sit"), heard);
        heard.clear();
        context.getInput().holdKeyFor(options -> options.keyShift, 10);
        context.waitTicks(20);
        boolean standing = context.computeOnClient(mc -> mc.player.getVehicle() == null);
        check("cushion: sneaking gets up", standing, "");
        check("cushion: get-up sound", heard.contains("minecraft:entity.cushion.get_up"), heard);
        heard.clear();
        rcon.cmd("tp @a " + (X + 2.5) + " " + Y + " " + (Z + 8.5) + " 0 35");
        context.waitTicks(10);
        context.getInput().pressKey(options -> options.keyAttack);
        context.waitTicks(20);
        String left = rcon.cmd("execute if entity @e[type=minecraft:cushion]");
        check("cushion: hitting it breaks it", !left.contains("passed"), left.trim());
        check("cushion: break sound", heard.contains("minecraft:entity.cushion.break"), heard);
        String server = rcon.cmd("data get entity @p Pos[1]");
        check("cushion: no position desync after getting up", server.contains("-60"), server.trim());
    }

    private static void sulfurCubes(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("kill @e[type=minecraft:sulfur_cube]");
        rcon.cmd("summon minecraft:sulfur_cube " + (X - 6.5) + " " + Y + " " + (Z + 2.5) + " {NoAI:1b,Age:-24000}");
        rcon.cmd("summon minecraft:sulfur_cube " + (X - 6.5) + " " + Y + " " + (Z + 6.5) + " {NoAI:1b,equipment:{body:{id:\"minecraft:tnt\",count:1}}}");
        rcon.cmd("tp @a " + (X - 2.5) + " " + Y + " " + (Z + 4.5) + " 90 20");
        context.waitTicks(40);
        String info = context.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!SulfurCubeRenderer.isSulfurCube(e)) continue;
                sb.append(Math.round(e.getZ() - 0.5)).append(":baby=").append(SulfurCubeData.isBaby(e.getId()))
                        .append(",body=").append(BuiltInRegistries.ITEM.getKey(((LivingEntity) e).getItemBySlot(EquipmentSlot.BODY).getItem())).append(' ');
            }
            return sb.toString();
        });
        check("sulfur: baby flag captured from the 26.3 stream", info.contains((Z + 2) + ":baby=true"), info);
        check("sulfur: swallowed TNT arrives in the body slot", info.contains((Z + 6) + ":baby=false,body=minecraft:tnt"), info);
        context.takeScreenshot("audit_remote_sulfur");
        // Prime it on a redstone block.
        rcon.cmd("setblock " + (X - 7) + " " + (Y - 1) + " " + (Z + 6) + " minecraft:redstone_block");
        float fuse = 0;
        for (int i = 0; i < 40 && fuse <= 0; i++) {
            context.waitTick();
            fuse = context.computeOnClient(mc -> {
                for (Entity e : mc.level.entitiesForRendering())
                    if (SulfurCubeRenderer.isSulfurCube(e) && Math.round(e.getZ() - 0.5) == Z + 6) return SulfurCubeData.fuseRemaining(e.getId());
                return 0f;
            });
        }
        check("sulfur: priming captured (fuse counting down)", fuse > 0, fuse);
        rcon.cmd("tp @a " + (X + 10.5) + " " + Y + " " + (Z + 10.5));
        context.waitTicks(100);
        rcon.cmd("kill @e[type=minecraft:sulfur_cube]");
        rcon.cmd("setblock " + (X - 7) + " " + (Y - 1) + " " + (Z + 6) + " minecraft:stone");
    }

    private static void futureSounds(ClientGameTestContext context, Rcon rcon, Set<String> heard) {
        for (String s : new String[]{"entity.sulfur_cube.jump", "block.note_block.trumpet", "entity.baby_cat.ambient", "block.cinnabar.place"}) {
            heard.clear();
            rcon.cmd("execute as @p at @s run playsound minecraft:" + s + " master @s ~ ~ ~");
            context.waitTicks(10);
            check("sounds: 26.x sound " + s, heard.contains("minecraft:" + s), heard);
        }
    }

    private static void restoreToggle(ClientGameTestContext context, Rcon rcon) {
        Config.restoreBlocks = false;
        reloadChunks(context, rcon);
        String off = context.computeOnClient(mc -> blockId(mc, new BlockPos(X - 2, Y, Z)));
        check("blocks: option off shows ViaBackwards' stand-in after a chunk reload", off.startsWith("minecraft:"), off);
        Config.restoreBlocks = true;
        reloadChunks(context, rcon);
        String on = context.computeOnClient(mc -> blockId(mc, new BlockPos(X - 2, Y, Z)));
        check("blocks: option back on restores after a chunk reload", on.startsWith("leapfrog:"), on);
    }

    private static void reloadChunks(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("tp @a 4000 100 4000");
        context.waitTicks(60);
        rcon.cmd("tp @a " + (X + 0.5) + " " + Y + " " + (Z - 4.5) + " 0 30");
        context.waitTicks(80);
    }

    private static void dimension(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("execute in minecraft:the_nether run tp @a 0 120 0");
        context.waitTicks(80);
        String dim = context.computeOnClient(mc -> mc.level.dimension().identifier().toString());
        check("world: nether visit", dim.equals("minecraft:the_nether"), dim);
        rcon.cmd("execute in minecraft:overworld run tp @a " + (X + 0.5) + " " + Y + " " + (Z - 4.5) + " 0 30");
        context.waitTicks(80);
        String id = context.computeOnClient(mc -> blockId(mc, new BlockPos(X - 2, Y, Z)));
        check("world: blocks still restored after a dimension change", id.startsWith("leapfrog:"), id);
    }

    private static void death(ClientGameTestContext context, Rcon rcon) {
        rcon.cmd("gamemode survival @a");
        rcon.cmd("effect clear @a");
        rcon.cmd("kill @a");
        context.waitTicks(30);
        boolean dead = context.computeOnClient(mc -> mc.player.isDeadOrDying());
        context.runOnClient(mc -> mc.player.respawn());
        context.waitTicks(60);
        context.setScreen(() -> null);
        boolean alive = context.computeOnClient(mc -> mc.player != null && mc.player.isAlive());
        check("world: death and respawn", dead && alive, "dead=" + dead + " alive=" + alive);
        rcon.cmd("gamemode creative @a");
        rcon.cmd("tp @a " + (X + 0.5) + " " + Y + " " + (Z - 4.5) + " 0 30");
        context.waitTicks(60);
        String id = context.computeOnClient(mc -> blockId(mc, new BlockPos(X - 2, Y, Z)));
        check("world: blocks restored after respawn", id.startsWith("leapfrog:"), id);
    }

    // helpers

    private static String blockId(Minecraft mc, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
    }

    /** "joined", or "refused: <screen>" if the connection ends on a disconnect screen. */
    private static String connect(ClientGameTestContext context, String address) {
        context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                new ServerData("leapfrog", address, ServerData.Type.OTHER), false, null));
        for (int i = 0; i < 20 * 40; i++) {
            context.waitTick();
            String state = context.computeOnClient(mc -> {
                if (mc.player != null && mc.level != null) return "joined";
                if (mc.screen instanceof DisconnectedScreen d) return "refused: " + d.getNarrationMessage().getString().replace('\n', ' ');
                return null;
            });
            if (state != null) {
                if (state.equals("joined")) context.waitTicks(40);
                return state;
            }
        }
        return "timeout";
    }

    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (mc.getConnection() != null) mc.getConnection().getConnection().disconnect(Component.literal("audit"));
        });
        context.waitTicks(40);
        backToTitle(context);
    }

    private static void backToTitle(ClientGameTestContext context) {
        context.setScreen(TitleScreen::new);
        context.waitTicks(10);
    }

    private static void setViaFabric(boolean enabled) {
        try {
            Class<?> viaFabric = Class.forName("com.viaversion.fabric.mc12111.ViaFabric");
            ((java.util.concurrent.CompletableFuture<?>) viaFabric.getField("INIT_FUTURE").get(null)).get(60, java.util.concurrent.TimeUnit.SECONDS);
            Object config = viaFabric.getField("config").get(null);
            Class<?> pv = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            int protocol = (int) pv.getMethod("getVersion").invoke(pv.getMethod("getClosest", String.class).invoke(null, "26.3"));
            config.getClass().getMethod("setClientSideEnabled", boolean.class).invoke(config, enabled);
            config.getClass().getMethod("setClientSideVersion", int.class).invoke(config, protocol);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean viaFabricEnabled() {
        try {
            Object config = Class.forName("com.viaversion.fabric.mc12111.ViaFabric").getField("config").get(null);
            return (boolean) config.getClass().getMethod("isClientSideEnabled").invoke(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long size(Path p) {
        try {
            return p == null ? 0 : Files.size(p);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String tail(Path p, long from) {
        try {
            byte[] all = Files.readAllBytes(p);
            return new String(all, (int) Math.min(from, all.length), (int) (all.length - Math.min(from, all.length)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
