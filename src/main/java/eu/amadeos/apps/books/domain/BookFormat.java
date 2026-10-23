package eu.amadeos.apps.books.domain;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** The kinds of files the reader opens: reflowable EPUB books and CBZ comics (a ZIP of page pictures). */
public enum BookFormat {
    EPUB("epub"),
    CBZ("cbz");

    private final String extension;

    BookFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public boolean comic() {
        return this == CBZ;
    }

    /** The format by the file name's extension; empty for other files. */
    public static Optional<BookFormat> of(Path file) {
        Path name = file.getFileName();
        if (name == null) return Optional.empty();
        String lower = name.toString().toLowerCase(Locale.ROOT);
        for (BookFormat format : values()) {
            if (lower.endsWith("." + format.extension)) return Optional.of(format);
        }
        return Optional.empty();
    }
}
