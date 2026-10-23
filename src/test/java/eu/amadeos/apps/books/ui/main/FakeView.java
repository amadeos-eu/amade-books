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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** A window that remembers what it was asked to show. */
class FakeView implements MainView {
    Settings settings;
    Map<ShelfFilter, Integer> facets = Map.of();
    ShelfFilter filter;
    List<LibraryEntry> books = List.of();
    boolean scanning;
    BookInfo info;
    List<TocEntry> toc = List.of();
    Appearance appearance;
    boolean bookFonts;
    Dimension area = new Dimension(1000, 700);
    ChapterLayout layout;
    PageGeometry geometry;
    int chapter = -1;
    int firstPage = -1;
    String[] heads;
    ReadTheme theme;
    String place = "";
    int page;
    int pages;
    double progress;
    boolean bookmarked;
    int tocEntry = -2;
    List<Bookmark> bookmarks = List.of();
    SearchState search = SearchState.NONE;
    boolean back;
    boolean forward;
    String footnote;
    int[] selection;
    int comicPages = -1;
    List<BufferedImage> comicShown = List.of();
    List<Integer> comicNumbers = List.of();
    ComicFit fit;
    double zoom;
    boolean rightToLeft;
    boolean comicTwo;
    String status = "";
    boolean fullScreen;
    final Deque<Optional<Path>> files = new ArrayDeque<>();
    List<Path> chosenBooks = List.of();
    Optional<String> collection = Optional.empty();
    Optional<List<Path>> folders = Optional.empty();
    String clipboard;
    boolean searchFocused;
    int beeps;
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    boolean about;
    boolean closed;

    @Override
    public void showSettings(Settings newSettings) {
        settings = newSettings;
    }

    @Override
    public void showLibrary(Map<ShelfFilter, Integer> newFacets, ShelfFilter selected, List<LibraryEntry> newBooks,
                            Function<LibraryEntry, BookState> states) {
        facets = newFacets;
        filter = selected;
        books = newBooks;
        info = null;
        comicPages = -1;
    }

    @Override
    public void showScanning(boolean running) {
        scanning = running;
    }

    @Override
    public void showBook(BookInfo newInfo, List<TocEntry> newToc) {
        info = newInfo;
        toc = newToc;
    }

    @Override
    public void showAppearance(Appearance a, boolean fonts) {
        appearance = a;
        bookFonts = fonts;
    }

    @Override
    public Dimension readingArea() {
        return area;
    }

    @Override
    public void showPages(EpubSession session, ChapterLayout newLayout, PageGeometry newGeometry, int newChapter,
                          int newFirstPage, String[] newHeads, ReadTheme newTheme) {
        layout = newLayout;
        geometry = newGeometry;
        chapter = newChapter;
        firstPage = newFirstPage;
        heads = newHeads;
        theme = newTheme;
    }

    @Override
    public void showPlace(String chapterTitle, int newPage, int newPages, double newProgress, List<Double> chapterStarts,
                          boolean newBookmarked) {
        place = chapterTitle;
        page = newPage;
        pages = newPages;
        progress = newProgress;
        bookmarked = newBookmarked;
    }

    @Override
    public void showTocEntry(int index) {
        tocEntry = index;
    }

    @Override
    public void showBookmarks(List<Bookmark> newBookmarks) {
        bookmarks = newBookmarks;
    }

    @Override
    public void showSearch(SearchState state) {
        search = state;
    }

    @Override
    public void showHistory(boolean canBack, boolean canForward) {
        back = canBack;
        forward = canForward;
    }

    @Override
    public void showFootnote(String text) {
        footnote = text;
    }

    @Override
    public Optional<int[]> selection() {
        return Optional.ofNullable(selection);
    }

    @Override
    public void showComic(BookInfo newInfo, int pageCount) {
        info = newInfo;
        comicPages = pageCount;
    }

    @Override
    public void showComicPages(List<BufferedImage> shown, List<Integer> numbers, ComicFit newFit, double newZoom,
                               boolean rtl, boolean two) {
        comicShown = shown;
        comicNumbers = numbers;
        fit = newFit;
        zoom = newZoom;
        rightToLeft = rtl;
        comicTwo = two;
    }

    @Override
    public void showStatus(String message) {
        status = message;
    }

    @Override
    public void setFullScreen(boolean on) {
        fullScreen = on;
    }

    @Override
    public boolean fullScreen() {
        return fullScreen;
    }

    @Override
    public Optional<Path> chooseFile(Optional<Path> folder) {
        return files.isEmpty() ? Optional.empty() : files.pop();
    }

    @Override
    public List<Path> chooseBooks(Optional<Path> folder) {
        return chosenBooks;
    }

    @Override
    public Optional<String> askCollection(List<String> existing) {
        return collection;
    }

    @Override
    public Optional<List<Path>> editFolders(List<Path> current) {
        return folders;
    }

    @Override
    public void copyToClipboard(String text) {
        clipboard = text;
    }

    @Override
    public void focusSearch() {
        searchFocused = true;
    }

    @Override
    public void beep() {
        beeps++;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
        about = true;
    }

    @Override
    public void close() {
        closed = true;
    }
}
