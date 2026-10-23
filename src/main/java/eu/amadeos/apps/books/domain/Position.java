package eu.amadeos.apps.books.domain;

/**
 * A place in a book: for an EPUB the chapter (index in the reading order) and the character offset in its plain
 * text, so that it survives a change of font or window size; for a comic the page in {@code chapter} and 0.
 */
public record Position(int chapter, int offset) implements Comparable<Position> {
    public static final Position START = new Position(0, 0);

    public Position {
        chapter = Math.max(0, chapter);
        offset = Math.max(0, offset);
    }

    @Override
    public int compareTo(Position o) {
        return chapter != o.chapter ? Integer.compare(chapter, o.chapter) : Integer.compare(offset, o.offset);
    }
}
