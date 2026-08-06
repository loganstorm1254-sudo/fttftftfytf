package com.chunkboomerits.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.chunkboomerits.ChunkBoomeritsMod;
import com.chunkboomerits.network.ModNetworking;

public final class ModEntities {
	public static final EntityType<ChunkBoomeritsEntity> CHUNK_BOOMERITS = register(
			"chunk_boomerits",
			EntityType.Builder.<ChunkBoomeritsEntity>of(ChunkBoomeritsEntity::new, MobCategory.MISC)
					.sized(1.25F, 1.25F)
					.clientTrackingRange(8)
					.updateInterval(10)
	);

	public static final EntityType<AscendingChunkEntity> ASCENDING_CHUNK = register(
			"ascending_chunk",
			EntityType.Builder.<AscendingChunkEntity>of(AscendingChunkEntity::new, MobCategory.MISC)
					.sized(16.0F, 384.0F)
					.clientTrackingRange(12)
					.updateInterval(1)
					.fireImmune()
	);

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(
				Registries.ENTITY_TYPE,
				Identifier.fromNamespaceAndPath(ChunkBoomeritsMod.MOD_ID, name)
		);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {
		ModNetworking.register();
		ChunkLifter.ensureRegistered();
	}
}
