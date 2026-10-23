package eu.amadeos.apps.books.domain.library;

import eu.amadeos.apps.books.domain.BookInfo;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A book on the shelf: its metadata, the file's size and modification time (to notice a changed file), when it
 * was first seen, and the cover thumbnail in the cache.
 */
public record LibraryEntry(BookInfo info, long size, long modified, long added, Optional<Path> cover) {
    public Path file() {
        return info.file();
    }
}
