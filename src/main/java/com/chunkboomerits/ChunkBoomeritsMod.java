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

	/** How many blocks up the chunk rises before it is eaten. */
	public static final double EAT_HEIGHT = 100.0;

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.initialize();
		LOGGER.info("Chunk Boomerits ready — throw the big ball, watch the chunk fly, then get eaten.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
