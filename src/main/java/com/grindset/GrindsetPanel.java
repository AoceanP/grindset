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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * The sidebar: one card per method. The one you're doing is open; the rest fold into a single
 * line each and open on a click.
 */
class GrindsetPanel extends PluginPanel
{
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	static final Color HEADER = new Color(24, 24, 24);
	static final Color SLOT = new Color(36, 36, 36);
	static final Color BORDER = new Color(52, 52, 52);
	static final Color TRACK = new Color(17, 17, 17);
	static final Color TEXT = Color.WHITE;
	static final Color MUTED = new Color(168, 168, 168);
	static final Color SUBTLE = new Color(118, 118, 118);
	static final Color GOLD = new Color(255, 198, 64);
	static final Color GREEN = new Color(96, 220, 140);
	static final Color LOSS = new Color(232, 93, 74);
	private static final int ICON = 26;
	private static final String ISSUES = "https://github.com/AoceanP/grindset/issues/new";

	private final GrindsetPlugin plugin;
	private final GrindsetConfig config;
	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;
	private final JPanel list = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JLabel count = label("", FontManager.getRunescapeSmallFont(), MUTED);
	private final JLabel hiddenLabel = label("", FontManager.getRunescapeSmallFont(), SUBTLE);
	// Cards opened or closed by hand; the rest follow the default (open while you're doing it).
	private final Set<String> toggled = new HashSet<>();
	private String viewKey;

