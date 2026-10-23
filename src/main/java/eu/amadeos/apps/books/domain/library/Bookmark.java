package eu.amadeos.apps.books.domain.library;

import eu.amadeos.apps.books.domain.Position;

/** A place the reader marked, with the start of the text there and when it was set. */
public record Bookmark(Position position, String excerpt, long created) {
}
