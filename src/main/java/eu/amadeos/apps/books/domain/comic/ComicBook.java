package eu.amadeos.apps.books.domain.comic;

import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.BookInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A comic book archive (CBZ): a ZIP of page pictures, read in natural name order, and an optional
 * {@code ComicInfo.xml} with title, series, number, writer, genre and whether it reads right to left (manga).
 * Folders inside the archive are allowed; hidden files and the macOS resource folder are skipped.
 */
public final class ComicBook implements AutoCloseable {
    static final List<String> PICTURES = List.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");

    private final ZipFile zip;
    private final List<String> pages;
    private final BookInfo info;
    private final boolean rightToLeft;

    private ComicBook(ZipFile zip, List<String> pages, BookInfo info, boolean rightToLeft) {
        this.zip = zip;
        this.pages = List.copyOf(pages);
        this.info = info;
        this.rightToLeft = rightToLeft;
    }

    // --8<-- [start:open]
    public static ComicBook open(Path file) throws IOException {
        ZipFile zip = new ZipFile(file.toFile());
        try {
            List<String> pages = new ArrayList<>();
            ZipEntry comicInfo = null;
            for (ZipEntry entry : zip.stream().toList()) {
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                String base = lower.substring(lower.lastIndexOf('/') + 1);
                if (entry.isDirectory() || lower.startsWith("__macosx/") || base.startsWith(".")) continue;
                if (base.equals("comicinfo.xml")) comicInfo = entry;
                else if (PICTURES.stream().anyMatch(lower::endsWith)) pages.add(name);
            }
            if (pages.isEmpty()) throw new IOException("No pictures in the comic archive " + file.getFileName());
            pages.sort(NaturalOrder.INSTANCE);
            String title = null;
            String series = "";
            double number = 0;
            List<String> authors = new ArrayList<>();
            List<String> genres = new ArrayList<>();
            String language = "";
            boolean rtl = false;
            if (comicInfo != null) {
                try (InputStream in = zip.getInputStream(comicInfo)) {
                    Document doc = Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "", Parser.xmlParser());
                    title = text(doc, "Title");
                    series = text(doc, "Series");
                    try {
                        number = Double.parseDouble(text(doc, "Number"));
                    } catch (NumberFormatException e) {
                        number = 0;
                    }
                    for (String role : List.of("Writer", "Penciller", "Artist")) {
                        for (String name : text(doc, role).split("\\s*,\\s*")) {
                            if (!name.isBlank() && !authors.contains(name)) authors.add(name);
                        }
                    }
                    for (String g : text(doc, "Genre").split("\\s*,\\s*")) {
                        if (!g.isBlank()) genres.add(g);
                    }
                    language = text(doc, "LanguageISO");
                    rtl = text(doc, "Manga").equalsIgnoreCase("YesAndRightToLeft");
                }
            }
            if ((title == null || title.isBlank()) && !series.isEmpty()) {
                title = series + (number > 0 ? " " + (number == Math.rint(number) ? String.valueOf((long) number)
                        : String.valueOf(number)) : "");
            }
            return new ComicBook(zip, pages, new BookInfo(file, BookFormat.CBZ, title, authors, series, number, genres,
                    language), rtl);
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
    }
    // --8<-- [end:open]

    private static String text(Document doc, String tag) {
        Element e = doc.selectFirst(tag);
        return e == null ? "" : e.text().strip();
    }

    public BookInfo info() {
        return info;
    }

    public int pageCount() {
        return pages.size();
    }

    /** The archive says it reads right to left ({@code <Manga>YesAndRightToLeft</Manga>}). */
    public boolean rightToLeft() {
        return rightToLeft;
    }

    public String pageName(int page) {
        return pages.get(page);
    }

    /** A stream of a file in the archive; the caller closes it. */
    InputStream open(String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("Missing in the archive: " + name);
        return zip.getInputStream(entry);
    }

    /** A page picture, decoded; empty when it cannot be read. */
    public Optional<BufferedImage> page(int page) throws IOException {
        ZipEntry entry = zip.getEntry(pages.get(page));
        if (entry == null) return Optional.empty();
        try (InputStream in = zip.getInputStream(entry)) {
            return Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(in.readAllBytes())));
        }
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
