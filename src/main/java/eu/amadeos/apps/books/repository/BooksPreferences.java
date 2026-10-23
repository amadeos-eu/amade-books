package eu.amadeos.apps.books.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.books.domain.ComicFit;
import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.Settings;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.library.Shelf;
import eu.amadeos.apps.books.domain.library.SortOrder;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings of amade-books in {@code ~/.config/amadeos/books/books.properties}: the window's choices, the default
 * appearance of books, the library folders and the book read last. Each change is written at once; unknown or
 * broken values fall back to the defaults.
 */
public class BooksPreferences {
    public static final String FILE = "books.properties";

    private final Path file;
    private final Path defaultFolder;
    private final Path includedFolder;
    private final Properties properties;

    @Inject
    public BooksPreferences(AppDirectories directories, @LibraryFolder Path defaultFolder) {
        this(directories.configFile(FILE), defaultFolder, Shelf.INCLUDED_FOLDER);
    }

    public BooksPreferences(Path file, Path defaultFolder) {
        this(file, defaultFolder, null);
    }

    /** @param includedFolder the books that come with the system, a library folder until the reader removes it */
    public BooksPreferences(Path file, Path defaultFolder, Path includedFolder) {
        this.file = Objects.requireNonNull(file, "file");
        this.defaultFolder = defaultFolder;
        this.includedFolder = includedFolder;
        this.properties = PropertiesFile.load(file);
    }

    public Settings settings() {
        Settings d = Settings.DEFAULT;
        return new Settings(ReadTheme.forKey(get("theme")).orElse(d.theme()), flag("two-pages", d.twoPages()),
                flag("sidebar", d.sidebar()), "bookmarks".equals(get("sidebar-tab")), "list".equals(get("library-view")),
                SortOrder.forKey(get("sort")).orElse(d.sort()), ComicFit.forKey(get("comic-fit")).orElse(d.comicFit()),
                flag("comic-two-pages", d.comicTwoPages()), flag("reopen-last", d.reopenLast()));
    }

    public void setSettings(Settings s) {
        properties.setProperty("theme", s.theme().key());
        properties.setProperty("two-pages", String.valueOf(s.twoPages()));
        properties.setProperty("sidebar", String.valueOf(s.sidebar()));
        properties.setProperty("sidebar-tab", s.bookmarksTab() ? "bookmarks" : "contents");
        properties.setProperty("library-view", s.listView() ? "list" : "covers");
        properties.setProperty("sort", s.sort().key());
        properties.setProperty("comic-fit", s.comicFit().key());
        properties.setProperty("comic-two-pages", String.valueOf(s.comicTwoPages()));
        properties.setProperty("reopen-last", String.valueOf(s.reopenLast()));
        save();
    }

    // --8<-- [start:appearance]
    /** The appearance of books that have none of their own. */
    public Appearance appearance() {
        Appearance d = Appearance.DEFAULT;
        String family = get("font");
        return new Appearance(family == null || family.isBlank() ? d.family() : family.strip(),
                (int) number("size", d.size()), number("line-spacing", d.lineSpacing()), number("margin", d.margin()),
                flag("justify", d.justify()), flag("hyphenate", d.hyphenate()));
    }

    public void setAppearance(Appearance a) {
        properties.setProperty("font", a.family());
        properties.setProperty("size", String.valueOf(a.size()));
        properties.setProperty("line-spacing", String.format(Locale.ROOT, "%.2f", a.lineSpacing()));
        properties.setProperty("margin", String.format(Locale.ROOT, "%.3f", a.margin()));
        properties.setProperty("justify", String.valueOf(a.justify()));
        properties.setProperty("hyphenate", String.valueOf(a.hyphenate()));
        save();
    }
    // --8<-- [end:appearance]

    /**
     * The folders the library shows; when none were chosen {@code ~/Books} and, if installed, the books that come
     * with amadeOS.
     */
    public List<Path> folders() {
        List<Path> folders = new ArrayList<>();
        for (int i = 0; i < 20; i++) path(get("folder." + i)).ifPresent(folders::add);
        if (folders.isEmpty()) {
            if (defaultFolder != null) folders.add(defaultFolder);
            if (includedFolder != null && Files.isDirectory(includedFolder)) folders.add(includedFolder);
        }
        return folders;
    }

    public void setFolders(List<Path> folders) {
        for (int i = 0; i < 20; i++) {
            if (i < folders.size()) properties.setProperty("folder." + i, folders.get(i).toAbsolutePath().normalize().toString());
            else properties.remove("folder." + i);
        }
        save();
    }

    /** The folder new books are copied to: the first library folder that is not the system's. */
    public Path mainFolder() {
        return folders().stream().filter(f -> includedFolder == null || !f.startsWith(includedFolder)).findFirst()
                .orElse(defaultFolder);
    }

    public Optional<Path> lastBook() {
        return path(get("last-book"));
    }

    public void setLastBook(Path book) {
        if (book == null) properties.remove("last-book");
        else properties.setProperty("last-book", book.toAbsolutePath().normalize().toString());
        save();
    }

    /** The folder of the last Open, if one was remembered. */
    public Optional<Path> lastFolder() {
        return path(get("last-folder"));
    }

    public void setLastFolder(Path folder) {
        properties.setProperty("last-folder", folder.toAbsolutePath().normalize().toString());
        save();
    }

    private String get(String key) {
        return properties.getProperty(key);
    }

    private boolean flag(String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : !"false".equals(value.strip());
    }

    private double number(String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Optional<Path> path(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(value.strip()));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private void save() {
        PropertiesFile.store(file, properties, "amade-books");
    }
}
