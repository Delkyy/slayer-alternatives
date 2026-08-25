/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

class SlayerAltsPanel extends PluginPanel
{
	// Same palette as the everykill panel. Delk's plugins should look like they came
	// from the same person, and these sit close enough to ColorScheme that the panel
	// still reads as runelite rather than a website bolted into the client.
	//   PANEL    #16181d  row background
	//   BG_ALT   #101216  title strip and nested detail, darker than the row
	//   LINE     #23262d  rules
	//   FG       #e8eaed  the thing itself
	//   FG_DIM   #9aa0a8  supporting numbers
	//   FG_FAINT #63696f  headings, absent values
	//   ACC      #d94f2b  rs-adjacent rust, not jagex gold
	private static final Color PANEL = new Color(0x16, 0x18, 0x1d);
	private static final Color BG_ALT = new Color(0x10, 0x12, 0x16);
	private static final Color LINE = new Color(0x23, 0x26, 0x2d);
	private static final Color FG = new Color(0xe8, 0xea, 0xed);
	private static final Color FG_DIM = new Color(0x9a, 0xa0, 0xa8);
	private static final Color FG_FAINT = new Color(0x63, 0x69, 0x6f);
	private static final Color ACC = new Color(0xd9, 0x4f, 0x2b);
	private static final Color GOOD = new Color(0x4f, 0x9d, 0x5d);

	private static final Color HOVER = new Color(0x1c, 0x1f, 0x25);

	private final SlayerAltsConfig config;
	private final ItemManager itemManager;
	private final Icons icons;

	private final IconTextField search = new IconTextField();
	private final JPanel results = new JPanel();
	private final JLabel status = new JLabel();

	private TaskBook book;
	private String currentTaskName;
	private SlayerTask currentTask;

