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
}
