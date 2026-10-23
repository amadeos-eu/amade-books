package eu.amadeos.apps.books.domain.layout;

import eu.amadeos.apps.books.TestBooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypesetterTest {
    static final Appearance PLAIN = new Appearance("DejaVu Serif", 16, 1.4, 0.05, true, false);

    @TempDir
    Path dir;

    private static ChapterText chapter(String body) {
        return ChapterParser.parse(TestBooks.xhtml("t", body), "OEBPS/ch.xhtml", new Css().add(TestBooks.CSS), "en");
    }

    private static Typesetter setter(Appearance a, Map<String, Dimension> pictures) {
        return new Typesetter(a, new Hyphenators(Path.of("/nonexistent")), p -> Optional.ofNullable(pictures.get(p)), Map.of());
    }

    @Test
    void fillsPagesInOrderWithoutLosingText() {
        ChapterText text = chapter("<p class='first'>" + TestBooks.words(400) + "</p><p>" + TestBooks.words(300) + "</p>");
        ChapterLayout layout = setter(PLAIN, Map.of()).layout(text, 300, 300);
        assertTrue(layout.pageCount() > 3, "pages: " + layout.pageCount());
        int expected = 0;
        for (Page page : layout.pages()) {
            for (Page.Line line : page.lines()) {
                if (line.start() > expected) {
                    // the line break between the two paragraphs
                    assertEquals(text.plain().charAt(expected), '\n');
                    expected++;
                }
                assertEquals(expected, line.start());
                expected = line.end();
                while (expected < text.length() && text.plain().charAt(expected) == ' ') expected++;
                assertTrue(line.bottom() <= 300.5f, "line below the page");
            }
        }
        assertEquals(text.length() - 1, expected);
    }

    @Test
    void justifiesAllButTheLastLine() {
        ChapterText text = chapter("<p class='first'>" + TestBooks.words(80) + "</p>");
        Page page = setter(PLAIN, Map.of()).layout(text, 300, 2000).pages().getFirst();
        List<Page.Line> lines = page.lines();
        for (int i = 0; i < lines.size() - 1; i++) {
            assertEquals(300, lines.get(i).layout().getVisibleAdvance(), 1.5, "line " + i);
        }
        assertTrue(lines.getLast().layout().getVisibleAdvance() <= 300.5, "last " + lines.getLast().layout().getVisibleAdvance());
        ChapterLayout ragged = setter(PLAIN.withJustify(false), Map.of()).layout(text, 300, 2000);
        assertTrue(ragged.pages().getFirst().lines().stream().anyMatch(l -> l.layout().getVisibleAdvance() < 290),
                "a left aligned paragraph has shorter lines");
    }

    @Test
    void indentsCentresAndKeepsHeadingsWithTheirText() {
        ChapterText text = chapter("<h1>Chapter</h1><p>" + TestBooks.words(30) + "</p>");
        Page page = setter(PLAIN, Map.of()).layout(text, 300, 2000).pages().getFirst();
        Page.Line heading = page.lines().getFirst();
        float centre = heading.x() + heading.layout().getVisibleAdvance() / 2;
        assertEquals(150, centre, 2);
        assertEquals(1.5f * 16, page.lines().get(1).x(), 0.5f);
        // A heading near the bottom of a page moves to the next page with its first lines.
        ChapterText late = chapter("<p class='first'>" + TestBooks.words(95) + "</p><h1>Next</h1><p>" + TestBooks.words(60) + "</p>");
        ChapterLayout layout = setter(PLAIN, Map.of()).layout(late, 300, 200);
        for (Page p : layout.pages()) {
            Page.Line last = p.lines().getLast();
            assertFalse(late.plain().substring(last.start(), last.end()).equals("Next"), "heading alone at the bottom");
        }
    }

    // --8<-- [start:hyphenation]
    @Test
    void hyphenatesTheWordThatDoesNotFit() throws Exception {
        Files.write(dir.resolve("hyph_en.dic"), ("UTF-8\n" + HyphenatorTest.PATTERNS).getBytes(StandardCharsets.UTF_8));
        Typesetter setter = new Typesetter(PLAIN.withHyphenate(true), new Hyphenators(dir), p -> Optional.empty(), Map.of());
        ChapterText text = chapter("<p class='first'>" + "hyphenation ".repeat(12) + "</p>");
        List<Page.Line> lines = setter.layout(text, 170, 2000).pages().getFirst().lines();
        assertTrue(lines.stream().anyMatch(Page.Line::hyphen), "no line ends with a hyphen");
        for (Page.Line line : lines) {
            if (line.hyphen()) {
                String piece = text.plain().substring(line.start(), line.end());
                assertTrue(piece.endsWith("hy") || piece.endsWith("hyphen"), piece);
            }
        }
    }
    // --8<-- [end:hyphenation]

    // --8<-- [start:floats]
    @Test
    void textFlowsAroundAFloatingPicture() {
        ChapterText text = chapter("<div class='right'><img src='a.png'/></div><p class='first'>" + TestBooks.words(120) + "</p>");
        Page page = setter(PLAIN, Map.of("OEBPS/a.png", new Dimension(200, 200))).layout(text, 400, 2000).pages().getFirst();
        Page.Picture picture = page.pictures().getFirst();
        assertEquals(160, picture.width(), 0.5);
        assertEquals(400 - 160, picture.x(), 0.5);
        Page.Line beside = page.lines().getFirst();
        Page.Line below = page.lines().getLast();
        assertTrue(beside.x() + beside.layout().getVisibleAdvance() < picture.x(), "text runs into the picture");
        assertTrue(below.top() > picture.y() + picture.height());
        assertTrue(below.layout().getVisibleAdvance() > 300 || below == page.lines().getLast());
    }
    // --8<-- [end:floats]

    @Test
    void aPictureThatDoesNotFitMovesToTheNextPageAndTheTextGoesOn() {
        ChapterText text = chapter("<p class='first'>" + TestBooks.words(60) + "</p><img src='big.png'/><p>"
                + TestBooks.words(40) + "</p>");
        ChapterLayout layout = setter(PLAIN, Map.of("OEBPS/big.png", new Dimension(300, 250))).layout(text, 300, 330);
        Page first = layout.pages().getFirst();
        assertTrue(first.pictures().isEmpty());
        int pictureOffset = text.blocks().get(1).offset();
        assertTrue(first.lines().stream().anyMatch(l -> l.start() > pictureOffset), "the text after the picture moved up");
        Page.Picture moved = layout.pages().get(1).pictures().getFirst();
        assertEquals(0, moved.y(), 0.01);
    }

    @Test
    void picturesShrinkToThePage() {
        ChapterText text = chapter("<img src='tall.png'/>");
        Page.Picture p = setter(PLAIN, Map.of("OEBPS/tall.png", new Dimension(1000, 3000))).layout(text, 300, 400)
                .pages().getFirst().pictures().getFirst();
        assertEquals(400, p.height(), 0.5);
        assertEquals(400 / 3.0, p.width(), 0.5);
    }

    @Test
    void findsThePageOfAnOffsetAndTheTextUnderTheMouse() {
        ChapterText text = chapter("<p class='first'>" + TestBooks.words(300) + "</p>");
        ChapterLayout layout = setter(PLAIN, Map.of()).layout(text, 300, 300);
        Page second = layout.pages().get(1);
        assertEquals(1, layout.pageOf(second.start()));
        assertEquals(1, layout.pageOf(second.start() + 5));
        assertEquals(0, layout.pageOf(second.start() - 1));
        Page.Line line = second.lines().getFirst();
        assertEquals(line.start(), line.offsetAt(line.x() + 1));
        assertTrue(line.highlight(line.start(), line.start() + 3).getBounds().width > 5);
    }
}
