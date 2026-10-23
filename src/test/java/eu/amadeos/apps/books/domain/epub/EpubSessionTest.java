package eu.amadeos.apps.books.domain.epub;

import eu.amadeos.apps.books.TestBooks;
import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.layout.ChapterLayout;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubSessionTest {
    @TempDir
    Path dir;

    private EpubSession open(TestBooks.Epub epub) throws Exception {
        return new EpubSession(EpubBook.open(epub.write(dir.resolve("book.epub"))), new Hyphenators(dir));
    }

    @Test
    void progressAndPositionsThroughTheBook() throws Exception {
        try (EpubSession s = open(TestBooks.epub().chapter("a.xhtml", "<p>" + "x".repeat(99) + "</p>")
                .chapter("b.xhtml", "<p>" + "y".repeat(299) + "</p>"))) {
            assertEquals(0, s.progress(Position.START));
            assertEquals(0.25, s.progress(new Position(1, 0)), 1e-9);
            assertEquals(0.5, s.progress(new Position(1, 100)), 1e-9);
            assertEquals(new Position(1, 100), s.at(0.5));
            assertEquals(List.of(0.0, 0.25), s.chapterStarts());
        }
    }

    @Test
    void linksTheContentsAndFootnotes() throws Exception {
        try (EpubSession s = open(TestBooks.epub()
                .chapter("a.xhtml", "<p>Start <a epub:type='noteref' href='notes.xhtml#n1'>1</a></p><p id='mid'>Middle</p>")
                .chapter("notes.xhtml", "<ol><li id='n1'>The first note.</li></ol>")
                .nav("<li><a href='a.xhtml'>A</a></li><li><a href='a.xhtml#mid'>Mid</a></li>"))) {
            assertEquals(new Position(0, 8), s.resolve("OEBPS/a.xhtml#mid").orElseThrow());
            assertEquals(new Position(0, 8), s.resolve(s.book().toc().get(1)).orElseThrow());
            assertEquals("Mid", s.tocEntry(new Position(0, 10)).orElseThrow().label());
            assertEquals("A", s.tocEntry(new Position(0, 3)).orElseThrow().label());
            assertEquals("1. The first note.", s.footnote("OEBPS/notes.xhtml#n1").orElseThrow());
            assertTrue(s.resolve("OEBPS/missing.xhtml").isEmpty());
        }
    }

    @Test
    void searchIgnoresCaseAndAccents() throws Exception {
        try (EpubSession s = open(TestBooks.epub().chapter("a.xhtml", "<p>Die Bücher im Regal.</p>")
                .chapter("b.xhtml", "<p>Noch mehr BUCHER.</p>"))) {
            List<EpubSession.SearchHit> hits = s.search("bucher", 10);
            assertEquals(2, hits.size());
            assertEquals(new Position(0, 4), new Position(hits.get(0).chapter(), hits.get(0).offset()));
            assertEquals(1, hits.get(1).chapter());
            assertTrue(hits.get(0).excerpt().contains("Bücher"));
        }
    }

    @Test
    void keepsLayoutsAndPictures() throws Exception {
        try (EpubSession s = open(TestBooks.epub().chapter("a.xhtml", "<p>Text</p><img src='p.png'/>")
                .file("p.png", TestBooks.png(30, 20, java.awt.Color.BLUE)).cover("p.png"))) {
            ChapterLayout first = s.layout(0, Appearance.DEFAULT, 300, 400);
            assertSame(first, s.layout(0, Appearance.DEFAULT, 300, 400));
            assertTrue(s.cachedLayout(0, Appearance.DEFAULT, 300, 400).isPresent());
            assertTrue(s.cachedLayout(0, Appearance.DEFAULT, 301, 400).isEmpty());
            assertEquals(30, s.pictureSize("OEBPS/p.png").orElseThrow().width);
            assertEquals(20, s.cover().orElseThrow().getHeight());
            assertTrue(s.picture("OEBPS/none.png").isEmpty());
        }
    }

    @Test
    void brokenMarkupStillReads() throws Exception {
        try (EpubSession s = open(TestBooks.epub().chapter("a.xhtml", "<p>A</p>").rawChapter("b.xhtml", "<p>One<p>Two <b>bold"))) {
            assertEquals("One\nTwo bold\n", s.text(1).plain());
            assertEquals(1, s.layout(1, Appearance.DEFAULT, 300, 300).pageCount());
        }
    }
}
