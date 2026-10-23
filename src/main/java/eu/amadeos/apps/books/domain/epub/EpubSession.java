package eu.amadeos.apps.books.domain.epub;

import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.layout.Appearance;
import eu.amadeos.apps.books.domain.layout.ChapterLayout;
import eu.amadeos.apps.books.domain.layout.ChapterParser;
import eu.amadeos.apps.books.domain.layout.ChapterText;
import eu.amadeos.apps.books.domain.layout.Css;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import eu.amadeos.apps.books.domain.layout.Typesetter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An open EPUB book with what reading it needs: chapter texts, their layouts for the current appearance and page
 * size, pictures, the book's fonts, the length of every chapter for the progress, links and search. Chapters are
 * parsed and set when first needed and a few are kept. Safe to use from the book's background thread and the
 * Swing thread at the same time.
 */
public final class EpubSession implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EpubSession.class);
    private static final int KEEP_TEXTS = 12;
    private static final int KEEP_LAYOUTS = 8;
    private static final int KEEP_PICTURES = 24;

    /** A found place: chapter, offset and length of the match, and the text around it. */
    public record SearchHit(int chapter, int offset, int length, String excerpt) {
    }

    /** What a layout depends on besides the chapter. */
    private record LayoutKey(int chapter, Appearance appearance, int width, int height) {
    }

    private final EpubBook book;
    private final Hyphenators hyphenators;
    private final Map<String, String> sheets = new HashMap<>();
    private final Map<Integer, ChapterText> texts = lru(KEEP_TEXTS);
    private final Map<LayoutKey, ChapterLayout> layouts = lru(KEEP_LAYOUTS);
    private final Map<String, BufferedImage> pictures = Collections.synchronizedMap(lru(KEEP_PICTURES));
    private final Map<String, Optional<Dimension>> sizes = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Font> fonts;
    private int[] lengths;

    public EpubSession(EpubBook book, Hyphenators hyphenators) {
        this.book = book;
        this.hyphenators = hyphenators;
    }

    public EpubBook book() {
        return book;
    }

    public int chapterCount() {
        return book.spine().size();
    }

    // ---------------------------------------------------------------- chapters

    // --8<-- [start:text]
    /** The chapter as blocks; a chapter that cannot be read is an empty one, so the book stays readable. */
    public synchronized ChapterText text(int chapter) {
        ChapterText cached = texts.get(chapter);
        if (cached != null) return cached;
        String path = book.spine().get(chapter);
        ChapterText text;
        try {
            Document doc = Jsoup.parse(book.chapter(chapter));
            Css css = new Css();
            for (Element link : doc.select("link[rel~=(?i)stylesheet][href]")) {
                String sheetPath = ZipPaths.resolve(path, link.attr("href"));
                css.add(sheet(sheetPath), url -> ZipPaths.resolve(sheetPath, url));
            }
            for (Element style : doc.select("style")) css.add(style.data(), url -> ZipPaths.resolve(path, url));
            text = ChapterParser.parse(doc, path, css, book.info().language());
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read chapter {} of {}: {}", path, book.file(), e.toString());
            text = new ChapterText(path, List.of(), "", Map.of(), Map.of());
        }
        texts.put(chapter, text);
        return text;
    }
    // --8<-- [end:text]

    private String sheet(String path) throws IOException {
        String cached = sheets.get(path);
        if (cached != null) return cached;
        String text = book.textResource(path).orElse("");
        sheets.put(path, text);
        return text;
    }

    /** The chapter set into pages of the given text area; the last few layouts are kept. */
    public synchronized ChapterLayout layout(int chapter, Appearance appearance, int width, int height) {
        LayoutKey key = new LayoutKey(chapter, appearance, width, height);
        ChapterLayout cached = layouts.get(key);
        if (cached != null) return cached;
        long start = System.nanoTime();
        ChapterLayout layout = new Typesetter(appearance, hyphenators, this::pictureSize,
                appearance.publisherFont() ? fonts() : Map.of()).layout(text(chapter), width, height);
        LOG.debug("Chapter {} set in {} pages in {} ms", chapter, layout.pageCount(), (System.nanoTime() - start) / 1_000_000);
        layouts.put(key, layout);
        return layout;
    }

    /** Whether the layout is ready, so the window can show it without waiting. */
    public synchronized Optional<ChapterLayout> cachedLayout(int chapter, Appearance appearance, int width, int height) {
        return Optional.ofNullable(layouts.get(new LayoutKey(chapter, appearance, width, height)));
    }

    // ---------------------------------------------------------------- progress

    /** The characters of every chapter; reads the whole book the first time. */
    public synchronized int[] lengths() {
        if (lengths == null) {
            int[] result = new int[chapterCount()];
            for (int i = 0; i < result.length; i++) result[i] = Math.max(1, text(i).length());
            lengths = result;
        }
        return lengths.clone();
    }

    /** True when {@link #lengths()} will answer at once. */
    public synchronized boolean lengthsKnown() {
        return lengths != null;
    }

    // --8<-- [start:progress]
    /** How far into the book the position is, from 0 to 1. */
    public double progress(Position position) {
        int[] l = lengths();
        long total = 0;
        long before = 0;
        for (int i = 0; i < l.length; i++) {
            if (i < position.chapter()) before += l[i];
            total += l[i];
        }
        int chapter = Math.min(position.chapter(), l.length - 1);
        before += Math.min(position.offset(), l[chapter]);
        return total == 0 ? 0 : Math.min(1, (double) before / total);
    }
    // --8<-- [end:progress]

    /** The position at a fraction of the book, for the progress bar. */
    public Position at(double fraction) {
        int[] l = lengths();
        long total = 0;
        for (int length : l) total += length;
        long target = Math.round(Math.clamp(fraction, 0, 1) * total);
        for (int i = 0; i < l.length; i++) {
            if (target < l[i] || i == l.length - 1) return new Position(i, (int) Math.min(target, l[i] - 1));
            target -= l[i];
        }
        return Position.START;
    }

    /** Where each chapter starts, as a fraction of the book, for the marks on the progress bar. */
    public List<Double> chapterStarts() {
        int[] l = lengths();
        long total = 0;
        for (int length : l) total += length;
        List<Double> starts = new ArrayList<>();
        long sum = 0;
        for (int length : l) {
            starts.add(total == 0 ? 0 : (double) sum / total);
            sum += length;
        }
        return starts;
    }

    // ---------------------------------------------------------------- links

    // --8<-- [start:resolve]
    /**
     * Where a link inside the book leads ({@code OEBPS/ch2.xhtml#note3}, as the parser stores links); empty for
     * links to files outside the reading order.
     */
    public Optional<Position> resolve(String link) {
        int hash = link.indexOf('#');
        String path = hash >= 0 ? link.substring(0, hash) : link;
        String fragment = hash >= 0 ? link.substring(hash + 1) : "";
        int chapter = book.chapterOf(path);
        if (chapter < 0) return Optional.empty();
        if (fragment.isEmpty()) return Optional.of(new Position(chapter, 0));
        return Optional.of(new Position(chapter, text(chapter).anchor(fragment).orElse(0)));
    }
    // --8<-- [end:resolve]

    /** The position of a table of contents entry. */
    public Optional<Position> resolve(TocEntry entry) {
        if (entry.chapter() < 0) return Optional.empty();
        return Optional.of(new Position(entry.chapter(),
                entry.fragment().isEmpty() ? 0 : text(entry.chapter()).anchor(entry.fragment()).orElse(0)));
    }

    /** The table of contents entry the position belongs to: the last one at or before it. */
    public Optional<TocEntry> tocEntry(Position position) {
        TocEntry found = null;
        for (TocEntry entry : book.toc()) {
            Optional<Position> p = resolve(entry);
            if (p.isPresent() && p.get().compareTo(position) <= 0) found = entry;
        }
        return Optional.ofNullable(found);
    }

    /**
     * The text of the footnote a note link points to: a hidden {@code aside}, or the paragraph at the anchor
     * (endnotes in a chapter of their own); empty when there is none.
     */
    public Optional<String> footnote(String link) {
        int hash = link.indexOf('#');
        if (hash < 0) return Optional.empty();
        String fragment = link.substring(hash + 1);
        int chapter = book.chapterOf(link.substring(0, hash));
        if (chapter < 0) return Optional.empty();
        ChapterText text = text(chapter);
        String hidden = text.footnotes().get(fragment);
        if (hidden != null) return Optional.of(hidden);
        return text.anchor(fragment).flatMap(text::paragraphAt).map(p -> p.text().strip());
    }

    // ---------------------------------------------------------------- pictures and fonts

    /** A picture of the book, decoded; empty when it is missing or not a picture. */
    public Optional<BufferedImage> picture(String path) {
        BufferedImage cached = pictures.get(path);
        if (cached != null) return Optional.of(cached);
        try {
            Optional<byte[]> bytes = book.resource(path);
            if (bytes.isEmpty()) return Optional.empty();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes.get()));
            if (image == null) return Optional.empty();
            pictures.put(path, image);
            return Optional.of(image);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The size of a picture from its header, without decoding it. */
    public Optional<Dimension> pictureSize(String path) {
        return sizes.computeIfAbsent(path, p -> {
            try {
                Optional<byte[]> bytes = book.resource(p);
                if (bytes.isEmpty()) return Optional.empty();
                try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes.get()))) {
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
        });
    }

    /** The cover picture, if the book has one. */
    public Optional<BufferedImage> cover() {
        return book.cover().flatMap(this::picture);
    }

    // --8<-- [start:fonts]
    /** The fonts of the book's {@code @font-face} rules that Java can read, by family (and |b, |i for styles). */
    synchronized Map<String, Font> fonts() {
        if (fonts != null) return fonts;
        Map<String, Font> loaded = new HashMap<>();
        for (String sheetPath : book.resources("text/css")) {
            try {
                Css css = new Css().add(sheet(sheetPath), url -> ZipPaths.resolve(sheetPath, url));
                for (Css.FontFace face : css.fontFaces()) {
                    Optional<byte[]> bytes = book.resource(face.src());
                    if (bytes.isEmpty()) continue;
                    try {
                        Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes.get()));
                        String key = face.family() + (face.bold() ? "|b" : "") + (face.italic() ? "|i" : "");
                        loaded.putIfAbsent(key, font);
                        loaded.putIfAbsent(face.family(), font);
                    } catch (FontFormatException e) {
                        // WOFF and obfuscated fonts cannot be read; the reader's font stands in
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        fonts = Map.copyOf(loaded);
        return fonts;
    }
    // --8<-- [end:fonts]

    /** True when the book brings fonts the publisher's font choice can use. */
    public boolean hasFonts() {
        return !fonts().isEmpty();
    }

    // ---------------------------------------------------------------- search

    // --8<-- [start:search]
    /**
     * Every place of the text in the book, ignoring case and accents ("Bucher" finds "Bücher"); at most
     * {@code limit} hits.
     */
    public List<SearchHit> search(String query, int limit) {
        String needle = fold(query.strip());
        List<SearchHit> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;
        for (int chapter = 0; chapter < chapterCount() && hits.size() < limit; chapter++) {
            ChapterText text = text(chapter);
            String haystack = fold(text.plain());
            for (int at = haystack.indexOf(needle); at >= 0 && hits.size() < limit; at = haystack.indexOf(needle, at + 1)) {
                hits.add(new SearchHit(chapter, at, needle.length(), text.excerpt(Math.max(0, at - 30), 90)));
            }
        }
        return hits;
    }

    /**
     * Lower case without accents, one character for each character so that offsets stay valid.
     */
    static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char b = base.isEmpty() ? c : base.charAt(0);
            String lower = String.valueOf(b).toLowerCase(Locale.ROOT);
            sb.append(lower.length() == 1 ? lower.charAt(0) : b);
        }
        return sb.toString();
    }
    // --8<-- [end:search]

    @Override
    public void close() throws IOException {
        book.close();
    }

    private static <K, V> Map<K, V> lru(int size) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > size;
            }
        };
    }
}
