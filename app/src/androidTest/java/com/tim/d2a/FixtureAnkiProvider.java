package com.tim.d2a;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

/** Runs in the test APK's provider process, so deliberately uses Android/Java only. */
public final class FixtureAnkiProvider extends ContentProvider {
    private String fieldNames = "";
    private int inserted = 0;
    private boolean failMove = true;
    private String lastFields = "";
    private int mediaImports = 0;
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/anki"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor;
        switch (uri.getPath()) {
            case "/models/42":
                cursor = new MatrixCursor(new String[] {"_id", "name", "field_names", "type"});
                cursor.addRow(new Object[] {42, "Dict·Anki Language v2", fieldNames, 0});
                return cursor;
            case "/models/42/templates":
                cursor = new MatrixCursor(new String[] {"ord", "card_template_name", "question_format", "answer_format"});
                cursor.addRow(new Object[] {0, "Recognition", "front", "back"});
                cursor.addRow(new Object[] {1, "Production", "front", "back"});
                return cursor;
            case "/decks":
                cursor = new MatrixCursor(new String[] {"deck_id", "deck_name"});
                cursor.addRow(new Object[] {101, "Spanish"}); return cursor;
            case "/notes/9001":
                cursor = new MatrixCursor(new String[] {"_id"});
                if (inserted > 0) cursor.addRow(new Object[] {9001}); return cursor;
            case "/notes/9001/cards":
                cursor = new MatrixCursor(new String[] {"ord"});
                cursor.addRow(new Object[] {0}); return cursor;
            default: return new MatrixCursor(new String[] {"_id"});
        }
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        if ("/media".equals(uri.getPath())) {
            mediaImports++;
            return Uri.parse("file:///renamed-image.png");
        }
        lastFields = values.getAsString("flds");
        inserted++;
        return Uri.withAppendedPath(uri, "9001");
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (failMove) { failMove = false; return 0; }
        return 1;
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("reset".equals(method)) {
            lastFields = ""; mediaImports = 0;
            inserted = 0; failMove = true; fieldNames = extras == null ? "" : extras.getString("fields", "");
        }
        Bundle result = new Bundle(); result.putInt("inserted", inserted); result.putInt("mediaImports", mediaImports); result.putString("fields", lastFields); return result;
    }
}
