package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.OnEdt;
import eu.amadeos.apps.books.TestBooks;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import eu.amadeos.apps.books.domain.layout.Page;
import eu.amadeos.apps.books.repository.BooksPreferences;
import eu.amadeos.apps.books.repository.LibraryCache;
import eu.amadeos.apps.books.repository.ReadingStates;
import eu.amadeos.commons.desktop.DesktopLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with real books: shelf, pages, keys, mouse and the appearance. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private Path books;
    private MainWindow window;
    private MainPresenter presenter;

    @BeforeEach
    void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        books = Files.createDirectories(dir.resolve("Books"));
        TestBooks.epub().subject("Fantasy")
                .chapter("c1.xhtml", "<h1>Down the Rabbit-Hole</h1><p>" + TestBooks.words(1200)
                        + " <a href='c2.xhtml'>on to the pool</a> and a note<a epub:type='noteref' href='c1.xhtml#n1'>1</a>.</p>"
                        + "<aside epub:type='footnote' id='n1'><p>The note.</p></aside>")
                .chapter("c2.xhtml", "<h1>The Pool of Tears</h1><p>Curiouser and curiouser. " + TestBooks.words(200) + "</p>")
                .nav("<li><a href='c1.xhtml'>Down the Rabbit-Hole</a></li><li><a href='c2.xhtml'>The Pool of Tears</a></li>")
                .write(books.resolve("alice.epub"));
        Map<String, int[]> pages = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) pages.put("p" + i + ".png", new int[]{60, 90});
        TestBooks.cbz(books.resolve("kaze.cbz"), pages, "<ComicInfo><Manga>YesAndRightToLeft</Manga></ComicInfo>");
        BooksPreferences preferences = new BooksPreferences(dir.resolve("config/books.properties"), books);
        ReadingStates states = new ReadingStates(dir.resolve("data/library.json"));
        OnEdt.run(() -> {
            window = new MainWindow(MainPresenterTest.MSG, null);
            presenter = new MainPresenter(window, MainPresenterTest.MSG, null, preferences, states,
                    new LibraryCache(dir.resolve("cache/library-cache.json")), new Hyphenators(dir), new DesktopLauncher(),
                    Runnable::run, Runnable::run, System::currentTimeMillis, () -> { });
            window.bind(presenter);
            window.setSize(1200, 800);
            window.setVisible(true);
            presenter.start(List.of());
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
    }

    private void openBook(String name) throws Exception {
        OnEdt.run(() -> presenter.open(books.resolve(name)));
        OnEdt.run(() -> { });
    }

    private static void key(JComponent c, int code) {
        c.dispatchEvent(new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }

    private static void click(JComponent c, int x, int y) {
        long t = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, t, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void theShelfShowsCoversShelvesAndAList() throws Exception {
        OnEdt.run(() -> {
            LibraryPanel library = window.libraryPanel();
            assertEquals(2, library.grid().books().size());
            List<String> lines = new java.util.ArrayList<>();
            for (int i = 0; i < library.shelfList().getModel().getSize(); i++) {
                Object o = library.shelfList().getModel().getElementAt(i);
                lines.add(o instanceof LibraryPanel.Heading h ? "# " + h.text() : ((LibraryPanel.Shelf) o).label());
            }
            assertTrue(lines.containsAll(List.of("# Library", "All books", "# Format", "Books", "Comics", "# Genre", "Fantasy")),
                    lines.toString());
            presenter.setListView(true);
            assertEquals(2, library.table().getRowCount());
            assertTrue(library.table().isShowing());
            library.searchField().setText("kaze");
            assertEquals(1, library.grid().books().size());
            assertEquals("Books", window.getTitle());
        });
    }

    @Test
    void aBookOpensWithItsPagesContentsAndPlace() throws Exception {
        openBook("alice.epub");
        OnEdt.run(() -> {
            assertEquals("Alice’s Adventures in Wonderland - Books", window.getTitle());
            assertEquals(2, window.pageView().visiblePages().size());
            assertEquals(2, window.sidebar().tocList().getModel().getSize());
            assertTrue(window.statusLabel().getText().startsWith("Down the Rabbit-Hole  ·  Page 1 of "),
                    window.statusLabel().getText());
            assertTrue(window.progressStrip().progress() >= 0);
            key(window.pageView(), KeyEvent.VK_RIGHT);
            assertEquals(2, presenter.page());
            key(window.pageView(), KeyEvent.VK_LEFT);
            assertEquals(0, presenter.page());
            // a click in the right third turns the page, in the left third back
            PageView pages = window.pageView();
            click(pages, pages.getWidth() - 5, pages.getHeight() / 2);
            assertEquals(2, presenter.page());
            click(pages, 5, pages.getHeight() / 2);
            assertEquals(0, presenter.page());
        });
    }

    @Test
    void pagesArePaintedInTheThemesColours() throws Exception {
        openBook("alice.epub");
        OnEdt.run(() -> {
            presenter.setTheme(ReadTheme.NIGHT);
            PageView pages = window.pageView();
            BufferedImage image = new BufferedImage(pages.getWidth(), pages.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            pages.paint(g);
            g.dispose();
            assertEquals(ReadTheme.NIGHT.background().getRGB(), image.getRGB(2, 2));
            // the first line of text is light on dark
            Page first = pages.visiblePages().getFirst();
            Page.Line line = first.lines().get(1);
            int bright = 0;
            int y = Math.round(line.baseline() - line.layout().getAscent() / 2);
            for (int x = 0; x < pages.getWidth() / 2; x++) {
                int rgb = image.getRGB(x, y + topOfText(pages));
                if ((rgb & 0xff) > 0x80) bright++;
            }
            assertTrue(bright > 20, "text pixels: " + bright);
        });
    }

    private static int topOfText(PageView pages) {
        // the geometry the presenter chose for this size
        return eu.amadeos.apps.books.domain.layout.PageGeometry.of(pages.getWidth(), pages.getHeight(), true,
                eu.amadeos.apps.books.domain.layout.Appearance.DEFAULT).textTop();
    }

    @Test
    void selectingAndCopyingText() throws Exception {
        openBook("alice.epub");
        AtomicReference<String> copied = new AtomicReference<>();
        OnEdt.run(() -> {
            window.pageView().select(0, 20);
            assertEquals(List.of(0, 0, 20), java.util.Arrays.stream(window.selection().orElseThrow()).boxed().toList());
            presenter.copy();
            try {
                copied.set((String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .getData(java.awt.datatransfer.DataFlavor.stringFlavor));
            } catch (Exception e) {
                copied.set("clipboard: " + e);
            }
        });
        assertEquals("Down the Rabbit-Hole", copied.get());
    }

    @Test
    void theAppearancePanelChangesTheBook() throws Exception {
        openBook("alice.epub");
        AtomicInteger before = new AtomicInteger();
        OnEdt.run(() -> {
            AppearancePanel panel = window.appearancePanel();
            before.set(window.pageView().visiblePages().getFirst().end());
            panel.sizeSpinner().setValue(26);
            assertEquals(26, presenter.appearance().size());
            assertTrue(window.pageView().visiblePages().getFirst().end() < before.get(), "less text on a page");
            panel.themeButton(ReadTheme.SEPIA).doClick();
            assertEquals(ReadTheme.SEPIA, presenter.settings().theme());
            assertEquals(ReadTheme.SEPIA, window.pageView().theme());
            panel.justifyBox().doClick();
            assertFalse(presenter.appearance().justify());
        });
    }

    @Test
    void bookmarksAndTheSidebar() throws Exception {
        openBook("alice.epub");
        OnEdt.run(() -> {
            presenter.setSidebar(true);
            assertTrue(window.sidebar().isShowing());
            presenter.next();
            presenter.toggleBookmark();
            assertEquals(1, window.sidebar().bookmarkList().getModel().getSize());
            assertEquals(Boolean.TRUE, window.bookmarkAction().getValue(javax.swing.Action.SELECTED_KEY));
            assertEquals("Bookmark set", window.noteLabel().getText());
            presenter.showBookmarksTab(true);
            assertEquals(1, window.sidebar().tabs().getSelectedIndex());
            window.sidebar().tocList().setSelectedIndex(1);
            presenter.openToc(window.sidebar().tocList().getSelectedValue());
            assertEquals(1, presenter.chapter());
        });
    }

    @Test
    void searchShowsTheCount() throws Exception {
        openBook("alice.epub");
        OnEdt.run(() -> {
            window.searchField().setText("curiouser");
            window.searchField().postActionEvent();
            assertEquals("1 of 2", window.searchLabel().getText());
            assertEquals(1, presenter.chapter());
            presenter.search("nothing like this", false);
            assertEquals("Not found", window.searchLabel().getText());
        });
    }

    @Test
    void aMangaReadsFromRightToLeft() throws Exception {
        openBook("kaze.cbz");
        OnEdt.run(() -> {
            assertEquals(List.of(1), window.comicView().numbers());
            key(window.comicView(), KeyEvent.VK_LEFT);
            assertEquals(List.of(3, 2), window.comicView().numbers());
            assertTrue(window.statusLabel().getText().startsWith("Pages 2-3 of 4"), window.statusLabel().getText());
            assertTrue(window.comicView().scale() > 1, "the small test pages are scaled up to the window");
            presenter.showLibraryView();
            assertTrue(window.showingLibrary());
        });
    }

    @Test
    void fullScreenHidesTheBars() throws Exception {
        openBook("alice.epub");
        OnEdt.run(() -> {
            presenter.toggleFullScreen();
            assertTrue(window.fullScreen());
            assertFalse(window.getJMenuBar().isVisible());
            key(window.pageView(), KeyEvent.VK_ESCAPE);
            assertFalse(window.fullScreen());
            assertTrue(window.getJMenuBar().isVisible());
        });
    }
}
