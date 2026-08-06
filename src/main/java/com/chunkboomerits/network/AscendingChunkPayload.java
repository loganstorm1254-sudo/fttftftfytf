package com.chunkboomerits.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.chunkboomerits.ChunkBoomeritsMod;

/**
 * Syncs the rising chunk's visible block mesh to tracking clients (one packet, not thousands of entities).
 */
public record AscendingChunkPayload(int entityId, int[] packedPositions, int[] stateIds) implements CustomPacketPayload {
	public static final Type<AscendingChunkPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(ChunkBoomeritsMod.MOD_ID, "ascending_chunk"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AscendingChunkPayload> CODEC = StreamCodec.of(
			AscendingChunkPayload::write,
			AscendingChunkPayload::read
	);

	private static void write(RegistryFriendlyByteBuf buf, AscendingChunkPayload payload) {
		buf.writeVarInt(payload.entityId);
		buf.writeVarInt(payload.packedPositions.length);
		for (int packed : payload.packedPositions) {
			buf.writeInt(packed);
		}
		for (int stateId : payload.stateIds) {
			buf.writeVarInt(stateId);
		}
	}

	private static AscendingChunkPayload read(RegistryFriendlyByteBuf buf) {
		int entityId = buf.readVarInt();
		int count = buf.readVarInt();
		int[] packed = new int[count];
		for (int i = 0; i < count; i++) {
			packed[i] = buf.readInt();
		}
		int[] states = new int[count];
		for (int i = 0; i < count; i++) {
			states[i] = buf.readVarInt();
		}
		return new AscendingChunkPayload(entityId, packed, states);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	/** Pack local chunk coords: lx (0-15), ly (0-4095 relative), lz (0-15). */
	public static int pack(int lx, int ly, int lz) {
		return (lx & 15) | ((lz & 15) << 4) | ((ly & 0xFFF) << 8);
	}

	public static int unpackX(int packed) {
		return packed & 15;
	}

	public static int unpackZ(int packed) {
		return (packed >> 4) & 15;
	}

	public static int unpackY(int packed) {
		return (packed >> 8) & 0xFFF;
	}
}
