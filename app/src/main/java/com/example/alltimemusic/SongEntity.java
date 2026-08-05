package com.example.alltimemusic;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class SongEntity {
    @PrimaryKey
    @NonNull
    public String songPath;
    public String songTitle;
    public String artistName;
    public long albumId;

    public SongEntity(@NonNull String songPath, String songTitle, String artistName, long albumId) {
        this.songPath = songPath;
        this.songTitle = songTitle;
        this.artistName = artistName;
        this.albumId = albumId;
    }

    public static SongEntity fromStructure(musicList_Structure song) {
        return new SongEntity(song.songPath, song.songTitle, song.artistName, song.albumId);
    }

    public musicList_Structure toStructure() {
        musicList_Structure song = new musicList_Structure(songTitle, songPath, artistName, albumId);
        song.isFavourite = true;
        return song;
    }
}
