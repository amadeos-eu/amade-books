package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.ReadTheme;
import eu.amadeos.apps.books.domain.epub.EpubSession;
import eu.amadeos.apps.books.domain.layout.Block;
import eu.amadeos.apps.books.domain.layout.ChapterLayout;
import eu.amadeos.apps.books.domain.layout.ChapterText;
import eu.amadeos.apps.books.domain.layout.Page;
import eu.amadeos.apps.books.domain.layout.PageGeometry;
import eu.amadeos.apps.books.domain.layout.StyledRun;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The open book: one or two pages side by side with the running head above and the page number below. A click
 * on a link follows it, a click in the outer thirds turns the page, dragging selects text. The mouse wheel turns
 * pages too.
 */
final class PageView extends JComponent {
    /** What the view asks of the presenter. */
    interface Callbacks {
        /** A click on the text; true when there was a link to follow. */
        boolean clicked(int chapter, int offset);

        void turn(boolean forward);

        /** The link under the mouse, or null when it left the link. */
        void hover(int chapter, int offset);
    }

    private final Callbacks callbacks;
    private EpubSession session;
    private ChapterLayout layout;
    private PageGeometry geometry;
    private int chapter;
    private int firstPage;
    private String[] heads = {"", ""};
    private ReadTheme theme = ReadTheme.PAPER;
    private SearchState search = SearchState.NONE;
    private String emptyText = "";
    private int anchor = -1;
    private int selFrom = -1;
    private int selTo = -1;
    private boolean dragged;
    private boolean overLink;

