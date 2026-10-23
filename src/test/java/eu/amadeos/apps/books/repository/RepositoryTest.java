package eu.amadeos.apps.books.repository;

import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.BookInfo;
import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.Settings;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.Bookmark;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.apps.books.domain.library.SortOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {
    @TempDir
    Path dir;

    @Test
    void preferencesRoundTrip() {
        Path file = dir.resolve("books.properties");
        BooksPreferences p = new BooksPreferences(file, dir.resolve("Books"));
        assertEquals(Settings.DEFAULT, p.settings());
        assertEquals(Appearance.DEFAULT, p.appearance());
        assertEquals(List.of(dir.resolve("Books")), p.folders());
        Settings s = Settings.DEFAULT.withTheme(ReadTheme.NIGHT).withSort(SortOrder.AUTHOR).withComicFit(ComicFit.WIDTH)
                .withListView(true).withSidebar(true, true);
        p.setSettings(s);
        Appearance a = new Appearance("Caladea", 21, 1.6, 0.12, false, true);
        p.setAppearance(a);
        p.setFolders(List.of(dir.resolve("A"), dir.resolve("B")));
        p.setLastBook(dir.resolve("A/x.epub"));
        BooksPreferences again = new BooksPreferences(file, dir.resolve("Books"));
        assertEquals(s, again.settings());
        assertEquals(a, again.appearance());
        assertEquals(List.of(dir.resolve("A"), dir.resolve("B")), again.folders());
        assertEquals(dir.resolve("A"), again.mainFolder());
        assertEquals(dir.resolve("A/x.epub"), again.lastBook().orElseThrow());
    }

    @Test
    void readingStatesRoundTrip() throws Exception {
        Path file = dir.resolve("data/library.json");
        ReadingStates states = new ReadingStates(file);
        Path book = dir.resolve("alice.epub");
        assertEquals(BookState.NEW, states.get(book));
        BookState s = BookState.NEW.read(new Position(3, 120), 0.25, 1234)
                .toggleBookmark(new Bookmark(new Position(1, 5), "Down \"the\" hole", 99))
                .withCollection("Holiday", true).withAppearance(new Appearance("Caladea", 20, 1.5, 0.1, true, false))
                .withRightToLeft(true).withTwoPages(false);
        states.put(book, s);
        assertTrue(Files.isRegularFile(file));
        ReadingStates again = new ReadingStates(file);
        assertEquals(s, again.get(book));
        assertEquals(List.of("Holiday"), again.collections());
        Files.writeString(file, "{ broken");
        assertEquals(BookState.NEW, new ReadingStates(file).get(book));
    }

    @Test
    void libraryCacheRoundTrip() {
        LibraryCache cache = new LibraryCache(dir.resolve("cache/library-cache.json"));
        LibraryEntry e = new LibraryEntry(new BookInfo(dir.resolve("k.cbz"), BookFormat.CBZ, "Kaze 2", List.of("Aoi Senda"),
                "Kaze", 2, List.of("Fantasy"), "ja"), 100, 200, 300, Optional.of(dir.resolve("cache/covers/x.jpg")));
        cache.save(List.of(e));
        assertEquals(List.of(e), cache.load());
        assertEquals(dir.resolve("cache/covers"), cache.coverDirectory());
    }
}
