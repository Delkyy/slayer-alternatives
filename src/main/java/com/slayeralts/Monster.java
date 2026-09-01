/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * One monster that counts for a task.
 *
 * Plain data, no RuneLite imports, so the matching logic stays unit testable.
 */
@Value
public class Monster
{
	@SerializedName("monster")
	String name;
	String combat;
	String slayerXp;
	List<String> locations;
	List<String> notes;

	/** melee/ranged/magic, however many the statblock actually hits with. Empty if unknown. */
	List<String> styles;

	/** Rare spawn you can't choose to farm, so it never sets the headline number. */
	boolean superior;

	/** False means the number came off an article table with no infobox to check it. */
	boolean verified;

	public List<String> getLocations()
	{
		return locations == null ? Collections.emptyList() : locations;
	}

	public List<String> getNotes()
	{
		return notes == null ? Collections.emptyList() : notes;
	}

	public List<String> getStyles()
	{
		return styles == null ? Collections.emptyList() : styles;
	}

	/** Slayer xp as a number, or -1 when the wiki didn't give one. */
	public double xp()
	{
		if (slayerXp == null || slayerXp.isEmpty())
		{
			return -1;
		}
		try
		{
			return Double.parseDouble(slayerXp.replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
