package com.example.alltimemusic;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MusicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavorite(SongEntity song);

    @Delete
    void deleteFavorite(SongEntity song);

    @Query("SELECT * FROM favorites")
    List<SongEntity> getAllFavorites();

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE songPath = :path)")
    boolean isFavorite(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLyrics(LyricEntity lyric);

    @Query("SELECT * FROM lyrics_cache WHERE songPath = :path")
    LyricEntity getLyrics(String path);

    @Query("DELETE FROM lyrics_cache WHERE songPath = :path")
    void deleteLyrics(String path);
}
