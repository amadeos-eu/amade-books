package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.OnEdt;
import eu.amadeos.apps.books.TestBooks;
import eu.amadeos.apps.books.bootstrap.BooksModule;
import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.apps.books.domain.library.ShelfFilter;
import eu.amadeos.apps.books.domain.library.SortOrder;
import eu.amadeos.apps.books.repository.BooksPreferences;
import eu.amadeos.apps.books.repository.LibraryCache;
import eu.amadeos.apps.books.repository.ReadingStates;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The presenter with a fake window; slow work runs at once on the calling thread. */
class MainPresenterTest {
    static final Messages MSG = Messages.load(BooksModule.class, BooksModule.MESSAGES, Locale.ENGLISH);

    @TempDir
    Path dir;

    private final FakeView view = new FakeView();
    private final List<String> browsed = new ArrayList<>();
    private Path books;
    private BooksPreferences preferences;
    private ReadingStates states;
    private MainPresenter presenter;
    private long now = 1_000;
    private boolean exited;

    @BeforeEach
    void setUp() throws Exception {
        books = Files.createDirectories(dir.resolve("Books"));
        preferences = new BooksPreferences(dir.resolve("config/books.properties"), books);
        states = new ReadingStates(dir.resolve("data/library.json"));
        presenter = presenter();
    }

    private MainPresenter presenter() {
        DesktopLauncher launcher = new DesktopLauncher() {
            @Override
            public void browse(String uri) {
                browsed.add(uri);
            }
        };
        return new MainPresenter(view, MSG, null, preferences, states, new LibraryCache(dir.resolve("cache/library-cache.json")),
                new Hyphenators(dir.resolve("no-patterns")), launcher, Runnable::run, Runnable::run, () -> now += 1000,
                () -> exited = true);
    }

    private void start(String... args) throws Exception {
        OnEdt.run(() -> presenter.start(List.of(args)));
    }

    /** Three chapters of running text, a link from the first to the third and a footnote. */
    private Path alice() throws Exception {
        return TestBooks.epub().subject("Fantasy").subject("Classics")
                .chapter("c1.xhtml", "<h1>Down the Rabbit-Hole</h1><p>" + TestBooks.words(900)
                        + " <a href='c3.xhtml#tea'>to the tea party</a> and a note"
                        + "<a epub:type='noteref' href='notes.xhtml#n1'>1</a>.</p>")
                .chapter("c2.xhtml", "<h1>The Pool of Tears</h1><p>Curiouser and curiouser, cried Alice. "
                        + TestBooks.words(300) + "</p>")
                .chapter("c3.xhtml", "<h1>A Mad Tea-Party</h1><p id='tea'>There was a table set out under a tree.</p>")
                .chapter("notes.xhtml", "<ol><li id='n1'>A note about the rabbit.</li></ol>")
                .nav("<li><a href='c1.xhtml'>Down the Rabbit-Hole</a></li><li><a href='c2.xhtml'>The Pool of Tears</a></li>"
                        + "<li><a href='c3.xhtml'>A Mad Tea-Party</a></li>")
                .write(books.resolve("alice.epub"));
    }

    private Path manga() throws Exception {
        Map<String, int[]> pages = new LinkedHashMap<>();
        pages.put("p1.png", new int[]{60, 90});
        pages.put("p2.png", new int[]{60, 90});
        pages.put("p3.png", new int[]{60, 90});
        pages.put("p4.png", new int[]{60, 90});
        return TestBooks.cbz(books.resolve("kaze.cbz"), pages, """
                <?xml version="1.0"?><ComicInfo><Series>Kaze</Series><Number>1</Number>
                <Manga>YesAndRightToLeft</Manga></ComicInfo>""");
    }

    private void open(Path file) throws Exception {
        OnEdt.run(() -> presenter.open(file));
    }

    // ---------------------------------------------------------------- library

