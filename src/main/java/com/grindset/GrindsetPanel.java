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
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
	static final Color SLOT = new Color(36, 36, 36);
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

	enum Tab
	{
		METHODS("Methods", "Every method, the one you're doing first"),
		GOALS("Goals", "How long each of your methods takes to reach a level"),
		COMPARE("Compare", "A skill's methods side by side");

		final String label;
		final String tooltip;

		Tab(String label, String tooltip)
		{
			this.label = label;
			this.tooltip = tooltip;
		}
	}

	private Tab tab = Tab.METHODS;
	private final Map<Tab, JLabel> tabLabels = new EnumMap<>(Tab.class);
	private final JPanel controls = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JComboBox<String> goalSkill = new JComboBox<>();
	private final JComboBox<Integer> goalLevel = new JComboBox<>();
	private final JComboBox<String> compareSkill = new JComboBox<>();
	private final JComboBox<String> compareSort = new JComboBox<>(new String[]{"Sort: XP/hr", "Sort: GP/hr", "Sort: Items/hr"});
	// Set while the skill pickers are refilled, so that doesn't redraw the panel halfway.
	private boolean updatingBoxes;
	// The skill each picker should show, kept while the list of skills changes. Saved in the settings.
	private String wantedGoalSkill;
	private String wantedCompareSkill;
	// The tab whose pickers are showing; they only move when the tab changes, so an open dropdown stays open.
	private Tab controlsTab;
	// The open tab is built into this, then only the rows that look different are swapped into the list.
	private final List<Component> pending = new ArrayList<>();
	// The method being done, for opening cards from Goals and Compare.
	private String nowMethod;
	private boolean built;
	private static final String SIGNATURE = "grindset.signature";
	private static final String OWN = "grindset.own";

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
		title.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		title.add(label("Grindset", FontManager.getRunescapeBoldFont(), GOLD), BorderLayout.WEST);
		count.setVerticalAlignment(SwingConstants.BOTTOM);
		title.add(count, BorderLayout.EAST);
		// The three tabs sit in one rounded track, like a switch.
		Ui.RoundPanel tabRow = new Ui.RoundPanel(new GridLayout(1, 3, 2, 0), CARD, Ui.CARD_RADIUS);
		tabRow.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		for (Tab t : Tab.values())
		{
			JLabel label = tabLabel(t);
			tabLabels.put(t, label);
			tabRow.add(label);
		}
		JPanel top = new JPanel(new DynamicGridLayout(0, 1, 0, 8));
		top.setOpaque(false);
		top.add(title);
		top.add(tabRow);
		add(top, BorderLayout.NORTH);

		for (JComboBox<?> box : Arrays.asList(goalSkill, goalLevel, compareSkill, compareSort))
		{
			box.setFocusable(false);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setBackground(CARD);
			box.setForeground(TEXT);
			box.addActionListener(e ->
			{
				if (!updatingBoxes)
				{
					rememberPicks();
					refresh();
				}
			});
		}
		updatingBoxes = true;
		for (int level = 2; level <= Format.MAX_LEVEL; level++)
		{
			goalLevel.addItem(level);
		}
		goalLevel.setSelectedItem(Math.max(2, Math.min(Format.MAX_LEVEL, config.goalLevel())));
		compareSort.setSelectedIndex(Math.max(0, Math.min(compareSort.getItemCount() - 1, config.compareSort())));
		wantedGoalSkill = blankToNull(config.goalSkill());
		wantedCompareSkill = blankToNull(config.compareSkill());
		updatingBoxes = false;
		goalLevel.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				return super.getListCellRendererComponent(list, value == null ? "" : "Level " + value, index, selected, focus);
			}
		});
		goalLevel.setToolTipText("The level to reach");
		goalSkill.setToolTipText("The skill to plan");
		compareSkill.setToolTipText("The skill whose methods to compare");
		compareSort.setToolTipText("What to sort the methods by");
		controls.setOpaque(false);
		list.setOpaque(false);
		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setOpaque(false);
		content.add(controls, BorderLayout.NORTH);
		content.add(list, BorderLayout.CENTER);
		add(content, BorderLayout.CENTER);

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
		// Feedback goes to GitHub issues, like Combat & XP Tracker; it sits at the bottom, out of the way.
		JPanel feedback = new JPanel(new GridLayout(1, 2, 4, 0));
		feedback.setOpaque(false);
		feedback.add(flatButton("Report bug", "Opens the plugin's GitHub page to report a bug",
			() -> LinkBrowser.browse(ISSUES + "?labels=bug")));
		feedback.add(flatButton("Suggest", "Opens the plugin's GitHub page to suggest a feature",
			() -> LinkBrowser.browse(ISSUES + "?labels=enhancement")));
		JPanel bottom = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		bottom.setOpaque(false);
		bottom.add(hiddenLabel);
		bottom.add(feedback);
		add(bottom, BorderLayout.SOUTH);
		refresh();
	}

	/**
	 * WHAT: Redraws the tab that's open when the panel opens again.
	 * WHY: Refreshes are skipped while the panel is hidden.
	 * PRE: Called by RuneLite on the Swing thread when the panel is shown.
	 * POST: The open tab is up to date.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	@Override
	public void onActivate()
	{
		viewKey = null;
		SwingUtilities.invokeLater(this::refresh);
	}

	/**
	 * Redraws the tab that's open. Swing thread; reads nothing from the game. Does nothing while
	 * the panel is hidden; onActivate redraws it when it opens.
	 */
	void refresh()
	{
		if (built && !isShowing())
		{
			viewKey = null;
			return;
		}
		built = true;
		List<MethodStats.Snapshot> methods = new ArrayList<>();
		Set<String> hidden = plugin.hiddenMethods();
		int hiddenCount = 0;
		for (MethodStats.Snapshot s : plugin.getMethods())
		{
			if (hidden.contains(s.name))
			{
				hiddenCount++;
			}
			else
			{
				methods.add(s);
			}
		}
		Map<String, Integer> xp = plugin.getCurrentXp();
		updateSkillBoxes(methods);
		String key = tab + "|" + plugin.getRateTick() + "|" + hidden + "|" + config.price() + "|" + config.countDropped() + "|"
			+ xp.hashCode() + "|" + toggled + "|" + goalSkill.getSelectedItem() + "|" + goalLevel.getSelectedItem() + "|"
			+ compareSkill.getSelectedItem() + "|" + compareSort.getSelectedItem() + "|" + summary(methods);
		if (key.equals(viewKey))
		{
			return;
		}
		viewKey = key;
		nowMethod = !methods.isEmpty() && methods.get(0).now ? methods.get(0).name : null;

		for (Map.Entry<Tab, JLabel> t : tabLabels.entrySet())
		{
			styleTab(t.getValue(), t.getKey() == tab);
		}
		if (tab != controlsTab)
		{
			controlsTab = tab;
			controls.removeAll();
			if (tab == Tab.GOALS)
			{
				controls.add(pickers(goalSkill, goalLevel));
			}
			else if (tab == Tab.COMPARE)
			{
				controls.add(pickers(compareSkill, compareSort));
			}
		}
		pending.clear();
		switch (tab)
		{
			case GOALS:
				showGoals(methods, xp);
				break;
			case COMPARE:
				showCompare(methods);
				break;
			default:
				for (int i = 0; i < methods.size(); i++)
				{
					MethodStats.Snapshot s = methods.get(i);
					if (i == 1 && methods.get(0).now)
					{
						// The one you're doing is first; the rest sit under a small heading.
						pending.add(heading("Other methods \u00b7 " + (methods.size() - 1)));
					}
					boolean open = s.now != toggled.contains(s.name);
					pending.add(open ? card(s, xp.get(s.skill)) : folded(s));
				}
				if (methods.isEmpty())
				{
					pending.add(empty());
				}
		}
		sync(list, pending);
		pending.clear();
		controls.setVisible(controls.getComponentCount() > 0);
		count.setText(methods.size() == 1 ? "1 method" : methods.size() + " methods");
		hiddenLabel.setText(hiddenCount == 0 ? "" : hiddenCount + " hidden. Click to show.");
		hiddenLabel.setVisible(hiddenCount > 0 && tab == Tab.METHODS);
		revalidate();
		repaint();
	}

	// ---- Tabs -----------------------------------------------------------------------

	/**
	 * WHAT: One of the three tabs, a rounded label inside the tab track.
	 * WHY: Methods, Goals and Compare switch the whole list.
	 * PRE: Swing thread.
	 * POST: Clicking it opens that tab.
	 * RETURN: the tab
	 * THROWS: Nothing.
	 */
	private JLabel tabLabel(Tab t)
	{
		Ui.RoundLabel label = new Ui.RoundLabel(t.label, Ui.BOX_RADIUS);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setPreferredSize(new Dimension(1, 24));
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.setToolTipText(t.tooltip);
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				tab = t;
				refresh();
			}
		});
		return label;
	}

	/**
	 * WHAT: Styles a tab as selected (gold on a soft gold background) or not.
	 * WHY: The open tab must stand out without a heavy underline.
	 * PRE: label came from tabLabel.
	 * POST: The tab repaints.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private static void styleTab(JLabel label, boolean selected)
	{
		label.setForeground(selected ? GOLD : MUTED);
		label.setBackground(selected ? Ui.tint(GOLD, 0.82f) : CARD);
		label.repaint();
	}

	/**
	 * Skills that have methods, most recent first, for the Goals and Compare pickers. Kept as
	 * they are when nothing changed, so a choice isn't lost.
	 */
	private void updateSkillBoxes(List<MethodStats.Snapshot> methods)
	{
		List<String> skills = new ArrayList<>();
		for (MethodStats.Snapshot s : methods)
		{
			if (!skills.contains(s.skill))
			{
				skills.add(s.skill);
			}
		}
		updatingBoxes = true;
		for (JComboBox<String> box : Arrays.asList(goalSkill, compareSkill))
		{
			List<String> have = new ArrayList<>();
			for (int i = 0; i < box.getItemCount(); i++)
			{
				have.add(box.getItemAt(i));
			}
			String wanted = box == goalSkill ? wantedGoalSkill : wantedCompareSkill;
			if (!have.equals(skills) || wanted != null && skills.contains(wanted) && !wanted.equals(box.getSelectedItem()))
			{
				Object chosen = box.getSelectedItem();
				box.removeAllItems();
				skills.forEach(box::addItem);
				if (wanted != null && skills.contains(wanted))
				{
					box.setSelectedItem(wanted);
				}
				else if (chosen != null && skills.contains(chosen))
				{
					box.setSelectedItem(chosen);
				}
			}
		}
		updatingBoxes = false;
	}

	/**
	 * WHAT: Saves the Goals and Compare picks after you change one.
	 * WHY: They used to reset to the first skill and level 99 every time RuneLite started.
	 * PRE: Swing thread; not while the pickers are being refilled.
	 * POST: The picks are in the settings and kept while the skill list changes.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	private void rememberPicks()
	{
		if (goalSkill.getSelectedItem() != null)
		{
			wantedGoalSkill = (String) goalSkill.getSelectedItem();
		}
		if (compareSkill.getSelectedItem() != null)
		{
			wantedCompareSkill = (String) compareSkill.getSelectedItem();
		}
		Integer level = (Integer) goalLevel.getSelectedItem();
		plugin.savePicks(wantedGoalSkill, level == null ? Format.MAX_LEVEL : level, wantedCompareSkill,
			Math.max(0, compareSort.getSelectedIndex()));
	}

	private static String blankToNull(String s)
	{
		return s == null || s.trim().isEmpty() ? null : s.trim();
	}

	private JPanel pickers(JComboBox<?> left, JComboBox<?> right)
	{
		JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
		row.setOpaque(false);
		row.add(left);
		row.add(right);
		return row;
	}

	/**
	 * Opens a method's card on the Methods tab.
	 */
	private void openMethod(String method)
	{
		tab = Tab.METHODS;
		// Open unless it's the one you're doing, which is open already.
		if (method.equals(nowMethod))
		{
			toggled.remove(method);
		}
		else
		{
			toggled.add(method);
		}
		refresh();
	}

	// ---- Goals: how long each method takes to a level ----------------------------------

	private void showGoals(List<MethodStats.Snapshot> methods, Map<String, Integer> xp)
	{
		String skill = (String) goalSkill.getSelectedItem();
		Integer target = (Integer) goalLevel.getSelectedItem();
		Integer have = skill == null ? null : xp.get(skill);
		if (skill == null || target == null || have == null)
		{
			pending.add(message("No goals yet", "Train a skill for a minute and plan your levels here."));
			return;
		}
		int level = Experience.getLevelForXp(have);
		int toGo = Experience.getXpForLevel(target) - have;
		if (toGo <= 0)
		{
			pending.add(message("Already there", "You're level " + level + " " + skill + ". Pick a higher level."));
			return;
		}

		List<MethodStats.Snapshot> ranked = new ArrayList<>();
		for (MethodStats.Snapshot s : methods)
		{
			if (s.skill.equals(skill) && s.xpPerHour > 0)
			{
				ranked.add(s);
			}
		}
		ranked.sort((a, b) -> Double.compare(b.xpPerHour, a.xpPerHour));

		pending.add(Ui.strip(
			new JLabel[]{
				label(level + " \u2192 " + target, FontManager.getRunescapeBoldFont(), TEXT),
				label(Format.compact(toGo), FontManager.getRunescapeBoldFont(), TEXT),
				label(ranked.isEmpty() ? "-" : Format.duration(toGo / ranked.get(0).xpPerHour), FontManager.getRunescapeBoldFont(), GREEN)},
			new String[]{"Level", "XP to go", "Fastest"},
			new String[]{"Your " + skill + " level", String.format(Locale.US, "%,d XP to level %d", toGo, target),
				"Hours of play with your fastest method"}));
		if (ranked.isEmpty())
		{
			pending.add(message("No rates yet", "Train " + skill + " for a minute to see how long each method takes."));
			return;
		}
		pending.add(heading("Your methods, fastest first"));
		double fastest = toGo / ranked.get(0).xpPerHour;
		for (int i = 0; i < ranked.size(); i++)
		{
			pending.add(goalRow(ranked.get(i), toGo, i, fastest));
		}
		pending.add(footnote("Times use your own XP/hr. The bar compares each method's speed with the fastest; gold is"
			+ " the profit on the way."));
	}

	/**
	 * WHAT: One method on the Goals tab: how long it takes to the level, how it compares with the
	 * fastest, and what you'd make on the way.
	 * WHY: Picking a method for a goal is a trade between time and gold.
	 * PRE: s.xpPerHour is above 0; fastest is the fastest method's hours.
	 * POST: Clicking the card opens the method on the Methods tab.
	 * RETURN: the card
	 * THROWS: Nothing.
	 */
	private JPanel goalRow(MethodStats.Snapshot s, int toGo, int rank, double fastest)
	{
		double hours = toGo / s.xpPerHour;
		boolean best = rank == 0;
		Ui.RoundPanel row = Ui.card(new BorderLayout(0, 5));
		if (best)
		{
			row.outline(GREEN);
		}
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		top.add(slot(icon(s)), BorderLayout.WEST);

		JPanel names = new JPanel(new GridLayout(2, 1));
		names.setOpaque(false);
		names.add(label(s.name, FontManager.getRunescapeFont(), TEXT));
		JPanel sub = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		sub.setOpaque(false);
		sub.add(label(Format.compact(s.xpPerHour) + "/hr", FontManager.getRunescapeSmallFont(), SUBTLE));
		if (!s.dropping && s.value != 0 && s.gpPerHour != -1)
		{
			double made = s.gpPerHour * hours;
			sub.add(label("  \u00b7  ", FontManager.getRunescapeSmallFont(), SUBTLE));
			sub.add(label((made >= 0 ? "+" : "") + Format.gp(made), FontManager.getRunescapeSmallFont(), made >= 0 ? GOLD : LOSS));
		}
		names.add(sub);
		top.add(names, BorderLayout.CENTER);

		JPanel time = new JPanel(new GridLayout(2, 1));
		time.setOpaque(false);
		JLabel t = label(Format.duration(hours), FontManager.getRunescapeBoldFont(), best ? GREEN : TEXT);
		t.setHorizontalAlignment(SwingConstants.RIGHT);
		t.setToolTipText("Hours of play at this method's XP/hr");
		time.add(t);
		JPanel tag = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		tag.setOpaque(false);
		tag.add(best ? Ui.pill("fastest", GREEN) : label("#" + (rank + 1), FontManager.getRunescapeSmallFont(), SUBTLE));
		time.add(tag);
		top.add(time, BorderLayout.EAST);
		row.add(top, BorderLayout.NORTH);

		JComponent speed = Ui.bar(fastest / hours, best ? GREEN : MUTED, 4);
		speed.setToolTipText(best ? "The fastest of your methods"
			: String.format(Locale.US, "%d%% of the fastest method's speed", Math.round(fastest / hours * 100)));
		row.add(speed, BorderLayout.CENTER);
		openOnClick(row, s);
		return row;
	}

	// ---- Compare: a skill's methods side by side -------------------------------------

	private void showCompare(List<MethodStats.Snapshot> methods)
	{
		String skill = (String) compareSkill.getSelectedItem();
		List<MethodStats.Snapshot> rows = new ArrayList<>();
		for (MethodStats.Snapshot s : methods)
		{
			if (s.skill.equals(skill))
			{
				rows.add(s);
			}
		}
		if (rows.isEmpty())
		{
			pending.add(message("Nothing to compare yet", "Train a skill two different ways and compare them here."));
			return;
		}
		int sort = compareSort.getSelectedIndex();
		rows.sort((a, b) -> Double.compare(column(b, sort), column(a, sort)));
		double[] best = new double[3];
		for (MethodStats.Snapshot s : rows)
		{
			for (int c = 0; c < 3; c++)
			{
				best[c] = Math.max(best[c], column(s, c));
			}
		}

		JPanel heads = new JPanel(new GridLayout(1, 3, 3, 0));
		heads.setOpaque(false);
		heads.setBorder(BorderFactory.createEmptyBorder(2, 7, 0, 8));
		String[] titles = {"XP/hr", "GP/hr", "Items/hr"};
		for (int c = 0; c < titles.length; c++)
		{
			JLabel l = label(titles[c], FontManager.getRunescapeSmallFont(), c == sort ? GOLD : MUTED);
			l.setToolTipText(c == sort ? "Sorted by this, highest first" : null);
			l.setHorizontalAlignment(SwingConstants.CENTER);
			heads.add(l);
		}
		pending.add(heads);
		for (MethodStats.Snapshot s : rows)
		{
			pending.add(compareRow(s, best, rows.size() > 1));
		}
		pending.add(footnote("The best in each column is highlighted. Click a method to open its card."));
	}

	/**
	 * A column's number for sorting and highlighting: XP/hr, GP/hr or items per hour.
	 */
	private static double column(MethodStats.Snapshot s, int column)
	{
		switch (column)
		{
			case 1:
				return s.dropping || s.value == 0 ? -1 : s.gpPerHour;
			case 2:
				return itemsPerHour(s);
			default:
				return s.xpPerHour;
		}
	}

	/**
	 * Items gathered, made, used or actions per hour, whichever the method is about.
	 */
	private static double itemsPerHour(MethodStats.Snapshot s)
	{
		MethodDetector.Kind kind = MethodDetector.KINDS.get(s.skill);
		if (kind == MethodDetector.Kind.USE)
		{
			return s.usedPerHour;
		}
		return kind == MethodDetector.Kind.ACTION || s.made == 0 ? s.actionsPerHour : s.madePerHour;
	}

	private JPanel compareRow(MethodStats.Snapshot s, double[] best, boolean highlight)
	{
		Ui.RoundPanel row = Ui.card(new BorderLayout(0, 5));
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		top.add(slot(icon(s)), BorderLayout.WEST);
		top.add(names(s, false, false), BorderLayout.CENTER);
		row.add(top, BorderLayout.NORTH);

		JPanel cells = new JPanel(new GridLayout(1, 3, 3, 0));
		cells.setOpaque(false);
		cells.add(cell(s.xpPerHour < 0 ? "-" : Format.compact(s.xpPerHour), TEXT, highlight && s.xpPerHour > 0 && s.xpPerHour >= best[0]));
		double gp = column(s, 1);
		cells.add(cell(s.dropping ? "dropping" : gp == -1 ? "-" : Format.gp(gp), s.dropping ? SUBTLE : gp < 0 ? LOSS : GOLD,
			highlight && gp > 0 && gp >= best[1]));
		double items = itemsPerHour(s);
		cells.add(cell(items < 0 ? "-" : Format.compact(items), TEXT, highlight && items > 0 && items >= best[2]));
		row.add(cells, BorderLayout.CENTER);
		openOnClick(row, s);
		return row;
	}

	/**
	 * WHAT: One number in a Compare row, in a small rounded box.
	 * WHY: The best in each column gets a soft green box and bold text so it stands out.
	 * PRE: None.
	 * POST: Returns a new cell.
	 * RETURN: the cell
	 * THROWS: Nothing.
	 */
	private static JPanel cell(String value, Color colour, boolean best)
	{
		Ui.RoundPanel c = new Ui.RoundPanel(new BorderLayout(), best ? Ui.tint(GREEN, 0.8f) : SLOT, Ui.BOX_RADIUS);
		c.setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 2));
		if (best)
		{
			c.outline(Ui.tint(GREEN, 0.45f));
		}
		JLabel l = new JLabel(value, SwingConstants.CENTER);
		l.setFont(best ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		l.setForeground(colour);
		c.add(l);
		return c;
	}

	private void openOnClick(JPanel row, MethodStats.Snapshot s)
	{
		row.setName(s.name);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(s.name + ". Click to open its card.");
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(CARD);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					openMethod(s.name);
				}
			}
		});
	}

	/**
	 * WHAT: A centred message card, e.g. "No goals yet".
	 * WHY: Empty tabs explain what to do instead of showing nothing.
	 * PRE: None.
	 * POST: Returns a new card.
	 * RETURN: the card
	 * THROWS: Nothing.
	 */
	private static JPanel message(String title, String body)
	{
		JLabel text = label("<html><div style='text-align:center;width:160px'><b>" + title + "</b><br><br>" + body + "</div></html>",
			FontManager.getRunescapeSmallFont(), MUTED);
		text.setHorizontalAlignment(SwingConstants.CENTER);
		Ui.RoundPanel p = Ui.card(new BorderLayout());
		p.setBorder(BorderFactory.createEmptyBorder(16, 10, 16, 10));
		p.add(text, BorderLayout.CENTER);
		return p;
	}

	/**
	 * WHAT: A small grey heading over part of a list.
	 * WHY: Splits the method you're doing from the rest, and labels the Goals list.
	 * PRE: None.
	 * POST: Returns a new label.
	 * RETURN: the heading
	 * THROWS: Nothing.
	 */
	private static JLabel heading(String text)
	{
		JLabel l = label(text, FontManager.getRunescapeSmallFont(), MUTED);
		l.setBorder(BorderFactory.createEmptyBorder(4, 3, 0, 0));
		return l;
	}

	/**
	 * WHAT: A short note under a list, wrapped to the sidebar.
	 * WHY: Explains how the numbers are worked out.
	 * PRE: None.
	 * POST: Returns a new label.
	 * RETURN: the note
	 * THROWS: Nothing.
	 */
	private static JLabel footnote(String text)
	{
		JLabel l = label("<html><div style='width:155px'>" + text + "</div></html>", FontManager.getRunescapeSmallFont(), SUBTLE);
		l.setBorder(BorderFactory.createEmptyBorder(2, 3, 0, 0));
		return l;
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

	/**
	 * WHAT: Puts freshly built rows into the list, keeping every part of the old rows that looks the same.
	 * WHY: A part that stays put keeps its tooltip, right-click menu and hover; rebuilding the whole
	 * list every 3 seconds closed them while you were skilling. Inside the card you're doing, only
	 * the numbers that changed are swapped.
	 * PRE: Swing thread; fresh rows have no parent yet.
	 * POST: target shows rows that look like fresh, in the same order.
	 * RETURN: Nothing.
	 * THROWS: Nothing.
	 */
	static void sync(JPanel target, List<Component> fresh)
	{
		for (int i = 0; i < fresh.size(); i++)
		{
			Component next = fresh.get(i);
			stamp(next);
			if (i < target.getComponentCount())
			{
				if (merge(target.getComponent(i), next))
				{
					continue;
				}
				target.remove(i);
			}
			target.add(next, i);
		}
		while (target.getComponentCount() > fresh.size())
		{
			target.remove(target.getComponentCount() - 1);
		}
	}

	/**
	 * WHAT: Makes an old part look like a fresh one, keeping the old part and any of its children
	 * that look the same.
	 * WHY: Only what changed is swapped, so tooltips and menus on the rest stay open.
	 * PRE: Both were stamped; old is in the list, next has no parent.
	 * POST: On true, old looks like next; on false nothing changed.
	 * RETURN: false if old must be replaced: a different method, colour, tooltip or shape
	 * THROWS: Nothing.
	 */
	private static boolean merge(Component old, Component next)
	{
		if (!(old instanceof JComponent) || !(next instanceof JComponent))
		{
			return false;
		}
		JComponent o = (JComponent) old;
		JComponent n = (JComponent) next;
		if (Objects.equals(o.getClientProperty(SIGNATURE), n.getClientProperty(SIGNATURE)))
		{
			return true;
		}
		if (!Objects.equals(o.getClientProperty(OWN), n.getClientProperty(OWN)) || o instanceof JLabel
			|| o.getComponentCount() != n.getComponentCount() || o.getLayout() == null
			|| n.getLayout() == null || o.getLayout().getClass() != n.getLayout().getClass())
		{
			return false;
		}
		Component[] kids = n.getComponents();
		for (int i = 0; i < kids.length; i++)
		{
			Component oldKid = o.getComponent(i);
			if (!merge(oldKid, kids[i]))
			{
				Object where = n.getLayout() instanceof BorderLayout ? ((BorderLayout) n.getLayout()).getConstraints(kids[i]) : null;
				o.remove(i);
				o.add(kids[i], where, i);
			}
		}
		o.putClientProperty(SIGNATURE, n.getClientProperty(SIGNATURE));
		return true;
	}

	/**
	 * WHAT: Records what a part and each of its children show, as text: text, colours, tooltips,
	 * outlines and bars.
	 * WHY: Two parts with the same text look the same, so the old one can stay. Recorded before
	 * the part goes into the list, so colours it would inherit from there don't creep in.
	 * PRE: c has no parent.
	 * POST: Every JComponent in c holds its own and its whole signature.
	 * RETURN: c's whole signature
	 * THROWS: Nothing.
	 */
	static String stamp(Component c)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(c.getClass().getName()).append('|').append(c.getName()).append('|').append(c.getForeground())
			.append('|').append(c.getBackground()).append('|').append(c.getFont());
		if (c instanceof JLabel)
		{
			sb.append('|').append(((JLabel) c).getText());
		}
		if (c instanceof JComponent)
		{
			sb.append('|').append(((JComponent) c).getToolTipText());
		}
		if (c instanceof Ui.RoundPanel)
		{
			sb.append('|').append(((Ui.RoundPanel) c).getOutline());
		}
		String own = sb.toString();
		if (c instanceof Container)
		{
			sb.append('[');
			for (Component child : ((Container) c).getComponents())
			{
				sb.append(stamp(child));
			}
			sb.append(']');
		}
		String whole = sb.toString();
		if (c instanceof JComponent)
		{
			((JComponent) c).putClientProperty(OWN, own);
			((JComponent) c).putClientProperty(SIGNATURE, whole);
		}
		return whole;
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
		Ui.RoundPanel row = Ui.card(new BorderLayout(8, 0));
		row.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 8));
		row.add(slot(icon(s)), BorderLayout.WEST);
		row.add(names(s, false, false), BorderLayout.CENTER);

		JPanel rates = new JPanel(new GridLayout(2, 1));
		rates.setOpaque(false);
		JLabel xpRate = label(s.xpPerHour < 0 ? "-" : Format.compact(s.xpPerHour) + " XP", FontManager.getRunescapeSmallFont(), TEXT);
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
		return s.value != 0 && s.gpPerHour != -1 ? Format.gp(s.gpPerHour) + " GP" : "";
	}

	private static Color gpColour(MethodStats.Snapshot s)
	{
		return s.dropping ? SUBTLE : s.gpPerHour < 0 ? LOSS : GOLD;
	}

	// ---- Open: the whole card ---------------------------------------------------------------

	private JPanel card(MethodStats.Snapshot s, Integer skillXp)
	{
		Ui.RoundPanel card = Ui.card(new BorderLayout(0, 6));
		card.setBorder(BorderFactory.createEmptyBorder(7, 7, 8, 8));
		if (s.now)
		{
			card.outline(GREEN);
		}

		// The head is see-through, so hovering it lights the whole card.
		JPanel head = new JPanel(new BorderLayout(8, 0))
		{
			@Override
			public void setBackground(Color c)
			{
				super.setBackground(c);
				if (getParent() != null && c != null)
				{
					getParent().setBackground(c);
				}
			}
		};
		head.setOpaque(false);
		head.setBackground(CARD);
		head.add(slot(icon(s)), BorderLayout.WEST);
		head.add(names(s, true, true), BorderLayout.CENTER);
		if (s.now || s.away)
		{
			JPanel status = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
			status.setOpaque(false);
			status.add(Ui.pill(s.now ? "now" : "away", s.now ? GREEN : MUTED));
			head.add(status, BorderLayout.EAST);
		}
		clickable(head, s.name, "Click to fold");
		card.add(head, BorderLayout.NORTH);

		JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		body.setOpaque(false);
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
		card.setName(s.name);
		card.setComponentPopupMenu(menu(s.name));
		return card;
	}

	/**
	 * WHAT: The method's name, with its skill and spot underneath so a long name never hides them.
	 * WHY: Compare already picks one skill, so there it shows only the spot.
	 * PRE: None.
	 * POST: Returns a new panel.
	 * RETURN: the two lines
	 * THROWS: Nothing.
	 */
	private static JPanel names(MethodStats.Snapshot s, boolean bold, boolean withSkill)
	{
		JPanel names = new JPanel(new GridLayout(2, 1));
		names.setOpaque(false);
		names.add(label(s.name, bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont(), TEXT));
		String where = s.area == null ? s.skill : withSkill ? s.skill + " \u00b7 " + s.area : s.area;
		names.add(label(where, FontManager.getRunescapeSmallFont(), SUBTLE));
		return names;
	}

	/**
	 * WHAT: The method's icon in a small rounded slot, like an inventory square.
	 * WHY: Gives every card the same tidy left edge, whatever the item's shape.
	 * PRE: icon came from icon().
	 * POST: Returns a new slot holding the icon.
	 * RETURN: the slot
	 * THROWS: Nothing.
	 */
	private static JPanel slot(JComponent icon)
	{
		Ui.RoundPanel slot = new Ui.RoundPanel(new BorderLayout(), SLOT, Ui.BOX_RADIUS);
		slot.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		slot.add(icon, BorderLayout.CENTER);
		JPanel holder = new JPanel(new GridBagLayout());
		holder.setOpaque(false);
		holder.add(slot);
		return holder;
	}

	/**
	 * The method's item, with its skill's icon in the corner.
	 */
	JComponent icon(MethodStats.Snapshot s)
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ICON, ICON));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setName(s.iconItemId + s.skill);
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
		Ui.RoundPanel t = new Ui.RoundPanel(new GridLayout(1, 3, 0, 0), SLOT, Ui.BOX_RADIUS);
		t.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
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
		// Thin lines between the three numbers, so they read as one strip.
		for (int i = 1; i < t.getComponentCount(); i++)
		{
			((JComponent) t.getComponent(i)).setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Ui.LINE));
		}
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
		wrap.setBorder(BorderFactory.createEmptyBorder(8, 1, 4, 1));
		JPanel labels = new JPanel(new BorderLayout());
		labels.setOpaque(false);
		labels.add(label("Level " + level, FontManager.getRunescapeSmallFont(), TEXT), BorderLayout.WEST);
		labels.add(label(Math.round(done * 100) + "% to " + (level + 1), FontManager.getRunescapeSmallFont(), MUTED), BorderLayout.EAST);
		wrap.add(labels, BorderLayout.NORTH);
		JComponent bar = Ui.bar(done, GREEN, 6);
		bar.setToolTipText(String.format(Locale.US, "%,d XP to level %d", to - xp, level + 1));
		wrap.add(bar, BorderLayout.CENTER);
		return wrap;
	}

	private List<JPanel> rows(MethodStats.Snapshot s, Integer skillXp)
	{
		List<JPanel> rows = new ArrayList<>();
		// The GP/hr tile is the trip rate; these two only show when banking makes a real difference.
		if (!s.dropping && s.value != 0 && s.spotGpPerHour > 0 && s.gpPerHour > 0 && s.spotGpPerHour > s.gpPerHour * 1.05)
		{
			rows.add(row("Trip (banking incl.)", Format.gp(s.gpPerHour) + " GP/hr", GOLD,
				"Profit per hour over the whole trip, banking and walking included. Updates every 5 ticks, and falls while you're away."));
			rows.add(row("At the spot only", Format.gp(s.spotGpPerHour) + " GP/hr", MUTED,
				"Profit per hour while actually doing it: gaps over 30 seconds left out."));
		}
		// Other skills it trains, one row each: "+ Agility 8.42K XP/hr".
		s.otherXpPerHour.forEach((skill, rate) ->
		{
			if (rate > 0)
			{
				rows.add(row("+ " + skill, Format.compact(rate) + " XP/hr", MUTED, "XP per hour in " + skill + " from this method"));
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
				rows.add(row("Next level", next, GREEN, "Hours of play at this method's XP/hr"));
			}
			if (level + 1 < Format.MAX_LEVEL)
			{
				String to99 = Format.timeToLevel(skillXp, Format.MAX_LEVEL, s.xpPerHour);
				if (to99 != null)
				{
					rows.add(row("Time to 99", to99, GREEN, "Hours of play at this method's XP/hr"));
				}
			}
		}
		if (s.bestXpPerHour > 0)
		{
			rows.add(row("Best session", Format.compact(s.bestXpPerHour) + " XP/hr", TEXT,
				"Your best XP/hr for this method in one session of at least 10 minutes"));
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
		panel.setName(method);
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
		Ui.RoundPanel p = Ui.card(new BorderLayout());
		p.setBorder(BorderFactory.createEmptyBorder(16, 10, 16, 10));
		p.add(text, BorderLayout.CENTER);
		return p;
	}

	// ---- Small pieces -----------------------------------------------------------------

	private static JPanel tile(String value, String caption, Color colour, String tooltip)
	{
		JPanel t = new JPanel(new GridLayout(2, 1));
		t.setOpaque(false);
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
		r.setBorder(BorderFactory.createEmptyBorder(2, 1, 2, 1));
		r.add(label(key, FontManager.getRunescapeSmallFont(), MUTED), BorderLayout.WEST);
		JLabel v = label(value, FontManager.getRunescapeSmallFont(), colour);
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
		JLabel button = new Ui.RoundLabel(text, Ui.BOX_RADIUS);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(MUTED);
		button.setBackground(CARD);
		button.setToolTipText(tooltip);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
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
