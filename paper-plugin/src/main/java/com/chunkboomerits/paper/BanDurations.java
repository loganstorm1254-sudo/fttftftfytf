package com.chunkboomerits.paper;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ban durations like {@code 1s}, {@code 5m}, {@code 1d}, {@code perm}.
 * Permanent is represented as {@link #PERMANENT} ({@code -1}).
 */
public final class BanDurations {
	public static final long PERMANENT = -1L;

	private static final Pattern TOKEN = Pattern.compile(
			"^(\\d+)\\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks|mo|month|months|y|yr|year|years)$",
			Pattern.CASE_INSENSITIVE
	);

	private BanDurations() {
	}

	public record Parsed(long millis, String label) {
		public boolean permanent() {
			return millis < 0;
		}

		public Duration toDuration() {
			if (permanent()) {
				return null;
			}
			return Duration.ofMillis(millis);
		}
	}

	public static Optional<Parsed> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace("_", "");
		if (cleaned.equals("perm") || cleaned.equals("permanent") || cleaned.equals("forever")
				|| cleaned.equals("perma") || cleaned.equals("infinite") || cleaned.equals("inf")) {
			return Optional.of(new Parsed(PERMANENT, "Permanent"));
		}

		// Allow glued combos like 1d12h by summing tokens split on non-alnum boundaries... keep simple:
		// either one token, or space-separated tokens that sum.
		String[] parts = cleaned.split("\\s+");
		long total = 0;
		StringBuilder label = new StringBuilder();
		for (String part : parts) {
			Matcher m = TOKEN.matcher(part);
			if (!m.matches()) {
				return Optional.empty();
			}
			long amount = Long.parseLong(m.group(1));
			if (amount <= 0) {
				return Optional.empty();
			}
			String unit = m.group(2).toLowerCase(Locale.ROOT);
			long millis = switch (unit) {
				case "s", "sec", "secs", "second", "seconds" -> amount * 1_000L;
				case "m", "min", "mins", "minute", "minutes" -> amount * 60_000L;
				case "h", "hr", "hrs", "hour", "hours" -> amount * 3_600_000L;
				case "d", "day", "days" -> amount * 86_400_000L;
				case "w", "week", "weeks" -> amount * 7L * 86_400_000L;
				case "mo", "month", "months" -> amount * 30L * 86_400_000L;
				case "y", "yr", "year", "years" -> amount * 365L * 86_400_000L;
				default -> -2L;
			};
			if (millis < 0) {
				return Optional.empty();
			}
			// Cap individual absurd values (100 years)
			if (millis > 100L * 365L * 86_400_000L) {
				return Optional.empty();
			}
			total += millis;
			if (!label.isEmpty()) {
				label.append(' ');
			}
			label.append(prettyUnit(amount, unit));
		}
		if (total <= 0) {
			return Optional.empty();
		}
		return Optional.of(new Parsed(total, label.toString()));
	}

	public static String formatMillis(long millis) {
		if (millis < 0) {
			return "Permanent";
		}
		long seconds = millis / 1000L;
		if (seconds < 60) {
			return seconds + (seconds == 1 ? " second" : " seconds");
		}
		long minutes = seconds / 60L;
		if (minutes < 60) {
			return minutes + (minutes == 1 ? " minute" : " minutes");
		}
		long hours = minutes / 60L;
		if (hours < 24) {
			return hours + (hours == 1 ? " hour" : " hours");
		}
		long days = hours / 24L;
		if (days < 7) {
			return days + (days == 1 ? " day" : " days");
		}
		if (days < 30) {
			long weeks = days / 7L;
			return weeks + (weeks == 1 ? " week" : " weeks");
		}
		if (days < 365) {
			long months = days / 30L;
			return months + (months == 1 ? " month" : " months");
		}
		long years = days / 365L;
		return years + (years == 1 ? " year" : " years");
	}

	private static String prettyUnit(long amount, String unit) {
		return switch (unit) {
			case "s", "sec", "secs", "second", "seconds" -> amount + (amount == 1 ? " second" : " seconds");
			case "m", "min", "mins", "minute", "minutes" -> amount + (amount == 1 ? " minute" : " minutes");
			case "h", "hr", "hrs", "hour", "hours" -> amount + (amount == 1 ? " hour" : " hours");
			case "d", "day", "days" -> amount + (amount == 1 ? " day" : " days");
			case "w", "week", "weeks" -> amount + (amount == 1 ? " week" : " weeks");
			case "mo", "month", "months" -> amount + (amount == 1 ? " month" : " months");
			case "y", "yr", "year", "years" -> amount + (amount == 1 ? " year" : " years");
			default -> amount + " " + unit;
		};
	}
}
