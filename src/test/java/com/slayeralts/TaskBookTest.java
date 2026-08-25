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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Runs against the real bundled data, not a fixture. A fixture would only prove the
 * parser works on a file I wrote; this proves it works on the file that ships.
 */
public class TaskBookTest
{
	private TaskBook book;

	@Before
	public void setUp() throws Exception
	{
		book = TaskData.load(new Gson());
	}

	@Test
	public void loadsEveryTask()
	{
		assertEquals(116, book.all().size());
	}

	@Test
	public void everyTaskHasAName()
	{
		for (SlayerTask t : book.all())
		{
			assertNotNull(t.getTask());
			assertFalse(t.getTask().trim().isEmpty());
		}
	}

	@Test
	public void findsTaskFromTheClientsShoutyName()
	{
		// the client hands over "GREATER DEMONS"
		SlayerTask t = book.find("GREATER DEMONS");
		assertNotNull(t);
		assertEquals("Greater demons", t.getTask());
	}

	@Test
	public void findsTaskAcrossPluralDisagreement()
	{
		assertNotNull(book.find("Bloodveld"));
		assertNotNull(book.find("Bloodvelds"));
		assertNotNull(book.find("BLOODVELDS"));
	}

	@Test
	public void unknownTaskIsNull()
	{
		assertNull(book.find("Sea monsters"));
	}

	@Test
	public void greaterDemonsCarryTheRealAlternatives()
	{
		SlayerTask t = book.find("Greater demons");
		List<String> names = new java.util.ArrayList<>();
		for (Monster m : t.getMonsters())
		{
			names.add(m.getName());
		}
		assertTrue(names.contains("Tormented Demon"));
		assertTrue(names.contains("Skotizo"));
		assertTrue(names.contains("K'ril Tsutsaroth"));
		assertTrue(names.contains("Tstanon Karlak"));
	}

	@Test
	public void tormentedDemonIsTheBestGreaterDemonXp()
	{
		SlayerTask t = book.find("Greater demons");
		assertEquals(1065, t.bestXp(), 0.01);
		assertEquals("Tormented Demon", t.choosable().get(0).getName());
	}

	@Test
	public void superiorsNeverSetTheHeadlineNumber()
	{
		// Nechryarch is a superior at 3280 xp; the headline must be the greater nechryael
		SlayerTask t = book.find("Nechryael");
		assertEquals(210, t.bestXp(), 0.01);
		for (Monster m : t.choosable())
		{
			assertFalse(m.getName() + " is superior and shouldn't be choosable", m.isSuperior());
		}
	}

	@Test
	public void nechryaelXpCameFromTheInfoboxNotTheStaleTable()
	{
		// the article table said 128, the infobox says 105. we take the infobox.
		SlayerTask t = book.find("Nechryael");
		for (Monster m : t.getMonsters())
		{
			if (m.getName().equals("Nechryael"))
			{
				assertEquals(105, m.xp(), 0.01);
				return;
			}
		}
		throw new AssertionError("no Nechryael row");
	}

	@Test
	public void searchFindsATaskByItsBoss()
	{
		// "what task do I need for vorkath" is the question backwards
		List<SlayerTask> hits = book.search("vorkath");
		assertFalse(hits.isEmpty());
		assertEquals("Blue dragons", hits.get(0).getTask());
	}

	@Test
	public void searchPrefersTaskNameOverAMention()
	{
		List<SlayerTask> hits = book.search("greater demons");
		assertEquals("Greater demons", hits.get(0).getTask());
	}

	@Test
	public void emptySearchReturnsEverything()
	{
		assertEquals(book.all().size(), book.search("").size());
		assertEquals(book.all().size(), book.search("   ").size());
	}

	@Test
	public void searchMissIsEmptyNotEverything()
	{
		assertTrue(book.search("zzzznothing").isEmpty());
	}

	@Test
	public void choosableIsSortedBestFirst()
	{
		for (SlayerTask t : book.all())
		{
			List<Monster> c = t.choosable();
			for (int i = 1; i < c.size(); i++)
			{
				assertTrue(t.getTask() + " not sorted",
					c.get(i - 1).xp() >= c.get(i).xp());
			}
		}
	}

