/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;

class SlayerAltsPanel extends PluginPanel
{
	private static final Color HEADER_BG = ColorScheme.DARKER_GRAY_COLOR.darker();
	private static final Color REC_BG = new Color(47, 42, 32);
	private static final Color GATE = new Color(165, 113, 77);
	private static final Color OPEN = new Color(95, 158, 95);
	private static final Color ROW_LINE = new Color(47, 47, 47);

	private final SlayerAltsConfig config;

	private final IconTextField search = new IconTextField();
	private final JPanel results = new JPanel();
	private final JLabel status = new JLabel();

	private TaskBook book;
	private String currentTaskName;
	private SlayerTask currentTask;

	@Inject
	SlayerAltsPanel(SlayerAltsConfig config)
	{
		this.config = config;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 26));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
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
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setBorder(new EmptyBorder(6, 2, 4, 2));

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(search, BorderLayout.NORTH);
		top.add(status, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(results, BorderLayout.CENTER);
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
				status.setText("Your task: " + currentTask.getTask()
					+ "  \u00b7  " + countable(opts) + " options");
				results.add(taskBox(currentTask, true));
			}
			else if (currentTaskName != null)
			{
				status.setText("No data for " + currentTaskName.toLowerCase());
				results.add(hint("Search for another task above."));
			}
			else
			{
				status.setText(book.all().size() + " tasks");
				results.add(hint("Get a slayer task, or search above."));
			}
		}
		else
		{
			List<SlayerTask> hits = book.search(q);
			status.setText(hits.size() + (hits.size() == 1 ? " match" : " matches"));
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
		l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		l.setBorder(new EmptyBorder(10, 2, 0, 2));
		return l;
	}

	private JPanel taskBox(SlayerTask task, boolean expanded)
	{
		List<Option> options = Option.from(task, config.sortByXp());

		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(5, 0, 0, 0));

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(HEADER_BG);
		header.setBorder(new EmptyBorder(6, 7, 6, 7));

		JLabel name = new JLabel(task.getTask());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel right = new JLabel(range(options));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		header.add(name, BorderLayout.WEST);
		header.add(right, BorderLayout.EAST);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(new EmptyBorder(5, 7, 7, 7));
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
				header.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setBackground(HEADER_BG);
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
	 * The whole point of the panel: what to go kill, and what it costs.
	 *
	 * Only worth drawing when there's an actual choice AND the best option is properly
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
		rec.setBackground(REC_BG);
		rec.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 2, 0, 0, ColorScheme.BRAND_ORANGE),
			new EmptyBorder(5, 6, 5, 6)));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(REC_BG);

		JLabel who = new JLabel(best.getName());
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(ColorScheme.BRAND_ORANGE);

		JLabel x = new JLabel(fmtMult(mult) + " xp");
		x.setFont(FontManager.getRunescapeSmallFont());
		x.setForeground(ColorScheme.BRAND_ORANGE);

		top.add(who, BorderLayout.WEST);
		top.add(x, BorderLayout.EAST);

		StringBuilder why = new StringBuilder(Option.trim(best.getBestXp()) + " xp each");
		if (!best.getGate().isEmpty())
		{
			why.append(". Needs ").append(best.getGate());
		}
		why.append('.');

		JLabel sub = new JLabel("<html><body style='width:150px'>" + why + "</body></html>");
		sub.setFont(FontManager.getRunescapeSmallFont());
		sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sub.setBorder(new EmptyBorder(2, 0, 0, 0));

		rec.add(top, BorderLayout.NORTH);
		rec.add(sub, BorderLayout.CENTER);

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(0, 0, 6, 0));
		wrap.add(rec);
		return wrap;
	}

	private static String fmtMult(double m)
	{
		return (m >= 10 ? String.valueOf(Math.round(m))
			: String.valueOf(Math.round(m * 10) / 10.0)) + "\u00d7";
	}

	/** Two lines: name/combat/xp, then where it is and what gates it. */
	private JPanel optionRow(Option o)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			new MatteBorder(0, 0, 1, 0, ROW_LINE),
			new EmptyBorder(3, 1, 3, 1)));

		Color fg = o.isSuperior() ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE;
		Color dim = o.isSuperior() ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR;

		JPanel line1 = new JPanel(new BorderLayout(4, 0));
		line1.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel left = new JPanel(new BorderLayout(4, 0));
		left.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel name = new JLabel(o.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(fg);
		left.add(name, BorderLayout.WEST);

		if (!o.getCombat().isEmpty())
		{
			JLabel cb = new JLabel(o.getCombat());
			cb.setFont(FontManager.getRunescapeSmallFont());
			cb.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			left.add(cb, BorderLayout.CENTER);
		}

		JLabel xp = new JLabel(o.getXp().isEmpty() ? "-" : o.getXp() + " xp");
		xp.setFont(FontManager.getRunescapeSmallFont());
		xp.setForeground(dim);

		line1.add(left, BorderLayout.WEST);
		line1.add(xp, BorderLayout.EAST);

		JPanel line2 = new JPanel(new BorderLayout(4, 0));
		line2.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		String loc = o.getLocation();
		if (o.getExtraLocations() > 0)
		{
			loc += ", +" + o.getExtraLocations();
		}
		JLabel where = new JLabel(loc);
		where.setFont(FontManager.getRunescapeSmallFont());
		where.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

		JLabel gate = new JLabel(o.getGate().isEmpty() ? "open" : o.getGate());
		gate.setFont(FontManager.getRunescapeSmallFont());
		gate.setForeground(o.getGate().isEmpty() ? OPEN : GATE);

		line2.add(where, BorderLayout.WEST);
		line2.add(gate, BorderLayout.EAST);

		row.add(line1);
		row.add(line2);

		StringBuilder tip = new StringBuilder("<html>");
		tip.append(o.getName());
		if (!o.getCombat().isEmpty())
		{
			tip.append("<br>Combat ").append(o.getCombat());
		}
		if (!o.getLocation().isEmpty())
		{
			tip.append("<br>").append(o.getLocation());
		}
		if (!o.isVerified())
		{
			tip.append("<br><i>unverified - wiki article table only</i>");
		}
		row.setToolTipText(tip.append("</html>").toString());

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse("https://oldschool.runescape.wiki/w/"
					+ o.getName().replace(' ', '_'));
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBg(row, ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBg(row, ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return row;
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

	private JLabel detail(String text)
	{
		JLabel l = new JLabel("<html><body style='width:150px'>" + text + "</body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		return l;
	}
}
