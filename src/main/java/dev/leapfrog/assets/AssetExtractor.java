package dev.leapfrog.assets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leapfrog.Leapfrog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Builds the 26.x asset cache from Mojang's servers: every client asset the future version added, the
 * textures it redrew, its new sounds and the translated names of its new items. Nothing of this ships
 * in the mod jar.
 */
final class AssetExtractor {
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String OBJECTS = "https://resources.download.minecraft.net/";
    private static final List<String> LANGS = List.of("en_us", "fr_fr");
    private static final List<String> GEYSER_PARTICLES = List.of(
            "sulfur_bubbles", "noxious_gas", "geyser_plume", "geyser_base", "geyser_poof", "sulfur_cube_goo");
    private static final Pattern SKIPPED = Pattern.compile("^assets/minecraft/(lang|shaders|texts|sounds|post_effect|font)/");
    private static final Pattern REDRAWABLE = Pattern.compile("^assets/minecraft/textures/(block|entity|item|map|gui)/.*\\.png$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ModContainer game = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow();
    private final Path pack;
    private final Path redrawn;
    private final Path work;

    AssetExtractor(Path target) {
        this.pack = target.resolve("pack");
        this.redrawn = target.resolve("redrawn");
        this.work = target;
    }

    void run(String futureVersion, String baseVersion) throws Exception {
        if (game.findPath("assets/minecraft/textures/block/stone.png").isEmpty()) {
            throw new IOException("Cannot read the game's own assets");
        }
        JsonObject manifest = getJson(MANIFEST);
        JsonObject future = getJson(versionUrl(manifest, futureVersion));
        JsonObject base = getJson(versionUrl(manifest, baseVersion));
        JsonObject futureIndex = getJson(future.getAsJsonObject("assetIndex").get("url").getAsString()).getAsJsonObject("objects");
        JsonObject baseIndex = getJson(base.getAsJsonObject("assetIndex").get("url").getAsString()).getAsJsonObject("objects");

        JsonObject client = future.getAsJsonObject("downloads").getAsJsonObject("client");
        Path jar = work.resolve("client.jar");
        Files.write(jar, download(client.get("url").getAsString(), client.get("sha1").getAsString()));
        List<String> items;
        Map<String, String> english;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            copyNewAssets(zip);
            copyRedrawnTextures(zip);
            items = futureItems();
            try (InputStream in = zip.getInputStream(zip.getEntry("assets/minecraft/lang/en_us.json"))) {
                english = readStrings(in);
            }
        } finally {
            Files.deleteIfExists(jar);
        }

        Set<String> subtitles = copyNewSounds(object(futureIndex, "minecraft/sounds.json"), object(baseIndex, "minecraft/sounds.json"), futureIndex);
        Set<String> blocks = copyBlockStates();
        for (String name : GEYSER_PARTICLES) copyInto(pack.resolve("assets/minecraft/particles/" + name + ".json"), "particles/" + name + ".json");

        for (String lang : LANGS) {
            Map<String, String> source;
            if (lang.equals("en_us")) {
                source = english;
            } else {
                JsonObject obj = futureIndex.getAsJsonObject("minecraft/lang/" + lang + ".json");
                if (obj == null) continue;
                source = readStrings(new ByteArrayInputStream(fetchObject(obj.get("hash").getAsString())));
            }
            writeLang(lang, source, items, subtitles, blocks);
        }
        Files.writeString(pack.resolve("future_items.txt"), String.join("\n", items) + "\n");
    }

