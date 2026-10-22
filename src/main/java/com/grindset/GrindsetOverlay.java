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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The method you're doing, on screen: XP/hr, GP/hr and time to your next level.
 */
class GrindsetOverlay extends OverlayPanel
{
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color GOLD = new Color(255, 198, 64);
	private static final Color GREEN = new Color(96, 220, 140);
	private static final Color LOSS = new Color(232, 93, 74);

	private final GrindsetPlugin plugin;
	private final GrindsetConfig config;

	@Inject
	GrindsetOverlay(GrindsetPlugin plugin, GrindsetConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}
		MethodStats.Snapshot s = plugin.getOverlayMethod();
		if (s == null || !s.now || s.xpPerHour < 0)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text(s.name).color(TITLE).build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("XP/hr:")
			.right(Format.compact(s.xpPerHour))
			.build());
		if (s.dropping)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("GP/hr:").right("dropping").rightColor(Color.LIGHT_GRAY).build());
		}
		else if (s.gpPerHour != -1 && s.value != 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("GP/hr:")
				.right(Format.gp(s.gpPerHour))
				.rightColor(s.gpPerHour < 0 ? LOSS : GOLD)
				.build());
		}
		Map<String, Integer> xp = plugin.getCurrentXp();
		Integer have = xp.get(s.skill);
		if (have != null)
		{
			String next = Format.timeToNextLevel(have, s.xpPerHour);
			if (next != null)
			{
				panelComponent.getChildren().add(LineComponent.builder().left("Next level:").right(next).rightColor(GREEN).build());
			}
		}
		return super.render(graphics);
	}
}
