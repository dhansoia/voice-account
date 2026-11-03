package com.ndsg.voicehisaab;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.ndsg.voicehisaab.XlsxWriter.*;

/** Builds the Excel workbook: Summary, Transactions, Monthly, Daily and Category × Month sheets. */
public final class ReportBuilder {
    private ReportBuilder() {}

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd-MMM-yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter MON = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final String TX = "Transactions";

    private static double rs(long paise) { return paise / 100.0; }

    private static String day(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    /**
     * @param txns     entries within [from, to], sorted oldest first
     * @param opening  balance (paise) of all entries before {@code from}
     */
    public static XlsxWriter build(List<Txn> txns, LocalDate from, LocalDate to, String label, long opening,
                                   LocalDateTime generated) {
        XlsxWriter wb = new XlsxWriter();
        String period = "Period: " + from.format(DMY) + " to " + to.format(DMY) + (label == null ? "" : "   (" + label + ")");
        String gen = "Generated on " + generated.format(DMY_HM) + "  ·  Voice Hisaab";

        long totIn = 0, totOut = 0;
        for (Txn t : txns) { if (t.income) totIn += t.paise; else totOut += t.paise; }

        XlsxWriter.Sheet sum = wb.addSheet("Summary");
        XlsxWriter.Sheet tx = wb.addSheet(TX);

        // ---------------- Transactions ----------------
        tx.widths(7, 13, 7, 10, 22, 9, 15, 15, 16, 50).freeze(4);
        tx.row().height(22).text("Income & Expenditure Register", S_TITLE);
        tx.merge("A1:J1");
        tx.row().text(period, S_SUBTITLE);
        tx.merge("A2:J2");
        tx.row();
        tx.row().height(30).text("S.No", S_HEADER).text("Date", S_HEADER).text("Day", S_HEADER).text("Type", S_HEADER)
                .text("Category", S_HEADER).text("Mode", S_HEADER).text("Income (₹)", S_HEADER)
                .text("Expenditure (₹)", S_HEADER).text("Balance (₹)", S_HEADER).text("Description", S_HEADER);
        XlsxWriter.Row op = tx.row();
        int openRow = op.num();
        op.blank(S_TEXT).date(from, S_DATE).text(day(from), S_CENTER).blank(S_TEXT).text("Opening Balance", S_BOLD)
                .blank(S_TEXT).blank(S_MONEY).blank(S_MONEY).num(rs(opening), S_TOTAL_MONEY)
                .text("Balance brought forward", S_TEXT);
        long bal = opening;
        int sn = 0;
        int prev = openRow;
        for (Txn t : txns) {
            XlsxWriter.Row r = tx.row();
            int n = r.num();
            bal += t.signedPaise();
            r.num(++sn, S_CENTER).date(t.date, S_DATE).text(day(t.date), S_CENTER)
                    .text(t.income ? "Income" : "Expense", S_CENTER).text(t.category, S_TEXT).text(t.mode, S_CENTER);
            if (t.income) r.num(rs(t.paise), S_IN_MONEY).blank(S_MONEY);
            else r.blank(S_MONEY).num(rs(t.paise), S_OUT_MONEY);
            r.formula("I" + prev + "+G" + n + "-H" + n, rs(bal), S_MONEY).text(t.note, S_TEXT);
            prev = n;
        }
        int firstData = openRow + 1;
        int lastData = Math.max(prev, openRow);
        XlsxWriter.Row tr = tx.row();
        int txTotalRow = tr.num();
        String gRange = "G" + firstData + ":G" + Math.max(lastData, firstData);
        String hRange = "H" + firstData + ":H" + Math.max(lastData, firstData);
        if (txns.isEmpty()) { gRange = "G" + openRow + ":G" + openRow; hRange = "H" + openRow + ":H" + openRow; }
        tr.blank(S_TOTAL_LABEL).blank(S_TOTAL_LABEL).blank(S_TOTAL_LABEL).blank(S_TOTAL_LABEL).blank(S_TOTAL_LABEL)
                .text("TOTAL", S_TOTAL_LABEL)
                .formula("SUM(" + gRange + ")", rs(totIn), S_TOTAL_MONEY)
                .formula("SUM(" + hRange + ")", rs(totOut), S_TOTAL_MONEY)
                .formula("I" + lastData, rs(bal), S_TOTAL_MONEY)
                .text("Closing balance", S_TOTAL_LABEL);
        tx.filter("A4:J" + lastData);

        // ---------------- Summary ----------------
        String q = quoteSheet(TX);
        sum.portrait().widths(34, 19, 14, 12);
        sum.row().height(22).text("Income & Expenditure Statement", S_TITLE);
        sum.merge("A1:D1");
        sum.row().text(period, S_SUBTITLE);
        sum.merge("A2:D2");
        sum.row().text(gen, S_SUBTITLE);
        sum.merge("A3:D3");
        sum.row();
        sum.row().height(22).text("Particulars", S_HEADER).text("Amount (₹)", S_HEADER);
        int rOpen = sum.row().text("Opening Balance", S_TEXT).num(rs(opening), S_MONEY).num();
        int rIn = sum.row().text("Total Income", S_TEXT).formula(q + "!G" + txTotalRow, rs(totIn), S_IN_MONEY).num();
        int rOut = sum.row().text("Total Expenditure", S_TEXT).formula(q + "!H" + txTotalRow, rs(totOut), S_OUT_MONEY).num();
        int rNet = sum.row().text("Net Surplus / (Deficit)", S_TOTAL_LABEL)
                .formula("B" + rIn + "-B" + rOut, rs(totIn - totOut), S_TOTAL_MONEY).num();
        sum.row().text("Closing Balance", S_TOTAL_LABEL)
                .formula("B" + rOpen + "+B" + rNet, rs(opening + totIn - totOut), S_TOTAL_MONEY);
        sum.row().text("Number of entries", S_TEXT).num(txns.size(), S_CENTER);
        sum.row().text("Savings rate (Net ÷ Income)", S_TEXT)
                .formula("IF(B" + rIn + ">0,B" + rNet + "/B" + rIn + ",0)", totIn > 0 ? (double) (totIn - totOut) / totIn : 0, S_PCT);
        sum.row();

        categoryTable(sum, txns, false, q);
        sum.row();
        categoryTable(sum, txns, true, q);
        sum.row();
        modeTable(sum, txns, q);

        // ---------------- Monthly ----------------
        YearMonth startYm = YearMonth.from(from), endYm = YearMonth.from(to);
        if (!txns.isEmpty()) {
            YearMonth firstTx = YearMonth.from(txns.get(0).date);
            YearMonth lastTx = YearMonth.from(txns.get(txns.size() - 1).date);
            if (firstTx.isAfter(startYm)) startYm = firstTx;
            if (startYm.plusMonths(239).isBefore(endYm)) endYm = lastTx;
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth ym = startYm; !ym.isAfter(endYm) && months.size() < 240; ym = ym.plusMonths(1)) months.add(ym);

        Map<YearMonth, long[]> byMonth = new LinkedHashMap<>();
        for (YearMonth ym : months) byMonth.put(ym, new long[3]);
        for (Txn t : txns) {
            long[] a = byMonth.get(YearMonth.from(t.date));
            if (a == null) continue;
            if (t.income) a[0] += t.paise; else a[1] += t.paise;
            a[2]++;
        }
        XlsxWriter.Sheet mo = wb.addSheet("Monthly");
        mo.portrait().widths(14, 16, 16, 17, 11, 9).freeze(4);
        mo.row().height(22).text("Month-wise Summary", S_TITLE);
        mo.merge("A1:F1");
        mo.row().text(period, S_SUBTITLE);
        mo.merge("A2:F2");
        mo.row();
        mo.row().height(30).text("Month", S_HEADER).text("Income (₹)", S_HEADER).text("Expenditure (₹)", S_HEADER)
                .text("Net Surplus (₹)", S_HEADER).text("Savings %", S_HEADER).text("Entries", S_HEADER);
        int mFirst = mo.nextRowNum();
        for (Map.Entry<YearMonth, long[]> e : byMonth.entrySet()) {
            long[] a = e.getValue();
            XlsxWriter.Row r = mo.row();
            int n = r.num();
            r.text(e.getKey().format(MON), S_CENTER).num(rs(a[0]), S_IN_MONEY).num(rs(a[1]), S_OUT_MONEY)
                    .formula("B" + n + "-C" + n, rs(a[0] - a[1]), S_MONEY)
                    .formula("IF(B" + n + ">0,D" + n + "/B" + n + ",0)", a[0] > 0 ? (double) (a[0] - a[1]) / a[0] : 0, S_PCT)
                    .num(a[2], S_CENTER);
        }
        int mLast = mo.nextRowNum() - 1;
        totalsRow(mo, mFirst, mLast, new char[]{'B', 'C', 'D'}, new double[]{rs(totIn), rs(totOut), rs(totIn - totOut)},
                'F', txns.size(), 'E', totIn > 0 ? (double) (totIn - totOut) / totIn : 0);

        // ---------------- Daily ----------------
        TreeMap<LocalDate, long[]> byDay = new TreeMap<>();
        for (Txn t : txns) {
            long[] a = byDay.computeIfAbsent(t.date, k -> new long[3]);
            if (t.income) a[0] += t.paise; else a[1] += t.paise;
            a[2]++;
        }
        XlsxWriter.Sheet dy = wb.addSheet("Daily");
        dy.portrait().widths(14, 7, 16, 16, 16, 9, 17).freeze(4);
        dy.row().height(22).text("Day-wise Summary", S_TITLE);
        dy.merge("A1:G1");
        dy.row().text(period, S_SUBTITLE);
        dy.merge("A2:G2");
        dy.row();
        dy.row().height(30).text("Date", S_HEADER).text("Day", S_HEADER).text("Income (₹)", S_HEADER)
                .text("Expenditure (₹)", S_HEADER).text("Net (₹)", S_HEADER).text("Entries", S_HEADER)
                .text("Closing Balance (₹)", S_HEADER);
        int dFirst = dy.nextRowNum();
        long run = opening;
        for (Map.Entry<LocalDate, long[]> e : byDay.entrySet()) {
            long[] a = e.getValue();
            run += a[0] - a[1];
            XlsxWriter.Row r = dy.row();
            int n = r.num();
            r.date(e.getKey(), S_DATE).text(day(e.getKey()), S_CENTER).num(rs(a[0]), S_IN_MONEY).num(rs(a[1]), S_OUT_MONEY)
                    .formula("C" + n + "-D" + n, rs(a[0] - a[1]), S_MONEY).num(a[2], S_CENTER)
                    .formula(n == dFirst ? numStr(rs(opening)) + "+E" + n : "G" + (n - 1) + "+E" + n, rs(run), S_MONEY);
        }
        int dLast = dy.nextRowNum() - 1;
        if (dLast >= dFirst) {
            XlsxWriter.Row r = dy.row();
            r.text("TOTAL", S_TOTAL_LABEL).blank(S_TOTAL_LABEL)
                    .formula("SUM(C" + dFirst + ":C" + dLast + ")", rs(totIn), S_TOTAL_MONEY)
                    .formula("SUM(D" + dFirst + ":D" + dLast + ")", rs(totOut), S_TOTAL_MONEY)
                    .formula("SUM(E" + dFirst + ":E" + dLast + ")", rs(totIn - totOut), S_TOTAL_MONEY)
                    .formula("SUM(F" + dFirst + ":F" + dLast + ")", txns.size(), S_TOTAL_LABEL)
                    .formula("G" + dLast, rs(run), S_TOTAL_MONEY);
        } else {
            dy.row().text("No entries in this period", S_SUBTITLE);
        }

        // ---------------- Category × Month (expenses) ----------------
        if (months.size() >= 2 && months.size() <= 36 && totOut > 0) {
            TreeMap<String, long[]> grid = new TreeMap<>();
            for (Txn t : txns) {
                if (t.income) continue;
                int idx = months.indexOf(YearMonth.from(t.date));
                if (idx < 0) continue;
                grid.computeIfAbsent(t.category, k -> new long[months.size()])[idx] += t.paise;
            }
            XlsxWriter.Sheet cm = wb.addSheet("Expense by Month");
            int nc = months.size();
            double[] w = new double[nc + 2];
            w[0] = 22;
            for (int i = 1; i <= nc; i++) w[i] = 13;
            w[nc + 1] = 15;
            cm.widths(w).freeze(4);
            cm.row().height(22).text("Category-wise Expenditure by Month", S_TITLE);
            cm.merge("A1:" + col(Math.min(nc + 1, 9)) + "1");
            cm.row().text(period, S_SUBTITLE);
            cm.merge("A2:" + col(Math.min(nc + 1, 9)) + "2");
            cm.row();
            XlsxWriter.Row h = cm.row().height(22).text("Category", S_HEADER);
            for (YearMonth ym : months) h.text(ym.format(MON), S_HEADER);
            h.text("Total (₹)", S_HEADER);
            int cFirst = cm.nextRowNum();
            String lastCol = col(nc);
            for (Map.Entry<String, long[]> e : grid.entrySet()) {
                XlsxWriter.Row r = cm.row();
                r.text(e.getKey(), S_TEXT);
                long rowTot = 0;
                for (long v : e.getValue()) {
                    rowTot += v;
                    if (v == 0) r.blank(S_MONEY); else r.num(rs(v), S_MONEY);
                }
                r.formula("SUM(B" + r.num() + ":" + lastCol + r.num() + ")", rs(rowTot), S_TOTAL_MONEY);
            }
            int cLast = cm.nextRowNum() - 1;
            XlsxWriter.Row t = cm.row().text("TOTAL", S_TOTAL_LABEL);
            for (int i = 0; i <= nc; i++) {
                long colTot = 0;
                if (i < nc) for (long[] a : grid.values()) colTot += a[i];
                else colTot = totOut;
                String c = col(i + 1);
                t.formula("SUM(" + c + cFirst + ":" + c + cLast + ")", rs(colTot), S_TOTAL_MONEY);
            }
        }
        return wb;
    }

    private static void totalsRow(XlsxWriter.Sheet s, int first, int last, char[] cols, double[] cached,
                                  char countCol, int count, char pctCol, double pct) {
        if (last < first) return;
        XlsxWriter.Row r = s.row();
        int n = r.num();
        r.text("TOTAL", S_TOTAL_LABEL);
        for (int i = 0; i < cols.length; i++) {
            r.formula("SUM(" + cols[i] + first + ":" + cols[i] + last + ")", cached[i], S_TOTAL_MONEY);
        }
        r.formula("IF(B" + n + ">0,D" + n + "/B" + n + ",0)", pct, S_TOTAL_PCT);
        r.formula("SUM(" + countCol + first + ":" + countCol + last + ")", count, S_TOTAL_LABEL);
    }

    private static void categoryTable(XlsxWriter.Sheet s, List<Txn> txns, boolean income, String q) {
        String type = income ? "Income" : "Expense";
        String amtCol = income ? "G" : "H";
        Map<String, long[]> m = new LinkedHashMap<>();
        long total = 0;
        for (Txn t : txns) {
            if (t.income != income) continue;
            long[] a = m.computeIfAbsent(t.category, k -> new long[2]);
            a[0] += t.paise;
            a[1]++;
            total += t.paise;
        }
        List<Map.Entry<String, long[]>> list = new ArrayList<>(m.entrySet());
        Collections.sort(list, (x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));

        s.row().height(20).text(income ? "Income by Category" : "Expenditure by Category", S_SECTION);
        s.row().text("Category", S_HEADER).text("Amount (₹)", S_HEADER).text("% Share", S_HEADER).text("Entries", S_HEADER);
        if (list.isEmpty()) {
            s.row().text(income ? "No income in this period" : "No expenditure in this period", S_SUBTITLE);
            return;
        }
        int first = s.nextRowNum();
        int totalRow = first + list.size();
        for (Map.Entry<String, long[]> e : list) {
            XlsxWriter.Row r = s.row();
            int n = r.num();
            long amt = e.getValue()[0];
            r.text(e.getKey(), S_TEXT)
                    .formula("SUMIFS(" + q + "!$" + amtCol + ":$" + amtCol + "," + q + "!$E:$E,A" + n + "," + q
                            + "!$D:$D,\"" + type + "\")", rs(amt), income ? S_IN_MONEY : S_OUT_MONEY)
                    .formula("IF($B$" + totalRow + "=0,0,B" + n + "/$B$" + totalRow + ")",
                            total == 0 ? 0 : (double) amt / total, S_PCT)
                    .formula("COUNTIFS(" + q + "!$E:$E,A" + n + "," + q + "!$D:$D,\"" + type + "\")",
                            e.getValue()[1], S_CENTER);
        }
        int last = totalRow - 1;
        long cnt = 0;
        for (Map.Entry<String, long[]> e : list) cnt += e.getValue()[1];
        s.row().text("Total", S_TOTAL_LABEL)
                .formula("SUM(B" + first + ":B" + last + ")", rs(total), S_TOTAL_MONEY)
                .formula("SUM(C" + first + ":C" + last + ")", total == 0 ? 0 : 1, S_TOTAL_PCT)
                .formula("SUM(D" + first + ":D" + last + ")", cnt, S_TOTAL_LABEL);
    }

    private static void modeTable(XlsxWriter.Sheet s, List<Txn> txns, String q) {
        Map<String, long[]> m = new LinkedHashMap<>();
        for (String mode : Categories.modes()) m.put(mode, new long[3]);
        for (Txn t : txns) {
            long[] a = m.computeIfAbsent(t.mode == null ? "Cash" : t.mode, k -> new long[3]);
            if (t.income) a[0] += t.paise; else a[1] += t.paise;
            a[2]++;
        }
        s.row().height(20).text("Payment Mode-wise", S_SECTION);
        s.row().text("Mode", S_HEADER).text("Income (₹)", S_HEADER).text("Expenditure (₹)", S_HEADER).text("Entries", S_HEADER);
        int first = s.nextRowNum();
        long ti = 0, to = 0, tc = 0;
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            long[] a = e.getValue();
            if (a[2] == 0) continue;
            ti += a[0]; to += a[1]; tc += a[2];
            XlsxWriter.Row r = s.row();
            int n = r.num();
            r.text(e.getKey(), S_TEXT)
                    .formula("SUMIFS(" + q + "!$G:$G," + q + "!$F:$F,A" + n + ")", rs(a[0]), S_IN_MONEY)
                    .formula("SUMIFS(" + q + "!$H:$H," + q + "!$F:$F,A" + n + ")", rs(a[1]), S_OUT_MONEY)
                    .formula("COUNTIFS(" + q + "!$F:$F,A" + n + ")", a[2], S_CENTER);
        }
        int last = s.nextRowNum() - 1;
        if (last < first) {
            s.row().text("No entries in this period", S_SUBTITLE);
            return;
        }
        s.row().text("Total", S_TOTAL_LABEL)
                .formula("SUM(B" + first + ":B" + last + ")", rs(ti), S_TOTAL_MONEY)
                .formula("SUM(C" + first + ":C" + last + ")", rs(to), S_TOTAL_MONEY)
                .formula("SUM(D" + first + ":D" + last + ")", tc, S_TOTAL_LABEL);
    }
}
