package eu.amadeos.apps.books.domain.epub;

import eu.amadeos.apps.books.domain.BookFormat;
import eu.amadeos.apps.books.domain.BookInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An EPUB 2 or 3 book: the ZIP container, the package document (metadata, manifest, reading order) and the
 * table of contents from the EPUB 3 navigation document or the EPUB 2 NCX. Chapters, style sheets and pictures
 * are read from the ZIP when asked for. Not thread-safe; the book's one reader thread uses it.
 */
public final class EpubBook implements AutoCloseable {
    private static final Pattern XML_ENCODING = Pattern.compile("^<\\?xml[^>]*encoding=[\"']([A-Za-z0-9_.-]+)[\"']");

    private final Path file;
    private final ZipFile zip;
    private final BookInfo info;
    private final List<String> spine;
    private final List<TocEntry> toc;
    private final Optional<String> cover;
    private final Map<String, String> mediaTypes;

    private EpubBook(Path file, ZipFile zip, BookInfo info, List<String> spine, List<TocEntry> toc,
                     Optional<String> cover, Map<String, String> mediaTypes) {
        this.file = file;
        this.zip = zip;
        this.info = info;
        this.spine = List.copyOf(spine);
        this.toc = List.copyOf(toc);
        this.cover = cover;
        this.mediaTypes = Map.copyOf(mediaTypes);
    }

