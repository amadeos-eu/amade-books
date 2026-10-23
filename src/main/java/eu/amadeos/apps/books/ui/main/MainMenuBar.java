package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.Settings;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** File, Edit, View, Go, Options and Help; items for books and comics are enabled while one is open. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Messages msg;
    private final MainPresenter presenter;
    private final JCheckBoxMenuItem sidebar;
    private final JRadioButtonMenuItem contentsTab;
    private final JRadioButtonMenuItem bookmarksTab;
    private final JCheckBoxMenuItem twoPages;
    private final JCheckBoxMenuItem rightToLeft;
    private final JCheckBoxMenuItem fullScreen;
    private final JCheckBoxMenuItem reopen;
    private final Map<ReadTheme, JRadioButtonMenuItem> themes = new EnumMap<>(ReadTheme.class);
    private final Map<ComicFit, JRadioButtonMenuItem> fits = new EnumMap<>(ComicFit.class);
    private final JMenuItem back;
    private final JMenuItem forward;
    private final List<JMenuItem> readerItems = new ArrayList<>();
    private final List<JMenuItem> bookItems = new ArrayList<>();
    private final List<JMenuItem> comicItems = new ArrayList<>();
    private boolean updating;

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.presenter = presenter;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item("menu.file.open", key(KeyEvent.VK_O, CTRL), presenter::chooseAndOpen));
        file.add(item("menu.file.add", null, presenter::addBooks));
        file.add(reader(item("menu.file.library", key(KeyEvent.VK_L, CTRL), presenter::showLibraryView)));
        file.addSeparator();
        file.add(item("menu.file.folders", null, presenter::editFolders));
        file.add(item("menu.file.rescan", key(KeyEvent.VK_F5, 0), presenter::rescan));
        file.addSeparator();
        file.add(item("menu.file.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(book(item("menu.edit.copy", key(KeyEvent.VK_C, CTRL), presenter::copy)));
        edit.addSeparator();
        edit.add(book(item("menu.edit.find", key(KeyEvent.VK_F, CTRL), presenter::find)));
        edit.add(book(item("menu.edit.find-next", key(KeyEvent.VK_F3, 0), presenter::findNext)));
        edit.add(book(item("menu.edit.find-previous", key(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK),
                presenter::findPrevious)));

        JMenu view = menu("menu.view");
        sidebar = check("menu.view.sidebar", key(KeyEvent.VK_F9, 0), () -> presenter.setSidebar(sidebarSelected()));
        view.add(reader(sidebar));
        ButtonGroup tabs = new ButtonGroup();
        contentsTab = radio("menu.view.contents", tabs, () -> presenter.showBookmarksTab(false));
        bookmarksTab = radio("menu.view.bookmarks", tabs, () -> presenter.showBookmarksTab(true));
        view.add(book(contentsTab));
        view.add(reader(bookmarksTab));
        view.addSeparator();
        twoPages = check("menu.view.two-pages", key(KeyEvent.VK_2, CTRL), () -> presenter.setTwoPages(twoPagesSelected()));
        view.add(reader(twoPages));
        ButtonGroup themeGroup = new ButtonGroup();
        for (ReadTheme t : ReadTheme.values()) {
            JRadioButtonMenuItem item = radio("menu.view.theme." + t.key(), themeGroup, () -> presenter.setTheme(t));
            themes.put(t, item);
            view.add(item);
        }
        view.add(book(item("menu.view.bigger", key(KeyEvent.VK_PLUS, CTRL), presenter::zoomIn)));
        view.add(book(item("menu.view.smaller", key(KeyEvent.VK_MINUS, CTRL), presenter::zoomOut)));
        view.addSeparator();
        JMenu comicMenu = Mnemonics.apply(new JMenu(), msg.get("menu.view.comic"));
        comicMenu.setName("menu.view.comic");
        ButtonGroup fitGroup = new ButtonGroup();
        for (ComicFit f : ComicFit.values()) {
            JRadioButtonMenuItem item = radio("menu.comic.fit." + f.key(), fitGroup, () -> presenter.setComicFit(f));
            fits.put(f, item);
            comicMenu.add(item);
        }
        comicMenu.addSeparator();
        comicMenu.add(item("menu.comic.zoom-in", null, presenter::zoomIn));
        comicMenu.add(item("menu.comic.zoom-out", null, presenter::zoomOut));
        comicMenu.add(item("menu.comic.actual-size", key(KeyEvent.VK_0, CTRL), presenter::resetZoom));
        comicMenu.addSeparator();
        rightToLeft = check("menu.comic.right-to-left", null, () -> presenter.setRightToLeft(rightToLeftSelected()));
        comicMenu.add(rightToLeft);
        view.add(comic(comicMenu));
        view.addSeparator();
        fullScreen = check("menu.view.full-screen", key(KeyEvent.VK_F11, 0), presenter::toggleFullScreen);
        view.add(reader(fullScreen));

        JMenu go = menu("menu.go");
        go.add(reader(item("menu.go.previous", key(KeyEvent.VK_PAGE_UP, CTRL), presenter::previous)));
        go.add(reader(item("menu.go.next", key(KeyEvent.VK_PAGE_DOWN, CTRL), presenter::next)));
        go.add(reader(item("menu.go.first", key(KeyEvent.VK_HOME, CTRL), presenter::first)));
        go.add(reader(item("menu.go.last", key(KeyEvent.VK_END, CTRL), presenter::last)));
        go.addSeparator();
        back = item("menu.go.back", key(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), presenter::goBack);
        forward = item("menu.go.forward", key(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), presenter::goForward);
        go.add(back);
        go.add(forward);
        go.addSeparator();
        go.add(reader(item("menu.go.bookmark", key(KeyEvent.VK_D, CTRL), presenter::toggleBookmark)));

        JMenu options = menu("menu.options");
        reopen = check("menu.options.reopen", null, () -> presenter.setReopenLast(reopenSelected()));
        options.add(reopen);
        if (locales != null) options.add(languageMenu(locales));

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
        showHistory(false, false);
        setMode(MainPresenter.Mode.LIBRARY);
    }

    private boolean sidebarSelected() {
        return sidebar.isSelected();
    }

    private boolean twoPagesSelected() {
        return twoPages.isSelected();
    }

    private boolean rightToLeftSelected() {
        return rightToLeft.isSelected();
    }

    private boolean reopenSelected() {
        return reopen.isSelected();
    }

    void update(Settings settings) {
        updating = true;
        try {
            sidebar.setSelected(settings.sidebar());
            (settings.bookmarksTab() ? bookmarksTab : contentsTab).setSelected(true);
            themes.get(settings.theme()).setSelected(true);
            fits.get(settings.comicFit()).setSelected(true);
            reopen.setSelected(settings.reopenLast());
            twoPages.setSelected(presenter.twoPages());
        } finally {
            updating = false;
        }
    }

    void showComicState(boolean rtl, boolean two, ComicFit fit) {
        updating = true;
        try {
            rightToLeft.setSelected(rtl);
            twoPages.setSelected(two);
            fits.get(fit).setSelected(true);
        } finally {
            updating = false;
        }
    }

    void showFullScreen(boolean on) {
        updating = true;
        try {
            fullScreen.setSelected(on);
        } finally {
            updating = false;
        }
    }

    void setMode(MainPresenter.Mode mode) {
        for (JMenuItem item : readerItems) item.setEnabled(mode != MainPresenter.Mode.LIBRARY);
        for (JMenuItem item : bookItems) item.setEnabled(mode == MainPresenter.Mode.BOOK);
        for (JMenuItem item : comicItems) item.setEnabled(mode == MainPresenter.Mode.COMIC);
        for (JRadioButtonMenuItem item : themes.values()) item.setEnabled(mode != MainPresenter.Mode.COMIC);
        if (mode != MainPresenter.Mode.BOOK) {
            back.setEnabled(false);
            forward.setEnabled(false);
        }
    }

    void showHistory(boolean canGoBack, boolean canGoForward) {
        back.setEnabled(canGoBack);
        forward.setEnabled(canGoForward);
    }

    JCheckBoxMenuItem sidebarItem() {
        return sidebar;
    }

    JCheckBoxMenuItem twoPagesItem() {
        return twoPages;
    }

    JRadioButtonMenuItem themeItem(ReadTheme theme) {
        return themes.get(theme);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem reader(JMenuItem item) {
        readerItems.add(item);
        return item;
    }

    private JMenuItem book(JMenuItem item) {
        bookItems.add(item);
        return item;
    }

    private JMenuItem comic(JMenuItem item) {
        comicItems.add(item);
        return item;
    }

    private JRadioButtonMenuItem radio(String key, ButtonGroup group, Runnable action) {
        JRadioButtonMenuItem item = Mnemonics.apply(new JRadioButtonMenuItem(), msg.get(key));
        item.setName(key);
        item.addActionListener(e -> {
            if (!updating) action.run();
        });
        group.add(item);
        return item;
    }

    private JCheckBoxMenuItem check(String key, KeyStroke accelerator, Runnable action) {
        JCheckBoxMenuItem item = Mnemonics.apply(new JCheckBoxMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> {
            if (!updating) action.run();
        });
        return item;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        return item;
    }
}
