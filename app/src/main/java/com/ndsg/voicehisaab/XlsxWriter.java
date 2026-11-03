package com.ndsg.voicehisaab;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal, dependency-free .xlsx (Office Open XML) writer: multiple sheets, styles,
 * formulas with cached values, frozen header rows, auto-filter, merged cells, A4 print setup.
 */
public final class XlsxWriter {
    public static final int S_DEFAULT = 0, S_HEADER = 1, S_MONEY = 2, S_DATE = 3, S_TOTAL_MONEY = 4,
            S_TITLE = 5, S_BOLD = 6, S_PCT = 7, S_TEXT = 8, S_IN_MONEY = 9, S_OUT_MONEY = 10,
            S_SUBTITLE = 11, S_TOTAL_LABEL = 12, S_SECTION = 13, S_CENTER = 14, S_TOTAL_PCT = 15;

    private final List<Sheet> sheets = new ArrayList<>();

    public Sheet addSheet(String name) {
        String n = name.replaceAll("[\\[\\]:*?/\\\\]", " ");
        if (n.length() > 31) n = n.substring(0, 31);
        Sheet s = new Sheet(n);
        sheets.add(s);
        return s;
    }

    public static final class Sheet {
        final String name;
        final List<Row> rows = new ArrayList<>();
        final List<String> merges = new ArrayList<>();
        double[] widths = new double[0];
        int freezeRows = 0;
        String autoFilter;
        boolean landscape = true;

        Sheet(String name) { this.name = name; }

        public String name() { return name; }

        public Row row() {
            Row r = new Row(rows.size() + 1);
            rows.add(r);
            return r;
        }

        /** Current next row number (1-based). */
        public int nextRowNum() { return rows.size() + 1; }

        public Sheet widths(double... w) { widths = w; return this; }
        public Sheet freeze(int rowsAbove) { freezeRows = rowsAbove; return this; }
        public Sheet filter(String ref) { autoFilter = ref; return this; }
        public Sheet merge(String ref) { merges.add(ref); return this; }
        public Sheet portrait() { landscape = false; return this; }
    }

    public static final class Row {
        final int num;
        final List<String> cells = new ArrayList<>();
        int col = 0;
        double height = 0;

        Row(int num) { this.num = num; }

        public int num() { return num; }
        public Row height(double h) { height = h; return this; }

        private String ref() { return col(col) + num; }

        public Row text(String v, int style) {
            if (v == null) v = "";
            cells.add("<c r=\"" + ref() + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                    + esc(v) + "</t></is></c>");
            col++;
            return this;
        }

        public Row num(double v, int style) {
            cells.add("<c r=\"" + ref() + "\" s=\"" + style + "\"><v>" + numStr(v) + "</v></c>");
            col++;
            return this;
        }

        public Row date(LocalDate d, int style) {
            return num(serial(d), style);
        }

        /** Formula with its pre-computed value so viewers that do not recalculate still show numbers. */
        public Row formula(String f, double cached, int style) {
            cells.add("<c r=\"" + ref() + "\" s=\"" + style + "\"><f>" + esc(f) + "</f><v>" + numStr(cached)
                    + "</v></c>");
            col++;
            return this;
        }

        /** Formula returning text (e.g. IF(...,"")) with cached string. */
        public Row formulaText(String f, String cached, int style) {
            cells.add("<c r=\"" + ref() + "\" s=\"" + style + "\" t=\"str\"><f>" + esc(f) + "</f><v>" + esc(cached)
                    + "</v></c>");
            col++;
            return this;
        }

        public Row blank(int style) {
            cells.add("<c r=\"" + ref() + "\" s=\"" + style + "\"/>");
            col++;
            return this;
        }

        public Row skip() { col++; return this; }
    }

