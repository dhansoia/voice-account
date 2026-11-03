package com.ndsg.voicehisaab;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Local SQLite storage. Data never leaves the phone except through the Excel export. */
public class Db extends SQLiteOpenHelper {
    private static final String NAME = "hisaab.db";
    private static final int VERSION = 1;
    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE txn (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "d TEXT NOT NULL," +
                "inc INTEGER NOT NULL," +
                "paise INTEGER NOT NULL," +
                "cat TEXT NOT NULL," +
                "mode TEXT NOT NULL," +
                "note TEXT," +
                "heard TEXT," +
                "created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_txn_d ON txn(d)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // Version 1 – nothing to migrate yet.
    }

    private static ContentValues values(Txn t) {
        ContentValues v = new ContentValues();
        v.put("d", t.date.toString());
        v.put("inc", t.income ? 1 : 0);
        v.put("paise", t.paise);
        v.put("cat", t.category);
        v.put("mode", t.mode == null ? "Cash" : t.mode);
        v.put("note", t.note);
        v.put("heard", t.heard);
        v.put("created", t.created == 0 ? System.currentTimeMillis() : t.created);
        return v;
    }

    public long insert(Txn t) {
        t.id = getWritableDatabase().insertOrThrow("txn", null, values(t));
        return t.id;
    }

    public void update(Txn t) {
        getWritableDatabase().update("txn", values(t), "_id=?", new String[]{Long.toString(t.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete("txn", "_id=?", new String[]{Long.toString(id)});
    }

    /** Entries with from ≤ date ≤ to. */
    public List<Txn> range(LocalDate from, LocalDate to, boolean oldestFirst) {
        String order = oldestFirst ? "d ASC, _id ASC" : "d DESC, _id DESC";
        List<Txn> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("txn", null, "d>=? AND d<=?",
                new String[]{from.toString(), to.toString()}, null, null, order)) {
            int iId = c.getColumnIndexOrThrow("_id"), iD = c.getColumnIndexOrThrow("d"),
                    iInc = c.getColumnIndexOrThrow("inc"), iP = c.getColumnIndexOrThrow("paise"),
                    iCat = c.getColumnIndexOrThrow("cat"), iMode = c.getColumnIndexOrThrow("mode"),
                    iNote = c.getColumnIndexOrThrow("note"), iHeard = c.getColumnIndexOrThrow("heard"),
                    iCr = c.getColumnIndexOrThrow("created");
            while (c.moveToNext()) {
                Txn t = new Txn();
                t.id = c.getLong(iId);
                t.date = LocalDate.parse(c.getString(iD));
                t.income = c.getInt(iInc) == 1;
                t.paise = c.getLong(iP);
                t.category = c.getString(iCat);
                t.mode = c.getString(iMode);
                t.note = c.isNull(iNote) ? "" : c.getString(iNote);
                t.heard = c.isNull(iHeard) ? null : c.getString(iHeard);
                t.created = c.getLong(iCr);
                out.add(t);
            }
        }
        return out;
    }

    /** Net balance (paise) of all entries dated before {@code date}. */
    public long balanceBefore(LocalDate date) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE inc WHEN 1 THEN paise ELSE -paise END),0) FROM txn WHERE d<?",
                new String[]{date.toString()})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    /** Earliest entry date, or null if there are no entries. */
    public LocalDate firstDate() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(d) FROM txn", null)) {
            if (c.moveToFirst() && !c.isNull(0)) return LocalDate.parse(c.getString(0));
        }
        return null;
    }

    public LocalDate lastDate() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MAX(d) FROM txn", null)) {
            if (c.moveToFirst() && !c.isNull(0)) return LocalDate.parse(c.getString(0));
        }
        return null;
    }
}
