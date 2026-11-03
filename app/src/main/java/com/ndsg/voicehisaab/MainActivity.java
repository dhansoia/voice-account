package com.ndsg.voicehisaab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_VOICE = "com.ndsg.voicehisaab.VOICE";
    public static final String ACTION_REPORT = "com.ndsg.voicehisaab.REPORT";
    private static final int REQ_VOICE = 41;
    private static final int M_REPORT = 1, M_ADD = 2, M_SETTINGS = 3, M_HELP = 4, M_TYPE = 5;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);

    private Db db;
    private Prefs prefs;
    private YearMonth month = YearMonth.now();
    private TextView tvMonth, tvIn, tvOut, tvBal, tvToday, tvEmpty;
    private ListView list;
    private final Adapter adapter = new Adapter();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Db.get(this);
        prefs = new Prefs(this);
        if (state != null && state.getString("month") != null) month = YearMonth.parse(state.getString("month"));
        setContentView(buildUi());
        if (getActionBar() != null) getActionBar().setSubtitle("v" + version());
        refresh();
        handleIntent(getIntent(), state == null);
        if (!prefs.helpShown() && state == null) {
            prefs.helpShown(true);
            showHelp();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("month", month.toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void handleIntent(Intent i, boolean fresh) {
        if (!fresh || i == null) return;
        if (ACTION_VOICE.equals(i.getAction())) {
            list.post(this::startVoice);
        } else if (ACTION_REPORT.equals(i.getAction())) {
            startActivity(new Intent(this, ReportActivity.class));
        }
    }

    // ------------------------------------------------------------------ UI
    private View buildUi() {
        int p = Ui.dp(this, 16);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout col = Ui.vbox(this);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // Summary card
        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.rounded(Ui.NAVY, Ui.dp(this, 16)));
        card.setPadding(p, Ui.dp(this, 10), p, p);
        LinearLayout.LayoutParams clp = Ui.lp(-1, -2);
        clp.setMargins(p, Ui.dp(this, 12), p, Ui.dp(this, 6));
        col.addView(card, clp);

        LinearLayout nav = Ui.hbox(this);
        TextView prev = navArrow("‹");
        prev.setOnClickListener(v -> { month = month.minusMonths(1); refresh(); });
        TextView next = navArrow("›");
        next.setOnClickListener(v -> { month = month.plusMonths(1); refresh(); });
        tvMonth = Ui.text(this, "", 17, Color.WHITE, true);
        tvMonth.setGravity(Gravity.CENTER);
        tvMonth.setOnClickListener(v -> { month = YearMonth.now(); refresh(); });
        nav.addView(prev);
        nav.addView(tvMonth, Ui.weight(1));
        nav.addView(next);
        card.addView(nav);

        LinearLayout nums = Ui.hbox(this);
        nums.setPadding(0, Ui.dp(this, 8), 0, 0);
        tvIn = stat(nums, "Income", Color.parseColor("#9BE3B1"));
        tvOut = stat(nums, "Expense", Color.parseColor("#FFB4AB"));
        tvBal = stat(nums, "Balance", Color.WHITE);
        card.addView(nums);

        tvToday = Ui.text(this, "", 13, Color.parseColor("#C9D6E6"), false);
        tvToday.setPadding(0, Ui.dp(this, 10), 0, 0);
        tvToday.setGravity(Gravity.CENTER);
        card.addView(tvToday);

        // List
        list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(p, 0, p, Ui.dp(this, 120));
        list.setAdapter(adapter);
        list.setOnItemClickListener((AdapterView<?> parent, View v, int pos, long id) -> {
            Object o = adapter.getItem(pos);
            if (o instanceof Txn) EntryDialog.show(this, (Txn) o, null, this::refresh, null);
        });
        list.setOnItemLongClickListener((parent, v, pos, id) -> {
            Object o = adapter.getItem(pos);
            if (!(o instanceof Txn)) return false;
            Txn t = (Txn) o;
            new AlertDialog.Builder(this)
                    .setMessage("Delete " + (t.income ? "income " : "expense ") + Money.fmt(t.paise) + " (" + t.category + ")?")
                    .setPositiveButton("Delete", (d, w) -> { db.delete(t.id); refresh(); })
                    .setNegativeButton("Cancel", null).show();
            return true;
        });
        FrameLayout listWrap = new FrameLayout(this);
        listWrap.addView(list, new FrameLayout.LayoutParams(-1, -1));
        tvEmpty = Ui.text(this, "No entries this month.\n\nTap the mic and say, for example:\n“Petrol 500 UPI se”\n“Salary 85000 mili”",
                15, Ui.MUTED, false);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 120));
        listWrap.addView(tvEmpty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(listWrap, new LinearLayout.LayoutParams(-1, 0, 1));

        // Bottom: big mic + manual add
        LinearLayout bottom = Ui.hbox(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(p, 0, p, Ui.dp(this, 20));

        ImageButton mic = new ImageButton(this);
        mic.setImageResource(R.drawable.ic_mic);
        mic.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x44FFFFFF), oval(Ui.GREEN), null));
        mic.setElevation(Ui.dp(this, 6));
        mic.setContentDescription("Speak an entry");
        mic.setOnClickListener(v -> startVoice());
        int micSize = Ui.dp(this, 76);

        ImageButton add = new ImageButton(this);
        add.setImageResource(R.drawable.ic_add);
        add.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x44FFFFFF), oval(Ui.NAVY), null));
        add.setElevation(Ui.dp(this, 4));
        add.setContentDescription("Add manually");
        add.setOnClickListener(v -> addManual());
        int addSize = Ui.dp(this, 52);

        View spacerL = new View(this);
        bottom.addView(spacerL, Ui.lp(addSize, addSize));
        LinearLayout.LayoutParams mlp = Ui.lp(micSize, micSize);
        mlp.setMargins(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);
        bottom.addView(mic, mlp);
        bottom.addView(add, Ui.lp(addSize, addSize));

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, blp);
        return root;
    }

    private android.graphics.drawable.GradientDrawable oval(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private TextView navArrow(String s) {
        TextView t = Ui.text(this, s, 28, Color.WHITE, true);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        return t;
    }

    private TextView stat(LinearLayout parent, String label, int color) {
        LinearLayout b = Ui.vbox(this);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(Ui.text(this, label, 12, Color.parseColor("#C9D6E6"), false));
        TextView v = Ui.text(this, "₹0", 18, color, true);
        v.setSingleLine(true);
        b.addView(v);
        parent.addView(b, Ui.weight(1));
        return v;
    }

    // ------------------------------------------------------------------ data
    private void refresh() {
        if (list == null) return;
        LocalDate from = month.atDay(1), to = month.atEndOfMonth();
        List<Txn> txns = db.range(from, to, false);
        long in = 0, out = 0;
        for (Txn t : txns) { if (t.income) in += t.paise; else out += t.paise; }
        tvMonth.setText(month.format(MONTH) + (month.equals(YearMonth.now()) ? "" : "  ↺"));
        tvIn.setText(Money.fmt(in));
        tvOut.setText(Money.fmt(out));
        tvBal.setText(Money.fmt(in - out));

        LocalDate today = LocalDate.now();
        long ti = 0, to2 = 0;
        for (Txn t : db.range(today, today, true)) { if (t.income) ti += t.paise; else to2 += t.paise; }
        tvToday.setText("Today:  in " + Money.fmt(ti) + "   ·   out " + Money.fmt(to2)
                + "   ·   overall balance " + Money.fmt(db.balanceBefore(today.plusDays(1))));

        List<Object> rows = new ArrayList<>();
        LocalDate cur = null;
        for (Txn t : txns) {
            if (!t.date.equals(cur)) { cur = t.date; rows.add(cur); }
            rows.add(t);
        }
        adapter.set(rows, txns);
        tvEmpty.setVisibility(txns.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ voice
    private static final int REQ_MIC = 42;

    /** Main mic button: in-app recogniser → Google dialog → type-in, in that order of fallback. */
    private void startVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!VoiceInput.available(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("No speech service")
                    .setMessage("This phone has no speech-to-text service enabled for apps.\n\n"
                            + "Fix: install/update \"Speech Recognition & Synthesis from Google\" and the \"Google\" app "
                            + "from Play Store, and allow the Google app the Microphone permission.\n\n"
                            + "Meanwhile you can type, or dictate with your keyboard's mic.")
                    .setPositiveButton("Type instead", (d, w) -> typeSentence())
                    .setNeutralButton("Google voice", (d, w) -> startGoogleDialog())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        new VoiceInput(this, prefs.language(), new VoiceInput.Callback() {
            @Override public void onHeard(List<String> results) { handleHeard(results); }

            @Override public void onFailed(String message, boolean serviceProblem) {
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Voice")
                        .setMessage(message)
                        .setPositiveButton("Try again", (d, w) -> startVoice())
                        .setNeutralButton("Type instead", (d, w) -> typeSentence());
                if (serviceProblem) b.setNegativeButton("Google voice", (d, w) -> startGoogleDialog());
                else b.setNegativeButton("Cancel", null);
                b.show();
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req != REQ_MIC) return;
        if (grants.length > 0 && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startVoice();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Microphone permission")
                    .setMessage("Voice entry needs the microphone. Allow it in Settings › Apps › Voice Hisaab › Permissions, "
                            + "or type your entry instead.")
                    .setPositiveButton("Type instead", (d, w) -> typeSentence())
                    .setNeutralButton("Open settings", (d, w) -> {
                        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", getPackageName(), null));
                        startActivity(i);
                    })
                    .setNegativeButton("Cancel", null).show();
        }
    }

    /** Fallback: Google's own voice pop-up. */
    private void startGoogleDialog() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.language());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye… e.g. “Petrol 500 UPI se”");
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            typeSentence();
        }
    }

    /** Last resort that always works: type (or dictate with the keyboard's mic) a sentence. */
    private void typeSentence() {
        int p = Ui.dp(this, 20);
        LinearLayout box = Ui.vbox(this);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("e.g. Petrol 500 UPI se");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(et);
        TextView tip = Ui.text(this, "Tip: tap the mic on your keyboard to dictate.", 13, Ui.MUTED, false);
        tip.setPadding(0, Ui.dp(this, 6), 0, 0);
        box.addView(tip);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Type an entry")
                .setView(box)
                .setPositiveButton("OK", (x, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) handleHeard(java.util.Collections.singletonList(s));
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        et.requestFocus();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VOICE) return;
        if (res != RESULT_OK || data == null) {
            new AlertDialog.Builder(this)
                    .setMessage("Google voice didn't return any text.")
                    .setPositiveButton("Type instead", (d, w) -> typeSentence())
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "Didn't catch that. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        handleHeard(results);
    }

    private void handleHeard(List<String> results) {
        LocalDate today = LocalDate.now();
        String heard = results.get(0);
        VoiceParser.Result r = VoiceParser.parse(heard, today);
        if (r.paise <= 0) {
            for (String alt : results) {
                VoiceParser.Result ar = VoiceParser.parse(alt, today);
                if (ar.paise > 0) { heard = alt; r = ar; break; }
            }
        }
        Txn t = new Txn();
        t.income = r.income;
        t.paise = r.paise;
        t.category = r.category;
        t.mode = r.mode != null ? r.mode : prefs.defaultMode();
        t.date = r.date;
        t.note = r.note;
        t.heard = heard;

        if (prefs.confirm() || t.paise <= 0) {
            EntryDialog.show(this, t, heard, () -> {
                month = YearMonth.from(t.date);
                refresh();
            }, this::startVoice);
        } else {
            db.insert(t);
            month = YearMonth.from(t.date);
            refresh();
            Toast.makeText(this, (t.income ? "Income " : "Expense ") + Money.fmt(t.paise) + " · " + t.category
                    + " saved. Tap the entry to edit.", Toast.LENGTH_LONG).show();
        }
    }

    private void addManual() {
        Txn t = new Txn();
        t.income = false;
        t.date = LocalDate.now();
        t.mode = prefs.defaultMode();
        t.category = Categories.OTHER_EXPENSE;
        EntryDialog.show(this, t, null, this::refresh, null);
    }

    // ------------------------------------------------------------------ menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, M_REPORT, 0, "Excel Report").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(0, M_ADD, 1, "Add manually").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_SETTINGS, 2, "Settings").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_TYPE, 3, "Type an entry").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_HELP, 4, "How to speak").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case M_REPORT: startActivity(new Intent(this, ReportActivity.class)); return true;
            case M_ADD: addManual(); return true;
            case M_SETTINGS: showSettings(); return true;
            case M_HELP: showHelp(); return true;
            case M_TYPE: typeSentence(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettings() {
        int p = Ui.dp(this, 20);
        LinearLayout box = Ui.vbox(this);
        box.setPadding(p, Ui.dp(this, 8), p, 0);

        box.addView(Ui.text(this, "Voice language", 13, Ui.MUTED, true));
        RadioGroup lang = new RadioGroup(this);
        RadioButton hi = new RadioButton(this);
        hi.setId(View.generateViewId());
        hi.setText("Hindi + English mix (Hinglish)");
        RadioButton en = new RadioButton(this);
        en.setId(View.generateViewId());
        en.setText("English (India)");
        lang.addView(hi);
        lang.addView(en);
        lang.check("en-IN".equals(prefs.language()) ? en.getId() : hi.getId());
        box.addView(lang);

        CheckBox confirm = new CheckBox(this);
        confirm.setText("Show confirmation before saving a voice entry");
        confirm.setChecked(prefs.confirm());
        confirm.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        box.addView(confirm);

        box.addView(Ui.text(this, "Default payment mode (when not spoken)", 13, Ui.MUTED, true));
        Spinner mode = new Spinner(this);
        List<String> modes = Categories.modes();
        ArrayAdapter<String> ma = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        ma.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(ma);
        mode.setSelection(Math.max(0, modes.indexOf(prefs.defaultMode())));
        box.addView(mode);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.language(lang.getCheckedRadioButtonId() == en.getId() ? "en-IN" : "hi-IN");
                    prefs.confirm(confirm.isChecked());
                    prefs.defaultMode((String) mode.getSelectedItem());
                    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHelp() {
        String msg = "Tap the green mic and speak naturally — Hindi, English or both.\n\n"
                + "Expenses\n"
                + "•  Petrol 500 UPI se\n"
                + "•  Doodh 60 rupaye cash\n"
                + "•  Bijli ka bill 3450 bhara\n"
                + "•  Kal dawai pe 1200 kharch kiye\n"
                + "•  Do hazar paanch sau ka ration\n"
                + "•  पेट्रोल के लिए 500 रुपये दिए\n\n"
                + "Income\n"
                + "•  Salary 85000 mili bank mein\n"
                + "•  Consultancy fees 50000 mili NEFT\n"
                + "•  किराया 15000 मिला\n\n"
                + "Tips\n"
                + "•  Words like mila / received / aaya mark INCOME; kharch / diya / paid / bhara mark EXPENSE.\n"
                + "•  Say kal (yesterday), parson, or “5 tarikh” to change the date.\n"
                + "•  Say cash, UPI, PhonePe, card or bank for the payment mode.\n"
                + "•  You can always correct the entry before saving. Tap any entry later to edit; long-press to delete.\n"
                + "•  Excel Report (top right) exports month, financial-year or custom-period reports.";
        new AlertDialog.Builder(this).setTitle("How to speak  ·  v" + version()).setMessage(msg).setPositiveButton("Got it", null).show();
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ------------------------------------------------------------------ list adapter
    private final class Adapter extends BaseAdapter {
        private List<Object> rows = new ArrayList<>();
        private List<Txn> txns = new ArrayList<>();

        void set(List<Object> r, List<Txn> t) {
            rows = r;
            txns = t;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { Object o = rows.get(i); return o instanceof Txn ? ((Txn) o).id : -i - 1; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return rows.get(i) instanceof Txn ? 1 : 0; }
        @Override public boolean isEnabled(int i) { return rows.get(i) instanceof Txn; }
        @Override public boolean areAllItemsEnabled() { return false; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Object o = rows.get(pos);
            if (o instanceof LocalDate) {
                LocalDate d = (LocalDate) o;
                TextView h = cv instanceof TextView ? (TextView) cv : Ui.text(MainActivity.this, "", 13, Ui.MUTED, true);
                h.setPadding(Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 14), 0, Ui.dp(MainActivity.this, 6));
                long net = 0;
                for (Txn t : txns) if (t.date.equals(d)) net += t.signedPaise();
                String label = d.equals(LocalDate.now()) ? "Today" : d.equals(LocalDate.now().minusDays(1)) ? "Yesterday" : d.format(DAY);
                h.setText(label + "   ·   net " + Money.fmt(net));
                return h;
            }
            Txn t = (Txn) o;
            RowHolder rh;
            if (cv != null && cv.getTag() instanceof RowHolder) {
                rh = (RowHolder) cv.getTag();
            } else {
                rh = new RowHolder();
                cv = rh.build();
            }
            rh.bar.setBackgroundColor(t.income ? Ui.GREEN : Ui.RED);
            rh.cat.setText(t.category);
            String sub = t.mode + (t.note == null || t.note.isEmpty() ? "" : "  ·  " + t.note);
            rh.sub.setText(sub);
            rh.amt.setText((t.income ? "+ " : "− ") + Money.fmt(t.paise));
            rh.amt.setTextColor(t.income ? Ui.GREEN : Ui.RED);
            return cv;
        }
    }

    private final class RowHolder {
        View bar;
        TextView cat, sub, amt;

        View build() {
            LinearLayout row = Ui.hbox(MainActivity.this);
            row.setBackground(Ui.rounded(Ui.CARD, Ui.dp(MainActivity.this, 12)));
            row.setPadding(0, 0, Ui.dp(MainActivity.this, 14), 0);
            row.setMinimumHeight(Ui.dp(MainActivity.this, 62));
            bar = new View(MainActivity.this);
            LinearLayout.LayoutParams blp = Ui.lp(Ui.dp(MainActivity.this, 5), Ui.dp(MainActivity.this, 40));
            blp.setMargins(Ui.dp(MainActivity.this, 8), 0, Ui.dp(MainActivity.this, 12), 0);
            row.addView(bar, blp);
            LinearLayout mid = Ui.vbox(MainActivity.this);
            mid.setPadding(0, Ui.dp(MainActivity.this, 10), Ui.dp(MainActivity.this, 8), Ui.dp(MainActivity.this, 10));
            cat = Ui.text(MainActivity.this, "", 15, Ui.TEXT, true);
            sub = Ui.text(MainActivity.this, "", 13, Ui.MUTED, false);
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(cat);
            mid.addView(sub);
            row.addView(mid, Ui.weight(1));
            amt = Ui.text(MainActivity.this, "", 16, Ui.TEXT, true);
            amt.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(amt);

            LinearLayout wrap = Ui.vbox(MainActivity.this);
            wrap.setPadding(0, Ui.dp(MainActivity.this, 3), 0, Ui.dp(MainActivity.this, 3));
            wrap.addView(row, Ui.lp(-1, -2));
            wrap.setTag(this);
            return wrap;
        }
    }
}