    PageView(Callbacks callbacks) {
        this.callbacks = callbacks;
        setName("pages");
        setFocusable(true);
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (e.getButton() != MouseEvent.BUTTON1) return;
                dragged = false;
                anchor = offsetAt(e.getX(), e.getY());
                if (selFrom >= 0) {
                    selFrom = selTo = -1;
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (anchor < 0) return;
                int at = offsetAt(e.getX(), e.getY());
                if (at < 0) return;
                dragged = true;
                selFrom = Math.min(anchor, at);
                selTo = Math.max(anchor, at);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1 || dragged || layout == null) return;
                int at = offsetAt(e.getX(), e.getY());
                if (at >= 0 && overText(e.getX(), e.getY()) && callbacks.clicked(chapter, at)) return;
                if (e.getX() < getWidth() * 0.3) callbacks.turn(false);
                else if (e.getX() > getWidth() * 0.7) callbacks.turn(true);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (layout == null) return;
                int at = overText(e.getX(), e.getY()) ? offsetAt(e.getX(), e.getY()) : -1;
                boolean link = at >= 0 && session.text(chapter).linkAt(at).isPresent();
                if (link || overLink) callbacks.hover(chapter, link ? at : -1);
                overLink = link;
                setCursor(Cursor.getPredefinedCursor(link ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (layout != null && e.getWheelRotation() != 0) callbacks.turn(e.getWheelRotation() > 0);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setEmptyText(String text) {
        emptyText = text;
    }

    void showPages(EpubSession newSession, ChapterLayout newLayout, PageGeometry newGeometry, int newChapter,
                   int newFirstPage, String[] newHeads, ReadTheme newTheme) {
        if (newSession != session || newChapter != chapter) selFrom = selTo = -1;
        session = newSession;
        layout = newLayout;
        geometry = newGeometry;
        chapter = newChapter;
        firstPage = newFirstPage;
        heads = newHeads;
        theme = newTheme;
        repaint();
    }

    void clear() {
        session = null;
        layout = null;
        selFrom = selTo = -1;
        search = SearchState.NONE;
        repaint();
    }

    void setSearch(SearchState state) {
        search = state;
        repaint();
    }

    void setTheme(ReadTheme newTheme) {
        theme = newTheme;
        repaint();
    }

    ReadTheme theme() {
        return theme;
    }

    void clearSelection() {
        selFrom = selTo = -1;
        repaint();
    }

    /** {chapter, from, to} of the selected text. */
    Optional<int[]> selection() {
        if (layout == null || selFrom < 0 || selTo <= selFrom) return Optional.empty();
        return Optional.of(new int[]{chapter, selFrom, selTo});
    }

    void select(int from, int to) {
        selFrom = from;
        selTo = to;
        repaint();
    }

    int firstPage() {
        return firstPage;
    }

    /** The pages shown, left to right. */
    List<Page> visiblePages() {
        List<Page> shown = new ArrayList<>();
        if (layout == null) return shown;
        for (int c = 0; c < geometry.columns() && firstPage + c < layout.pageCount(); c++) {
            shown.add(layout.pages().get(firstPage + c));
        }
        return shown;
    }

    // ---------------------------------------------------------------- finding text under the mouse

    private int column(int x) {
        if (geometry == null) return -1;
        int c = (x - geometry.left()) / Math.max(1, geometry.pageWidth());
        return x < geometry.left() || c >= geometry.columns() || firstPage + c >= layout.pageCount() ? -1 : c;
    }

    private boolean overText(int x, int y) {
        int c = column(x);
        if (c < 0) return false;
        float px = x - geometry.textX(c);
        float py = y - geometry.textTop();
        for (Page.Line line : layout.pages().get(firstPage + c).lines()) {
            if (py >= line.top() && py <= line.bottom() && px >= line.x()
                    && px <= line.x() + line.layout().getAdvance()) return true;
        }
        return false;
    }

    /** The chapter offset nearest to the point: of the line at its height, or the line closest above or below. */
    int offsetAt(int x, int y) {
        if (layout == null) return -1;
        int c = column(x);
        if (c < 0) return -1;
        Page page = layout.pages().get(firstPage + c);
        float px = x - geometry.textX(c);
        float py = y - geometry.textTop();
        Page.Line best = null;
        float distance = Float.MAX_VALUE;
        for (Page.Line line : page.lines()) {
            float d = py < line.top() ? line.top() - py : py > line.bottom() ? py - line.bottom() : 0;
            if (d < distance) {
                distance = d;
                best = line;
            }
        }
        return best == null ? page.start() : best.offsetAt(px);
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setColor(theme.background());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (layout == null) {
                paintEmpty(g);
                return;
            }
            ChapterText text = session.text(chapter);
            List<int[]> links = links(text);
            List<Page> shown = visiblePages();
            for (int c = 0; c < shown.size(); c++) paintPage(g, text, links, shown.get(c), c, shown.size());
            if (shown.size() == 2) {
                // the fold between the pages
                int x = geometry.left() + geometry.pageWidth();
                g.setPaint(new java.awt.GradientPaint(x - 18, 0, new Color(0, 0, 0, 0), x, 0,
                        new Color(0, 0, 0, theme.dark() ? 70 : 28)));
                g.fillRect(x - 18, 0, 18, getHeight());
                g.setPaint(new java.awt.GradientPaint(x, 0, new Color(0, 0, 0, theme.dark() ? 70 : 28), x + 18, 0,
                        new Color(0, 0, 0, 0)));
                g.fillRect(x, 0, 18, getHeight());
            }
        } finally {
            g.dispose();
        }
    }

    private void paintEmpty(Graphics2D g) {
        if (emptyText.isEmpty()) return;
        g.setColor(theme.dim());
        g.setFont(getFont().deriveFont(Font.ITALIC, getFont().getSize2D() * 1.2f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(emptyText, (getWidth() - fm.stringWidth(emptyText)) / 2, getHeight() / 2);
    }

    // --8<-- [start:paint]
    private void paintPage(Graphics2D g, ChapterText text, List<int[]> links, Page page, int column, int columns) {
        int tx = geometry.textX(column);
        int ty = geometry.textTop();
        // running head and page number in the bands above and below the text
        Font small = new Font(Font.SERIF, Font.ITALIC, Math.max(10, Math.round(geometry.textTop() / 2.6f * 0.7f)));
        g.setFont(small);
        g.setColor(theme.dim());
        FontMetrics fm = g.getFontMetrics();
        String head = CoverGrid.ellipsis(fm, columns == 2 && column == 0 ? heads[0] : heads[1], geometry.textWidth());
        if (firstPage + column > 0) {
            g.drawString(head, tx + (geometry.textWidth() - fm.stringWidth(head)) / 2f, ty * 0.55f);
        }
        String number = String.valueOf(firstPage + column + 1);
        g.drawString(number, tx + (geometry.textWidth() - fm.stringWidth(number)) / 2f,
                ty + geometry.textHeight() + (getHeight() - ty - geometry.textHeight()) * 0.6f);

        Graphics2D t = (Graphics2D) g.create();
        t.translate(tx, ty);
        for (Page.Picture p : page.pictures()) paintPicture(t, p);
        t.setStroke(new BasicStroke(1f));
        t.setColor(theme.dim());
        for (Page.Rule r : page.rules()) t.draw(new Line2D.Float(r.x1(), r.y(), r.x2(), r.y()));
        paintMarks(t, page);
        for (Page.Line line : page.lines()) {
            t.setColor(theme.ink());
            line.layout().draw(t, line.x(), line.baseline());
            for (int[] link : links) {
                Shape area = line.highlight(link[0], link[1]);
                if (area == null) continue;
                // the link once more in the link colour, clipped to its characters, with a thin underline
                Graphics2D l = (Graphics2D) t.create();
                l.clip(area);
                l.setColor(theme.link());
                line.layout().draw(l, line.x(), line.baseline());
                l.dispose();
                Rectangle2D b = area.getBounds2D();
                t.setColor(new Color(theme.link().getRed(), theme.link().getGreen(), theme.link().getBlue(), 110));
                t.draw(new Line2D.Double(b.getMinX(), line.baseline() + 2, b.getMaxX(), line.baseline() + 2));
            }
        }
        t.dispose();
    }
    // --8<-- [end:paint]

    /** The search hits and the selection behind the text. */
    private void paintMarks(Graphics2D t, Page page) {
        Composite before = t.getComposite();
        t.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, theme.dark() ? 0.45f : 0.38f));
        for (int i = 0; i < search.hits().size(); i++) {
            EpubSession.SearchHit hit = search.hits().get(i);
            if (hit.chapter() != chapter || hit.offset() >= page.end() || hit.offset() + hit.length() <= page.start()) continue;
            t.setColor(i == search.current() ? new Color(0xff9a1f) : new Color(0xf2d335));
            for (Page.Line line : page.lines()) {
                Shape area = line.highlight(hit.offset(), hit.offset() + hit.length());
                if (area != null) t.fill(area);
            }
        }
        if (selFrom >= 0 && selTo > selFrom) {
            t.setColor(theme.dark() ? new Color(0x5a7fb0) : new Color(0x9ec0ea));
            for (Page.Line line : page.lines()) {
                Shape area = line.highlight(selFrom, selTo);
                if (area != null) t.fill(area);
            }
        }
        t.setComposite(before);
    }

    private void paintPicture(Graphics2D t, Page.Picture p) {
        Optional<BufferedImage> image = session.picture(p.path());
        Rectangle2D box = new Rectangle2D.Float(p.x(), p.y(), p.width(), p.height());
        if (image.isEmpty()) {
            t.setColor(theme.dim());
            t.draw(box);
            if (!p.alt().isEmpty()) {
                t.setFont(getFont());
                t.drawString(CoverGrid.ellipsis(t.getFontMetrics(), p.alt(), (int) p.width() - 8), p.x() + 4,
                        p.y() + t.getFontMetrics().getAscent() + 4);
            }
            return;
        }
        Composite before = t.getComposite();
        // pictures are dimmed a little at night so they do not glare
        if (theme.dark()) t.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        t.drawImage(image.get(), Math.round(p.x()), Math.round(p.y()), Math.round(p.width()), Math.round(p.height()), null);
        t.setComposite(before);
    }

    /** The chapter offsets {from, to} of the links in the chapter. */
    private static List<int[]> links(ChapterText text) {
        List<int[]> result = new ArrayList<>();
        for (Block block : text.blocks()) {
            if (!(block instanceof Block.Text t)) continue;
            for (StyledRun run : t.runs()) {
                if (run.style().link() != null) result.add(new int[]{t.offset() + run.start(), t.offset() + run.end()});
            }
        }
        return result;
    }
}
