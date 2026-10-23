package eu.amadeos.apps.books.domain.comic;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An open comic: decoded pages with a small cache (pages are large), and the page sizes for the spreads.
 * Pages are decoded on the book's background thread; the window asks with {@link #cached(int)}.
 */
public final class ComicSession implements AutoCloseable {
    private static final int KEEP = 8;

    private final ComicBook book;
    private final Map<Integer, BufferedImage> pages = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
            return size() > KEEP;
        }
    });
    private boolean[] wide;

    public ComicSession(ComicBook book) {
        this.book = book;
    }

    public ComicBook book() {
        return book;
    }

    public int pageCount() {
        return book.pageCount();
    }

    /** Decodes the page if it is not in the cache; may take a moment for large scans. */
    public Optional<BufferedImage> page(int page) {
        BufferedImage cached = pages.get(page);
        if (cached != null) return Optional.of(cached);
        try {
            Optional<BufferedImage> image = book.page(page);
            image.ifPresent(i -> pages.put(page, i));
            return image;
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The page if it is decoded already. */
    public Optional<BufferedImage> cached(int page) {
        return Optional.ofNullable(pages.get(page));
    }

    /** For every page whether it is wider than high, read from the picture headers once. */
    public synchronized boolean[] wide() {
        if (wide == null) {
            boolean[] result = new boolean[pageCount()];
            for (int i = 0; i < result.length; i++) {
                Optional<Dimension> size = size(i);
                result[i] = size.isPresent() && size.get().width > size.get().height;
            }
            wide = result;
        }
        return wide.clone();
    }

    /** The spreads for single or two-page view. */
    public List<Spreads.Spread> spreads(boolean twoPages) {
        return Spreads.of(wide(), twoPages);
    }

    private Optional<Dimension> size(int page) {
        try {
            var entry = book.pageName(page);
            Optional<BufferedImage> cached = cached(page);
            if (cached.isPresent()) return Optional.of(new Dimension(cached.get().getWidth(), cached.get().getHeight()));
            try (java.io.InputStream raw = book.open(entry);
                 ImageInputStream in = ImageIO.createImageInputStream(raw)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return Optional.empty();
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return Optional.of(new Dimension(reader.getWidth(0), reader.getHeight(0)));
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {
        book.close();
    }
}
