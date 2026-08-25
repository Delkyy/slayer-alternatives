/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerAltsConfig.GROUP)
public interface SlayerAltsConfig extends Config
{
	String GROUP = "slayeralternatives";

	@ConfigItem(
		keyName = "hideSuperiors",
		name = "Hide superiors",
		description = "Superiors are rare spawns you can't choose to farm. Off shows them greyed out.",
		position = 1
	)
	default boolean hideSuperiors()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sortByXp",
		name = "Sort by slayer XP",
		description = "Best XP first. Off keeps the wiki's own order.",
		position = 2
	)
	default boolean sortByXp()
	{
		return true;
	}
}
