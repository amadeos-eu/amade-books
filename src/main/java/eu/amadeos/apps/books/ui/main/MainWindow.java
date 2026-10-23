package eu.amadeos.apps.books.ui.main;

import com.google.inject.Inject;
import eu.amadeos.apps.books.bootstrap.AmadeBooksApplication;
import eu.amadeos.apps.books.domain.BookInfo;
import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.Settings;
import eu.amadeos.apps.books.domain.epub.EpubSession;
import eu.amadeos.apps.books.domain.epub.TocEntry;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.layout.ChapterLayout;
import eu.amadeos.apps.books.domain.layout.PageGeometry;
import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.Bookmark;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.apps.books.domain.library.ShelfFilter;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The window: the library, or the reader with its toolbar, the sidebar with contents and bookmarks, the pages
 * (of a book or a comic) and the status bar with the progress through the book. Books can be dropped onto it.
 */
public final class MainWindow extends JFrame implements MainView {
    private static final String LIBRARY = "library";
    private static final String READER = "reader";
    private static final String BOOK = "book";
    private static final String COMIC = "comic";

    private final Messages msg;
    private final LocaleSettings locales;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final CardLayout readerCards = new CardLayout();
    private final JPanel readerArea = new JPanel(readerCards);
    private final PageView pages;
    private final ComicView comic;
    private final JScrollPane comicScroll;
    private final ReaderSidebar sidebar;
    private final AppearancePanel appearancePanel;
    private final JDialog appearanceDialog;
    private final ProgressStrip progress;
    private final JTextField searchField = new JTextField(14);
    private final JLabel searchLabel = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JLabel note = new JLabel(" ");
    private final Timer noteTimer = new Timer(5000, e -> note.setText(" "));
    private final Timer resizeTimer;
    private final List<JComponent> bookOnly = new ArrayList<>();
    private final List<JComponent> comicOnly = new ArrayList<>();
    private final Action sidebarAction;
    private final Action bookmarkAction;
    private final Action twoPagesAction;
    private final Action rightToLeftAction;
    private final Action fullScreenAction;
    private final Action backAction;
    private final Action forwardAction;
    private final JComboBox<ComicFit> fitBox = new JComboBox<>(ComicFit.values());
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private LibraryPanel library;
    private JToolBar readerBar;
    private JComponent statusBar;
    private Settings settings = Settings.DEFAULT;
    private MainPresenter.Mode mode = MainPresenter.Mode.LIBRARY;
    private boolean fullScreen;
    private Rectangle windowedBounds;
    private boolean updating;

    @Inject
    public MainWindow(Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-books.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
        pages = new PageView(new PageView.Callbacks() {
            @Override
            public boolean clicked(int chapter, int offset) {
                return presenter != null && presenter.clickedText(chapter, offset);
            }

            @Override
            public void turn(boolean forward) {
                if (presenter == null) return;
                if (forward) presenter.next();
                else presenter.previous();
            }

            @Override
            public void hover(int chapter, int offset) {
                if (presenter == null) return;
                note.setText(offset < 0 ? " " : presenter.linkAt(chapter, offset).orElse(" "));
            }
        });
        pages.setEmptyText(" ");
        comic = new ComicView(right -> {
            if (presenter != null) presenter.arrow(right);
        });
        comicScroll = new JScrollPane(comic);
        comicScroll.setName("comic.scroll");
        comicScroll.setBorder(BorderFactory.createEmptyBorder());
        comicScroll.getViewport().setBackground(ComicView.DESK);
        sidebar = new ReaderSidebar(msg);
        appearancePanel = new AppearancePanel(msg);
        appearanceDialog = new JDialog(this, msg.get("appearance.title"), false);
        appearanceDialog.setName("appearance.dialog");
        appearanceDialog.add(appearancePanel);
        appearanceDialog.pack();
        progress = new ProgressStrip(f -> {
            if (presenter != null) presenter.goToFraction(f);
        });
        resizeTimer = new Timer(150, e -> {
            if (presenter != null) presenter.relayout();
        });
        resizeTimer.setRepeats(false);
        sidebarAction = action("tool.sidebar", Glyph.SIDEBAR, () -> presenter.toggleSidebar());
        bookmarkAction = action("tool.bookmark", Glyph.BOOKMARK, () -> presenter.toggleBookmark());
        twoPagesAction = action("tool.two-pages", Glyph.TWO_PAGES, () -> presenter.setTwoPages(!presenter.twoPages()));
        rightToLeftAction = action("tool.right-to-left", Glyph.RIGHT_TO_LEFT,
                () -> presenter.setRightToLeft(Boolean.TRUE.equals(rightToLeftActionSelected())));
        fullScreenAction = action("tool.full-screen", StandardGlyph.FULL_SCREEN, () -> presenter.toggleFullScreen());
        backAction = action("tool.back", Glyph.BACK, () -> presenter.goBack());
        forwardAction = action("tool.forward", Glyph.FORWARD, () -> presenter.goForward());
        for (Action a : List.of(sidebarAction, bookmarkAction, twoPagesAction, rightToLeftAction)) {
            a.putValue(Action.SELECTED_KEY, false);
        }
    }

