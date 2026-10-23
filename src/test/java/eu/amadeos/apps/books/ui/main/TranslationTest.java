package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.bootstrap.BooksModule;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(BooksModule.class, BooksModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(BooksModule.class, BooksModule.MESSAGES));
    }

    /** No two entries share a mnemonic letter in the menu bar or in a menu. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = new HashMap<>();
        groups.put("bar", List.of("menu.file", "menu.edit", "menu.view", "menu.go", "menu.options", "menu.help"));
        groups.put("file", List.of("menu.file.open", "menu.file.add", "menu.file.library", "menu.file.folders",
                "menu.file.rescan", "menu.file.exit"));
        groups.put("edit", List.of("menu.edit.copy", "menu.edit.find", "menu.edit.find-next", "menu.edit.find-previous"));
        groups.put("view", List.of("menu.view.sidebar", "menu.view.contents", "menu.view.bookmarks",
                "menu.view.two-pages", "menu.view.theme.paper", "menu.view.theme.sepia", "menu.view.theme.night",
                "menu.view.bigger", "menu.view.smaller", "menu.view.comic", "menu.view.full-screen"));
        groups.put("comic", List.of("menu.comic.fit.page", "menu.comic.fit.width", "menu.comic.fit.height",
                "menu.comic.zoom-in", "menu.comic.zoom-out", "menu.comic.actual-size", "menu.comic.right-to-left"));
        groups.put("go", List.of("menu.go.previous", "menu.go.next", "menu.go.first", "menu.go.last", "menu.go.back",
                "menu.go.forward", "menu.go.bookmark"));
        groups.put("options", List.of("menu.options.reopen", "menu.options.language"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertEquals(null, before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void windowTitles() {
        assertEquals("Bücher", texts(Language.GERMAN).get("window.title"));
        assertEquals("Livres", texts(Language.FRENCH).get("window.title"));
        assertEquals("Moby-Dick - Libros", texts(Language.SPANISH).format("window.title.book", "Moby-Dick"));
    }
}
