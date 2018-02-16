package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

/**
 * Created by Wee Dingwall on 23/01/2017.
 */

public class MutedWordsDatabase extends SQLiteOpenHelper {
    private static String WORDS = "words";


    public MutedWordsDatabase(Context context) {
        super(context, "FILTERED_WORDS", null, 1);
    }


    public ArrayList<String> getWords() {
        Cursor cursor = null;
        ArrayList<String> strings = new ArrayList<String>();
        try {
            cursor = getReadableDatabase().rawQuery("SELECT * FROM  " + WORDS, null);
            while (cursor.moveToNext()) strings.add(extractMessageEntity(cursor));
        } finally {
            if (cursor != null) cursor.close();
        }
        return strings;
    }

    private String extractMessageEntity(Cursor cursor) {
        return getStringValueByKey(cursor, "word");
    }

    private String getStringValueByKey(Cursor cursor, String word) {
        int index = cursor.getColumnIndex(word);
        return cursor.getString(index);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table " + WORDS + " (" +
                "id integer primary key autoincrement, " +
                "word text  non null" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void insertWord(String word) {
        ContentValues cv = new ContentValues();
        cv.put("word", word);

        getWritableDatabase().insert(WORDS, null, cv);
    }

    public void clearDatabase() {
        SQLiteDatabase writableDatabase = getWritableDatabase();
        writableDatabase.delete(WORDS, null, null);


    }

    public void removeWord(String s) {
        getWritableDatabase().execSQL("DELETE FROM " + WORDS + " WHERE word = ?"
                , new String[]{s});
    }
}