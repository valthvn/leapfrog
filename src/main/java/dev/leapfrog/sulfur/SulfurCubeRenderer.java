package dev.leapfrog.sulfur;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.leapfrog.block.FutureBlock;
import dev.leapfrog.block.FutureBlocks;
import dev.leapfrog.item.FutureItems;
import java.util.Locale;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Draws the slime ViaBackwards uses for a sulfur cube like 26.3 does: translucent shell with the core
 * or the swallowed block inside, small model for babies, and the TNT-style swell and flash when primed.
 */
public class SulfurCubeRenderer extends MobRenderer<Slime, SulfurCubeRenderer.State, SulfurCubeRenderer.Model> {
    private static final Identifier OUTER = tex("sulfur_cube_outer");
    private static final Identifier OUTER_SMALL = tex("sulfur_cube_outer_small");
    private static final Identifier INNER = tex("sulfur_cube_inner");
    private static final Identifier INNER_SMALL = tex("sulfur_cube_inner_small");

    private final Model normalModel;
    private final Model smallModel;

    public SulfurCubeRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(outerLayer().bakeRoot()), 0.25F);
        this.normalModel = this.model;
        this.smallModel = new Model(smallOuterLayer().bakeRoot());
        Model inner = new Model(innerLayer().bakeRoot());
        Model smallInner = new Model(smallInnerLayer().bakeRoot());
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                int overlay = state.fuse > 0.0F && isLit(state.fuse)
                        ? OverlayTexture.pack(OverlayTexture.u(1.0F), 10)
                        : LivingEntityRenderer.getOverlayCoords(state, 0.0F);
                if (state.containedBlock != null) {
                    pose.pushPose();
                    pose.mulPose(Axis.XP.rotationDegrees(180.0F));
                    if (state.baby) pose.scale(0.5F, 0.5F, 0.5F);
                    pose.translate(-0.5F, -0.518F, -0.5F);
                    collector.submitBlock(pose, state.containedBlock, state.lightCoords, overlay, state.outlineColor);
                    pose.popPose();
                } else if (!state.isInvisible) {
                    collector.order(-1).submitModel(state.baby ? smallInner : inner, state, pose,
                            RenderTypes.entityTranslucent(state.baby ? INNER_SMALL : INNER), light, overlay, -1, null, state.outlineColor, null);
                }
            }
        });
    }

    private static Identifier tex(String name) {
        return Identifier.withDefaultNamespace("textures/entity/sulfur_cube/" + name + ".png");
    }

    /** ViaBackwards names the slime after the sulfur cube. */
    public static boolean isSulfurCube(Entity entity) {
        if (!(entity instanceof Slime) || entity.getType() != EntityType.SLIME) return false;
        Component name = entity.getCustomName();
        if (name == null) return false;
        if (name.getContents() instanceof TranslatableContents t && t.getKey().endsWith("sulfur_cube")) return true;
        return name.getString().toLowerCase(Locale.ROOT).contains("sulfur cube");
    }

    static LayerDefinition outerLayer() {
        return cube(0, 0, 18, 128);
    }

    static LayerDefinition innerLayer() {
        return cube(0, 36, 16, 128);
    }

    static LayerDefinition smallOuterLayer() {
        return cube(0, 0, 10, 64);
    }

    static LayerDefinition smallInnerLayer() {
        return cube(0, 20, 8, 64);
    }

    private static LayerDefinition cube(int u, int v, int size, int texSize) {
        MeshDefinition mesh = new MeshDefinition();
        float half = size / 2.0F;
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().texOffs(u, v).addBox(-half, -half, -half, size, size, size), PartPose.ZERO);
        return LayerDefinition.create(mesh, texSize, texSize);
    }

    /** Same blink as TntRenderer. */
    private static boolean isLit(float fuse) {
        return (int) fuse / 5 % 2 == 0;
    }

    /** Same as TntRenderer.getSwellAmount. */
    private static float swellAmount(float fuse) {
        float g = Mth.clamp(1.0F - fuse / 10.0F, 0.0F, 1.0F);
        g *= g;
        g *= g;
        return g * 0.3F;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        this.model = state.baby ? this.smallModel : this.normalModel;
        super.submit(state, pose, collector, camera);
    }

    @Override
    protected float getShadowRadius(State state) {
        return state.size * 0.25F;
    }

    @Override
    protected void scale(State state, PoseStack pose) {
        pose.scale(0.999F, 0.999F, 0.999F);
        pose.translate(0.0F, 0.001F, 0.0F);
        float size = state.size;
        // A cube holding a block does not squish.
        float ss = state.containedBlock == null ? state.squish / (size * 0.5F + 1.0F) : 0.0F;
        float w = 1.0F / (ss + 1.0F);
        pose.scale(w * size, 1.0F / w * size, w * size);
        if (state.fuse > 0.0F && state.fuse < 10.0F) {
            float s = 1.0F + swellAmount(state.fuse);
            pose.scale(s, s, s);
        }
        float vOffset = state.baby ? 1.24F : 0.98F;
        float extraDownscale = state.baby ? 1.0F : 0.5F;
        float onePixelUp = (state.isInvisible ? 0.0F : 1.0F) / 16.0F;
        pose.scale(extraDownscale, extraDownscale, extraDownscale);
        pose.translate(0.0F, vOffset - onePixelUp, 0.0F);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return state.baby ? OUTER_SMALL : OUTER;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Slime slime, State state, float partialTick) {
        super.extractRenderState(slime, state, partialTick);
        state.squish = Mth.lerp(partialTick, slime.oSquish, slime.squish);
        state.size = slime.getSize();
        state.nameTag = null; // the stand-in's "Sulfur Cube" label does not exist in 26.x
        state.baby = SulfurCubeData.isBaby(slime.getId());
        state.fuse = SulfurCubeData.fuseRemaining(slime.getId());
        state.containedBlock = containedBlock(slime.getItemBySlot(EquipmentSlot.BODY));
    }

    /** Block held in the body slot, with ViaBackwards items mapped back to 26.x blocks. */
    static @Nullable BlockState containedBlock(ItemStack stack) {
        if (stack.isEmpty()) return null;
        BlockState state = null;
        Identifier original = FutureItems.originalId(stack);
        if (original != null) {
            FutureBlock future = FutureBlocks.all().get(original.getPath());
            if (future != null) state = future.defaultBlockState();
        }
        if (state == null && stack.getItem() instanceof BlockItem blockItem) {
            // 26.3 draws heads with the head layer, which we don't have. Show the core instead.
            if (blockItem.getBlock() instanceof AbstractSkullBlock) return null;
            state = blockItem.getBlock().defaultBlockState();
        }
        if (state == null) return null;
        BlockItemStateProperties props = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        state = props.apply(state);
        // No block model (chests...), show the core.
        return state.getRenderShape() == RenderShape.MODEL ? state : null;
    }

    /** Own state class so EntityRenderDispatcherMixin can find this renderer again. */
    public static class State extends SlimeRenderState {
        public boolean baby;
        public float fuse;
        public @Nullable BlockState containedBlock;
    }

    public static class Model extends EntityModel<State> {
        Model(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
        }
    }
}
