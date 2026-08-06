package com.chunkboomerits.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.Entity;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.chunkboomerits.entity.AscendingChunkEntity;
import com.chunkboomerits.entity.ModEntities;
import com.chunkboomerits.network.AscendingChunkPayload;

public class ChunkBoomeritsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.CHUNK_BOOMERITS, context -> new ThrownItemRenderer<>(context, 2.5F, true));
		EntityRenderers.register(ModEntities.ASCENDING_CHUNK, AscendingChunkRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(AscendingChunkPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				Entity entity = Minecraft.getInstance().level == null
						? null
						: Minecraft.getInstance().level.getEntity(payload.entityId());
				if (entity instanceof AscendingChunkEntity ascending) {
					ascending.acceptClientMesh(payload.packedPositions(), payload.stateIds());
				}
			});
		});
	}
}
