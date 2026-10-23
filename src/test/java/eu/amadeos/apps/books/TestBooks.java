package eu.amadeos.apps.books;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small EPUB and CBZ files for the tests, written into a temporary folder. */
public final class TestBooks {
    private TestBooks() {
    }

    public static final String CSS = """
            p { text-indent: 1.5em; margin: 0; text-align: justify }
            p.first { text-indent: 0 }
            h1 { text-align: center; font-size: 1.4em; margin: 2em 0 0.3em }
            .right { float: right; width: 40% }
            """;

    /** Builder for an EPUB 3 book. */
    public static final class Epub {
        private String title = "Alice’s Adventures in Wonderland";
        private final List<String> creators = new ArrayList<>(List.of("Lewis Carroll"));
        private final List<String> subjects = new ArrayList<>();
        private String language = "en";
        private String series = "";
        private String seriesIndex = "";
        private final Map<String, String> chapters = new LinkedHashMap<>();
        private final Map<String, byte[]> files = new LinkedHashMap<>();
        private String nav;
        private String ncx;
        private String cover;

        public Epub title(String t) {
            title = t;
            return this;
        }

        public Epub creators(String... names) {
            creators.clear();
            creators.addAll(List.of(names));
            return this;
        }

        public Epub subject(String s) {
            subjects.add(s);
            return this;
        }

        public Epub language(String l) {
            language = l;
            return this;
        }

        public Epub series(String name, String index) {
            series = name;
            seriesIndex = index;
            return this;
        }

        /** A chapter file below {@code OEBPS/} with the body's inner XHTML. */
        public Epub chapter(String name, String body) {
            chapters.put(name, xhtml(name, body));
            return this;
        }

        /** A chapter file with complete XHTML. */
        public Epub rawChapter(String name, String xhtml) {
            chapters.put(name, xhtml);
            return this;
        }

        public Epub file(String name, byte[] content) {
            files.put(name, content);
            return this;
        }

        public Epub nav(String listItems) {
            nav = listItems;
            return this;
        }

        /** An EPUB 2 table of contents instead of the navigation document. */
        public Epub ncx(String navPoints) {
            ncx = navPoints;
            return this;
        }

        public Epub cover(String imageName) {
            cover = imageName;
            return this;
        }

        public Path write(Path file) throws IOException {
            if (!files.containsKey("style.css")) files.put("style.css", CSS.getBytes(StandardCharsets.UTF_8));
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
                stored(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
                put(zip, "META-INF/container.xml", """
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                        <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                        </container>
                        """);
                StringBuilder manifest = new StringBuilder();
                StringBuilder spine = new StringBuilder();
                int n = 0;
                for (String name : chapters.keySet()) {
                    n++;
                    manifest.append("<item id=\"c").append(n).append("\" href=\"").append(name)
                            .append("\" media-type=\"application/xhtml+xml\"/>\n");
                    spine.append("<itemref idref=\"c").append(n).append("\"/>\n");
                }
                int f = 0;
                for (String name : files.keySet()) {
                    f++;
                    String type = name.endsWith(".css") ? "text/css" : name.endsWith(".png") ? "image/png"
                            : name.endsWith(".jpg") ? "image/jpeg" : name.endsWith(".ttf") ? "font/ttf" : "application/octet-stream";
                    String props = name.equals(cover) ? " properties=\"cover-image\"" : "";
                    manifest.append("<item id=\"f").append(f).append("\" href=\"").append(name).append("\" media-type=\"")
                            .append(type).append("\"").append(props).append("/>\n");
                }
                if (nav != null) {
                    manifest.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
                    put(zip, "OEBPS/nav.xhtml", xhtml("nav.xhtml",
                            "<nav epub:type=\"toc\"><h1>Contents</h1><ol>" + nav + "</ol></nav>"));
                }
                String spineAttr = "";
                if (ncx != null) {
                    manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n");
                    spineAttr = " toc=\"ncx\"";
                    put(zip, "OEBPS/toc.ncx", """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
                            """ + ncx + "</navMap></ncx>");
                }
                StringBuilder meta = new StringBuilder();
                meta.append("<dc:title>").append(title).append("</dc:title>\n");
                for (String c : creators) meta.append("<dc:creator>").append(c).append("</dc:creator>\n");
                for (String s : subjects) meta.append("<dc:subject>").append(s).append("</dc:subject>\n");
                meta.append("<dc:language>").append(language).append("</dc:language>\n");
                if (!series.isEmpty()) {
                    meta.append("<meta name=\"calibre:series\" content=\"").append(series).append("\"/>\n");
                    meta.append("<meta name=\"calibre:series_index\" content=\"").append(seriesIndex).append("\"/>\n");
                }
                put(zip, "OEBPS/content.opf", """
                        <?xml version="1.0" encoding="utf-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:identifier id="uid">urn:uuid:test</dc:identifier>
                        """ + meta + """
                        </metadata>
                        <manifest>
                        """ + manifest + "</manifest>\n<spine" + spineAttr + ">\n" + spine + "</spine>\n</package>\n");
                for (Map.Entry<String, String> c : chapters.entrySet()) put(zip, "OEBPS/" + c.getKey(), c.getValue());
                for (Map.Entry<String, byte[]> e : files.entrySet()) put(zip, "OEBPS/" + e.getKey(), e.getValue());
            }
            return file;
        }
    }

    public static Epub epub() {
        return new Epub();
    }

    public static String xhtml(String title, String body) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en" lang="en">
                <head><title>%s</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, body);
    }

    /** A comic archive with pictures of the given sizes; names as given, ComicInfo.xml when not null. */
    public static Path cbz(Path file, Map<String, int[]> pages, String comicInfo) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, int[]> page : pages.entrySet()) {
                put(zip, page.getKey(), png(page.getValue()[0], page.getValue()[1], Color.getHSBColor(page.getKey().length() / 10f, 0.5f, 0.9f)));
            }
            if (comicInfo != null) put(zip, "ComicInfo.xml", comicInfo);
        }
        return file;
    }

    public static byte[] png(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** About this many words of running text. */
    public static String words(int count) {
        String[] pool = ("Alice was beginning to get very tired of sitting by her sister on the bank and of having "
                + "nothing to do once or twice she had peeped into the book her sister was reading but it had no "
                + "pictures or conversations in it and what is the use of a book thought Alice without pictures").split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(' ');
            sb.append(pool[i % pool.length]);
        }
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        put(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void stored(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        OutputStream out = zip;
        out.write(bytes);
        zip.closeEntry();
    }
}
