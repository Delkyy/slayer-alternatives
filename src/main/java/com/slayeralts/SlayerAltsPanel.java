/*
 * Copyright (c) 2026, Delkyy
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.slayeralts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
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
				status.setText("Your task: " + currentTask.getTask());
				results.add(taskBox(currentTask, true));
			}
			else if (currentTaskName != null)
			{
				// core knows the task but we have no row for it. say so rather than
				// showing an empty panel and looking broken
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
				results.add(taskBox(t, false));
			}
		}

		results.revalidate();
		results.repaint();
	}

	private JLabel hint(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		l.setBorder(new EmptyBorder(10, 2, 0, 2));
		return l;
	}

	/** One task as a titled box, the way core's loot tracker does its entries. */
	private JPanel taskBox(SlayerTask task, boolean expanded)
	{
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(5, 0, 0, 0));

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(HEADER_BG);
		header.setBorder(new EmptyBorder(6, 7, 6, 7));

		JLabel name = new JLabel(task.getTask());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel right = new JLabel(headline(task));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		header.add(name, BorderLayout.WEST);
		header.add(right, BorderLayout.EAST);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(new EmptyBorder(4, 7, 7, 7));
		body.setVisible(expanded);

		List<Monster> monsters = config.sortByXp() ? task.choosable() : task.getMonsters();
		boolean any = false;
		for (Monster m : monsters)
		{
			if (m.isSuperior() && config.hideSuperiors())
			{
				continue;
			}
			body.add(monsterRow(m));
			any = true;
		}

		// superiors are dropped by choosable(), so add them back at the bottom greyed out
		if (config.sortByXp() && !config.hideSuperiors())
		{
			for (Monster m : task.getMonsters())
			{
				if (m.isSuperior())
				{
					body.add(monsterRow(m));
					any = true;
				}
			}
		}

		if (!any)
		{
			body.add(hint("No monster data on the wiki for this one."));
		}

		if (!task.getRequirements().isEmpty())
		{
			body.add(detail("Requires: " + String.join(", ", task.getRequirements())));
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

	private String headline(SlayerTask task)
	{
		double best = task.bestXp();
		if (best < 0)
		{
			return "";
		}
		return trim(best) + " xp";
	}

	private JPanel monsterRow(Monster m)
	{
		JPanel row = new JPanel(new GridLayout(1, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));

		JLabel name = new JLabel(m.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(m.isSuperior() ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
		if (!m.getLocations().isEmpty())
		{
			name.setToolTipText(String.join(", ", m.getLocations()));
		}

		String xp = m.xp() < 0 ? "-" : trim(m.xp()) + " xp";
		JLabel value = new JLabel(xp, JLabel.RIGHT);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setForeground(m.isSuperior()
			? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		StringBuilder tip = new StringBuilder("<html>");
		if (m.getCombat() != null && !m.getCombat().isEmpty())
		{
			tip.append("Combat ").append(m.getCombat());
		}
		for (String n : m.getNotes())
		{
			tip.append("<br>").append(n);
		}
		if (!m.isVerified())
		{
			tip.append("<br><i>unverified - wiki article table only</i>");
		}
		value.setToolTipText(tip.append("</html>").toString());

		row.add(name);
		row.add(value);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse("https://oldschool.runescape.wiki/w/"
					+ m.getName().replace(' ', '_'));
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

	/** 1065.0 reads as 1065, 618.5 stays 618.5. */
	private static String trim(double v)
	{
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}
}
