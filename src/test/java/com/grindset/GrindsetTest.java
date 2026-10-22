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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class GrindsetTest
{
	private static final Gson GSON = new Gson();
	private MethodBook book;
	private MethodDetector detector;
	private MethodDetector.Context ctx;
	private int tick;

	@Before
	public void setUp()
	{
		book = new MethodBook(GSON.fromJson(new InputStreamReader(GrindsetPlugin.class.getResourceAsStream("methods.json"),
			StandardCharsets.UTF_8), new TypeToken<List<MethodBook.Def>>()
		{
		}.getType()));
		Map<String, String> courses = GSON.fromJson(new InputStreamReader(GrindsetPlugin.class.getResourceAsStream("courses.json"),
			StandardCharsets.UTF_8), new TypeToken<Map<String, String>>()
		{
		}.getType());
		Map<Integer, String> byRegion = new HashMap<>();
		courses.forEach((k, v) -> byRegion.put(Integer.parseInt(k), v));
		detector = new MethodDetector(book, byRegion);
		ctx = new MethodDetector.Context();
		tick = 100;
	}

	private static MethodDetector.Item item(String name, long quantity, long price)
	{
		return new MethodDetector.Item(Math.abs(name.hashCode()), name, quantity, price, price / 2);
	}

	private static Map<String, Long> xp(Object... skillsAndXp)
	{
		Map<String, Long> out = new HashMap<>();
		for (int i = 0; i < skillsAndXp.length; i += 2)
		{
			out.put((String) skillsAndXp[i], ((Number) skillsAndXp[i + 1]).longValue());
		}
		return out;
	}

	private List<MethodDetector.Result> tick(Map<String, Long> xp, List<MethodDetector.Item> gained, List<MethodDetector.Item> lost)
	{
		tick++;
		detector.onItems(tick, gained, lost);
		return detector.onTick(tick, xp, ctx, tick * 600L);
	}

	private static MethodDetector.Result only(List<MethodDetector.Result> results)
	{
		assertEquals(1, results.size());
		return results.get(0);
	}

	// ---- gathering and making ----

	@Test
	public void fishingIsNamedByWhatYouCatch()
	{
		MethodDetector.Result r = only(tick(xp("Fishing", 50), Collections.singletonList(item("Raw karambwan", 1, 1_900)),
			Collections.emptyList()));
		assertEquals("Karambwans", r.method);
		assertEquals("Fishing", r.mainSkill);
		assertEquals(1, r.made.size());
	}

	@Test
	public void barbarianFishingCountsItsAgilityAndStrength()
	{
		MethodDetector.Result r = only(tick(xp("Fishing", 70, "Agility", 7, "Strength", 7),
			Collections.singletonList(item("Leaping sturgeon", 1, 60)), Collections.singletonList(item("Feather", 1, 3))));
		assertEquals("Barbarian fishing", r.method);
		assertEquals(Long.valueOf(7), r.xp.get("Agility"));
		assertEquals(Long.valueOf(7), r.xp.get("Strength"));
		// The feather was used up as bait.
		assertEquals(1, r.used.size());
	}

	@Test
	public void cookingCountsWhatItUsesAsWellAsWhatItMakes()
	{
		MethodDetector.Result r = only(tick(xp("Cooking", 190), Collections.singletonList(item("Cooked karambwan", 1, 600)),
			Collections.singletonList(item("Raw karambwan", 1, 450))));
		assertEquals("Cooked karambwan", r.method);
		assertEquals(1, r.made.size());
		assertEquals(1, r.used.size());
	}

	@Test
	public void firemakingIsNamedByTheLogsBurned()
	{
		MethodDetector.Result r = only(tick(xp("Firemaking", 135), Collections.emptyList(),
			Collections.singletonList(item("Maple logs", 1, 30))));
		assertEquals("Maple logs", r.method);
		assertEquals("Firemaking", r.mainSkill);
	}

	@Test
	public void itemsCanArriveATickAfterTheirXp()
	{
		tick++;
		// XP first, with nothing to name it by: it waits instead of making a "Woodcutting" card.
		assertTrue(detector.onTick(tick, xp("Woodcutting", 85), ctx, 0).isEmpty());
		tick++;
		detector.onItems(tick, Collections.singletonList(item("Teak logs", 1, 150)), Collections.emptyList());
		MethodDetector.Result r = only(detector.onTick(tick, xp(), ctx, 0));
		assertEquals("Teak logs", r.method);
		assertEquals(Long.valueOf(85), r.xp.get("Woodcutting"));
	}

	@Test
	public void aLogBasketKeepsTheLastMethod()
	{
		tick(xp("Woodcutting", 85), Collections.singletonList(item("Teak logs", 1, 150)), Collections.emptyList());
		// The basket fills up: XP but no logs in the inventory.
		MethodDetector.Result r = only(tick(xp("Woodcutting", 85), Collections.emptyList(), Collections.emptyList()));
		assertEquals("Teak logs", r.method);
	}

	// ---- actions ----

	@Test
	public void thievingIsNamedByWhoYouPickpocket()
	{
		ctx.menuOption = "Pickpocket";
		ctx.menuTarget = "<col=ffff00>Master Farmer<col=ff00>  (level-3)";
		ctx.menuTick = tick;
		MethodDetector.Result r = only(tick(xp("Thieving", 43), Collections.singletonList(item("Ranarr seed", 1, 30_000)),
			Collections.emptyList()));
		assertEquals("Pickpocket Master Farmer", r.method);
		assertEquals(1, r.made.size());
	}

	@Test
	public void agilityIsNamedByTheCourse()
	{
		ctx.regionId = 10806;
		MethodDetector.Result r = only(tick(xp("Agility", 50), Collections.emptyList(), Collections.emptyList()));
		assertEquals("Seers' Village rooftops", r.method);
	}

	@Test
	public void alchingIsNamedBySpellAndShowsTheProfit()
	{
		ctx.menuOption = "Cast";
		ctx.menuTarget = "<col=00ff00>High Level Alchemy</col><col=ffffff> -> <col=ff9040>Rune platebody";
		ctx.menuTick = tick;
		MethodDetector.Result r = only(tick(xp("Magic", 65), Collections.singletonList(item("Coins", 39_000, 1)),
			Arrays.asList(item("Rune platebody", 1, 38_000), item("Nature rune", 1, 150))));
		assertEquals("High Level Alchemy", r.method);
		assertEquals(1, r.made.size());
		assertEquals(2, r.used.size());
	}

	@Test
	public void xpWithNothingToGoOnStillCountsAfterATick()
	{
		tick++;
		assertTrue(detector.onTick(tick, xp("Hunter", 100), ctx, 0).isEmpty());
		tick++;
		MethodDetector.Result r = only(detector.onTick(tick, xp(), ctx, 0));
		assertEquals("Hunter", r.method);
		assertEquals(Long.valueOf(100), r.xp.get("Hunter"));
	}

	@Test
	public void combatIsLeftToCombatTrackers()
	{
		assertTrue(tick(xp("Attack", 120, "Hitpoints", 40), Collections.emptyList(), Collections.emptyList()).isEmpty());
		ctx.fighting = true;
		assertTrue(tick(xp("Magic", 90, "Hitpoints", 30), Collections.emptyList(), Collections.emptyList()).isEmpty());
	}

	// ---- minigames ----

	@Test
	public void guardiansOfTheRiftCollectsItsSkillsAndRewards()
	{
		ctx.inGotr = true;
		MethodDetector.Result mine = only(tick(xp("Mining", 10), Collections.singletonList(item("Guardian fragments", 1, 0)),
			Collections.emptyList()));
		assertEquals("Guardians of the Rift", mine.method);
		assertEquals("Runecraft", mine.mainSkill);
		MethodDetector.Result craft = only(tick(xp("Runecraft", 300), Collections.singletonList(item("Fire rune", 60, 5)),
			Collections.singletonList(item("Guardian essence", 20, 0))));
		assertEquals("Guardians of the Rift", craft.method);

		// A reward search: items with no XP, shortly after.
		ctx.currentMethod = "Guardians of the Rift";
		ctx.currentLastMillis = tick * 600L;
		tick++;
		detector.onItems(tick, Collections.singletonList(item("Abyssal pearls", 5, 0)), Collections.emptyList());
		tick++;
		MethodDetector.Result search = only(detector.onTick(tick, xp(), ctx, tick * 600L));
		assertEquals(1, search.rewards.size());
	}

	@Test
	public void runesOutsideTheRiftAreTheirOwnMethod()
	{
		MethodDetector.Result r = only(tick(xp("Runecraft", 300), Collections.singletonList(item("Blood rune", 20, 300)),
			Collections.singletonList(item("Dark essence fragments", 20, 0))));
		assertEquals("Blood runes", r.method);
	}

	@Test
	public void theMotherlodeSackCountsTowardsTheMine()
	{
		MethodDetector.Result mine = only(tick(xp("Mining", 60), Collections.singletonList(item("Pay-dirt", 1, 0)),
			Collections.emptyList()));
		assertEquals("Motherlode Mine", mine.method);
		ctx.currentMethod = "Motherlode Mine";
		ctx.currentLastMillis = tick * 600L;
		tick++;
		detector.onItems(tick, Arrays.asList(item("Golden nugget", 3, 0), item("Coal", 20, 150)), Collections.emptyList());
		tick++;
		MethodDetector.Result sack = only(detector.onTick(tick, xp(), ctx, tick * 600L));
		assertEquals("Motherlode Mine", sack.method);
		assertEquals(2, sack.rewards.size());
	}

	@Test
	public void coalMinedWithXpIsItsOwnMethod()
	{
		ctx.currentMethod = "Motherlode Mine";
		ctx.currentLastMillis = 0;
		MethodDetector.Result r = only(tick(xp("Mining", 50), Collections.singletonList(item("Coal", 1, 150)), Collections.emptyList()));
		assertEquals("Coal", r.method);
	}

	// ---- stats ----

	private long now;

	private MethodStats stats()
	{
		MethodStats s = new MethodStats();
		s.setClock(() -> now);
		return s;
	}

	private MethodStats.Snapshot get(MethodStats s, String method)
	{
		for (MethodStats.Snapshot x : s.snapshot(false, false))
		{
			if (x.name.equals(method))
			{
				return x;
			}
		}
		return null;
	}

	/**
	 * 10 minutes: a karambwan every 6 seconds, 50 xp and 1,900 gp each.
	 */
	private void fish(MethodStats s)
	{
		for (int i = 0; i <= 100; i++)
		{
			s.recordXp("Karambwans", "Fishing", "Fishing", 50, now);
			s.recordMade("Karambwans", "Fishing", 3142, "Raw karambwan", 1, 1_900, 0, false, now);
			now += 6_000;
		}
		now -= 6_000;
	}

	@Test
	public void ratesPerHour()
	{
		MethodStats s = stats();
		fish(s);
		MethodStats.Snapshot k = get(s, "Karambwans");
		assertEquals(30_300, k.xpPerHour, 1);
		assertEquals(606, k.madePerHour, 1);
		assertEquals(1_151_400, k.gpPerHour, 100);
		assertTrue(k.now);
	}

	@Test
	public void tripGpPerHourFallsWhileYouBank()
	{
		MethodStats s = stats();
		fish(s);
		now += 10 * 60_000;
		MethodStats.Snapshot k = get(s, "Karambwans");
		assertTrue(k.away);
		assertEquals(1_151_400 / 2.0, k.gpPerHour, 100);
		assertEquals(1_151_400, k.spotGpPerHour, 100);
	}

	@Test
	public void droppingIsNotCountedAsProfit()
	{
		MethodStats s = stats();
		for (int i = 0; i < 100; i++)
		{
			s.recordXp("Teak logs", "Woodcutting", "Woodcutting", 85, now);
			s.recordMade("Teak logs", "Woodcutting", 6333, "Teak logs", 1, 150, 0, false, now);
			s.recordDropped("Teak logs", 6333, 1);
			now += 3_000;
		}
		MethodStats.Snapshot t = get(s, "Teak logs");
		assertTrue(t.dropping);
		assertEquals(0, t.value);
		assertFalse(stats().snapshot(false, true).iterator().hasNext());
	}

	@Test
	public void burningLogsCostsMoney()
	{
		MethodStats s = stats();
		for (int i = 0; i <= 100; i++)
		{
			s.recordXp("Maple logs", "Firemaking", "Firemaking", 135, now);
			s.recordUsed("Maple logs", "Firemaking", 1517, "Maple logs", 1, 30, 0, now);
			now += 3_000;
		}
		MethodStats.Snapshot m = get(s, "Maple logs");
		assertTrue(m.gpPerHour < 0);
		assertEquals(-3_030, m.value);
	}

	@Test
	public void bestSessionNeedsTenMinutes()
	{
		MethodStats s = stats();
		fish(s);
		s.updateBest();
		assertEquals(30_300, get(s, "Karambwans").bestXpPerHour, 1);
		// A short burst next session doesn't replace it.
		s.endSession();
		for (int i = 0; i < 10; i++)
		{
			s.recordXp("Karambwans", "Fishing", "Fishing", 500, now);
			now += 6_000;
		}
		s.updateBest();
		assertEquals(30_300, get(s, "Karambwans").bestXpPerHour, 1);
	}

	@Test
	public void methodsAreSavedAndLoaded()
	{
		MethodStats s = stats();
		fish(s);
		s.recordXp("Karambwans", "Fishing", "Agility", 5, now);
		String json = GSON.toJson(s.exportState());
		MethodStats loaded = stats();
		loaded.importState(Arrays.asList(GSON.fromJson(json, MethodStats.Saved[].class)));
		MethodStats.Snapshot k = get(loaded, "Karambwans");
		assertEquals(5_050, k.xp);
		assertEquals(191_900, k.value);
		assertTrue(k.otherXpPerHour.containsKey("Agility"));
		// A fresh load isn't "now" until you do it again.
		assertFalse(k.now);
	}

	@Test
	public void resetKeepsTheMethodRemoveDeletesIt()
	{
		MethodStats s = stats();
		fish(s);
		s.reset("Karambwans");
		assertEquals(0, get(s, "Karambwans").xp);
		s.remove("Karambwans");
		assertNull(get(s, "Karambwans"));
	}

	@Test
	public void nothingIsSavedUnlessItWasLoaded()
	{
		assertTrue(GrindsetPlugin.canSave("rsprofile.a", "rsprofile.a"));
		assertFalse(GrindsetPlugin.canSave("rsprofile.a", null));
		assertFalse(GrindsetPlugin.canSave("rsprofile.b", "rsprofile.a"));
		assertFalse(GrindsetPlugin.canSave(null, "rsprofile.a"));
	}

	// ---- data and formatting ----

	@Test
	public void bundledMethodsLoad()
	{
		assertTrue(book.size() > 60);
		assertEquals("Lava runes", book.forItem("Lava rune", false).name);
		assertEquals("Guardians of the Rift", book.forItem("Fire rune", true).name);
		assertNull(book.forItem("Dragon bones", false));
	}

	@Test
	public void formatting()
	{
		assertEquals("62.1K", Format.compact(62_100));
		assertEquals("1.08M", Format.compact(1_080_000));
		assertEquals("584", Format.compact(584));
		assertEquals("8.42K", Format.compact(8_421));
		assertEquals("-120K", Format.compact(-120_000));
		assertEquals("1h 52m", Format.duration(1 + 52 / 60.0));
		assertEquals("41m", Format.duration(41 / 60.0));
		assertNull(Format.timeToNextLevel(13_034_431, 50_000));
		assertEquals("1h 0m", Format.timeToLevel(0, 2, 83));
	}

	@Test
	public void everySkillHasAKind()
	{
		List<String> missing = new ArrayList<>();
		for (String skill : MethodDetector.ORDER)
		{
			if (!MethodDetector.KINDS.containsKey(skill))
			{
				missing.add(skill);
			}
		}
		assertTrue(missing.toString(), missing.isEmpty());
	}
}
