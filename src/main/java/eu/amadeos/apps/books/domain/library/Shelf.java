package eu.amadeos.apps.books.domain.library;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The library's books with their reading states: the groups and facets of the sidebar with their counts, and the
 * books a filter, a search and a sort order leave.
 */
public final class Shelf {
    private final List<LibraryEntry> entries;
    private final Function<LibraryEntry, BookState> states;
    private final Collator collator;

    public Shelf(List<LibraryEntry> entries, Function<LibraryEntry, BookState> states, Locale locale) {
        this.entries = List.copyOf(entries);
        this.states = states;
        this.collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
    }

    public List<LibraryEntry> entries() {
        return entries;
    }

    // --8<-- [start:facets]
    /** Every line of the sidebar with its number of books, in display order; facets sorted by name. */
    public Map<ShelfFilter, Integer> facets() {
        Map<ShelfFilter, Integer> result = new LinkedHashMap<>();
        for (ShelfFilter.Kind kind : List.of(ShelfFilter.Kind.ALL, ShelfFilter.Kind.READING, ShelfFilter.Kind.NOT_STARTED,
                ShelfFilter.Kind.FINISHED, ShelfFilter.Kind.BOOKS, ShelfFilter.Kind.COMICS)) {
            ShelfFilter filter = ShelfFilter.of(kind);
            result.put(filter, (int) entries.stream().filter(e -> matches(e, filter)).count());
        }
        facet(result, ShelfFilter.Kind.GENRE, e -> e.info().genres());
        facet(result, ShelfFilter.Kind.AUTHOR, e -> e.info().authors());
        facet(result, ShelfFilter.Kind.SERIES, e -> e.info().series().isEmpty() ? List.of() : List.of(e.info().series()));
        facet(result, ShelfFilter.Kind.COLLECTION, e -> states.apply(e).collections());
        return result;
    }
    // --8<-- [end:facets]

    private void facet(Map<ShelfFilter, Integer> result, ShelfFilter.Kind kind, Function<LibraryEntry, List<String>> values) {
        Map<String, Integer> counts = new TreeMap<>(collator);
        for (LibraryEntry e : entries) {
            for (String v : values.apply(e)) counts.merge(v, 1, Integer::sum);
        }
        counts.forEach((v, n) -> result.put(new ShelfFilter(kind, v), n));
    }

    public boolean matches(LibraryEntry e, ShelfFilter filter) {
        BookState s = states.apply(e);
        return switch (filter.kind()) {
            case ALL -> true;
            case READING -> s.reading();
            case NOT_STARTED -> !s.started() && !s.finished();
            case FINISHED -> s.finished();
            case BOOKS -> !e.info().format().comic();
            case COMICS -> e.info().format().comic();
            case GENRE -> e.info().genres().stream().anyMatch(g -> collator.equals(g, filter.value()));
            case AUTHOR -> e.info().authors().stream().anyMatch(a -> collator.equals(a, filter.value()));
            case SERIES -> collator.equals(e.info().series(), filter.value());
            case COLLECTION -> s.collections().contains(filter.value());
        };
    }

    // --8<-- [start:books]
    /** The books the filter and the search leave, sorted; the search looks at title, authors and series. */
    public List<LibraryEntry> books(ShelfFilter filter, String query, SortOrder order) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<LibraryEntry> result = new ArrayList<>();
        for (LibraryEntry e : entries) {
            if (!matches(e, filter)) continue;
            if (!q.isEmpty()) {
                String text = (e.info().title() + " " + e.info().authorLine() + " " + e.info().series()).toLowerCase(Locale.ROOT);
                if (!text.contains(q)) continue;
            }
            result.add(e);
        }
        Comparator<LibraryEntry> byTitle = Comparator.comparing(e -> e.info().title(), collator);
        Comparator<LibraryEntry> comparator = switch (order) {
            case RECENT -> Comparator.<LibraryEntry>comparingLong(e -> -states.apply(e).lastRead()).thenComparing(byTitle);
            case TITLE -> byTitle;
            case AUTHOR -> Comparator.<LibraryEntry, String>comparing(e -> sortName(e.info().authors()), collator)
                    .thenComparing(byTitle);
            case SERIES -> Comparator.<LibraryEntry, String>comparing(e -> e.info().series().isEmpty() ? "￿"
                            : e.info().series(), collator)
                    .thenComparingDouble(e -> e.info().seriesIndex()).thenComparing(byTitle);
            case ADDED -> Comparator.<LibraryEntry>comparingLong(e -> -e.added()).thenComparing(byTitle);
        };
        result.sort(comparator);
        return result;
    }
    // --8<-- [end:books]

    /** Authors sort by last name: "Lewis Carroll" as "Carroll Lewis"; "Carroll, Lewis" stays. */
    static String sortName(List<String> authors) {
        if (authors.isEmpty()) return "￿";
        String a = authors.getFirst().strip();
        if (a.contains(",")) return a;
        int space = a.lastIndexOf(' ');
        return space > 0 ? a.substring(space + 1) + " " + a.substring(0, space) : a;
    }
}
