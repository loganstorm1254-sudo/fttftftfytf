package com.chunkboomerits;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chunkboomerits.entity.ModEntities;
import com.chunkboomerits.item.ModItems;

public class ChunkBoomeritsMod implements ModInitializer {
	public static final String MOD_ID = "chunkboomerits";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.initialize();
		LOGGER.info("Chunk Boomerits ready — throw the ball to delete a chunk.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
