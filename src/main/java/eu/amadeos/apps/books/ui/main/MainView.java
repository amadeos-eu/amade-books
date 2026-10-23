package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.BookInfo;
import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.Settings;
import eu.amadeos.apps.books.domain.epub.EpubSession;
import eu.amadeos.apps.books.domain.epub.TocEntry;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.layout.ChapterLayout;
import eu.amadeos.apps.books.domain.layout.PageGeometry;
import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.Bookmark;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.apps.books.domain.library.ShelfFilter;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The window as the presenter sees it: the library, the reader for books and the reader for comics. EDT only. */
public interface MainView {
    void showSettings(Settings settings);

    // ---------------------------------------------------------------- library

    /** Shows the shelf: the sidebar lines with their counts, the chosen one, and the books it leaves. */
    void showLibrary(Map<ShelfFilter, Integer> facets, ShelfFilter selected, List<LibraryEntry> books,
                     Function<LibraryEntry, BookState> states);

    /** The library is being scanned (true) or the scan is done. */
    void showScanning(boolean running);

    // ---------------------------------------------------------------- books

    /** Switches to the reader for an EPUB book. */
    void showBook(BookInfo info, List<TocEntry> toc);

    void showAppearance(Appearance appearance, boolean bookHasFonts);

    /** The size of the area the pages are drawn in, for the layout. */
    Dimension readingArea();

    /**
     * Shows pages of a chapter.
     *
     * @param firstPage the page in the left column (or the only one)
     * @param heads     running heads of the left and right page
     */
    void showPages(EpubSession session, ChapterLayout layout, PageGeometry geometry, int chapter, int firstPage,
                   String[] heads, ReadTheme theme);

    /**
     * The place in the book for the status bar and the progress bar.
     *
     * @param progress -1 while the length of the book is not known yet
     */
    void showPlace(String chapterTitle, int page, int pages, double progress, List<Double> chapterStarts,
                   boolean bookmarked);

    /** Marks the table of contents entry of the place; -1 for none. */
    void showTocEntry(int index);

    void showBookmarks(List<Bookmark> bookmarks);

    void showSearch(SearchState state);

    void showHistory(boolean back, boolean forward);

    /** A footnote in a small window near the mouse. */
    void showFootnote(String text);

    /** The selected text as chapter and offsets {from, to}; empty when nothing is selected. */
    Optional<int[]> selection();

    // ---------------------------------------------------------------- comics

    void showComic(BookInfo info, int pageCount);

    /**
     * Shows one or two comic pages.
     *
     * @param pages     the pictures from left to right as they appear on screen; null for a page still loading
     * @param numbers   their page numbers, 1-based, in the same order
     */
    void showComicPages(List<BufferedImage> pages, List<Integer> numbers, ComicFit fit, double zoom,
                        boolean rightToLeft, boolean twoPages);

    // ---------------------------------------------------------------- common

    /** A short note in the status bar, e.g. "Bookmark set". */
    void showStatus(String message);

    void setFullScreen(boolean on);

    boolean fullScreen();

    Optional<Path> chooseFile(Optional<Path> folder);

    List<Path> chooseBooks(Optional<Path> folder);

    /** Asks for a collection name, offering the existing ones; empty when cancelled. */
    Optional<String> askCollection(List<String> existing);

    /** Lets the reader change the library folders; empty when cancelled. */
    Optional<List<Path>> editFolders(List<Path> folders);

    void copyToClipboard(String text);

    void focusSearch();

    void beep();

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();
}
