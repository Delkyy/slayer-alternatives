/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * A slayer task and everything that counts towards it.
 */
@Value
public class SlayerTask
{
	String task;
	String slayerLevel;
	List<String> alternatives;
	List<String> superiors;
	List<String> locations;
	List<String> items;
	List<String> requirements;
	String masters;
	List<Monster> monsters;

	private static List<String> safe(List<String> l)
	{
		return l == null ? Collections.emptyList() : l;
	}

	public List<String> getAlternatives()
	{
		return safe(alternatives);
	}

	public List<String> getSuperiors()
	{
		return safe(superiors);
	}

	public List<String> getLocations()
	{
		return safe(locations);
	}

	public List<String> getItems()
	{
		return safe(items);
	}

	public List<String> getRequirements()
	{
		return safe(requirements);
	}

	public List<Monster> getMonsters()
	{
		return monsters == null ? Collections.emptyList() : monsters;
	}

	/** Everything you could actually decide to go and kill, best xp first. */
	public List<Monster> choosable()
	{
		List<Monster> out = new ArrayList<>();
		for (Monster m : getMonsters())
		{
			if (!m.isSuperior())
			{
				out.add(m);
			}
		}
		out.sort((a, b) -> Double.compare(b.xp(), a.xp()));
		return out;
	}

	/** The best slayer xp on offer, ignoring superiors. -1 when nothing is known. */
	public double bestXp()
	{
		double best = -1;
		for (Monster m : choosable())
		{
			best = Math.max(best, m.xp());
		}
		return best;
	}
}
