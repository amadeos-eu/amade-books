package eu.amadeos.apps.books.domain.epub;

/**
 * One line of a book's table of contents.
 *
 * @param chapter  index in the spine; -1 when the link leads outside the reading order
 * @param fragment the anchor inside the chapter without {@code #}, empty for its start
 * @param depth    0 for top-level entries, 1 for their sub-entries and so on
 */
public record TocEntry(String label, int chapter, String fragment, int depth) {
    public TocEntry {
        label = label == null ? "" : label.replaceAll("\\s+", " ").strip();
        fragment = fragment == null ? "" : fragment;
    }
}