    private Object rightToLeftActionSelected() {
        return rightToLeftAction.getValue(Action.SELECTED_KEY);
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);
        library = new LibraryPanel(presenter, msg);
        sidebar.onToc(presenter::openToc);
        sidebar.onBookmark(presenter::openBookmark);
        sidebar.onRemove(presenter::removeBookmark);
        sidebar.onTab(presenter::showBookmarksTab);
        appearancePanel.onChange(presenter::setAppearance);
        appearancePanel.onTheme(presenter::setTheme);
        appearancePanel.onPages(presenter::setTwoPages);

        readerArea.add(pages, BOOK);
        readerArea.add(comicScroll, COMIC);
        readerArea.setBorder(AmadeUI.sunken());
        pages.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });
        comicScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                comic.revalidate();
                comic.repaint();
            }
        });
        JPanel reader = new JPanel(new BorderLayout());
        JPanel centre = new JPanel(new BorderLayout(4, 0));
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        centre.add(sidebar, BorderLayout.WEST);
        centre.add(readerArea, BorderLayout.CENTER);
        readerBar = toolBar();
        statusBar = statusBar();
        reader.add(readerBar, BorderLayout.NORTH);
        reader.add(centre, BorderLayout.CENTER);
        reader.add(statusBar, BorderLayout.SOUTH);
        root.add(library, LIBRARY);
        root.add(reader, READER);
        add(root, BorderLayout.CENTER);
        keys();
        TransferHandler drop = new FileDrop();
        for (JComponent c : List.of(pages, comic, library.grid(), library.table())) c.setTransferHandler(drop);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                library.focusBooks();
            }
        });
        Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        // room for the window manager's frame on a small screen
        setSize(Math.min(Math.round(1180 * AmadeUI.fontScale()), screen.width - 24),
                Math.min(Math.round(820 * AmadeUI.fontScale()), screen.height - 48));
        setMinimumSize(new Dimension(560, 400));
        setLocationRelativeTo(null);
        noteTimer.setRepeats(false);
        setMode(MainPresenter.Mode.LIBRARY);
    }

    // ---------------------------------------------------------------- toolbar and status bar

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.button(action("tool.library", Glyph.LIBRARY, () -> presenter.showLibraryView())));
        bar.add(ToolButtons.toggle(sidebarAction));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("tool.previous", Glyph.PREVIOUS, () -> presenter.arrow(false))));
        bar.add(ToolButtons.button(action("tool.next", Glyph.NEXT, () -> presenter.arrow(true))));
        bar.add(book(ToolButtons.button(backAction)));
        bar.add(book(ToolButtons.button(forwardAction)));
        bar.addSeparator();
        bar.add(ToolButtons.toggle(bookmarkAction));
        JButton appearance = ToolButtons.button(action("tool.appearance", Glyph.APPEARANCE, this::toggleAppearance));
        bar.add(book(appearance));
        bar.add(ToolButtons.toggle(twoPagesAction));
        bar.add(comic(ToolButtons.toggle(rightToLeftAction)));
        bar.add(comic(ToolButtons.button(action("tool.zoom-out", StandardGlyph.ZOOM_OUT, () -> presenter.zoomOut()))));
        bar.add(comic(ToolButtons.button(action("tool.zoom-in", StandardGlyph.ZOOM_IN, () -> presenter.zoomIn()))));
        fitBox.setName("tool.fit");
        fitBox.setToolTipText(msg.get("tool.fit"));
        fitBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean s, boolean f) {
                return super.getListCellRendererComponent(list, value == null ? "" : msg.get("fit." + ((ComicFit) value).key()),
                        index, s, f);
            }
        });
        fitBox.setMaximumSize(new Dimension(fitBox.getPreferredSize().width + 20, fitBox.getPreferredSize().height));
        fitBox.addActionListener(e -> {
            if (!updating && fitBox.getSelectedItem() != null) presenter.setComicFit((ComicFit) fitBox.getSelectedItem());
        });
        bar.add(comic(fitBox));
        bar.addSeparator();
        bar.add(ToolButtons.button(fullScreenAction));
        bar.add(Box.createHorizontalGlue());
        JLabel find = new JLabel(new ToolIcon(StandardGlyph.FIND));
        find.setToolTipText(msg.get("tool.find"));
        find.setLabelFor(searchField);
        bar.add(book(find));
        bar.add(Box.createHorizontalStrut(4));
        searchField.setName("search");
        searchField.setToolTipText(msg.get("tool.find"));
        searchField.setMaximumSize(searchField.getPreferredSize());
        searchField.addActionListener(e -> presenter.search(searchField.getText(),
                (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0));
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        searchField.getActionMap().put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
                presenter.clearSearch();
                pages.requestFocusInWindow();
            }
        });
        bar.add(book(searchField));
        searchLabel.setName("search.result");
        searchLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        bar.add(book(searchLabel));
        return bar;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        status.setName("status");
        note.setName("status.note");
        for (JLabel label : List.of(status, note)) {
            label.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        }
        bar.add(status, BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(8, 0));
        right.add(progress, BorderLayout.WEST);
        note.setPreferredSize(new Dimension(Math.round(300 * AmadeUI.fontScale()), status.getPreferredSize().height));
        right.add(note, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
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

    private <C extends JComponent> C book(C c) {
        bookOnly.add(c);
        return c;
    }

    private <C extends JComponent> C comic(C c) {
        comicOnly.add(c);
        return c;
    }

    private void setMode(MainPresenter.Mode newMode) {
        mode = newMode;
        for (JComponent c : bookOnly) c.setVisible(mode == MainPresenter.Mode.BOOK);
        for (JComponent c : comicOnly) c.setVisible(mode == MainPresenter.Mode.COMIC);
        menuBar.setMode(mode);
        if (mode != MainPresenter.Mode.BOOK) appearanceDialog.setVisible(false);
        cards.show(root, mode == MainPresenter.Mode.LIBRARY ? LIBRARY : READER);
        readerCards.show(readerArea, mode == MainPresenter.Mode.COMIC ? COMIC : BOOK);
        sidebar.setVisible(mode != MainPresenter.Mode.LIBRARY && settings.sidebar() && !fullScreen);
        if (mode == MainPresenter.Mode.LIBRARY) {
            setTitle(msg.get("window.title"));
            pages.clear();
        }
        validate();
    }

    private void toggleAppearance() {
        if (appearanceDialog.isVisible()) {
            appearanceDialog.setVisible(false);
            return;
        }
        Point at = readerArea.getLocationOnScreen();
        appearanceDialog.setLocation(at.x + readerArea.getWidth() - appearanceDialog.getWidth() - 12, at.y + 12);
        appearanceDialog.setVisible(true);
    }

    // --8<-- [start:keys]
    /** Keys for reading while the pages have the focus; Escape leaves the full screen. */
    private void keys() {
        for (JComponent c : List.of(pages, comic)) {
            bind(c, KeyEvent.VK_RIGHT, 0, () -> presenter.arrow(true));
            bind(c, KeyEvent.VK_LEFT, 0, () -> presenter.arrow(false));
            bind(c, KeyEvent.VK_PAGE_DOWN, 0, presenter::next);
            bind(c, KeyEvent.VK_PAGE_UP, 0, presenter::previous);
            bind(c, KeyEvent.VK_SPACE, 0, () -> down(true));
            bind(c, KeyEvent.VK_SPACE, InputEvent.SHIFT_DOWN_MASK, () -> down(false));
            bind(c, KeyEvent.VK_BACK_SPACE, 0, presenter::previous);
            bind(c, KeyEvent.VK_DOWN, 0, () -> down(true));
            bind(c, KeyEvent.VK_UP, 0, () -> down(false));
            bind(c, KeyEvent.VK_HOME, 0, presenter::first);
            bind(c, KeyEvent.VK_END, 0, presenter::last);
            bind(c, KeyEvent.VK_ESCAPE, 0, () -> {
                if (fullScreen) presenter.toggleFullScreen();
                else {
                    pages.clearSelection();
                    searchField.setText("");
                    presenter.clearSearch();
                }
            });
        }
        JComponent rootPane = getRootPane();
        for (int code : new int[]{KeyEvent.VK_EQUALS, KeyEvent.VK_ADD}) {
            for (int modifiers : new int[]{InputEvent.CTRL_DOWN_MASK, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK}) {
                global(rootPane, code, modifiers, "zoom-in", presenter::zoomIn);
            }
        }
        global(rootPane, KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK, "zoom-out", presenter::zoomOut);
    }
    // --8<-- [end:keys]

    /** Down and Space: in a comic larger than the window they scroll first, then turn the page. */
    private void down(boolean forward) {
        if (mode == MainPresenter.Mode.COMIC) {
            javax.swing.JScrollBar bar = comicScroll.getVerticalScrollBar();
            int step = comicScroll.getViewport().getHeight() - 48;
            boolean canScroll = forward ? bar.getValue() + bar.getVisibleAmount() < bar.getMaximum() : bar.getValue() > 0;
            if (bar.isVisible() && canScroll) {
                bar.setValue(bar.getValue() + (forward ? step : -step));
                return;
            }
        }
        if (forward) presenter.next();
        else presenter.previous();
    }

    private static void bind(JComponent c, int code, int modifiers, Runnable action) {
        String name = "key-" + code + "-" + modifiers;
        c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(code, modifiers), name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static void global(JComponent root, int code, int modifiers, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(code, modifiers), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- MainView: library

    @Override
    public void showSettings(Settings newSettings) {
        EdtExecutor.requireEdt();
        settings = newSettings;
        menuBar.update(settings);
        library.showListView(settings.listView());
        library.showSort(settings.sort());
        sidebarAction.putValue(Action.SELECTED_KEY, settings.sidebar());
        sidebar.setVisible(mode != MainPresenter.Mode.LIBRARY && settings.sidebar() && !fullScreen);
        sidebar.showTab(settings.bookmarksTab() || mode == MainPresenter.Mode.COMIC);
        if (mode != MainPresenter.Mode.COMIC) twoPagesAction.putValue(Action.SELECTED_KEY, settings.twoPages());
        appearancePanel.showTheme(settings.theme(), settings.twoPages());
        pages.setTheme(settings.theme());
        updating = true;
        try {
            fitBox.setSelectedItem(settings.comicFit());
        } finally {
            updating = false;
        }
        revalidate();
    }

    @Override
    public void showLibrary(Map<ShelfFilter, Integer> facets, ShelfFilter selected, List<LibraryEntry> books,
                            Function<LibraryEntry, BookState> states) {
        library.showBooks(facets, selected, books, states);
        if (mode != MainPresenter.Mode.LIBRARY && presenter.mode() == MainPresenter.Mode.LIBRARY) {
            setMode(MainPresenter.Mode.LIBRARY);
            library.focusBooks();
        }
    }

    @Override
    public void showScanning(boolean running) {
        library.showScanning(running);
    }

    // ---------------------------------------------------------------- MainView: books

    @Override
    public void showBook(BookInfo info, List<TocEntry> toc) {
        setTitle(msg.format("window.title.book", info.title()));
        sidebar.setToc(toc);
        sidebar.showTab(settings.bookmarksTab());
        searchField.setText("");
        status.setText(" ");
        progress.show(-1, List.of());
        twoPagesAction.putValue(Action.SELECTED_KEY, settings.twoPages());
        setMode(MainPresenter.Mode.BOOK);
        pages.requestFocusInWindow();
    }

    @Override
    public void showAppearance(Appearance appearance, boolean bookHasFonts) {
        appearancePanel.show(appearance, bookHasFonts);
    }

    @Override
    public Dimension readingArea() {
        return pages.getSize();
    }

    @Override
    public void showPages(EpubSession session, ChapterLayout layout, PageGeometry geometry, int chapter, int firstPage,
                          String[] heads, ReadTheme theme) {
        pages.showPages(session, layout, geometry, chapter, firstPage, heads, theme);
    }

    @Override
    public void showPlace(String chapterTitle, int page, int pageCount, double fraction, List<Double> chapterStarts,
                          boolean bookmarked) {
        String place = msg.format("status.page", page, pageCount);
        if (!chapterTitle.isEmpty()) place = chapterTitle + "  ·  " + place;
        if (fraction >= 0) place += "  ·  " + msg.format("status.percent", Math.round(fraction * 100));
        status.setText(place);
        progress.show(fraction, chapterStarts);
        bookmarkAction.putValue(Action.SELECTED_KEY, bookmarked);
    }

    @Override
    public void showTocEntry(int index) {
        sidebar.showTocEntry(index);
    }

    @Override
    public void showBookmarks(List<Bookmark> bookmarks) {
        sidebar.setBookmarks(bookmarks);
    }

    @Override
    public void showSearch(SearchState state) {
        pages.setSearch(state);
        if (!state.active()) {
            searchLabel.setText(" ");
        } else if (state.hits().isEmpty()) {
            searchLabel.setText(state.running() ? "…" : msg.get("search.none"));
        } else {
            searchLabel.setText(msg.format("search.count", state.current() + 1, state.hits().size()));
        }
        searchLabel.setForeground(state.active() && !state.running() && state.hits().isEmpty()
                ? new Color(0xb00000) : AmadeUI.theme().textActive());
    }

    @Override
    public void showHistory(boolean back, boolean forward) {
        menuBar.showHistory(back, forward);
        backAction.setEnabled(back);
        forwardAction.setEnabled(forward);
    }

    // --8<-- [start:footnote]
    @Override
    public void showFootnote(String text) {
        JTextArea area = new JTextArea(text, 1, 34);
        area.setName("footnote");
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        area.setBackground(pages.theme().background());
        area.setForeground(pages.theme().ink());
        area.setSize(new Dimension(Math.round(380 * AmadeUI.fontScale()), Short.MAX_VALUE));
        area.setPreferredSize(new Dimension(area.getWidth(), area.getPreferredSize().height));
        JPopupMenu popup = new JPopupMenu();
        popup.setName("footnote.popup");
        popup.add(area);
        Point at = new Point(pages.getWidth() / 3, pages.getHeight() / 3);
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer != null && pages.isShowing()) {
            Point p = pointer.getLocation();
            SwingUtilities.convertPointFromScreen(p, pages);
            if (pages.contains(p)) at = new Point(p.x + 8, p.y + 12);
        }
        if (pages.isShowing()) popup.show(pages, at.x, at.y);
    }
    // --8<-- [end:footnote]

    @Override
    public Optional<int[]> selection() {
        return pages.selection();
    }

    // ---------------------------------------------------------------- MainView: comics

    @Override
    public void showComic(BookInfo info, int pageCount) {
        setTitle(msg.format("window.title.book", info.title()));
        sidebar.setToc(List.of());
        sidebar.showTab(true);
        setMode(MainPresenter.Mode.COMIC);
        comic.requestFocusInWindow();
    }

    @Override
    public void showComicPages(List<BufferedImage> shown, List<Integer> numbers, ComicFit fit, double zoom,
                               boolean rightToLeft, boolean twoPages) {
        comic.showPages(shown, numbers, fit, zoom, twoPages);
        int total = presenter == null ? 0 : presenter.comicPageCount();
        String place = numbers.size() == 2
                ? msg.format("status.pages", Math.min(numbers.get(0), numbers.get(1)), Math.max(numbers.get(0), numbers.get(1)), total)
                : msg.format("status.page", numbers.getFirst(), total);
        status.setText(place + "  ·  " + msg.format("status.zoom", Math.round(comic.scale() * 100)));
        progress.show(total <= 1 ? 1 : (Math.max(numbers.get(0), numbers.getLast()) - 1) / (double) (total - 1), List.of());
        rightToLeftAction.putValue(Action.SELECTED_KEY, rightToLeft);
        twoPagesAction.putValue(Action.SELECTED_KEY, twoPages);
        bookmarkAction.putValue(Action.SELECTED_KEY, presenter != null && presenter.comicBookmarked());
        menuBar.showComicState(rightToLeft, twoPages, fit);
        updating = true;
        try {
            fitBox.setSelectedItem(fit);
        } finally {
            updating = false;
        }
    }

    // ---------------------------------------------------------------- MainView: common

    @Override
    public void showStatus(String message) {
        note.setText(message == null || message.isEmpty() ? " " : message);
        noteTimer.restart();
    }

    // --8<-- [start:fullscreen]
    /**
     * Full screen without changing the display mode; where the screen does not support it, a borderless window
     * that covers the screen. The toolbar, sidebar and status bar go away; F11 and Escape bring them back.
     */
    @Override
    public void setFullScreen(boolean on) {
        if (on == fullScreen) return;
        fullScreen = on;
        GraphicsDevice device = getGraphicsConfiguration().getDevice();
        readerBar.setVisible(!on);
        statusBar.setVisible(!on);
        getJMenuBar().setVisible(!on);
        sidebar.setVisible(!on && mode != MainPresenter.Mode.LIBRARY && settings.sidebar());
        readerArea.setBorder(on ? BorderFactory.createEmptyBorder() : AmadeUI.sunken());
        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(on ? this : null);
        } else if (on) {
            windowedBounds = getBounds();
            dispose();
            setUndecorated(true);
            setBounds(getGraphicsConfiguration().getBounds());
            setVisible(true);
        } else {
            dispose();
            setUndecorated(false);
            if (windowedBounds != null) setBounds(windowedBounds);
            setVisible(true);
        }
        fullScreenAction.putValue(Action.LARGE_ICON_KEY, new ToolIcon(on ? StandardGlyph.LEAVE_FULL_SCREEN : StandardGlyph.FULL_SCREEN));
        menuBar.showFullScreen(on);
        validate();
        (mode == MainPresenter.Mode.COMIC ? comic : pages).requestFocusInWindow();
    }
    // --8<-- [end:fullscreen]

    @Override
    public boolean fullScreen() {
        return fullScreen;
    }

    @Override
    public Optional<Path> chooseFile(Optional<Path> folder) {
        JFileChooser chooser = chooser(folder);
        chooser.setDialogTitle(msg.get("dialog.open.title"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public List<Path> chooseBooks(Optional<Path> folder) {
        JFileChooser chooser = chooser(folder);
        chooser.setDialogTitle(msg.get("dialog.add.title"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return List.of();
        List<Path> chosen = new ArrayList<>();
        for (File f : chooser.getSelectedFiles()) chosen.add(f.toPath());
        return chosen;
    }

    private JFileChooser chooser(Optional<Path> folder) {
        JFileChooser chooser = new JFileChooser(folder.map(Path::toFile).orElse(null));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("dialog.open.filter"), "epub", "cbz"));
        return chooser;
    }

    @Override
    public Optional<String> askCollection(List<String> existing) {
        JComboBox<String> box = new JComboBox<>(existing.toArray(String[]::new));
        box.setEditable(true);
        box.setSelectedItem("");
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(msg.get("dialog.collection.text")), BorderLayout.NORTH);
        panel.add(box, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(this, panel, msg.get("dialog.collection.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        Object chosen = box.getEditor().getItem();
        return answer == JOptionPane.OK_OPTION && chosen != null ? Optional.of(chosen.toString()) : Optional.empty();
    }

    // --8<-- [start:folders]
    @Override
    public Optional<List<Path>> editFolders(List<Path> folders) {
        DefaultListModel<Path> model = new DefaultListModel<>();
        model.addAll(folders);
        JList<Path> list = new JList<>(model);
        list.setName("folders");
        list.setVisibleRowCount(6);
        JButton add = new JButton(msg.get("dialog.folders.add"));
        add.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(folders.isEmpty() ? null : folders.getFirst().toFile());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(list) == JFileChooser.APPROVE_OPTION) {
                Path p = chooser.getSelectedFile().toPath();
                if (!model.contains(p)) model.addElement(p);
            }
        });
        JButton remove = new JButton(msg.get("dialog.folders.remove"));
        remove.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i >= 0) model.remove(i);
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(add);
        buttons.add(remove);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(msg.get("dialog.folders.text")), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(Math.round(420 * AmadeUI.fontScale()), Math.round(140 * AmadeUI.fontScale())));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        int answer = JOptionPane.showConfirmDialog(this, panel, msg.get("dialog.folders.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return Optional.empty();
        List<Path> result = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) result.add(model.get(i));
        return Optional.of(result);
    }
    // --8<-- [end:folders]

    @Override
    public void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Override
    public void focusSearch() {
        if (mode == MainPresenter.Mode.LIBRARY) {
            library.focusSearch();
            return;
        }
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    @Override
    public void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, plain(message), getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-books " + AmadeBooksApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        if (fullScreen) setFullScreen(false);
        noteTimer.stop();
        resizeTimer.stop();
        appearanceDialog.dispose();
        dispose();
    }

    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        return text;
    }

    // ---------------------------------------------------------------- dropping files

    /** Files dropped from a file manager: as a file list, or as {@code text/uri-list} under X11. */
    private final class FileDrop extends TransferHandler {
        private static final DataFlavor URI_LIST = uriList();

        private static DataFlavor uriList() {
            try {
                return new DataFlavor("text/uri-list;class=java.lang.String");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || support.isDataFlavorSupported(URI_LIST);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                Transferable t = support.getTransferable();
                Path dropped = null;
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<?> files = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty() && files.getFirst() instanceof File f) dropped = f.toPath();
                } else {
                    for (String line : ((String) t.getTransferData(URI_LIST)).split("\r?\n")) {
                        if (line.startsWith("file:")) {
                            dropped = Path.of(URI.create(line.strip()));
                            break;
                        }
                    }
                }
                if (dropped == null) return false;
                presenter.open(dropped);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ---------------------------------------------------------------- for tests

    MainMenuBar menu() {
        return menuBar;
    }

    LibraryPanel libraryPanel() {
        return library;
    }

    PageView pageView() {
        return pages;
    }

    ComicView comicView() {
        return comic;
    }

    ReaderSidebar sidebar() {
        return sidebar;
    }

    AppearancePanel appearancePanel() {
        return appearancePanel;
    }

    JTextField searchField() {
        return searchField;
    }

    JLabel searchLabel() {
        return searchLabel;
    }

    JLabel statusLabel() {
        return status;
    }

    JLabel noteLabel() {
        return note;
    }

    ProgressStrip progressStrip() {
        return progress;
    }

    boolean showingLibrary() {
        return library.isShowing() || mode == MainPresenter.Mode.LIBRARY;
    }

    Action bookmarkAction() {
        return bookmarkAction;
    }
}
