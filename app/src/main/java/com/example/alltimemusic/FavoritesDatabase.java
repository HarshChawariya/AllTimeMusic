package com.example.alltimemusic;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Objects;

public class FavoritesDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "favorites.db";
    private static final int DATABASE_VERSION = 3; // Incremented version for lyrics table
    private static final String TABLE_FAVORITES = "favorites";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PATH = "path";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_ARTIST = "artist";
    private static final String COLUMN_ALBUM_ID = "album_id";

    // Lyrics Cache Table
    private static final String TABLE_LYRICS = "lyrics_cache";
    private static final String COLUMN_LYRICS_PLAIN = "plain_lyrics";
    private static final String COLUMN_LYRICS_SYNCED = "synced_lyrics";

    public static ArrayList<musicList_Structure> favoriteList = new ArrayList<>();

    public FavoritesDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        if (favoriteList.isEmpty()) {
            loadFavoritesToList();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createFavoritesTable = "CREATE TABLE " + TABLE_FAVORITES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_PATH + " TEXT UNIQUE, " +
                COLUMN_TITLE + " TEXT, " +
                COLUMN_ARTIST + " TEXT, " +
                COLUMN_ALBUM_ID + " INTEGER)";
        db.execSQL(createFavoritesTable);

        String createLyricsTable = "CREATE TABLE " + TABLE_LYRICS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_PATH + " TEXT UNIQUE, " +
                COLUMN_LYRICS_PLAIN + " TEXT, " +
                COLUMN_LYRICS_SYNCED + " TEXT)";
        db.execSQL(createLyricsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            String createLyricsTable = "CREATE TABLE " + TABLE_LYRICS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PATH + " TEXT UNIQUE, " +
                    COLUMN_LYRICS_PLAIN + " TEXT, " +
                    COLUMN_LYRICS_SYNCED + " TEXT)";
            db.execSQL(createLyricsTable);
        }
    }

    public void saveLyrics(String path, String plain, String synced) {
        // Check if we already have some lyrics for this path BEFORE opening writable DB
        String[] existing = getCachedLyrics(path);
        
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PATH, path);

            if (existing != null) {
                // Smart Merge: If adding plain but synced exists, keep synced and vice versa
                String finalPlain = (plain != null && !plain.isEmpty()) ? plain : existing[0];
                String finalSynced = (synced != null && !synced.isEmpty()) ? synced : existing[1];

                // Ensure we don't save literal "null" strings
                values.put(COLUMN_LYRICS_PLAIN, (finalPlain != null && !finalPlain.equalsIgnoreCase("null")) ? finalPlain : "");
                values.put(COLUMN_LYRICS_SYNCED, (finalSynced != null && !finalSynced.equalsIgnoreCase("null")) ? finalSynced : "");
            } else {
                values.put(COLUMN_LYRICS_PLAIN, (plain != null && !plain.equalsIgnoreCase("null")) ? plain : "");
                values.put(COLUMN_LYRICS_SYNCED, (synced != null && !synced.equalsIgnoreCase("null")) ? synced : "");
            }

            db.insertWithOnConflict(TABLE_LYRICS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    public String[] getCachedLyrics(String path) {
        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.query(TABLE_LYRICS, new String[]{COLUMN_LYRICS_PLAIN, COLUMN_LYRICS_SYNCED},
                     COLUMN_PATH + "=?", new String[]{path}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return new String[]{
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LYRICS_PLAIN)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LYRICS_SYNCED))
                };
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadFavoritesToList() {
        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_FAVORITES, null)) {
            if (cursor.moveToFirst()) {
                do {
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PATH));
                    String artist = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ARTIST));
                    long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ALBUM_ID));
                    musicList_Structure song = new musicList_Structure(title, path, artist, albumId);
                    song.isFavourite = true;
                    favoriteList.add(song);
                } while (cursor.moveToNext());
            }
        }
    }

    public void addFavorite(musicList_Structure song) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_PATH, song.songPath);
            values.put(COLUMN_TITLE, song.songTitle);
            values.put(COLUMN_ARTIST, song.artistName);
            values.put(COLUMN_ALBUM_ID, song.albumId);
            db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        if (!isFavorite(song.songPath)) {
            song.isFavourite = true;
            favoriteList.add(song);
        }
    }

    public void removeFavorite(String path) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.delete(TABLE_FAVORITES, COLUMN_PATH + "=?", new String[]{path});
        }

        favoriteList.removeIf(song -> Objects.equals(song.songPath, path));
    }

    public void deleteLyrics(String path) {
        try (SQLiteDatabase db = this.getWritableDatabase()) {
            db.delete(TABLE_LYRICS, COLUMN_PATH + "=?", new String[]{path});
        }
    }

    public boolean isFavorite(String path) {
        for (musicList_Structure song : favoriteList) {
            if (Objects.equals(song.songPath, path)) return true;
        }
        return false;
    }
}
