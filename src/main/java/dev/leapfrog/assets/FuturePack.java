package dev.leapfrog.assets;

import dev.leapfrog.Leapfrog;
import dev.leapfrog.Config;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;

/**
 * Always-on resource pack serving the downloaded 26.x assets, just above the vanilla pack so other
 * resource packs still win. Its metadata is built here, so the pack can be listed before the download
 * is over; reads wait for it.
 */
public final class FuturePack implements RepositorySource {
    private static final PackLocationInfo LOCATION = new PackLocationInfo("leapfrog",
            Component.literal("Leapfrog " + FutureAssets.VERSION), PackSource.BUILT_IN, Optional.empty());
    private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);
    private static final Set<String> NAMESPACES = Set.of("minecraft", Leapfrog.MOD_ID);

    @Override
    public void loadPacks(Consumer<Pack> out) {
        Pack.Metadata metadata = new Pack.Metadata(Component.literal(FutureAssets.VERSION + " textures, models and sounds"),
                PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        out.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new Resources();
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata meta) {
                return new Resources();
            }
        }, metadata, SELECTION));
    }

    private static final class Resources implements PackResources {
        private @Nullable List<PackResources> parts;

        private synchronized List<PackResources> parts() {
            if (parts == null) {
                parts = new ArrayList<>();
                if (FutureAssets.await()) {
                    if (Config.redrawnTextures) parts.add(new PathPackResources(LOCATION, FutureAssets.redrawn()));
                    parts.add(new PathPackResources(LOCATION, FutureAssets.pack()));
                }
            }
            return parts;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            if (type != PackType.CLIENT_RESOURCES || !NAMESPACES.contains(id.getNamespace())) return null;
            for (PackResources part : parts()) {
                IoSupplier<InputStream> found = part.getResource(type, id);
                if (found != null) return found;
            }
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != PackType.CLIENT_RESOURCES || !NAMESPACES.contains(namespace)) return;
            for (PackResources part : parts()) part.listResources(type, namespace, path, output);
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? NAMESPACES : Set.of();
        }

        @Override
        public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return LOCATION;
        }

        @Override
        public synchronized void close() {
            if (parts != null) parts.forEach(PackResources::close);
        }
    }
}
