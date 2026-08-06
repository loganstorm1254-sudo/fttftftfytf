package com.chunkboomerits.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.chunkboomerits.ChunkBoomeritsMod;
import com.chunkboomerits.item.ModItems;

/**
 * Gives Chunk Boomerits. Visible in the command list; only OPs (level 2+) can run it.
 *
 * <pre>
 * /chunkboomerits
 * /chunkboomerits 16
 * /chunkboomerits Steve
 * /chunkboomerits @a 4
 * </pre>
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			registerTree(dispatcher, "chunkboomerits");
			registerTree(dispatcher, "boomerits");
			ChunkBoomeritsMod.LOGGER.info("Registered /chunkboomerits and /boomerits ({})", environment);
		});
	}

	private static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
		dispatcher.register(
				Commands.literal(name)
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

	private static boolean ensureOp(CommandSourceStack source) {
		if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return true;
		}
		source.sendFailure(Component.literal("Chunk Boomerits is OP-only (need permission level 2+ / cheats on)."));
		return false;
	}

	private static int giveSelf(CommandSourceStack source, int count) throws CommandSyntaxException {
		if (!ensureOp(source)) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		return givePlayers(source, List.of(player), count);
	}

	private static int givePlayers(CommandSourceStack source, Collection<ServerPlayer> players, int count) {
		if (!ensureOp(source)) {
			return 0;
		}

		int given = 0;
		for (ServerPlayer player : players) {
			ItemStack stack = new ItemStack(ModItems.CHUNK_BOOMERITS, count);
			boolean added = player.getInventory().add(stack);
			if (!added || !stack.isEmpty()) {
				player.drop(stack, false);
			}
			player.containerMenu.broadcastChanges();
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
