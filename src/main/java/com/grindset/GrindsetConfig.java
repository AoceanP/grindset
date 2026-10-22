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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GrindsetConfig.GROUP)
public interface GrindsetConfig extends Config
{
	String GROUP = "grindset";

	enum Price
	{
		GRAND_EXCHANGE("Grand Exchange"),
		HIGH_ALCHEMY("High Alchemy");

		private final String label;

		Price(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Overlay",
		description = "Show the method you're doing on screen: XP/hr, GP/hr and time to your next level.",
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rememberMethods",
		name = "Remember methods",
		description = "Keep your methods between sessions and client restarts, separately for each account.",
		position = 1
	)
	default boolean rememberMethods()
	{
		return true;
	}

	@ConfigItem(
		keyName = "price",
		name = "GP/hr price",
		description = "Value what you gather at Grand Exchange prices or High Alchemy.",
		position = 2
	)
	default Price price()
	{
		return Price.GRAND_EXCHANGE;
	}

	@ConfigItem(
		keyName = "countDropped",
		name = "Count dropped items",
		description = "Include the value of items you drop (power-training) in GP/hr.",
		position = 3
	)
	default boolean countDropped()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hiddenMethods",
		name = "",
		description = "",
		hidden = true
	)
	default String hiddenMethods()
	{
		return "";
	}
}
