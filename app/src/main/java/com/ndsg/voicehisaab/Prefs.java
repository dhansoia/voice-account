package com.ndsg.voicehisaab;

import android.content.Context;
import android.content.SharedPreferences;

/** User settings. */
public final class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** "hi-IN" understands Hindi + English mixed speech; "en-IN" is English (India). */
    public String language() { return sp.getString("lang", "hi-IN"); }
    public void language(String v) { sp.edit().putString("lang", v).apply(); }

    public boolean confirm() { return sp.getBoolean("confirm", true); }
    public void confirm(boolean v) { sp.edit().putBoolean("confirm", v).apply(); }

    public String defaultMode() { return sp.getString("mode", "Cash"); }
    public void defaultMode(String v) { sp.edit().putString("mode", v).apply(); }

    public boolean helpShown() { return sp.getBoolean("help", false); }
    public void helpShown(boolean v) { sp.edit().putBoolean("help", v).apply(); }
}
