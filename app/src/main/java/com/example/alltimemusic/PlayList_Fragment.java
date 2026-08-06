package com.example.alltimemusic;

import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UnstableApi
public class PlayList_Fragment extends Fragment {

    private TextView songTitleTextView, artist_name, textSeek1, textSeek2, syncedLyricsTxt;
    private SeekBar seekBar;
    private ImageView pause, next, previous, loopButton, favButton;
    private ShapeableImageView profile;
    private LinearLayout rootLayout;
    private List<LyricLine> lyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;
    private MusicViewModel musicViewModel;
    private int currentLoopMode = 0; 
    private long lastClickTime = 0;

    @Override
    public void onDestroy() { super.onDestroy(); }

    public void toggleFavourite() { if (musicViewModel != null) musicViewModel.toggleFavourite(); }

    public void applyLoopMode(boolean showToast) {
        switch (currentLoopMode) {
            case 0: loopButton.setImageResource(R.drawable.no_loop);
            if (showToast) Toast.makeText(getContext(), getString(R.string.loop_off), Toast.LENGTH_SHORT).show();
            break;

            case 1: loopButton.setImageResource(R.drawable.single_loop);
            if (showToast) Toast.makeText(getContext(), getString(R.string.single_loop), Toast.LENGTH_SHORT).show();
            break;

            case 2: loopButton.setImageResource(R.drawable.play_list_loop);
            if (showToast) Toast.makeText(getContext(), getString(R.string.playlist_loop), Toast.LENGTH_SHORT).show();
            break;

            case 3: loopButton.setImageResource(R.drawable.shuffle);
            if (showToast) Toast.makeText(getContext(), getString(R.string.shuffle_on), Toast.LENGTH_SHORT).show();
            break;
        }
    }

