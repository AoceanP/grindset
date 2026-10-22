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
 * Time for gold per hour, two ways:
 * <ul>
 * <li>the trip: everything from the first kill on, banking and walking included. While the
 * trip is live the clock keeps running after the last kill or drop, so the rate falls while
 * you're away and climbs again with new loot.</li>
 * <li>fighting: only the time spent fighting, with gaps over {@link #FIGHT_GAP_MILLIS} left out.</li>
 * </ul>
 * A trip ends when the player moves on to another method, logs out, or takes a break longer
 * than {@link #TRIP_BREAK_MILLIS}.
 *
 * Not thread-safe; owners synchronize.
 */
final class TripTimer
{
	/** A gap longer than this ends the trip, so an AFK break doesn't sink the rate. */
	static final long TRIP_BREAK_MILLIS = 20 * 60_000L;
	/** Gaps up to this between kills, hits and drops count as fighting. */
	static final long FIGHT_GAP_MILLIS = 30_000L;

	private long tripMillis;
	private long fightMillis;
	// The last kill, hit or drop of the current trip, or -1 when no trip is going.
	private long lastMillis = -1;

	void mark(long nowMillis)
	{
		if (lastMillis >= 0)
		{
			long gap = nowMillis - lastMillis;
			if (gap > 0 && gap <= TRIP_BREAK_MILLIS)
			{
				tripMillis += gap;
			}
			if (gap > 0 && gap <= FIGHT_GAP_MILLIS)
			{
				fightMillis += gap;
			}
		}
		lastMillis = Math.max(lastMillis, nowMillis);
	}

	/**
	 * Ends the trip: time away from now on isn't counted.
	 */
	void end()
	{
		lastMillis = -1;
	}

	/**
	 * @param live whether the clock is still running since the last kill or drop
	 * @return the trip's time, up to now when live
	 */
	long getTripMillis(long nowMillis, boolean live)
	{
		long gap = nowMillis - lastMillis;
		return live && lastMillis >= 0 && gap > 0 && gap <= TRIP_BREAK_MILLIS ? tripMillis + gap : tripMillis;
	}

	long getFightMillis()
	{
		return fightMillis;
	}

	/**
	 * @return whether the trip is live and the player stopped fighting, so its rate is falling
	 */
	boolean isAway(long nowMillis, boolean live)
	{
		long gap = nowMillis - lastMillis;
		return live && lastMillis >= 0 && gap > FIGHT_GAP_MILLIS && gap <= TRIP_BREAK_MILLIS;
	}

	void restore(long tripMillis, long fightMillis)
	{
		this.tripMillis = Math.max(0, tripMillis);
		this.fightMillis = Math.max(0, fightMillis);
		lastMillis = -1;
	}

	void add(TripTimer other)
	{
		tripMillis += other.tripMillis;
		fightMillis += other.fightMillis;
	}

	void reset()
	{
		tripMillis = 0;
		fightMillis = 0;
		lastMillis = -1;
	}
}
