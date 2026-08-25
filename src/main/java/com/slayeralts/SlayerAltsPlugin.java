/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
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

	@Inject
	private ClientThread clientThread;

	@Getter
	private TaskBook book;

	private SlayerAltsPanel panel;
	private NavigationButton navButton;
	private String lastTask;

	/** Refreshed on login and every few minutes - quests don't change often. */
	private Account account = Account.UNKNOWN;
	private int ticksUntilRefresh;

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

		// the gate strings are wiki prose ("Dragon Slayer II"); this is how a pure
		// class can tell a quest gate from a consumable one without importing Quest.
		Set<String> questNames = new HashSet<>();
		for (Quest q : Quest.values())
		{
			questNames.add(q.getName().toLowerCase(Locale.ENGLISH));
		}
		Account.KnownQuests.set(questNames);

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
		if (--ticksUntilRefresh <= 0)
		{
			// 100 ticks is a minute. quests don't change often, and QUEST_STATUS_GET
			// is a script call per quest - not something to run every tick.
			ticksUntilRefresh = 100;
			refreshAccount();
		}

		String task = currentTask();
		if (task == null ? lastTask == null : task.equals(lastTask))
		{
			return;
		}

		lastTask = task;
		SlayerTask match = task == null ? null : book.find(task);
		SwingUtilities.invokeLater(() -> panel.setCurrentTask(task, match));
	}

	/**
	 * Read what this account can reach.
	 *
	 * Quest.getState runs a client script, so this has to happen on the client thread -
	 * calling it from swing throws. The panel gets a finished snapshot instead.
	 */
	private void refreshAccount()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Set<String> finished = new HashSet<>();
		for (Quest q : Quest.values())
		{
			try
			{
				if (q.getState(client) == QuestState.FINISHED)
				{
					finished.add(q.getName().toLowerCase(Locale.ENGLISH));
				}
			}
			catch (RuntimeException e)
			{
				// a quest the client can't resolve shouldn't kill the whole sweep
				log.debug("couldn't read quest {}", q, e);
			}
		}

		Map<String, Integer> levels = new HashMap<>();
		for (Skill s : Skill.values())
		{
			levels.put(s.getName().toLowerCase(Locale.ENGLISH), client.getRealSkillLevel(s));
		}

		Account fresh = new Account(finished, levels, client.getRealSkillLevel(Skill.SLAYER));
		account = fresh;
		SwingUtilities.invokeLater(() -> panel.setAccount(fresh));
	}

	/** The task core's slayer plugin last recorded, or null. */
	private String currentTask()
	{
		String task = configManager.getRSProfileConfiguration(SLAYER_GROUP, TASK_NAME_KEY);
		return task == null || task.trim().isEmpty() ? null : task.trim();
	}
}
