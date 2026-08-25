/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class AccountTest
{
	private Account withQuests(String... finished)
	{
		Set<String> s = new HashSet<>();
		for (String q : finished)
		{
			s.add(q.toLowerCase());
		}
		return new Account(s, Collections.emptyMap(), 99);
	}

	@Before
	public void setUp()
	{
		// the plugin does this from RuneLite's Quest enum at startup
		Account.KnownQuests.set(new HashSet<>(Arrays.asList(
			"dragon slayer ii", "while guthix sleeps", "bone voyage", "priest in peril")));
	}

	@Test
	public void noGateIsAlwaysOpen()
	{
		assertFalse(withQuests().check("").isLocked());
		assertFalse(withQuests().check(null).isLocked());
	}

	@Test
	public void finishedQuestOpensTheGate()
	{
		assertFalse(withQuests("Dragon Slayer II").check("Dragon Slayer II").isLocked());
	}

	@Test
	public void unfinishedQuestLocksIt()
	{
		assertTrue(withQuests("Bone Voyage").check("Dragon Slayer II").isLocked());
	}

	@Test
	public void unknownAccountNeverLocksAnything()
	{
		// before login we know nothing. showing a monster greyed out because we haven't
		// read the account yet would hide something the player can actually kill.
		assertFalse(Account.UNKNOWN.check("Dragon Slayer II").isLocked());
		assertEquals(Access.State.UNKNOWN,
			Account.UNKNOWN.check("Dragon Slayer II").getState());
	}

	@Test
	public void consumableGatesAreNeverLocked()
	{
		// a dark totem is something you bring, not something you've unlocked. saying
		// LOCKED would be wrong every time you happen to have one.
		assertFalse(withQuests().check("dark totem").isLocked());
		assertFalse(withQuests().check("key / kc").isLocked());
	}

	@Test
	public void nonQuestGatesStayUnknownRatherThanLocked()
	{
		// "hard diary" and "grapple" aren't quests, and we don't read diaries or
		// inventories. unknown is honest; locked would be a guess.
		assertEquals(Access.State.UNKNOWN, withQuests().check("hard diary").getState());
		assertEquals(Access.State.UNKNOWN, withQuests().check("grapple").getState());
		assertFalse(withQuests().check("hard diary").isLocked());
	}

	@Test
	public void gateMatchIsCaseInsensitive()
	{
		assertFalse(withQuests("DRAGON SLAYER II").check("dragon slayer ii").isLocked());
	}
}
