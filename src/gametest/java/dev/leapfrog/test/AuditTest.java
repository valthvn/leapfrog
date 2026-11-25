package dev.leapfrog.test;

import static dev.leapfrog.test.Audit.check;

import dev.leapfrog.Config;
import dev.leapfrog.block.FutureBlock;
import dev.leapfrog.block.FutureBlocks;
import dev.leapfrog.cushion.CushionDetector;
import dev.leapfrog.cushion.CushionRenderer;
import dev.leapfrog.item.FutureItems;
import dev.leapfrog.sulfur.SulfurCubeData;
import dev.leapfrog.sulfur.SulfurCubeRenderer;
import dev.leapfrog.ui.SettingsScreen;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.lwjgl.glfw.GLFW;

/**
 * Exhaustive singleplayer audit: every setting, the UI driven by real mouse/keyboard input, and edge
 * cases of cushions, items, sulfur cubes, geysers and restored blocks. Logs PASS/FAIL per check.
 */
public class AuditTest implements FabricClientGameTest {
    private static final String SULFUR_NAME = "CustomName:{translate:\"vb.entity.sulfur_cube\",fallback:\"Sulfur Cube\"}";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("LEAPFROG_REMOTE") != null) return;
        List<String> heard = new CopyOnWriteArrayList<>();
        context.runOnClient(mc -> mc.getSoundManager().addListener((instance, events, range) -> heard.add(instance.getIdentifier().toString())));
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("gamemode creative @p");
            floor(server);

            safe("config", () -> config());
            safe("cushions", () -> cushions(context, server, heard));
            safe("items", () -> items(context));
            safe("sulfur", () -> sulfur(context, server));
            safe("geyser", () -> geyser(context, server));
            safe("blocks", () -> blocks(context, server));
            safe("keybind", () -> keybind(context));
            safe("ui", () -> ui(context, server));
        }
        safe("ui-title", () -> uiFromTitle(context));
        Audit.summary("singleplayer audit");
    }

    private static void safe(String section, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            t.printStackTrace();
            check(section + " section crashed", false, t);
        }
    }

    private static void floor(TestServerContext server) {
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("fill -12 98 -12 12 99 12 minecraft:stone");
        server.runCommand("fill -12 100 -12 12 100 12 minecraft:oak_planks");
        server.runCommand("fill -12 101 -12 12 112 12 minecraft:air");
    }

    // config

    private static void config() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("leapfrog.json");
        boolean saved = Config.cushionModel;
        try {
            Files.writeString(file, "{ this is : not json ,,,");
            Config.load();
            check("config: corrupt file falls back to defaults", Config.cushionModel, "cushionModel=" + Config.cushionModel);
            String rewritten = Files.readString(file);
            check("config: corrupt file is rewritten as valid JSON", rewritten.trim().startsWith("{") && rewritten.contains("cushion_model"), "");
            Files.writeString(file, "{\"cushion_model\": false}");
            Config.load();
            check("config: partial file keeps other defaults", !Config.cushionModel && Config.futureItems, "");
            Files.writeString(file, "{\"cushion_model\": \"oops\", \"auto_connect_hosts\": [1, \"Foo.NET \"]}");
            Config.load();
            check("config: wrong value types do not crash", true, "cushionModel=" + Config.cushionModel + " hosts=" + Config.autoConnectHosts);
            Files.writeString(file, "");
            Config.load();
            check("config: empty file does not crash", true, "");
        } catch (Throwable t) {
            check("config: load/save robustness", false, t);
        } finally {
            Config.cushionModel = saved;
            Config.autoConnectHosts = new java.util.ArrayList<>(List.of("donutsmp.net", "*.donutsmp.net"));
            Config.save();
        }
        check("host match: plain", Config.matchesAutoConnect("donutsmp.net"), "");
        check("host match: subdomain", Config.matchesAutoConnect("play.donutsmp.net"), "");
        check("host match: upper case + trailing dot", Config.matchesAutoConnect("PLAY.DonutSMP.net."), "");
        check("host match: with port", Config.matchesAutoConnect("donutsmp.net:25565"), "");
        check("host match: lookalike rejected", !Config.matchesAutoConnect("notdonutsmp.net") && !Config.matchesAutoConnect("donutsmp.net.evil.com"), "");
        check("host match: IPv6 literal does not crash", !Config.matchesAutoConnect("[::1]:25565"), "");
    }

    // cushions

    private static void cushions(ClientGameTestContext context, TestServerContext server, List<String> heard) {
        floor(server);
        server.runCommand("tp @p 0.5 101 -3.5 0 30");
        server.runCommand("summon minecraft:falling_block 0.5 101 0.5 {NoGravity:1b,Time:1,BlockState:{Name:\"minecraft:red_carpet\"}}");
        context.waitTicks(5);
        boolean renderer = context.computeOnClient(mc -> (Object) mc.getEntityRenderDispatcher().getRenderer(cushion(mc)) instanceof CushionRenderer);
        check("cushion: stand-in drawn with the cushion renderer", renderer, "");
        float h = context.computeOnClient(mc -> cushion(mc).getBbHeight());
        check("cushion: hitbox height 0.25", Math.abs(h - 0.25f) < 1e-4, h);

        Config.cushionSeat = false;
        context.waitTicks(3);
        float h2 = context.computeOnClient(mc -> cushion(mc).getBbHeight());
        check("cushion: seat off restores the stand-in size", Math.abs(h2 - 0.98f) < 1e-3, h2);
        Config.cushionSeat = true;
        context.waitTicks(3);
        float h3 = context.computeOnClient(mc -> cushion(mc).getBbHeight());
        check("cushion: seat back on resizes again", Math.abs(h3 - 0.25f) < 1e-4, h3);

        Config.cushionModel = false;
        boolean vanilla = context.computeOnClient(mc -> !((Object) mc.getEntityRenderDispatcher().getRenderer(cushion(mc)) instanceof CushionRenderer));
        check("cushion: model off uses the vanilla falling block renderer", vanilla, "");
        Config.cushionModel = true;

        // A falling carpet with gravity (real falling block) is not a cushion.
        server.runCommand("summon minecraft:falling_block 6.5 110 6.5 {Time:1,BlockState:{Name:\"minecraft:blue_carpet\"}}");
        context.waitTicks(2);
        boolean realFalling = context.computeOnClient(mc -> {
            for (Entity e : mc.level.entitiesForRendering())
                if (e instanceof FallingBlockEntity f && !f.isNoGravity()) return CushionDetector.cushionColor(f) == null;
            return true;
        });
        check("cushion: real falling carpet is not mistaken for a cushion", realFalling, "");

        // Riding, then the cushion disappears under the player.
        server.runCommand("ride @p mount @e[type=minecraft:falling_block,limit=1,sort=nearest,nbt={NoGravity:1b}]");
        context.waitTicks(5);
        boolean riding = context.computeOnClient(mc -> mc.player.getVehicle() != null);
        check("cushion: player can be seated", riding, "");
        server.runCommand("kill @e[type=minecraft:falling_block]");
        context.waitTicks(5);
        boolean dismounted = context.computeOnClient(mc -> mc.player.getVehicle() == null);
        check("cushion: removed under the rider, player is dismounted cleanly", dismounted, "");

        // Cushion standing on a bottom slab (y = .5): its sounds arrive at a non-integer Y.
        server.runCommand("setblock 3 101 0 minecraft:oak_slab[type=bottom]");
        server.runCommand("summon minecraft:falling_block 3.5 101.5 0.5 {NoGravity:1b,Time:1,BlockState:{Name:\"minecraft:lime_carpet\"}}");
        context.waitTicks(2);
        heard.clear();
        server.runCommand("playsound minecraft:block.wool.place block @a 3.5 101.5 0.5");
        context.waitTicks(8);
        check("cushion on a slab: place sound restored", heard.contains("minecraft:entity.cushion.place"), heard);
        context.waitTicks(20);
        heard.clear();
        server.runCommand("playsound minecraft:block.wool.break block @a 3.5 101.5 0.5");
        context.waitTicks(8);
        check("cushion on a slab: get-up sound restored", heard.contains("minecraft:entity.cushion.get_up"), heard);

        // Sounds off: wool stays wool.
        Config.cushionSounds = false;
        heard.clear();
        server.runCommand("playsound minecraft:block.wool.place block @a 3.5 101.5 0.5");
        context.waitTicks(8);
        check("cushion: sounds off keeps the wool sound", heard.contains("minecraft:block.wool.place"), heard);
        Config.cushionSounds = true;

        // Many cushions: render a crowd (stress) and make sure nothing breaks.
        server.runCommand("kill @e[type=minecraft:falling_block]");
        for (int x = -10; x <= 10; x += 2)
            for (int z = -10; z <= 10; z += 2)
                server.runCommand("summon minecraft:falling_block " + x + ".5 101 " + z + ".5 {NoGravity:1b,Time:1,BlockState:{Name:\"minecraft:"
                        + (Math.abs(x + z) % 4 == 0 ? "white" : "orange") + "_carpet\"}}");
        server.runCommand("tp @p 0.5 104 -11.5 0 25");
        context.waitTicks(20);
        long count = context.computeOnClient(mc -> mc.level.getEntitiesOfClass(FallingBlockEntity.class, mc.player.getBoundingBox().inflate(40)).stream()
                .filter(e -> CushionDetector.cushionColor(e) != null).count());
        check("cushion: 121 cushions recognised", count == 121, count);
        context.takeScreenshot("audit_cushion_crowd");
        server.runCommand("kill @e[type=minecraft:falling_block]");
        server.runCommand("setblock 3 101 0 minecraft:air");
    }

    private static Entity cushion(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof FallingBlockEntity f && f.isNoGravity()) return e;
        throw new IllegalStateException("no cushion stand-in");
    }

    // items

    private static void items(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            ItemStack standIn = new ItemStack(Items.QUARTZ_STAIRS);
            standIn.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of("minecraft:white_wool_stairs"), List.of()));
            Identifier vanillaModel = standIn.get(DataComponents.ITEM_MODEL);
            check("items: stand-in resolves to the 26.x item", Identifier.withDefaultNamespace("white_wool_stairs").equals(FutureItems.originalId(standIn)), FutureItems.originalId(standIn));
            check("items: stand-in uses the 26.x model", Identifier.withDefaultNamespace("white_wool_stairs").equals(FutureItems.remapModel(standIn, vanillaModel)), "");
            Config.futureItems = false;
            check("items: option off keeps the stand-in model", vanillaModel.equals(FutureItems.remapModel(standIn, vanillaModel)), "");
            Config.futureItems = true;

            ItemStack serverCustom = new ItemStack(Items.DIAMOND_SWORD);
            serverCustom.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(1f), List.of(), List.of("donut:legendary_sword"), List.of()));
            check("items: server custom items are left alone", FutureItems.originalId(serverCustom) == null, "");

            ItemStack named = new ItemStack(Items.STONE);
            named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable("vb.item.not_a_real_item"));
            check("items: unknown vb.item name ignored", FutureItems.originalId(named) == null, "");

            ItemStack byName = new ItemStack(Items.GRANITE);
            byName.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable("vb.item.cinnabar"));
            check("items: recognised from the vb.item name alone", Identifier.withDefaultNamespace("cinnabar").equals(FutureItems.originalId(byName)), "");
            check("items: empty stack is safe", FutureItems.originalId(ItemStack.EMPTY) == null, "");
        });
    }

    // sulfur cube

    private static void sulfur(ClientGameTestContext context, TestServerContext server) {
        floor(server);
        server.runCommand("summon minecraft:slime 0.5 101 0.5 {Size:1," + SULFUR_NAME + "}");
        server.runCommand("tp @p 0.5 101 -4.5 0 20");
        context.waitTicks(5);
        boolean renderer = context.computeOnClient(mc -> (Object) mc.getEntityRenderDispatcher().getRenderer(slime(mc)) instanceof SulfurCubeRenderer);
        check("sulfur: drawn with the sulfur cube renderer", renderer, "");

        // Landing particles: count over a few jumps.
        Map<String, Integer> seen = new java.util.TreeMap<>();
        for (int i = 0; i < 12; i++) {
            context.waitTicks(10);
            context.computeOnClient(Audit::particles).forEach((k, v) -> seen.merge(k, v, Math::max));
        }
        check("sulfur: lands with sulfur goo particles", seen.containsKey("Goo"), seen);
        check("sulfur: no slime-ball particles", !seen.containsKey("BreakingItemParticle"), seen);

        Config.sulfurCube = false;
        boolean vanilla = context.computeOnClient(mc -> !((Object) mc.getEntityRenderDispatcher().getRenderer(slime(mc)) instanceof SulfurCubeRenderer));
        check("sulfur: option off shows the plain slime", vanilla, "");
        Config.sulfurCube = true;

        // Body-slot contents.
        server.runCommand("kill @e[type=minecraft:slime]");
        server.runCommand("summon minecraft:slime 0.5 101 0.5 {NoAI:1b,Size:1," + SULFUR_NAME
                + ",equipment:{body:{id:\"minecraft:quartz_stairs\",count:1,components:{\"minecraft:custom_model_data\":{strings:[\"minecraft:white_wool_stairs\"]}}}}}");
        server.runCommand("summon minecraft:slime 3.5 101 0.5 {NoAI:1b,Size:1," + SULFUR_NAME + ",equipment:{body:{id:\"minecraft:player_head\",count:1}}}");
        server.runCommand("summon minecraft:slime -3.5 101 0.5 {NoAI:1b,Size:3," + SULFUR_NAME + ",equipment:{body:{id:\"minecraft:diamond_ore\",count:1}}}");
        context.waitTicks(10);
        String contents = context.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof Slime s)) continue;
                BlockState inside = containedBlock(s.getItemBySlot(EquipmentSlot.BODY));
                sb.append(Math.round(e.getX() - 0.5)).append('=').append(inside == null ? "core" : BuiltInRegistries.BLOCK.getKey(inside.getBlock())).append(' ');
            }
            return sb.toString();
        });
        check("sulfur: 26.x stand-in block shown as the real block", contents.contains("0=leapfrog:white_wool_stairs"), contents);
        check("sulfur: swallowed head keeps the core (no stray block model)", contents.contains("3=core"), contents);
        check("sulfur: big cube with ore", contents.contains("-4=minecraft:diamond_ore"), contents);
        server.runCommand("tp @p 0.5 102 -5.5 0 20");
        context.waitTicks(10);
        context.takeScreenshot("audit_sulfur_contents");

        // Captured data life cycle.
        int id = context.computeOnClient(mc -> slime(mc).getId());
        SulfurCubeData.setFuse(id, 40);
        SulfurCubeData.setBaby(id, true);
        check("sulfur: fuse counts down", SulfurCubeData.fuseRemaining(id) > 0, SulfurCubeData.fuseRemaining(id));
        server.runCommand("tp @e[type=minecraft:slime] 0 -200 0");
        context.waitTicks(40);
        check("sulfur: data forgotten when the entity leaves", !SulfurCubeData.isBaby(id) && SulfurCubeData.fuseRemaining(id) == 0, "");

        // Magma cube with the same name must stay a magma cube.
        server.runCommand("summon minecraft:magma_cube 0.5 101 0.5 {NoAI:1b,Size:1," + SULFUR_NAME + "}");
        context.waitTicks(3);
        boolean magma = context.computeOnClient(mc -> {
            for (Entity e : mc.level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.MAGMA_CUBE) return !SulfurCubeRenderer.isSulfurCube(e);
            return false;
        });
        check("sulfur: magma cube is never treated as a sulfur cube", magma, "");
        server.runCommand("kill @e[type=!minecraft:player]");
    }

    private static BlockState containedBlock(ItemStack stack) {
        try {
            var m = SulfurCubeRenderer.class.getDeclaredMethod("containedBlock", ItemStack.class);
            m.setAccessible(true);
            return (BlockState) m.invoke(null, stack);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Slime slime(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof Slime s && SulfurCubeRenderer.isSulfurCube(s)) return s;
        throw new IllegalStateException("no sulfur cube");
    }

    // geyser

    private static void geyser(ClientGameTestContext context, TestServerContext server) {
        floor(server);
        server.runCommand("gamemode survival @p");
        server.runCommand("effect give @p minecraft:resistance 600 4 true");
        buildGeyser(server);
        double rise = launch(context, server);
        check("geyser: launches the player", rise > 1.5, String.format("%.2f", rise));

        Config.safeMode = true;
        rise = launch(context, server);
        check("geyser: safe mode never moves the player", rise < 0.2, String.format("%.2f", rise));
        Config.safeMode = false;
        Config.geyserLaunch = false;
        rise = launch(context, server);
        check("geyser: launch option off", rise < 0.2, String.format("%.2f", rise));
        Config.geyserLaunch = true;

        Config.geyserEffects = false;
        server.runCommand("tp @p 5.5 101 4.5 90 5");
        context.waitTicks(60);
        Map<String, Integer> p = context.computeOnClient(Audit::particles);
        check("geyser: effects off spawns no plume", !p.containsKey("Plume") && !p.containsKey("Eruption"), p);
        Config.geyserEffects = true;

        // Chunk unload/reload: the geyser must be tracked again.
        long packed = BlockPos.asLong(0, 98, 4);
        LongSet positions = Audit.staticField(dev.leapfrog.geyser.GeyserLogic.class, "POSITIONS");
        check("geyser: tracked", positions.contains(packed), "");
        server.runCommand("tp @p 3000.5 200 3000.5");
        context.waitTicks(60);
        check("geyser: forgotten when its chunk unloads", !positions.contains(packed), positions.size());
        server.runCommand("tp @p 0.5 101 -3.5");
        context.waitTicks(40);
        context.waitFor(mc -> mc.level.getChunkSource().hasChunk(0, 0), 200);
        context.waitTicks(10);
        check("geyser: tracked again after the chunk comes back", positions.contains(packed), positions.size());
        rise = launch(context, server);
        check("geyser: still launches after reload", rise > 1.5, String.format("%.2f", rise));

        server.runCommand("setblock 0 98 4 minecraft:stone");
        context.waitTicks(3);
        check("geyser: untracked when the block is replaced", !positions.contains(packed), "");
        server.runCommand("gamemode creative @p");
    }

    private static void buildGeyser(TestServerContext server) {
        server.runCommand("setblock 0 98 4 leapfrog:potent_sulfur[potent_sulfur_state=erupting]");
        server.runCommand("setblock 0 99 4 minecraft:water");
        server.runCommand("setblock 0 100 4 minecraft:water");
    }

    private static double launch(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @p 0.5 101 4.5 0 0");
        context.waitTicks(3);
        double startY = context.computeOnClient(mc -> mc.player.getY());
        double maxY = startY;
        for (int i = 0; i < 30; i++) {
            context.waitTick();
            maxY = Math.max(maxY, context.computeOnClient(mc -> mc.player.getY()));
        }
        server.runCommand("tp @p 0.5 101 -3.5");
        context.waitTicks(20);
        return maxY - startY;
    }

    // restored blocks

    private static void blocks(ClientGameTestContext context, TestServerContext server) {
        floor(server);
        server.runCommand("gamemode survival @p");
        server.runCommand("effect give @p minecraft:resistance 600 4 true");

        server.runCommand("setblock 0 101 0 leapfrog:white_wool_slab[type=bottom]");
        standOn(context, server, 0, 0);
        double y = context.computeOnClient(mc -> mc.player.getY());
        check("blocks: standing on a wool slab (y = .5)", Math.abs(y - 101.5) < 0.01, y);

        server.runCommand("setblock 0 101 0 leapfrog:white_wool_stairs[facing=south,half=bottom]");
        standOn(context, server, 0, 0);
        y = context.computeOnClient(mc -> mc.player.getY());
        check("blocks: wool stairs have a collision box", y > 101.4, y);

        server.runCommand("setblock 0 101 0 leapfrog:golden_dandelion");
        standOn(context, server, 0, 0);
        y = context.computeOnClient(mc -> mc.player.getY());
        check("blocks: flowers have no collision", Math.abs(y - 101.0) < 0.01, y);

        server.runCommand("setblock 0 101 0 leapfrog:sulfur_spike");
        context.waitTicks(3);
        boolean spikeShape = context.computeOnClient(mc -> !mc.level.getBlockState(new BlockPos(0, 101, 0))
                .getCollisionShape(mc.level, new BlockPos(0, 101, 0)).isEmpty());
        check("blocks: sulfur spike keeps its (offset) collision", spikeShape, "");

        server.runCommand("setblock 0 101 0 leapfrog:white_wool_stairs[waterlogged=true]");
        context.waitTicks(3);
        boolean water = context.computeOnClient(mc -> mc.level.getFluidState(new BlockPos(0, 101, 0)).is(Fluids.WATER));
        check("blocks: waterlogged stairs hold water", water, "");

        server.runCommand("setblock 4 101 4 leapfrog:poplar_planks");
        server.runCommand("setblock 4 102 4 minecraft:torch");
        context.waitTicks(5);
        boolean torch = context.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(4, 102, 4)).is(net.minecraft.world.level.block.Blocks.TORCH));
        check("blocks: a torch stays on poplar planks", torch, "");
        boolean solid = context.computeOnClient(mc -> {
            BlockState s = mc.level.getBlockState(new BlockPos(4, 101, 4));
            return s.isSolidRender() && s.isFaceSturdy(mc.level, new BlockPos(4, 101, 4), Direction.UP) && s.isCollisionShapeFullBlock(mc.level, new BlockPos(4, 101, 4));
        });
        check("blocks: full 26.x block is solid, sturdy and occluding", solid, "");

        // Light: the first restored block that emits light, inside a dark stone box.
        String lit = context.computeOnClient(mc -> {
            for (FutureBlock b : FutureBlocks.all().values())
                for (BlockState st : b.getStateDefinition().getPossibleStates())
                    if (st.getLightEmission() > 0) {
                        String str = st.toString();
                        int i = str.indexOf('[');
                        return b.info().name() + (i >= 0 ? str.substring(i) : "");
                    }
            return null;
        });
        if (lit != null) {
            server.runCommand("fill 6 101 6 10 105 10 minecraft:stone");
            server.runCommand("setblock 8 103 8 leapfrog:" + lit);
            server.runCommand("setblock 8 104 8 minecraft:air");
            context.waitTicks(10);
            int light = context.computeOnClient(mc -> mc.level.getBrightness(LightLayer.BLOCK, new BlockPos(8, 104, 8)));
            check("blocks: " + lit + " emits light", light > 0, light);
        } else {
            check("blocks: no 26.x block state emits light (nothing to test)", true, "");
        }
        server.runCommand("gamemode creative @p");
        server.runCommand("fill 6 101 6 10 105 10 minecraft:air");
        context.takeScreenshot("audit_blocks");
    }

    private static void standOn(ClientGameTestContext context, TestServerContext server, int x, int z) {
        server.runCommand("tp @p " + x + ".5 103 " + z + ".5");
        context.waitTicks(30);
    }

    // keybind

    private static void keybind(ClientGameTestContext context) {
        context.setScreen(() -> null);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_K);
        context.waitTicks(3);
        boolean opened = context.computeOnClient(mc -> mc.screen instanceof SettingsScreen);
        check("keybind: K opens the settings", opened, "");
        context.setScreen(() -> null);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyChat);
        context.waitTicks(3);
        context.getInput().typeChars("k");
        context.waitTicks(3);
        boolean stillChat = context.computeOnClient(mc -> mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        check("keybind: typing K in the chat does not open the settings", stillChat, "");
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    // UI

    private static void ui(ClientGameTestContext context, TestServerContext server) {
        context.runOnClient(mc -> {
            mc.getWindow().setWindowed(1280, 720);
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        });
        context.waitTicks(2);
        SettingsScreen[] s = new SettingsScreen[1];
        context.setScreen(() -> s[0] = new SettingsScreen(null));
        context.waitTicks(5);

        // Tabs by mouse.
        for (int i = 0; i <= 4; i++) {
            int tab = i;
            clickGui(context, tabX(context, tab) + 10, 30);
            context.waitTicks(2);
            int selected = context.computeOnClient(mc -> Audit.field(s[0], "tab"));
            check("ui: tab " + tab + " opens by click", selected == tab, selected);
        }

        // Toggle the first option of "Visual" by clicking its row.
        clickGui(context, tabX(context, 1) + 10, 30);
        context.waitTicks(2);
        boolean before = Config.cushionModel;
        int colX = context.computeOnClient(mc -> (mc.getWindow().getGuiScaledWidth() - Math.min(mc.getWindow().getGuiScaledWidth() - 40, 320)) / 2);
        clickGui(context, colX + 10, 72 + 8);
        context.waitTicks(2);
        check("ui: clicking an option toggles it", Config.cushionModel != before, Config.cushionModel);
        clickGui(context, colX + 10, 72 + 8);
        context.waitTicks(2);
        check("ui: clicking again restores it", Config.cushionModel == before, Config.cushionModel);

        // Safe mode footer button.
        boolean safe = Config.safeMode;
        int[] footer = context.computeOnClient(mc -> new int[]{mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
        int bw = Math.min(150, (footer[0] - 30) / 2);
        clickGui(context, footer[0] / 2 - bw - 4 + 10, footer[1] - 20);
        context.waitTicks(2);
        check("ui: safe mode button toggles", Config.safeMode != safe, Config.safeMode);
        clickGui(context, footer[0] / 2 - bw - 4 + 10, footer[1] - 20);
        context.waitTicks(2);

        // Search through the keyboard.
        context.getInput().typeChars("baby");
        context.waitTicks(2);
        String q = context.computeOnClient(mc -> Audit.field(s[0], "query"));
        check("ui: typing goes straight to the search", "baby".equals(q), q);
        context.takeScreenshot("audit_ui_search_accents");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(2);
        boolean open = context.computeOnClient(mc -> mc.screen == s[0]);
        String q2 = context.computeOnClient(mc -> Audit.field(s[0], "query"));
        check("ui: Escape first clears the search", open && q2.isEmpty(), "open=" + open + " query='" + q2 + "'");
        context.getInput().typeChars("zzzzqqq");
        context.waitTicks(2);
        context.takeScreenshot("audit_ui_no_result");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(2);
        context.getInput().typeChars("x".repeat(90));
        context.waitTicks(2);
        String longQ = context.computeOnClient(mc -> Audit.field(s[0], "query"));
        check("ui: very long search is capped", longQ.length() <= 64, longQ.length());
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(2);

        // Edit the server list through its text field.
        context.getInput().typeChars("servers");
        context.waitTicks(3);
        boolean autoBefore = Config.autoConnect26;
        int[] box = context.computeOnClient(mc -> {
            net.minecraft.client.gui.components.EditBox b = Audit.field(s[0], "hostsBox");
            return new int[]{b.getX(), b.getY(), b.visible ? 1 : 0};
        });
        check("ui: server list field visible in the search results", box[2] == 1, "");
        clickGui(context, box[0] + 20, box[1] + 9);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_END);
        context.getInput().typeChars(", Example.COM");
        context.waitTicks(2);
        check("ui: server list field edits the hosts", Config.autoConnectHosts.contains("example.com"), Config.autoConnectHosts);
        check("ui: clicking the field does not toggle a neighbouring option", Config.autoConnect26 == autoBefore, "");
        context.takeScreenshot("audit_ui_hosts");
        Config.autoConnectHosts = new java.util.ArrayList<>(List.of("donutsmp.net", "*.donutsmp.net"));
        Config.save();
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(2);
        context.setScreen(() -> s[0] = new SettingsScreen(null));
        context.waitTicks(3);

        // Scroll far past both ends.
        clickGui(context, tabX(context, 0) + 10, 30);
        context.getInput().setCursorPos(640, 400);
        for (int i = 0; i < 20; i++) context.getInput().scroll(-5);
        context.waitTicks(2);
        float scroll = context.computeOnClient(mc -> Audit.field(s[0], "scroll"));
        context.takeScreenshot("audit_ui_scrolled_bottom");
        for (int i = 0; i < 20; i++) context.getInput().scroll(5);
        context.waitTicks(2);
        float top = context.computeOnClient(mc -> Audit.field(s[0], "scroll"));
        check("ui: scrolling stays within bounds", scroll > 0 && top == 0f, "bottom=" + scroll + " top=" + top);

        // Every GUI scale and some window sizes, every tab.
        int[][] sizes = {{854, 480}, {1920, 1080}, {640, 480}, {2560, 1080}};
        for (int[] size : sizes) {
            for (int scale = 1; scale <= 4; scale++) {
                int sc = scale;
                context.runOnClient(mc -> {
                    mc.getWindow().setWindowed(size[0], size[1]);
                    mc.options.guiScale().set(sc);
                    mc.resizeDisplay();
                });
                context.waitTicks(1);
                for (int t = 0; t <= 4; t++) {
                    int tt = t;
                    context.runOnClient(mc -> s[0].selectCategory(tt));
                    context.waitTicks(1);
                }
                String overflow = context.computeOnClient(mc -> {
                    int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
                    return w + "x" + h;
                });
                check("ui: renders at " + size[0] + "x" + size[1] + " scale " + sc + " (" + overflow + " gui units)", true, "");
                if (size[0] == 640 && sc == 3) context.takeScreenshot("audit_ui_small_scale3");
            }
        }
        context.runOnClient(mc -> {
            mc.getWindow().setWindowed(854, 480);
            mc.options.guiScale().set(0);
            mc.resizeDisplay();
        });
        context.waitTicks(2);
        context.takeScreenshot("audit_ui_auto_scale");

        // Resize while searching keeps the search.
        context.runOnClient(mc -> s[0].search("geyser"));
        context.runOnClient(mc -> {
            mc.getWindow().setWindowed(1280, 720);
            mc.resizeDisplay();
        });
        context.waitTicks(2);
        String kept = context.computeOnClient(mc -> Audit.field(s[0], "query"));
        check("ui: window resize keeps the search", "geyser".equals(kept), kept);
        context.runOnClient(mc -> s[0].search(""));

        // Escape closes and saves.
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(3);
        boolean closed = context.computeOnClient(mc -> mc.screen == null);
        check("ui: Escape closes the screen in game", closed, "");
    }

    private static int tabX(ClientGameTestContext context, int tab) {
        return context.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth();
            int tabW = Math.min(70, (w - 20) / 5);
            return (w - tabW * 5) / 2 + tab * tabW;
        });
    }

    private static void clickGui(ClientGameTestContext context, int x, int y) {
        double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(x * scale + scale / 2, y * scale + scale / 2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void uiFromTitle(ClientGameTestContext context) {
        context.setScreen(() -> new SettingsScreen(new TitleScreen()));
        context.waitTicks(10);
        context.takeScreenshot("audit_ui_from_title");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(3);
        boolean back = context.computeOnClient(mc -> mc.screen instanceof TitleScreen);
        check("ui: from the title screen (Mod Menu), Escape goes back to it", back, "");
    }
}
