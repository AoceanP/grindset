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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which method a tick's XP belongs to, from what changed: items gained, items used
 * up, what was clicked and where the player is. Kept apart from the game so it can be tested.
 *
 * <ul>
 * <li>Gathering and making (Fishing, Mining, Cooking, Fletching...): named by what you get,
 * grouped into families from methods.json ("Barbarian fishing").</li>
 * <li>Using up (Firemaking, Prayer, Construction): named by what you use: logs, bones, planks.</li>
 * <li>Actions (Thieving, Agility, non-combat Magic): named by what you clicked, or the agility
 * course you're on.</li>
 * </ul>
 * Other skills trained in the same tick (barbarian fishing's Agility and Strength) count
 * towards that method. Combat on its own is left to combat trackers.
 */
final class MethodDetector
{
	enum Kind
	{
		GATHER, USE, ACTION
	}

	static final Map<String, Kind> KINDS = new HashMap<>();
	/** Trained by fighting: only counted alongside a skilling method (barbarian fishing's Strength). */
	static final Set<String> COMBAT = new HashSet<>(Arrays.asList("Attack", "Strength", "Defence", "Hitpoints",
		"Ranged", "Slayer"));
	/** Skills are processed in this order, so making something outranks the XP that comes with it. */
	static final List<String> ORDER = Arrays.asList("Fishing", "Woodcutting", "Mining", "Hunter", "Farming",
		"Runecraft", "Cooking", "Smithing", "Fletching", "Crafting", "Herblore", "Firemaking", "Prayer",
		"Construction", "Thieving", "Agility", "Magic", "Sailing");
	/** Items and XP up to this many ticks apart belong together. */
	static final int PAIR_TICKS = 1;
	/** A skill's XP with nothing else to go on keeps going to its last method for this long. */
	static final int STICKY_TICKS = 10;
	private static final String WEAK = "weak";
	/** Rewards collected without XP (a Motherlode sack, a GotR search) count this long after the method. */
	static final long REWARD_MILLIS = 3 * 60_000L;

	static
	{
		for (String s : Arrays.asList("Fishing", "Woodcutting", "Mining", "Hunter", "Farming", "Runecraft", "Cooking",
			"Smithing", "Fletching", "Crafting", "Herblore", "Sailing"))
		{
			KINDS.put(s, Kind.GATHER);
		}
		for (String s : Arrays.asList("Firemaking", "Prayer", "Construction"))
		{
			KINDS.put(s, Kind.USE);
		}
		for (String s : Arrays.asList("Thieving", "Agility", "Magic"))
		{
			KINDS.put(s, Kind.ACTION);
		}
	}

	/**
	 * An item stack and its prices.
	 */
	static final class Item
	{
		final int id;
		final String name;
		final long quantity;
		final long unitPrice;
		final long haPrice;

		Item(int id, String name, long quantity, long unitPrice, long haPrice)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.haPrice = haPrice;
		}
	}

	/**
	 * What the player was doing this tick, apart from XP and items.
	 */
	static final class Context
	{
		String menuOption;
		String menuTarget;
		int menuTick = -100;
		int regionId = -1;
		boolean inGotr;
		boolean fighting;
		/** The method being done, and when it was last done. */
		String currentMethod;
		long currentLastMillis = -1;
	}

	/**
	 * One method's share of a tick.
	 */
	static final class Result
	{
		final String method;
		final String mainSkill;
		final Map<String, Long> xp = new LinkedHashMap<>();
		final List<Item> made = new ArrayList<>();
		final List<Item> used = new ArrayList<>();
		final List<Item> rewards = new ArrayList<>();

		Result(String method, String mainSkill)
		{
			this.method = method;
			this.mainSkill = mainSkill;
		}
	}

	private static final class Pending
	{
		final int tick;
		final Item item;
		final boolean gained;

		Pending(int tick, Item item, boolean gained)
		{
			this.tick = tick;
			this.item = item;
			this.gained = gained;
		}
	}

	private final MethodBook book;
	private final Map<Integer, String> courses;
	private final List<Pending> pending = new ArrayList<>();
	private final Map<String, String[]> lastMethod = new HashMap<>();
	private final Map<String, Integer> lastMethodTick = new HashMap<>();
	// XP with nothing to name it by yet, waiting a tick for its items.
	private Map<String, Long> heldXp = new HashMap<>();

	MethodDetector(MethodBook book, Map<Integer, String> courses)
	{
		this.book = book;
		this.courses = courses == null ? Collections.emptyMap() : courses;
	}

	/**
	 * Items that appeared in or left the inventory this tick (not while banking).
	 */
	void onItems(int tick, List<Item> gained, List<Item> lost)
	{
		for (Item i : gained)
		{
			pending.add(new Pending(tick, i, true));
		}
		for (Item i : lost)
		{
			pending.add(new Pending(tick, i, false));
		}
	}

	/**
	 * @param xp this tick's XP drops by skill name
	 * @return what each method gets from this tick
	 */
	List<Result> onTick(int tick, Map<String, Long> tickXp, Context ctx, long nowMillis)
	{
		Map<String, Long> xp = new HashMap<>(tickXp);
		Map<String, Long> held = heldXp;
		heldXp = new HashMap<>();
		held.forEach((skill, gain) -> xp.merge(skill, gain, Long::sum));

		List<Pending> near = new ArrayList<>();
		for (Pending p : pending)
		{
			if (Math.abs(p.tick - tick) <= PAIR_TICKS)
			{
				near.add(p);
			}
		}

		Map<String, Result> results = new LinkedHashMap<>();
		Result primary = null;
		Set<Pending> claimed = new HashSet<>();
		for (String skill : ORDER)
		{
			Long gain = xp.get(skill);
			if (gain == null || gain <= 0 || ("Magic".equals(skill) && ctx.fighting))
			{
				continue;
			}
			String[] method = name(skill, near, claimed, ctx, tick);
			if (method == null)
			{
				continue;
			}
			// No evidence of its own this tick (barbarian fishing's Agility): it goes with the method found.
			if (primary != null && method.length > 2)
			{
				primary.xp.merge(skill, gain, Long::sum);
				continue;
			}
			// Only the skill's name to go on: wait a tick in case its items come next.
			if (method.length > 2 && method[0].equals(skill) && !held.containsKey(skill))
			{
				heldXp.put(skill, gain);
				continue;
			}
			Result r = results.computeIfAbsent(method[0], m -> new Result(m, method[1]));
			r.xp.merge(skill, gain, Long::sum);
			if (primary == null)
			{
				primary = r;
			}
			// What this skill gained or used goes to its method.
			for (Pending p : near)
			{
				if (!claimed.contains(p) && belongs(skill, method[0], p, ctx))
				{
					claimed.add(p);
					(p.gained ? r.made : r.used).add(p.item);
				}
			}
			lastMethod.put(skill, new String[]{method[0], method[1]});
			lastMethodTick.put(skill, tick);
		}

		// Combat XP alongside a skilling method (barbarian fishing's Strength) counts towards it.
		if (primary != null)
		{
			for (String skill : COMBAT)
			{
				Long gain = xp.get(skill);
				if (gain != null && gain > 0)
				{
					primary.xp.merge(skill, gain, Long::sum);
				}
			}
		}

		// Rewards collected without XP, shortly after their method.
		MethodBook.Def current = book.byName(ctx.currentMethod);
		boolean recent = current != null && ctx.currentLastMillis >= 0 && nowMillis - ctx.currentLastMillis <= REWARD_MILLIS;
		if (recent && xp.isEmpty())
		{
			for (Pending p : near)
			{
				if (p.gained && !claimed.contains(p) && p.tick < tick && book.isReward(current, p.item.name))
				{
					claimed.add(p);
					results.computeIfAbsent(current.name, m -> new Result(m, current.skills.get(0))).rewards.add(p.item);
				}
			}
		}

		// Claimed items are done with; unclaimed ones wait a tick in case their XP comes late.
		pending.removeIf(p -> claimed.contains(p) || p.tick < tick - PAIR_TICKS);
		return new ArrayList<>(results.values());
	}

	/**
	 * @return {method name, main skill} for a skill's XP this tick, or null to leave it
	 */
	private String[] name(String skill, List<Pending> near, Set<Pending> claimed, Context ctx, int tick)
	{
		Kind kind = KINDS.get(skill);
		if (kind == null)
		{
			return null;
		}
		// A known family first: "Barbarian fishing", "Guardians of the Rift".
		for (Pending p : near)
		{
			if (p.gained && !claimed.contains(p))
			{
				MethodBook.Def def = book.forItem(p.item.name, ctx.inGotr);
				if (def != null && def.hasSkill(skill))
				{
					return new String[]{def.name, def.skills.get(0)};
				}
			}
		}
		if ("Agility".equals(skill))
		{
			String course = courses.get(ctx.regionId);
			if (course != null)
			{
				return new String[]{course, skill};
			}
		}
		String byItem = null;
		if (kind != Kind.ACTION)
		{
			byItem = firstItem(near, claimed, kind == Kind.GATHER);
			if (byItem == null)
			{
				byItem = firstItem(near, claimed, kind != Kind.GATHER);
			}
		}
		if (byItem != null)
		{
			return new String[]{byItem, skill};
		}
		// Weaker evidence from here on, marked with a third element.
		String byClick = clicked(skill, ctx, tick);
		if (byClick != null)
		{
			return new String[]{byClick, skill, WEAK};
		}
		// Nothing to go on (a log basket filling up): its last method, if recent.
		Integer last = lastMethodTick.get(skill);
		if (last != null && tick - last <= STICKY_TICKS)
		{
			String[] previous = lastMethod.get(skill);
			return new String[]{previous[0], previous[1], WEAK};
		}
		return new String[]{skill, skill, WEAK};
	}

	private static String firstItem(List<Pending> near, Set<Pending> claimed, boolean gained)
	{
		for (Pending p : near)
		{
			if (p.gained == gained && !claimed.contains(p) && !"Coins".equalsIgnoreCase(p.item.name))
			{
				return p.item.name;
			}
		}
		return null;
	}

	/**
	 * Thieving, Agility off a course and Magic are named by what was clicked:
	 * "Pickpocket Master Farmer", "High Level Alchemy".
	 */
	private static String clicked(String skill, Context ctx, int tick)
	{
		if (ctx.menuOption == null || tick - ctx.menuTick > STICKY_TICKS)
		{
			return null;
		}
		String target = clean(ctx.menuTarget);
		if ("Magic".equals(skill))
		{
			// "High Level Alchemy -> Rune platebody": the spell.
			int arrow = target.indexOf("->");
			String spell = (arrow >= 0 ? target.substring(0, arrow) : target).trim();
			return spell.isEmpty() ? null : spell;
		}
		if ("Thieving".equals(skill))
		{
			return target.isEmpty() ? null : clean(ctx.menuOption) + " " + target;
		}
		return target.isEmpty() ? null : target;
	}

	/**
	 * Whether an item gained or used this tick belongs to the skill's method.
	 */
	private boolean belongs(String skill, String method, Pending p, Context ctx)
	{
		// A known family only claims its own items; anything else claims what changed with it
		// (thieving loot, an alch's coins and the item alched, bait, ingredients).
		MethodBook.Def def = book.byName(method);
		if (def != null && p.gained)
		{
			return def.equals(book.forItem(p.item.name, ctx.inGotr)) || book.isReward(def, p.item.name);
		}
		return true;
	}

	/**
	 * @return menu text without colour tags and levels: "Master Farmer"
	 */
	static String clean(String text)
	{
		if (text == null)
		{
			return "";
		}
		String s = text.replaceAll("<[^>]*>", "").replaceAll("\\s*\\(level-\\d+\\)", "").trim();
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}
}