	GrindsetPanel(GrindsetPlugin plugin, GrindsetConfig config, ItemManager itemManager,
		SkillIconManager skillIcons)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
		setLayout(new BorderLayout(0, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel title = new JPanel(new BorderLayout());
		title.setOpaque(false);
		title.add(label("Grindset", FontManager.getRunescapeBoldFont(), TEXT), BorderLayout.WEST);
		title.add(count, BorderLayout.EAST);
		// Feedback goes to GitHub issues, like Combat & XP Tracker.
		JPanel feedback = new JPanel(new GridLayout(1, 2, 4, 0));
		feedback.setOpaque(false);
		feedback.add(flatButton("Report bug", "Opens the plugin's GitHub page to report a bug",
			() -> LinkBrowser.browse(ISSUES + "?labels=bug")));
		feedback.add(flatButton("Suggest", "Opens the plugin's GitHub page to suggest a feature",
			() -> LinkBrowser.browse(ISSUES + "?labels=enhancement")));
		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.setOpaque(false);
		top.add(title, BorderLayout.NORTH);
		top.add(feedback, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		list.setOpaque(false);
		add(list, BorderLayout.CENTER);

		hiddenLabel.setHorizontalAlignment(SwingConstants.CENTER);
		hiddenLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hiddenLabel.setToolTipText("Click to show every hidden method again");
		hiddenLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				plugin.showAllMethods();
			}
		});
		add(hiddenLabel, BorderLayout.SOUTH);
		refresh();
	}

	/**
	 * Redraws the cards. Swing thread; reads nothing from the game.
	 */
	void refresh()
	{
		List<MethodStats.Snapshot> methods = plugin.getMethods();
		Set<String> hidden = plugin.hiddenMethods();
		Map<String, Integer> xp = plugin.getCurrentXp();
		String key = plugin.getRateTick() + "|" + hidden + "|" + config.price() + "|" + config.countDropped() + "|"
			+ xp.hashCode() + "|" + toggled + "|" + summary(methods);
		if (key.equals(viewKey))
		{
			return;
		}
		viewKey = key;

		list.removeAll();
		int shown = 0;
		int hiddenCount = 0;
		for (MethodStats.Snapshot s : methods)
		{
			if (hidden.contains(s.name))
			{
				hiddenCount++;
				continue;
			}
			boolean open = s.now != toggled.contains(s.name);
			list.add(open ? card(s, xp.get(s.skill)) : folded(s));
			shown++;
		}
		if (shown == 0)
		{
			list.add(empty());
		}
		count.setText(shown == 1 ? "1 method" : shown + " methods");
		hiddenLabel.setText(hiddenCount == 0 ? "" : hiddenCount + " hidden. Click to show.");
		hiddenLabel.setVisible(hiddenCount > 0);
		revalidate();
		repaint();
	}

	private static String summary(List<MethodStats.Snapshot> methods)
	{
		StringBuilder sb = new StringBuilder();
		for (MethodStats.Snapshot s : methods)
		{
			sb.append(s.name).append(s.xp).append(s.value).append(s.now).append(s.away).append(s.games).append(';');
		}
		return sb.toString();
	}

	private void toggle(String method)
	{
		if (!toggled.remove(method))
		{
			toggled.add(method);
		}
		refresh();
	}

	// ---- Folded: one line ---------------------------------------------------------------

	private JPanel folded(MethodStats.Snapshot s)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(CARD);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 8));
		row.add(icon(s), BorderLayout.WEST);
		row.add(names(s, false), BorderLayout.CENTER);

		JPanel rates = new JPanel(new GridLayout(2, 1));
		rates.setOpaque(false);
		JLabel xpRate = label(s.xpPerHour < 0 ? "-" : Format.compact(s.xpPerHour) + " xp", FontManager.getRunescapeSmallFont(), TEXT);
		xpRate.setHorizontalAlignment(SwingConstants.RIGHT);
		JLabel gpRate = label(gpText(s), FontManager.getRunescapeSmallFont(), gpColour(s));
		gpRate.setHorizontalAlignment(SwingConstants.RIGHT);
		rates.add(xpRate);
		rates.add(gpRate);
		row.add(rates, BorderLayout.EAST);

		clickable(row, s.name, s.name + " (" + s.skill + "). Click to open");
		return row;
	}

	private static String gpText(MethodStats.Snapshot s)
	{
		if (s.dropping)
		{
			return "dropping";
		}
		return s.value != 0 && s.gpPerHour != -1 ? Format.gp(s.gpPerHour) + " gp" : "";
	}

	private static Color gpColour(MethodStats.Snapshot s)
	{
		return s.dropping ? SUBTLE : s.gpPerHour < 0 ? LOSS : GOLD;
	}

	// ---- Open: the whole card ---------------------------------------------------------------

	private JPanel card(MethodStats.Snapshot s, Integer skillXp)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(CARD);
		if (s.now)
		{
			card.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, GREEN));
		}

		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setBackground(HEADER);
		head.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 8));
		head.add(icon(s), BorderLayout.WEST);
		head.add(names(s, true), BorderLayout.CENTER);
		if (s.now || s.away)
		{
			JLabel status = label(s.now ? "now" : "away", FontManager.getRunescapeSmallFont(), s.now ? GREEN : MUTED);
			status.setVerticalAlignment(SwingConstants.TOP);
			head.add(status, BorderLayout.EAST);
		}
		clickable(head, s.name, "Click to fold");
		card.add(head, BorderLayout.NORTH);

		JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		body.add(tiles(s));
		if (skillXp != null)
		{
			JPanel progress = levelBar(skillXp);
			if (progress != null)
			{
				body.add(progress);
			}
		}
		for (JPanel row : rows(s, skillXp))
		{
			body.add(row);
		}
		card.add(body, BorderLayout.CENTER);
		card.setComponentPopupMenu(menu(s.name));
		return card;
	}

	/**
	 * The method's name, with its skill underneath so a long name never hides it.
	 */
	private static JPanel names(MethodStats.Snapshot s, boolean bold)
	{
		JPanel names = new JPanel(new GridLayout(2, 1));
		names.setOpaque(false);
		names.add(label(s.name, bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont(), TEXT));
		names.add(label(s.skill, FontManager.getRunescapeSmallFont(), SUBTLE));
		return names;
	}

	/**
	 * The method's item, with its skill's icon in the corner.
	 */
	JComponent icon(MethodStats.Snapshot s)
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ICON, ICON));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		BufferedImage skill = skillImage(s.skill);
		if (s.iconItemId > 0 && itemManager != null)
		{
			AsyncBufferedImage item = itemManager.getImage(s.iconItemId);
			Runnable paint = () -> icon.setIcon(new ImageIcon(withBadge(ImageUtil.resizeImage(item, ICON, ICON), skill)));
			item.onLoaded(paint);
			paint.run();
		}
		else if (skill != null)
		{
			icon.setIcon(new ImageIcon(skill));
		}
		return icon;
	}

	BufferedImage skillImage(String skillName)
	{
		if (skillIcons == null || skillName == null)
		{
			return null;
		}
		try
		{
			return skillIcons.getSkillImage(Skill.valueOf(skillName.toUpperCase(Locale.ROOT)), true);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	static BufferedImage withBadge(BufferedImage item, BufferedImage skill)
	{
		BufferedImage out = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
		Graphics g = out.getGraphics();
		g.drawImage(item, 0, 0, null);
		if (skill != null)
		{
			g.drawImage(skill, ICON - 11, ICON - 11, 11, 11, null);
		}
		g.dispose();
		return out;
	}

	private JPanel tiles(MethodStats.Snapshot s)
	{
		JPanel t = new JPanel(new GridLayout(1, 3, 3, 0));
		t.setOpaque(false);
		t.add(tile(s.xpPerHour < 0 ? "-" : Format.compact(s.xpPerHour), "XP/hr", TEXT,
			"XP per hour in " + s.skill + " while doing this, breaks over 5 minutes left out"));
		if (s.dropping)
		{
			t.add(tile("dropping", "GP/hr", SUBTLE, "You're dropping what you gather, so there's no GP/hr."
				+ " Turn on \"Count dropped items\" to count it anyway."));
		}
		else
		{
			boolean has = s.value != 0 && s.gpPerHour != -1;
			t.add(tile(has ? Format.gp(s.gpPerHour) : "-", "GP/hr", !has ? TEXT : s.gpPerHour < 0 ? LOSS : GOLD,
				"Profit per hour: what you make minus what you use up, banking and walking included."));
		}
		t.add(thirdTile(s));
		return t;
	}

	/**
	 * Catch/hr, Logs/hr, Ore/hr, Runes/hr, Used/hr, Actions/hr or Games/hr.
	 */
	private static JPanel thirdTile(MethodStats.Snapshot s)
	{
		if (s.games > 0)
		{
			return tile(rate(s.gamesPerHour), "Games/hr", TEXT, "Games finished per hour");
		}
		MethodDetector.Kind kind = MethodDetector.KINDS.get(s.skill);
		if (kind == MethodDetector.Kind.USE)
		{
			return tile(rate(s.usedPerHour), "Used/hr", TEXT, "Items used up per hour: logs, bones or planks");
		}
		if (kind == MethodDetector.Kind.ACTION || s.made == 0)
		{
			return tile(rate(s.actionsPerHour), "Actions/hr", TEXT, "XP drops per hour");
		}
		return tile(rate(s.madePerHour), madeLabel(s.skill), TEXT, "Items gathered or made per hour");
	}

	static String madeLabel(String skill)
	{
		switch (skill)
		{
			case "Fishing":
				return "Catch/hr";
			case "Woodcutting":
				return "Logs/hr";
			case "Mining":
				return "Ore/hr";
			case "Runecraft":
				return "Runes/hr";
			case "Hunter":
				return "Caught/hr";
			default:
				return "Made/hr";
		}
	}

	private static String rate(double perHour)
	{
		return perHour < 0 ? "-" : Format.compact(perHour);
	}

	/**
	 * "Lv 87 → 88" and how far through the level you are, as a bar.
	 */
	private static JPanel levelBar(int xp)
	{
		int level = Experience.getLevelForXp(xp);
		if (level >= Format.MAX_LEVEL)
		{
			return null;
		}
		int from = Experience.getXpForLevel(level);
		int to = Experience.getXpForLevel(level + 1);
		double done = (xp - from) / (double) (to - from);

		JPanel wrap = new JPanel(new BorderLayout(0, 3));
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(7, 0, 3, 0));
		JPanel labels = new JPanel(new BorderLayout());
		labels.setOpaque(false);
		labels.add(label("Lv " + level + " → " + (level + 1), FontManager.getRunescapeSmallFont(), MUTED), BorderLayout.WEST);
		labels.add(label(Math.round(done * 100) + "%", FontManager.getRunescapeSmallFont(), MUTED), BorderLayout.EAST);
		wrap.add(labels, BorderLayout.NORTH);
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(TRACK);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(GREEN);
				g.fillRect(0, 0, (int) Math.round(getWidth() * Math.max(0, Math.min(1, done))), getHeight());
			}
		};
		bar.setPreferredSize(new Dimension(0, 5));
		bar.setToolTipText(String.format(Locale.US, "%,d xp to level %d", to - xp, level + 1));
		wrap.add(bar, BorderLayout.CENTER);
		return wrap;
	}

	private List<JPanel> rows(MethodStats.Snapshot s, Integer skillXp)
	{
		List<JPanel> rows = new ArrayList<>();
		// The GP/hr tile is the trip rate; these two only show when banking makes a real difference.
		if (!s.dropping && s.value != 0 && s.spotGpPerHour > 0 && s.gpPerHour > 0 && s.spotGpPerHour > s.gpPerHour * 1.05)
		{
			rows.add(row("Trip (banking incl.)", Format.gp(s.gpPerHour) + " gp/hr", GOLD,
				"Profit per hour over the whole trip, banking and walking included. Updates every 5 ticks, and falls while you're away."));
			rows.add(row("At the spot only", Format.gp(s.spotGpPerHour) + " gp/hr", MUTED,
				"Profit per hour while actually doing it: gaps over 30 seconds left out."));
		}
		// Other skills it trains, one row each: "+ Agility 8.42K xp/hr".
		s.otherXpPerHour.forEach((skill, rate) ->
		{
			if (rate > 0)
			{
				rows.add(row("+ " + skill, Format.compact(rate) + " xp/hr", MUTED, "XP per hour in " + skill + " from this method"));
			}
		});
		if (s.games > 0)
		{
			rows.add(row("Games played", String.format(Locale.US, "%,d", s.games), TEXT, null));
		}
		if (skillXp != null && s.xpPerHour > 0)
		{
			int level = Experience.getLevelForXp(skillXp);
			String next = Format.timeToNextLevel(skillXp, s.xpPerHour);
			if (next != null)
			{
				rows.add(row("Next level", next, GREEN, "At this method's XP/hr"));
			}
			if (level + 1 < Format.MAX_LEVEL)
			{
				String to99 = Format.timeToLevel(skillXp, Format.MAX_LEVEL, s.xpPerHour);
				if (to99 != null)
				{
					rows.add(row("Time to 99", to99, GREEN, "At this method's XP/hr"));
				}
			}
		}
		if (s.bestXpPerHour > 0)
		{
			rows.add(row("Best session", Format.compact(s.bestXpPerHour) + " xp/hr", TEXT,
				"Your best XP/hr for this method in one session of at least 10 minutes"));
		}
		if (!rows.isEmpty())
		{
			// No line under the last row.
			rows.get(rows.size() - 1).setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		}
		return rows;
	}

	// ---- Clicks and menus ---------------------------------------------------------------

	/**
	 * Left-click folds or opens; right-click shows Hide, Reset stats and Remove.
	 */
	private void clickable(JPanel panel, String method, String tooltip)
	{
		Color normal = panel.getBackground();
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		panel.setToolTipText(tooltip + ". Right-click for more.");
		panel.setComponentPopupMenu(menu(method));
		panel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				panel.setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				panel.setBackground(normal);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					toggle(method);
				}
			}
		});
	}

	private JPopupMenu menu(String method)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem hide = new JMenuItem("Hide");
		hide.addActionListener(e -> plugin.hideMethod(method));
		JMenuItem reset = new JMenuItem("Reset stats");
		reset.addActionListener(e ->
		{
			if (confirm("Reset " + method + "'s stats? It stays on the list, starting again from 0."))
			{
				plugin.resetMethod(method);
			}
		});
		JMenuItem remove = new JMenuItem("Remove");
		remove.addActionListener(e ->
		{
			if (confirm("Remove " + method + " and its stats?"))
			{
				plugin.removeMethod(method);
			}
		});
		menu.add(hide);
		menu.add(reset);
		menu.add(remove);
		return menu;
	}

	private boolean confirm(String question)
	{
		return JOptionPane.showConfirmDialog(this, question, "Grindset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}

	private static JPanel empty()
	{
		JLabel text = label("<html><div style='text-align:center;width:160px'><b>No methods yet</b><br><br>"
			+ "Train any skill and a card appears for what you're doing, with XP/hr, GP/hr and time to level.</div></html>",
			FontManager.getRunescapeSmallFont(), MUTED);
		text.setHorizontalAlignment(SwingConstants.CENTER);
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(CARD);
		p.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
		p.add(text, BorderLayout.CENTER);
		return p;
	}

	// ---- Small pieces -----------------------------------------------------------------

	private static JPanel tile(String value, String caption, Color colour, String tooltip)
	{
		JPanel t = new JPanel(new GridLayout(2, 1));
		t.setBackground(SLOT);
		t.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
		JLabel v = new JLabel(value, SwingConstants.CENTER);
		v.setFont(FontManager.getRunescapeBoldFont());
		v.setForeground(colour);
		JLabel c = new JLabel(caption, SwingConstants.CENTER);
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setForeground(MUTED);
		t.add(v);
		t.add(c);
		t.setToolTipText(tooltip);
		return t;
	}

	private static JPanel row(String key, String value, Color colour, String tooltip)
	{
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setOpaque(false);
		r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
			BorderFactory.createEmptyBorder(4, 0, 4, 0)));
		r.add(label(key, FontManager.getRunescapeFont(), MUTED), BorderLayout.WEST);
		JLabel v = label(value, FontManager.getRunescapeFont(), colour);
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		r.add(v, BorderLayout.CENTER);
		r.setToolTipText(tooltip);
		return r;
	}

	/**
	 * A flat button in the sidebar's style.
	 */
	static JLabel flatButton(String text, String tooltip, Runnable onClick)
	{
		JLabel button = new JLabel(text, SwingConstants.CENTER);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(TEXT);
		button.setOpaque(true);
		button.setBackground(CARD);
		button.setToolTipText(tooltip);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(CARD);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (button.contains(e.getPoint()) && e.getButton() == MouseEvent.BUTTON1)
				{
					onClick.run();
				}
			}
		});
		return button;
	}

	static JLabel label(String text, Font font, Color colour)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(colour);
		return l;
	}
}
