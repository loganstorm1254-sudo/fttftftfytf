package com.chunkboomerits.paper.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.chunkboomerits.paper.ChunkBoomeritsPlugin;

/**
 * Per-player sidebar showing $ balance. Style is editable via the OP shovel GUI.
 */
public final class EconomyScoreboard {
	static final NamedTextColor[] COLORS = {
			NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.AQUA, NamedTextColor.GREEN,
			NamedTextColor.LIGHT_PURPLE, NamedTextColor.RED, NamedTextColor.WHITE, NamedTextColor.DARK_AQUA
	};

	private final ChunkBoomeritsPlugin plugin;
	private final EconomyService economy;
	private final Map<UUID, Scoreboard> boards = new HashMap<>();

	private boolean enabled = true;
	private int titleColorIndex = 0;
	private int balanceColorIndex = 1;
	private boolean animation = false;
	private String titleText = "Economy";
	private BukkitTask animTask;
	private int animTick;

	public EconomyScoreboard(ChunkBoomeritsPlugin plugin, EconomyService economy) {
		this.plugin = plugin;
		this.economy = economy;
	}

	public void load() {
		FileConfiguration config = plugin.getConfig();
		enabled = config.getBoolean("economy.scoreboard.enabled", true);
		titleColorIndex = clampIndex(config.getInt("economy.scoreboard.title-color", 0));
		balanceColorIndex = clampIndex(config.getInt("economy.scoreboard.balance-color", 1));
		animation = config.getBoolean("economy.scoreboard.animation", false);
		titleText = config.getString("economy.scoreboard.title", "Economy");
		restartAnimation();
	}

	public void save() {
		FileConfiguration config = plugin.getConfig();
		config.set("economy.scoreboard.enabled", enabled);
		config.set("economy.scoreboard.title-color", titleColorIndex);
		config.set("economy.scoreboard.balance-color", balanceColorIndex);
		config.set("economy.scoreboard.animation", animation);
		config.set("economy.scoreboard.title", titleText);
		plugin.saveConfig();
	}

	public void show(Player player) {
		if (!enabled) {
			hide(player);
			return;
		}
		Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
		Objective objective = board.getObjective("cbecon");
		if (objective == null) {
			objective = board.registerNewObjective("cbecon", Criteria.DUMMY, titleComponent());
			objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		} else {
			objective.displayName(titleComponent());
		}

		for (String entry : board.getEntries()) {
			board.resetScores(entry);
		}

		String balLine = economy.format(economy.getBalance(player));
		objective.getScore("§7Your balance").setScore(2);
		objective.getScore(balanceEntry(balLine)).setScore(1);
		player.setScoreboard(board);
	}

	private String balanceEntry(String formatted) {
		char code = legacyCode(colorAt(balanceColorIndex));
		return "§" + code + formatted;
	}

	private Component titleComponent() {
		NamedTextColor color = animation ? colorAt((titleColorIndex + animTick) % COLORS.length) : colorAt(titleColorIndex);
		return Component.text(titleText, color).decoration(TextDecoration.BOLD, true);
	}

	public void refresh(Player player) {
		if (player == null || !player.isOnline() || !enabled) {
			return;
		}
		show(player);
	}

	public void refreshAll() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			refresh(player);
		}
	}

	public void hide(Player player) {
		boards.remove(player.getUniqueId());
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	public void remove(Player player) {
		boards.remove(player.getUniqueId());
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		save();
		if (!enabled) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				hide(player);
			}
		} else {
			refreshAll();
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void cycleTitleColor() {
		titleColorIndex = (titleColorIndex + 1) % COLORS.length;
		save();
		refreshAll();
	}

	public void cycleBalanceColor() {
		balanceColorIndex = (balanceColorIndex + 1) % COLORS.length;
		save();
		refreshAll();
	}

	public void toggleAnimation() {
		animation = !animation;
		save();
		restartAnimation();
		refreshAll();
	}

	public boolean isAnimation() {
		return animation;
	}

	public NamedTextColor titleColor() {
		return colorAt(titleColorIndex);
	}

	public NamedTextColor balanceColor() {
		return colorAt(balanceColorIndex);
	}

	public String titleText() {
		return titleText;
	}

	public void setTitleText(String titleText) {
		this.titleText = titleText == null || titleText.isBlank() ? "Economy" : titleText;
		save();
		refreshAll();
	}

	private void restartAnimation() {
		if (animTask != null) {
			animTask.cancel();
			animTask = null;
		}
		if (!animation) {
			return;
		}
		animTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			animTick++;
			for (Player player : Bukkit.getOnlinePlayers()) {
				Scoreboard board = boards.get(player.getUniqueId());
				if (board == null) {
					continue;
				}
				Objective objective = board.getObjective("cbecon");
				if (objective != null) {
					objective.displayName(titleComponent());
				}
			}
		}, 10L, 10L);
	}

	public void shutdown() {
		if (animTask != null) {
			animTask.cancel();
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			hide(player);
		}
		boards.clear();
	}

	private static NamedTextColor colorAt(int index) {
		return COLORS[clampIndex(index)];
	}

	private static int clampIndex(int index) {
		if (index < 0) {
			return 0;
		}
		return index % COLORS.length;
	}

	static char legacyCode(TextColor color) {
		if (color.equals(NamedTextColor.BLACK)) return '0';
		if (color.equals(NamedTextColor.DARK_BLUE)) return '1';
		if (color.equals(NamedTextColor.DARK_GREEN)) return '2';
		if (color.equals(NamedTextColor.DARK_AQUA)) return '3';
		if (color.equals(NamedTextColor.DARK_RED)) return '4';
		if (color.equals(NamedTextColor.DARK_PURPLE)) return '5';
		if (color.equals(NamedTextColor.GOLD)) return '6';
		if (color.equals(NamedTextColor.GRAY)) return '7';
		if (color.equals(NamedTextColor.DARK_GRAY)) return '8';
		if (color.equals(NamedTextColor.BLUE)) return '9';
		if (color.equals(NamedTextColor.GREEN)) return 'a';
		if (color.equals(NamedTextColor.AQUA)) return 'b';
		if (color.equals(NamedTextColor.RED)) return 'c';
		if (color.equals(NamedTextColor.LIGHT_PURPLE)) return 'd';
		if (color.equals(NamedTextColor.YELLOW)) return 'e';
		return 'f';
	}
}
