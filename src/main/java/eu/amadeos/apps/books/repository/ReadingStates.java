package eu.amadeos.apps.books.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.Bookmark;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.json.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reading progress, bookmarks, collections and per-book choices in
 * {@code ~/.local/share/amadeos/books/library.json}, by the book's absolute path. Kept apart from the cache, so
 * clearing the cache loses nothing the reader did.
 */
public class ReadingStates {
    public static final String FILE = "library.json";

    private final Path file;
    private final Map<String, BookState> states = new LinkedHashMap<>();

    @Inject
    public ReadingStates(XdgDirectories xdg) {
        this(xdg.dataHome().resolve("amadeos").resolve("books").resolve(FILE));
    }

    public ReadingStates(Path file) {
        this.file = file;
        Map<String, Object> root = JsonFiles.read(file);
        if (root.get("books") instanceof Map<?, ?> books) {
            books.forEach((key, value) -> states.put(String.valueOf(key), state(value)));
        }
    }

    public synchronized BookState get(Path book) {
        return states.getOrDefault(key(book), BookState.NEW);
    }

    public synchronized void put(Path book, BookState state) {
        states.put(key(book), state);
        save();
    }

    /** Every collection name in use, sorted. */
    public synchronized List<String> collections() {
        return states.values().stream().flatMap(s -> s.collections().stream()).distinct().sorted().toList();
    }

    private static String key(Path book) {
        return book.toAbsolutePath().normalize().toString();
    }

    // --8<-- [start:state]
    private static BookState state(Object o) {
        List<Bookmark> bookmarks = new ArrayList<>();
        for (Object b : Json.list(o, "bookmarks")) {
            bookmarks.add(new Bookmark(new Position(Json.integer(b, "chapter"), Json.integer(b, "offset")),
                    Json.str(b, "text"), JsonFiles.longNumber(b, "created")));
        }
        Optional<Appearance> appearance = Optional.empty();
        Object a = Json.member(o, "appearance");
        if (a instanceof Map<?, ?>) {
            Appearance d = Appearance.DEFAULT;
            appearance = Optional.of(new Appearance(Json.str(a, "font").isEmpty() ? d.family() : Json.str(a, "font"),
                    (int) JsonFiles.number(a, "size", d.size()), JsonFiles.number(a, "lineSpacing", d.lineSpacing()),
                    JsonFiles.number(a, "margin", d.margin()), JsonFiles.flag(a, "justify"),
                    JsonFiles.flag(a, "hyphenate")));
        }
        return new BookState(new Position(Json.integer(o, "chapter"), Json.integer(o, "offset")),
                JsonFiles.number(o, "progress", 0), JsonFiles.longNumber(o, "lastRead"), JsonFiles.flag(o, "finished"),
                bookmarks, JsonFiles.strings(o, "collections"), appearance, optionalFlag(o, "rightToLeft"),
                optionalFlag(o, "twoPages"));
    }
    // --8<-- [end:state]

    private static Optional<Boolean> optionalFlag(Object o, String key) {
        return Json.member(o, key) instanceof Boolean b ? Optional.of(b) : Optional.empty();
    }

    private void save() {
        Map<String, Object> books = new LinkedHashMap<>();
        states.forEach((key, s) -> {
            if (s.equals(BookState.NEW)) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chapter", s.position().chapter());
            m.put("offset", s.position().offset());
            m.put("progress", Double.valueOf(String.format(Locale.ROOT, "%.4f", s.progress())));
            m.put("lastRead", s.lastRead());
            m.put("finished", s.finished());
            List<Object> marks = new ArrayList<>();
            for (Bookmark b : s.bookmarks()) {
                Map<String, Object> mark = new LinkedHashMap<>();
                mark.put("chapter", b.position().chapter());
                mark.put("offset", b.position().offset());
                mark.put("text", b.excerpt());
                mark.put("created", b.created());
                marks.add(mark);
            }
            m.put("bookmarks", marks);
            m.put("collections", new ArrayList<>(s.collections()));
            s.appearance().ifPresent(a -> {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("font", a.family());
                am.put("size", a.size());
                am.put("lineSpacing", a.lineSpacing());
                am.put("margin", a.margin());
                am.put("justify", a.justify());
                am.put("hyphenate", a.hyphenate());
                m.put("appearance", am);
            });
            s.rightToLeft().ifPresent(v -> m.put("rightToLeft", v));
            s.twoPages().ifPresent(v -> m.put("twoPages", v));
            books.put(key, m);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("books", books);
        JsonFiles.write(file, root);
    }
}
