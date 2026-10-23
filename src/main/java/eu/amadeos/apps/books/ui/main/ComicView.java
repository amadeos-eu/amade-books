package eu.amadeos.apps.books.ui.main;

import eu.amadeos.apps.books.domain.ComicFit;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * One or two comic pages on a dark desk, scaled by the fit and the zoom. When the pages are larger than the
 * window the view scrolls; a click on the left or right half turns the page (the presenter swaps the direction
 * for right-to-left comics).
 */
final class ComicView extends JComponent implements Scrollable {
    static final Color DESK = new Color(0x232323);
    private static final int GAP = 2;

    interface Callbacks {
        /** A click on the left (false) or the right half (true). */
        void side(boolean right);
    }

    private List<BufferedImage> pages = List.of();
    private List<Integer> numbers = List.of();
    private ComicFit fit = ComicFit.PAGE;
    private double zoom = 1;
    private boolean twoPages;

    ComicView(Callbacks callbacks) {
        setName("comic");
        setFocusable(true);
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                int x = e.getX() - visibleOrigin();
                callbacks.side(x > viewWidth() / 2);
            }
        });
    }

    private int visibleOrigin() {
        return getParent() instanceof JViewport v ? v.getViewPosition().x : 0;
    }

    private int viewWidth() {
        return getParent() instanceof JViewport v ? v.getWidth() : getWidth();
    }

    private int viewHeight() {
        return getParent() instanceof JViewport v ? v.getHeight() : getHeight();
    }

    void showPages(List<BufferedImage> newPages, List<Integer> newNumbers, ComicFit newFit, double newZoom, boolean two) {
        pages = new ArrayList<>(newPages);
        numbers = List.copyOf(newNumbers);
        fit = newFit;
        zoom = newZoom;
        twoPages = two;
        revalidate();
        repaint();
        if (getParent() instanceof JViewport v) v.setViewPosition(new java.awt.Point(0, 0));
    }

    List<Integer> numbers() {
        return numbers;
    }

    // --8<-- [start:scale]
    /** The scale of the pages: the fit for the window times the zoom; both pages share one height. */
    double scale() {
        int[] size = total();
        if (size[0] == 0) return 1;
        return fit.scale(size[0], size[1], viewWidth() - 2 * GAP, viewHeight() - 2 * GAP) * zoom;
    }

    /** The width of the pages side by side at the height of the tallest, and that height. */
    private int[] total() {
        int height = 0;
        for (BufferedImage p : pages) if (p != null) height = Math.max(height, p.getHeight());
        if (height == 0) return new int[]{0, 0};
        int width = 0;
        for (BufferedImage p : pages) {
            width += p == null ? height * 2 / 3 : (int) Math.round(p.getWidth() * (double) height / p.getHeight());
        }
        return new int[]{width + GAP * (pages.size() - 1), height};
    }
    // --8<-- [end:scale]

    @Override
    public Dimension getPreferredSize() {
        int[] size = total();
        double s = scale();
        return new Dimension((int) Math.ceil(size[0] * s) + 2 * GAP, (int) Math.ceil(size[1] * s) + 2 * GAP);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setColor(DESK);
            g.fillRect(0, 0, getWidth(), getHeight());
            int[] size = total();
            if (size[0] == 0) return;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double s = scale();
            int h = (int) Math.round(size[1] * s);
            int w = (int) Math.round(size[0] * s);
            int x = Math.max(GAP, (getWidth() - w) / 2);
            int y = Math.max(GAP, (getHeight() - h) / 2);
            for (BufferedImage p : pages) {
                int pw = p == null ? h * 2 / 3 : (int) Math.round(p.getWidth() * (double) h / p.getHeight());
                if (p == null) {
                    g.setColor(DESK.brighter());
                    g.fillRect(x, y, pw, h);
                } else {
                    g.drawImage(p, x, y, pw, h, null);
                }
                x += pw + GAP;
            }
        } finally {
            g.dispose();
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 48;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(48, (orientation == javax.swing.SwingConstants.VERTICAL ? visible.height : visible.width) - 48);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport v && getPreferredSize().width <= v.getWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport v && getPreferredSize().height <= v.getHeight();
    }

    boolean twoPages() {
        return twoPages;
    }
}
