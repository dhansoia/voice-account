package com.ndsg.voicehisaab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Add / confirm / edit dialog for one entry. */
public final class EntryDialog {
    private EntryDialog() {}

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    /**
     * @param t        entry to edit, or a new (id 0) pre-filled entry
     * @param heard    the spoken sentence, or null for manual entry
     * @param onSaved  called after save or delete
     * @param onRetry  "Speak again" action for voice entries (may be null)
     */
    public static void show(final Activity a, final Txn t, final String heard, final Runnable onSaved,
                            final Runnable onRetry) {
        final boolean editing = t.id > 0;
        final int pad = Ui.dp(a, 20);

        LinearLayout box = Ui.vbox(a);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);

        if (heard != null) {
            TextView h = Ui.text(a, "“" + heard + "”", 15, Ui.MUTED, false);
            h.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
            h.setPadding(0, 0, 0, Ui.dp(a, 10));
            box.addView(h);
        }

        final RadioGroup type = new RadioGroup(a);
        type.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbOut = new RadioButton(a);
        rbOut.setId(View.generateViewId());
        rbOut.setText("Expense");
        rbOut.setTextColor(Ui.RED);
        final RadioButton rbIn = new RadioButton(a);
        rbIn.setId(View.generateViewId());
        rbIn.setText("Income");
        rbIn.setTextColor(Ui.GREEN);
        type.addView(rbOut);
        type.addView(rbIn);
        type.check(t.income ? rbIn.getId() : rbOut.getId());
        box.addView(type);

        box.addView(label(a, "Amount (₹)"));
        final EditText amount = new EditText(a);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(22);
        amount.setTypeface(Typeface.DEFAULT_BOLD);
        amount.setHint("0");
        if (t.paise > 0) amount.setText(Money.plain(t.paise));
        box.addView(amount);

        box.addView(label(a, "Category"));
        final Spinner cat = new Spinner(a);
        box.addView(cat);

        box.addView(label(a, "Paid / received by"));
        final Spinner mode = new Spinner(a);
        List<String> modes = Categories.modes();
        ArrayAdapter<String> ma = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, modes);
        ma.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(ma);
        int mi = modes.indexOf(t.mode);
        mode.setSelection(Math.max(mi, 0));
        box.addView(mode);

        box.addView(label(a, "Date"));
        final LocalDate[] date = {t.date == null ? LocalDate.now() : t.date};
        final Button dateBtn = new Button(a);
        dateBtn.setAllCaps(false);
        dateBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        dateBtn.setText(date[0].format(FMT));
        dateBtn.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(a, (view, y, m, d) -> {
                date[0] = LocalDate.of(y, m + 1, d);
                dateBtn.setText(date[0].format(FMT));
            }, date[0].getYear(), date[0].getMonthValue() - 1, date[0].getDayOfMonth());
            dp.show();
        });
        box.addView(dateBtn);

        box.addView(label(a, "Description"));
        final EditText note = new EditText(a);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setMaxLines(3);
        note.setText(t.note == null ? "" : t.note);
        box.addView(note);

        final Runnable fillCategories = () -> {
            boolean inc = type.getCheckedRadioButtonId() == rbIn.getId();
            List<String> names = Categories.names(inc);
            ArrayAdapter<String> ca = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, names);
            ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            cat.setAdapter(ca);
            int ci = names.indexOf(t.category);
            if (ci < 0 && inc != t.income) {
                // Type was switched: re-guess the category from the spoken words for the new type.
                if (heard != null) {
                    VoiceParser.Result r = VoiceParser.parse(heard, LocalDate.now());
                    String guess = r.income == inc ? r.category : null;
                    ci = guess == null ? -1 : names.indexOf(guess);
                }
            }
            cat.setSelection(ci >= 0 ? ci : names.size() - 1);
        };
        fillCategories.run();
        type.setOnCheckedChangeListener((g, id) -> fillCategories.run());

        ScrollView sv = new ScrollView(a);
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(editing ? "Edit entry" : heard != null ? "Confirm entry" : "New entry")
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (editing) b.setNeutralButton("Delete", null);
        else if (onRetry != null) b.setNeutralButton("Speak again", null);

        final AlertDialog d = b.create();
        d.setOnShowListener(di -> {
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                long p = Money.parse(amount.getText().toString());
                if (p <= 0) {
                    amount.setError("Enter a valid amount");
                    amount.requestFocus();
                    return;
                }
                t.income = type.getCheckedRadioButtonId() == rbIn.getId();
                t.paise = p;
                t.category = (String) cat.getSelectedItem();
                t.mode = (String) mode.getSelectedItem();
                t.date = date[0];
                t.note = note.getText().toString().trim();
                if (heard != null && t.heard == null) t.heard = heard;
                Db db = Db.get(a);
                if (editing) db.update(t); else db.insert(t);
                Toast.makeText(a, (t.income ? "Income " : "Expense ") + Money.fmt(t.paise) + " saved",
                        Toast.LENGTH_SHORT).show();
                d.dismiss();
                if (onSaved != null) onSaved.run();
            });
            Button neutral = d.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neutral != null) {
                if (editing) {
                    neutral.setTextColor(Ui.RED);
                    neutral.setOnClickListener(v -> new AlertDialog.Builder(a)
                            .setMessage("Delete this entry of " + Money.fmt(t.paise) + "?")
                            .setPositiveButton("Delete", (x, y) -> {
                                Db.get(a).delete(t.id);
                                d.dismiss();
                                if (onSaved != null) onSaved.run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show());
                } else {
                    neutral.setOnClickListener(v -> {
                        d.dismiss();
                        onRetry.run();
                    });
                }
            }
            if (t.paise <= 0) amount.requestFocus();
        });
        d.show();
    }

    private static TextView label(Activity a, String s) {
        TextView l = Ui.text(a, s, 12, Ui.MUTED, true);
        l.setPadding(0, Ui.dp(a, 10), 0, 0);
        return l;
    }
}
