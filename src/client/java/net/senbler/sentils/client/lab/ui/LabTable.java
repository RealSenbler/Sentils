package net.senbler.sentils.client.lab.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

import gg.essential.elementa.UIComponent;
import gg.essential.elementa.components.ScrollComponent;
import gg.essential.elementa.components.UIContainer;
import gg.essential.elementa.components.UIRoundedRectangle;
import gg.essential.elementa.components.UIText;
import gg.essential.elementa.constraints.RelativeConstraint;
import gg.essential.elementa.constraints.SiblingConstraint;

import net.senbler.sentils.client.gui.Theme;
import net.senbler.sentils.client.gui.elementa.E;

/**
 * A scrolling table on a card: the Lab's reports, lists and comparisons are all one of these.
 * Rows are column headings, section titles, lines of free text, or cells; a row of cells can be
 * clicked. A cell can have a mark in front of its text: a small picture, a colored dot, or an
 * arrow up or down.
 * <p>
 * Rows are added, and marked up, after the table is made, so it is only once the screen has
 * finished building that the table is laid out, by finish.
 */
public class LabTable extends UIRoundedRectangle {

    public static final int ROW_H = 12;
    private static final int PAD = 6;
    private static final int SCROLLBAR_W = 6;
    private static final int ICON_W = 12; // a small picture and the gap after it
    private static final int DOT_W = 7;
    private static final int ARROW_W = 8;
    private static final float ICON_SIZE = 9f;

    private enum Kind { CELLS, HEAD, SECTION, TEXT }

    public static final class Row {
        private final Kind kind;
        private final String[] cells;
        private final int[] colors; // 0 for the default
        private final int[] arrows; // 1 for up, -1 for down, 0 for none
        private final int[] dots; // a color, or 0 for none
        private final Icons.Icon[] icons;
        private boolean selected;
        private IntConsumer onClick; // given the column that was clicked

        private Row(Kind kind, String... cells) {
            this.kind = kind;
            this.cells = cells;
            this.colors = new int[cells.length];
            this.arrows = new int[cells.length];
            this.dots = new int[cells.length];
            this.icons = new Icons.Icon[cells.length];
        }

        public Row color(int column, int color) {
            if (column < this.colors.length) this.colors[column] = color;
            return this;
        }

        /** Colors every cell. */
        public Row color(int color) {
            Arrays.fill(this.colors, color);
            return this;
        }

        /**
         * Marks a cell as higher (above zero) or lower (below) than what it is set against, with
         * an arrow in green or red and its text in the same color. Zero leaves it unmarked.
         */
        public Row arrow(int column, double difference) {
            if (column < this.arrows.length && difference != 0) {
                this.arrows[column] = difference > 0 ? 1 : -1;
                this.colors[column] = difference > 0 ? Theme.GOOD : Theme.DANGER;
            }
            return this;
        }

        /** Puts a colored dot in front of a cell's text. */
        public Row dot(int column, int color) {
            if (column < this.dots.length) this.dots[column] = color;
            return this;
        }

        /** Puts a small picture in front of a cell's text. */
        public Row icon(int column, Icons.Icon icon) {
            if (column < this.icons.length) this.icons[column] = icon;
            return this;
        }

        public Row selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Row onClick(IntConsumer onClick) {
            this.onClick = onClick;
            return this;
        }
    }

    private final int width;
    private final int height;
    private final float[] weights;
    private final boolean[] right;
    private final int[] scroll; // one number, kept by the screen so it survives the table being rebuilt
    private final List<Row> rows = new ArrayList<>();
    private ScrollComponent body;

    /**
     * @param scroll  holds how many rows are scrolled off the top
     * @param weights how the width is shared out among the columns
     */
    public LabTable(int x, int y, int width, int height, int[] scroll, float... weights) {
        super(6f);
        this.width = width;
        this.height = height;
        this.scroll = scroll;
        this.weights = weights;
        this.right = new boolean[weights.length];
        this.setX(E.px(x)).setY(E.px(y)).setWidth(E.px(width)).setHeight(E.px(height)).setColor(E.color(Theme.CARD));
    }

    /** Sets the given columns to line up on the right, as numbers should. */
    public LabTable alignRight(int... columns) {
        for (int column : columns) this.right[column] = true;
        return this;
    }

    public Row add(String... cells) {
        return put(new Row(Kind.CELLS, cells));
    }

    /** A row of column headings. */
    public Row head(String... cells) {
        return put(new Row(Kind.HEAD, cells));
    }

    /** A title for the rows that follow, with a gap above it unless it is the first row. */
    public void section(String title) {
        if (!this.rows.isEmpty()) put(new Row(Kind.TEXT, ""));
        put(new Row(Kind.SECTION, title));
    }

    /** Free text across the whole width, wrapped onto as many rows as it needs. */
    public void text(String text, int color) {
        for (String line : E.wrap(text, innerWidth())) put(new Row(Kind.TEXT, line).color(color));
    }

    /** A line of free text with a colored dot in front of it, for a note or a warning. */
    public void note(String text, int dotColor) {
        boolean first = true;
        for (String line : E.wrap(text, innerWidth() - DOT_W)) {
            Row row = put(new Row(Kind.TEXT, line));
            // Only the first line has the dot; the rest are set in under it.
            row.dots[0] = first ? dotColor : -1;
            first = false;
        }
    }

    public boolean isEmpty() {
        return this.rows.isEmpty();
    }

    private Row put(Row row) {
        this.rows.add(row);
        return row;
    }

    private int innerWidth() {
        return this.width - PAD * 2 - SCROLLBAR_W;
    }

