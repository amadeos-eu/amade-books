package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.library.BookState;
import eu.amadeos.apps.books.domain.library.LibraryEntry;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The shelf as covers: a grid that wraps with the window's width, each book with its cover, title, author and
 * how far it is read. Books without a cover picture get a plain cover with their title. Arrow keys move the
 * selection, Enter and a double click open the book, the right mouse button shows the book's menu.
 */
final class CoverGrid extends JComponent implements Scrollable {
    private final Messages msg;
    private final Map<Path, Optional<Image>> covers = new HashMap<>();
    private List<LibraryEntry> books = List.of();
    private Function<LibraryEntry, BookState> states = e -> BookState.NEW;
    private int selected = -1;
    private Consumer<LibraryEntry> onOpen = e -> {
    };
    private BiConsumer<LibraryEntry, MouseEvent> onMenu = (e, m) -> {
    };
    private Consumer<LibraryEntry> onSelect = e -> {
    };
    private String emptyText = "";

    CoverGrid(Messages msg) {
        this.msg = msg;
        setName("covers");
        setFocusable(true);
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int index = indexAt(e.getX(), e.getY());
                if (index >= 0) select(index);
                if (e.isPopupTrigger() && index >= 0) onMenu.accept(books.get(index), e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (e.isPopupTrigger() && index >= 0) onMenu.accept(books.get(index), e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = indexAt(e.getX(), e.getY());
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && index >= 0) onOpen.accept(books.get(index));
            }
        };
        addMouseListener(mouse);
        key(KeyEvent.VK_RIGHT, () -> move(1));
        key(KeyEvent.VK_LEFT, () -> move(-1));
        key(KeyEvent.VK_DOWN, () -> move(columns()));
        key(KeyEvent.VK_UP, () -> move(-columns()));
        key(KeyEvent.VK_HOME, () -> select(0));
        key(KeyEvent.VK_END, () -> select(books.size() - 1));
        key(KeyEvent.VK_ENTER, () -> {
            if (selected >= 0 && selected < books.size()) onOpen.accept(books.get(selected));
        });
    }

    private void key(int code, Runnable action) {
        String name = "grid-" + code;
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(code, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    void onOpen(Consumer<LibraryEntry> action) {
        onOpen = action;
    }

    void onMenu(BiConsumer<LibraryEntry, MouseEvent> action) {
        onMenu = action;
    }

    void onSelect(Consumer<LibraryEntry> action) {
        onSelect = action;
    }

    void setEmptyText(String text) {
        emptyText = text;
    }

    void setBooks(List<LibraryEntry> newBooks, Function<LibraryEntry, BookState> newStates) {
        LibraryEntry was = selectedBook().orElse(null);
        books = List.copyOf(newBooks);
        states = newStates;
        selected = was == null ? (books.isEmpty() ? -1 : 0) : Math.max(0, indexOf(was));
        if (books.isEmpty()) selected = -1;
        // a changed cover file is read again
        covers.keySet().removeIf(p -> books.stream().noneMatch(b -> b.cover().map(p::equals).orElse(false)));
        revalidate();
        repaint();
    }

    private int indexOf(LibraryEntry entry) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).file().equals(entry.file())) return i;
        }
        return -1;
    }

    Optional<LibraryEntry> selectedBook() {
        return selected >= 0 && selected < books.size() ? Optional.of(books.get(selected)) : Optional.empty();
    }

    List<LibraryEntry> books() {
        return books;
    }

    void select(int index) {
        if (books.isEmpty()) return;
        selected = Math.clamp(index, 0, books.size() - 1);
        scrollRectToVisible(cell(selected));
        onSelect.accept(books.get(selected));
        repaint();
    }

    private void move(int delta) {
        select(selected < 0 ? 0 : selected + delta);
    }

    // ---------------------------------------------------------------- geometry

    private int coverWidth() {
        return Math.round(112 * AmadeUI.fontScale());
    }

    private int coverHeight() {
        return Math.round(166 * AmadeUI.fontScale());
    }

    private int cellWidth() {
        return coverWidth() + Math.round(36 * AmadeUI.fontScale());
    }

    private int cellHeight() {
        return coverHeight() + Math.round(84 * AmadeUI.fontScale());
    }

    private int margin() {
        return Math.round(14 * AmadeUI.fontScale());
    }

    int columns() {
        int width = getParent() == null ? getWidth() : getParent().getWidth();
        return Math.max(1, (width - 2 * margin()) / cellWidth());
    }

    Rectangle cell(int index) {
        int columns = columns();
        int width = getParent() == null ? getWidth() : getParent().getWidth();
        int extra = Math.max(0, (width - 2 * margin() - columns * cellWidth()) / columns);
        int w = cellWidth() + extra;
        return new Rectangle(margin() + (index % columns) * w, margin() + (index / columns) * cellHeight(), w, cellHeight());
    }

    int indexAt(int x, int y) {
        for (int i = 0; i < books.size(); i++) {
            if (cell(i).contains(x, y)) return i;
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = (books.size() + columns() - 1) / columns();
        return new Dimension(cellWidth() + 2 * margin(), Math.max(1, rows) * cellHeight() + 2 * margin());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(5 * cellWidth(), 2 * cellHeight());
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return cellHeight() / 4;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(cellHeight(), visible.height - cellHeight() / 2);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        DesktopTheme t = AmadeUI.theme();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(t.sunkenBg());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (books.isEmpty()) {
            g.setColor(t.textInactive());
            g.setFont(getFont().deriveFont(Font.PLAIN));
            int y = margin() * 3;
            for (String line : emptyText.split("\n")) {
                g.drawString(line, (getWidth() - g.getFontMetrics().stringWidth(line)) / 2, y);
                y += g.getFontMetrics().getHeight();
            }
            g.dispose();
            return;
        }
        Rectangle clip = g.getClipBounds();
        for (int i = 0; i < books.size(); i++) {
            Rectangle cell = cell(i);
            if (clip != null && !clip.intersects(cell)) continue;
            paintCell(g, t, books.get(i), cell, i == selected);
        }
        g.dispose();
    }

    private void paintCell(Graphics2D g, DesktopTheme t, LibraryEntry book, Rectangle cell, boolean isSelected) {
        int cw = coverWidth();
        int ch = coverHeight();
        int x = cell.x + (cell.width - cw) / 2;
        int y = cell.y + 6;
        if (isSelected) {
            g.setColor(t.selectionBackground());
            g.fillRect(x - 8, cell.y, cw + 16, cell.height - 6);
        }
        Image cover = cover(book).orElse(null);
        int dw = cw;
        int dh = ch;
        if (cover != null) {
            double scale = Math.min((double) cw / cover.getWidth(null), (double) ch / cover.getHeight(null));
            dw = (int) Math.round(cover.getWidth(null) * scale);
            dh = (int) Math.round(cover.getHeight(null) * scale);
        }
        int cx = x + (cw - dw) / 2;
        int cy = y + ch - dh;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(cx + 4, cy + 4, dw, dh);
        if (cover != null) g.drawImage(cover, cx, cy, dw, dh, null);
        else plainCover(g, book, cx, cy, dw, dh);
        Color text = isSelected ? t.selectionText() : t.fieldText();
        Font base = getFont();
        g.setColor(text);
        g.setFont(base.deriveFont(Font.BOLD));
        int ty = y + ch + g.getFontMetrics().getAscent() + 6;
        String title = ellipsis(g.getFontMetrics(), book.info().title(), cell.width - 8);
        g.drawString(title, cell.x + (cell.width - g.getFontMetrics().stringWidth(title)) / 2, ty);
        g.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() * 0.92f));
        String author = book.info().authors().isEmpty() ? msg.get("library.unknown-author") : book.info().authorLine();
        author = ellipsis(g.getFontMetrics(), author, cell.width - 8);
        ty += g.getFontMetrics().getHeight();
        g.drawString(author, cell.x + (cell.width - g.getFontMetrics().stringWidth(author)) / 2, ty);
        BookState s = states.apply(book);
        int py = ty + 8;
        if (s.reading() && s.progress() > 0) {
            g.setColor(isSelected ? t.selectionText().darker() : t.shadow());
            g.fillRect(x, py, cw, 4);
            g.setColor(isSelected ? t.selectionText() : t.selectionBackground().darker());
            g.fillRect(x, py, (int) Math.round(cw * s.progress()), 4);
        }
        g.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() * 0.85f));
        g.setColor(isSelected ? t.selectionText() : t.textInactive());
        String label = s.finished() ? "✓ " + msg.get("library.finished")
                : s.reading() ? msg.format("library.percent", Math.round(s.progress() * 100))
                : msg.get("library.new");
        g.drawString(label, cell.x + (cell.width - g.getFontMetrics().stringWidth(label)) / 2,
                py + 6 + g.getFontMetrics().getAscent());
    }

    private Optional<Image> cover(LibraryEntry book) {
        if (book.cover().isEmpty()) return Optional.empty();
        return covers.computeIfAbsent(book.cover().get(), p -> {
            try {
                return Optional.ofNullable(ImageIO.read(p.toFile()));
            } catch (IOException e) {
                return Optional.empty();
            }
        });
    }

    // --8<-- [start:plain-cover]
    /** A cover for a book without one: a colour from the title, a frame, the title and the author. */
    static void plainCover(Graphics2D g, LibraryEntry book, int x, int y, int w, int h) {
        float hue = (book.info().title().hashCode() & 0xffff) / 65535f;
        Color base = Color.getHSBColor(hue, 0.45f, 0.55f);
        Graphics2D c = (Graphics2D) g.create(x, y, w, h);
        c.setPaint(new GradientPaint(0, 0, base.brighter(), 0, h, base.darker()));
        c.fillRect(0, 0, w, h);
        Color gold = new Color(0xe9d9a6);
        c.setColor(gold);
        c.drawRect(5, 5, w - 11, h - 11);
        c.setFont(new Font(Font.SERIF, Font.BOLD, Math.max(10, w / 8)));
        int ty = h / 5;
        for (String line : wrap(c.getFontMetrics(), book.info().title(), w - 20, 4)) {
            ty += c.getFontMetrics().getHeight();
            c.drawString(line, (w - c.getFontMetrics().stringWidth(line)) / 2, ty);
        }
        c.setFont(new Font(Font.SERIF, Font.ITALIC, Math.max(9, w / 11)));
        ty += c.getFontMetrics().getHeight() + 4;
        for (String line : wrap(c.getFontMetrics(), book.info().authorLine(), w - 20, 2)) {
            c.drawString(line, (w - c.getFontMetrics().stringWidth(line)) / 2, ty);
            ty += c.getFontMetrics().getHeight();
        }
        c.dispose();
    }
    // --8<-- [end:plain-cover]

    static List<String> wrap(FontMetrics fm, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(next) > width && !line.isEmpty()) {
                lines.add(line);
                line = word;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) lines.add(line);
        if (lines.size() > maxLines) {
            List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            cut.set(maxLines - 1, ellipsis(fm, cut.get(maxLines - 1) + " …", width));
            return cut;
        }
        return lines;
    }

    static String ellipsis(FontMetrics fm, String s, int width) {
        if (fm.stringWidth(s) <= width) return s;
        String cut = s;
        while (!cut.isEmpty() && fm.stringWidth(cut + "…") > width) cut = cut.substring(0, cut.length() - 1);
        return cut.strip() + "…";
    }

    /** For tests: the cover image as the grid would draw it, without a cover file a plain one. */
    static BufferedImage render(LibraryEntry book, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        plainCover(g, book, 0, 0, w, h);
        g.dispose();
        return img;
    }
}
