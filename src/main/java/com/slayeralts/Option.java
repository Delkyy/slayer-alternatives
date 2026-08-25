/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/**
 * One line in the panel. Either a single monster or a run of identical ones squashed
 * together, because "Greater Demon" five times at different combat levels is one
 * decision, not five.
 */
@Value
public class Option
{
	String name;
	String combat;
	String xp;
	double bestXp;

	/** Every place this thing is found, deduped across its statblocks. */
	List<String> locations;

	/** Short tag saying what stands between you and this kill. Empty means nothing does. */
	String gate;
	boolean superior;
	boolean verified;

	public List<String> getLocations()
	{
		return locations == null ? java.util.Collections.emptyList() : locations;
	}

	/** First location, for the one-line summary. */
	public String getLocation()
	{
		return getLocations().isEmpty() ? "" : getLocations().get(0);
	}

	public int getExtraLocations()
	{
		return Math.max(0, getLocations().size() - 1);
	}

	/** How many times better this is than the worst option on the task. */
	public double multiplier(double baseXp)
	{
		return baseXp > 0 ? bestXp / baseXp : 0;
	}

	static List<Option> from(SlayerTask task, boolean sortByXp)
	{
		List<Monster> monsters = new ArrayList<>(task.getMonsters());

		// group by name - the wiki lists a monster once per statblock
		List<List<Monster>> groups = new ArrayList<>();
		for (Monster m : monsters)
		{
			List<Monster> found = null;
			for (List<Monster> g : groups)
			{
				if (g.get(0).getName().equalsIgnoreCase(m.getName()))
				{
					found = g;
					break;
				}
			}
			if (found == null)
			{
				found = new ArrayList<>();
				groups.add(found);
			}
			found.add(m);
		}

		List<Option> out = new ArrayList<>();
		for (List<Monster> g : groups)
		{
			out.add(squash(g));
		}

		if (sortByXp)
		{
			out.sort((a, b) ->
			{
				if (a.superior != b.superior)
				{
					return a.superior ? 1 : -1;   // superiors always last
				}
				return Double.compare(b.bestXp, a.bestXp);
			});
		}
		return out;
	}

	private static Option squash(List<Monster> g)
	{
		Monster first = g.get(0);

		double lo = Double.MAX_VALUE, hi = -1;
		int loCb = Integer.MAX_VALUE, hiCb = -1;
		for (Monster m : g)
		{
			double x = m.xp();
			if (x >= 0)
			{
				lo = Math.min(lo, x);
				hi = Math.max(hi, x);
			}
			int c = parseCombat(m.getCombat());
			if (c >= 0)
			{
				loCb = Math.min(loCb, c);
				hiCb = Math.max(hiCb, c);
			}
		}

		String xp = hi < 0 ? "" : (lo == hi ? trim(hi) : trim(lo) + "-" + trim(hi));
		String cb = hiCb < 0 ? "" : (loCb == hiCb ? String.valueOf(hiCb) : loCb + "-" + hiCb);

		// locations across the whole group, deduped
		List<String> locs = new ArrayList<>();
		for (Monster m : g)
		{
			for (String l : m.getLocations())
			{
				if (!locs.contains(l))
				{
					locs.add(l);
				}
			}
		}

		// a gate only counts if it holds for EVERY statblock in the group. one of the
		// five greater demons lives in the wilderness cave; the other eight spots are
		// open, so the collapsed row is open. gating it on the worst variant is a lie.
		String gate = Gate.from(first.getNotes());
		for (Monster m : g)
		{
			String mg = Gate.from(m.getNotes());
			if (mg.isEmpty())
			{
				gate = "";
				break;
			}
			if (!mg.equals(gate))
			{
				// all gated but differently - say nothing rather than pick one
				gate = "";
				break;
			}
		}

		boolean verified = true;
		for (Monster m : g)
		{
			verified &= m.isVerified();
		}

		return new Option(first.getName(), cb, xp, hi < 0 ? -1 : hi,
			locs, gate, first.isSuperior(), verified);
	}