    @Test
    void startShowsTheShelfWithFormatsAndGenres() throws Exception {
        alice();
        manga();
        start();
        assertFalse(view.scanning);
        assertEquals(2, view.books.size());
        assertEquals(2, view.facets.get(ShelfFilter.ALL));
        assertEquals(1, view.facets.get(ShelfFilter.of(ShelfFilter.Kind.BOOKS)));
        assertEquals(1, view.facets.get(ShelfFilter.of(ShelfFilter.Kind.COMICS)));
        assertEquals(1, view.facets.get(new ShelfFilter(ShelfFilter.Kind.GENRE, "Fantasy")));
        OnEdt.run(() -> presenter.setFilter(new ShelfFilter(ShelfFilter.Kind.GENRE, "Fantasy")));
        assertEquals(1, view.books.size());
        assertEquals("alice.epub", view.books.getFirst().file().getFileName().toString());
        OnEdt.run(() -> presenter.setQuery("kaze"));
        assertEquals(0, view.books.size());
        OnEdt.run(() -> presenter.setFilter(ShelfFilter.ALL));
        assertEquals(1, view.books.size());
    }

    @Test
    void sortAndListViewAreRemembered() throws Exception {
        start();
        OnEdt.run(() -> {
            presenter.setSort(SortOrder.AUTHOR);
            presenter.setListView(true);
        });
        assertEquals(SortOrder.AUTHOR, preferences.settings().sort());
        assertTrue(preferences.settings().listView());
        assertTrue(view.settings.listView());
    }

    @Test
    void addBooksCopiesIntoTheFirstFolder() throws Exception {
        start();
        Path elsewhere = Files.createDirectories(dir.resolve("Downloads"));
        Path book = TestBooks.epub().title("Moby-Dick").chapter("a.xhtml", "<p>Call me Ishmael.</p>")
                .write(elsewhere.resolve("moby.epub"));
        Files.writeString(elsewhere.resolve("notes.txt"), "not a book");
        view.chosenBooks = List.of(book, elsewhere.resolve("notes.txt"));
        OnEdt.run(presenter::addBooks);
        assertTrue(Files.exists(books.resolve("moby.epub")));
        assertFalse(Files.exists(books.resolve("notes.txt")));
        assertEquals("1 books added to the library", view.status);
        assertEquals(List.of("Moby-Dick"), view.books.stream().map(e -> e.info().title()).toList());
        OnEdt.run(presenter::addBooks);
        assertEquals(1, view.errors.size(), "the second time the book is already there");
    }

    @Test
    void collectionsAndFinishedBooks() throws Exception {
        alice();
        start();
        LibraryEntry entry = view.books.getFirst();
        view.collection = Optional.of(" Holiday ");
        OnEdt.run(() -> presenter.addToCollection(entry));
        assertEquals(1, view.facets.get(new ShelfFilter(ShelfFilter.Kind.COLLECTION, "Holiday")));
        OnEdt.run(() -> presenter.setFinished(entry, true));
        assertEquals(1, view.facets.get(ShelfFilter.of(ShelfFilter.Kind.FINISHED)));
        OnEdt.run(() -> presenter.removeFromCollection(entry, "Holiday"));
        assertFalse(view.facets.containsKey(new ShelfFilter(ShelfFilter.Kind.COLLECTION, "Holiday")));
    }

    @Test
    void otherFilesAreRefused() throws Exception {
        start();
        open(dir.resolve("letter.pdf"));
        assertEquals(List.of("letter.pdf is neither an EPUB book nor a CBZ comic."), view.errors);
    }

    // ---------------------------------------------------------------- books

    @Test
    void opensABookAtTheStart() throws Exception {
        Path file = alice();
        start();
        open(file);
        assertEquals("Alice’s Adventures in Wonderland", view.info.title());
        assertEquals(3, view.toc.size());
        assertEquals(0, view.chapter);
        assertEquals(0, view.firstPage);
        assertEquals(2, view.geometry.columns(), "two pages side by side in a wide window");
        assertEquals("Down the Rabbit-Hole", view.place);
        assertEquals(0, view.tocEntry);
        assertTrue(view.layout.pageCount() > 2);
        assertEquals(ReadTheme.PAPER, view.theme);
    }

    @Test
    void turningPagesGoesIntoTheNextChapterAndBack() throws Exception {
        Path file = alice();
        start();
        open(file);
        int pages = view.layout.pageCount();
        OnEdt.run(presenter::next);
        assertEquals(2, view.firstPage);
        for (int i = 0; i < pages; i++) {
            if (view.chapter == 0) OnEdt.run(presenter::next);
        }
        assertEquals(1, view.chapter);
        assertEquals(0, view.firstPage);
        assertEquals("The Pool of Tears", view.place);
        OnEdt.run(presenter::previous);
        assertEquals(0, view.chapter);
        int last = view.layout.pageCount() - 1;
        assertEquals(last - last % 2, view.firstPage, "the last spread of the chapter before");
        OnEdt.run(presenter::first);
        assertEquals(0, view.firstPage);
        OnEdt.run(presenter::previous);
        assertEquals(1, view.beeps);
    }

