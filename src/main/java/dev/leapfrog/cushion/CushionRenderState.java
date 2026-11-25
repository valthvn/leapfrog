package dev.leapfrog.cushion;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public class CushionRenderState extends EntityRenderState {
    public Identifier texture = CushionDetector.texture(DyeColor.WHITE);
    public float yRot;
}
