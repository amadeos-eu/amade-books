package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.epub.EpubSession.SearchHit;

import java.util.List;

/**
 * The search in the open book: what was searched, the hits and which one is shown.
 *
 * @param current index into {@code hits}, -1 when none is shown
 * @param running the search over the book is not finished yet
 */
public record SearchState(String query, List<SearchHit> hits, int current, boolean running) {
    public static final SearchState NONE = new SearchState("", List.of(), -1, false);

    public SearchState {
        hits = List.copyOf(hits);
    }

    public boolean active() {
        return !query.isEmpty();
    }

    public SearchHit currentHit() {
        return current >= 0 && current < hits.size() ? hits.get(current) : null;
    }
}
