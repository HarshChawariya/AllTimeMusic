package com.example.alltimemusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public class MusicService extends Service {

    private final IBinder binder = new MusicBinder();
    public static ExoPlayer player;
    
    public static ArrayList<musicList_Structure> songs = new ArrayList<>();
    public static int position = -1;
    public static ArrayList<musicList_Structure> arrPlayNext = new ArrayList<>();
    public static int currentLoopMode = 0; // 0: No Loop, 1: Single, 2: Playlist, 3: Shuffle
    private static final ArrayList<Integer> shuffledIndices = new ArrayList<>();
    private static int shufflePointer = -1;

    private static final String CHANNEL_ID = "AllTimeMusic_Silent_Channel";
    private static final int NOTIFICATION_ID = 999;
    
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

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        createNotificationChannel();
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                updateGlobalPosition();
                updateForegroundNotification();
                notifyUI();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    handleSongCompletion();
                }
                updateForegroundNotification();
            }
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateForegroundNotification();
                if (isPlaying) {
                    progressHandler.post(progressRunnable);
                } else {
                    progressHandler.removeCallbacks(progressRunnable);
                }
                notifyUI();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateForegroundNotification() {
        if (player == null) return;

        if (!player.isPlaying() && player.getPlaybackState() != Player.STATE_BUFFERING) {
            stopForeground(false);
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_app)
                .setContentTitle("All Time Music")
                .setContentText("Playing in background")
                .setContentIntent(pendingIntent)
                .setSilent(true)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
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

    public void playSong() {
        if (songs == null || songs.isEmpty() || position < 0 || position >= songs.size()) {
            // Log warning if needed
            return;
        }
        if (player == null) return;

        if (player.getMediaItemCount() != songs.size()) {
            player.clearMediaItems();
            List<MediaItem> mediaItems = new ArrayList<>();
            for (musicList_Structure song : songs) {
                mediaItems.add(createMediaItem(song));
            }
            player.setMediaItems(mediaItems);
            player.prepare();
        }

        if (player.getCurrentMediaItemIndex() != position) {
            player.seekTo(position, 0);
        }
        player.play();
        updateForegroundNotification();
        notifyUI();
    }

    private MediaItem createMediaItem(musicList_Structure song) {
        return new MediaItem.Builder()
                .setMediaId(song.songPath)
                .setUri(android.net.Uri.parse(song.songPath))
                .build();
    }

    public void playNext() {
        if (player == null) return;
        
        if (!arrPlayNext.isEmpty()) {
            playFromNextQueue();
            return;
        }

        if (currentLoopMode == 3) { // Shuffle
            if (shuffledIndices.size() != songs.size()) setupShuffleQueue();
            shufflePointer = (shufflePointer + 1) % shuffledIndices.size();
            position = shuffledIndices.get(shufflePointer);
            player.seekTo(position, 0);
        } else {
            if (player.hasNextMediaItem()) {
                player.seekToNext();
            } else if (currentLoopMode == 2) {
                player.seekTo(0, 0);
            }
        }
        player.play();
    }

    public void playPrevious() {
        if (player == null) return;

        if (currentLoopMode == 3) { // Shuffle
            if (shuffledIndices.size() != songs.size()) setupShuffleQueue();
            shufflePointer = (shufflePointer - 1 + shuffledIndices.size()) % shuffledIndices.size();
            position = shuffledIndices.get(shufflePointer);
            player.seekTo(position, 0);
        } else {
            if (player.hasPreviousMediaItem()) {
                player.seekToPrevious();
            } else if (currentLoopMode == 2) {
                player.seekTo(songs.size() - 1, 0);
            }
        }
        player.play();
    }

    private void setupShuffleQueue() {
        if (songs == null || songs.isEmpty()) return;
        shuffledIndices.clear();
        for (int i = 0; i < songs.size(); i++) {
            shuffledIndices.add(i);
        }
        Collections.shuffle(shuffledIndices);
        for (int i = 0; i < shuffledIndices.size(); i++) {
            if (shuffledIndices.get(i) == position) {
                shufflePointer = i;
                break;
            }
        }
    }

    private void handleSongCompletion() {
        if (player == null) return;
        if (currentLoopMode == 1) {
            player.seekTo(player.getCurrentMediaItemIndex(), 0);
            player.play();
        } else if (currentLoopMode == 2 || currentLoopMode == 3 || (currentLoopMode == 0 && player.hasNextMediaItem())) {
            playNext();
        }
    }

    private void playFromNextQueue() {
        if (arrPlayNext.isEmpty() || player == null) return;
        musicList_Structure nextSong = arrPlayNext.remove(0);
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).songPath.equals(nextSong.songPath)) {
                position = i;
                player.seekTo(i, 0);
                player.play();
                break;
            }
        }
    }

    private void notifyUI() {
        Intent intent = new Intent("MUSIC_UPDATED");
        sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player != null) {
            player.pause();
            player.release();
            player = null;
        }
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
