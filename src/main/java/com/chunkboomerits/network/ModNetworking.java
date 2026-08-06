package com.chunkboomerits.network;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.chunkboomerits.entity.AscendingChunkEntity;

public final class ModNetworking {
	/** ~2 MiB cap — surface meshes stay well under this. */
	private static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;

	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().registerLarge(AscendingChunkPayload.ID, AscendingChunkPayload.CODEC, MAX_PAYLOAD_BYTES);

		EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
			if (entity instanceof AscendingChunkEntity ascending) {
				ServerPlayNetworking.send(player, ascending.createPayload());
			}
		});
	}
}
