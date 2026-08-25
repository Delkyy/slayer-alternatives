/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The task table, and how to find things in it.
 *
 * No RuneLite imports on purpose - this is the part with the logic in it, so it has to
 * be testable without a game client.
 */
public class TaskBook
{
	private final List<SlayerTask> tasks;
	private final Map<String, SlayerTask> byKey = new HashMap<>();

	public TaskBook(List<SlayerTask> tasks)
	{
		this.tasks = tasks == null ? Collections.emptyList() : tasks;
		for (SlayerTask t : this.tasks)
		{
			byKey.put(key(t.getTask()), t);
		}
	}

	public List<SlayerTask> all()
	{
		return Collections.unmodifiableList(tasks);
	}

	/**
	 * Squash a task name to something comparable.
	 *
	 * The client hands you "GREATER DEMONS" in caps, the wiki page is "Greater demons",
	 * and the two wiki pages themselves disagree on plurals - "Bloodveld" on one,
	 * "Bloodvelds" on the other. Lowercase, drop everything that isn't a letter, drop a
	 * trailing s. Crude, and it's what makes all three line up.
	 */
	static String key(String name)
	{
		if (name == null)
		{
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ENGLISH).toCharArray())
		{
			if (c >= 'a' && c <= 'z')
			{
				sb.append(c);
			}
		}
		String s = sb.toString();
		return s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
	}

	/** Exact-ish lookup for a task name from the client. Null when nothing matches. */
	public SlayerTask find(String taskName)
	{
		return byKey.get(key(taskName));
	}

	/**
	 * Free text search over task names, alternatives and monsters.
	 *
	 * Deliberately matches monsters too - "vorkath" should find the blue dragon task,
	 * because "what task do I need for this boss" is the same question backwards.
	 */
	public List<SlayerTask> search(String query)
	{
		if (query == null || query.trim().isEmpty())
		{
			return all();
		}

		String q = query.toLowerCase(Locale.ENGLISH).trim();
		List<SlayerTask> hits = new ArrayList<>();
		List<SlayerTask> weak = new ArrayList<>();

		for (SlayerTask t : tasks)
		{
			if (t.getTask().toLowerCase(Locale.ENGLISH).contains(q))
			{
				hits.add(t);
				continue;
			}

			if (matchesAny(t.getAlternatives(), q) || matchesMonster(t, q)
				|| matchesAny(t.getLocations(), q))
			{
				weak.add(t);
			}
		}

		// name matches first, then things that merely mention it
		hits.addAll(weak);
		return hits;
	}

	private static boolean matchesAny(List<String> values, String q)
	{
		for (String v : values)
		{
			if (v.toLowerCase(Locale.ENGLISH).contains(q))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean matchesMonster(SlayerTask t, String q)
	{
		for (Monster m : t.getMonsters())
		{
			if (m.getName().toLowerCase(Locale.ENGLISH).contains(q)
				|| matchesAny(m.getLocations(), q))
			{
				return true;
			}
		}
		return false;
	}
}
