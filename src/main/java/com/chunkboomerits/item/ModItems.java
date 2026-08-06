package com.chunkboomerits.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import com.chunkboomerits.ChunkBoomeritsMod;

public final class ModItems {
	public static final Item CHUNK_BOOMERITS = register(
			"chunk_boomerits",
			ChunkBoomeritsItem::new,
			new Item.Properties().stacksTo(16)
	);

	private ModItems() {
	}

	public static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ChunkBoomeritsMod.MOD_ID, name));
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
		// Operator Utilities tab (visible when you have OP / cheats)
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.OP_BLOCKS)
				.register(entries -> entries.accept(CHUNK_BOOMERITS));
	}
}
