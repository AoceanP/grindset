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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * WHAT: The building blocks of the 1.1 sidebar: rounded cards, stat strips, thin bars and
 * small coloured tags.
 * WHY: One place for the look, so the three tabs read the same, as in Combat & XP Tracker 2.2.
 * PRE: Swing thread only.
 * POST: Nothing here touches the game client.
 * RETURN: Not applicable.
 * THROWS: Nothing.
 */
final class Ui
{
	/** Corner radius of cards, and of the boxes inside them. */
	static final int CARD_RADIUS = 12;
	static final int BOX_RADIUS = 8;
	/** A faint divider that shows on the dark sidebar. */
	static final Color LINE = new Color(255, 255, 255, 22);

	private Ui()
	{
	}

	/**
	 * WHAT: A panel painted as a rounded rectangle in its background colour, with an optional outline.
	 * WHY: Rounded cards are the new look; Swing panels are square by default.
	 * PRE: Children should be non-opaque so the rounded corners show.
	 * POST: Paints only itself; children paint as usual.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class RoundPanel extends JPanel
	{
		private final int radius;
		private Color outline;

		RoundPanel(LayoutManager layout, Color background, int radius)
		{
			super(layout);
			this.radius = radius;
			setBackground(background);
			setOpaque(false);
		}

		/**
		 * WHAT: Sets the outline, e.g. green for the method you're doing.
		 * WHY: Marks a card without stripes or icons.
		 * PRE: colour may be null for none.
		 * POST: The panel repaints.
		 * RETURN: this panel, for chaining
		 * THROWS: Nothing.
		 */
		RoundPanel outline(Color colour)
		{
			outline = colour;
			repaint();
			return this;
		}

		Color getOutline()
		{
			return outline;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
			if (outline != null)
			{
				g2.setStroke(new BasicStroke(1.5f));
				g2.setColor(outline);
				g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, radius, radius));
			}
			g2.dispose();
		}
	}

	/**
	 * WHAT: A label painted on a rounded background, for tabs, buttons and tags.
	 * WHY: Square labels looked heavy next to rounded cards.
	 * PRE: Swing thread.
	 * POST: Paints a rounded background behind its text.
	 * RETURN: Not applicable.
	 * THROWS: Nothing.
	 */
	static class RoundLabel extends JLabel
	{
		private final int radius;

		RoundLabel(String text, int radius)
		{
			super(text, SwingConstants.CENTER);
			this.radius = radius;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/**
	 * WHAT: A rounded card with the standard padding.
	 * WHY: Every card in every tab starts the same way.
	 * PRE: layout is the card's layout.
	 * POST: Returns a new card.
	 * RETURN: the card
	 * THROWS: Nothing.
	 */
	static RoundPanel card(LayoutManager layout)
	{
		RoundPanel card = new RoundPanel(layout, GrindsetPanel.CARD, CARD_RADIUS);
		card.setBorder(BorderFactory.createEmptyBorder(6, 7, 7, 8));
		return card;
	}

	/**
	 * WHAT: Several stats in one rounded box, value above caption, split by thin lines.
	 * WHY: Reads as one unit and takes less room than separate tiles.
	 * PRE: values, captions and tooltips have the same length; a tooltip may be null.
	 * POST: Returns a new strip.
	 * RETURN: the strip
	 * THROWS: Nothing.
	 */
	static RoundPanel strip(JLabel[] values, String[] captions, String[] tooltips)
	{
		RoundPanel strip = new RoundPanel(new GridLayout(1, values.length, 0, 0), GrindsetPanel.SLOT, BOX_RADIUS);
		strip.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		for (int i = 0; i < values.length; i++)
		{
			JPanel cell = new JPanel(new GridLayout(2, 1, 0, 0));
			cell.setOpaque(false);
			if (i > 0)
			{
				cell.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, LINE));
			}
			values[i].setHorizontalAlignment(SwingConstants.CENTER);
			JLabel caption = GrindsetPanel.label(captions[i], FontManager.getRunescapeSmallFont(), GrindsetPanel.MUTED);
			caption.setHorizontalAlignment(SwingConstants.CENTER);
			cell.add(values[i]);
			cell.add(caption);
			cell.setToolTipText(tooltips[i]);
			strip.add(cell);
		}
		return strip;
	}

	/**
	 * WHAT: A thin rounded progress bar.
	 * WHY: Level progress and how methods compare read faster as bars.
	 * PRE: fill is 0 to 1 (clamped).
	 * POST: Returns a new bar.
	 * RETURN: the bar
	 * THROWS: Nothing.
	 */
	static JComponent bar(double fill, Color colour, int height)
	{
		double f = Math.max(0, Math.min(1, fill));
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int y = (getHeight() - height) / 2;
				g2.setColor(GrindsetPanel.TRACK);
				g2.fill(new RoundRectangle2D.Float(0, y, getWidth(), height, height, height));
				if (f > 0)
				{
					g2.setColor(colour);
					g2.fill(new RoundRectangle2D.Float(0, y, Math.max(height, (float) (getWidth() * f)), height, height, height));
				}
				g2.dispose();
			}
		};
		bar.setPreferredSize(new Dimension(0, height));
		// The fill, so the panel can tell when a bar changed.
		bar.setName(String.valueOf(Math.round(f * 1000)));
		return bar;
	}

	/**
	 * WHAT: A small rounded tag in a colour, e.g. "now", "away" or "fastest".
	 * WHY: A status reads faster as a coloured tag than as plain text.
	 * PRE: None.
	 * POST: Returns a new tag.
	 * RETURN: the tag
	 * THROWS: Nothing.
	 */
	static RoundLabel pill(String text, Color colour)
	{
		RoundLabel pill = new RoundLabel(text, BOX_RADIUS);
		pill.setFont(FontManager.getRunescapeSmallFont());
		pill.setForeground(colour);
		pill.setBackground(tint(colour, 0.75f));
		pill.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
		return pill;
	}

	/**
	 * WHAT: A colour mixed toward the card background.
	 * WHY: Soft backgrounds for tags, the selected tab and the best cells.
	 * PRE: amount is 0 (the colour) to 1 (the card colour).
	 * POST: Nothing changes.
	 * RETURN: the mixed colour
	 * THROWS: Nothing.
	 */
	static Color tint(Color colour, float amount)
	{
		Color base = GrindsetPanel.CARD;
		return new Color(
			Math.round(colour.getRed() + (base.getRed() - colour.getRed()) * amount),
			Math.round(colour.getGreen() + (base.getGreen() - colour.getGreen()) * amount),
			Math.round(colour.getBlue() + (base.getBlue() - colour.getBlue()) * amount));
	}
}
