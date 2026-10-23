package eu.amadeos.apps.books.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.Font;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** The toolbar pictures of the reader that amade-commons has no standard glyph for. */
enum Glyph implements ToolIcon.Glyph {
    LIBRARY, SIDEBAR, PREVIOUS, NEXT, BOOKMARK, APPEARANCE, TWO_PAGES, RIGHT_TO_LEFT, COVERS, LIST, ADD, BACK, FORWARD;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case LIBRARY -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 3.5, 3, 10), pen.paper());
                pen.fillAndDraw(new Rectangle2D.Double(4.5, 2.5, 3, 11), pen.light());
                pen.fillAndDraw(new Rectangle2D.Double(7.5, 4.5, 2.5, 9), pen.paper());
                Path2D leaning = new Path2D.Double();
                leaning.moveTo(10.5, 5);
                leaning.lineTo(13, 4.2);
                leaning.lineTo(15, 13.3);
                leaning.lineTo(12.5, 13.5);
                leaning.closePath();
                pen.fillAndDraw(leaning, pen.light());
                pen.line(0.5, 14.5, 15.5, 14.5);
            }
            case SIDEBAR -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
                pen.fill(new Rectangle2D.Double(2, 3, 4, 10), pen.light());
                pen.line(6, 2.5, 6, 13.5);
            }
            case PREVIOUS -> pen.triangle(11, 3, 11, 13, 4.5, 8);
            case NEXT -> pen.triangle(5, 3, 5, 13, 11.5, 8);
            case BOOKMARK -> {
                Path2D ribbon = new Path2D.Double();
                ribbon.moveTo(4.5, 1.5);
                ribbon.lineTo(11.5, 1.5);
                ribbon.lineTo(11.5, 14.5);
                ribbon.lineTo(8, 11);
                ribbon.lineTo(4.5, 14.5);
                ribbon.closePath();
                pen.fillAndDraw(ribbon, pen.light());
            }
            case APPEARANCE -> {
                pen.g().setColor(pen.ink());
                pen.g().setFont(new Font(Font.SERIF, Font.BOLD, 13));
                pen.g().drawString("A", 0.5f, 13.5f);
                pen.g().setFont(new Font(Font.SERIF, Font.PLAIN, 9));
                pen.g().drawString("a", 10f, 13.5f);
            }
            case TWO_PAGES -> {
                pen.fillAndDraw(new Rectangle2D.Double(0.5, 2.5, 7, 11), pen.paper());
                pen.fillAndDraw(new Rectangle2D.Double(8.5, 2.5, 7, 11), pen.paper());
                for (int i = 0; i < 4; i++) {
                    pen.line(2, 5 + 2 * i, 6, 5 + 2 * i);
                    pen.line(10, 5 + 2 * i, 14, 5 + 2 * i);
                }
            }
            case RIGHT_TO_LEFT -> {
                pen.bold(1.8f);
                pen.line(6, 8, 14, 8);
                pen.thin();
                pen.triangle(7, 3.5, 7, 12.5, 1.5, 8);
            }
            case COVERS -> {
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        pen.fillAndDraw(new Rectangle2D.Double(2.5 + 6 * i, 1.5 + 7 * j, 4, 6), pen.paper());
                    }
                }
            }
            case LIST -> {
                for (int i = 0; i < 3; i++) {
                    pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5 + 4 * i, 2, 2), pen.light());
                    pen.line(5.5, 3.5 + 4 * i, 14.5, 3.5 + 4 * i);
                }
            }
            case ADD -> {
                pen.bold(2.2f);
                pen.line(8, 2.5, 8, 13.5);
                pen.line(2.5, 8, 13.5, 8);
                pen.thin();
            }
            case BACK -> {
                pen.mirror();
                arrow(pen);
            }
            case FORWARD -> arrow(pen);
        }
    }

    private static void arrow(ToolIcon.Pen pen) {
        pen.bold(2f);
        pen.line(2.5, 8, 9, 8);
        pen.thin();
        pen.triangle(8, 3.5, 8, 12.5, 13.5, 8);
    }
}
