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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Everything tracked for each method: XP (in its main skill and any others it trains), what it
 * made and what it used up, actions, time spent, and games for minigames. Rates are per hour
 * of activity with breaks over 5 minutes left out; the trip rate counts banking and walking too.
 *
 * Thread-safe: the client thread records, the panel reads snapshots.
 */
final class MethodStats
{
	/** A session needs this much activity before it can be a method's best. */
	static final long BEST_MIN_MILLIS = 10 * 60_000L;
	/** Dropping at least this share of what's gathered means the method is power-training. */
	static final double DROPPING_SHARE = 0.8;

	static final class Line
	{
		final int itemId;
		final String name;
		long quantity;
		long unitPrice;
		long haPrice;
		final boolean reward;

		Line(int itemId, String name, long quantity, long unitPrice, long haPrice, boolean reward)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.haPrice = haPrice;
			this.reward = reward;
		}

		long price(boolean highAlchemy)
		{
			return highAlchemy ? haPrice : unitPrice;
		}
	}

	private static final class Entry
	{
		final String name;
		String skill;
		final Map<String, Long> xp = new LinkedHashMap<>();
		final Map<Integer, Line> made = new LinkedHashMap<>();
		final Map<Integer, Line> used = new LinkedHashMap<>();
		final Map<Integer, Long> dropped = new HashMap<>();
		long actions;
		final ActivityTimer activity = new ActivityTimer();
		final TripTimer trip = new TripTimer();
		int games;
		double bestXpPerHour;
		// This session (since logging in): not saved.
		long sessionXp;
		final ActivityTimer session = new ActivityTimer();

		Entry(String name, String skill)
		{
			this.name = name;
			this.skill = skill;
		}

		long mainXp()
		{
			return xp.getOrDefault(skill, 0L);
		}
	}

	/**
	 * Plain data for saving with Gson.
	 */
	static final class Saved
	{
		String name;
		String skill;
		Map<String, Long> xp;
		List<SavedLine> made;
		List<SavedLine> used;
		Map<Integer, Long> dropped;
		long actions;
		long activeMillis;
		long lastMillis = -1;
		long tripMillis;
		long fightMillis;
		int games;
		double bestXpPerHour;
	}

	static final class SavedLine
	{
		int id;
		String name;
		long quantity;
		long unitPrice;
		long haPrice;
		boolean reward;
	}

	/**
	 * A read-only view of one method for the panel and overlay.
	 */
	static final class Snapshot
	{
		final String name;
		final String skill;
		final long xp;
		final double xpPerHour;
		/** Other skills this method trains, with their XP/hr. */
		final Map<String, Double> otherXpPerHour = new LinkedHashMap<>();
		final double gpPerHour;
		final double spotGpPerHour;
		final double madePerHour;
		final double usedPerHour;
		final double actionsPerHour;
		final double gamesPerHour;
		final int games;
		final long made;
		final long used;
		final long value;
		final boolean dropping;
		final boolean now;
		final boolean away;
		final double bestXpPerHour;
		final long lastMillis;
		/** The item that best shows the method, for its icon: what it makes most of, or uses most. */
		final int iconItemId;

		Snapshot(Entry e, boolean highAlchemy, boolean countDropped, boolean current, long nowMillis)
		{
			name = e.name;
			skill = e.skill;
			xp = e.mainXp();
			long active = e.activity.getActiveMillis();
			xpPerHour = ActivityTimer.perHour(xp, active);
			e.xp.forEach((s, n) ->
			{
				if (!s.equals(e.skill))
				{
					otherXpPerHour.put(s, ActivityTimer.perHour(n, active));
				}
			});
			long gathered = 0;
			long droppedTotal = 0;
			long v = 0;
			for (Line line : e.made.values())
			{
				long dropped = Math.min(line.quantity, e.dropped.getOrDefault(line.itemId, 0L));
				long kept = countDropped ? line.quantity : line.quantity - dropped;
				v += kept * line.price(highAlchemy);
				if (!line.reward)
				{
					gathered += line.quantity;
					droppedTotal += dropped;
				}
			}
			long usedTotal = 0;
			for (Line line : e.used.values())
			{
				v -= line.quantity * line.price(highAlchemy);
				usedTotal += line.quantity;
			}
			made = gathered;
			used = usedTotal;
			value = v;
			dropping = gathered > 0 && droppedTotal >= gathered * DROPPING_SHARE;
			away = e.trip.isAway(nowMillis, current);
			gpPerHour = ActivityTimer.perHour(v, e.trip.getTripMillis(nowMillis, current));
			spotGpPerHour = ActivityTimer.perHour(v, e.trip.getFightMillis());
			madePerHour = ActivityTimer.perHour(gathered, active);
			usedPerHour = ActivityTimer.perHour(usedTotal, active);
			actionsPerHour = ActivityTimer.perHour(e.actions, active);
			games = e.games;
			gamesPerHour = ActivityTimer.perHour(e.games, active);
			now = current && !away;
			bestXpPerHour = e.bestXpPerHour;
			lastMillis = e.activity.getLastMillis();
			int icon = mostOf(e.made);
			// Only coins made (an alch, a stall): the item used shows it better.
			if ((icon < 0 || isCoins(e.made.get(icon))) && !e.used.isEmpty())
			{
				icon = mostOf(e.used);
			}
			iconItemId = icon;
		}

		private static boolean isCoins(Line line)
		{
			return line != null && "Coins".equalsIgnoreCase(line.name);
		}

		private static int mostOf(Map<Integer, Line> lines)
		{
			Line best = null;
			for (Line line : lines.values())
			{
				// Coins only if there's nothing else.
				boolean coins = isCoins(line);
				if (best == null || (!coins && (isCoins(best) || line.quantity > best.quantity)))
				{
					best = line;
				}
			}
			return best == null ? -1 : best.itemId;
		}
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private String current;
	private int revision;
	private LongSupplier clock = System::currentTimeMillis;

	/**
	 * @param skill the skill the XP is in: the method's main skill, or another it also trains
	 */
	synchronized void recordXp(String method, String mainSkill, String skill, long xp, long nowMillis)
	{
		if (xp <= 0)
		{
			return;
		}
		Entry e = entry(method, mainSkill);
		e.xp.merge(skill, xp, Long::sum);
		if (skill.equals(e.skill))
		{
			e.sessionXp += xp;
			e.actions++;
		}
		mark(e, nowMillis);
	}

	/**
	 * Something the method made or gathered (or, as a reward, collected later).
	 */
	synchronized void recordMade(String method, String skill, int itemId, String itemName, long quantity,
		long unitPrice, long haPrice, boolean reward, long nowMillis)
	{
		if (quantity <= 0)
		{
			return;
		}
		Entry e = entry(method, skill);
		add(e.made, itemId, itemName, quantity, unitPrice, haPrice, reward);
		mark(e, nowMillis);
	}

	/**
	 * Something the method used up: logs burned, bones offered, ingredients, planks.
	 */
	synchronized void recordUsed(String method, String skill, int itemId, String itemName, long quantity,
		long unitPrice, long haPrice, long nowMillis)
	{
		if (quantity <= 0)
		{
			return;
		}
		Entry e = entry(method, skill);
		add(e.used, itemId, itemName, quantity, unitPrice, haPrice, false);
		mark(e, nowMillis);
	}

	private static void add(Map<Integer, Line> lines, int itemId, String itemName, long quantity, long unitPrice,
		long haPrice, boolean reward)
	{
		Line line = lines.get(itemId);
		if (line == null)
		{
			lines.put(itemId, new Line(itemId, itemName, quantity, unitPrice, haPrice, reward));
			return;
		}
		line.quantity += quantity;
		// The latest price, so the value follows the market.
		line.unitPrice = unitPrice;
		line.haPrice = haPrice;
	}

	/**
	 * Items of this method dropped on the floor (power-training).
	 */
	synchronized void recordDropped(String method, int itemId, long quantity)
	{
		Entry e = method == null ? null : entries.get(method);
		if (e == null || quantity <= 0 || !e.made.containsKey(itemId))
		{
			return;
		}
		e.dropped.merge(itemId, quantity, Long::sum);
		revision++;
	}

	synchronized void recordGame(String method, String skill, long nowMillis)
	{
		Entry e = entry(method, skill);
		e.games++;
		mark(e, nowMillis);
	}

	private Entry entry(String method, String skill)
	{
		Entry e = entries.computeIfAbsent(method, n -> new Entry(n, skill));
		if (e.skill == null)
		{
			e.skill = skill;
		}
		return e;
	}

	private void mark(Entry e, long nowMillis)
	{
		e.activity.mark(nowMillis);
		e.session.mark(nowMillis);
		if (current != null && !current.equals(e.name))
		{
			Entry last = entries.get(current);
			if (last != null)
			{
				last.trip.end();
			}
		}
		current = e.name;
		e.trip.mark(nowMillis);
		revision++;
	}

	/**
	 * @return whether the method has gathered this item, so dropping it counts as power-training
	 */
	synchronized boolean hasMade(String method, int itemId)
	{
		Entry e = method == null ? null : entries.get(method);
		return e != null && e.made.containsKey(itemId);
	}

	/**
	 * @return the method being done, or null
	 */
	synchronized String getCurrent()
	{
		return current;
	}

	/**
	 * @return the main skill of a method, or null
	 */
	synchronized String skillOf(String method)
	{
		Entry e = method == null ? null : entries.get(method);
		return e == null ? null : e.skill;
	}

	/**
	 * @return when the method was last done, or -1
	 */
	synchronized long lastMillis(String method)
	{
		Entry e = method == null ? null : entries.get(method);
		return e == null ? -1 : e.activity.getLastMillis();
	}

	/**
	 * Logged out: the current trip ends and this session's numbers start again next time.
	 */
	synchronized void endSession()
	{
		updateBest();
		Entry last = current == null ? null : entries.get(current);
		if (last != null)
		{
			last.trip.end();
		}
		current = null;
		for (Entry e : entries.values())
		{
			e.sessionXp = 0;
			e.session.reset();
		}
	}

	/**
	 * Keeps each method's best session XP/hr. Sessions shorter than 10 minutes don't count.
	 */
	synchronized void updateBest()
	{
		for (Entry e : entries.values())
		{
			if (e.session.getActiveMillis() >= BEST_MIN_MILLIS)
			{
				double rate = ActivityTimer.perHour(e.sessionXp, e.session.getActiveMillis());
				if (rate > e.bestXpPerHour)
				{
					e.bestXpPerHour = rate;
					revision++;
				}
			}
		}
	}

	synchronized List<Snapshot> snapshot(boolean highAlchemy, boolean countDropped)
	{
		long now = clock.getAsLong();
		List<Snapshot> out = new ArrayList<>(entries.size());
		for (Entry e : entries.values())
		{
			out.add(new Snapshot(e, highAlchemy, countDropped, e.name.equals(current), now));
		}
		// What you're doing now first, then most recent.
		out.sort((a, b) -> a.now != b.now ? (a.now ? -1 : 1) : Long.compare(b.lastMillis, a.lastMillis));
		return out;
	}

	synchronized void remove(String method)
	{
		if (entries.remove(method) != null)
		{
			revision++;
		}
		if (method.equals(current))
		{
			current = null;
		}
	}

	/**
	 * Clears a method's numbers but keeps it on the list.
	 */
	synchronized void reset(String method)
	{
		Entry e = entries.get(method);
		if (e != null)
		{
			entries.put(method, new Entry(method, e.skill));
			if (method.equals(current))
			{
				current = null;
			}
			revision++;
		}
	}

	synchronized boolean isEmpty()
	{
		return entries.isEmpty();
	}

	synchronized int getRevision()
	{
		return revision;
	}

	void setClock(LongSupplier clock)
	{
		this.clock = clock;
	}

	synchronized List<Saved> exportState()
	{
		List<Saved> out = new ArrayList<>(entries.size());
		for (Entry e : entries.values())
		{
			Saved s = new Saved();
			s.name = e.name;
			s.skill = e.skill;
			s.xp = new LinkedHashMap<>(e.xp);
			s.made = save(e.made);
			s.used = save(e.used);
			s.dropped = new HashMap<>(e.dropped);
			s.actions = e.actions;
			s.activeMillis = e.activity.getActiveMillis();
			s.lastMillis = e.activity.getLastMillis();
			s.tripMillis = e.trip.getTripMillis(0, false);
			s.fightMillis = e.trip.getFightMillis();
			s.games = e.games;
			s.bestXpPerHour = e.bestXpPerHour;
			out.add(s);
		}
		return out;
	}

	private static List<SavedLine> save(Map<Integer, Line> lines)
	{
		List<SavedLine> out = new ArrayList<>();
		for (Line line : lines.values())
		{
			SavedLine l = new SavedLine();
			l.id = line.itemId;
			l.name = line.name;
			l.quantity = line.quantity;
			l.unitPrice = line.unitPrice;
			l.haPrice = line.haPrice;
			l.reward = line.reward;
			out.add(l);
		}
		return out;
	}

	/**
	 * Replaces everything with saved data. Bad entries are skipped rather than failing the load.
	 */
	synchronized void importState(Collection<Saved> saved)
	{
		entries.clear();
		current = null;
		for (Saved s : saved == null ? Collections.<Saved>emptyList() : saved)
		{
			if (s == null || s.name == null || s.name.trim().isEmpty() || s.skill == null)
			{
				continue;
			}
			Entry e = new Entry(s.name, s.skill);
			if (s.xp != null)
			{
				s.xp.forEach((skill, n) ->
				{
					if (skill != null && n != null && n > 0)
					{
						e.xp.put(skill, n);
					}
				});
			}
			load(s.made, e.made);
			load(s.used, e.used);
			if (s.dropped != null)
			{
				s.dropped.forEach((id, n) ->
				{
					if (id != null && n != null && n > 0)
					{
						e.dropped.put(id, n);
					}
				});
			}
			e.actions = Math.max(0, s.actions);
			e.activity.restore(s.activeMillis, s.lastMillis);
			e.trip.restore(s.tripMillis, s.fightMillis);
			e.games = Math.max(0, s.games);
			e.bestXpPerHour = Math.max(0, s.bestXpPerHour);
			entries.put(e.name, e);
		}
		revision++;
	}

	private static void load(List<SavedLine> saved, Map<Integer, Line> into)
	{
		if (saved == null)
		{
			return;
		}
		for (SavedLine l : saved)
		{
			if (l != null && l.quantity > 0)
			{
				into.put(l.id, new Line(l.id, l.name == null ? "Unknown" : l.name, l.quantity, l.unitPrice, l.haPrice, l.reward));
			}
		}
	}
}
