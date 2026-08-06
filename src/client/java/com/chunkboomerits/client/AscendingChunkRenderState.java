package com.chunkboomerits.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AscendingChunkRenderState extends EntityRenderState {
	public int[] packedPositions = new int[0];
	public BlockState[] states = new BlockState[0];
	public final MovingBlockRenderState movingBlock = new MovingBlockRenderState();
	public final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

	public void setMesh(int[] packedPositions, int[] stateIds) {
		this.packedPositions = packedPositions;
		this.states = new BlockState[stateIds.length];
		for (int i = 0; i < stateIds.length; i++) {
			this.states[i] = Block.stateById(stateIds[i]);
		}
	}
}
