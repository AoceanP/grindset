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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Grindset",
	description = "Skilling tracked by method: XP/hr, trip GP/hr with banking included and time to level for every skill",
	tags = {"skilling", "xp", "gp", "profit", "rates", "method", "tracker"}
)
public class GrindsetPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(GrindsetPlugin.class);
	private static final String METHODS_KEY = "methods";
	/** How often rates redraw while a method is going: 5 ticks, 3 seconds. */
	private static final int RATE_TICKS = 5;
	/** Best sessions are checked this often. */
	private static final int BEST_TICKS = 100;
	/** Being in a Guardians of the Rift game: guardian essence or fragments seen this recently. */
	private static final int GOTR_TICKS = 200;
	/** The end-of-game message of a Guardians of the Rift game. */
	private static final String GOTR_GAME_END = "Total elemental energy";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GrindsetConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private GrindsetOverlay overlay;

	private final MethodStats stats = new MethodStats();
	private MethodBook book;
	private MethodDetector detector;
	private GrindsetPanel panel;
	private NavigationButton navButton;

	// XP per skill, to turn StatChanged into drops; unknown until the first update after login.
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final Map<String, Long> xpThisTick = new HashMap<>();
	// Each skill's XP right now, for time to level. Read by the panel.
	private volatile Map<String, Integer> currentXp = Collections.emptyMap();
	private Map<Integer, Integer> lastInventory;
	private final MethodDetector.Context context = new MethodDetector.Context();
	private int lastGotrTick = -1000;
	private int ticks;
	private volatile int rateTick;
	// The method being done, for the overlay. Updated every tick.
	private volatile MethodStats.Snapshot overlayMethod;
	// The account whose saved methods are loaded. Nothing is saved for any other.
	private volatile String loadedProfile;
	private int savedRevision = -1;

	@Provides
	GrindsetConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GrindsetConfig.class);
	}

	@Override
	protected void startUp()
	{
		book = new MethodBook(loadResource("methods.json", new TypeToken<List<MethodBook.Def>>()
		{
		}.getType()));
		Map<String, String> courses = loadResource("courses.json", new TypeToken<Map<String, String>>()
		{
		}.getType());
		Map<Integer, String> byRegion = new HashMap<>();
		if (courses != null)
		{
			courses.forEach((region, name) -> byRegion.put(Integer.parseInt(region), name));
		}
		detector = new MethodDetector(book, byRegion);

		panel = new GrindsetPanel(this, config, itemManager, skillIconManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Grindset")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::loadProfile);
		}
	}

	@Override
	protected void shutDown()
	{
		stats.endSession();
		save();
		overlayManager.remove(overlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = null;
		panel = null;
		lastXp.clear();
		xpThisTick.clear();
		lastInventory = null;
		loadedProfile = null;
	}

	// ---- Events -------------------------------------------------------------------

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		Integer before = lastXp.put(skill, xp);
		if (before != null && xp > before)
		{
			xpThisTick.merge(skill.getName(), (long) (xp - before), Long::sum);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		Map<Integer, Integer> now = counts(event.getItemContainer());
		Map<Integer, Integer> before = lastInventory;
		lastInventory = now;
		if (before == null || isBankOpen())
		{
			return;
		}
		List<MethodDetector.Item> gained = new ArrayList<>();
		List<MethodDetector.Item> lost = new ArrayList<>();
		Set<Integer> ids = new LinkedHashSet<>(now.keySet());
		ids.addAll(before.keySet());
		boolean hadGotrEssence = false;
		for (int id : ids)
		{
			int diff = now.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (before.containsKey(id) && isGotrItem(id))
			{
				hadGotrEssence = true;
			}
			if (diff != 0)
			{
				MethodDetector.Item item = item(id, Math.abs(diff));
				(diff > 0 ? gained : lost).add(item);
				if (isGotrItem(id))
				{
					lastGotrTick = client.getTickCount();
				}
			}
		}
		if (hadGotrEssence)
		{
			lastGotrTick = client.getTickCount();
		}
		detector.onItems(client.getTickCount(), gained, lost);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		context.menuOption = event.getMenuOption();
		context.menuTarget = event.getMenuTarget();
		context.menuTick = client.getTickCount();
	}

	/**
	 * Items dropped while power-training: they don't count towards GP/hr.
	 */
	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		if (item.getOwnership() != TileItem.OWNERSHIP_SELF)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		WorldPoint where = event.getTile().getWorldLocation();
		if (me == null || where == null || where.distanceTo(me.getWorldLocation()) > 1)
		{
			return;
		}
		String method = stats.getCurrent();
		if (stats.hasMade(method, item.getId()))
		{
			stats.recordDropped(method, item.getId(), item.getQuantity());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains(GOTR_GAME_END)
			&& client.getTickCount() - lastGotrTick <= GOTR_TICKS)
		{
			stats.recordGame(MethodBook.GOTR, "Runecraft", System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		int now = client.getTickCount();
		Player me = client.getLocalPlayer();
		context.regionId = me == null ? -1 : me.getWorldLocation().getRegionID();
		context.inGotr = now - lastGotrTick <= GOTR_TICKS;
		context.fighting = me != null && me.getInteracting() instanceof NPC;
		context.currentMethod = stats.getCurrent();
		context.currentLastMillis = stats.lastMillis(context.currentMethod);

		long millis = System.currentTimeMillis();
		Map<String, Long> xp = new HashMap<>(xpThisTick);
		xpThisTick.clear();
		for (MethodDetector.Result r : detector.onTick(now, xp, context, millis))
		{
			r.xp.forEach((skill, gain) -> stats.recordXp(r.method, r.mainSkill, skill, gain, millis));
			for (MethodDetector.Item i : r.made)
			{
				stats.recordMade(r.method, r.mainSkill, i.id, i.name, i.quantity, i.unitPrice, i.haPrice, false, millis);
			}
			for (MethodDetector.Item i : r.rewards)
			{
				stats.recordMade(r.method, r.mainSkill, i.id, i.name, i.quantity, i.unitPrice, i.haPrice, true, millis);
			}
			for (MethodDetector.Item i : r.used)
			{
				stats.recordUsed(r.method, r.mainSkill, i.id, i.name, i.quantity, i.unitPrice, i.haPrice, millis);
			}
		}

		overlayMethod = config.showOverlay() ? currentMethod() : null;
		if (++ticks % RATE_TICKS == 0)
		{
			Map<String, Integer> xpNow = new HashMap<>();
			for (Skill s : Skill.values())
			{
				xpNow.put(s.getName(), client.getSkillExperience(s));
			}
			currentXp = xpNow;
			if (stats.getCurrent() != null)
			{
				rateTick++;
			}
			refreshPanel();
		}
		if (ticks % BEST_TICKS == 0)
		{
			stats.updateBest();
			save();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// XP and inventory start again from what the next login says.
			lastXp.clear();
			xpThisTick.clear();
			lastInventory = null;
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			stats.endSession();
			save();
			refreshPanel();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProfile();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GrindsetConfig.GROUP.equals(event.getGroup()))
		{
			refreshPanel();
		}
	}

	// ---- Saving -------------------------------------------------------------------

	private void loadProfile()
	{
		loadedProfile = null;
		List<MethodStats.Saved> saved = null;
		if (config.rememberMethods() && configManager.getRSProfileKey() != null)
		{
			String json = configManager.getRSProfileConfiguration(GrindsetConfig.GROUP, METHODS_KEY);
			if (json != null && !json.isEmpty())
			{
				try
				{
					MethodStats.Saved[] parsed = gson.fromJson(json, MethodStats.Saved[].class);
					saved = parsed == null ? null : Arrays.asList(parsed);
				}
				catch (JsonParseException e)
				{
					log.debug("Couldn't read saved methods", e);
				}
			}
		}
		stats.importState(saved);
		savedRevision = stats.getRevision();
		loadedProfile = configManager.getRSProfileKey();
		refreshPanel();
	}

	/**
	 * Saves the methods if they changed, only for the account they were loaded for.
	 */
	private void save()
	{
		String profile = configManager.getRSProfileKey();
		if (!canSave(profile, loadedProfile) || stats.getRevision() == savedRevision)
		{
			return;
		}
		savedRevision = stats.getRevision();
		if (!config.rememberMethods() || stats.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(GrindsetConfig.GROUP, METHODS_KEY);
			return;
		}
		configManager.setRSProfileConfiguration(GrindsetConfig.GROUP, METHODS_KEY, gson.toJson(stats.exportState()));
	}

	/**
	 * @return whether it's safe to save: logged in, to the account whose data was loaded
	 */
	static boolean canSave(String currentProfile, String loadedProfile)
	{
		return currentProfile != null && currentProfile.equals(loadedProfile);
	}

	// ---- Called from the panel (Swing thread) -------------------------------------

	/**
	 * @return every method, what you're doing now first. Safe off the client thread.
	 */
	List<MethodStats.Snapshot> getMethods()
	{
		return stats.snapshot(config.price() == GrindsetConfig.Price.HIGH_ALCHEMY, config.countDropped());
	}

	/**
	 * @return each skill's XP, as of the last few ticks
	 */
	Map<String, Integer> getCurrentXp()
	{
		return currentXp;
	}

	int getRateTick()
	{
		return rateTick;
	}

	void hideMethod(String method)
	{
		Set<String> hidden = hiddenMethods();
		hidden.add(method);
		configManager.setConfiguration(GrindsetConfig.GROUP, "hiddenMethods", String.join("\n", hidden));
	}

	void showAllMethods()
	{
		configManager.unsetConfiguration(GrindsetConfig.GROUP, "hiddenMethods");
	}

	Set<String> hiddenMethods()
	{
		Set<String> out = new LinkedHashSet<>();
		for (String s : config.hiddenMethods().split("\n"))
		{
			if (!s.trim().isEmpty())
			{
				out.add(s.trim());
			}
		}
		return out;
	}

	void resetMethod(String method)
	{
		clientThread.invokeLater(() ->
		{
			stats.reset(method);
			save();
			refreshPanel();
		});
	}

	void removeMethod(String method)
	{
		clientThread.invokeLater(() ->
		{
			stats.remove(method);
			save();
			refreshPanel();
		});
	}

	// ---- Helpers --------------------------------------------------------------------

	private void refreshPanel()
	{
		GrindsetPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	private static boolean isGotrItem(int id)
	{
		return id == ItemID.GOTR_GUARDIAN_ESSENCE || id == ItemID.GOTR_GUARDIAN_FRAGMENT;
	}

	private MethodDetector.Item item(int id, long quantity)
	{
		ItemComposition comp = itemManager.getItemComposition(id);
		long price = id == ItemID.COINS ? 1 : itemManager.getItemPrice(id);
		long ha = id == ItemID.COINS ? 1 : comp.getHaPrice();
		return new MethodDetector.Item(id, comp.getName(), quantity, price, ha);
	}

	private static Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					out.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return out;
	}

	private <T> T loadResource(String name, Type type)
	{
		try (InputStream in = GrindsetPlugin.class.getResourceAsStream(name))
		{
			return in == null ? null : gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("Couldn't read {}", name, e);
			return null;
		}
	}

	/**
	 * @return the method being done, for the overlay, or null
	 */
	MethodStats.Snapshot getOverlayMethod()
	{
		return overlayMethod;
	}

	private MethodStats.Snapshot currentMethod()
	{
		String current = stats.getCurrent();
		if (current == null)
		{
			return null;
		}
		for (MethodStats.Snapshot s : getMethods())
		{
			if (s.name.equals(current))
			{
				return s;
			}
		}
		return null;
	}
}
