package com.example.alltimemusic;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.util.ArrayList;
@UnstableApi
public class MusicService extends MediaSessionService {

    private MediaSession mediaSession = null;
    public static ExoPlayer player;
    
    public static ArrayList<musicList_Structure> songs = new ArrayList<>();
    public static int position = -1;
    public static ArrayList<musicList_Structure> arrPlayNext = new ArrayList<>();

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

        mediaSession = new MediaSession.Builder(this, player).build();

        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                updateGlobalPosition();
                notifyUI();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    progressHandler.post(progressRunnable);
                } else {
                    progressHandler.removeCallbacks(progressRunnable);
                }
                notifyUI();
            }
        });
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
