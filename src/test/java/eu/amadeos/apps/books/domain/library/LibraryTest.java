package eu.amadeos.apps.books.domain.library;

import eu.amadeos.apps.books.TestBooks;
import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.BookInfo;
import eu.amadeos.apps.books.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryTest {
    @TempDir
    Path dir;

    private static LibraryEntry entry(String title, String author, BookFormat format, String series, double index,
                                      List<String> genres, long added) {
        return new LibraryEntry(new BookInfo(Path.of("/b/" + title + "." + format.extension()), format, title,
                List.of(author), series, index, genres, "en"), 1, 1, added, Optional.empty());
    }

    // --8<-- [start:shelf]
    @Test
    void facetsAndFilters() {
        LibraryEntry alice = entry("Alice", "Lewis Carroll", BookFormat.EPUB, "", 0, List.of("Fantasy"), 1);
        LibraryEntry kafka = entry("Die Verwandlung", "Franz Kafka", BookFormat.EPUB, "", 0, List.of("Novella"), 2);
        LibraryEntry kaze = entry("Kaze 2", "Aoi Senda", BookFormat.CBZ, "Kaze", 2, List.of("Fantasy"), 3);
        LibraryEntry kaze1 = entry("Kaze 1", "Aoi Senda", BookFormat.CBZ, "Kaze", 1, List.of("Fantasy"), 4);
        Map<Path, BookState> states = new HashMap<>();
        states.put(alice.file(), BookState.NEW.read(new Position(0, 10), 0.04, 100));
        states.put(kafka.file(), BookState.NEW.withFinished(true).withCollection("Holiday", true));
        Shelf shelf = new Shelf(List.of(alice, kafka, kaze, kaze1), e -> states.getOrDefault(e.file(), BookState.NEW),
                Locale.GERMAN);
        Map<ShelfFilter, Integer> facets = shelf.facets();
        assertEquals(4, facets.get(ShelfFilter.ALL));
        assertEquals(1, facets.get(ShelfFilter.of(ShelfFilter.Kind.READING)));
        assertEquals(2, facets.get(ShelfFilter.of(ShelfFilter.Kind.NOT_STARTED)));
        assertEquals(1, facets.get(ShelfFilter.of(ShelfFilter.Kind.FINISHED)));
        assertEquals(2, facets.get(ShelfFilter.of(ShelfFilter.Kind.COMICS)));
        assertEquals(3, facets.get(new ShelfFilter(ShelfFilter.Kind.GENRE, "Fantasy")));
        assertEquals(1, facets.get(new ShelfFilter(ShelfFilter.Kind.COLLECTION, "Holiday")));
        assertEquals(List.of(kaze1, kaze), shelf.books(new ShelfFilter(ShelfFilter.Kind.SERIES, "Kaze"), "", SortOrder.SERIES));
        assertEquals(alice, shelf.books(ShelfFilter.ALL, "", SortOrder.RECENT).getFirst());
        assertEquals(List.of(alice), shelf.books(ShelfFilter.ALL, "carroll", SortOrder.TITLE));
        assertEquals(List.of(kaze1, kaze, kafka, alice), shelf.books(ShelfFilter.ALL, "", SortOrder.ADDED));
        assertEquals(List.of(alice, kafka, kaze1, kaze), shelf.books(ShelfFilter.ALL, "", SortOrder.TITLE));
        // by last name: Carroll, Kafka, Senda
        assertEquals(List.of(alice, kafka, kaze1, kaze), shelf.books(ShelfFilter.ALL, "", SortOrder.AUTHOR));
    }
    // --8<-- [end:shelf]

    @Test
    void bookStates() {
        BookState s = BookState.NEW.read(new Position(2, 40), 0.3, 1000);
        assertTrue(s.reading());
        Bookmark mark = new Bookmark(new Position(1, 5), "Down", 7);
        BookState marked = s.toggleBookmark(mark);
        assertEquals(List.of(mark), marked.bookmarks());
        assertEquals(List.of(), marked.toggleBookmark(new Bookmark(new Position(1, 5), "other", 9)).bookmarks());
        assertTrue(s.read(new Position(9, 0), 1.0, 2000).finished());
        BookState reset = s.withFinished(true).withFinished(false);
        assertEquals(0, reset.progress());
    }

    @Test
    void scansFoldersAndReusesUnchangedBooks() throws Exception {
        Path books = Files.createDirectories(dir.resolve("Books/Classics"));
        Path alice = TestBooks.epub().chapter("a.xhtml", "<p>A</p>")
                .file("c.png", TestBooks.png(60, 90, java.awt.Color.ORANGE)).cover("c.png")
                .write(books.resolve("alice.epub"));
        Path comic = TestBooks.cbz(dir.resolve("loose.cbz"), Map.of("1.png", new int[]{40, 60}), null);
        Files.writeString(books.resolve("notes.txt"), "not a book");
        LibraryScanner scanner = new LibraryScanner(dir.resolve("covers"), () -> 42);
        List<LibraryEntry> found = scanner.scan(List.of(dir.resolve("Books")), List.of(comic), Map.of());
        assertEquals(2, found.size());
        LibraryEntry book = found.getFirst();
        assertEquals("Alice’s Adventures in Wonderland", book.info().title());
        assertEquals(42, book.added());
        assertTrue(Files.isRegularFile(book.cover().orElseThrow()));
        assertTrue(found.get(1).cover().isPresent());
        LibraryEntry again = scanner.scan(List.of(dir.resolve("Books")), List.of(), Map.of(alice.toAbsolutePath(), book))
                .getFirst();
        assertSame(book, again);
    }
}
