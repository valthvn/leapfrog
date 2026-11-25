package dev.leapfrog.assets;

import dev.leapfrog.Leapfrog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The 26.x assets live in {@code <game dir>/leapfrog/<version>/}. They are downloaded from Mojang the
 * first time the game starts, in the background; the resource pack waits for them during the loading
 * screen. Without a connection the game starts without them and the next launch tries again.
 */
public final class FutureAssets {
    public static final String VERSION = "26.3";
    private static final String BASE_VERSION = "1.21.11";
    // Bump when the cache layout or its content changes, so old caches get rebuilt.
    private static final String FORMAT = "2";
    private static final CompletableFuture<Boolean> READY = new CompletableFuture<>();
    private static Path dir;

    private FutureAssets() {}

    public static synchronized void start() {
        if (dir != null) return;
        dir = FabricLoader.getInstance().getGameDir().resolve("leapfrog").resolve(VERSION);
        if (isComplete(dir)) {
            READY.complete(true);
            return;
        }
        Thread thread = new Thread(FutureAssets::build, "Leapfrog asset download");
        thread.setDaemon(true);
        thread.start();
    }

    private static void build() {
        Path temp = dir.resolveSibling(VERSION + ".part");
        try {
            long start = System.currentTimeMillis();
            Leapfrog.LOG.info("Downloading the {} assets from Mojang", VERSION);
            delete(temp);
            delete(dir);
            Files.createDirectories(temp);
            new AssetExtractor(temp).run(VERSION, BASE_VERSION);
            Files.writeString(temp.resolve("format"), FORMAT);
            Files.move(temp, dir);
            Leapfrog.LOG.info("{} assets ready in {} s", VERSION, (System.currentTimeMillis() - start) / 1000);
            READY.complete(true);
        } catch (Throwable t) {
            Leapfrog.LOG.error("Could not download the {} assets, 26.x visuals and sounds are off until next launch", VERSION, t);
            try {
                delete(temp);
            } catch (IOException ignored) {
            }
            READY.complete(false);
        }
    }

    private static boolean isComplete(Path dir) {
        try {
            return FORMAT.equals(Files.readString(dir.resolve("format")).trim());
        } catch (IOException e) {
            return false;
        }
    }

    /** Blocks until the download is over. False if it failed. */
    public static boolean await() {
        return READY.join();
    }

    public static boolean failed() {
        return READY.isDone() && !READY.join();
    }

    public static Path pack() {
        return dir.resolve("pack");
    }

    public static Path redrawn() {
        return dir.resolve("redrawn");
    }

    public static List<String> futureItems() {
        if (!await()) return List.of();
        try {
            return Files.readAllLines(pack().resolve("future_items.txt")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (IOException e) {
            Leapfrog.LOG.error("Could not read the 26.x item list", e);
            return List.of();
        }
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> files = Files.walk(path)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