	@Test
	public void alternativeItemsAreSeparatedNotMashedTogether()
	{
		// several {{plink}}s on one wiki line are alternatives. expanding them straight
		// produced "Dragonfire ward Ancient wyvern shield Dragonfire shield" - three
		// items rendered as one nonsense string.
		SlayerTask t = book.find("Metal dragons");
		for (String item : t.getItems())
		{
			if (item.contains("Dragonfire ward"))
			{
				assertTrue("alternatives not separated: " + item, item.contains("/"));
				return;
			}
		}
		throw new AssertionError("no dragonfire ward row to check");
	}

	@Test
	public void noItemLineRunsAwayInLength()
	{
		// a single line longer than this can't wrap sensibly in a 225px panel, and is
		// usually a sign several items got joined into one.
		for (SlayerTask t : book.all())
		{
			for (String item : t.getItems())
			{
				assertTrue(t.getTask() + " item line too long (" + item.length() + "): " + item,
					item.length() <= 90);
			}
		}
	}

	@Test
	public void stackedCellsBecomeSeparateMonsters()
	{
		// the dagannoth page puts Prime, Rex and Supreme in ONE cell, one per line, with
		// their three xp values stacked the same way. flattening it gave a monster
		// called "Dagannoth Prime Dagannoth Rex Dagannoth Supreme" with no xp at all.
		SlayerTask t = book.find("Dagannoth");
		java.util.Set<String> names = new java.util.HashSet<>();
		for (Monster m : t.getMonsters())
		{
			names.add(m.getName());
		}
		assertTrue("Prime missing", names.contains("Dagannoth Prime"));
		assertTrue("Rex missing", names.contains("Dagannoth Rex"));
		assertTrue("Supreme missing", names.contains("Dagannoth Supreme"));

		for (Monster m : t.getMonsters())
		{
			if (m.getName().equals("Dagannoth Supreme"))
			{
				assertEquals(255, m.xp(), 0.5);
				return;
			}
		}
		throw new AssertionError("no supreme row");
	}

	@Test
	public void noMonsterNameIsSeveralMonstersJoined()
	{
		// a repeated word in a name is the tell that a stacked cell got flattened
		for (SlayerTask t : book.all())
		{
			for (Monster m : t.getMonsters())
			{
				String[] words = m.getName().split("\\s+");
				if (words.length <= 2)
				{
					continue;
				}
				java.util.Set<String> seen = new java.util.HashSet<>();
				for (String w : words)
				{
					// brackets are legitimate qualifiers, e.g. "(Nightmare Zone)"
					if (w.startsWith("(") || w.contains("/"))
					{
						continue;
					}
					assertTrue(t.getTask() + ": '" + m.getName() + "' repeats '" + w
						+ "' - looks like several monsters joined", seen.add(w));
				}
			}
		}
	}

	@Test
	public void noProseLeakedIntoTheNumberColumns()
	{
		// widening the stacked-cell split once invented rows with xp="Escape Caves" and
		// combat="|" by reading lines out of the neighbouring prose cell.
		for (SlayerTask t : book.all())
		{
			for (Monster m : t.getMonsters())
			{
				String xp = m.getSlayerXp() == null ? "" : m.getSlayerXp();
				String cb = m.getCombat() == null ? "" : m.getCombat();
				assertFalse(t.getTask() + " / " + m.getName() + " has prose in xp: " + xp,
					xp.matches(".*[A-Za-z]{3}.*"));
				assertFalse(t.getTask() + " / " + m.getName() + " has prose in combat: " + cb,
					cb.matches(".*[A-Za-z]{4}.*"));
			}
		}
	}

	@Test
	public void noDuplicateStatblocks()
	{
		for (SlayerTask t : book.all())
		{
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (Monster m : t.getMonsters())
			{
				String key = m.getName() + "|" + m.getCombat() + "|" + m.getSlayerXp();
				assertTrue(t.getTask() + " lists " + key + " twice", seen.add(key));
			}
		}
	}

	@Test
	public void mostRowsAreCrossCheckedAgainstTheInfoboxData()
	{
		int total = 0;
		int verified = 0;
		for (SlayerTask t : book.all())
		{
			for (Monster m : t.getMonsters())
			{
				total++;
				if (m.isVerified())
				{
					verified++;
				}
			}
		}
		assertEquals(448, total);
		// 434/448 at the time of writing. a drop means the merge stopped matching.
		assertTrue("only " + verified + "/" + total + " verified", verified >= 420);
	}
}
