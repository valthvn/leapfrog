package dev.leapfrog.ui;

import dev.leapfrog.Config;
import dev.leapfrog.Features;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Settings screen drawn with vanilla sprites and plain fills, so it looks like the options menu. */
public class SettingsScreen extends Screen {
    private static final Identifier TAB = Identifier.withDefaultNamespace("widget/tab");
    private static final Identifier TAB_HOVER = Identifier.withDefaultNamespace("widget/tab_highlighted");
    private static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("widget/tab_selected");
    private static final Identifier TAB_SELECTED_HOVER = Identifier.withDefaultNamespace("widget/tab_selected_highlighted");
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");

    private static final int LIST_BG = 0x55000000;
    private static final int ROW_HOVER = 0x22FFFFFF;
    private static final int CARD_BG = 0x33000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int DARK_GRAY = 0xFF707070;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;

    private static final String SEARCH_HINT = "Search options...";
    private static final String INFO = "Info";

    private final @Nullable Screen parent;
    private final List<Section> sections = new ArrayList<>();
    private int tab;
    private String query = "";
    private float scroll;
    private @Nullable EditBox searchBox, hostsBox;

    private int colX, colW, tabsY, tabW, listTop, listBottom;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.literal("Leapfrog"));
        this.parent = parent;
        build();
    }

    private void build() {
        Section visual = new Section("Visual");
        visual.add(new Toggle("3D cushions", "The real 26.3 model instead of a floating carpet, without the name tag.",
                () -> Config.cushionModel, v -> Config.cushionModel = v, false));
        visual.add(new Toggle("Cushion seating", "Seat height and hitbox of the real cushion (0.25 block).",
                () -> Config.cushionSeat, v -> Config.cushionSeat = v, false));
        visual.add(new Toggle("26.x items", "Real models and names of 26.x items in the inventory and in hand.",
                () -> Config.futureItems, v -> Config.futureItems = v, false));
        visual.add(new Toggle("Redrawn textures", "Existing textures that 26.x redrew.",
                () -> Config.redrawnTextures, v -> { Config.redrawnTextures = v; Features.applyRedrawnTextures(); }, false));
        if (Features.babyModelsAvailable()) {
            visual.add(new Toggle("26.x babies", "New baby animal models and textures.",
                    () -> Config.babyModels, v -> { Config.babyModels = v; Features.reloadModels(); }, false));
        }
        visual.add(new Toggle("Sulfur cube", "Translucent cube with the swallowed block, fuse, babies and particles.",
                () -> Config.sulfurCube, v -> Config.sulfurCube = v, false));
        sections.add(visual);

        Section sounds = new Section("Sounds");
        sounds.add(new Toggle("Cushion sounds", "Place, sit, get up and break, instead of wool sounds.",
                () -> Config.cushionSounds, v -> Config.cushionSounds = v, false));
        sounds.add(new Toggle("Placing 26.x blocks", "The block's real place sound (wool for wool stairs, not stone).",
                () -> Config.placeSounds, v -> Config.placeSounds = v, false));
        sounds.add(new Toggle("New 26.x sounds", "Trumpet, sulfur cube, animal variants... instead of the substitute sounds.",
                () -> Config.futureSounds, v -> Config.futureSounds = v, true));
        sections.add(sounds);

        Section blocks = new Section("Blocks");
        if (!Features.viaFabricPresent()) {
            blocks.add(new Note(YELLOW, "ViaFabric is not installed",
                    "Add ViaFabric, ViaVersion and ViaBackwards 5.12.2+ to restore placed blocks and geysers."));
        }
        blocks.add(new Toggle("Restore placed blocks", "115 blocks from 26.x with their exact shapes, light and sounds. Applies on reconnect.",
                () -> Config.restoreBlocks, v -> Config.restoreBlocks = v, true));
        blocks.add(new Toggle("Geyser effects", "Bubbles, toxic gas, eruption plume and potent sulfur sounds.",
                () -> Config.geyserEffects, v -> Config.geyserEffects = v, true));
        blocks.add(new Toggle("Geyser launch", "An erupting geyser pushes you up, like in 26.3.",
                () -> Config.geyserLaunch, v -> Config.geyserLaunch = v, true) {
            @Override
            @Nullable String blocked() {
                return Config.safeMode ? "Disabled by safe mode." : super.blocked();
            }
        });
        blocks.add(new Toggle("Join listed servers as 26.3",
                "ON: the servers below are joined as 26.3 (blocks and geysers restored). OFF: never 26.3, normal connection everywhere.",
                () -> Config.autoConnect26, v -> Config.autoConnect26 = v, true));
        blocks.add(new Hosts());
        blocks.add(new Toggle("ViaFabric on other servers",
                "OFF (recommended): ViaFabric is turned off, plain vanilla connection. ON: ViaFabric uses its own settings.",
                () -> Config.viaOtherServers, v -> Config.viaOtherServers = v, true));
        sections.add(blocks);

        Section info = new Section(INFO);
        info.add(new Status());
        info.add(new Note(GREEN, "Two ways to play",
                "Mod only: the server translates 26.3; cushions, items, sounds and seating are restored. "
                        + "With ViaFabric: your client connects as 26.3, so placed blocks and geysers are restored too."));
        info.add(new Note(WHITE, "What the mod does",
                "Everything runs on your client and nothing is sent beyond what a normal client sends. "
                        + "Geyser launch copies a vanilla 26.3 client and only works over a 26.3 connection. "
                        + "Safe mode turns it off."));
        info.add(new Note(YELLOW, "DonutSMP rules",
                "Banned: modified clients, movement mods, unfair advantages. Visual mods and ViaFabric are not "
                        + "mentioned: if in doubt, turn on safe mode and ask support."));
        sections.add(info);
    }

    /** Used by tests and screenshots: 0 is "All", then one index per section. */
    public void selectCategory(int index) {
        tab = Math.max(0, Math.min(sections.size(), index));
        if (searchBox != null) searchBox.setValue("");
        query = "";
        scroll = 0;
    }

    public int categoryCount() {
        return sections.size() + 1;
    }

    public void search(String text) {
        if (searchBox != null) searchBox.setValue(text);
        else onSearch(text);
    }

    private void onSearch(String text) {
        query = text;
        scroll = 0;
    }

    private List<String> tabLabels() {
        List<String> out = new ArrayList<>();
        out.add("All");
        for (Section s : sections) out.add(s.name);
        return out;
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (!query.isBlank()) {
            String q = normalize(query.trim());
            for (Section s : sections) {
                List<Entry> hits = new ArrayList<>();
                for (Entry e : s.entries) if (e.matches(q)) hits.add(e);
                if (!hits.isEmpty()) {
                    out.add(s.header);
                    out.addAll(hits);
                }
            }
            return out;
        }
        if (tab == 0) {
            for (Section s : sections) {
                if (s.name.equals(INFO)) continue;
                out.add(s.header);
                for (Entry e : s.entries) if (e instanceof Toggle || e instanceof Hosts) out.add(e);
            }
            return out;
        }
        return sections.get(tab - 1).entries;
    }

    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    @Override
    protected void init() {
        colW = Math.min(width - 40, 320);
        colX = (width - colW) / 2;
        tabsY = 20;
        tabW = Math.min(70, (width - 20) / (sections.size() + 1));
        listTop = 72;
        listBottom = height - 33;

        searchBox = new EditBox(font, colX, 47, colW, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal(SEARCH_HINT).withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(64);
        searchBox.setValue(query);
        searchBox.setResponder(this::onSearch);
        addRenderableWidget(searchBox);

        hostsBox = new EditBox(font, colX, 0, colW - 16, 18, Component.literal("Servers"));
        hostsBox.setMaxLength(256);
        hostsBox.setValue(String.join(", ", Config.autoConnectHosts));
        hostsBox.setHint(Component.literal("donutsmp.net, *.donutsmp.net").withStyle(ChatFormatting.DARK_GRAY));
        hostsBox.setResponder(value -> {
            List<String> hosts = new ArrayList<>();
            for (String part : value.split("[,\\s]+")) if (!part.isBlank()) hosts.add(part.trim().toLowerCase(Locale.ROOT));
            Config.autoConnectHosts = hosts;
            Config.save();
        });
        hostsBox.visible = false;
        addRenderableWidget(hostsBox);

        int bw = Math.min(150, (width - 30) / 2);
        addRenderableWidget(Button.builder(safeLabel(), b -> {
            Config.safeMode = !Config.safeMode;
            Config.save();
            b.setMessage(safeLabel());
        }).bounds(width / 2 - bw - 4, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + 4, height - 26, bw, 20).build());
        setInitialFocus(searchBox);
    }

    private static Component safeLabel() {
        return Component.literal("Safe mode: ").append(Config.safeMode
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF"));
    }

    private record Placed(Entry entry, int y, int h) {}

    private List<Placed> place() {
        List<Placed> out = new ArrayList<>();
        int y = 4;
        for (Entry e : entries()) {
            int eh = e.height(colW);
            out.add(new Placed(e, y, eh));
            y += eh + 2;
        }
        return out;
    }

    private int maxScroll(List<Placed> placed) {
        int total = placed.isEmpty() ? 0 : placed.getLast().y + placed.getLast().h + 4;
        return Math.max(0, total - (listBottom - listTop));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) renderPanorama(g, partialTick);
        renderBlurredBackground(g);
        g.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawCenteredString(font, title, width / 2, 7, WHITE);
        drawStatus(g);
        drawTabs(g, mouseX, mouseY);

        Identifier header = minecraft.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
        Identifier footer = minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
        g.fill(0, listTop, width, listBottom, LIST_BG);
        g.blit(RenderPipelines.GUI_TEXTURED, header, 0, listTop - 2, 0, 0, width, 2, 32, 2);
        g.blit(RenderPipelines.GUI_TEXTURED, footer, 0, listBottom, 0, 0, width, 2, 32, 2);

        drawList(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        // EditBox hides its hint while focused, and the search box always has focus.
        if (searchBox != null && query.isEmpty()) {
            g.drawString(font, SEARCH_HINT, searchBox.getX() + 12, searchBox.getY() + 5, DARK_GRAY);
        }
    }

    private void drawStatus(GuiGraphics g) {
        String label;
        int color;
        if (Features.connectedAs26()) {
            label = "Connected as 26.3";
            color = GREEN;
        } else if (Features.viaFabricPresent()) {
            label = "ViaFabric ready";
            color = WHITE;
        } else {
            label = "Mod only";
            color = YELLOW;
        }
        int w = font.width(label);
        g.fill(width - 8 - w - 8, 10, width - 8 - w - 4, 14, color);
        g.drawString(font, label, width - 8 - w, 7, GRAY);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        List<String> labels = tabLabels();
        int x = (width - tabW * labels.size()) / 2;
        boolean searching = !query.isBlank();
        for (int i = 0; i < labels.size(); i++) {
            boolean selected = i == tab && !searching;
            boolean hover = inside(mx, my, x, tabsY, tabW, 24);
            Identifier sprite = selected ? (hover ? TAB_SELECTED_HOVER : TAB_SELECTED) : (hover ? TAB_HOVER : TAB);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, tabsY, tabW, 24);
            g.drawCenteredString(font, labels.get(i), x + tabW / 2, tabsY + (selected ? 8 : 9), selected ? WHITE : GRAY);
            x += tabW;
        }
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        List<Placed> placed = place();
        int max = maxScroll(placed);
        scroll = Math.max(0, Math.min(scroll, max));
        boolean inList = my >= listTop && my < listBottom;
        boolean hostsShown = false;

        g.enableScissor(0, listTop, width, listBottom);
        for (Placed p : placed) {
            int y = listTop + p.y - Math.round(scroll);
            if (y + p.h < listTop || y > listBottom) continue;
            boolean hover = inList && inside(mx, my, colX, y, colW, p.h);
            p.entry.render(g, colX, y, colW, p.h, hover, mx, my);
            if (p.entry instanceof Hosts && hostsBox != null && y + 14 >= listTop && y + 32 <= listBottom) {
                hostsBox.setPosition(colX + 8, y + 14);
                hostsShown = true;
            }
        }
        g.disableScissor();
        if (hostsBox != null) {
            hostsBox.visible = hostsShown;
            if (!hostsShown && getFocused() == hostsBox) setFocused(searchBox);
        }

        if (placed.isEmpty()) {
            g.drawCenteredString(font, "No option matches \"" + query.trim() + "\"", width / 2, listTop + 20, GRAY);
        }
        if (max > 0) {
            int sx = colX + colW + 6, viewH = listBottom - listTop;
            int barH = Math.max(32, viewH * viewH / (viewH + max));
            int barY = listTop + Math.round((viewH - barH) * (scroll / max));
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BG, sx, listTop, 6, viewH);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, sx, barY, 6, barH);
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            // Buttons grab focus on click; hand it back so typing and Escape still reach the search.
            if (getFocused() != searchBox && getFocused() != hostsBox) setFocused(searchBox);
            return true;
        }
        double mx = event.x(), my = event.y();
        int x = (width - tabW * (sections.size() + 1)) / 2;
        for (int i = 0; i <= sections.size(); i++) {
            if (inside(mx, my, x + i * tabW, tabsY, tabW, 24)) {
                if (i != tab || !query.isBlank()) {
                    selectCategory(i);
                    click();
                }
                setFocused(searchBox);
                return true;
            }
        }
        if (my >= listTop && my < listBottom) {
            for (Placed p : place()) {
                int y = listTop + p.y - Math.round(scroll);
                if (inside(mx, my, colX, y, colW, p.h)) {
                    p.entry.click();
                    if (!(p.entry instanceof Hosts)) setFocused(searchBox);
                    return true;
                }
            }
        }
        setFocused(searchBox);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (float) dy * 18f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && getFocused() == searchBox && !query.isEmpty()) {
            searchBox.setValue("");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private final class Section {
        final String name;
        final Header header;
        final List<Entry> entries = new ArrayList<>();

        Section(String name) {
            this.name = name;
            this.header = new Header(name);
        }

        void add(Entry e) {
            entries.add(e);
        }
    }

    private abstract class Entry {
        boolean matches(String q) {
            return false;
        }

        abstract int height(int w);

        abstract void render(GuiGraphics g, int x, int y, int w, int h, boolean hover, int mx, int my);

        void click() {}
    }

    private final class Header extends Entry {
        private final Component label;

        Header(String label) {
            this.label = Component.literal(label).withStyle(ChatFormatting.BOLD);
        }

        @Override
        int height(int w) {
            return 16;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hover, int mx, int my) {
            g.drawCenteredString(font, label, x + w / 2, y + 5, YELLOW);
        }
    }

    private class Toggle extends Entry {
        private final String title, desc, haystack;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private final boolean needsVia;
        private int wrappedWidth = -1;
        private List<FormattedCharSequence> wrapped = List.of();

        Toggle(String title, String desc, BooleanSupplier get, Consumer<Boolean> set, boolean needsVia) {
            this.title = title;
            this.desc = desc;
            this.get = get;
            this.set = set;
            this.needsVia = needsVia;
            this.haystack = normalize(title + " " + desc);
        }

        @Nullable String blocked() {
            return needsVia && !Features.viaFabricPresent() ? "Requires ViaFabric, ViaVersion and ViaBackwards." : null;
        }

        @Override
        boolean matches(String q) {
            return haystack.contains(q);
        }

        private List<FormattedCharSequence> lines(int w) {
            if (w != wrappedWidth) {
                wrappedWidth = w;
                wrapped = font.split(Component.literal(desc), w - 6 - 54);
            }
            return wrapped;
        }

        @Override
        int height(int w) {
            return Math.max(26, 15 + lines(w).size() * 10 + 1);
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hover, int mx, int my) {
            String reason = blocked();
            boolean enabled = reason == null;
            g.fill(x, y, x + w, y + h, hover && enabled ? ROW_HOVER : CARD_BG);
            g.drawString(font, font.plainSubstrByWidth(title, w - 6 - 54), x + 6, y + 4, enabled ? WHITE : DARK_GRAY);
            int ly = y + 15;
            for (FormattedCharSequence line : lines(w)) {
                g.drawString(font, line, x + 6, ly, enabled ? GRAY : DARK_GRAY, false);
                ly += 10;
            }
            int bx = x + w - 46, by = y + (h - 20) / 2;
            boolean on = get.getAsBoolean() && enabled;
            boolean buttonHover = enabled && inside(mx, my, bx, by, 42, 20);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_DISABLED : buttonHover ? BUTTON_HOVER : BUTTON, bx, by, 42, 20);
            g.drawCenteredString(font, on ? "ON" : "OFF", bx + 21, by + 6, !enabled ? DARK_GRAY : on ? GREEN : RED);

            if (hover && !enabled) g.setTooltipForNextFrame(font, Component.literal(reason), mx, my);
        }

        @Override
        void click() {
            if (blocked() != null) return;
            set.accept(!get.getAsBoolean());
            Config.save();
            SettingsScreen.this.click();
        }
    }

    private class Note extends Entry {
        private final int color;
        private final String title, body, haystack;

        Note(int color, String title, String body) {
            this.color = color;
            this.title = title;
            this.body = body;
            this.haystack = normalize(title + " " + body);
        }

        @Override
        boolean matches(String q) {
            return haystack.contains(q);
        }

        @Override
        int height(int w) {
            return 18 + font.split(Component.literal(body), w - 12).size() * 10 + 4;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hover, int mx, int my) {
            g.fill(x, y, x + w, y + h, CARD_BG);
            g.fill(x, y, x + 2, y + h, color);
            g.drawString(font, title, x + 8, y + 5, color);
            int ly = y + 17;
            for (FormattedCharSequence line : font.split(Component.literal(body), w - 12)) {
                g.drawString(font, line, x + 8, ly, GRAY, false);
                ly += 10;
            }
        }
    }

    private class Status extends Entry {
        private record Line(String label, Supplier<Boolean> ok, String yes, String no) {}

        private final List<Line> lines = List.of(
                new Line("ViaFabric", Features::viaFabricPresent, "installed", "missing"),
                new Line("ViaBackwards hook", Features::viaHooked, "active", "inactive"),
                new Line("26.3 blocks restored", Features::connectedAs26, "right now", "not connected as 26.3"),
                new Line("Auto 26.3 connection", () -> Config.autoConnect26 && Features.viaFabricPresent(), "on", "off"));

        @Override
        boolean matches(String q) {
            return "status viafabric connection".contains(q);
        }

        @Override
        int height(int w) {
            return 6 + lines.size() * 13 + 2;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hover, int mx, int my) {
            g.fill(x, y, x + w, y + h, CARD_BG);
            int ly = y + 6;
            for (Line l : lines) {
                boolean ok = l.ok.get();
                g.drawString(font, l.label, x + 8, ly, WHITE);
                String v = ok ? l.yes : l.no;
                g.drawString(font, v, x + w - 8 - font.width(v), ly, ok ? GREEN : GRAY);
                ly += 13;
            }
        }
    }

    private class Hosts extends Entry {
        @Override
        boolean matches(String q) {
            return "26.3 servers automatic connection donutsmp".contains(q);
        }

        @Override
        int height(int w) {
            return 38;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hover, int mx, int my) {
            g.fill(x, y, x + w, y + h, CARD_BG);
            g.drawString(font, "26.3 servers", x + 6, y + 3, WHITE);
            String hint = "comma separated";
            g.drawString(font, hint, x + w - 6 - font.width(hint), y + 3, DARK_GRAY, false);
        }

        @Override
        void click() {
            if (hostsBox != null && hostsBox.visible) setFocused(hostsBox);
        }
    }
}
