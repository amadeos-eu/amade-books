package eu.amadeos.apps.books.domain.library;

import java.util.Objects;

/**
 * One line of the library's sidebar: a fixed group or a value of a facet.
 *
 * @param value the genre, author, series or collection; empty for the fixed groups and formats
 */
public record ShelfFilter(Kind kind, String value) {
    public enum Kind {
        ALL, READING, NOT_STARTED, FINISHED, BOOKS, COMICS, GENRE, AUTHOR, SERIES, COLLECTION
    }

    public static final ShelfFilter ALL = new ShelfFilter(Kind.ALL, "");

    public ShelfFilter {
        Objects.requireNonNull(kind, "kind");
        value = value == null ? "" : value;
    }

    public static ShelfFilter of(Kind kind) {
        return new ShelfFilter(kind, "");
    }
}
