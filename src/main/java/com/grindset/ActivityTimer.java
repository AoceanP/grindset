/*
 * Copyright (c) 2026, AoceanP <https://github.com/AoceanP>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.grindset;

/**
 * Measures how long something was actually being done, ignoring breaks.
 *
 * Each {@link #mark} adds the time since the previous mark, unless that gap is longer
 * than the break threshold, in which case the player was away and the gap isn't
 * counted. This is what per-hour rates are divided by, so a lunch break in the middle
 * of a slayer task doesn't halve the kills/hr.
 *
 * Not thread-safe; owners synchronize.
 */
final class ActivityTimer
{
	/** Gaps longer than this are treated as a break. */
	static final long BREAK_MILLIS = 5 * 60_000L;
	/** Rates aren't shown until this much activity, so they don't start wildly high. */
	static final long MIN_RATE_MILLIS = 60_000L;

	private long activeMillis;
	private long lastMillis = -1;

	void mark(long nowMillis)
	{
		if (lastMillis >= 0)
		{
			long gap = nowMillis - lastMillis;
			if (gap > 0 && gap <= BREAK_MILLIS)
			{
				activeMillis += gap;
			}
		}
		lastMillis = Math.max(lastMillis, nowMillis);
	}

	long getActiveMillis()
	{
		return activeMillis;
	}

	long getLastMillis()
	{
		return lastMillis;
	}

	void restore(long activeMillis, long lastMillis)
	{
		this.activeMillis = Math.max(0, activeMillis);
		this.lastMillis = lastMillis;
	}

	void reset()
	{
		activeMillis = 0;
		lastMillis = -1;
	}

	/**
	 * @return amount per hour of activity, or -1 with less than a minute of activity
	 */
	static double perHour(long amount, long activeMillis)
	{
		if (activeMillis < MIN_RATE_MILLIS)
		{
			return -1;
		}
		return amount * 3_600_000.0 / activeMillis;
	}
}
