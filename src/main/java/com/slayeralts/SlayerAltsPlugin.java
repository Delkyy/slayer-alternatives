/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Slayer Alternatives",
	description = "What else counts for your slayer task, and what it's worth",
	tags = {"slayer", "task", "alternatives", "boss", "xp", "pvm"}
)
@Slf4j
public class SlayerAltsPlugin extends Plugin
{
	// core's slayer plugin writes the current task here. reading it beats re-deriving it
	// from the DBTable rows ourselves, and it's already correct on login.
	private static final String SLAYER_GROUP = "slayer";
	private static final String TASK_NAME_KEY = "taskName";

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Getter
	private TaskBook book;

	private SlayerAltsPanel panel;
	private NavigationButton navButton;
	private String lastTask;

	@Provides
	SlayerAltsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerAltsConfig.class);
	}

	@Override
	protected void startUp() throws IOException
	{
		book = TaskData.load(gson);
		log.debug("loaded {} slayer tasks", book.all().size());

		panel = injector.getInstance(SlayerAltsPanel.class);
		panel.init(book);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Slayer Alternatives")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
		lastTask = null;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		String task = currentTask();
		if (task == null ? lastTask == null : task.equals(lastTask))
		{
			return;
		}

		lastTask = task;
		SlayerTask match = task == null ? null : book.find(task);
		SwingUtilities.invokeLater(() -> panel.setCurrentTask(task, match));
	}

	/** The task core's slayer plugin last recorded, or null. */
	private String currentTask()
	{
		String task = configManager.getRSProfileConfiguration(SLAYER_GROUP, TASK_NAME_KEY);
		return task == null || task.trim().isEmpty() ? null : task.trim();
	}
}
