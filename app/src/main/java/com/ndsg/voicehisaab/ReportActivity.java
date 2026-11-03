package com.ndsg.voicehisaab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Choose a period and export an Excel (.xlsx) report to Downloads/VoiceHisaab. */
public class ReportActivity extends Activity {
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MON = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private static final int P_THIS_MONTH = 0, P_LAST_MONTH = 1, P_THIS_FY = 2, P_LAST_FY = 3, P_THIS_YEAR = 4,
            P_ALL = 5, P_CUSTOM = 6;

    private int period = P_THIS_MONTH;
    private LocalDate customFrom = LocalDate.now().withDayOfMonth(1), customTo = LocalDate.now();
    private TextView preview;
    private Button fromBtn, toBtn, exportBtn;
    private LinearLayout customRow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Excel Report");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        int p = Ui.dp(this, 16);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(p, p, p, p);
        sv.addView(col);

        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 14)));
        card.setPadding(p, p, p, p);
        col.addView(card, Ui.lp(-1, -2));
        card.addView(Ui.text(this, "Report period", 13, Ui.MUTED, true));

        LocalDate today = LocalDate.now();
        int fyStart = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        String[] labels = {
                "This month (" + YearMonth.now().format(MON) + ")",
                "Last month (" + YearMonth.now().minusMonths(1).format(MON) + ")",
                "This financial year (FY " + fyStart + "-" + String.valueOf(fyStart + 1).substring(2) + ")",
                "Last financial year (FY " + (fyStart - 1) + "-" + String.valueOf(fyStart).substring(2) + ")",
                "This calendar year (" + today.getYear() + ")",
                "All entries",
                "Custom dates"};
        RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            rb.setPadding(rb.getPaddingLeft(), Ui.dp(this, 6), 0, Ui.dp(this, 6));
            rg.addView(rb);
        }
        rg.check(1000 + period);
        rg.setOnCheckedChangeListener((g, id) -> { period = id - 1000; update(); });
        card.addView(rg);

        customRow = Ui.hbox(this);
        fromBtn = new Button(this);
        fromBtn.setAllCaps(false);
        toBtn = new Button(this);
        toBtn.setAllCaps(false);
        fromBtn.setOnClickListener(v -> pick(true));
        toBtn.setOnClickListener(v -> pick(false));
        customRow.addView(fromBtn, Ui.weight(1));
        TextView dash = Ui.text(this, "  to  ", 14, Ui.MUTED, false);
        customRow.addView(dash);
        customRow.addView(toBtn, Ui.weight(1));
        card.addView(customRow);

        preview = Ui.text(this, "", 15, Ui.TEXT, false);
        preview.setBackground(Ui.rounded(Color.parseColor("#E9EEF5"), Ui.dp(this, 12)));
        preview.setPadding(p, p, p, p);
        preview.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams plp = Ui.lp(-1, -2);
        plp.setMargins(0, p, 0, p);
        col.addView(preview, plp);

        exportBtn = Ui.button(this, "Export to Excel", Ui.GREEN, Color.WHITE);
        exportBtn.setOnClickListener(v -> export());
        col.addView(exportBtn, Ui.lp(-1, Ui.dp(this, 54)));

        TextView info = Ui.text(this,
                "The workbook has 5 sheets: Summary (income, expenditure, net, category & payment-mode totals), "
                        + "Transactions (full register with running balance), Monthly, Daily and Category × Month. "
                        + "Files are saved in Downloads › VoiceHisaab and can be opened in Excel, Google Sheets or WPS.",
                13, Ui.MUTED, false);
        info.setPadding(0, p, 0, 0);
        info.setGravity(Gravity.START);
        col.addView(info);

        setContentView(sv);
        update();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void pick(boolean from) {
        LocalDate d = from ? customFrom : customTo;
        new DatePickerDialog(this, (v, y, m, day) -> {
            LocalDate n = LocalDate.of(y, m + 1, day);
            if (from) customFrom = n; else customTo = n;
            if (customFrom.isAfter(customTo)) {
                LocalDate x = customFrom; customFrom = customTo; customTo = x;
            }
            update();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    /** [from, to, label] for the selected period. */
    private Object[] range() {
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.now();
        int fy = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        switch (period) {
            case P_LAST_MONTH: {
                YearMonth l = ym.minusMonths(1);
                return new Object[]{l.atDay(1), l.atEndOfMonth(), l.format(MON)};
            }
            case P_THIS_FY:
                return new Object[]{LocalDate.of(fy, 4, 1), LocalDate.of(fy + 1, 3, 31),
                        "FY " + fy + "-" + String.valueOf(fy + 1).substring(2)};
            case P_LAST_FY:
                return new Object[]{LocalDate.of(fy - 1, 4, 1), LocalDate.of(fy, 3, 31),
                        "FY " + (fy - 1) + "-" + String.valueOf(fy).substring(2)};
            case P_THIS_YEAR:
                return new Object[]{LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31),
                        "Year " + today.getYear()};
            case P_ALL: {
                Db db = Db.get(this);
                LocalDate f = db.firstDate(), l = db.lastDate();
                if (f == null) f = today.withDayOfMonth(1);
                if (l == null || l.isBefore(today)) l = today;
                return new Object[]{f, l, "All entries"};
            }
            case P_CUSTOM:
                return new Object[]{customFrom, customTo, "Custom"};
            default:
                return new Object[]{ym.atDay(1), ym.atEndOfMonth(), ym.format(MON)};
        }
    }

    private void update() {
        customRow.setVisibility(period == P_CUSTOM ? View.VISIBLE : View.GONE);
        fromBtn.setText(customFrom.format(D));
        toBtn.setText(customTo.format(D));
        Object[] r = range();
        LocalDate from = (LocalDate) r[0], to = (LocalDate) r[1];
        Db db = Db.get(this);
        List<Txn> txns = db.range(from, to, true);
        long in = 0, out = 0;
        for (Txn t : txns) { if (t.income) in += t.paise; else out += t.paise; }
        long opening = db.balanceBefore(from);
        preview.setText(from.format(D) + "  →  " + to.format(D) + "\n"
                + "Entries: " + txns.size() + "\n"
                + "Opening balance: " + Money.fmt(opening) + "\n"
                + "Income: " + Money.fmt(in) + "\n"
                + "Expenditure: " + Money.fmt(out) + "\n"
                + "Net: " + Money.fmt(in - out) + "\n"
                + "Closing balance: " + Money.fmt(opening + in - out));
    }

    private void export() {
        Object[] r = range();
        final LocalDate from = (LocalDate) r[0], to = (LocalDate) r[1];
        final String label = (String) r[2];
        exportBtn.setEnabled(false);
        exportBtn.setText("Creating report…");
        new Thread(() -> {
            Uri uri = null;
            String error = null;
            String name = "Hisaab_" + label.replaceAll("[^A-Za-z0-9]+", "_") + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
            try {
                Db db = Db.get(this);
                List<Txn> txns = db.range(from, to, true);
                long opening = db.balanceBefore(from);
                XlsxWriter wb = ReportBuilder.build(txns, from, to, label, opening, LocalDateTime.now());

                ContentResolver cr = getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, MIME);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VoiceHisaab");
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("Could not create file in Downloads");
                try (OutputStream os = cr.openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("Could not open file for writing");
                    wb.write(os);
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, done, null, null);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                if (uri != null) {
                    try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
                    uri = null;
                }
            }
            final Uri result = uri;
            final String err = error;
            runOnUiThread(() -> {
                exportBtn.setEnabled(true);
                exportBtn.setText("Export to Excel");
                if (isFinishing()) return;
                if (result == null) {
                    new AlertDialog.Builder(this).setTitle("Export failed").setMessage(err)
                            .setPositiveButton("OK", null).show();
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Report ready")
                        .setMessage("Saved to Downloads › VoiceHisaab\n\n" + name)
                        .setPositiveButton("Open", (d, w) -> open(result))
                        .setNeutralButton("Share", (d, w) -> share(result))
                        .setNegativeButton("Close", null)
                        .show();
            });
        }).start();
    }

    private void open(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, MIME);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No spreadsheet app found. Install Microsoft Excel, Google Sheets or WPS Office.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void share(Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(MIME);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, "Income & Expenditure Report");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share report"));
    }
}
