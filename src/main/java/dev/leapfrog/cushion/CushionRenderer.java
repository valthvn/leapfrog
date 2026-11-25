package dev.leapfrog.cushion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.DyeColor;

/** The 26.3 cushion renderer, used for the falling block ViaBackwards sends instead. */
public class CushionRenderer extends EntityRenderer<FallingBlockEntity, CushionRenderState> {
    private final CushionModel model;

    public CushionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new CushionModel(CushionModel.createBodyLayer().bakeRoot());
        this.shadowRadius = 0.0F;
    }

    @Override
    public CushionRenderState createRenderState() {
        return new CushionRenderState();
    }

    @Override
    public void extractRenderState(FallingBlockEntity entity, CushionRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        DyeColor color = CushionDetector.cushionColor(entity);
        state.texture = CushionDetector.texture(color == null ? DyeColor.WHITE : color);
        state.yRot = entity.getYRot();
        // Real cushions don't show a name tag.
        state.nameTag = null;
    }

    @Override
    public void submit(CushionRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Direction.fromYRot(state.yRot).toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(0.0, -0.25, 0.0);
        collector.submitModel(this.model, state, poseStack, this.model.renderType(state.texture),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
