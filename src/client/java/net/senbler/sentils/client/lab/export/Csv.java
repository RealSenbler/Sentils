package net.senbler.sentils.client.lab.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes and reads comma-separated values the way spreadsheets expect them. */
public final class Csv {

    private Csv() {}

    /** Starts a file so that spreadsheet programs read it as UTF-8. */
    public static final String BYTE_ORDER_MARK = "﻿";

    /**
     * A cell of text. It is quoted if it holds a comma, a quote or a line break, and text a
     * spreadsheet would run as a formula is marked as plain text with a leading apostrophe.
     */
    public static String text(String value) {
        if (value == null) return "";
        String text = value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                || text.startsWith(" ") || text.endsWith(" ");
        return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    /** A cell holding a number, or an empty cell if there is none: never a zero that was not measured. */
    public static String number(Double value) {
        if (value == null || !Double.isFinite(value)) return "";
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString(value.longValue());
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public static String row(List<String> cells) {
        return String.join(",", cells) + "\r\n";
    }

    /** Reads CSV text into rows of cells, undoing the quoting. */
    public static List<List<String>> parse(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        String text = csv.startsWith(BYTE_ORDER_MARK) ? csv.substring(1) : csv;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
