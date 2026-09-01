/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Value;
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
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
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
	private static final Color LOCKED = new Color(0xa5, 0x4d, 0x4d);

	private static final Color HOVER = new Color(0x1c, 0x1f, 0x25);

	private final SlayerAltsConfig config;
	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;
	private final Icons icons;

	private final IconTextField search = new IconTextField();
	private final JPanel results = new JPanel();
	private final JLabel status = new JLabel();

	/** Monster name -> its money making guide page titles. */
	private final Map<String, List<Guide>> guides;

	@Value
	static class Guide
	{
		String guide;
		String setup;
	}

	private static Map<String, List<Guide>> loadGuides(Gson gson)
	{
		Type type = new TypeToken<Map<String, List<Guide>>>()
		{
		}.getType();

		try (InputStream in = SlayerAltsPanel.class.getResourceAsStream("gp.json"))
		{
			if (in == null)
			{
				return Collections.emptyMap();
			}
			Map<String, List<Guide>> m = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), type);
			return m == null ? Collections.emptyMap() : m;
		}
		catch (IOException e)
		{
			return Collections.emptyMap();
		}
	}

	private TaskBook book;
	private String currentTaskName;
	private SlayerTask currentTask;
	private Account account = Account.UNKNOWN;

	void setAccount(Account account)
	{
		this.account = account;
		rebuild();
	}

	@Inject
	SlayerAltsPanel(SlayerAltsConfig config, ItemManager itemManager, SkillIconManager skillIconManager, Gson gson)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;
		this.icons = Icons.load(gson);
		this.guides = loadGuides(gson);

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
		add(credit(), BorderLayout.SOUTH);
	}

	/**
	 * Wiki credit, pinned to the bottom of the panel.
	 *
	 * The data is CC BY-NC-SA 3.0 and the BY half means attribution has to be where a
	 * user can see it, not buried in a repo file. Click it to open the wiki.
	 */
	private JPanel credit()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(PANEL);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(5, 2, 0, 2)));

		JLabel l = new JLabel("Data: OSRS Wiki (CC BY-NC-SA)", JLabel.CENTER);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(FG_FAINT);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.setToolTipText("Slayer data comes from the Old School RuneScape Wiki");
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse("https://oldschool.runescape.wiki");
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				l.setForeground(FG_DIM);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				l.setForeground(FG_FAINT);
			}
		});

		p.add(l, BorderLayout.CENTER);
		return p;
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

	/** Redraw with whatever's current - config changed, account changed. */
	void refresh()
	{
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
				// no task assigned - show every task worth an alternative, best first.
				// answers "which tasks are worth keeping" rather than nothing at all.
				List<SlayerTask> ranked = new java.util.ArrayList<>();
				for (SlayerTask t : book.all())
				{
					if (!t.getMonsters().isEmpty())
					{
						ranked.add(t);
					}
				}
				ranked.sort((a, b) -> Double.compare(best(b), best(a)));

				status.setText("ALL TASKS \u00b7 BEST XP FIRST");
				for (SlayerTask t : ranked)
				{
					results.add(taskBox(t, false));
				}
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

	/** Best xp you could get on this task, ignoring superiors. */
	private static double best(SlayerTask t)
	{
		double hi = -1;
		for (Option o : Option.from(t, true))
		{
			if (!o.isSuperior())
			{
				hi = Math.max(hi, o.getBestXp());
			}
		}
		return hi;
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
			if (o.isNightmareZoneOnly() && config.hideNightmareZone())
			{
				continue;
			}
			body.add(optionRow(o));
			any = true;
		}

		if (!any)
		{
			// no monster rows at all. the task still has locations from the master
			// table, and those are what you actually need to go do it.
			if (!task.getLocations().isEmpty())
			{
				body.add(detail("Found at: " + String.join(", ", task.getLocations())));
			}
			else
			{
				body.add(detail("The wiki lists no variants for this task."));
			}
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
	 * What to go kill and what it costs.
	 *
	 * Recommends the best thing you can ACTUALLY reach - headlining a monster behind a
	 * quest you haven't done is advice you can't act on. Falls back to the best overall
	 * when the account isn't known yet.
	 */
	private JPanel recommendation(List<Option> options)
	{
		Option best = null;
		Option bestReachable = null;
		double worst = Double.MAX_VALUE;
		int choices = 0;

		for (Option o : options)
		{
			if (o.isSuperior() || o.getBestXp() < 0)
			{
				continue;
			}
			// an NMZ dream boss is a real option but never the answer to "where do I
			// go kill this", so it must not become the headline
			if (o.isNightmareZoneOnly() && config.hideNightmareZone())
			{
				continue;
			}
			choices++;
			worst = Math.min(worst, o.getBestXp());
			if (best == null || o.getBestXp() > best.getBestXp())
			{
				best = o;
			}
			if (!account.check(o.getGate()).isLocked()
				&& (bestReachable == null || o.getBestXp() > bestReachable.getBestXp()))
			{
				bestReachable = o;
			}
		}

		if (best == null || choices < 2)
		{
			return null;
		}

		boolean limited = bestReachable != null && bestReachable != best;
		Option pick = bestReachable != null ? bestReachable : best;

		double mult = worst > 0 ? pick.getBestXp() / worst : 0;
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

		JLabel who = new JLabel(pick.getName());
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(ACC);

		JLabel x = new JLabel(fmtMult(mult) + " xp");
		x.setFont(FontManager.getRunescapeSmallFont());
		x.setForeground(ACC);

		top.add(who, BorderLayout.WEST);
		top.add(x, BorderLayout.EAST);

		StringBuilder why = new StringBuilder();
		if (limited)
		{
			why.append("Best you can reach. ");
		}
		why.append(Option.trim(pick.getBestXp())).append(" xp each");
		if (!pick.getGate().isEmpty())
		{
			why.append(". Needs ").append(pick.getGate());
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

		Access access = account.check(o.getGate());
		boolean dim = o.isSuperior() || access.isLocked();

		Color fg = dim ? FG_FAINT : FG;
		Color dimmed = dim ? FG_FAINT : FG_DIM;

		// every row gets one, drawn if the cache has no picture, so the column holds
		row.add(icon(o.getName()), BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(PANEL);
		text.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

		JPanel line1 = new JPanel(new BorderLayout(4, 0));
		line1.setBackground(PANEL);

		JLabel name = new JLabel(o.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(fg);

		JLabel xp = new JLabel(o.getXp().isEmpty() ? "-" : o.getXp() + " xp");
		xp.setFont(FontManager.getRunescapeSmallFont());
		xp.setForeground(dimmed);

		JPanel nameAndStyles = new JPanel();
		nameAndStyles.setLayout(new BoxLayout(nameAndStyles, BoxLayout.X_AXIS));
		nameAndStyles.setBackground(PANEL);
		nameAndStyles.add(name);
		for (String style : o.getStyles())
		{
			JLabel s = new JLabel(styleIcon(style));
			s.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
			s.setToolTipText("Hits with " + style);
			nameAndStyles.add(s);
		}

		line1.add(nameAndStyles, BorderLayout.CENTER);
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

		// green when you can walk in, red when you can't, grey when we can't tell
		JLabel gate = new JLabel(o.getGate().isEmpty() ? "open" : o.getGate());
		gate.setFont(FontManager.getRunescapeSmallFont());
		gate.setForeground(o.getGate().isEmpty() ? GOOD
			: access.isLocked() ? LOCKED : FG_FAINT);

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

		// the wiki has a money making guide for this one. a POINTER, not a number - the
		// guides compute profit live from GE prices and each assumes a specific setup,
		// so any figure we bundled would be a claim about gear we can't see.
		List<Guide> money = guides.get(o.getName());
		if (money != null && !money.isEmpty())
		{
			Guide g = money.get(0);
			String label = money.size() > 1
				? "money guides (" + money.size() + ")"
				: (g.getSetup().isEmpty() ? "money guide" : "money guide: " + g.getSetup());

			JLabel gp = centred(label, GOOD);
			gp.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			gp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			gp.setToolTipText("Open the wiki's money making guide");
			gp.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					e.consume();
					LinkBrowser.browse("https://oldschool.runescape.wiki/w/"
						+ g.getGuide().replace(' ', '_'));
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					gp.setForeground(FG);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					gp.setForeground(GOOD);
				}
			});
			inner.add(gp);
		}

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

	/**
	 * The game's own Attack/Ranged/Magic skill icon, scaled down to sit inline with
	 * a row's text. Real game art rather than a drawn glyph - these ship inside
	 * RuneLite core itself (SkillIconManager reads them from its own resources), so
	 * there's no licensing question and no risk of looking off-brand next to the
	 * actual skill icons the player sees everywhere else in the client.
	 *
	 * "melee" has no single RuneLite skill of its own - Attack is the closest 1:1
	 * icon (it's also what the wiki's own attack-style documentation uses to stand
	 * in for melee), so that's what's shown for every melee-hitting monster.
	 */
	private Icon styleIcon(String style)
	{
		Skill skill;
		switch (style)
		{
			case "ranged":
				skill = Skill.RANGED;
				break;
			case "magic":
				skill = Skill.MAGIC;
				break;
			case "melee":
			default:
				skill = Skill.ATTACK;
				break;
		}

		java.awt.Image img = skillIconManager.getSkillImage(skill, true)
			.getScaledInstance(18, 18, java.awt.Image.SCALE_SMOOTH);
		return new javax.swing.ImageIcon(img);
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
	 * The monster's icon.
	 *
	 * A real item picture when the cache has one that genuinely depicts this monster,
	 * otherwise a drawn initial. Only about half of them have art - there is no NPC
	 * image API, so the rest come from items that happen to show the monster, and for
	 * 187 of them nothing does. A lettered chip keeps every row the same shape instead
	 * of leaving a ragged hole down the left edge.
	 */
	private JLabel icon(String monster)
	{
		JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(32, 30));
		l.setHorizontalAlignment(JLabel.CENTER);

		int id = icons.forName(monster);
		if (id < 0)
		{
			l.setIcon(new InitialGlyph(monster));
			return l;
		}

		AsyncBufferedImage img = itemManager.getImage(id);
		img.addTo(l);
		return l;
	}

	/**
	 * A monster's first letter in a muted rounded box.
	 *
	 * Deliberately plain: it has to read as "no picture for this one" rather than
	 * pretending to be art, while still holding the column.
	 */
	private static class InitialGlyph implements Icon
	{
		private static final int SIZE = 18;
		private final String letter;

		InitialGlyph(String name)
		{
			String n = name == null ? "" : name.trim();
			this.letter = n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(BG_ALT);
			g2.fillRoundRect(x, y, SIZE, SIZE, 5, 5);
			g2.setColor(LINE);
			g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 5, 5);
			g2.setColor(FG_FAINT);
			g2.setFont(FontManager.getRunescapeSmallFont());
			int w = g2.getFontMetrics().stringWidth(letter);
			int h = g2.getFontMetrics().getAscent();
			g2.drawString(letter, x + (SIZE - w) / 2, y + (SIZE + h) / 2 - 2);
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
