package dev.leapfrog.geyser;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** The 26.3 geyser and potent sulfur particles. */
public final class GeyserParticles {
    private GeyserParticles() {}

    public static void registerProviders() {
        ParticleFactoryRegistry r = ParticleFactoryRegistry.getInstance();
        r.register(GeyserParticleTypes.SULFUR_BUBBLES, sprites -> (o, level, x, y, z, xa, ya, za, random) ->
                new SulfurBubble(level, x, y, z, xa, ya, sprites.get(random)));
        r.register(GeyserParticleTypes.NOXIOUS_GAS, sprites -> (o, level, x, y, z, xa, ya, za, random) ->
                new NoxiousGas(level, x, y, z, xa, ya, za, 3.0F, sprites));
        r.register(GeyserParticleTypes.NOXIOUS_GAS_CLOUD, (o, level, x, y, z, xa, ya, za, random) -> new NoxiousGasCloud(level, x, y, z));
        r.register(GeyserParticleTypes.GEYSER, (o, level, x, y, z, xa, ya, za, random) -> new Eruption(level, x, y, z, xa, ya, za, o.waterBlocks()));
        r.register(GeyserParticleTypes.GEYSER_PLUME, sprites -> (o, level, x, y, z, xa, ya, za, random) -> new Plume(level,
                x + (random.nextFloat() - 0.5F) * 0.2F, y + random.nextFloat(), z + (random.nextFloat() - 0.5F) * 0.2F, xa, ya, za, o.waterBlocks(), sprites));
        ParticleFactoryRegistry.PendingParticleFactory<GeyserParticleTypes.GeyserBase> base = sprites -> (o, level, x, y, z, xa, ya, za, random) ->
                new Base(level, x + (random.nextFloat() - 0.5F) * 0.5F, y + (random.nextFloat() - 0.5F) * 0.5F + 0.2F,
                        z + (random.nextFloat() - 0.5F) * 0.5F, xa, ya, za, o.waterBlocks(), o.burstImpulseBase(), sprites);
        r.register(GeyserParticleTypes.GEYSER_BASE, base);
        r.register(GeyserParticleTypes.GEYSER_POOF, base);
        // Like BreakingItemParticle.SulfurCubeProvider: a small piece of the goo sprite.
        r.register(GeyserParticleTypes.SULFUR_CUBE_GOO, sprites -> (o, level, x, y, z, xa, ya, za, random) ->
                new Goo(level, x, y, z, sprites.first()));
    }

    static final class Goo extends BreakingItemParticle {
        Goo(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
        }
    }

    /** GeyserEruptionParticle, an invisible emitter for the plume, base and poof. */
    static final class Eruption extends NoRenderParticle {
        private final int waterBlocks;
        private final double xa, ya, za;
        private final GeyserParticleTypes.Geyser plume;
        private final GeyserParticleTypes.GeyserBase basePart;
        private final GeyserParticleTypes.GeyserBase poof;

