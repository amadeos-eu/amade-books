package eu.amadeos.apps.books.domain.comic;

import eu.amadeos.apps.books.TestBooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComicTest {
    @TempDir
    Path dir;

    @Test
    void naturalOrder() {
        List<String> names = new ArrayList<>(List.of("p10.jpg", "p2.jpg", "p1.jpg", "P03.jpg", "cover.jpg"));
        names.sort(NaturalOrder.INSTANCE);
        assertEquals(List.of("cover.jpg", "p1.jpg", "p2.jpg", "P03.jpg", "p10.jpg"), names);
    }

    @Test
    void readsPagesInOrderAndComicInfo() throws Exception {
        Map<String, int[]> pages = new LinkedHashMap<>();
        pages.put("v2/page10.png", new int[]{60, 90});
        pages.put("v2/page2.png", new int[]{60, 90});
        pages.put("v2/page1.png", new int[]{60, 90});
        pages.put("__MACOSX/v2/._page1.png", new int[]{1, 1});
        Path file = TestBooks.cbz(dir.resolve("kaze.cbz"), pages, """
                <?xml version="1.0"?>
                <ComicInfo><Series>Kaze no Michi</Series><Number>2</Number><Writer>Aoi Senda</Writer>
                <Genre>Adventure, Fantasy</Genre><Manga>YesAndRightToLeft</Manga><LanguageISO>ja</LanguageISO></ComicInfo>
                """);
        try (ComicSession s = new ComicSession(ComicBook.open(file))) {
            assertEquals(3, s.pageCount());
            assertEquals("v2/page1.png", s.book().pageName(0));
            assertEquals("v2/page10.png", s.book().pageName(2));
            assertEquals("Kaze no Michi 2", s.book().info().title());
            assertEquals(List.of("Aoi Senda"), s.book().info().authors());
            assertEquals(List.of("Adventure", "Fantasy"), s.book().info().genres());
            assertTrue(s.book().rightToLeft());
            assertEquals(90, s.page(1).orElseThrow().getHeight());
            assertTrue(s.cached(1).isPresent());
        }
    }

    @Test
    void withoutComicInfoTheFileNameIsTheTitle() throws Exception {
        Path file = TestBooks.cbz(dir.resolve("Harbour_Patrol_01.cbz"), Map.of("a.png", new int[]{10, 10}), null);
        try (ComicBook comic = ComicBook.open(file)) {
            assertEquals("Harbour Patrol 01", comic.info().title());
            assertFalse(comic.rightToLeft());
        }
    }

    // --8<-- [start:spreads]
    @Test
    void spreadsKeepTheCoverAndWidePagesAlone() {
        boolean[] wide = {false, false, false, true, false, false, false};
        assertEquals(List.of(new Spreads.Spread(0, -1), new Spreads.Spread(1, 2), new Spreads.Spread(3, -1),
                new Spreads.Spread(4, 5), new Spreads.Spread(6, -1)), Spreads.of(wide, true));
        assertEquals(7, Spreads.of(wide, false).size());
        assertEquals(3, Spreads.indexOf(Spreads.of(wide, true), 4));
    }
    // --8<-- [end:spreads]

    @Test
    void wideFlagsComeFromThePictureSizes() throws Exception {
        Map<String, int[]> pages = new LinkedHashMap<>();
        pages.put("1.png", new int[]{60, 90});
        pages.put("2.png", new int[]{120, 90});
        try (ComicSession s = new ComicSession(ComicBook.open(TestBooks.cbz(dir.resolve("w.cbz"), pages, null)))) {
            assertArrayEquals(new boolean[]{false, true}, s.wide());
        }
    }
}
