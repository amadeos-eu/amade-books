package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.apps.books.domain.library.ShelfFilter;
import eu.amadeos.apps.books.domain.library.SortOrder;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.util.Map;
import java.util.function.Function;

/**
 * The library: a toolbar with adding, opening, the view and the sort order, the shelves on the left (reading
 * state, format, genre, author, series and the reader's own collections) and the books as covers or a list.
 */
final class LibraryPanel extends JPanel {
    private static final String COVERS = "covers";
    private static final String LIST = "list";

    private final Messages msg;
    private final MainPresenter presenter;
    private final CoverGrid grid;
    private final BookTable tableModel;
    private final JTable table;
    private final CardLayout cards = new CardLayout();
    private final JPanel books = new JPanel(cards);
    private final DefaultListModel<Object> shelfModel = new DefaultListModel<>();
    private final JList<Object> shelves = new JList<>(shelfModel);
    private final JComboBox<SortOrder> sort = new JComboBox<>(SortOrder.values());
    private final JTextField search = new JTextField(16);
    private final JLabel scanning = new JLabel(" ");
    private final Action coversAction;
    private final Action listAction;
    private Function<LibraryEntry, BookState> states = e -> BookState.NEW;
    private boolean updating;

    LibraryPanel(MainPresenter presenter, Messages msg) {
        super(new BorderLayout());
        this.msg = msg;
        this.presenter = presenter;
        setName("library");
        tableModel = new BookTable();
        table = new JTable(tableModel);
        grid = new CoverGrid(msg);
        grid.setEmptyText(msg.get("library.empty"));
        grid.onOpen(presenter::open);
        grid.onMenu((entry, e) -> bookMenu(entry).show(e.getComponent(), e.getX(), e.getY()));
        coversAction = action("library.covers", Glyph.COVERS, () -> presenter.setListView(false));
        listAction = action("library.list", Glyph.LIST, () -> presenter.setListView(true));

        table.setName("library.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setRowHeight(Math.round(table.getRowHeight() * 1.3f));
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) presenter.open(tableModel.get(row));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            private void popup(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (!e.isPopupTrigger() || row < 0) return;
                table.setRowSelectionInterval(row, row);
                bookMenu(tableModel.get(row)).show(table, e.getX(), e.getY());
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        table.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.getSelectedRow() >= 0) presenter.open(tableModel.get(table.getSelectedRow()));
            }
        });

        JScrollPane gridScroll = new JScrollPane(grid);
        gridScroll.setName("covers.scroll");
        gridScroll.setBorder(AmadeUI.sunken());
        gridScroll.getVerticalScrollBar().setUnitIncrement(32);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AmadeUI.sunken());
        books.add(gridScroll, COVERS);
        books.add(tableScroll, LIST);

        shelves.setName("shelves");
        shelves.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shelves.setCellRenderer(new ShelfRenderer());
        shelves.addListSelectionListener(e -> {
            if (updating || e.getValueIsAdjusting()) return;
            if (shelves.getSelectedValue() instanceof Shelf s) presenter.setFilter(s.filter());
        });
        JScrollPane shelfScroll = new JScrollPane(shelves);
        shelfScroll.setBorder(AmadeUI.sunken());
        shelfScroll.setPreferredSize(new Dimension(Math.round(210 * AmadeUI.fontScale()), 100));

        JPanel centre = new JPanel(new BorderLayout(4, 0));
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        centre.add(shelfScroll, BorderLayout.WEST);
        centre.add(books, BorderLayout.CENTER);
        add(toolBar(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
    }

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.button(action("library.add", Glyph.ADD, presenter::addBooks)));
        bar.add(ToolButtons.button(action("tool.open", StandardGlyph.OPEN, presenter::chooseAndOpen)));
        bar.add(ToolButtons.button(action("library.rescan", StandardGlyph.REFRESH, presenter::rescan)));
        bar.addSeparator();
        bar.add(ToolButtons.toggle(coversAction));
        bar.add(ToolButtons.toggle(listAction));
        bar.addSeparator();
        JLabel sortLabel = new JLabel(msg.get("library.sort") + " ");
        sortLabel.setLabelFor(sort);
        bar.add(sortLabel);
        sort.setName("library.sort");
        sort.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                String text = value == null ? "" : msg.get("sort." + ((SortOrder) value).key());
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        sort.setMaximumSize(new Dimension(sort.getPreferredSize().width + 20, sort.getPreferredSize().height));
        sort.addActionListener(e -> {
            if (!updating && sort.getSelectedItem() != null) presenter.setSort((SortOrder) sort.getSelectedItem());
        });
        bar.add(sort);
        bar.add(Box.createHorizontalStrut(12));
        scanning.setName("library.scanning");
        scanning.setForeground(AmadeUI.theme().textInactive());
        bar.add(scanning);
        bar.add(Box.createHorizontalGlue());
        JLabel find = new JLabel(new ToolIcon(StandardGlyph.FIND));
        find.setToolTipText(msg.get("library.search"));
        find.setLabelFor(search);
        bar.add(find);
        bar.add(Box.createHorizontalStrut(4));
        search.setName("library.search");
        search.setToolTipText(msg.get("library.search"));
        search.setMaximumSize(search.getPreferredSize());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                presenter.setQuery(search.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                presenter.setQuery(search.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        search.getActionMap().put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search.setText("");
            }
        });
        bar.add(search);
        return bar;
    }

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        AbstractAction action = new AbstractAction(msg.get(key)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        action.putValue(Action.SHORT_DESCRIPTION, msg.get(key));
        action.putValue(Action.ACTION_COMMAND_KEY, key);
        return action;
    }

    // --8<-- [start:menu]
    /** The menu of a book: open, mark as read, collections, show the file. */
    JPopupMenu bookMenu(LibraryEntry entry) {
        JPopupMenu menu = new JPopupMenu();
        BookState state = states.apply(entry);
        menu.add(item("book.open", () -> presenter.open(entry)));
        menu.addSeparator();
        menu.add(item(state.finished() ? "book.unfinished" : "book.finished", () -> presenter.setFinished(entry, !state.finished())));
        menu.add(item("book.collection.add", () -> presenter.addToCollection(entry)));
        if (!state.collections().isEmpty()) {
            JMenu remove = new JMenu(msg.get("book.collection.remove"));
            remove.setName("book.collection.remove");
            for (String c : state.collections()) {
                JMenuItem item = new JMenuItem(c);
                item.addActionListener(e -> presenter.removeFromCollection(entry, c));
                remove.add(item);
            }
            menu.add(remove);
        }
        menu.addSeparator();
        menu.add(item("book.folder", () -> presenter.showInFolder(entry)));
        return menu;
    }
    // --8<-- [end:menu]

    private JMenuItem item(String key, Runnable action) {
        JMenuItem item = new JMenuItem(msg.get(key));
        item.setName(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    // ---------------------------------------------------------------- showing

    void showBooks(Map<ShelfFilter, Integer> facets, ShelfFilter selected, List<LibraryEntry> list,
                   Function<LibraryEntry, BookState> newStates) {
        states = newStates;
        updating = true;
        try {
            shelfModel.clear();
            ShelfFilter.Kind section = null;
            int select = -1;
            for (Map.Entry<ShelfFilter, Integer> f : facets.entrySet()) {
                ShelfFilter.Kind heading = heading(f.getKey().kind());
                if (heading != section) {
                    section = heading;
                    shelfModel.addElement(new Heading(msg.get("section." + sectionKey(heading))));
                }
                if (f.getKey().equals(selected)) select = shelfModel.size();
                shelfModel.addElement(new Shelf(f.getKey(), label(f.getKey()), f.getValue()));
            }
            if (select >= 0) shelves.setSelectedIndex(select);
        } finally {
            updating = false;
        }
        grid.setEmptyText(msg.get(facets.getOrDefault(ShelfFilter.ALL, 0) == 0 ? "library.empty" : "library.none"));
        grid.setBooks(list, newStates);
        tableModel.set(list);
    }

    /** The section a sidebar line belongs to: the fixed groups under the library, the two formats together. */
    private static ShelfFilter.Kind heading(ShelfFilter.Kind kind) {
        return switch (kind) {
            case ALL, READING, NOT_STARTED, FINISHED, INCLUDED -> ShelfFilter.Kind.ALL;
            case BOOKS, COMICS -> ShelfFilter.Kind.BOOKS;
            default -> kind;
        };
    }

    private static String sectionKey(ShelfFilter.Kind heading) {
        return switch (heading) {
            case ALL -> "library";
            case BOOKS -> "format";
            case GENRE -> "genre";
            case AUTHOR -> "author";
            case SERIES -> "series";
            default -> "collection";
        };
    }

    private String label(ShelfFilter f) {
        return switch (f.kind()) {
            case ALL -> msg.get("filter.all");
            case READING -> msg.get("filter.reading");
            case NOT_STARTED -> msg.get("filter.not-started");
            case FINISHED -> msg.get("filter.finished");
            case INCLUDED -> msg.get("filter.included");
            case BOOKS -> msg.get("filter.books");
            case COMICS -> msg.get("filter.comics");
            default -> f.value();
        };
    }

    void showListView(boolean list) {
        coversAction.putValue(Action.SELECTED_KEY, !list);
        listAction.putValue(Action.SELECTED_KEY, list);
        cards.show(books, list ? LIST : COVERS);
    }

    void showSort(SortOrder order) {
        updating = true;
        try {
            sort.setSelectedItem(order);
        } finally {
            updating = false;
        }
    }

    void showScanning(boolean running) {
        scanning.setText(running ? msg.get("library.scanning") : " ");
    }

    void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    void focusBooks() {
        grid.requestFocusInWindow();
    }

    CoverGrid grid() {
        return grid;
    }

    JTable table() {
        return table;
    }

    JList<Object> shelfList() {
        return shelves;
    }

    JTextField searchField() {
        return search;
    }

    JComboBox<SortOrder> sortBox() {
        return sort;
    }

    // ---------------------------------------------------------------- the sidebar's lines

    record Heading(String text) {
    }

    record Shelf(ShelfFilter filter, String label, int count) {
    }

    private final class ShelfRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            if (value instanceof Heading h) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, h.text(), index, false, false);
                label.setFont(list.getFont().deriveFont(Font.BOLD));
                label.setForeground(AmadeUI.theme().textInactive());
                label.setBorder(BorderFactory.createEmptyBorder(index == 0 ? 2 : 10, 6, 2, 6));
                return label;
            }
            Shelf s = (Shelf) value;
            String text = "<html>" + ReaderSidebar.escape(s.label()) + " <font color='#808080'>" + s.count() + "</font></html>";
            JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, selected, focus);
            label.setBorder(BorderFactory.createEmptyBorder(2, 16, 2, 6));
            return label;
        }
    }

    // ---------------------------------------------------------------- the list view

    private final class BookTable extends AbstractTableModel {
        private List<LibraryEntry> rows = List.of();
        private DateTimeFormatter dates;

        private DateTimeFormatter dates() {
            if (dates == null) dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(msg.locale());
            return dates;
        }

        void set(List<LibraryEntry> list) {
            rows = List.copyOf(list);
            fireTableDataChanged();
        }

        LibraryEntry get(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return msg.get(switch (column) {
                case 0 -> "column.title";
                case 1 -> "column.author";
                case 2 -> "column.series";
                case 3 -> "column.format";
                case 4 -> "column.progress";
                default -> "column.read";
            });
        }

        @Override
        public Object getValueAt(int row, int column) {
            LibraryEntry e = rows.get(row);
            BookState s = states.apply(e);
            return switch (column) {
                case 0 -> e.info().title();
                case 1 -> e.info().authorLine();
                case 2 -> e.info().series().isEmpty() ? "" : (e.info().series() + " " + e.info().seriesNumber()).strip();
                case 3 -> msg.get(e.info().format().comic() ? "filter.comics" : "filter.books");
                case 4 -> s.finished() ? "✓" : s.reading() ? Math.round(s.progress() * 100) + " %" : "";
                default -> s.lastRead() <= 0 ? "" : dates().format(Instant.ofEpochMilli(s.lastRead()).atZone(ZoneId.systemDefault()));
            };
        }
    }
}
