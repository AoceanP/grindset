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

import java.util.Locale;
import net.runelite.api.Experience;

/**
 * Numbers and times as the panel and overlay show them.
 */
final class Format
{
	static final int MAX_LEVEL = 99;

	private Format()
	{
	}

	/**
	 * @return e.g. "62.1K", "1.08M", "584"
	 */
	static String compact(double value)
	{
		double v = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (v >= 1_000_000_000)
		{
			return sign + trim(v / 1_000_000_000) + "B";
		}
		if (v >= 1_000_000)
		{
			return sign + trim(v / 1_000_000) + "M";
		}
		if (v >= 1_000)
		{
			return sign + trim(v / 1_000) + "K";
		}
		if (v >= 100)
		{
			return sign + String.format(Locale.US, "%,d", Math.round(v));
		}
		return sign + String.format(Locale.US, "%.1f", v);
	}

	/**
	 * Three significant figures: 1.08M, 62.1K, 584K.
	 */
	private static String trim(double v)
	{
		if (v >= 100)
		{
			return String.format(Locale.US, "%.0f", v);
		}
		if (v >= 10)
		{
			return String.format(Locale.US, "%.1f", v);
		}
		return String.format(Locale.US, "%.2f", v);
	}

	/**
	 * @return gold per hour, e.g. "1.08M" or "-120K" for a method that costs money
	 */
	static String gp(double perHour)
	{
		return compact(perHour);
	}

	/**
	 * @return e.g. "1h 52m", "41m", "2d 4h"
	 */
	static String duration(double hours)
	{
		long minutes = Math.round(hours * 60);
		if (minutes < 1)
		{
			return "<1m";
		}
		long days = minutes / (60 * 24);
		long h = (minutes / 60) % 24;
		long m = minutes % 60;
		if (days > 0)
		{
			return days + "d " + h + "h";
		}
		return minutes >= 60 ? (minutes / 60) + "h " + m + "m" : m + "m";
	}

	/**
	 * @return the time to the next level at this rate, or null at 99 or with no rate yet
	 */
	static String timeToNextLevel(int xp, double xpPerHour)
	{
		int level = Experience.getLevelForXp(xp);
		if (level >= MAX_LEVEL || xpPerHour <= 0)
		{
			return null;
		}
		return duration((Experience.getXpForLevel(level + 1) - xp) / xpPerHour);
	}

	/**
	 * @return the time to a level at this rate, or null if it's reached or there's no rate yet
	 */
	static String timeToLevel(int xp, int target, double xpPerHour)
	{
		int needed = Experience.getXpForLevel(target) - xp;
		if (needed <= 0 || xpPerHour <= 0)
		{
			return null;
		}
		return duration(needed / xpPerHour);
	}
}
