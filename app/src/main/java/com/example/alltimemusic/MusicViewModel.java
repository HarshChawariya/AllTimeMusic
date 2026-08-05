package com.example.alltimemusic;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Locale;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public class MusicViewModel extends ViewModel {

    private final MutableLiveData<musicList_Structure> currentSong = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> themeColor = new MutableLiveData<>(0xFF9D201A);
    private final MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> duration = new MutableLiveData<>(0L);
    private final MutableLiveData<String> formattedPosition = new MutableLiveData<>("00:00");
    private final MutableLiveData<String> formattedDuration = new MutableLiveData<>("00:00");

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (MusicService.player != null && MusicService.player.isPlaying()) {
                long pos = MusicService.player.getCurrentPosition();
                setCurrentPosition(pos);
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    public void setCurrentSong(musicList_Structure song) {
        currentSong.postValue(song);
    }

    public LiveData<musicList_Structure> getCurrentSong() {
        return currentSong;
    }

    public void setIsPlaying(boolean playing) {
        isPlaying.postValue(playing);
        if (playing) {
            startProgressUpdates();
        } else {
            stopProgressUpdates();
        }
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public void setThemeColor(int color) {
        themeColor.postValue(color);
    }

    public LiveData<Integer> getThemeColor() {
        return themeColor;
    }

    public void setCurrentPosition(long pos) {
        currentPosition.postValue(pos);
        formattedPosition.postValue(formatTime(pos));
    }

    public LiveData<Long> getCurrentPosition() {
        return currentPosition;
    }

    public void setDuration(long dur) {
        duration.postValue(dur);
        formattedDuration.postValue(formatTime(dur));
    }

    public LiveData<Long> getDuration() {
        return duration;
    }

    public LiveData<String> getFormattedPosition() {
        return formattedPosition;
    }

    public LiveData<String> getFormattedDuration() {
        return formattedDuration;
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

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
    }
}
