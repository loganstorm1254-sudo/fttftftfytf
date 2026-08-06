package com.chunkboomerits.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import net.fabricmc.api.ClientModInitializer;

import com.chunkboomerits.entity.ModEntities;

public class ChunkBoomeritsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Scale 2.5 makes the projectile read as a big ball in the air.
		EntityRenderers.register(ModEntities.CHUNK_BOOMERITS, context -> new ThrownItemRenderer<>(context, 2.5F, true));
	}
}
