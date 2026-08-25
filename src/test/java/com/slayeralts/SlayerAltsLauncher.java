/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SlayerAltsLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerAltsPlugin.class);
		RuneLite.main(args);
	}
}
