package eu.amadeos.apps.books.domain.library;

import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.BookInfo;
import eu.amadeos.apps.books.domain.comic.ComicBook;
import eu.amadeos.apps.books.domain.epub.EpubBook;
import eu.amadeos.apps.books.domain.epub.EpubSession;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Finds the books in the library folders and reads what the shelf shows: metadata and a cover thumbnail
 * (the EPUB's cover picture, the first page of a comic) saved as JPEG in the cache. A file that did not change
 * since the last scan is taken from the cache without opening it.
 */
public final class LibraryScanner {
    /** Height of the cover thumbnails in pixels, enough for large shelves on high-resolution screens. */
    public static final int COVER_HEIGHT = 360;
    static final int DEPTH = 8;
    private static final Logger LOG = LoggerFactory.getLogger(LibraryScanner.class);

    private final Path coverDirectory;
    private final LongSupplier clock;

    public LibraryScanner(Path coverDirectory, LongSupplier clock) {
        this.coverDirectory = coverDirectory;
        this.clock = clock;
    }

    // --8<-- [start:scan]
    /**
     * The books in the folders (and below) and the single files, in that order, each once.
     *
     * @param known the entries of the last scan by file; unchanged files are reused
     */
    public List<LibraryEntry> scan(List<Path> folders, List<Path> files, Map<Path, LibraryEntry> known) {
        Set<Path> found = new LinkedHashSet<>();
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) continue;
            try (Stream<Path> walk = Files.walk(folder, DEPTH)) {
                walk.filter(Files::isRegularFile).filter(p -> BookFormat.of(p).isPresent())
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .sorted().forEach(p -> found.add(p.toAbsolutePath().normalize()));
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot read the library folder {}: {}", folder, e.toString());
            }
        }
        for (Path file : files) {
            if (Files.isRegularFile(file) && BookFormat.of(file).isPresent()) found.add(file.toAbsolutePath().normalize());
        }
        List<LibraryEntry> entries = new ArrayList<>();
        for (Path file : found) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                LibraryEntry old = known.get(file);
                long modified = attributes.lastModifiedTime().toMillis();
                if (old != null && old.size() == attributes.size() && old.modified() == modified
                        && old.cover().map(Files::isRegularFile).orElse(true)) {
                    entries.add(old);
                } else {
                    entries.add(read(file, attributes.size(), modified, old == null ? clock.getAsLong() : old.added()));
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot read {}: {}", file, e.toString());
                BookFormat.of(file).ifPresent(format -> entries.add(new LibraryEntry(
                        new BookInfo(file, format, null, List.of(), "", 0, List.of(), ""), 0, 0, clock.getAsLong(),
                        Optional.empty())));
            }
        }
        return entries;
    }
    // --8<-- [end:scan]

    /** Opens the book once for its metadata and cover. */
    LibraryEntry read(Path file, long size, long modified, long added) throws IOException {
        BookFormat format = BookFormat.of(file).orElseThrow(() -> new IOException("Not a book: " + file));
        BookInfo info;
        Optional<BufferedImage> cover;
        if (format == BookFormat.EPUB) {
            try (EpubSession session = new EpubSession(EpubBook.open(file), new Hyphenators(Path.of("/nonexistent")))) {
                info = session.book().info();
                cover = session.cover();
            }
        } else {
            try (ComicBook comic = ComicBook.open(file)) {
                info = comic.info();
                cover = comic.page(0);
            }
        }
        return new LibraryEntry(info, size, modified, added, cover.flatMap(c -> saveCover(file, c)));
    }

    private Optional<Path> saveCover(Path book, BufferedImage picture) {
        try {
            Files.createDirectories(coverDirectory);
            Path target = coverDirectory.resolve(key(book) + ".jpg");
            ImageIO.write(thumbnail(picture, COVER_HEIGHT), "jpg", target.toFile());
            return Optional.of(target);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot save the cover of {}: {}", book, e.toString());
            return Optional.empty();
        }
    }

    /** The picture scaled to the height, on white (JPEG has no transparency). */
    public static BufferedImage thumbnail(BufferedImage picture, int height) {
        int h = Math.min(height, picture.getHeight());
        int w = Math.max(1, Math.round((float) picture.getWidth() * h / picture.getHeight()));
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(picture, 0, 0, w, h, null);
        g.dispose();
        return result;
    }

    /** A file name for the cover that is the same for the same book path. */
    static String key(Path book) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(book.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(book.hashCode());
        }
    }
}
