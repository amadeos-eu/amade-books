package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.epub.TocEntry;
import eu.amadeos.apps.books.domain.library.Bookmark;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.function.Consumer;

/** The reader's sidebar: the book's table of contents and its bookmarks, on two tabs. */
final class ReaderSidebar extends JPanel {
    private final Messages msg;
    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultListModel<TocEntry> tocModel = new DefaultListModel<>();
    private final JList<TocEntry> toc = new JList<>(tocModel);
    private final DefaultListModel<Bookmark> markModel = new DefaultListModel<>();
    private final JList<Bookmark> marks = new JList<>(markModel);
    private final JLabel noMarks;
    private Consumer<TocEntry> tocChosen = e -> { };
    private Consumer<Bookmark> markChosen = b -> { };
    private Consumer<Bookmark> markRemoved = b -> { };
    private Consumer<Boolean> tabChosen = b -> { };
    private boolean updating;

    ReaderSidebar(Messages msg) {
        super(new BorderLayout());
        this.msg = msg;
        setName("sidebar");
        setPreferredSize(new Dimension(Math.round(250 * AmadeUI.fontScale()), 100));
        toc.setName("toc");
        toc.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        toc.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                TocEntry e = (TocEntry) value;
                JLabel label = (JLabel) super.getListCellRendererComponent(list, e.label(), index, selected, focus);
                label.setBorder(BorderFactory.createEmptyBorder(2, 6 + 14 * e.depth(), 2, 6));
                label.setFont(list.getFont().deriveFont(e.depth() == 0 ? Font.BOLD : Font.PLAIN));
                return label;
            }
        });
        marks.setName("bookmarks");
        marks.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(msg.locale());
        marks.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Bookmark b = (Bookmark) value;
                String date = dates.format(Instant.ofEpochMilli(b.created()).atZone(ZoneId.systemDefault()));
                String html = "<html><body style='width:" + Math.max(80, list.getWidth() - 40) + "px'>"
                        + escape(b.excerpt()) + "<br><font size='-2'>" + escape(date) + "</font></body></html>";
                JLabel label = (JLabel) super.getListCellRendererComponent(list, html, index, selected, focus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return label;
            }
        });
        click(toc, () -> {
            if (toc.getSelectedValue() != null) tocChosen.accept(toc.getSelectedValue());
        });
        click(marks, () -> {
            if (marks.getSelectedValue() != null) markChosen.accept(marks.getSelectedValue());
        });
        marks.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove");
        marks.getActionMap().put("remove", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (marks.getSelectedValue() != null) markRemoved.accept(marks.getSelectedValue());
            }
        });
        marks.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = marks.locationToIndex(e.getPoint());
                if (index < 0) return;
                marks.setSelectedIndex(index);
                JPopupMenu menu = new JPopupMenu();
                JMenuItem remove = new JMenuItem(msg.get("bookmarks.remove"));
                remove.setName("bookmarks.remove");
                Bookmark b = markModel.get(index);
                remove.addActionListener(a -> markRemoved.accept(b));
                menu.add(remove);
                menu.show(marks, e.getX(), e.getY());
            }
        });
        noMarks = new JLabel("<html><center>" + escape(msg.get("bookmarks.empty")) + "</center></html>", JLabel.CENTER);
        noMarks.setForeground(AmadeUI.theme().textInactive());
        JPanel markPanel = new JPanel(new BorderLayout());
        markPanel.add(new JScrollPane(marks), BorderLayout.CENTER);
        markPanel.add(noMarks, BorderLayout.SOUTH);
        tabs.setName("sidebar.tabs");
        tabs.addTab(msg.get("sidebar.contents"), new JScrollPane(toc));
        tabs.addTab(msg.get("sidebar.bookmarks"), markPanel);
        tabs.addChangeListener(e -> {
            if (!updating) tabChosen.accept(tabs.getSelectedIndex() == 1);
        });
        add(tabs, BorderLayout.CENTER);
    }

    /** Enter and a single click choose the entry. */
    private static void click(JList<?> list, Runnable chosen) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && list.locationToIndex(e.getPoint()) >= 0) chosen.run();
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose");
        list.getActionMap().put("choose", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chosen.run();
            }
        });
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    void onToc(Consumer<TocEntry> action) {
        tocChosen = action;
    }

    void onBookmark(Consumer<Bookmark> action) {
        markChosen = action;
    }

    void onRemove(Consumer<Bookmark> action) {
        markRemoved = action;
    }

    void onTab(Consumer<Boolean> action) {
        tabChosen = action;
    }

    void setToc(List<TocEntry> entries) {
        tocModel.clear();
        tocModel.addAll(entries);
        tabs.setEnabledAt(0, !entries.isEmpty());
    }

    void showTocEntry(int index) {
        updating = true;
        try {
            if (index < 0 || index >= tocModel.size()) {
                toc.clearSelection();
            } else {
                toc.setSelectedIndex(index);
                toc.ensureIndexIsVisible(index);
            }
        } finally {
            updating = false;
        }
    }

    void setBookmarks(List<Bookmark> bookmarks) {
        markModel.clear();
        markModel.addAll(bookmarks);
        noMarks.setVisible(bookmarks.isEmpty());
    }

    void showTab(boolean bookmarks) {
        updating = true;
        try {
            tabs.setSelectedIndex(bookmarks || !tabs.isEnabledAt(0) ? 1 : 0);
        } finally {
            updating = false;
        }
    }

    JList<TocEntry> tocList() {
        return toc;
    }

    JList<Bookmark> bookmarkList() {
        return marks;
    }

    JTabbedPane tabs() {
        return tabs;
    }
}
