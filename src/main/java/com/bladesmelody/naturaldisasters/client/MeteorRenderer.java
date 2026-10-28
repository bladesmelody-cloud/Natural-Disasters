package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.entity.MeteorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;

/** The meteor itself: a glowing, tumbling lump of magma rock, sized to its crater. */
public class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorRenderer.State> {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private static final int FULL_BRIGHT = 0xF000F0;

    public static class State extends EntityRenderState {
        final BlockModelRenderState rock = new BlockModelRenderState();
        float size = 1;
    }

    private final BlockModelResolver models;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.models = context.getBlockModelResolver();
        this.shadowRadius = 0;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MeteorEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.size = entity.size();
        models.update(state.rock, Blocks.MAGMA_BLOCK.defaultBlockState(), CONTEXT);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.rock.isEmpty()) return;
        poseStack.pushPose();
        float s = state.size;
        poseStack.translate(0, s / 2, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 13));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * 7));
        poseStack.scale(s, s, s);
        poseStack.translate(-0.5, -0.5, -0.5);
        state.rock.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
