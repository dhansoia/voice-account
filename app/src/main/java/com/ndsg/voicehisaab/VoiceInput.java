package com.ndsg.voicehisaab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.speech.RecognitionService;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * In-app speech capture using Android's SpeechRecognizer (no Google pop-up).
 * If Hindi is not available on the phone it retries automatically in English (India).
 */
public final class VoiceInput implements RecognitionListener {

    public interface Callback {
        void onHeard(List<String> results);

        /** Recognition failed; {@code message} is user-friendly. */
        void onFailed(String message, boolean serviceProblem);
    }

    private final Activity a;
    private final Callback cb;
    private String lang;
    private boolean triedFallbackLang = false;
    private SpeechRecognizer sr;
    private AlertDialog dialog;
    private TextView live;
    private ProgressBar level;
    private boolean finished = false;
    private final List<Object> engines = new ArrayList<>(); // "default", ComponentName, or "ondevice"
    private int engine = 0;
    private String engineName = "";

    public VoiceInput(Activity a, String lang, Callback cb) {
        this.a = a;
        this.lang = lang;
        this.cb = cb;
        engines.addAll(engines(a));
    }

    /** Every speech engine on the phone: system default, installed recognition services (Google first), on-device. */
    public static List<Object> engines(Activity a) {
        List<Object> list = new ArrayList<>();
        if (SpeechRecognizer.isRecognitionAvailable(a)) list.add("default");
        try {
            List<ResolveInfo> svcs = a.getPackageManager().queryIntentServices(
                    new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            List<ComponentName> google = new ArrayList<>(), other = new ArrayList<>();
            for (ResolveInfo ri : svcs) {
                if (ri.serviceInfo == null) continue;
                ComponentName cn = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
                if (ri.serviceInfo.packageName.startsWith("com.google")) google.add(cn); else other.add(cn);
            }
            list.addAll(google);
            list.addAll(other);
        } catch (RuntimeException ignored) { }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(a)) list.add("ondevice");
            } catch (RuntimeException ignored) { }
        }
        return list;
    }

    public static boolean available(Activity a) {
        return !engines(a).isEmpty();
    }

    public void start() {
        showDialog();
        listen();
    }

    private void listen() {
        destroyRecognizer();
        finished = false;
        try {
            Object e = engines.get(engine);
            if (e instanceof ComponentName) {
                sr = SpeechRecognizer.createSpeechRecognizer(a, (ComponentName) e);
                engineName = ((ComponentName) e).getPackageName();
            } else if ("ondevice".equals(e) && Build.VERSION.SDK_INT >= 31) {
                sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(a);
                engineName = "on-device";
            } else {
                sr = SpeechRecognizer.createSpeechRecognizer(a);
                engineName = "default";
            }
            sr.setRecognitionListener(this);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, a.getPackageName());
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
            sr.startListening(i);
            if (live != null) live.setText("Listening… (" + ("en-IN".equals(lang) ? "English" : "Hindi + English") + ")");
        } catch (RuntimeException ex) {
            if (!nextEngine()) fail("Voice input could not start on this phone.", true);
        }
    }

    private void showDialog() {
        int p = Ui.dp(a, 20);
        LinearLayout box = Ui.vbox(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(p, p, p, Ui.dp(a, 8));
        live = Ui.text(a, "Starting…", 18, Ui.TEXT, false);
        live.setGravity(Gravity.CENTER);
        live.setMinLines(2);
        box.addView(live, Ui.lp(-1, -2));
        level = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        level.setMax(100);
        LinearLayout.LayoutParams lp = Ui.lp(-1, Ui.dp(a, 10));
        lp.setMargins(0, Ui.dp(a, 16), 0, Ui.dp(a, 8));
        box.addView(level, lp);
        TextView hint = Ui.text(a, "e.g. “Petrol 500 UPI se”  ·  “Salary 85000 mili”", 13, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);

        dialog = new AlertDialog.Builder(a)
                .setTitle("Speak now")
                .setView(box)
                .setPositiveButton("Done", null)
                .setNegativeButton("Cancel", null)
                .setCancelable(true)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (sr != null) sr.stopListening();
            live.setText("Processing…");
        }));
        dialog.setOnDismissListener(d -> {
            if (!finished) {
                finished = true;
                destroyRecognizer();
            }
        });
        dialog.show();
    }

    private void destroyRecognizer() {
        if (sr != null) {
            try { sr.cancel(); } catch (RuntimeException ignored) { }
            try { sr.destroy(); } catch (RuntimeException ignored) { }
            sr = null;
        }
    }

    private void closeDialog() {
        finished = true;
        destroyRecognizer();
        if (dialog != null && dialog.isShowing()) {
            try { dialog.dismiss(); } catch (RuntimeException ignored) { }
        }
    }

    private void fail(String msg, boolean serviceProblem) {
        if (finished) return;
        closeDialog();
        cb.onFailed(msg, serviceProblem);
    }

    /** Try the next engine; after all engines fail in Hindi, start over in English (India). */
    private boolean nextEngine() {
        if (finished) return true;
        if (engine + 1 < engines.size()) {
            engine++;
            listen();
            return true;
        }
        if (!triedFallbackLang && !"en-IN".equals(lang)) {
            triedFallbackLang = true;
            lang = "en-IN";
            engine = 0;
            listen();
            return true;
        }
        return false;
    }

    // ---------------- RecognitionListener ----------------
    @Override public void onReadyForSpeech(Bundle params) { if (live != null) live.setText("Listening… speak now"); }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { if (live != null) live.setText("Processing…"); }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (level != null) level.setProgress((int) Math.max(0, Math.min(100, (rmsdB + 2) * 8)));
    }

    @Override
    public void onPartialResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r != null && !r.isEmpty() && live != null && !r.get(0).trim().isEmpty()) live.setText("“" + r.get(0) + "”");
    }

    @Override
    public void onResults(Bundle b) {
        if (finished) return;
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        closeDialog();
        if (r == null || r.isEmpty() || r.get(0).trim().isEmpty()) {
            cb.onFailed("Didn't catch that. Please try again and speak clearly.", false);
        } else {
            cb.onHeard(r);
        }
    }

    @Override
    public void onError(int error) {
        if (finished) return;
        switch (error) {
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
            case SpeechRecognizer.ERROR_SERVER:
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
            case SpeechRecognizer.ERROR_CLIENT:
            case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT:
                if (nextEngine()) return;
                fail("Speech service is not working on this phone (code " + error + ").", true);
                return;
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                fail("Didn't catch that. Tap the mic and speak again.", false);
                return;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                fail("Voice needs an internet connection. Please check data / Wi-Fi.", false);
                return;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                fail("Microphone permission is needed for voice entry.", false);
                return;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                fail("Voice service is busy. Please try again in a moment.", false);
                return;
            default:
                if (nextEngine()) return;
                fail("Voice input failed (code " + error + ").", true);
        }
    }
}
