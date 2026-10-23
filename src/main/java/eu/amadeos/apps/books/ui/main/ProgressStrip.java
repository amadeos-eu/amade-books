package eu.amadeos.apps.books.ui.main;

import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.DoubleConsumer;

/** How far the book is read, with a tick at the start of every chapter; a click jumps to that place. */
final class ProgressStrip extends JComponent {
    private double progress = -1;
    private List<Double> ticks = List.of();

    ProgressStrip(DoubleConsumer clicked) {
        setName("progress");
        setPreferredSize(new Dimension(Math.round(220 * AmadeUI.fontScale()), 14));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (progress >= 0 && getWidth() > 4) clicked.accept(Math.clamp((e.getX() - 2) / (double) (getWidth() - 4), 0, 1));
            }
        });
    }

    /** @param newProgress -1 while it is not known */
    void show(double newProgress, List<Double> chapterStarts) {
        progress = newProgress;
        ticks = List.copyOf(chapterStarts);
        setToolTipText(progress < 0 ? null : Math.round(progress * 100) + " %");
        repaint();
    }

    double progress() {
        return progress;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            DesktopTheme t = AmadeUI.theme();
            int h = 8;
            int y = (getHeight() - h) / 2;
            int w = getWidth() - 4;
            AmadeUI.paintBevel(g, 1, y - 1, w + 2, h + 2, false, 1);
            g.setColor(t.sunkenBg());
            g.fillRect(2, y, w, h);
            if (progress < 0) return;
            g.setColor(t.activeTitleBg());
            g.fillRect(2, y, (int) Math.round(w * progress), h);
            g.setColor(t.shadow());
            for (double tick : ticks) {
                if (tick <= 0) continue;
                int x = 2 + (int) Math.round(w * tick);
                g.drawLine(x, y, x, y + h - 1);
            }
        } finally {
            g.dispose();
        }
    }
}
