package com.chunkboomerits.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import com.chunkboomerits.entity.AscendingChunkEntity;
import com.chunkboomerits.network.AscendingChunkPayload;

@Environment(EnvType.CLIENT)
public class AscendingChunkRenderer extends EntityRenderer<AscendingChunkEntity, AscendingChunkRenderState> {
	public AscendingChunkRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public boolean shouldRender(AscendingChunkEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		return super.shouldRender(entity, frustum, camX, camY, camZ);
	}

	@Override
	public void submit(
			AscendingChunkRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			CameraRenderState cameraRenderState
	) {
		int[] packed = state.packedPositions;
		BlockState[] states = state.states;
		int count = packed.length;
		if (count == 0) {
			return;
		}

		MovingBlockRenderState moving = state.movingBlock;
		moving.level = EmptyBlockAndTintGetter.INSTANCE;

		int baseX = (int) Math.floor(state.x);
		int baseY = (int) Math.floor(state.y);
		int baseZ = (int) Math.floor(state.z);

		for (int i = 0; i < count; i++) {
			BlockState blockState = states[i];
			if (blockState.getRenderShape() != RenderShape.MODEL) {
				continue;
			}

			int lx = AscendingChunkPayload.unpackX(packed[i]);
			int ly = AscendingChunkPayload.unpackY(packed[i]);
			int lz = AscendingChunkPayload.unpackZ(packed[i]);

			poseStack.pushPose();
			poseStack.translate(lx, ly, lz);

			state.scratchPos.set(baseX + lx, baseY + ly, baseZ + lz);
			moving.randomSeedPos = state.scratchPos;
			moving.blockPos = state.scratchPos;
			moving.blockState = blockState;

			submitNodeCollector.submitMovingBlock(poseStack, moving);
			poseStack.popPose();
		}
	}

	@Override
	public AscendingChunkRenderState createRenderState() {
		return new AscendingChunkRenderState();
	}

	@Override
	public void extractRenderState(AscendingChunkEntity entity, AscendingChunkRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		int[] packed = entity.getPackedPositions();
		if (state.packedPositions != packed) {
			state.setMesh(packed, entity.getStateIds());
		}
	}
}
