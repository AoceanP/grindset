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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The skilling methods the plugin knows: each one's name, its skills, the items that show
 * it's being done, and (for minigames like the Motherlode Mine) the rewards collected later
 * without XP. Bundled in methods.json.
 */
final class MethodBook
{
	/**
	 * One method, as written in methods.json.
	 */
	static final class Def
	{
		String name;
		List<String> skills;
		List<String> items;
		List<String> rewards;
		boolean minigame;

		boolean hasSkill(String skill)
		{
			return skills != null && skills.contains(skill);
		}
	}

	/** Carrying this means the player is in a Guardians of the Rift game. */
	static final String GOTR_ESSENCE = "guardian essence";
	static final String GOTR = "Guardians of the Rift";

	private final Map<String, Def> byItem = new HashMap<>();
	private final Map<String, List<Def>> byReward = new HashMap<>();
	private final Map<String, Def> byName = new HashMap<>();

	MethodBook(List<Def> defs)
	{
		if (defs == null)
		{
			return;
		}
		for (Def d : defs)
		{
			if (d == null || d.name == null)
			{
				continue;
			}
			byName.put(d.name, d);
			for (String item : d.items == null ? Collections.<String>emptyList() : d.items)
			{
				byItem.put(key(item), d);
			}
			for (String reward : d.rewards == null ? Collections.<String>emptyList() : d.rewards)
			{
				byReward.computeIfAbsent(key(reward), k -> new ArrayList<>()).add(d);
			}
		}
	}

	/**
	 * @param inGotr whether the player is in a Guardians of the Rift game, where runes crafted
	 *               count towards the minigame rather than that rune's own method
	 * @return the method an item gathered with XP belongs to, or null
	 */
	Def forItem(String itemName, boolean inGotr)
	{
		if (itemName == null)
		{
			return null;
		}
		if (inGotr && isGotrReward(itemName))
		{
			return byName.get(GOTR);
		}
		return byItem.get(key(itemName));
	}

	/**
	 * @return whether the item is a reward of the method, collected without XP
	 */
	boolean isReward(Def method, String itemName)
	{
		List<Def> defs = itemName == null ? null : byReward.get(key(itemName));
		return defs != null && defs.contains(method);
	}

	private boolean isGotrReward(String itemName)
	{
		List<Def> defs = byReward.get(key(itemName));
		return defs != null && defs.contains(byName.get(GOTR));
	}

	Def byName(String name)
	{
		return name == null ? null : byName.get(name);
	}

	int size()
	{
		return byName.size();
	}

	static String key(String s)
	{
		return s.trim().toLowerCase(Locale.ROOT);
	}
}