    // ---------------- helpers ----------------
    public static String col(int idx) {
        StringBuilder sb = new StringBuilder();
        int n = idx + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    public static double serial(LocalDate d) {
        return ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), d);
    }

    static String numStr(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD)
                            || Character.isSurrogate(c)) sb.append(c);
            }
        }
        return sb.toString();
    }

    static String quoteSheet(String name) {
        return "'" + name.replace("'", "''") + "'";
    }

    // ---------------- writing ----------------
    public void write(OutputStream os) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(os);
        put(zip, "[Content_Types].xml", contentTypes());
        put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>");
        String now = java.time.Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        put(zip, "docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>Income &amp; Expenditure Report</dc:title><dc:creator>Voice Hisaab</dc:creator>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created>"
                + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>"
                + "</cp:coreProperties>");
        put(zip, "docProps/app.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">"
                + "<Application>Voice Hisaab</Application></Properties>");
        put(zip, "xl/workbook.xml", workbook());
        put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
        put(zip, "xl/styles.xml", styles());
        for (int i = 0; i < sheets.size(); i++) put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
        zip.finish();
        zip.flush();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<bookViews><workbookView activeTab=\"0\"/></bookViews><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(esc(sheets.get(i).name)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets>");
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < sheets.size(); i++) {
            Sheet s = sheets.get(i);
            if (s.autoFilter == null) continue;
            String[] p = s.autoFilter.split(":");
            names.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i)
                    .append("\" hidden=\"1\">").append(esc(quoteSheet(s.name))).append("!")
                    .append(abs(p[0])).append(":").append(abs(p[1])).append("</definedName>");
        }
        if (names.length() > 0) sb.append("<definedNames>").append(names).append("</definedNames>");
        sb.append("<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>");
        return sb.toString();
    }

    private static String abs(String ref) {
        return ref.replaceAll("^([A-Z]+)(\\d+)$", "\\$$1\\$$2");
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private static String styles() {
        String b = "<border><left style=\"thin\"><color rgb=\"FFC9D2DC\"/></left><right style=\"thin\"><color rgb=\"FFC9D2DC\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFC9D2DC\"/></top><bottom style=\"thin\"><color rgb=\"FFC9D2DC\"/></bottom><diagonal/></border>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"3\">"
                + "<numFmt numFmtId=\"164\" formatCode=\"#,##0.00;[Red]\\-#,##0.00\"/>"
                + "<numFmt numFmtId=\"165\" formatCode=\"dd\\-mmm\\-yyyy\"/>"
                + "<numFmt numFmtId=\"166\" formatCode=\"0.0%\"/>"
                + "</numFmts>"
                + "<fonts count=\"8\">"
                + "<font><sz val=\"11\"/><color theme=\"1\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"   // 0
                + "<font><b/><sz val=\"11\"/><color theme=\"1\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 1
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 2
                + "<font><b/><sz val=\"15\"/><color rgb=\"FF1E3A5F\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 3
                + "<font><sz val=\"11\"/><color rgb=\"FF1B7A3A\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 4
                + "<font><sz val=\"11\"/><color rgb=\"FFB3261E\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 5
                + "<font><i/><sz val=\"10\"/><color rgb=\"FF5F6B7A\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 6
                + "<font><b/><sz val=\"12\"/><color rgb=\"FF1E3A5F\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" // 7
                + "</fonts>"
                + "<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1E3A5F\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE6ECF3\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" + b + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"16\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" // 0 default
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>" // 1 header
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\"/>" // 2 money
                + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>" // 3 date
                + "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" // 4 total money
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" // 5 title
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\"/>" // 6 bold
                + "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\"/>" // 7 pct
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>" // 8 text
                + "<xf numFmtId=\"164\" fontId=\"4\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyBorder=\"1\"/>" // 9 income money
                + "<xf numFmtId=\"164\" fontId=\"5\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyBorder=\"1\"/>" // 10 expense money
                + "<xf numFmtId=\"0\" fontId=\"6\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" // 11 subtitle
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" // 12 total label
                + "<xf numFmtId=\"0\" fontId=\"7\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" // 13 section
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>" // 14 center
                + "<xf numFmtId=\"166\" fontId=\"1\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" // 15 total pct
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheetXml(Sheet s) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
                .append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\">");
        if (s.freezeRows > 0) {
            sb.append("<pane ySplit=\"").append(s.freezeRows).append("\" topLeftCell=\"A").append(s.freezeRows + 1)
                    .append("\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\"/>");
        }
        sb.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>");
        if (s.widths.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < s.widths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                        .append(numStr(s.widths[i])).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        for (Row r : s.rows) {
            sb.append("<row r=\"").append(r.num).append("\"");
            if (r.height > 0) sb.append(" ht=\"").append(numStr(r.height)).append("\" customHeight=\"1\"");
            sb.append(">");
            for (String c : r.cells) sb.append(c);
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (s.autoFilter != null) sb.append("<autoFilter ref=\"").append(s.autoFilter).append("\"/>");
        if (!s.merges.isEmpty()) {
            sb.append("<mergeCells count=\"").append(s.merges.size()).append("\">");
            for (String m : s.merges) sb.append("<mergeCell ref=\"").append(m).append("\"/>");
            sb.append("</mergeCells>");
        }
        sb.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
                .append("<pageSetup paperSize=\"9\" orientation=\"").append(s.landscape ? "landscape" : "portrait")
                .append("\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
                .append("</worksheet>");
        return sb.toString();
    }
}