    // --8<-- [start:open]
    /** Reads the container, the package document and the table of contents. */
    public static EpubBook open(Path file) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(file.toFile());
        } catch (IOException e) {
            throw new EpubException("Not a ZIP archive: " + file.getFileName(), e);
        }
        try {
            String opfPath = packagePath(zip);
            Document opf = Jsoup.parse(text(zip, opfPath), "", Parser.xmlParser());
            Element metadata = first(opf, "metadata");
            Map<String, Element> manifest = new LinkedHashMap<>();
            Map<String, String> mediaTypes = new HashMap<>();
            for (Element item : byName(opf, "item")) {
                manifest.put(item.attr("id"), item);
                mediaTypes.put(ZipPaths.resolve(opfPath, item.attr("href")), item.attr("media-type"));
            }
            List<String> spine = new ArrayList<>();
            Element spineElement = first(opf, "spine");
            if (spineElement != null) {
                for (Element ref : byName(spineElement, "itemref")) {
                    Element item = manifest.get(ref.attr("idref"));
                    if (item != null && entry(zip, ZipPaths.resolve(opfPath, item.attr("href"))) != null) {
                        spine.add(ZipPaths.resolve(opfPath, item.attr("href")));
                    }
                }
            }
            if (spine.isEmpty()) throw new EpubException("The book has no reading order: " + file.getFileName());
            BookInfo info = info(file, metadata);
            List<TocEntry> toc = toc(zip, opfPath, manifest, spineElement, spine);
            Optional<String> cover = cover(opfPath, metadata, manifest).filter(path -> entry(zip, path) != null);
            return new EpubBook(file, zip, info, spine, toc, cover, mediaTypes);
        } catch (IOException | RuntimeException e) {
            zip.close();
            if (e instanceof EpubException epub) throw epub;
            throw new EpubException("Cannot read the book " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
    // --8<-- [end:open]

    /** {@code META-INF/container.xml} names the package document; without it the first {@code .opf} file. */
    private static String packagePath(ZipFile zip) throws IOException {
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container != null) {
            Document doc = Jsoup.parse(text(zip, container.getName()), "", Parser.xmlParser());
            for (Element rootfile : byName(doc, "rootfile")) {
                String path = rootfile.attr("full-path");
                if (!path.isEmpty() && zip.getEntry(path) != null) return path;
            }
        }
        return zip.stream().map(ZipEntry::getName).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".opf")).findFirst()
                .orElseThrow(() -> new EpubException("No package document in the book"));
    }

    private static BookInfo info(Path file, Element metadata) {
        if (metadata == null) {
            return new BookInfo(file, BookFormat.EPUB, null, List.of(), "", 0, List.of(), "");
        }
        String title = byName(metadata, "title").stream().map(Element::text).findFirst().orElse(null);
        List<String> authors = new ArrayList<>();
        List<String> contributors = byName(metadata, "creator").stream().map(Element::text)
                .filter(s -> !s.isBlank()).toList();
        authors.addAll(contributors);
        List<String> genres = new ArrayList<>();
        for (Element subject : byName(metadata, "subject")) {
            // Subjects are often "Fiction -- Science Fiction"; the shelf shows each part once.
            for (String part : subject.text().split("\\s*(--|/|;)\\s*")) {
                String genre = part.strip();
                if (!genre.isEmpty() && genres.stream().noneMatch(g -> g.equalsIgnoreCase(genre))) genres.add(genre);
            }
        }
        String language = byName(metadata, "language").stream().map(Element::text).findFirst().orElse("");
        String series = "";
        double index = 0;
        for (Element meta : byName(metadata, "meta")) {
            String name = meta.attr("name");
            if (name.equals("calibre:series")) series = meta.attr("content");
            if (name.equals("calibre:series_index")) index = number(meta.attr("content"));
            if (meta.attr("property").equals("belongs-to-collection") && series.isEmpty()) {
                series = meta.text();
                String id = meta.attr("id");
                for (Element refine : byName(metadata, "meta")) {
                    if (refine.attr("refines").equals("#" + id) && refine.attr("property").equals("group-position")) {
                        index = number(refine.text());
                    }
                }
            }
        }
        return new BookInfo(file, BookFormat.EPUB, title, authors, series, index, genres, language);
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** EPUB 3 marks the cover picture in the manifest, EPUB 2 names it in a {@code meta}. */
    private static Optional<String> cover(String opfPath, Element metadata, Map<String, Element> manifest) {
        for (Element item : manifest.values()) {
            if (item.attr("properties").contains("cover-image")) return Optional.of(ZipPaths.resolve(opfPath, item.attr("href")));
        }
        if (metadata != null) {
            for (Element meta : byName(metadata, "meta")) {
                if (meta.attr("name").equals("cover")) {
                    Element item = manifest.get(meta.attr("content"));
                    if (item != null && item.attr("media-type").startsWith("image/")) {
                        return Optional.of(ZipPaths.resolve(opfPath, item.attr("href")));
                    }
                }
            }
        }
        for (Element item : manifest.values()) {
            if (item.attr("media-type").startsWith("image/")
                    && (item.attr("id") + item.attr("href")).toLowerCase(Locale.ROOT).contains("cover")) {
                return Optional.of(ZipPaths.resolve(opfPath, item.attr("href")));
            }
        }
        return Optional.empty();
    }

    // --8<-- [start:toc]
    /** The EPUB 3 navigation document's {@code toc} nav, else the EPUB 2 NCX, else one entry per chapter file. */
    private static List<TocEntry> toc(ZipFile zip, String opfPath, Map<String, Element> manifest, Element spineElement,
                                      List<String> spine) throws IOException {
        List<TocEntry> entries = new ArrayList<>();
        for (Element item : manifest.values()) {
            if (!item.attr("properties").contains("nav")) continue;
            String navPath = ZipPaths.resolve(opfPath, item.attr("href"));
            if (entry(zip, navPath) == null) continue;
            Document nav = Jsoup.parse(text(zip, navPath), "", Parser.xmlParser());
            Element tocNav = null;
            for (Element n : byName(nav, "nav")) {
                if (n.attr("epub:type").contains("toc") || n.attr("role").equals("doc-toc")) tocNav = n;
            }
            if (tocNav == null) tocNav = byName(nav, "nav").stream().findFirst().orElse(null);
            Element list = tocNav == null ? null : byName(tocNav, "ol").stream().findFirst().orElse(null);
            if (list != null) navList(list, 0, navPath, spine, entries);
            if (!entries.isEmpty()) return entries;
        }
        String ncxId = spineElement == null ? "" : spineElement.attr("toc");
        Element ncxItem = manifest.get(ncxId);
        if (ncxItem == null) {
            ncxItem = manifest.values().stream().filter(i -> i.attr("media-type").equals("application/x-dtbncx+xml"))
                    .findFirst().orElse(null);
        }
        if (ncxItem != null) {
            String ncxPath = ZipPaths.resolve(opfPath, ncxItem.attr("href"));
            if (entry(zip, ncxPath) != null) {
                Document ncx = Jsoup.parse(text(zip, ncxPath), "", Parser.xmlParser());
                Element navMap = first(ncx, "navMap");
                if (navMap != null) ncxPoints(navMap, 0, ncxPath, spine, entries);
            }
        }
        if (entries.isEmpty()) {
            for (int i = 0; i < spine.size(); i++) {
                entries.add(new TocEntry(BookInfo.fallbackTitle(Path.of(spine.get(i))), i, "", 0));
            }
        }
        return entries;
    }
    // --8<-- [end:toc]

    private static void navList(Element ol, int depth, String navPath, List<String> spine, List<TocEntry> out) {
        for (Element li : ol.children()) {
            if (!li.tagName().equals("li")) continue;
            Element label = null;
            Element sub = null;
            for (Element child : li.children()) {
                if (label == null && (child.tagName().equals("a") || child.tagName().equals("span"))) label = child;
                if (child.tagName().equals("ol")) sub = child;
            }
            if (label != null) {
                String href = label.attr("href");
                String target = href.isEmpty() ? "" : ZipPaths.resolve(navPath, href);
                out.add(new TocEntry(label.text(), href.isEmpty() ? -1 : spine.indexOf(target), ZipPaths.fragment(href), depth));
            }
            if (sub != null) navList(sub, depth + 1, navPath, spine, out);
        }
    }

    private static void ncxPoints(Element parent, int depth, String ncxPath, List<String> spine, List<TocEntry> out) {
        for (Element point : parent.children()) {
            if (!point.tagName().endsWith("navPoint")) continue;
            Element text = first(point, "text");
            Element content = first(point, "content");
            String src = content == null ? "" : content.attr("src");
            out.add(new TocEntry(text == null ? "" : text.text(),
                    src.isEmpty() ? -1 : spine.indexOf(ZipPaths.resolve(ncxPath, src)), ZipPaths.fragment(src), depth));
            ncxPoints(point, depth + 1, ncxPath, spine, out);
        }
    }

    // ---------------------------------------------------------------- reading

    public Path file() {
        return file;
    }

    public BookInfo info() {
        return info;
    }

    /** The chapter files in reading order, as paths inside the ZIP. */
    public List<String> spine() {
        return spine;
    }

    public List<TocEntry> toc() {
        return toc;
    }

    /** The path of the cover picture inside the ZIP, if the book names one. */
    public Optional<String> cover() {
        return cover;
    }

    /** The media type the manifest gives for the file, empty when it is not listed. */
    public String mediaType(String path) {
        return mediaTypes.getOrDefault(path, "");
    }

    /** The files of the manifest whose media type starts with the prefix, e.g. {@code text/css}. */
    public List<String> resources(String mediaTypePrefix) {
        return mediaTypes.entrySet().stream().filter(e -> e.getValue().startsWith(mediaTypePrefix))
                .map(Map.Entry::getKey).sorted().toList();
    }

    /** The XHTML of a chapter. */
    public String chapter(int index) throws IOException {
        return text(zip, spine.get(index));
    }

    /** The index in the reading order of a file inside the book, -1 if it is not a chapter. */
    public int chapterOf(String path) {
        return spine.indexOf(path);
    }

    /** A file of the book (picture, style sheet, font); empty when it is missing. */
    public Optional<byte[]> resource(String path) throws IOException {
        ZipEntry entry = entry(zip, path);
        if (entry == null) return Optional.empty();
        try (InputStream in = zip.getInputStream(entry)) {
            return Optional.of(in.readAllBytes());
        }
    }

    /** A text file of the book in the encoding its XML declaration names, else UTF-8. */
    public Optional<String> textResource(String path) throws IOException {
        Optional<byte[]> bytes = resource(path);
        return bytes.map(EpubBook::decode);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    // ---------------------------------------------------------------- helpers

    private static ZipEntry entry(ZipFile zip, String path) {
        ZipEntry entry = zip.getEntry(path);
        if (entry != null) return entry;
        // Some books link with different case than the file has.
        return zip.stream().filter(e -> e.getName().equalsIgnoreCase(path)).findFirst().orElse(null);
    }

    private static String text(ZipFile zip, String path) throws IOException {
        ZipEntry entry = entry(zip, path);
        if (entry == null) throw new EpubException("Missing in the book: " + path);
        try (InputStream in = zip.getInputStream(entry)) {
            return decode(in.readAllBytes());
        }
    }

    static String decode(byte[] bytes) {
        int start = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            start = 3;
        }
        String head = new String(bytes, start, Math.min(200, bytes.length - start), StandardCharsets.ISO_8859_1);
        Matcher m = XML_ENCODING.matcher(head);
        Charset charset = StandardCharsets.UTF_8;
        if (m.find()) {
            try {
                charset = Charset.forName(m.group(1));
            } catch (IllegalArgumentException e) {
                // unknown encoding: stay with UTF-8
            }
        }
        return new String(bytes, start, bytes.length - start, charset);
    }

    /** Elements by local name, whatever namespace prefix the book uses ({@code dc:title}, {@code opf:item}). */
    static List<Element> byName(Element root, String localName) {
        List<Element> found = new ArrayList<>();
        for (Element e : root.getAllElements()) {
            String tag = e.tagName();
            int colon = tag.indexOf(':');
            if ((colon >= 0 ? tag.substring(colon + 1) : tag).equals(localName)) found.add(e);
        }
        return found;
    }

    private static Element first(Element root, String localName) {
        List<Element> all = byName(root, localName);
        return all.isEmpty() ? null : all.getFirst();
    }
}
