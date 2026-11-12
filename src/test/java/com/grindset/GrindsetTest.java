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
import static org.junit.Assert.assertArrayEquals;
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
	public void wintertodtCollectsItsSkillsInTheArena()
	{
		ctx.regionId = 6462;
		MethodDetector.Result chop = only(tick(xp("Woodcutting", 40), Collections.singletonList(item("Bruma root", 1, 0)),
			Collections.emptyList()));
		assertEquals("Wintertodt", chop.method);
		assertEquals("Firemaking", chop.mainSkill);
		MethodDetector.Result burn = only(tick(xp("Firemaking", 300), Collections.emptyList(),
			Collections.singletonList(item("Bruma root", 1, 0))));
		assertEquals("Wintertodt", burn.method);
		MethodDetector.Result fix = only(tick(xp("Construction", 120), Collections.emptyList(), Collections.emptyList()));
		assertEquals("Fixing the brazier counts too", "Wintertodt", fix.method);
	}

	@Test
	public void foundryAndTemporossAreFoundByTheirArena()
	{
		ctx.regionId = 13491;
		assertEquals("Giants' Foundry", only(tick(xp("Smithing", 5000), Collections.emptyList(), Collections.emptyList())).method);
		ctx.regionId = 12076;
		assertEquals("Tempoross", only(tick(xp("Fishing", 80), Collections.singletonList(item("Raw harpoonfish", 1, 0)),
			Collections.emptyList())).method);
		// Outside the arena the same XP is its own method again.
		ctx.regionId = 12850;
		assertEquals("Lobsters", only(tick(xp("Fishing", 90), Collections.singletonList(item("Raw lobster", 1, 200)),
			Collections.emptyList())).method);
		assertNull("Only its own skills", book.forRegion(13491, "Mining"));
	}

	@Test
	public void sepulchreAndVolcanicMineAreFoundByTheirArena()
	{
		ctx.regionId = 9563;
		assertEquals("Hallowed Sepulchre", only(tick(xp("Agility", 400), Collections.emptyList(), Collections.emptyList())).method);
		ctx.regionId = 15263;
		assertEquals("Volcanic Mine", only(tick(xp("Mining", 900), Collections.emptyList(), Collections.emptyList())).method);
	}

	@Test
	public void minigameEndMessagesCountGames()
	{
		assertArrayEquals(new String[]{"Wintertodt", "Firemaking"},
			GrindsetPlugin.gameEnded("Your subdued Wintertodt count is: <col=ff0000>412</col>."));
		assertArrayEquals(new String[]{"Tempoross", "Fishing"}, GrindsetPlugin.gameEnded("Your Tempoross kill count is: 88."));
		assertNull(GrindsetPlugin.gameEnded("You light a fire."));
		assertNull(GrindsetPlugin.gameEnded(null));
	}

	@Test
	public void cardsShowTheGoalsTabLevel()
	{
		assertEquals(Integer.valueOf(90), GrindsetPanel.goalFor("Fishing", "Fishing", 90, 80));
		assertNull("Another skill's goal", GrindsetPanel.goalFor("Mining", "Fishing", 90, 80));
		assertNull("Already there", GrindsetPanel.goalFor("Fishing", "Fishing", 80, 80));
		assertNull("The next level has its own row", GrindsetPanel.goalFor("Fishing", "Fishing", 81, 80));
		assertNull("99 has its own row", GrindsetPanel.goalFor("Fishing", "Fishing", 99, 80));
		assertNull(GrindsetPanel.goalFor("Fishing", null, null, 80));
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
	public void bankingEndsATripForTheLog()
	{
		MethodStats s = stats();
		fish(s);
		s.endTrip(now + 60_000);
		MethodStats.Snapshot k = get(s, "Karambwans");
		assertEquals(1, k.trips.size());
		assertEquals(5_050, k.trips.get(0).xp);
		assertEquals(191_900, k.trips.get(0).value(false));
		assertEquals(600_000 + 60_000, k.trips.get(0).millis);
		// Opening the bank again with nothing done in between adds no empty trip.
		s.endTrip(now + 120_000);
		assertEquals(1, get(s, "Karambwans").trips.size());
	}

	@Test
	public void theTripLogKeepsTheLatestTen()
	{
		MethodStats s = stats();
		for (int i = 0; i < MethodStats.TRIPS_KEPT + 3; i++)
		{
			s.recordXp("Karambwans", "Fishing", "Fishing", 100 + i, now);
			now += 60_000;
			s.endTrip(now);
			now += 60_000;
		}
		MethodStats.Snapshot k = get(s, "Karambwans");
		assertEquals(MethodStats.TRIPS_KEPT, k.trips.size());
		assertEquals("Newest first", 100 + MethodStats.TRIPS_KEPT + 2, k.trips.get(0).xp);
	}

	@Test
	public void sessionsOfFiveMinutesGoInTheHistory()
	{
		MethodStats s = stats();
		fish(s);
		s.endSession();
		MethodStats.Snapshot k = get(s, "Karambwans");
		assertEquals(1, k.sessions.size());
		assertEquals(30_300, k.sessions.get(0).xpPerHour(), 1);
		assertEquals("Logging out ends the trip too", 1, k.trips.size());
		// A two-minute session is too short to count.
		for (int i = 0; i < 20; i++)
		{
			s.recordXp("Karambwans", "Fishing", "Fishing", 50, now);
			now += 6_000;
		}
		s.endSession();
		assertEquals(1, get(s, "Karambwans").sessions.size());
	}

	@Test
	public void tripsAndSessionsAreSaved()
	{
		MethodStats s = stats();
		fish(s);
		s.endSession();
		String json = GSON.toJson(s.exportState());
		MethodStats loaded = stats();
		loaded.importState(Arrays.asList(GSON.fromJson(json, MethodStats.Saved[].class)));
		MethodStats.Snapshot k = get(loaded, "Karambwans");
		assertEquals(1, k.trips.size());
		assertEquals(1, k.sessions.size());
		assertEquals(5_050, k.sessions.get(0).xp);
	}

	@Test
	public void tripRowsReadWell()
	{
		assertEquals("+42.1K XP · 310K GP", GrindsetPanel.tripText(42_100, 310_000));
		assertEquals("+5.05K XP · -12.0K GP", GrindsetPanel.tripText(5_050, -12_000));
		assertEquals("+900 XP", GrindsetPanel.tripText(900, 0));
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
		// Long goals read as hours of play, not calendar days.
		assertEquals("296h", Format.duration(296.2));
		assertEquals("1,250h", Format.duration(1250));
		assertEquals("23h 59m", Format.duration(23 + 59 / 60.0));
	}

	private static javax.swing.JPanel card(String method, String rate)
	{
		javax.swing.JPanel card = new javax.swing.JPanel(new java.awt.BorderLayout());
		card.setName(method);
		card.add(new javax.swing.JLabel(method), java.awt.BorderLayout.NORTH);
		card.add(new javax.swing.JLabel(rate), java.awt.BorderLayout.CENTER);
		return card;
	}

	@Test
	public void overlayOnlySnapshotsTheCurrentMethod()
	{
		MethodStats s = stats();
		assertNull(s.currentSnapshot(false, false));
		s.recordXp("Teak logs", "Woodcutting", "Woodcutting", 85, now);
		s.recordXp("Karambwans", "Fishing", "Fishing", 50, now);
		MethodStats.Snapshot current = s.currentSnapshot(false, false);
		assertEquals("Karambwans", current.name);
		assertEquals(get(s, "Karambwans").xp, current.xp);
	}

	@Test
	public void panelKeepsRowsThatDidNotChange()
	{
		javax.swing.JPanel list = new javax.swing.JPanel();
		List<java.awt.Component> first = Arrays.asList(new javax.swing.JLabel("Teak logs"), new javax.swing.JLabel("62.1K XP"));
		GrindsetPanel.sync(list, first);
		java.awt.Component kept = list.getComponent(0);

		// Same first row, new rate: only the second row is swapped.
		GrindsetPanel.sync(list, Arrays.asList(new javax.swing.JLabel("Teak logs"), new javax.swing.JLabel("63.0K XP")));
		assertTrue(kept == list.getComponent(0));
		assertEquals("63.0K XP", ((javax.swing.JLabel) list.getComponent(1)).getText());

		// A card whose rate changed stays; only the label inside it is swapped.
		GrindsetPanel.sync(list, Collections.singletonList(card("Teak logs", "62.1K XP")));
		java.awt.Component card = list.getComponent(0);
		java.awt.Component name = ((javax.swing.JPanel) card).getComponent(0);
		GrindsetPanel.sync(list, Collections.singletonList(card("Teak logs", "63.0K XP")));
		assertTrue(card == list.getComponent(0));
		assertTrue(name == ((javax.swing.JPanel) card).getComponent(0));
		assertEquals("63.0K XP", ((javax.swing.JLabel) ((javax.swing.JPanel) card).getComponent(1)).getText());
		// Another method's card is never kept for this one.
		GrindsetPanel.sync(list, Collections.singletonList(card("Karambwans", "63.0K XP")));
		assertFalse(card == list.getComponent(0));

		GrindsetPanel.sync(list, Collections.singletonList(new javax.swing.JLabel("Karambwans")));
		assertEquals(1, list.getComponentCount());
		assertEquals("Karambwans", ((javax.swing.JLabel) list.getComponent(0)).getText());
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

	// ---- 1.1: locations ----

	@Test
	public void methodsRememberWhereTheyWereDone()
	{
		MethodStats s = stats();
		fish(s);
		s.setArea("Karambwans", "Piscatorial");
		assertEquals("Piscatorial", get(s, "Karambwans").area);
		MethodStats loaded = stats();
		loaded.importState(Arrays.asList(GSON.fromJson(GSON.toJson(s.exportState()), MethodStats.Saved[].class)));
		assertEquals("Piscatorial", get(loaded, "Karambwans").area);
		// A spot that isn't known leaves it as it was.
		loaded.setArea("Karambwans", null);
		assertEquals("Piscatorial", get(loaded, "Karambwans").area);
	}

	@Test
	public void bundledSpotsResolve()
	{
		Map<String, String> areas = GSON.fromJson(new InputStreamReader(GrindsetPlugin.class.getResourceAsStream("areas.json"),
			StandardCharsets.UTF_8), new TypeToken<Map<String, String>>()
		{
		}.getType());
		assertTrue(areas.size() > 200);
		assertEquals("Piscatorial", areas.get("9273"));
		assertEquals("Motherlode Mine", areas.get("14936"));
		assertEquals("Wintertodt", areas.get("6462"));
		assertEquals("Blast Furnace", areas.get("7757"));
		assertEquals("Temple of the Eye", areas.get("14484"));
	}
}
