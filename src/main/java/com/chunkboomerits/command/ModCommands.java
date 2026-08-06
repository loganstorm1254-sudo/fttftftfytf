package com.chunkboomerits.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.chunkboomerits.item.ModItems;

/**
 * OP-only command: {@code /chunkboomerits [player] [count]}
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("chunkboomerits")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> giveSelf(ctx.getSource(), 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
								.executes(ctx -> giveSelf(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"))))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> givePlayers(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> givePlayers(
												ctx.getSource(),
												EntityArgument.getPlayers(ctx, "targets"),
												IntegerArgumentType.getInteger(ctx, "count")
										))))
		);
	}

	private static int giveSelf(CommandSourceStack source, int count) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return givePlayers(source, java.util.List.of(player), count);
	}

	private static int givePlayers(CommandSourceStack source, java.util.Collection<ServerPlayer> players, int count) {
		int given = 0;
		for (ServerPlayer player : players) {
			ItemStack stack = new ItemStack(ModItems.CHUNK_BOOMERITS, count);
			if (player.getInventory().add(stack)) {
				player.containerMenu.broadcastChanges();
			} else {
				player.drop(stack, false);
			}
			given++;
		}

		int finalGiven = given;
		if (finalGiven == 1) {
			ServerPlayer only = players.iterator().next();
			source.sendSuccess(
					() -> Component.literal("Gave " + count + " Chunk Boomerits to " + only.getScoreboardName()),
					true
			);
		} else {
			source.sendSuccess(
					() -> Component.literal("Gave " + count + " Chunk Boomerits to " + finalGiven + " players"),
					true
			);
		}
		return given;
	}
}