    /** The left edge of a column from the table's own, or of the one past the last for the right edge. */
    private int columnX(int column) {
        float total = 0;
        for (float weight : this.weights) total += weight;
        float before = 0;
        for (int i = 0; i < column; i++) before += this.weights[i];
        return PAD + Math.round(innerWidth() * before / total);
    }

    /** Notes how far the table is scrolled, for the one built in its place to start from. */
    void saveScroll() {
        if (this.body != null) this.scroll[0] = Math.max(0, Math.round(-this.body.getVerticalOffset() / ROW_H));
    }

    /** Lays the rows out. Called by the screen once it has finished adding and marking them. */
    void finish() {
        this.body = new ScrollComponent("", 0f, Color.WHITE, false, true, false, false, ROW_H * 3f, 1f, null);
        this.body.setX(E.px(0)).setY(E.px(PAD / 2f)).setWidth(E.px(this.width - SCROLLBAR_W))
                .setHeight(E.px(this.height - PAD)).setChildOf(this);
        UIComponent track = new UIContainer().setX(E.pxEnd(3)).setY(E.px(PAD)).setWidth(E.px(2))
                .setHeight(E.px(this.height - PAD * 2)).setChildOf(this);
        UIComponent thumb = new UIRoundedRectangle(1f).setWidth(new RelativeConstraint(1f))
                .setColor(E.color(Theme.TEXT_MUTED)).setChildOf(track);
        this.body.setVerticalScrollBarComponent(thumb, true);

        for (Row row : this.rows) addRow(row);
        if (this.scroll[0] > 0) this.body.scrollTo(0f, -this.scroll[0] * (float) ROW_H, false);
    }

    private void addRow(Row row) {
        Color normal = row.selected ? E.withAlpha(Theme.ACCENT, 50) : E.CLEAR;
        UIComponent line = new UIRoundedRectangle(3f).setX(E.px(2)).setY(new SiblingConstraint(1f))
                .setWidth(E.px(this.width - 4 - SCROLLBAR_W)).setHeight(E.px(ROW_H - 1)).setColor(normal)
                .setChildOf(this.body);
        float textY = (ROW_H - 1 - 8) / 2f;

        switch (row.kind) {
            // The accent color, lightened so that a dark one can still be read.
            case SECTION -> new UIText(row.cells[0], false).setX(E.px(PAD - 2)).setY(E.px(textY))
                    .setColor(E.color(Theme.lerpColor(Theme.ACCENT, Theme.TEXT, 0.4f))).setChildOf(line);
            case TEXT -> {
                int x = PAD - 2;
                if (row.dots[0] != 0) {
                    if (row.dots[0] != -1) dot(line, x, textY, row.dots[0]);
                    x += DOT_W;
                }
                new UIText(row.cells[0], false).setX(E.px(x)).setY(E.px(textY))
                        .setColor(E.color(row.colors[0] != 0 ? row.colors[0] : Theme.TEXT_MUTED)).setChildOf(line);
            }
            default -> addCells(line, row, textY);
        }

        if (row.onClick == null) return;
        E.hover(line, normal, row.selected ? E.withAlpha(Theme.ACCENT, 80) : E.color(Theme.CONTROL_HOVER));
        line.onMouseClickConsumer(event -> {
            if (event.getMouseButton() != 0) return;
            float x = event.getAbsoluteX() - this.getLeft();
            int column = 0;
            while (column + 1 < this.weights.length && x >= columnX(column + 1)) column++;
            int clicked = column;
            E.clickSound();
            E.later(() -> row.onClick.accept(clicked));
        });
    }

    private void addCells(UIComponent line, Row row, float textY) {
        int count = Math.min(row.cells.length, this.weights.length);
        for (int column = 0; column < count; column++) {
            if (row.cells[column] == null || row.cells[column].isEmpty()) continue;
            // A cell may run on over the empty cells after it.
            int end = column + 1;
            while (!this.right[column] && end < count
                    && (row.cells[end] == null || row.cells[end].isEmpty())) end++;
            int left = columnX(column) - 2; // the line starts two in from the table's edge
            int marks = (row.icons[column] != null ? ICON_W : 0) + (row.dots[column] != 0 ? DOT_W : 0)
                    + (row.arrows[column] != 0 ? ARROW_W : 0);
            int space = columnX(end) - columnX(column) - 4 - marks;
            String cell = E.fit(row.cells[column], space);
            int color = row.kind == Kind.HEAD ? Theme.TEXT_MUTED
                    : row.colors[column] != 0 ? row.colors[column] : Theme.TEXT;

            int x = this.right[column] ? left + space - E.font().width(cell) : left;
            if (row.icons[column] != null) {
                row.icons[column].component(ICON_SIZE).setX(E.px(x)).setY(E.px((ROW_H - 1 - ICON_SIZE) / 2f)).setChildOf(line);
                x += ICON_W;
            }
            if (row.dots[column] != 0) {
                dot(line, x, textY, row.dots[column]);
                x += DOT_W;
            }
            if (row.arrows[column] != 0) {
                E.arrow(row.arrows[column] > 0, E.color(color)).setX(E.px(x)).setY(E.px(textY + 1)).setChildOf(line);
                x += ARROW_W;
            }
            new UIText(cell, false).setX(E.px(x)).setY(E.px(textY)).setColor(E.color(color)).setChildOf(line);
        }
    }

    private static void dot(UIComponent line, float x, float textY, int color) {
        new UIRoundedRectangle(2f).setX(E.px(x)).setY(E.px(textY + 2)).setWidth(E.px(4)).setHeight(E.px(4))
                .setColor(E.color(color)).setChildOf(line);
    }
}
