package dev.leapfrog.test;

import dev.leapfrog.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Screenshots of every settings category, at two GUI scales, over a world. */
public class UiScreenshotTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("LEAPFROG_AUDIT") != null) return;
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            for (int scale : new int[]{2, 3}) {
                context.runOnClient(mc -> {
                    if (scale == 3) mc.getWindow().setWindowed(1600, 900);
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                });
                context.waitTicks(2);
                SettingsScreen[] holder = new SettingsScreen[1];
                context.setScreen(() -> holder[0] = new SettingsScreen(null));
                int count = context.computeOnClient(mc -> holder[0].categoryCount());
                for (int i = 0; i < count; i++) {
                    int index = i;
                    context.runOnClient(mc -> holder[0].selectCategory(index));
                    context.waitTicks(12);
                    context.takeScreenshot("ui_scale" + scale + "_cat" + i);
                }
                context.runOnClient(mc -> holder[0].search("geyser"));
                context.waitTicks(12);
                context.takeScreenshot("ui_scale" + scale + "_search");
                context.setScreen(() -> null);
            }
        }
    }
}