    public void updateInternalColors(int colorValue) {
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(colorValue);
        }
        // Keep White tint to ensure visibility on any dynamic background
        if (seekBar != null) {
            seekBar.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            seekBar.getThumb().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            seekBar.setVisibility(View.VISIBLE);
        }
    }

    private void updateProfileImage(ShapeableImageView profile_imageView, musicList_Structure song) {
        if (profile_imageView == null || song == null) return;
        
        // BUG FIX: Reset image and size immediately to prevent "Old image lingering"
        profile_imageView.setImageResource(R.drawable.profile);
        int placeholderSize = (int) (280 * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams initialParams = profile_imageView.getLayoutParams();
        initialParams.width = placeholderSize;
        initialParams.height = placeholderSize;
        profile_imageView.setLayoutParams(initialParams);

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, song.albumId);
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        profile_imageView.setImageDrawable(resource);
                        // Programmatically set MATCH_PARENT to fill the 390dp container as per previous behavior
                        ViewGroup.LayoutParams params = profile_imageView.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        profile_imageView.setLayoutParams(params);
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        profile_imageView.setImageResource(R.drawable.profile);
                        int sizeInPx = (int) (280 * getResources().getDisplayMetrics().density);
                        ViewGroup.LayoutParams params = profile_imageView.getLayoutParams();
                        params.width = sizeInPx;
                        params.height = sizeInPx;
                        profile_imageView.setLayoutParams(params);
                    }
                });
    }

    private void loadSyncedLyrics(String path) {
        if (syncedLyricsTxt != null) {

            syncedLyricsTxt.setText("");
            syncedLyricsTxt.setVisibility(View.GONE);

        }

        lyricLines.clear();
        currentLyricIndex = -1;

        if (getContext() == null) return;

        FavoritesDatabase db = new FavoritesDatabase(getContext());
        String[] cached = db.getCachedLyrics(path);

        if (cached != null && cached[1] != null && !cached[1].isEmpty() && !cached[1].equalsIgnoreCase("null")) {

            lyricLines = parseLRC(cached[1]);

            if (!lyricLines.isEmpty() && syncedLyricsTxt != null) syncedLyricsTxt.setVisibility(View.VISIBLE);
        }
    }

    private void updateSyncedLyrics(int currentMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentMs >= lyricLines.get(i).getTimeMs()) index = i;
            else break;
        }
        if (index != -1 && index != currentLyricIndex) {
            currentLyricIndex = index;
            String text = lyricLines.get(index).getText();
            if (syncedLyricsTxt != null) {
                syncedLyricsTxt.setText(text);
                Animation slideUp = AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
                syncedLyricsTxt.startAnimation(slideUp);
            }
        }
    }

    private List<LyricLine> parseLRC(String lrc) {
        List<LyricLine> lines = new ArrayList<>();
        if (lrc == null) return lines;
        String[] split = lrc.split("\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");
        for (String line : split) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    long min = Long.parseLong(matcher.group(1));
                    long sec = Long.parseLong(matcher.group(2));
                    String msStr = matcher.group(3);
                    long ms = Long.parseLong(msStr);
                    if (msStr.length() == 2) ms *= 10;
                    lines.add(new LyricLine((min * 60 * 1000) + (sec * 1000) + ms, matcher.group(4).trim()));
                } catch (Exception ignored) {}
            }
        }
        return lines;
    }

    public String createTime(int ms) {
        int seconds = ms / 1000; int minutes = seconds / 60; seconds = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public static PlayList_Fragment newInstance(String value1, String value2){
        PlayList_Fragment fragment = new PlayList_Fragment();
        Bundle bundle = new Bundle();
        bundle.putString("ARG1", value1);
        bundle.putString("ARG2", value2);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_play_list_, container, false);

        textSeek1 = view.findViewById(R.id.textSeek1);
        textSeek2 = view.findViewById(R.id.textSeek2);
        pause = view.findViewById(R.id.pause);
        previous = view.findViewById(R.id.previous);
        next = view.findViewById(R.id.next);
        favButton = view.findViewById(R.id.favButton);
        loopButton = view.findViewById(R.id.loopButton);
        profile = view.findViewById(R.id.profile);
        rootLayout = view.findViewById(R.id.fragment_PlayList);
        seekBar = view.findViewById(R.id.seekbar);
        songTitleTextView = view.findViewById(R.id.songTitleTextView);
        songTitleTextView.setSelected(true);
        artist_name = view.findViewById(R.id.song_Artist_TextView);
        syncedLyricsTxt = view.findViewById(R.id.playList_Fragment_syncedLyricsTextView);

        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        observeViewModel();

        pause.setOnClickListener(v -> { if (musicViewModel != null) musicViewModel.togglePlayPause(); });
        next.setOnClickListener(v -> { if (musicViewModel != null) musicViewModel.playNext(); });
        previous.setOnClickListener(v -> { if (musicViewModel != null) musicViewModel.playPrevious(); });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (musicViewModel != null) musicViewModel.seekTo(progress);
                    // BUG FIX: Update text immediately when sliding to avoid lag
                    if (textSeek1 != null) textSeek1.setText(createTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        loopButton.setOnClickListener(v -> {
            currentLoopMode = (currentLoopMode + 1) % 4;
            if (musicViewModel != null) musicViewModel.setLoopMode(currentLoopMode);
            applyLoopMode(true);
        });

        favButton.setOnClickListener(v -> toggleFavourite());
        favButton.setOnLongClickListener(v -> {
            Intent intent = new Intent(getContext(), LikedSongsActivity.class);
            startActivity(intent);
            return true;
        });
        view.findViewById(R.id.logo).setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < 300) toggleFavourite();
            lastClickTime = clickTime;
        });

        updateInternalColors(MainActivity.lastDynamicColor);
        return view;
    }

    private void observeViewModel() {
        musicViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                if (songTitleTextView != null) songTitleTextView.setText(song.songTitle);
                if (artist_name != null) artist_name.setText(song.getCleanArtist());
                updateProfileImage(profile, song);
                loadSyncedLyrics(song.songPath);
                
                // Reactive Favorite Sync
                try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
                    boolean isFav = db.isFavorite(song.songPath);
                    if (favButton != null) {
                        favButton.setImageResource(isFav ? R.drawable.fill_heart : R.drawable.boder_of_heart);
                        if (isFav) favButton.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                        else favButton.clearColorFilter();
                    }
                }
            }
        });

        musicViewModel.getIsCurrentSongFavourite().observe(getViewLifecycleOwner(), isFav -> {
            if (favButton != null) {
                favButton.setImageResource(isFav ? R.drawable.fill_heart : R.drawable.boder_of_heart);
                if (isFav) favButton.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                else favButton.clearColorFilter();
            }
        });

        musicViewModel.getIsPlaying().observe(getViewLifecycleOwner(), isPlaying -> pause.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play));

        musicViewModel.getThemeColor().observe(getViewLifecycleOwner(), this::updateInternalColors);

        musicViewModel.getCurrentPosition().observe(getViewLifecycleOwner(), pos -> {
            seekBar.setProgress(pos.intValue()); textSeek1.setText(createTime(pos.intValue()));
            updateSyncedLyrics(pos.intValue());
        });

        musicViewModel.getDuration().observe(getViewLifecycleOwner(), dur -> {
            if (dur != null && dur > 0) {
                seekBar.setMax(dur.intValue()); textSeek2.setText(createTime(dur.intValue()));
            }
            // Ensure SeekBar is always visible when we have music loaded
            if (seekBar != null) seekBar.setVisibility(View.VISIBLE);
        });

        musicViewModel.getLoopMode().observe(getViewLifecycleOwner(), mode -> {
            this.currentLoopMode = mode; applyLoopMode(false);
        });
    }
}
