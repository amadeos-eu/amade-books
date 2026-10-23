package eu.amadeos.apps.books.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What the library shows of a book or comic: from the EPUB's package document or the comic's
 * {@code ComicInfo.xml}, else from the file name.
 *
 * @param series      empty when the book is not part of a series
 * @param seriesIndex the number in the series, 0 when unknown
 * @param genres      subjects of an EPUB, the genre of a comic; may be empty
 * @param language    a language tag such as {@code en} or {@code de-DE}, empty when unknown
 */
public record BookInfo(Path file, BookFormat format, String title, List<String> authors, String series,
                       double seriesIndex, List<String> genres, String language) {
    public BookInfo {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(format, "format");
        title = title == null || title.isBlank() ? fallbackTitle(file) : title.strip();
        authors = List.copyOf(authors);
        series = series == null ? "" : series.strip();
        genres = List.copyOf(genres);
        language = language == null ? "" : language.strip();
    }

    /** "Moby-Dick" for {@code Moby-Dick.epub}; underscores become spaces. */
    public static String fallbackTitle(Path file) {
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ').strip();
    }

    /** The authors joined for display, "Unknown" left to the caller when there are none. */
    public String authorLine() {
        return String.join(", ", authors);
    }

    /** "Vol. 2" style series number without trailing ".0". */
    public String seriesNumber() {
        if (seriesIndex <= 0) return "";
        return seriesIndex == Math.rint(seriesIndex) ? String.valueOf((long) seriesIndex) : String.valueOf(seriesIndex);
    }
}
