package eu.amadeos.apps.books.domain.library;

import java.util.Optional;

/** How the shelf is sorted. */
public enum SortOrder {
    RECENT("recent"), TITLE("title"), AUTHOR("author"), SERIES("series"), ADDED("added");

    private final String key;

    SortOrder(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<SortOrder> forKey(String key) {
        for (SortOrder o : values()) {
            if (o.key.equals(key)) return Optional.of(o);
        }
        return Optional.empty();
    }
}
