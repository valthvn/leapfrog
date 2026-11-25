package dev.leapfrog.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Reproduces in singleplayer exactly what a ViaBackwards server sends to a 1.21.11 client for
 * 26.3 content, then takes screenshots so the restored visuals can be checked by eye.
 */
public class StandInRenderTest implements FabricClientGameTest {
    private static final String[] COLORS = {"white", "red", "lime", "light_blue", "yellow", "purple", "black", "orange"};

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("LEAPFROG_AUDIT") != null) return;
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @p");
            server.runCommand("fill -6 100 -6 6 100 6 minecraft:oak_planks");
            server.runCommand("fill -6 101 -6 6 110 6 minecraft:air");
            server.runCommand("tp @p 0.5 101 -3.5 0 40");

            // Cushions: ViaBackwards turns them into a NoGravity falling_block of carpet.
            for (int i = 0; i < COLORS.length; i++) {
                int x = -4 + i;
                int rot = (i % 4) * 90;
                server.runCommand("summon minecraft:falling_block " + x + ".5 101 0.5 {NoGravity:1b,Time:1,Rotation:[" + rot
                        + "f,0f],BlockState:{Name:\"minecraft:" + COLORS[i] + "_carpet\"}}");
            }
            // One stand-in with a "Cushion" name tag, as reported on DonutSMP.
            server.runCommand("summon minecraft:falling_block 0.5 101 2.5 {NoGravity:1b,Time:1,CustomName:\"Cushion\",CustomNameVisible:1b,"
                    + "BlockState:{Name:\"minecraft:pink_carpet\"}}");
            // Control: a real carpet block must stay a carpet.
            server.runCommand("setblock 2 101 2 minecraft:blue_carpet");

            // Items exactly as ViaBackwards rewrites them (custom_model_data string + vb.item name).
            giveStandIn(server, 0, "red_carpet", "red_cushion", "Red Cushion");
            giveStandIn(server, 1, "quartz_stairs", "white_wool_stairs", "White Wool Stairs");
            giveStandIn(server, 2, "purpur_slab", "magenta_concrete_slab", "Magenta Concrete Slab");
            giveStandIn(server, 3, "birch_log", "poplar_log", "Poplar Log");
            giveStandIn(server, 4, "granite", "cinnabar", "Cinnabar");
            giveStandIn(server, 5, "sandstone", "sulfur", "Sulfur");
            giveStandIn(server, 6, "dandelion", "golden_dandelion", "Golden Dandelion");
            giveStandIn(server, 7, "powder_snow_bucket", "sulfur_cube_bucket", "Sulfur Cube Bucket");
            server.runCommand("item replace entity @p hotbar.8 with minecraft:quartz_stairs");
            // Dropped stand-in item, rendered in the world.
            server.runCommand("summon minecraft:item 3.5 101.5 0.5 {NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:quartz_stairs\",count:1,"
                    + "components:{\"minecraft:custom_model_data\":{strings:[\"minecraft:white_wool_stairs\"]}}}}");

            context.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
            context.waitTicks(5);
            context.takeScreenshot("standins_overview");

            server.runCommand("tp @p 0.5 102.2 0.5 0 90");
            context.waitTicks(10);
            context.takeScreenshot("standins_cushions_topdown");

            testSounds(context, server);
            testSitting(context, server);
            testGeyser(context, server);
            testBabies(context, server);
        }
    }

    private static final String[] BABIES = {"cow", "pig", "chicken", "sheep", "cat", "wolf", "fox", "rabbit", "panda", "llama",
            "goat", "horse", "donkey", "mooshroom", "polar_bear", "armadillo", "turtle", "bee", "camel", "ocelot",
            "hoglin", "strider", "sniffer", "axolotl", "dolphin", "villager", "zombie", "piglin", "drowned", "squid"};

    /** Baby mobs in a grid, frozen, to compare with 26.3. */
    private static void testBabies(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("fill -12 98 -12 12 99 12 minecraft:stone");
        server.runCommand("fill -12 100 -12 12 100 12 minecraft:grass_block");
        server.runCommand("fill -12 101 -12 12 110 12 minecraft:air");
        for (int i = 0; i < BABIES.length; i++) {
            int x = -10 + (i % 10) * 2;
            int z = -2 + (i / 10) * 3;
            String extra = BABIES[i].equals("zombie") || BABIES[i].equals("drowned") ? "IsBaby:1b" : BABIES[i].equals("piglin") ? "IsBaby:1b" : "Age:-24000";
            server.runCommand("summon minecraft:" + BABIES[i] + " " + x + ".5 101 " + z + ".5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]," + extra + "}");
        }
        // Sulfur cubes exactly as ViaBackwards sends them: slimes named with the vb translation key.
        server.runCommand("summon minecraft:slime 6.5 101 -6.5 {NoAI:1b,Size:1,CustomName:{translate:\"vb.entity.sulfur_cube\",fallback:\"Sulfur Cube\",color:\"white\",italic:false},CustomNameVisible:1b}");
        server.runCommand("summon minecraft:slime 8.5 101 -6.5 {NoAI:1b,Size:0,CustomName:{translate:\"vb.entity.sulfur_cube\",fallback:\"Sulfur Cube\"},CustomNameVisible:1b}");
        server.runCommand("tp @p -0.5 103 -6.5 0 30");
        context.waitTicks(40);
        context.takeScreenshot("babies");
        // Swallowed block (body slot, passed through by ViaBackwards), baby flag and primed fuse (captured from the 26.3 stream).
        String name = "CustomName:{translate:\"vb.entity.sulfur_cube\",fallback:\"Sulfur Cube\"}";
        server.runCommand("summon minecraft:slime 4.5 101 -6.5 {NoAI:1b,Size:0," + name + "}");
        server.runCommand("summon minecraft:slime 10.5 101 -6.5 {NoAI:1b,Size:1," + name + ",equipment:{body:{id:\"minecraft:tnt\",count:1}}}");
        server.runCommand("summon minecraft:slime 12.5 101 -6.5 {NoAI:1b,Size:1," + name + ",equipment:{body:{id:\"minecraft:tnt\",count:1}}}");
        server.runCommand("tp @p 8.5 102 -11.5 0 20");
        context.waitTicks(20);
        context.runOnClient(mc -> {
            for (var e : mc.level.entitiesForRendering()) {
                if (!dev.leapfrog.sulfur.SulfurCubeRenderer.isSulfurCube(e)) continue;
                if (Math.abs(e.getX() - 4.5) < 0.1) dev.leapfrog.sulfur.SulfurCubeData.setBaby(e.getId(), true);
                if (Math.abs(e.getX() - 12.5) < 0.1) dev.leapfrog.sulfur.SulfurCubeData.setFuse(e.getId(), 6);
                if (e instanceof net.minecraft.world.entity.LivingEntity l)
                    System.out.println("[Leapfrog test] sulfur cube at x=" + e.getX() + " body=" + l.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY));
            }
        });
        context.waitTicks(2);
        context.takeScreenshot("sulfur_cubes");
    }

    /** 26.3 seats a cushion rider at cushion + 0.25 (the stand-in falling_block would give + 0.98). */
    private static void testSitting(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("kill @e[type=minecraft:falling_block]");
        server.runCommand("tp @p 0.5 101 -3.5 0 40");
        server.runCommand("summon minecraft:falling_block 0.5 101 0.5 {NoGravity:1b,Time:1,BlockState:{Name:\"minecraft:lime_carpet\"}}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=minecraft:falling_block,limit=1,sort=nearest]");
        context.waitTicks(10);
        double[] offset = context.computeOnClient(mc -> {
            var vehicle = mc.player.getVehicle();
            if (vehicle == null) return null;
            return new double[]{mc.player.getY() - vehicle.getY(), vehicle.getBbHeight()};
        });
        if (offset == null) throw new AssertionError("Player is not riding the cushion stand-in");
        // Player vehicle attachment is 0.6, so seated feet must be 0.25 - 0.6 = -0.35 below the cushion origin.
        if (Math.abs(offset[0] - (0.25 - 0.6)) > 0.02 || Math.abs(offset[1] - 0.25) > 0.001) {
            throw new AssertionError("Wrong seat: rider offset " + offset[0] + ", cushion height " + offset[1]);
        }
        System.out.println("[Leapfrog test] OK seated at cushion + " + (offset[0] + 0.6) + " (26.3: 0.25)");
        context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(5);
        context.takeScreenshot("sitting_on_cushion");
        context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        server.runCommand("ride @p dismount");
        context.waitTicks(5);
    }

    /** A restored potent sulfur in eruption: plume particles and the upward push of the local player. */
    private static void testGeyser(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("fill -6 98 -6 6 99 6 minecraft:stone");
        server.runCommand("setblock 0 98 4 leapfrog:potent_sulfur[potent_sulfur_state=erupting]");
        server.runCommand("setblock 0 99 4 minecraft:water");
        server.runCommand("setblock 0 100 4 minecraft:water");
        server.runCommand("tp @p 0.5 101 4.5 0 0");
        context.waitTicks(3);
        double startY = context.computeOnClient(mc -> mc.player.getY());
        double maxY = startY;
        for (int i = 0; i < 30; i++) {
            context.waitTick();
            maxY = Math.max(maxY, context.computeOnClient(mc -> mc.player.getY()));
        }
        if (maxY - startY < 1.5) throw new AssertionError("Geyser did not launch the player (rose " + (maxY - startY) + ")");
        System.out.println("[Leapfrog test] OK geyser launched the player " + String.format("%.2f", maxY - startY) + " blocks");

        server.runCommand("tp @p 5.5 102 4.5 90 5");
        for (int i = 0; i < 4; i++) {
            context.waitTicks(10);
            String count = context.computeOnClient(mc -> {
                java.util.TreeMap<String, Integer> byClass = new java.util.TreeMap<>();
                try {
                    var f = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");
                    f.setAccessible(true);
                    for (Object g : ((java.util.Map<?, ?>) f.get(mc.particleEngine)).values()) {
                        for (Object p : ((net.minecraft.client.particle.ParticleGroup<?>) g).getAll()) {
                            byClass.merge(p.getClass().getSimpleName(), 1, Integer::sum);
                        }
                    }
                } catch (ReflectiveOperationException e) {
                    return e.toString();
                }
                return byClass.toString();
            });
            System.out.println("[Leapfrog test] particles alive: " + count);
        }
        context.takeScreenshot("geyser_eruption");
        server.runCommand("setblock 0 98 4 leapfrog:potent_sulfur[potent_sulfur_state=wet]");
        server.runCommand("tp @p 2.5 101.5 4.5 90 45");
        context.waitTicks(40);
        context.takeScreenshot("geyser_wet_bubbles");
    }

    /** ViaBackwards turns cushion sounds into wool sounds; check they come back as the 26.3 ones. */
    private static void testSounds(ClientGameTestContext context, TestServerContext server) {
        List<String> heard = new CopyOnWriteArrayList<>();
        context.runOnClient(mc -> mc.getSoundManager().addListener((instance, events, range) -> heard.add(instance.getIdentifier().toString())));
        server.runCommand("kill @e[type=minecraft:falling_block]");
        server.runCommand("tp @p 0.5 101 -3.5 0 40");
        context.waitTicks(5);

        // Freshly placed cushion: wool.place at the cushion position -> cushion.place.
        server.runCommand("summon minecraft:falling_block 0.5 101 0.5 {NoGravity:1b,Time:1,BlockState:{Name:\"minecraft:red_carpet\"}}");
        expectSound(context, server, heard, "playsound minecraft:block.wool.place block @a 0.5 101 0.5", "minecraft:entity.cushion.place");
        context.waitTicks(30);
        // Old cushion: wool.place -> somebody sat down.
        expectSound(context, server, heard, "playsound minecraft:block.wool.place block @a 0.5 101 0.5", "minecraft:entity.cushion.sit");
        // Cushion still there after wool.break -> got up.
        expectSound(context, server, heard, "playsound minecraft:block.wool.break block @a 0.5 101 0.5", "minecraft:entity.cushion.get_up");
        // Cushion removed, then wool.break -> broken.
        server.runCommand("kill @e[type=minecraft:falling_block]");
        context.waitTicks(2);
        expectSound(context, server, heard, "playsound minecraft:block.wool.break block @a 0.5 101 0.5", "minecraft:entity.cushion.break");
        // A real wool block (block-centre position) must keep its own sound.
        expectSound(context, server, heard, "playsound minecraft:block.wool.place block @a 3.5 101.5 3.5", "minecraft:block.wool.place");
        // New 26.x block sounds are defined and playable.
        expectSound(context, server, heard, "playsound minecraft:block.cinnabar.place block @a 0.5 101.5 0.5", "minecraft:block.cinnabar.place");

        // Placing a stand-in (quartz stairs that really are white wool stairs) sounds like wool.
        server.runCommand("tp @p 0.5 101 -3.5 0 60");
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
        context.waitTicks(5);
        heard.clear();
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        if (!heard.contains("minecraft:block.wool.place") || heard.contains("minecraft:block.stone.place")) {
            throw new AssertionError("Placing wool stairs stand-in: expected block.wool.place, heard " + heard);
        }
        System.out.println("[Leapfrog test] OK stand-in place sound (heard " + heard + ")");
    }

    private static void expectSound(ClientGameTestContext context, TestServerContext server, List<String> heard, String command, String expected) {
        heard.clear();
        server.runCommand(command);
        context.waitTicks(8);
        if (!heard.contains(expected)) {
            throw new AssertionError("After '" + command + "' expected " + expected + " but heard " + heard);
        }
        System.out.println("[Leapfrog test] OK " + expected + " (heard " + heard + ")");
    }

    private static void giveStandIn(TestServerContext server, int slot, String standIn, String original, String name) {
        server.runCommand("item replace entity @p hotbar." + slot + " with minecraft:" + standIn
                + "[minecraft:custom_model_data={strings:[\"minecraft:" + original + "\"]},"
                + "minecraft:custom_name={translate:\"vb.item." + original + "\",fallback:\"26.3 " + name + "\",italic:false}]");
    }
}