	@Inject
	SlayerAltsPanel(SlayerAltsConfig config, ItemManager itemManager, Gson gson)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.icons = Icons.load(gson);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 6, 8, 6));
		setBackground(PANEL);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 12, 26));
		search.setBackground(BG_ALT);
		search.setHoverBackgroundColor(HOVER);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(FG_FAINT);
		status.setBorder(new EmptyBorder(7, 2, 5, 2));

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(PANEL);

		// BoxLayout stretches its children to fill leftover vertical space, which
		// squashed the last line of an expanded row. NORTH in a BorderLayout gives the
		// column its preferred height and leaves the slack below it.
		JPanel resultsHolder = new JPanel(new BorderLayout());
		resultsHolder.setBackground(PANEL);
		resultsHolder.add(results, BorderLayout.NORTH);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(PANEL);
		top.add(search, BorderLayout.NORTH);
		top.add(status, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(resultsHolder, BorderLayout.CENTER);
	}

	void init(TaskBook book)
	{
		this.book = book;
		SwingUtilities.invokeLater(this::rebuild);
	}

	void setCurrentTask(String name, SlayerTask task)
	{
		this.currentTaskName = name;
		this.currentTask = task;
		rebuild();
	}

	private void rebuild()
	{
		if (book == null)
		{
			return;
		}

		results.removeAll();
		String q = search.getText().trim();

		if (q.isEmpty())
		{
			if (currentTask != null)
			{
				List<Option> opts = Option.from(currentTask, config.sortByXp());
				status.setText("YOUR TASK \u00b7 " + countable(opts) + " OPTIONS");
				results.add(taskBox(currentTask, true));
			}
			else if (currentTaskName != null)
			{
				status.setText("NO DATA FOR " + currentTaskName.toUpperCase());
				results.add(hint("Search for another task above."));
			}
			else
			{
				status.setText(book.all().size() + " TASKS");
				results.add(hint("Get a slayer task, or search above."));
			}
		}
		else
		{
			List<SlayerTask> hits = book.search(q);
			status.setText(hits.size() + (hits.size() == 1 ? " MATCH" : " MATCHES"));
			if (hits.isEmpty())
			{
				results.add(hint("Nothing matches \"" + q + "\"."));
			}
			for (SlayerTask t : hits)
			{
				results.add(taskBox(t, hits.size() == 1));
			}
		}

		results.revalidate();
		results.repaint();
	}

	private static int countable(List<Option> opts)
	{
		int n = 0;
		for (Option o : opts)
		{
			if (!o.isSuperior())
			{
				n++;
			}
		}
		return n;
	}

	private JLabel hint(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(FG_FAINT);
		l.setBorder(new EmptyBorder(10, 2, 0, 2));
		return l;
	}

	private JPanel taskBox(SlayerTask task, boolean expanded)
	{
		List<Option> options = Option.from(task, config.sortByXp());

		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(PANEL);
		box.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		// title strip, darker than the row body so the whole thing reads as one object
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(BG_ALT);
		header.setBorder(BorderFactory.createEmptyBorder(7, 4, 7, 4));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel name = new JLabel(task.getTask());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(FG);

		JLabel right = new JLabel(range(options));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(FG_DIM);

		header.add(name, BorderLayout.WEST);
		header.add(right, BorderLayout.EAST);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(PANEL);
		body.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		body.setVisible(expanded);

		JPanel rec = recommendation(options);
		if (rec != null)
		{
			body.add(rec);
		}

		boolean any = false;
		for (Option o : options)
		{
			if (o.isSuperior() && config.hideSuperiors())
			{
				continue;
			}
			body.add(optionRow(o));
			any = true;
		}

		if (!any)
		{
			body.add(hint("No monster data on the wiki for this one."));
		}

		if (!task.getItems().isEmpty())
		{
			body.add(detail("Bring: " + String.join(", ", task.getItems())));
		}

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				body.setVisible(!body.isVisible());
				box.revalidate();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				header.setBackground(HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setBackground(BG_ALT);
			}
		});

		box.add(header, BorderLayout.NORTH);
		box.add(body, BorderLayout.CENTER);
		return box;
	}

	/** "87-1065 xp" across everything you could choose. */
	private static String range(List<Option> options)
	{
		double lo = Double.MAX_VALUE, hi = -1;
		for (Option o : options)
		{
			if (o.isSuperior() || o.getBestXp() < 0)
			{
				continue;
			}
			lo = Math.min(lo, o.getBestXp());
			hi = Math.max(hi, o.getBestXp());
		}
		if (hi < 0)
		{
			return "";
		}
		return (lo == hi ? Option.trim(hi) : Option.trim(lo) + "-" + Option.trim(hi)) + " xp";
	}

	/**
	 * What to go kill and what it costs. Accent bar on the left, same treatment the
	 * everykill panel gives its headline numbers.
	 *
	 * Only worth drawing when there's a real choice AND the best option is properly
	 * better than the worst. A 1.1x difference isn't a recommendation, it's noise.
	 */
	private JPanel recommendation(List<Option> options)
	{
		Option best = null;
		double worst = Double.MAX_VALUE;
		int choices = 0;

		for (Option o : options)
		{
			if (o.isSuperior() || o.getBestXp() < 0)
			{
				continue;
			}
			choices++;
			worst = Math.min(worst, o.getBestXp());
			if (best == null || o.getBestXp() > best.getBestXp())
			{
				best = o;
			}
		}

		if (best == null || choices < 2)
		{
			return null;
		}

		double mult = worst > 0 ? best.getBestXp() / worst : 0;
		if (mult < 1.5)
		{
			return null;
		}

		JPanel rec = new JPanel(new BorderLayout());
		rec.setBackground(BG_ALT);
		rec.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 0, 0, ACC),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(BG_ALT);

		JLabel who = new JLabel(best.getName());
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(ACC);

		JLabel x = new JLabel(fmtMult(mult) + " xp");
		x.setFont(FontManager.getRunescapeSmallFont());
		x.setForeground(ACC);

		top.add(who, BorderLayout.WEST);
		top.add(x, BorderLayout.EAST);

		StringBuilder why = new StringBuilder(Option.trim(best.getBestXp()) + " xp each");
		if (!best.getGate().isEmpty())
		{
			why.append(". Needs ").append(best.getGate());
		}
		why.append('.');

		JTextArea sub = wrapped(why.toString(), FG_DIM);
		sub.setBorder(new EmptyBorder(3, 0, 0, 0));

		rec.add(top, BorderLayout.NORTH);
		rec.add(sub, BorderLayout.CENTER);
		return rec;
	}

	private static String fmtMult(double m)
	{
		return (m >= 10 ? String.valueOf(Math.round(m))
			: String.valueOf(Math.round(m * 10) / 10.0)) + "\u00d7";
	}

	/** Icon, name and xp on top; level, where, and what gates it underneath. */
	private JPanel optionRow(Option o)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setBackground(PANEL);

		JPanel row = new JPanel(new BorderLayout(0, 0));
		row.setBackground(PANEL);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
			BorderFactory.createEmptyBorder(3, 4, 3, 6)));

		Color fg = o.isSuperior() ? FG_FAINT : FG;
		Color dim = o.isSuperior() ? FG_FAINT : FG_DIM;

		JLabel pic = icon(o.getName());
		if (pic != null)
		{
			row.add(pic, BorderLayout.WEST);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(PANEL);
		text.setBorder(BorderFactory.createEmptyBorder(0, pic == null ? 0 : 5, 0, 0));

		JPanel line1 = new JPanel(new BorderLayout(4, 0));
		line1.setBackground(PANEL);

		JLabel name = new JLabel(o.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(fg);

		JLabel xp = new JLabel(o.getXp().isEmpty() ? "-" : o.getXp() + " xp");
		xp.setFont(FontManager.getRunescapeSmallFont());
		xp.setForeground(dim);

		line1.add(name, BorderLayout.CENTER);
		line1.add(xp, BorderLayout.EAST);

		JPanel line2 = new JPanel(new BorderLayout(4, 0));
		line2.setBackground(PANEL);

		// the caret says "there's more in here". every row gets one - a caret on some
		// rows and not others reads as broken rather than as a distinction.
		int extra = o.getExtraLocations();
		String loc = o.getLocation().isEmpty() ? "no location listed" : o.getLocation();
		if (extra > 0)
		{
			loc = loc + "  +" + extra;
		}
		String meta = "\u25b8 " + (o.getCombat().isEmpty() ? loc : "lv " + o.getCombat() + "  " + loc);

		JLabel where = new JLabel(meta);
		where.setFont(FontManager.getRunescapeSmallFont());
		where.setForeground(FG_FAINT);

		JLabel gate = new JLabel(o.getGate().isEmpty() ? "open" : o.getGate());
		gate.setFont(FontManager.getRunescapeSmallFont());
		gate.setForeground(o.getGate().isEmpty() ? GOOD : ACC);

		line2.add(where, BorderLayout.CENTER);
		line2.add(gate, BorderLayout.EAST);

		text.add(line1);
		text.add(line2);
		row.add(text, BorderLayout.CENTER);

		wrap.add(row);

		// every place this thing lives plus its wiki link, hidden until asked for
		JPanel places = locations(o);
		places.setVisible(false);
		wrap.add(places);

		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				places.setVisible(!places.isVisible());
				where.setText((places.isVisible() ? "\u25be " : "\u25b8 ") + meta.substring(2));
				wrap.revalidate();
			}
		});

		StringBuilder tip = new StringBuilder("<html>").append(o.getName());
		if (!o.isVerified())
		{
			tip.append("<br><i>unverified - wiki article table only</i>");
		}
		row.setToolTipText(tip.append("</html>").toString());

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBg(row, HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBg(row, PANEL);
			}
		});

		return wrap;
	}

	/**
	 * The full location list for a monster, plus a wiki link.
	 *
	 * Centred, because it's a detail panel hanging under its row rather than another
	 * list of rows - left-aligning it made it read as more monsters.
	 */
	private JPanel locations(Option o)
	{
		JPanel inner = new JPanel();
		inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
		inner.setBackground(BG_ALT);

		if (o.getLocations().isEmpty())
		{
			inner.add(centred("Nowhere listed on the wiki", FG_FAINT));
		}
		else
		{
			for (String place : o.getLocations())
			{
				inner.add(centred(place, FG_DIM));
			}
		}

		inner.add(wikiIcon(o.getName()));

		// BoxLayout hands a child its MAXIMUM height when there's room and squeezes it
		// when there isn't - which is what cut the last line in half. wrapping the
		// column in a BorderLayout panel pins it to its preferred height instead.
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(BG_ALT);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
			BorderFactory.createEmptyBorder(6, 8, 7, 8)));
		p.add(inner, BorderLayout.CENTER);
		return p;
	}

	/**
	 * Small wiki button. An icon rather than the words "Open wiki page" - the text was
	 * long enough to clip in a 225px panel, and a 16px glyph never can.
	 *
	 * Drawn rather than bundled: RuneLite has no wiki icon an external plugin can use,
	 * and a hand-drawn "w" needs no asset and no licence.
	 */
	private JLabel wikiIcon(String monster)
	{
		JLabel b = new JLabel(new WikiGlyph(FG_FAINT));
		b.setAlignmentX(CENTER_ALIGNMENT);
		b.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		b.setToolTipText("Open the wiki page for " + monster);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setMaximumSize(b.getPreferredSize());

		b.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				e.consume();
				LinkBrowser.browse("https://oldschool.runescape.wiki/w/"
					+ monster.replace(' ', '_'));
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				b.setIcon(new WikiGlyph(ACC));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				b.setIcon(new WikiGlyph(FG_FAINT));
			}
		});
		return b;
	}

	/** A 16px rounded square with a "w" in it. */
	private static class WikiGlyph implements Icon
	{
		private static final int SIZE = 16;
		private final Color colour;

		WikiGlyph(Color colour)
		{
			this.colour = colour;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(colour);
			g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 4, 4);
			g2.setFont(FontManager.getRunescapeSmallFont());
			String s = "w";
			int w = g2.getFontMetrics().stringWidth(s);
			int h = g2.getFontMetrics().getAscent();
			g2.drawString(s, x + (SIZE - w) / 2, y + (SIZE + h) / 2 - 2);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	/** A centred line inside a BoxLayout column. */
	private static JLabel centred(String text, Color colour)
	{
		JLabel l = new JLabel(text, JLabel.CENTER);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(colour);
		// BoxLayout aligns children against each other, so every child must agree
		l.setAlignmentX(CENTER_ALIGNMENT);
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
		return l;
	}

	/**
	 * The monster's item icon, or null when we haven't got one.
	 *
	 * addTo() is the important half - the image loads off the EDT and repaints the label
	 * itself when it's ready, so nothing blocks swing. Same call LootTrackerBox makes.
	 */
	private JLabel icon(String monster)
	{
		int id = icons.forName(monster);
		if (id < 0)
		{
			return null;
		}

		JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(32, 30));
		l.setHorizontalAlignment(JLabel.CENTER);
		AsyncBufferedImage img = itemManager.getImage(id);
		img.addTo(l);
		return l;
	}

	/** Hover has to recurse or the row lights up in patches. */
	private static void setBg(JPanel row, Color c)
	{
		row.setBackground(c);
		for (Component child : row.getComponents())
		{
			if (child instanceof JPanel)
			{
				child.setBackground(c);
				for (Component sub : ((JPanel) child).getComponents())
				{
					if (sub instanceof JPanel)
					{
						sub.setBackground(c);
					}
				}
			}
		}
	}

	/**
	 * Wrapping text that actually wraps.
	 *
	 * An html JLabel with a pixel width in its body style does NOT reflow - it lays out
	 * once at whatever number you guessed and clips whatever doesn't fit. Guessed twice,
	 * clipped twice. A JTextArea with line wrap on is a real component that wraps to the
	 * width it's given, so the panel decides the width instead of me.
	 */
	private static JTextArea wrapped(String text, Color colour)
	{
		JTextArea a = new JTextArea(text);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setEditable(false);
		a.setFocusable(false);
		a.setOpaque(false);
		a.setBorder(null);
		a.setFont(FontManager.getRunescapeSmallFont());
		a.setForeground(colour);
		return a;
	}

	private JPanel detail(String text)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(BG_ALT);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(7, 6, 7, 6)));
		p.add(wrapped(text, FG_DIM));
		return p;
	}
}
