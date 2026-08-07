package com.example.alltimemusic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class MusicService extends MediaSessionService {

    private MediaSession mediaSession = null;
    public static ExoPlayer player;
    
    public static final String ACTION_FAVORITE = "com.example.alltimemusic.ACTION_FAVORITE";
    
    public static ArrayList<musicList_Structure> songs = new ArrayList<>();
    public static int position = -1;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                notifyUI();
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        mediaSession = new MediaSession.Builder(this, player)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller, @NonNull SessionCommand customCommand, @NonNull Bundle args) {
                        if (customCommand.customAction.equals(ACTION_FAVORITE)) {
                            toggleFavoriteCurrentSong();
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
                    }
                })
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                updateGlobalPosition();
                updateNotificationLayout();
                notifyUI();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    progressHandler.post(progressRunnable);
                } else {
                    progressHandler.removeCallbacks(progressRunnable);
                }
                updateNotificationLayout();
                notifyUI();
            }
        });
    }

    private void updateNotificationLayout() {
        if (mediaSession == null) return;
        
        musicList_Structure current = (position >= 0 && position < songs.size()) ? songs.get(position) : null;
        boolean isFavCurrent;
        if (current != null) {
            try (FavoritesDatabase db = new FavoritesDatabase(this)) {
                isFavCurrent = db.isFavorite(current.songPath);
            }
        } else {
            isFavCurrent = false;
        }

        boolean isPlaying = player != null && player.isPlaying();

        // Providing all buttons as custom layout to override system circular backgrounds (strokes)
        List<CommandButton> buttons = new ArrayList<>();

        // 1. Favorite (Far Left)
        buttons.add(new CommandButton.Builder()
                .setDisplayName("Favorite")
                .setIconResId(isFavCurrent ? R.drawable.fill_heart : R.drawable.boder_of_heart)
                .setSessionCommand(new SessionCommand(ACTION_FAVORITE, Bundle.EMPTY))
                .build());

        // 2. Previous (Stroke-less icon)
        buttons.add(new CommandButton.Builder()
                .setDisplayName("Previous")
                .setIconResId(R.drawable.ic_previous)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());

        // 3. Play/Pause (Stroke-less icon)
        buttons.add(new CommandButton.Builder()
                .setDisplayName(isPlaying ? "Pause" : "Play")
                .setIconResId(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .build());

        // 4. Next (Stroke-less icon)
        buttons.add(new CommandButton.Builder()
                .setDisplayName("Next")
                .setIconResId(R.drawable.ic_next)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .build());

        mediaSession.setCustomLayout(buttons);
    }

    private void toggleFavoriteCurrentSong() {
        musicList_Structure current = (position >= 0 && position < songs.size()) ? songs.get(position) : null;
        if (current == null) return;

        try (FavoritesDatabase db = new FavoritesDatabase(this)) {
            if (db.isFavorite(current.songPath)) {
                db.removeFavorite(current.songPath);
            } else {
                db.addFavorite(current);
            }
        }
        
        updateNotificationLayout();
        // Sync with UI components
        Intent intent = new Intent("FAV_CHANGED");
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@Nullable MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    private void updateGlobalPosition() {
        if (player != null) {
            int currentIdx = player.getCurrentMediaItemIndex();
            if (currentIdx >= 0 && currentIdx < songs.size()) {
                position = currentIdx;
                musicList_Recycler_Adapter.currentPosition = position;
                musicList_Recycler_Adapter.currentItem = songs.get(position);
            }
        }
    }
    private void notifyUI() {
        Intent intent = new Intent("MUSIC_UPDATED");
        sendBroadcast(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null) {
            player.pause();
            player.stop();
        }

        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