    @Test
    void thePlaceIsSavedAndTheBookOpensThereAgain() throws Exception {
        Path file = alice();
        start();
        open(file);
        OnEdt.run(presenter::next);
        OnEdt.run(presenter::next);
        Position place = presenter.position();
        OnEdt.run(presenter::showLibraryView);
        assertTrue(states.get(file).progress() > 0);
        assertTrue(view.info == null, "back on the shelf");
        open(file);
        assertEquals(place, presenter.position());
        assertEquals(4, view.firstPage);

        // and after a restart, the last book opens by itself
        presenter = presenter();
        view.firstPage = -1;
        start();
        assertEquals(4, view.firstPage);
    }

    @Test
    void bookmarksAreSetAndRemoved() throws Exception {
        Path file = alice();
        start();
        open(file);
        OnEdt.run(presenter::next);
        OnEdt.run(presenter::toggleBookmark);
        assertEquals(1, view.bookmarks.size());
        assertTrue(view.bookmarked);
        assertEquals("Bookmark set", view.status);
        assertEquals(1, states.get(file).bookmarks().size());
        OnEdt.run(presenter::first);
        assertFalse(view.bookmarked);
        OnEdt.run(() -> presenter.openBookmark(view.bookmarks.getFirst()));
        assertEquals(2, view.firstPage);
        assertTrue(view.back);
        OnEdt.run(presenter::toggleBookmark);
        assertEquals(0, view.bookmarks.size());
        assertEquals("Bookmark removed", view.status);
    }

    @Test
    void contentsLinksAndHistory() throws Exception {
        Path file = alice();
        start();
        open(file);
        OnEdt.run(() -> presenter.openToc(view.toc.get(2)));
        assertEquals(2, view.chapter);
        assertEquals(2, view.tocEntry);
        assertTrue(view.back);
        OnEdt.run(presenter::goBack);
        assertEquals(0, view.chapter);
        assertTrue(view.forward);
        OnEdt.run(presenter::goForward);
        assertEquals(2, view.chapter);
        OnEdt.run(presenter::goBack);

        // the link at the end of chapter 1 leads to the tea party
        int offset = linkOffset("to the tea party");
        assertEquals(Optional.of("OEBPS/c3.xhtml#tea"), presenter.linkAt(0, offset));
        OnEdt.run(() -> assertTrue(presenter.clickedText(0, offset)));
        assertEquals(2, view.chapter);
        assertFalse(presenter.clickedText(2, 3), "no link there");
    }

    /** The offset of a phrase in the first chapter. */
    private int linkOffset(String phrase) throws Exception {
        try (var s = new eu.amadeos.apps.books.domain.epub.EpubSession(
                eu.amadeos.apps.books.domain.epub.EpubBook.open(books.resolve("alice.epub")), new Hyphenators(dir))) {
            return s.text(0).plain().indexOf(phrase) + 2;
        }
    }

    @Test
    void footnotesShowInAWindowAndOuterLinksInTheBrowser() throws Exception {
        Path file = TestBooks.epub()
                .chapter("a.xhtml", "<p>See <a href='https://example.org/alice'>the web</a> and the note"
                        + "<a epub:type='noteref' href='a.xhtml#n1'>1</a>.</p>"
                        + "<aside epub:type='footnote' id='n1'><p>Written in 1865.</p></aside>")
                .write(books.resolve("notes.epub"));
        start();
        open(file);
        try (var s = new eu.amadeos.apps.books.domain.epub.EpubSession(
                eu.amadeos.apps.books.domain.epub.EpubBook.open(file), new Hyphenators(dir))) {
            String plain = s.text(0).plain();
            int web = plain.indexOf("the web") + 1;
            int note = plain.indexOf("note") + 4;
            OnEdt.run(() -> {
                assertTrue(presenter.clickedText(0, web));
                assertTrue(presenter.clickedText(0, note));
            });
        }
        assertEquals(List.of("https://example.org/alice"), browsed);
        assertEquals("Written in 1865.", view.footnote);
    }

