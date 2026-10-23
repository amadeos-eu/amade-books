package eu.amadeos.apps.books.domain.comic;

import java.util.ArrayList;
import java.util.List;

/**
 * Which pages are shown together in two-page view: the cover alone, then pairs, but a picture that is wider than
 * high (a drawing over two pages) always alone, and the page after it starts a new pair. In single page view
 * every page is its own spread.
 */
public final class Spreads {
    private Spreads() {
    }

    /** One or two pages in reading order; {@code second} is -1 for a single page. */
    public record Spread(int first, int second) {
        public boolean contains(int page) {
            return page == first || page == second;
        }

        public int last() {
            return second >= 0 ? second : first;
        }
    }

    // --8<-- [start:spreads]
    /**
     * @param wide for each page, whether its picture is wider than high; pages not decoded yet count as narrow
     */
    public static List<Spread> of(boolean[] wide, boolean twoPages) {
        List<Spread> result = new ArrayList<>();
        int i = 0;
        while (i < wide.length) {
            boolean alone = !twoPages || i == 0 || wide[i] || i + 1 >= wide.length || wide[i + 1];
            if (alone) {
                result.add(new Spread(i, -1));
                i++;
            } else {
                result.add(new Spread(i, i + 1));
                i += 2;
            }
        }
        return result;
    }
    // --8<-- [end:spreads]

    /** The index of the spread that shows the page. */
    public static int indexOf(List<Spread> spreads, int page) {
        for (int i = 0; i < spreads.size(); i++) {
            if (spreads.get(i).contains(page)) return i;
        }
        return 0;
    }
}
