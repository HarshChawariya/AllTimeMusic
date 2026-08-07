package com.example.alltimemusic;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.palette.graphics.Palette;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class MusicViewModel extends ViewModel {

    private final MutableLiveData<musicList_Structure> currentSong = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> themeColor = new MutableLiveData<>();
    private final MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> duration = new MutableLiveData<>(0L);
    private final MutableLiveData<String> formattedPosition = new MutableLiveData<>("00:00");
    private final MutableLiveData<String> formattedDuration = new MutableLiveData<>("00:00");
    private final MutableLiveData<Integer> loopMode = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isCurrentSongFavourite = new MutableLiveData<>(false);

    // High-Performance Color Cache for Zero-Lag UI
    public static final Map<String, Integer> colorCache = new HashMap<>();
    public static final int DEFAULT_THEME_COLOR = 0xFF9D201A;

    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private Context appContext;
    private SharedPreferences sharedPreferences;

    private static final String PREF_NAME = "AllTimeMusicPrefs";
    private static final String KEY_LOOP_MODE = "saved_loop_mode";

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaController != null && mediaController.isPlaying()) {
                long pos = mediaController.getCurrentPosition();
                setCurrentPosition(pos);
                progressHandler.postDelayed(this, 500); // 500ms for stable general UI
            }
        }
    };

    private BroadcastReceiver favReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            musicList_Structure current = currentSong.getValue();
            if (current != null) {
                setCurrentSong(current); // Re-trigger DB check and LiveData update
            }
        }
    };

    public void initController(Context context) {
        if (mediaController != null) return;
        this.appContext = context.getApplicationContext();
        this.sharedPreferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Register for favorite changes from notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.registerReceiver(favReceiver, new IntentFilter("FAV_CHANGED"), Context.RECEIVER_EXPORTED);
        }

        SessionToken sessionToken = new SessionToken(context, new ComponentName(context, MusicService.class));

        controllerFuture = new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                setupControllerListener();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setupControllerListener() {
        if (mediaController == null) return;

        // Restore saved Loop Mode from SharedPreferences on initialization
        int savedMode = sharedPreferences.getInt(KEY_LOOP_MODE, 0);
        setLoopMode(savedMode);

        updateStateFromController();

        mediaController.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                // BUG FIX: If Loop Mode is "Off" (0), stay on the SAME song and pause after completion
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && loopMode.getValue() != null && loopMode.getValue() == 0) {
                    mediaController.pause();
                    mediaController.seekToPreviousMediaItem();
                    mediaController.seekTo(0); // Reset to start for re-play
                    return; 
                }
                updateStateFromController();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlayingNow) {
                setIsPlaying(isPlayingNow);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateStateFromController();
            }
        });
    }

    private void updateStateFromController() {
        if (mediaController == null) return;

        setIsPlaying(mediaController.isPlaying());
        setDuration(mediaController.getDuration());
        setCurrentPosition(mediaController.getCurrentPosition());

        // Sync Loop Mode from Controller state to UI
        int rMode = mediaController.getRepeatMode();
        boolean sEnabled = mediaController.getShuffleModeEnabled();
        int lMode = 0;
        if (sEnabled) lMode = 3;
        else if (rMode == Player.REPEAT_MODE_ONE) lMode = 1;
        else if (rMode == Player.REPEAT_MODE_ALL) lMode = 2;
        else lMode = 0;
        
        if (loopMode.getValue() == null || loopMode.getValue() != lMode) {
            setLoopModeLiveData(lMode);
        }

        MediaItem mediaItem = mediaController.getCurrentMediaItem();
        if (mediaItem != null) {
            // CRITICAL FIX: Ensure we use the static list to maintain albumId and Structure integrity
            List<musicList_Structure> allSongs = MusicService.songs;
            if (allSongs != null) {
                for (musicList_Structure song : allSongs) {
                    if (song.songPath.equals(mediaItem.mediaId)) {
                        setCurrentSong(song);
                        return;
                    }
                }
            }
            
            // Fallback if not found in list (e.g. initial load or single song play)
            MediaMetadata metadata = mediaItem.mediaMetadata;
            String title = (metadata.title != null) ? metadata.title.toString() : "Unknown";
            String artist = (metadata.artist != null) ? metadata.artist.toString() : "Unknown";
            musicList_Structure fallback = new musicList_Structure(title, mediaItem.mediaId, artist, 0);
            setCurrentSong(fallback);
        }
    }

    public void setCurrentSong(musicList_Structure song) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            currentSong.setValue(song);
        } else {
            currentSong.postValue(song);
        }
        
        if (song != null) {
            // PUSH DURATION IMMEDIATELY: Ensure UI (textSeek2) is updated without lag
            if (song.durationMs > 0) {
                setDuration(song.durationMs);
            }

            if (appContext != null) {
                try (FavoritesDatabase db = new FavoritesDatabase(appContext)) {
                    boolean isFav = db.isFavorite(song.songPath);
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        isCurrentSongFavourite.setValue(isFav);
                    } else {
                        isCurrentSongFavourite.postValue(isFav);
                    }
                }
            }
        }
    }

    public LiveData<musicList_Structure> getCurrentSong() { return currentSong; }
    public LiveData<Boolean> getIsCurrentSongFavourite() { return isCurrentSongFavourite; }
    public LiveData<Boolean> getIsPlaying() { return isPlaying; }
    public LiveData<Integer> getThemeColor() { return themeColor; }
    public LiveData<Long> getCurrentPosition() { return currentPosition; }
    public LiveData<Long> getDuration() { return duration; }
    public LiveData<Integer> getLoopMode() { return loopMode; }

    public void setIsPlaying(boolean playing) {
        isPlaying.postValue(playing);
        if (playing) startProgressUpdates();
        else stopProgressUpdates();
    }

    public void setThemeColor(int color) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            themeColor.setValue(color);
        } else {
            themeColor.postValue(color);
        }
    }

    /**
     * Instantly updates the theme color from cache if available.
     * Prevents visual lag during song transitions.
     */
    public void updateThemeColorInstant(String path) {
        Integer cachedColor = colorCache.get(path);
        if (cachedColor != null) {
            setThemeColor(cachedColor);
        }
    }

    /**
     * Unified Color Extraction logic to ensure consistent look across all screens.
     */
    public static int extractThemeColor(Bitmap bitmap) {
        if (bitmap == null) return DEFAULT_THEME_COLOR;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Palette palette = Palette.from(bitmap)
                .setRegion(width / 4, height / 4, (3 * width) / 4, (3 * height) / 4)
                .generate();

        Palette.Swatch bestSwatch = palette.getVibrantSwatch();
        if (bestSwatch == null) bestSwatch = palette.getDominantSwatch();
        if (bestSwatch == null) bestSwatch = palette.getDarkVibrantSwatch();

        int targetColor = (bestSwatch != null) ? bestSwatch.getRgb() : DEFAULT_THEME_COLOR;

        // HSV Post-processing: Boosting vibrance matching premium theme
        float[] hsv = new float[3];
        Color.colorToHSV(targetColor, hsv);
        hsv[1] = Math.min(hsv[1] * 1.3f, 0.85f); // Boost Saturation
        hsv[2] = Math.max(Math.min(hsv[2], 0.45f), 0.18f); // Adjust Brightness

        return Color.HSVToColor(hsv);
    }

    public void setCurrentPosition(long pos) {
        currentPosition.postValue(pos);
        formattedPosition.postValue(formatTime(pos));
    }

    public void setDuration(long dur) {
        duration.postValue(dur);
        formattedDuration.postValue(formatTime(dur));
    }

    public void setLoopMode(int mode) {
        setLoopModeLiveData(mode);
        
        // Persist mode to SharedPreferences
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_LOOP_MODE, mode).apply();
        }

        if (mediaController == null) return;
        switch (mode) {
            case 0: // No Loop
                mediaController.setRepeatMode(Player.REPEAT_MODE_OFF);
                mediaController.setShuffleModeEnabled(false);
                break;
            case 1: // Single Loop
                mediaController.setRepeatMode(Player.REPEAT_MODE_ONE);
                mediaController.setShuffleModeEnabled(false);
                break;
            case 2: // Playlist Loop
                mediaController.setRepeatMode(Player.REPEAT_MODE_ALL);
                mediaController.setShuffleModeEnabled(false);
                break;
            case 3: // Shuffle
                mediaController.setRepeatMode(Player.REPEAT_MODE_ALL);
                mediaController.setShuffleModeEnabled(true);
                break;
        }
    }

    private void setLoopModeLiveData(int mode) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            loopMode.setValue(mode);
        } else {
            loopMode.postValue(mode);
        }
    }

    public void togglePlayPause() {
        if (mediaController != null) {
            if (mediaController.isPlaying()) mediaController.pause();
            else mediaController.play();
        }
    }

    public void playNext() {
        if (mediaController != null) {
            if (mediaController.hasNextMediaItem()) {
                mediaController.seekToNext();
            } else if (loopMode.getValue() != null && (loopMode.getValue() == 2 || loopMode.getValue() == 3)) {
                mediaController.seekTo(0, 0); // Manual wrap for All/Shuffle
            } else {
                return; // Standard "No Loop" behavior
            }
            mediaController.play();
        }
    }

    public void playPrevious() {
        if (mediaController != null) {
            if (mediaController.hasPreviousMediaItem()) {
                mediaController.seekToPrevious();
            } else if (loopMode.getValue() != null && (loopMode.getValue() == 2 || loopMode.getValue() == 3)) {
                mediaController.seekTo(mediaController.getMediaItemCount() - 1, 0);
            } else {
                return;
            }
            mediaController.play();
        }
    }

    public void addToPlayNext(musicList_Structure song) {
        if (mediaController == null) return;

        // If player is idle or empty, start playing the song immediately
        if (mediaController.getMediaItemCount() == 0) {
            ArrayList<musicList_Structure> single = new ArrayList<>();
            single.add(song);
            playPlaylist(single, 0);
            return;
        }

        int currentIndex = mediaController.getCurrentMediaItemIndex();
        int nextIndex = currentIndex + 1;
        
        // BUG FIX: Prevent Duplication. If song exists, reorder it to "Next" position.
        int existingIndex = -1;
        if (MusicService.songs != null) {
            for (int i = 0; i < MusicService.songs.size(); i++) {
                if (MusicService.songs.get(i).songPath.equals(song.songPath)) {
                    existingIndex = i;
                    break;
                }
            }
        }

        if (existingIndex != -1) {
            if (existingIndex == currentIndex) {
                Toast.makeText(appContext, "Already playing this song", Toast.LENGTH_SHORT).show();
            } else {
                // Move item in native queue
                mediaController.moveMediaItem(existingIndex, nextIndex);
                
                // Sync static metadata list
                if (MusicService.songs != null) {
                    musicList_Structure item = MusicService.songs.remove(existingIndex);
                    // Adjust target index if removal shifted the list
                    int target = (existingIndex < nextIndex) ? nextIndex - 1 : nextIndex;
                    MusicService.songs.add(target, item);
                }
                Toast.makeText(appContext, "Moved to Play Next: " + song.songTitle, Toast.LENGTH_SHORT).show();
            }
        } else {
            // New item addition
            mediaController.addMediaItem(nextIndex, createMediaItem(song));
            if (MusicService.songs != null) {
                MusicService.songs.add(nextIndex, song);
            }
            Toast.makeText(appContext, "Added to Play Next: " + song.songTitle, Toast.LENGTH_SHORT).show();
        }

        // If player was paused or ended, start playing the newly queued song
        if (!mediaController.isPlaying()) {
            mediaController.play();
        }
        
        // Refresh UI lists across activities
        android.content.Intent intent = new android.content.Intent("LIST_CHANGED");
        appContext.sendBroadcast(intent);
    }

    public void seekTo(long position) { if (mediaController != null) mediaController.seekTo(position); }

    public void toggleFavourite() {
        musicList_Structure song = currentSong.getValue();
        if (song != null && appContext != null) {
            try (FavoritesDatabase db = new FavoritesDatabase(appContext)) {
                if (db.isFavorite(song.songPath)) {
                    db.removeFavorite(song.songPath);
                    isCurrentSongFavourite.postValue(false);
                    Toast.makeText(appContext, "Removed from Favorite", Toast.LENGTH_SHORT).show();
                } else {
                    db.addFavorite(song);
                    isCurrentSongFavourite.postValue(true);
                    Toast.makeText(appContext, "Added to Favorite", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void playPlaylist(ArrayList<musicList_Structure> songs, int index) {
        if (mediaController == null || songs == null || songs.isEmpty()) return;
        
        // BUG FIX: Ensure the static songs list is updated for mapping integrity
        MusicService.songs = songs;

        List<MediaItem> mediaItems = new ArrayList<>();
        for (musicList_Structure song : songs) mediaItems.add(createMediaItem(song));
        if (mediaController.getMediaItemCount() != songs.size()) {
            mediaController.setMediaItems(mediaItems);
            mediaController.prepare();
        }
        if (mediaController.getCurrentMediaItemIndex() != index) mediaController.seekTo(index, 0);
        mediaController.play();
    }

    private MediaItem createMediaItem(musicList_Structure song) {
        Uri artworkUri = null;
        if (song.albumId > 0) {
            Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
            artworkUri = ContentUris.withAppendedId(sArtworkUri, song.albumId);
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.songTitle)
                .setArtist(song.artistName)
                .setArtworkUri(artworkUri) // Explicitly setting to null if no ID exists clears previous art
                .build();

        return new MediaItem.Builder()
                .setMediaId(song.songPath)
                .setUri(Uri.parse(song.songPath))
                .setMediaMetadata(metadata)
                .build();
    }

    private void startProgressUpdates() { progressHandler.removeCallbacks(progressRunnable); progressHandler.post(progressRunnable); }
    private void stopProgressUpdates() { progressHandler.removeCallbacks(progressRunnable); }

    private String formatTime(long ms) {
        int seconds = (int) (ms / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopProgressUpdates();
        if (appContext != null && favReceiver != null) {
            appContext.unregisterReceiver(favReceiver);
        }
    }
}
