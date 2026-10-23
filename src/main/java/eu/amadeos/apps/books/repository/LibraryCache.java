package eu.amadeos.apps.books.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.BookInfo;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.json.Json;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the last library scan found, in {@code ~/.cache/amadeos/books/library-cache.json}, with the cover
 * thumbnails in {@code covers/} next to it. The shelf appears at once from here while a new scan runs.
 */
public class LibraryCache {
    public static final String FILE = "library-cache.json";

    private final Path file;

    @Inject
    public LibraryCache(AppDirectories directories) {
        this(directories.cache().resolve(FILE));
    }

    public LibraryCache(Path file) {
        this.file = file;
    }

    /** Where the cover thumbnails go. */
    public Path coverDirectory() {
        return file.resolveSibling("covers");
    }

    public synchronized List<LibraryEntry> load() {
        List<LibraryEntry> entries = new ArrayList<>();
        for (Object o : Json.list(JsonFiles.read(file), "books")) {
            try {
                Path path = Path.of(Json.str(o, "file"));
                Optional<BookFormat> format = BookFormat.of(path);
                if (format.isEmpty()) continue;
                BookInfo info = new BookInfo(path, format.get(), Json.str(o, "title"), JsonFiles.strings(o, "authors"),
                        Json.str(o, "series"), JsonFiles.number(o, "seriesIndex", 0), JsonFiles.strings(o, "genres"),
                        Json.str(o, "language"));
                String cover = Json.str(o, "cover");
                entries.add(new LibraryEntry(info, JsonFiles.longNumber(o, "size"), JsonFiles.longNumber(o, "modified"),
                        JsonFiles.longNumber(o, "added"), cover.isEmpty() ? Optional.empty() : Optional.of(Path.of(cover))));
            } catch (InvalidPathException e) {
                // a broken line is scanned again
            }
        }
        return entries;
    }

    public synchronized void save(List<LibraryEntry> entries) {
        List<Object> books = new ArrayList<>();
        for (LibraryEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", e.file().toString());
            m.put("title", e.info().title());
            m.put("authors", new ArrayList<>(e.info().authors()));
            m.put("series", e.info().series());
            m.put("seriesIndex", e.info().seriesIndex());
            m.put("genres", new ArrayList<>(e.info().genres()));
            m.put("language", e.info().language());
            m.put("size", e.size());
            m.put("modified", e.modified());
            m.put("added", e.added());
            m.put("cover", e.cover().map(Path::toString).orElse(""));
            books.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("books", books);
        JsonFiles.write(file, root);
    }
}
