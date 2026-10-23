package eu.amadeos.apps.books.domain.epub;

import eu.amadeos.apps.books.TestBooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubBookTest {
    @TempDir
    Path dir;

    @Test
    void readsMetadataSpineAndNavigation() throws Exception {
        Path file = TestBooks.epub().subject("Fiction -- Fantasy").subject("Children").series("Alice", "1")
                .chapter("ch1.xhtml", "<h1 id='top'>I</h1><p>One</p><p id='two'>Two</p>")
                .chapter("ch2.xhtml", "<p>Three</p>")
                .nav("<li><a href='ch1.xhtml'>I. Down</a><ol><li><a href='ch1.xhtml#two'>Two</a></li></ol></li>"
                        + "<li><a href='ch2.xhtml'>II. Pool</a></li>")
                .write(dir.resolve("alice.epub"));
        try (EpubBook book = EpubBook.open(file)) {
            assertEquals("Alice’s Adventures in Wonderland", book.info().title());
            assertEquals(List.of("Lewis Carroll"), book.info().authors());
            assertEquals(List.of("Fiction", "Fantasy", "Children"), book.info().genres());
            assertEquals("Alice", book.info().series());
            assertEquals(1.0, book.info().seriesIndex());
            assertEquals("en", book.info().language());
            assertEquals(List.of("OEBPS/ch1.xhtml", "OEBPS/ch2.xhtml"), book.spine());
            assertEquals(List.of(new TocEntry("I. Down", 0, "", 0), new TocEntry("Two", 0, "two", 1),
                    new TocEntry("II. Pool", 1, "", 0)), book.toc());
            assertTrue(book.chapter(1).contains("Three"));
        }
    }

    @Test
    void readsTheEpub2TableOfContents() throws Exception {
        Path file = TestBooks.epub().chapter("a.xhtml", "<p>A</p>").chapter("b.xhtml", "<p id='x'>B</p>")
                .ncx("""
                        <navPoint id="1"><navLabel><text>Start</text></navLabel><content src="a.xhtml"/>
                          <navPoint id="2"><navLabel><text>Inside</text></navLabel><content src="b.xhtml#x"/></navPoint>
                        </navPoint>""")
                .write(dir.resolve("old.epub"));
        try (EpubBook book = EpubBook.open(file)) {
            assertEquals(List.of(new TocEntry("Start", 0, "", 0), new TocEntry("Inside", 1, "x", 1)), book.toc());
        }
    }

    @Test
    void withoutTableOfContentsEveryChapterIsAnEntry() throws Exception {
        Path file = TestBooks.epub().chapter("first_part.xhtml", "<p>A</p>").write(dir.resolve("plain.epub"));
        try (EpubBook book = EpubBook.open(file)) {
            assertEquals(List.of(new TocEntry("first part", 0, "", 0)), book.toc());
        }
    }

    @Test
    void findsTheCover() throws Exception {
        Path file = TestBooks.epub().chapter("a.xhtml", "<p>A</p>")
                .file("images/cover.png", TestBooks.png(60, 90, java.awt.Color.RED)).cover("images/cover.png")
                .write(dir.resolve("cover.epub"));
        try (EpubBook book = EpubBook.open(file)) {
            assertEquals("OEBPS/images/cover.png", book.cover().orElseThrow());
            assertEquals(List.of("OEBPS/style.css"), book.resources("text/css"));
        }
    }

    @Test
    void refusesFilesThatAreNoBooks() throws Exception {
        Path text = Files.writeString(dir.resolve("note.epub"), "not a zip");
        assertThrows(EpubException.class, () -> EpubBook.open(text));
    }

    @Test
    void resolvesPathsInsideTheBook() {
        assertEquals("OEBPS/images/a b.png", ZipPaths.resolve("OEBPS/text/ch1.xhtml", "../images/a%20b.png#x"));
        assertEquals("OEBPS/text/ch2.xhtml", ZipPaths.resolve("OEBPS/text/ch1.xhtml", "ch2.xhtml"));
        assertEquals("OEBPS/text/ch1.xhtml", ZipPaths.resolve("OEBPS/text/ch1.xhtml", "#note"));
        assertEquals("note", ZipPaths.fragment("ch1.xhtml#note"));
        assertTrue(ZipPaths.external("https://example.org/"));
        assertTrue(ZipPaths.external("mailto:a@b.c"));
    }

    @Test
    void decodesTheEncodingOfTheXmlDeclaration() {
        byte[] latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><p>Bücher</p>"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(EpubBook.decode(latin).contains("Bücher"));
    }
}