    @Test
    void searchFindsHitsInTheWholeBook() throws Exception {
        Path file = alice();
        start();
        open(file);
        OnEdt.run(() -> presenter.search("curiouser", false));
        assertEquals(2, view.search.hits().size());
        assertEquals(0, view.search.current());
        assertEquals(1, view.chapter);
        OnEdt.run(presenter::findNext);
        assertEquals(1, view.search.current());
        OnEdt.run(presenter::findNext);
        assertEquals(0, view.search.current(), "after the last hit the first comes again");
        OnEdt.run(() -> presenter.search("jabberwock", false));
        assertTrue(view.search.hits().isEmpty());
        assertEquals(1, view.beeps);
        OnEdt.run(presenter::clearSearch);
        assertFalse(view.search.active());
    }

    @Test
    void appearanceChangesTheLayoutAndIsKept() throws Exception {
        Path file = alice();
        start();
        open(file);
        int before = view.layout.pageCount();
        OnEdt.run(() -> presenter.setAppearance(presenter.appearance().withSize(28)));
        assertTrue(view.layout.pageCount() > before);
        assertEquals(28, preferences.appearance().size());
        assertEquals(28, states.get(file).appearance().orElseThrow().size());
        OnEdt.run(presenter::zoomOut);
        assertEquals(26, view.appearance.size());
        OnEdt.run(() -> presenter.setTheme(ReadTheme.NIGHT));
        assertEquals(ReadTheme.NIGHT, view.theme);
        OnEdt.run(() -> presenter.setTwoPages(false));
        assertEquals(1, view.geometry.columns());
    }

    @Test
    void copyTakesTheSelectedText() throws Exception {
        Path file = alice();
        start();
        open(file);
        view.selection = new int[]{0, 0, 20};
        OnEdt.run(presenter::copy);
        assertEquals("Down the Rabbit-Hole", view.clipboard);
    }

    @Test
    void fullScreenEndsOnTheShelf() throws Exception {
        Path file = alice();
        start();
        OnEdt.run(presenter::toggleFullScreen);
        assertFalse(view.fullScreen, "not for the library");
        open(file);
        OnEdt.run(presenter::toggleFullScreen);
        assertTrue(view.fullScreen);
        OnEdt.run(presenter::showLibraryView);
        assertFalse(view.fullScreen);
    }

    // ---------------------------------------------------------------- comics

    @Test
    void mangaOpensRightToLeftWithTheCoverAlone() throws Exception {
        Path file = manga();
        start();
        open(file);
        assertEquals(4, view.comicPages);
        assertTrue(view.rightToLeft);
        assertEquals(List.of(1), view.comicNumbers);
        OnEdt.run(presenter::next);
        assertEquals(List.of(3, 2), view.comicNumbers, "the first page of the spread on the right");
        assertTrue(view.comicShown.stream().allMatch(java.util.Objects::nonNull));
        OnEdt.run(() -> presenter.arrow(true));
        assertEquals(List.of(1), view.comicNumbers, "the right arrow goes back in a manga");
        OnEdt.run(() -> presenter.setRightToLeft(false));
        OnEdt.run(presenter::next);
        assertEquals(List.of(2, 3), view.comicNumbers);
        OnEdt.run(() -> presenter.setTwoPages(false));
        assertEquals(List.of(2), view.comicNumbers);
        assertFalse(states.get(file).rightToLeft().orElseThrow());
    }

    @Test
    void comicFitZoomAndPlace() throws Exception {
        Path file = manga();
        start();
        open(file);
        OnEdt.run(() -> presenter.setComicFit(ComicFit.WIDTH));
        assertEquals(ComicFit.WIDTH, view.fit);
        OnEdt.run(presenter::zoomIn);
        assertEquals(1.25, view.zoom, 1e-9);
        OnEdt.run(presenter::resetZoom);
        assertEquals(1, view.zoom, 1e-9);
        OnEdt.run(presenter::last);
        OnEdt.run(presenter::toggleBookmark);
        assertTrue(presenter.comicBookmarked());
        OnEdt.run(presenter::showLibraryView);
        assertEquals(1.0, states.get(file).progress(), 1e-9);
        open(file);
        assertEquals(List.of(4), view.comicNumbers);
    }

    @Test
    void exitSavesAndCloses() throws Exception {
        Path file = alice();
        start();
        open(file);
        OnEdt.run(presenter::next);
        OnEdt.run(presenter::exit);
        assertTrue(view.closed);
        assertTrue(exited);
        assertTrue(states.get(file).started());
    }
}
