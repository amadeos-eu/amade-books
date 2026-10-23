package eu.amadeos.apps.books.domain;

import eu.amadeos.apps.books.domain.library.SortOrder;

/**
 * The reader's choices that are not per book.
 *
 * @param twoPages      books show two pages side by side when the window is wide enough
 * @param sidebar       the contents and bookmarks are shown beside the book
 * @param bookmarksTab  the sidebar shows the bookmarks instead of the contents
 * @param listView      the library is a list instead of covers
 * @param comicTwoPages comics show two pages unless a comic says otherwise
 * @param reopenLast    the program starts with the book read last, at its place
 */
public record Settings(ReadTheme theme, boolean twoPages, boolean sidebar, boolean bookmarksTab, boolean listView,
                       SortOrder sort, ComicFit comicFit, boolean comicTwoPages, boolean reopenLast) {
    public static final Settings DEFAULT = new Settings(ReadTheme.PAPER, true, false, false, false, SortOrder.RECENT,
            ComicFit.PAGE, true, true);

    public Settings withTheme(ReadTheme t) {
        return new Settings(t, twoPages, sidebar, bookmarksTab, listView, sort, comicFit, comicTwoPages, reopenLast);
    }

    public Settings withTwoPages(boolean on) {
        return new Settings(theme, on, sidebar, bookmarksTab, listView, sort, comicFit, comicTwoPages, reopenLast);
    }

    public Settings withSidebar(boolean on, boolean bookmarks) {
        return new Settings(theme, twoPages, on, bookmarks, listView, sort, comicFit, comicTwoPages, reopenLast);
    }

    public Settings withListView(boolean on) {
        return new Settings(theme, twoPages, sidebar, bookmarksTab, on, sort, comicFit, comicTwoPages, reopenLast);
    }

    public Settings withSort(SortOrder s) {
        return new Settings(theme, twoPages, sidebar, bookmarksTab, listView, s, comicFit, comicTwoPages, reopenLast);
    }

    public Settings withComicFit(ComicFit f) {
        return new Settings(theme, twoPages, sidebar, bookmarksTab, listView, sort, f, comicTwoPages, reopenLast);
    }

    public Settings withComicTwoPages(boolean on) {
        return new Settings(theme, twoPages, sidebar, bookmarksTab, listView, sort, comicFit, on, reopenLast);
    }

    public Settings withReopenLast(boolean on) {
        return new Settings(theme, twoPages, sidebar, bookmarksTab, listView, sort, comicFit, comicTwoPages, on);
    }
}
