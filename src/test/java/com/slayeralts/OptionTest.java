/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import com.google.gson.Gson;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class OptionTest
{
	private TaskBook book;

	@Before
	public void setUp() throws Exception
	{
		book = TaskData.load(new Gson());
	}

	private List<Option> options(String task)
	{
		SlayerTask t = book.find(task);
		assertNotNull("no such task: " + task, t);
		return Option.from(t, true);
	}

	private Option find(String task, String monster)
	{
		for (Option o : options(task))
		{
			if (o.getName().equalsIgnoreCase(monster))
			{
				return o;
			}
		}
		throw new AssertionError(monster + " missing from " + task);
	}

	@Test
	public void fiveGreaterDemonRowsBecomeOne()
	{
		// the wiki lists Greater Demon once per statblock - cb 92/100/101/104/113
		SlayerTask t = book.find("Greater demons");
		int raw = 0;
		for (Monster m : t.getMonsters())
		{
			if (m.getName().equalsIgnoreCase("Greater Demon"))
			{
				raw++;
			}
		}
		assertEquals(5, raw);

		int collapsed = 0;
		for (Option o : options("Greater demons"))
		{
			if (o.getName().equalsIgnoreCase("Greater Demon"))
			{
				collapsed++;
			}
		}
		assertEquals(1, collapsed);
	}

	@Test
	public void collapsedRowKeepsTheWholeRange()
	{
		Option gd = find("Greater demons", "Greater Demon");
		assertEquals("87-130", gd.getXp());
		assertEquals("92-113", gd.getCombat());
	}

	@Test
	public void singleStatblockShowsOneNumberNotARange()
	{
		Option td = find("Greater demons", "Tormented Demon");
		assertEquals("1065", td.getXp());
		assertEquals("450", td.getCombat());
	}

	@Test
	public void collapsedRowMergesLocations()
	{
		Option gd = find("Greater demons", "Greater Demon");
		assertFalse(gd.getLocation().isEmpty());
		// 9 standard + catacombs + wilderness cave, deduped
		assertTrue("expected several locations", gd.getExtraLocations() > 3);
	}

	@Test
	public void questGateNamesTheQuest()
	{
		assertEquals("While Guthix Sleeps", find("Greater demons", "Tormented Demon").getGate());
	}

	@Test
	public void questGateSurvivesTheInOrderPhrasing()
	{
		// "Requires completion of Dragon Slayer II in order to access" used to cut at
		// " to " only, leaving "Dragon Slayer II in order" - too long, so it fell back
		// to a bare "quest" that told you nothing.
		assertEquals("Dragon Slayer II", find("Metal dragons", "Rune dragon").getGate());
		assertEquals("Dragon Slayer II", find("Metal dragons", "Adamant dragon").getGate());
	}

	@Test
	public void gateNamesAQuestRatherThanSayingQuest()
	{
		// a bare "quest" is a parse failure, not an answer. a couple are genuinely
		// unparseable, but it should be rare.
		int bare = 0;
		int named = 0;
		for (SlayerTask t : book.all())
		{
			for (Option o : Option.from(t, true))
			{
				if (o.getGate().equals("quest"))
				{
					bare++;
				}
				else if (!o.getGate().isEmpty())
				{
					named++;
				}
			}
		}
		assertTrue("too many unparsed quest gates: " + bare + " bare vs " + named + " named",
			bare <= 5);
	}

	@Test
	public void totemGateIsShort()
	{
		assertEquals("dark totem", find("Greater demons", "Skotizo").getGate());
	}

	@Test
	public void godwarsGateMentionsTheKey()
	{
		assertEquals("key / kc", find("Greater demons", "K'ril Tsutsaroth").getGate());
	}

	@Test
	public void plainMonsterHasNoGate()
	{
		assertEquals("", find("Greater demons", "Greater Demon").getGate());
	}

	@Test
	public void everyGateFitsOnARow()
	{
		for (SlayerTask t : book.all())
		{
			for (Option o : Option.from(t, true))
			{
				assertTrue(t.getTask() + " / " + o.getName() + " gate too long: " + o.getGate(),
					o.getGate().length() <= 24);
			}
		}
	}

	@Test
	public void bestFirstAndSuperiorsLast()
	{
		for (SlayerTask t : book.all())
		{
			List<Option> opts = Option.from(t, true);
			boolean seenSuperior = false;
			double prev = Double.MAX_VALUE;
			for (Option o : opts)
			{
				if (o.isSuperior())
				{
					seenSuperior = true;
					continue;
				}
				assertFalse(t.getTask() + ": a normal option came after a superior", seenSuperior);
				assertTrue(t.getTask() + " not sorted", o.getBestXp() <= prev);
				prev = o.getBestXp();
			}
		}
	}

	@Test
	public void tormentedDemonLeadsGreaterDemons()
	{
		assertEquals("Tormented Demon", options("Greater demons").get(0).getName());
	}

	@Test
	public void collapsingNeverLosesAMonster()
	{
		for (SlayerTask t : book.all())
		{
			// tasks with no variants table synthesize their options from the master
			// table's names instead, so there's nothing to compare against here
			if (t.getMonsters().isEmpty())
			{
				continue;
			}

			java.util.Set<String> raw = new java.util.HashSet<>();
			for (Monster m : t.getMonsters())
			{
				raw.add(m.getName().toLowerCase());
			}
			java.util.Set<String> got = new java.util.HashSet<>();
			for (Option o : Option.from(t, true))
			{
				got.add(o.getName().toLowerCase());
			}
			assertEquals(t.getTask() + " lost a monster", raw, got);
		}
	}

	@Test
	public void tasksWithNoVariantsTableStillOfferSomething()
	{
		// 13 tasks have no variants table but DO have alternatives named in the master
		// assignment table. showing nothing there wasted data we already had.
		SlayerTask cows = book.find("Cows");
		assertTrue("Cows has no variants table", cows.getMonsters().isEmpty());

		java.util.Set<String> names = new java.util.HashSet<>();
		for (Option o : Option.from(cows, true))
		{
			names.add(o.getName());
		}
		assertTrue("expected the master table's alternatives, got " + names,
			names.contains("Cow calf"));
	}

	@Test
	public void superiorPrefixIsStrippedFromNameOnlyOptions()
	{
		// the master table writes "Superior slayer monster Chasm Crawler"
		for (Option o : Option.from(book.find("Cave crawlers"), true))
		{
			assertFalse("prefix left on: " + o.getName(),
				o.getName().toLowerCase().startsWith("superior slayer"));
			if (o.getName().equals("Chasm Crawler"))
			{
				assertTrue("should be flagged superior", o.isSuperior());
				return;
			}
		}
		throw new AssertionError("Chasm Crawler missing");
	}
}