	private static int parseCombat(String s)
	{
		if (s == null || s.isEmpty())
		{
			return -1;
		}
		// "92" or "172-188" or "53,62" - take the first number we find
		StringBuilder d = new StringBuilder();
		for (char c : s.toCharArray())
		{
			if (c >= '0' && c <= '9')
			{
				d.append(c);
			}
			else if (d.length() > 0)
			{
				break;
			}
		}
		try
		{
			return d.length() == 0 ? -1 : Integer.parseInt(d.toString());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	static String trim(double v)
	{
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	/**
	 * Turns the wiki's requirement prose into something that fits on a row.
	 *
	 * The notes are full sentences - "Requires completion of While Guthix Sleeps to
	 * access" - and a panel row has about 60px for this. Match the shape, not the words.
	 */
	static final class Gate
	{
		private Gate()
		{
		}

		static String from(List<String> notes)
		{
			for (String note : notes)
			{
				String n = note.toLowerCase(Locale.ENGLISH);

				if (n.contains("dark totem"))
				{
					return "dark totem";
				}
				if (n.contains("ecumenical key") || n.contains("kill count"))
				{
					return "key / kc";
				}
				// diaries and gear read as quests to the parser but aren't. name what
				// they actually are - "quest" on a diary gate is just wrong.
				if (n.contains("diary"))
				{
					return diary(note);
				}
				if (n.contains("mith grapple") || n.contains("grapple"))
				{
					return "grapple";
				}
				if (n.contains("requires completion of") || n.contains("to access"))
				{
					return quest(note);
				}
				if (n.contains("combat achievement"))
				{
					return "combat achv";
				}
				if (n.contains("superior"))
				{
					return "rare spawn";
				}
				if (n.contains("wilderness"))
				{
					return "wilderness";
				}
				if (n.contains("two combat styles"))
				{
					return "2 styles";
				}
			}
			return "";
		}

		/** "Hard Wilderness Diary" -> "hard diary". */
		private static String diary(String note)
		{
			String low = note.toLowerCase(Locale.ENGLISH);
			for (String tier : new String[]{"easy", "medium", "hard", "elite"})
			{
				if (low.contains(tier + " "))
				{
					return tier + " diary";
				}
			}
			return "diary";
		}

		/**
		 * Pull the quest name out of the wiki's prose.
		 *
		 * There is no single phrasing. Real examples, all of which have to land on a
		 * name short enough for a 225px row:
		 *   "Requires completion of Dragon Slayer II in order to access."
		 *   "Requires completion of Bone Voyage"
		 *   "Requires completion of Dragon Slayer II; only has 1 spawn."
		 *   "Requires a certain amount of progress in Legends' Quest to access..."
		 *   "Requires Olaf's Quest to be started to access the cavern."
		 */
		private static String quest(String note)
		{
			String low = note.toLowerCase(Locale.ENGLISH);

			int i = low.indexOf("completion of");
			int skip = "completion of".length();
			if (i < 0)
			{
				i = low.indexOf("progress in");
				skip = "progress in".length();
			}
			if (i < 0)
			{
				i = low.indexOf("requires ");
				skip = "requires ".length();
			}
			if (i < 0)
			{
				return "quest";
			}

			String rest = note.substring(i + skip).trim();

			// cut at whatever ends the quest name first
			int cut = rest.length();
			for (String tail : new String[]{" in order", " to ", " for ", " before ",
				";", ".", ",", " and ", " or "})
			{
				int stop = rest.indexOf(tail);
				if (stop > 0)
				{
					cut = Math.min(cut, stop);
				}
			}
			rest = rest.substring(0, cut).trim();

			rest = rest.replaceAll("^(the first part of|partial) ", "").trim();
			return rest.isEmpty() || rest.length() > 24 ? "quest" : rest;
		}
	}
}
