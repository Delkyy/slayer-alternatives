/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import lombok.Value;

/**
 * Whether you can actually go and kill a thing right now.
 *
 * Plain data with no RuneLite imports - the plugin reads the client and hands the
 * answers in, so the panel and the tests both work without a game.
 */
@Value
public class Access
{
	public enum State
	{
		/** Nothing stands in the way. */
		OPEN,
		/** Gated on something you haven't got. */
		LOCKED,
		/** Gated, and we can't tell whether you have it. */
		UNKNOWN
	}

	State state;

	/** What's missing, short enough for a row. Empty when nothing is. */
	String missing;

	static final Access OPEN = new Access(State.OPEN, "");

	public boolean isLocked()
	{
		return state == State.LOCKED;
	}

	static Access locked(String what)
	{
		return new Access(State.LOCKED, what);
	}

	static Access unknown(String what)
	{
		return new Access(State.UNKNOWN, what);
	}
}
