/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * What this account can reach.
 *
 * A snapshot of finished quests and levels, taken on the client thread and handed to
 * the panel. Deliberately dumb data: no RuneLite imports, so the matching logic below
 * is unit testable without a game client.
 */
public class Account
{
	/** Nothing known - every gate reports UNKNOWN rather than guessing. */
	public static final Account UNKNOWN = new Account(null, null, -1);

	private final Set<String> finishedQuests;
	private final Map<String, Integer> levels;
	private final int slayerLevel;

	public Account(Set<String> finishedQuests, Map<String, Integer> levels, int slayerLevel)
	{
		this.finishedQuests = finishedQuests;
		this.levels = levels == null ? Collections.emptyMap() : levels;
		this.slayerLevel = slayerLevel;
	}

	public boolean isKnown()
	{
		return finishedQuests != null;
	}

	public int getSlayerLevel()
	{
		return slayerLevel;
	}

	/**
	 * Can you get at this monster?
	 *
	 * The gate string comes from the wiki's prose via Option.Gate, so it's things like
	 * "Dragon Slayer II", "hard diary", "dark totem", "grapple". Only quests and levels
	 * can be checked against the client - the rest stay UNKNOWN rather than guessing,
	 * because a wrong LOCKED hides a monster you could actually kill.
	 */
	public Access check(String gate)
	{
		if (gate == null || gate.isEmpty())
		{
			return Access.OPEN;
		}
		if (!isKnown())
		{
			return Access.unknown(gate);
		}

		// consumables and kill counts are per-trip, not account state
		if (gate.equals("dark totem") || gate.equals("key / kc"))
		{
			return Access.unknown(gate);
		}

		if (finishedQuests.contains(normalise(gate)))
		{
			return Access.OPEN;
		}

		// a gate that names a quest we know about, and it isn't finished
		if (KnownQuests.isQuest(gate))
		{
			return Access.locked(gate);
		}

		return Access.unknown(gate);
	}

	static String normalise(String s)
	{
		return s == null ? "" : s.toLowerCase(java.util.Locale.ENGLISH).trim();
	}

	/**
	 * The quest names the client knows, filled in by the plugin at startup.
	 *
	 * Static because the gate strings are matched against it from a pure context; the
	 * plugin populates it once from RuneLite's Quest enum so this class needs no
	 * RuneLite import of its own.
	 */
	static final class KnownQuests
	{
		private static Set<String> names = Collections.emptySet();

		private KnownQuests()
		{
		}

		static void set(Set<String> questNames)
		{
			names = questNames == null ? Collections.emptySet() : questNames;
		}

		static boolean isQuest(String gate)
		{
			return names.contains(normalise(gate));
		}
	}
}