    /** Assets of the future client jar that 1.21.11 doesn't have. */
    private void copyNewAssets(ZipFile zip) throws IOException {
        int count = 0;
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
            ZipEntry e = it.nextElement();
            String name = e.getName();
            if (e.isDirectory() || !name.startsWith("assets/minecraft/") || SKIPPED.matcher(name).find()) continue;
            if (game.findPath(name).isPresent()) continue;
            write(pack.resolve(name), zip, e);
            count++;
        }
        Leapfrog.LOG.info("Extracted {} new 26.x assets", count);
    }

    /** Textures redrawn in the future version. Only same-size images, so no UV can break. */
    private void copyRedrawnTextures(ZipFile zip) throws IOException {
        int count = 0;
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
            ZipEntry e = it.nextElement();
            String name = e.getName();
            if (!REDRAWABLE.matcher(name).matches()) continue;
            Optional<Path> old = game.findPath(name);
            if (old.isEmpty()) continue;
            byte[] now;
            try (InputStream in = zip.getInputStream(e)) {
                now = in.readAllBytes();
            }
            byte[] before = Files.readAllBytes(old.get());
            if (Arrays.equals(now, before) || !sameSize(now, before)) continue;
            Path dst = redrawn.resolve(name);
            Files.createDirectories(dst.getParent());
            Files.write(dst, now);
            count++;
        }
        Leapfrog.LOG.info("Extracted {} redrawn 26.x textures", count);
    }

    // PNG width and height sit at bytes 16-23, in the IHDR chunk.
    private static boolean sameSize(byte[] a, byte[] b) {
        return a.length > 24 && b.length > 24 && Arrays.equals(a, 16, 24, b, 16, 24);
    }

    private List<String> futureItems() throws IOException {
        List<String> ids = new ArrayList<>();
        Path dir = pack.resolve("assets/minecraft/items");
        if (!Files.isDirectory(dir)) return ids;
        try (var files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> "minecraft:" + n.substring(0, n.length() - 5))
                    .sorted()
                    .forEach(ids::add);
        }
        return ids;
    }

    /** New sound events (definitions merged into minecraft:sounds.json) and their .ogg files. Returns the subtitle keys. */
    private Set<String> copyNewSounds(JsonObject futureObj, JsonObject baseObj, JsonObject futureIndex) throws Exception {
        JsonObject futureSounds = JsonParser.parseString(new String(fetchObject(futureObj.get("hash").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject baseSounds = JsonParser.parseString(new String(fetchObject(baseObj.get("hash").getAsString()), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, JsonElement> added = new TreeMap<>();
        Set<String> subtitles = new TreeSet<>();
        Map<String, String> files = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : futureSounds.entrySet()) {
            if (baseSounds.has(e.getKey()) || e.getKey().startsWith("music")) continue;
            JsonObject def = e.getValue().getAsJsonObject();
            added.put(e.getKey(), def);
            if (def.has("subtitle")) subtitles.add(def.get("subtitle").getAsString());
            for (JsonElement s : def.getAsJsonArray("sounds")) {
                String name;
                if (s.isJsonObject()) {
                    if (s.getAsJsonObject().has("type") && s.getAsJsonObject().get("type").getAsString().equals("event")) continue;
                    name = s.getAsJsonObject().get("name").getAsString();
                } else {
                    name = s.getAsString();
                }
                String path = name.substring(name.indexOf(':') + 1);
                JsonObject obj = futureIndex.getAsJsonObject("minecraft/sounds/" + path + ".ogg");
                if (obj == null) {
                    Leapfrog.LOG.warn("Missing sound file {}", path);
                    continue;
                }
                files.put(path, obj.get("hash").getAsString());
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (Map.Entry<String, String> f : files.entrySet()) {
                jobs.add(pool.submit(() -> {
                    Path dst = pack.resolve("assets/minecraft/sounds/" + f.getKey() + ".ogg");
                    Files.createDirectories(dst.getParent());
                    Files.write(dst, fetchObject(f.getValue()));
                    return null;
                }));
            }
            for (Future<?> job : jobs) job.get();
        } finally {
            pool.shutdownNow();
        }
        Files.writeString(pack.resolve("assets/minecraft/sounds.json"), GSON.toJson(added));
        Leapfrog.LOG.info("Extracted {} 26.x sound events ({} files)", added.size(), files.size());
        return subtitles;
    }

    /** Restored blocks live in the leapfrog namespace, so their blockstate files go there too. */
    private Set<String> copyBlockStates() throws IOException {
        Set<String> names;
        try (InputStream in = AssetExtractor.class.getResourceAsStream("/assets/leapfrog/future_blocks.json")) {
            if (in == null) throw new IOException("future_blocks.json missing");
            names = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("blocks").keySet();
        }
        for (String name : names) copyInto(pack.resolve("assets/minecraft/blockstates/" + name + ".json"), "blockstates/" + name + ".json");
        return names;
    }

    private void copyInto(Path src, String path) throws IOException {
        if (!Files.exists(src)) {
            Leapfrog.LOG.warn("No 26.x file for {}", path);
            return;
        }
        Path dst = pack.resolve("assets/leapfrog/" + path);
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst);
    }

    /**
     * ViaBackwards names its stand-in items {"translate":"vb.item.<id>","fallback":"26.3 <Name>"}: these
     * keys get the official names. Subtitles of the new sounds and the restored blocks' names come along.
     */
    private void writeLang(String lang, Map<String, String> source, List<String> items, Set<String> subtitles, Set<String> blocks) throws IOException {
        Map<String, String> out = new TreeMap<>();
        for (String id : items) {
            String path = id.substring("minecraft:".length());
            String name = source.getOrDefault("item.minecraft." + path, source.get("block.minecraft." + path));
            if (name != null) out.put("vb.item." + path, name);
        }
        for (String key : subtitles) if (source.containsKey(key)) out.put(key, source.get(key));
        for (String name : blocks) {
            String value = source.get("block.minecraft." + name);
            if (value != null) out.put("block.leapfrog." + name, value);
        }
        Path dst = pack.resolve("assets/leapfrog/lang/" + lang + ".json");
        Files.createDirectories(dst.getParent());
        Files.writeString(dst, GSON.toJson(out));
    }

    private static Map<String, String> readStrings(InputStream in) {
        Map<String, String> out = new TreeMap<>();
        JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
        return out;
    }

    private static void write(Path dst, ZipFile zip, ZipEntry e) throws IOException {
        Files.createDirectories(dst.getParent());
        try (InputStream in = zip.getInputStream(e)) {
            Files.copy(in, dst);
        }
    }

    private static String versionUrl(JsonObject manifest, String version) throws IOException {
        for (JsonElement v : manifest.getAsJsonArray("versions")) {
            JsonObject o = v.getAsJsonObject();
            if (o.get("id").getAsString().equals(version)) return o.get("url").getAsString();
        }
        throw new IOException("Unknown Minecraft version " + version);
    }

    private static JsonObject object(JsonObject index, String name) throws IOException {
        JsonObject o = index.getAsJsonObject(name);
        if (o == null) throw new IOException(name + " missing from the asset index");
        return o;
    }

    private JsonObject getJson(String url) throws Exception {
        return JsonParser.parseString(new String(download(url, null), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private byte[] fetchObject(String hash) throws Exception {
        return download(OBJECTS + hash.substring(0, 2) + "/" + hash, hash);
    }

    private byte[] download(String url, String sha1) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);
        byte[] body = response.body();
        if (sha1 != null) {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(body));
            if (!actual.equalsIgnoreCase(sha1)) throw new IOException("Checksum mismatch for " + url);
        }
        return body;
    }
}