        Eruption(ClientLevel level, double x, double y, double z, double xa, double ya, double za, int waterBlocks) {
            super(level, x, y, z);
            this.xa = xa;
            this.ya = ya;
            this.za = za;
            this.waterBlocks = waterBlocks;
            this.lifetime = 20;
            this.plume = new GeyserParticleTypes.Geyser(GeyserParticleTypes.GEYSER_PLUME, waterBlocks);
            this.basePart = new GeyserParticleTypes.GeyserBase(GeyserParticleTypes.GEYSER_BASE, waterBlocks, 1.5F);
            this.poof = new GeyserParticleTypes.GeyserBase(GeyserParticleTypes.GEYSER_POOF, waterBlocks, 2.0F);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.age % 2 == 0) {
                for (int i = 0; i < 2; i++) this.level.addParticle(basePart, x, y, z, xa, ya, za);
            }
            for (int i = 0; i < waterBlocks + 2; i++) this.level.addParticle(plume, x, y, z, xa, ya, za);
            if (this.age % 10 == 0) {
                for (int i = 0; i < 20; i++) this.level.addParticle(poof, x, y, z, xa, ya, za);
            }
        }
    }

    /** GeyserBaseParticle (also used for the poof). */
    static final class Base extends BaseAshSmokeParticle {
        Base(ClientLevel level, double x, double y, double z, double xa, double ya, double za, int waterBlocks, float burstImpulseBase, SpriteSet sprites) {
            super(level, x, y, z, burstImpulseBase + 0.25F * waterBlocks, burstImpulseBase + 0.25F * waterBlocks, burstImpulseBase + 0.25F * waterBlocks,
                    xa, ya, za, 3.0F + 0.125F * waterBlocks, sprites, 0.0F, 0, 0.0F, true);
            this.friction = 0.725F;
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
            this.yd = Math.abs(this.yd);
            float lifetimeFactor = 0.8F + 0.2F * level.getRandom().nextFloat();
            this.lifetime = (int) (25.0F * lifetimeFactor);
        }
    }

    /** GeyserPlumeParticle, the rising water column. */
    static final class Plume extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final double startY;
        private final double maxY;
        private final float initialPropulsion;
        private final float horizontalSprayX;
        private final float horizontalSprayZ;
        private final float minSize;
        private final float maxSize;
        private boolean done;

        Plume(ClientLevel level, double x, double y, double z, double xa, double ya, double za, int waterBlocks, SpriteSet sprites) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            int plumeHeight = 5 * Math.max(1, waterBlocks);
            this.hasPhysics = true;
            this.speedUpWhenYMotionIsBlocked = true;
            this.lifetime = plumeHeight * 5;
            this.yd = 0.0;
            this.startY = y;
            this.maxY = this.startY + plumeHeight - 1.0;
            this.horizontalSprayX = (level.getRandom().nextFloat() - 0.5F) * 0.2F;
            this.horizontalSprayZ = (level.getRandom().nextFloat() - 0.5F) * 0.2F;
            this.friction = 1.0F;
            this.initialPropulsion = (waterBlocks == 1 ? 1.5F : 1.0F) * plumeHeight * 1.45F;
            this.gravity = -this.initialPropulsion;
            float initiallyRandomizedSize = this.quadSize * 0.75F;
            this.minSize = initiallyRandomizedSize * (2.0F + plumeHeight / 8.0F);
            this.maxSize = initiallyRandomizedSize * (3.0F + plumeHeight / 8.0F);
            this.quadSize = this.minSize;
            this.sprites = sprites;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!done && (this.yd < 0.0 || this.y > maxY || this.y == this.yo)) {
                this.lifetime = Math.min(this.lifetime, this.age + 5);
                this.friction = 0.0F;
                done = true;
            }
            double progress = Math.clamp((this.y - startY) / (maxY - startY), 0.0, 1.0);
            this.gravity = initialPropulsion * (float) Math.pow(progress, 3.0) * 0.12F;
            this.xd = progress * horizontalSprayX;
            this.zd = progress * horizontalSprayZ;
            this.setSpriteFromAge(sprites);
            this.quadSize = minSize + (float) (progress * (maxSize - minSize));
        }

        @Override
        protected Layer getLayer() {
            return Layer.OPAQUE;
        }
    }

    /** SulfurBubbleParticle. */
    static final class SulfurBubble extends SingleQuadParticle {
        private final double yStart;
        private final double yEnd;
        private final float sizeStart;
        private double yPrev;

        SulfurBubble(ClientLevel level, double x, double y, double z, double xa, double za, TextureAtlasSprite sprite) {
            super(level, x, y, z, sprite);
            this.gravity = -0.04F;
            this.friction = 0.85F;
            this.setSize(0.02F, 0.02F);
            this.xd = xa * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
            this.zd = za * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
            this.sizeStart = 0.02F + 0.02F * this.random.nextFloat();
            this.quadSize = this.sizeStart;
            this.lifetime = Integer.MAX_VALUE;
            this.yStart = this.yo;
            this.yEnd = this.yo + 4.0 - 1.0;
            this.yPrev = y;
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.removed && !this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).isSourceOfType(Fluids.WATER)) this.remove();
            if (!this.removed && this.y >= yEnd) this.remove();
            if (!this.removed && this.y <= yPrev) this.remove();
            this.xd += wiggle();
            this.zd += wiggle();
            this.move(this.xd, 0.0, this.zd);
            float travel = (float) ((this.y - yStart) / (yEnd - yStart));
            this.quadSize = sizeStart + travel * (0.15F - sizeStart);
            this.yPrev = this.y;
        }

        private double wiggle() {
            return this.random.nextFloat() * 0.003F * (this.random.nextBoolean() ? 1 : -1) * 0.5;
        }

        @Override
        protected Layer getLayer() {
            return Layer.OPAQUE;
        }
    }

    /** NoxiousGasParticle. */
    static final class NoxiousGas extends BaseAshSmokeParticle {
        private final float fadeOutStartingPoint;

        NoxiousGas(ClientLevel level, double x, double y, double z, double xa, double ya, double za, float scale, SpriteSet sprites) {
            super(level, x, y, z, 0.1F, 0.1F, 0.1F, xa, ya, za, scale, sprites, 0.3F, 5, -0.02F, true);
            this.rCol = 1.0F;
            this.gCol = 1.0F;
            this.bCol = 1.0F;
            this.lifetime = (int) (6.0 / (this.random.nextFloat() * 0.5 + 0.5) * scale);
            this.fadeOutStartingPoint = this.lifetime / 2.0F;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.age > fadeOutStartingPoint) {
                float since = this.age - fadeOutStartingPoint;
                this.setAlpha((this.lifetime - since) / this.lifetime);
            }
        }

        @Override
        public Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }

    /** NoxiousGasCloudParticle, spawns gas puffs around the water surface. */
    static final class NoxiousGasCloud extends NoRenderParticle {
        NoxiousGasCloud(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
            this.lifetime = 20;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.age % 2 == 0) {
                BlockPos source = BlockPos.containing(this.x, this.y, this.z);
                Vec3 p = randomSpawnPoint(this.level, source);
                if (GeyserLogic.canBeReachedByNoxiousGas(this.level, source, p)) {
                    this.level.addAlwaysVisibleParticle(GeyserParticleTypes.NOXIOUS_GAS, p.x, p.y, p.z, 0.0, 0.0, 0.0);
                }
            }
        }

        private static Vec3 randomSpawnPoint(Level level, BlockPos center) {
            var random = level.getRandom();
            Vec3 dir = new Vec3(random.nextFloat() - 0.5F, 0.0, random.nextFloat() - 0.5F).normalize();
            float distance = random.nextFloat() * 3.0F;
            return Vec3.atCenterOf(center).add(dir.scale(distance)).subtract(0.0, 0.25, 0.0);
        }
    }
}
