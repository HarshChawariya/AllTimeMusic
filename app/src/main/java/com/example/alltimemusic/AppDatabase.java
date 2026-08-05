package com.example.alltimemusic;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.io.File;

@Database(entities = {SongEntity.class, LyricEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase sInstance;
    private static final String DATABASE_NAME = "music_room.db";

    public abstract MusicDao musicDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() 
                    .build();
            
            // Check and migrate old data once
            migrateOldData(context, sInstance);
        }
        return sInstance;
    }

    private static void migrateOldData(Context context, AppDatabase newDb) {
        File oldDbFile = context.getDatabasePath("favorites.db");
        if (oldDbFile.exists()) {
            try {
                SQLiteDatabase oldDb = SQLiteDatabase.openDatabase(oldDbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                
                // Migrate Favorites
                Cursor favCursor = oldDb.rawQuery("SELECT * FROM favorites", null);
                if (favCursor.moveToFirst()) {
                    do {
                        String path = favCursor.getString(favCursor.getColumnIndexOrThrow("path"));
                        String title = favCursor.getString(favCursor.getColumnIndexOrThrow("title"));
                        String artist = favCursor.getString(favCursor.getColumnIndexOrThrow("artist"));
                        long albumId = favCursor.getLong(favCursor.getColumnIndexOrThrow("album_id"));
                        newDb.musicDao().insertFavorite(new SongEntity(path, title, artist, albumId));
                    } while (favCursor.moveToNext());
                }
                favCursor.close();

                // Migrate Lyrics
                Cursor lyricCursor = oldDb.rawQuery("SELECT * FROM lyrics_cache", null);
                if (lyricCursor.moveToFirst()) {
                    do {
                        String path = lyricCursor.getString(lyricCursor.getColumnIndexOrThrow("path"));
                        String plain = lyricCursor.getString(lyricCursor.getColumnIndexOrThrow("plain_lyrics"));
                        String synced = lyricCursor.getString(lyricCursor.getColumnIndexOrThrow("synced_lyrics"));
                        newDb.musicDao().insertLyrics(new LyricEntity(path, plain, synced));
                    } while (lyricCursor.moveToNext());
                }
                lyricCursor.close();
                
                oldDb.close();
                
                // Delete old file after successful migration
                oldDbFile.delete();
                
            } catch (Exception ignored) {}
        }
    }
}
