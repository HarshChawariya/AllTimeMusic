package com.example.alltimemusic;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "lyrics_cache")
public class LyricEntity {
    @PrimaryKey
    @NonNull
    public String songPath;
    public String plainLyrics;
    public String syncedLyrics;

    public LyricEntity(@NonNull String songPath, String plainLyrics, String syncedLyrics) {
        this.songPath = songPath;
        this.plainLyrics = plainLyrics;
        this.syncedLyrics = syncedLyrics;
    }
}
