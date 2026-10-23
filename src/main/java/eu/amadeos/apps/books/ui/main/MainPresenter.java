package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.Settings;
import eu.amadeos.apps.books.domain.comic.ComicBook;
import eu.amadeos.apps.books.domain.comic.ComicSession;
import eu.amadeos.apps.books.domain.comic.Spreads;
import eu.amadeos.apps.books.domain.epub.EpubBook;
import eu.amadeos.apps.books.domain.epub.EpubSession;
import eu.amadeos.apps.books.domain.epub.TocEntry;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.layout.ChapterLayout;
import eu.amadeos.apps.books.domain.layout.ChapterText;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import eu.amadeos.apps.books.domain.layout.Page;
import eu.amadeos.apps.books.domain.layout.PageGeometry;
import eu.amadeos.apps.books.domain.layout.RunStyle;
import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.Bookmark;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.apps.books.domain.library.LibraryScanner;
import eu.amadeos.apps.books.domain.library.Shelf;
import eu.amadeos.apps.books.domain.library.ShelfFilter;
import eu.amadeos.apps.books.domain.library.SortOrder;
import eu.amadeos.apps.books.repository.BooksPreferences;
import eu.amadeos.apps.books.repository.LibraryCache;
import eu.amadeos.apps.books.repository.ReadingStates;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * The reader's logic: the library with its filters, opening a book or comic, turning pages, the table of contents,
 * bookmarks, search, links, the appearance and the reading position, which is saved at every page turn.
 * Slow work (opening, scanning, setting a chapter, decoding comic pages) runs on the background executor; its
 * results come back on the Swing thread, and results for a book that is no longer open are dropped.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    static final int SEARCH_LIMIT = 500;
    static final int HISTORY = 50;
    /** Zoom steps for comics, on top of the fit. */
    static final double[] ZOOMS = {0.5, 0.67, 0.8, 1, 1.25, 1.5, 2, 3};

    enum Mode { LIBRARY, BOOK, COMIC }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final BooksPreferences preferences;
    private final ReadingStates states;
    private final LibraryCache cache;
    private final Hyphenators hyphenators;
    private final DesktopLauncher launcher;
    private final Executor background;
    private final Executor ui;
    private final LongSupplier clock;
    private final Runnable exit;

    private Settings settings = Settings.DEFAULT;
    private Mode mode = Mode.LIBRARY;
    private List<LibraryEntry> entries = List.of();
    private ShelfFilter filter = ShelfFilter.ALL;
    private String query = "";
    private boolean scanning;

    private int generation;
    private Path file;
    private BookState state = BookState.NEW;
    private EpubSession epub;
    private Appearance appearance = Appearance.DEFAULT;
    private Position position = Position.START;
    private ChapterLayout layout;
    private PageGeometry geometry;
    private int chapter;
    private int page;
    private final Deque<Position> back = new ArrayDeque<>();
    private final Deque<Position> forward = new ArrayDeque<>();
    private SearchState search = SearchState.NONE;

    private ComicSession comic;
    private List<Spreads.Spread> spreads = List.of();
    private int spread;
    private boolean rightToLeft;
    private boolean comicTwoPages;
    private double zoom = 1;

    /**
     * @param locales    null in tests: no language menu
     * @param background runs slow work; the window's executor in tests
     * @param exit       ends the program after the window closed
     */
    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, BooksPreferences preferences,
                         ReadingStates states, LibraryCache cache, Hyphenators hyphenators, DesktopLauncher launcher,
                         Executor background, Executor ui, LongSupplier clock, Runnable exit) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.preferences = preferences;
        this.states = states;
        this.cache = cache;
        this.hyphenators = hyphenators;
        this.launcher = launcher;
        this.background = background;
        this.ui = ui;
        this.clock = clock;
        this.exit = exit;
    }

    // --8<-- [start:start]
    /**
     * Shows the shelf from the cache at once and scans the library folders. A file on the command line opens
     * directly; without one the book read last opens again when the settings say so.
     */
    public void start(List<String> arguments) {
        EdtExecutor.requireEdt();
        settings = preferences.settings();
        view.showSettings(settings);
        entries = cache.load();
        showShelf();
        rescan();
        Optional<Path> argument = arguments.stream().filter(a -> !a.startsWith("-")).findFirst().map(MainPresenter::path);
        if (argument.isPresent()) open(argument.get());
        else if (settings.reopenLast()) preferences.lastBook().filter(Files::isRegularFile).ifPresent(this::open);
    }
    // --8<-- [end:start]

    /** A command-line argument: a path or a {@code file://} URI as file managers pass them. */
    static Path path(String argument) {
        if (argument.startsWith("file://")) {
            try {
                return Path.of(java.net.URI.create(argument));
            } catch (IllegalArgumentException e) {
                return Path.of(argument.substring("file://".length()));
            }
        }
        return Path.of(argument);
    }

    // ---------------------------------------------------------------- state for the window and tests

    public Settings settings() {
        return settings;
    }

    Mode mode() {
        return mode;
    }

    public Position position() {
        return position;
    }

    public int chapter() {
        return chapter;
    }

    public int page() {
        return page;
    }

    public Appearance appearance() {
        return appearance;
    }

    public SearchState search() {
        return search;
    }

    List<LibraryEntry> entries() {
        return entries;
    }

    ShelfFilter filter() {
        return filter;
    }

    int spread() {
        return spread;
    }

    boolean rightToLeft() {
        return rightToLeft;
    }

    /** The number of pages of the open comic, 0 when none is open. */
    public int comicPageCount() {
        return comic == null ? 0 : comic.pageCount();
    }

    /** The comic's pages on screen carry a bookmark. */
    public boolean comicBookmarked() {
        if (comic == null || spreads.isEmpty()) return false;
        int first = spreads.get(spread).first();
        return state.bookmarks().stream().anyMatch(b -> b.position().chapter() == first);
    }

    // ================================================================ library

    // --8<-- [start:rescan]
    /** Scans the library folders and the books opened from elsewhere, on the background thread. */
    public void rescan() {
        if (scanning) return;
        scanning = true;
        view.showScanning(true);
        List<Path> folders = preferences.folders();
        List<Path> elsewhere = new ArrayList<>();
        for (LibraryEntry e : entries) {
            if (folders.stream().noneMatch(f -> e.file().startsWith(f)) && states.get(e.file()).started()) {
                elsewhere.add(e.file());
            }
        }
        preferences.lastBook().filter(p -> folders.stream().noneMatch(p::startsWith)).ifPresent(elsewhere::add);
        Map<Path, LibraryEntry> known = new HashMap<>();
        for (LibraryEntry e : entries) known.put(e.file(), e);
        LibraryScanner scanner = new LibraryScanner(cache.coverDirectory(), clock);
        background.execute(() -> {
            List<LibraryEntry> found = scanner.scan(folders, elsewhere, known);
            cache.save(found);
            ui.execute(() -> {
                scanning = false;
                entries = found;
                view.showScanning(false);
                showShelf();
            });
        });
    }
    // --8<-- [end:rescan]

    private Shelf shelf() {
        return new Shelf(entries, e -> states.get(e.file()), msg.locale());
    }

    void showShelf() {
        Shelf shelf = shelf();
        Map<ShelfFilter, Integer> facets = shelf.facets();
        if (!facets.containsKey(filter)) filter = ShelfFilter.ALL;
        view.showLibrary(facets, filter, shelf.books(filter, query, settings.sort()), e -> states.get(e.file()));
    }

    public void setFilter(ShelfFilter newFilter) {
        filter = newFilter == null ? ShelfFilter.ALL : newFilter;
        showShelf();
    }

    public void setQuery(String text) {
        query = text == null ? "" : text;
        showShelf();
    }

    public void setSort(SortOrder order) {
        changeSettings(settings.withSort(order));
        showShelf();
    }

    public void setListView(boolean list) {
        changeSettings(settings.withListView(list));
    }

    public void setFinished(LibraryEntry entry, boolean finished) {
        states.put(entry.file(), states.get(entry.file()).withFinished(finished));
        showShelf();
    }

    public void addToCollection(LibraryEntry entry) {
        view.askCollection(states.collections()).map(String::strip).filter(s -> !s.isEmpty()).ifPresent(name -> {
            states.put(entry.file(), states.get(entry.file()).withCollection(name, true));
            showShelf();
        });
    }

    public void removeFromCollection(LibraryEntry entry, String name) {
        states.put(entry.file(), states.get(entry.file()).withCollection(name, false));
        showShelf();
    }

    /** Shows the book's folder in the file manager. */
    public void showInFolder(LibraryEntry entry) {
        try {
            Path folder = entry.file().getParent();
            if (folder != null) launcher.open(folder);
        } catch (IOException e) {
            view.showError(msg.format("error.folder", e.getMessage()));
        }
    }

    // --8<-- [start:add]
    /** Copies chosen books into the first library folder; a book with the same name there is left alone. */
    public void addBooks() {
        List<Path> chosen = view.chooseBooks(preferences.lastFolder());
        if (chosen.isEmpty()) return;
        if (chosen.getFirst().getParent() != null) preferences.setLastFolder(chosen.getFirst().getParent());
        Path target = preferences.mainFolder();
        int copied = 0;
        List<String> skipped = new ArrayList<>();
        for (Path source : chosen) {
            if (BookFormat.of(source).isEmpty()) continue;
            try {
                Files.createDirectories(target);
                if (source.toAbsolutePath().normalize().startsWith(target.toAbsolutePath().normalize())) continue;
                Files.copy(source, target.resolve(source.getFileName()));
                copied++;
            } catch (FileAlreadyExistsException e) {
                skipped.add(source.getFileName().toString());
            } catch (IOException e) {
                view.showError(msg.format("error.copy", source.getFileName(), e.getMessage()));
            }
        }
        if (!skipped.isEmpty()) view.showError(msg.format("error.exists", String.join(", ", skipped), target));
        if (copied > 0) view.showStatus(msg.format("status.added", copied));
        rescan();
    }
    // --8<-- [end:add]

    public void editFolders() {
        view.editFolders(preferences.folders()).ifPresent(folders -> {
            preferences.setFolders(folders);
            rescan();
        });
    }

    // ================================================================ opening

    public void chooseAndOpen() {
        view.chooseFile(preferences.lastFolder()).ifPresent(chosen -> {
            if (chosen.getParent() != null) preferences.setLastFolder(chosen.getParent());
            open(chosen);
        });
    }

    public void open(LibraryEntry entry) {
        open(entry.file());
    }

    // --8<-- [start:open]
    /** Opens an EPUB or a comic on the background thread and shows it at the place where the reader stopped. */
    public void open(Path chosen) {
        Path target = chosen.toAbsolutePath().normalize();
        Optional<BookFormat> format = BookFormat.of(target);
        if (format.isEmpty()) {
            view.showError(msg.format("error.format", target.getFileName()));
            return;
        }
        int gen = ++generation;
        background.execute(() -> {
            try {
                if (format.get().comic()) {
                    ComicSession session = new ComicSession(ComicBook.open(target));
                    session.wide();
                    ui.execute(() -> {
                        if (gen == generation) comicOpened(target, session);
                        else closeQuietly(session);
                    });
                } else {
                    EpubSession session = new EpubSession(EpubBook.open(target), hyphenators);
                    ui.execute(() -> {
                        if (gen == generation) bookOpened(target, session);
                        else closeQuietly(session);
                    });
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot open {}", target, e);
                ui.execute(() -> {
                    if (gen == generation) view.showError(msg.format("error.open", target.getFileName(), e.getMessage()));
                });
            }
        });
    }
    // --8<-- [end:open]

    private void bookOpened(Path target, EpubSession session) {
        closeBook();
        file = target;
        epub = session;
        mode = Mode.BOOK;
        state = states.get(target);
        appearance = state.appearance().orElse(preferences.appearance());
        position = state.position().chapter() < session.chapterCount() ? state.position() : Position.START;
        chapter = position.chapter();
        layout = null;
        back.clear();
        forward.clear();
        search = SearchState.NONE;
        preferences.setLastBook(target);
        view.showBook(session.book().info(), session.book().toc());
        view.showAppearance(appearance, false);
        view.showBookmarks(state.bookmarks());
        view.showSearch(search);
        view.showHistory(false, false);
        relayout();
        int gen = generation;
        background.execute(() -> {
            session.lengths();
            boolean fonts = session.hasFonts();
            ui.execute(() -> {
                if (gen != generation) return;
                view.showAppearance(appearance, fonts);
                showPlace();
            });
        });
    }

    private void comicOpened(Path target, ComicSession session) {
        closeBook();
        file = target;
        comic = session;
        mode = Mode.COMIC;
        state = states.get(target);
        rightToLeft = state.rightToLeft().orElse(session.book().rightToLeft());
        comicTwoPages = state.twoPages().orElse(settings.comicTwoPages());
        zoom = 1;
        spreads = session.spreads(comicTwoPages);
        int start = Math.min(state.position().chapter(), session.pageCount() - 1);
        spread = Spreads.indexOf(spreads, start);
        preferences.setLastBook(target);
        view.showComic(session.book().info(), session.pageCount());
        showComicSpread();
    }

    /** Back to the shelf; the place in the book is saved. */
    public void showLibraryView() {
        if (mode == Mode.LIBRARY) return;
        if (view.fullScreen()) view.setFullScreen(false);
        closeBook();
        mode = Mode.LIBRARY;
        showShelf();
    }

    private void closeBook() {
        if (file != null) saveState();
        generation++;
        closeQuietly(epub);
        closeQuietly(comic);
        epub = null;
        comic = null;
        file = null;
        layout = null;
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            // nothing to do about a file that does not close
        }
    }

    // ================================================================ books: pages

    // --8<-- [start:relayout]
    /**
     * Sets the current chapter for the window's size and appearance, then shows the page with the reading
     * position. Called after opening, resizing, and every change of appearance or two-page view.
     */
    public void relayout() {
        if (mode != Mode.BOOK || epub == null) return;
        Dimension area = view.readingArea();
        if (area.width < 50 || area.height < 50) return;
        PageGeometry g = PageGeometry.of(area.width, area.height, settings.twoPages(), appearance);
        Optional<ChapterLayout> ready = epub.cachedLayout(position.chapter(), appearance, g.textWidth(), g.textHeight());
        if (ready.isPresent()) {
            show(g, position.chapter(), ready.get(), ready.get().pageOf(position.offset()));
            return;
        }
        int gen = generation;
        EpubSession session = epub;
        Position at = position;
        Appearance a = appearance;
        background.execute(() -> {
            ChapterLayout l = session.layout(at.chapter(), a, g.textWidth(), g.textHeight());
            ui.execute(() -> {
                if (gen == generation && at.equals(position) && a.equals(appearance)) show(g, at.chapter(), l, l.pageOf(at.offset()));
            });
        });
    }
    // --8<-- [end:relayout]

    /** Shows the spread with page {@code index} of the chapter; the position becomes the first page's start. */
    private void show(PageGeometry g, int newChapter, ChapterLayout newLayout, int index) {
        geometry = g;
        chapter = newChapter;
        layout = newLayout;
        page = Math.clamp(index - index % g.columns(), 0, newLayout.pageCount() - 1);
        Page first = newLayout.pages().get(page);
        if (position.chapter() != chapter || !onSpread(position.offset())) {
            position = new Position(chapter, first.start());
        }
        String title = epub.book().info().title();
        String chapterTitle = epub.tocEntry(new Position(chapter, first.start())).map(TocEntry::label).orElse(title);
        view.showPages(epub, newLayout, g, chapter, page, new String[]{title, chapterTitle}, settings.theme());
        showPlace();
        saveState();
        prefetch();
    }

    private boolean onSpread(int offset) {
        int last = Math.min(layout.pageCount() - 1, page + geometry.columns() - 1);
        return offset >= layout.pages().get(page).start() && (last == layout.pageCount() - 1 || offset < layout.pages().get(last + 1).start());
    }

    /** Sets the next chapter in the background, so turning into it does not wait. */
    private void prefetch() {
        if (chapter + 1 >= epub.chapterCount()) return;
        EpubSession session = epub;
        int next = chapter + 1;
        Appearance a = appearance;
        PageGeometry g = geometry;
        background.execute(() -> session.layout(next, a, g.textWidth(), g.textHeight()));
    }

    private void showPlace() {
        if (mode != Mode.BOOK || layout == null) return;
        List<TocEntry> toc = epub.book().toc();
        Optional<TocEntry> entry = epub.tocEntry(position);
        String chapterTitle = entry.map(TocEntry::label).orElse("");
        double progress = epub.lengthsKnown() ? epub.progress(position) : -1;
        List<Double> starts = epub.lengthsKnown() ? epub.chapterStarts() : List.of();
        view.showPlace(chapterTitle, page + 1, layout.pageCount(), progress, starts, bookmarkOnSpread().isPresent());
        view.showTocEntry(entry.map(toc::indexOf).orElse(-1));
    }

    private Optional<Bookmark> bookmarkOnSpread() {
        if (layout == null) return Optional.empty();
        int last = Math.min(layout.pageCount() - 1, page + geometry.columns() - 1);
        int from = layout.pages().get(page).start();
        int to = last + 1 < layout.pageCount() ? layout.pages().get(last + 1).start() : Integer.MAX_VALUE;
        return state.bookmarks().stream().filter(b -> b.position().chapter() == chapter
                && b.position().offset() >= from && b.position().offset() < to).findFirst();
    }

    // --8<-- [start:turn]
    public void next() {
        if (mode == Mode.COMIC) {
            comicStep(1);
            return;
        }
        if (layout == null) return;
        if (page + geometry.columns() < layout.pageCount()) {
            show(geometry, chapter, layout, page + geometry.columns());
        } else if (chapter + 1 < epub.chapterCount()) {
            goTo(new Position(chapter + 1, 0), false);
        } else {
            view.beep();
        }
    }

    public void previous() {
        if (mode == Mode.COMIC) {
            comicStep(-1);
            return;
        }
        if (layout == null) return;
        if (page > 0) {
            show(geometry, chapter, layout, page - geometry.columns());
        } else if (chapter > 0) {
            // the last page of the chapter before
            goTo(new Position(chapter - 1, Integer.MAX_VALUE), false);
        } else {
            view.beep();
        }
    }
    // --8<-- [end:turn]

    public void first() {
        if (mode == Mode.COMIC) {
            spread = 0;
            showComicSpread();
        } else if (mode == Mode.BOOK) {
            goTo(Position.START, true);
        }
    }

    public void last() {
        if (mode == Mode.COMIC) {
            spread = spreads.size() - 1;
            showComicSpread();
        } else if (mode == Mode.BOOK) {
            goTo(new Position(epub.chapterCount() - 1, Integer.MAX_VALUE), true);
        }
    }

    /**
     * Shows the place; with {@code remember} the place before goes into the history for Back (links, contents,
     * bookmarks, search), not for turning pages.
     */
    public void goTo(Position target, boolean remember) {
        if (mode != Mode.BOOK) return;
        if (remember) {
            back.push(position);
            while (back.size() > HISTORY) back.removeLast();
            forward.clear();
            view.showHistory(true, false);
        }
        int c = Math.clamp(target.chapter(), 0, epub.chapterCount() - 1);
        int offset = target.offset() == Integer.MAX_VALUE ? Math.max(0, epub.text(c).length() - 1) : target.offset();
        position = new Position(c, offset);
        relayout();
    }

    public void goBack() {
        if (back.isEmpty()) return;
        forward.push(position);
        position = back.pop();
        view.showHistory(!back.isEmpty(), true);
        relayout();
    }

    public void goForward() {
        if (forward.isEmpty()) return;
        back.push(position);
        position = forward.pop();
        view.showHistory(true, !forward.isEmpty());
        relayout();
    }

    /** A click on the progress bar: the place at that share of the book. */
    public void goToFraction(double fraction) {
        if (mode == Mode.BOOK && epub.lengthsKnown()) goTo(epub.at(fraction), true);
        if (mode == Mode.COMIC) {
            spread = Math.clamp((int) Math.round(fraction * (spreads.size() - 1)), 0, spreads.size() - 1);
            showComicSpread();
        }
    }

    public void openToc(TocEntry entry) {
        if (epub != null) epub.resolve(entry).ifPresent(p -> goTo(p, true));
    }

    public void openBookmark(Bookmark bookmark) {
        if (mode == Mode.BOOK) goTo(bookmark.position(), true);
        if (mode == Mode.COMIC) {
            spread = Spreads.indexOf(spreads, bookmark.position().chapter());
            showComicSpread();
        }
    }

    // ================================================================ bookmarks

    // --8<-- [start:bookmark]
    /** Sets a bookmark at the top of the page, or removes the one on the pages shown. */
    public void toggleBookmark() {
        if (mode == Mode.BOOK && layout != null) {
            Optional<Bookmark> existing = bookmarkOnSpread();
            if (existing.isPresent()) {
                state = state.withoutBookmark(existing.get());
                view.showStatus(msg.get("status.bookmark.removed"));
            } else {
                int start = layout.pages().get(page).start();
                state = state.toggleBookmark(new Bookmark(new Position(chapter, start), epub.text(chapter).excerpt(start, 60),
                        clock.getAsLong()));
                view.showStatus(msg.get("status.bookmark.set"));
            }
            saveState();
            view.showBookmarks(state.bookmarks());
            showPlace();
        } else if (mode == Mode.COMIC) {
            Spreads.Spread s = spreads.get(spread);
            Bookmark mark = new Bookmark(new Position(s.first(), 0), msg.format("comic.page", s.first() + 1), clock.getAsLong());
            state = state.toggleBookmark(mark);
            saveState();
            view.showBookmarks(state.bookmarks());
        }
    }
    // --8<-- [end:bookmark]

    public void removeBookmark(Bookmark bookmark) {
        state = state.withoutBookmark(bookmark);
        saveState();
        view.showBookmarks(state.bookmarks());
        showPlace();
    }

    // ================================================================ search

    // --8<-- [start:search]
    /** Searches the whole book on the background thread and shows the first hit after the current place. */
    public void search(String text, boolean backwards) {
        if (mode != Mode.BOOK) return;
        String q = text == null ? "" : text.strip();
        if (q.isEmpty()) {
            clearSearch();
            return;
        }
        if (q.equals(search.query()) && !search.hits().isEmpty() && !search.running()) {
            int n = search.hits().size();
            int next = ((search.current() + (backwards ? -1 : 1)) % n + n) % n;
            showHit(new SearchState(q, search.hits(), next, false));
            return;
        }
        search = new SearchState(q, List.of(), -1, true);
        view.showSearch(search);
        int gen = generation;
        EpubSession session = epub;
        background.execute(() -> {
            List<EpubSession.SearchHit> hits = session.search(q, SEARCH_LIMIT);
            ui.execute(() -> {
                if (gen != generation || !q.equals(search.query())) return;
                int index = -1;
                for (int i = 0; i < hits.size() && index < 0; i++) {
                    if (new Position(hits.get(i).chapter(), hits.get(i).offset()).compareTo(position) >= 0) index = i;
                }
                if (index < 0 && !hits.isEmpty()) index = 0;
                if (hits.isEmpty()) {
                    search = new SearchState(q, hits, -1, false);
                    view.showSearch(search);
                    view.beep();
                } else {
                    showHit(new SearchState(q, hits, index, false));
                }
            });
        });
    }
    // --8<-- [end:search]

    private void showHit(SearchState s) {
        search = s;
        view.showSearch(s);
        EpubSession.SearchHit hit = s.currentHit();
        if (hit != null) goTo(new Position(hit.chapter(), hit.offset()), false);
    }

    public void findNext() {
        search(search.query(), false);
    }

    public void findPrevious() {
        search(search.query(), true);
    }

    public void clearSearch() {
        search = SearchState.NONE;
        view.showSearch(search);
    }

    public void find() {
        view.focusSearch();
    }

    // ================================================================ links and text

    // --8<-- [start:follow]
    /** A click on the text at a chapter offset: follows a link there, if there is one. */
    public boolean clickedText(int clickedChapter, int offset) {
        if (epub == null) return false;
        Optional<RunStyle> link = epub.text(clickedChapter).linkAt(offset);
        if (link.isEmpty()) return false;
        String target = link.get().link();
        if (eu.amadeos.apps.books.domain.epub.ZipPaths.external(target)) {
            try {
                launcher.browse(target);
            } catch (IOException e) {
                view.showError(msg.format("error.link", target, e.getMessage()));
            }
            return true;
        }
        if (link.get().noteRef()) {
            Optional<String> note = epub.footnote(target);
            if (note.isPresent()) {
                view.showFootnote(note.get());
                return true;
            }
        }
        Optional<Position> where = epub.resolve(target);
        where.ifPresent(p -> goTo(p, true));
        return where.isPresent();
    }
    // --8<-- [end:follow]

    /** The address of a link at the offset, for the status bar while the mouse is over it. */
    public Optional<String> linkAt(int linkChapter, int offset) {
        if (epub == null) return Optional.empty();
        return epub.text(linkChapter).linkAt(offset).map(RunStyle::link);
    }

    public void copy() {
        if (epub == null) return;
        view.selection().ifPresent(sel -> {
            ChapterText text = epub.text(sel[0]);
            int from = Math.clamp(sel[1], 0, text.length());
            int to = Math.clamp(sel[2], from, text.length());
            view.copyToClipboard(text.plain().substring(from, to).replace(String.valueOf('￼'), "").strip());
        });
    }

    // ================================================================ appearance and view

    // --8<-- [start:appearance]
    /** A change in the Appearance panel: saved for this book and as the default for books without their own. */
    public void setAppearance(Appearance changed) {
        if (changed.equals(appearance)) return;
        appearance = changed;
        preferences.setAppearance(changed);
        if (file != null && mode == Mode.BOOK) state = state.withAppearance(changed);
        saveState();
        relayout();
    }
    // --8<-- [end:appearance]

    public void setTheme(ReadTheme theme) {
        changeSettings(settings.withTheme(theme));
        relayout();
    }

    public void setTwoPages(boolean on) {
        if (mode == Mode.COMIC) {
            comicTwoPages = on;
            state = state.withTwoPages(on);
            int first = spreads.get(spread).first();
            spreads = comic.spreads(on);
            spread = Spreads.indexOf(spreads, first);
            saveState();
            showComicSpread();
            return;
        }
        changeSettings(settings.withTwoPages(on));
        relayout();
    }

    public boolean twoPages() {
        return mode == Mode.COMIC ? comicTwoPages : settings.twoPages();
    }

    public void setSidebar(boolean on) {
        changeSettings(settings.withSidebar(on, settings.bookmarksTab()));
    }

    public void toggleSidebar() {
        setSidebar(!settings.sidebar());
    }

    public void showBookmarksTab(boolean bookmarks) {
        changeSettings(settings.withSidebar(settings.sidebar(), bookmarks));
    }

    public void setReopenLast(boolean on) {
        changeSettings(settings.withReopenLast(on));
    }

    public void toggleFullScreen() {
        if (mode != Mode.LIBRARY) view.setFullScreen(!view.fullScreen());
    }

    private void changeSettings(Settings changed) {
        settings = changed;
        preferences.setSettings(changed);
        view.showSettings(changed);
    }

    // ================================================================ comics

    private void comicStep(int direction) {
        int target = spread + direction;
        if (target < 0 || target >= spreads.size()) {
            view.beep();
            return;
        }
        spread = target;
        showComicSpread();
    }

    // --8<-- [start:comic]
    /** Shows the current spread; pages not decoded yet are decoded in the background and shown when ready. */
    private void showComicSpread() {
        if (comic == null) return;
        Spreads.Spread s = spreads.get(spread);
        List<Integer> reading = s.second() >= 0 ? List.of(s.first(), s.second()) : List.of(s.first());
        List<Integer> shown = rightToLeft ? reading.reversed() : reading;
        List<BufferedImage> pictures = new ArrayList<>();
        boolean missing = false;
        for (int p : shown) {
            BufferedImage image = comic.cached(p).orElse(null);
            missing |= image == null;
            pictures.add(image);
        }
        view.showComicPages(pictures, shown.stream().map(p -> p + 1).toList(), settings.comicFit(), zoom, rightToLeft,
                comicTwoPages);
        state = state.read(new Position(s.first(), 0), comic.pageCount() <= 1 ? 1 : (double) s.last() / (comic.pageCount() - 1),
                clock.getAsLong());
        saveState();
        int gen = generation;
        ComicSession session = comic;
        int current = spread;
        if (missing) {
            background.execute(() -> {
                for (int p : shown) session.page(p);
                ui.execute(() -> {
                    if (gen == generation && current == spread) showComicSpread();
                });
            });
        }
        // the next spread, so turning the page does not wait
        if (spread + 1 < spreads.size()) {
            Spreads.Spread next = spreads.get(spread + 1);
            background.execute(() -> {
                session.page(next.first());
                if (next.second() >= 0) session.page(next.second());
            });
        }
    }
    // --8<-- [end:comic]

    public void setComicFit(ComicFit fit) {
        zoom = 1;
        changeSettings(settings.withComicFit(fit));
        showComicSpread();
    }

    public void setRightToLeft(boolean rtl) {
        if (comic == null) return;
        rightToLeft = rtl;
        state = state.withRightToLeft(rtl);
        saveState();
        showComicSpread();
    }

    public void zoomIn() {
        stepZoom(1);
    }

    public void zoomOut() {
        stepZoom(-1);
    }

    public void resetZoom() {
        zoom = 1;
        showComicSpread();
    }

    private void stepZoom(int direction) {
        if (mode == Mode.BOOK) {
            setAppearance(appearance.withSize(appearance.size() + 2 * direction));
            view.showAppearance(appearance, epub.hasFonts());
            return;
        }
        if (mode != Mode.COMIC) return;
        int index = 0;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (Math.abs(ZOOMS[i] - zoom) < 1e-6) index = i;
        }
        zoom = ZOOMS[Math.clamp(index + direction, 0, ZOOMS.length - 1)];
        showComicSpread();
    }

    /** Left and right arrows: in a right-to-left comic the left arrow goes forward. */
    public void arrow(boolean right) {
        if (mode == Mode.COMIC && rightToLeft) right = !right;
        if (right) next();
        else previous();
    }

    // ================================================================ saving and the rest

    // --8<-- [start:save]
    private void saveState() {
        if (file == null) return;
        if (mode == Mode.BOOK && epub != null) {
            double progress = epub.lengthsKnown() ? epub.progress(position) : state.progress();
            boolean atEnd = layout != null && chapter == epub.chapterCount() - 1
                    && page + geometry.columns() >= layout.pageCount();
            state = state.read(position, atEnd ? 1 : progress, clock.getAsLong());
        }
        states.put(file, state);
    }
    // --8<-- [end:save]

    public void setLanguage(Optional<Language> language) {
        if (locales == null) return;
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-books"));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void exit() {
        if (file != null) saveState();
        closeBook();
        view.close();
        exit.run();
    }
}
