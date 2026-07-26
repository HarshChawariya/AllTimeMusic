package com.example.alltimemusic;

import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.palette.graphics.Palette;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayList_Fragment extends Fragment {

    private static final String key1 = "ARG1";
    private static final String key2 = "ARG2";
    private TextView songTitleTextView, artist_name, textSeek1, textSeek2, syncedLyricsTxt;
    private SeekBar seekBar;
    private ImageView pause, next, previous, loopButton, favButton;
    private static com.google.android.material.imageview.ShapeableImageView profile;
    private LinearLayout rootLayout;
    private ArrayList<musicList_Structure> songs;
    public static ArrayList<musicList_Structure> arrPlayNext = new ArrayList<>();
    public static ArrayList<musicList_Structure> arrPlayList = new ArrayList<>();
    private static final ArrayList<Integer> shuffledIndices = new ArrayList<>();
    private List<LyricLine> lyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;
    private static int shufflePointer = -1;
    public static MediaPlayer mediaPlayer;
    private LinearLayout logo;
    private int position;
    public static int playingPosition = -1;
    private int currentLoopMode = 0; // 0: No Loop, 1: Single Loop, 2: Playlist Loop, 3: Shuffle
    private long lastClickTime = 0;
    private final Handler seekBarHandler = new Handler(Looper.getMainLooper());
    private final Runnable seekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                try {
                    int currentPos = mediaPlayer.getCurrentPosition();
                    if (seekBar != null) {
                        seekBar.setProgress(currentPos);
                    }
                    if (textSeek1 != null) {
                        textSeek1.setText(createTime(currentPos));
                    }

                    if (syncedLyricsTxt != null && syncedLyricsTxt.getVisibility() == View.VISIBLE && !lyricLines.isEmpty()) {
                        updateSyncedLyrics(currentPos);
                    }

                    if (mediaPlayer.isPlaying()) {
                        seekBarHandler.postDelayed(this, 500); // Faster update for smooth lyrics
                    }
                } catch (IllegalStateException e) {
                    // Ignore state errors during background updates
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        seekBarHandler.removeCallbacks(seekBarRunnable);
    }

    public void startSeekBarUpdate() {
        seekBarHandler.removeCallbacks(seekBarRunnable);
        seekBarHandler.post(seekBarRunnable);
    }

    public void updateSongFromAdapter() {
        songs = musicList_Recycler_Adapter.fullMusicList;
        arrPlayList = songs;
        position = musicList_Recycler_Adapter.currentPosition;

        if (playingPosition != position || mediaPlayer == null) {
            playSong();
        } else {
            syncUIWithCurrentSong();
        }
    }

    public void toggleFavourite() {
        if (arrPlayList == null || arrPlayList.isEmpty()) return;

        // Use globally tracked currentItem to avoid index shift issues when removing from Liked list
        musicList_Structure currentSong = musicList_Recycler_Adapter.currentItem;
        if (currentSong == null) {
            currentSong = arrPlayList.get(position);
        }

        try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
            if (!db.isFavorite(currentSong.songPath)) {
                db.addFavorite(currentSong);
                currentSong.isFavourite = true;
                favButton.setImageResource(R.drawable.fill_heart);
                Toast.makeText(getContext(), getString(R.string.added_to_favorite), Toast.LENGTH_SHORT).show();
            } else {
                db.removeFavorite(currentSong.songPath);
                currentSong.isFavourite = false;
                favButton.setImageResource(R.drawable.boder_of_heart);
                Toast.makeText(getActivity(), getString(R.string.removed_from_favorite), Toast.LENGTH_SHORT).show();
            }
        }

        // IMPORTANT: Immediate Position Sync to prevent crash on re-addition/removal
        if (arrPlayList != null) {
            boolean found = false;
            for (int i = 0; i < arrPlayList.size(); i++) {
                if (arrPlayList.get(i).songPath.equals(currentSong.songPath)) {
                    position = i;
                    musicList_Recycler_Adapter.currentPosition = i;
                    found = true;
                    break;
                }
            }
            // If not found (item removed), ensure position is still within bounds for the new list size
            if (!found && position >= arrPlayList.size()) {
                position = Math.max(0, arrPlayList.size() - 1);
                musicList_Recycler_Adapter.currentPosition = position;
            }
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayer();
            ((MainActivity) getActivity()).updateRecyclerViewSelection();
        } else if (getActivity() instanceof LikedSongsActivity) {
            ((LikedSongsActivity) getActivity()).updateMiniPlayer();
            ((LikedSongsActivity) getActivity()).updateRecyclerViewSelection();
        }
    }

    public void applyLoopMode(boolean showToast) {
        if (mediaPlayer == null) return;

        switch (currentLoopMode) {
            case 0: // No Loop
                mediaPlayer.setLooping(false);
                loopButton.setImageResource(R.drawable.no_loop);
                if (showToast) Toast.makeText(getContext(), getString(R.string.loop_off), Toast.LENGTH_SHORT).show();
                break;
            case 1: // Single Loop
                // Don't use mediaPlayer.setLooping(true) so onCompletionListener can handle queue
                mediaPlayer.setLooping(false); 
                loopButton.setImageResource(R.drawable.single_loop);
                if (showToast) Toast.makeText(getContext(), getString(R.string.single_loop), Toast.LENGTH_SHORT).show();
                break;
            case 2: // Playlist Loop
                mediaPlayer.setLooping(false);
                loopButton.setImageResource(R.drawable.play_list_loop);
                if (showToast) Toast.makeText(getContext(), getString(R.string.playlist_loop), Toast.LENGTH_SHORT).show();
                break;
            case 3: // Shuffle
                mediaPlayer.setLooping(false);
                loopButton.setImageResource(R.drawable.shuffle);
                if (shuffledIndices.size() != (arrPlayList != null ? arrPlayList.size() : 0)) {
                    setupShuffleQueue();
                }
                if (showToast) Toast.makeText(getContext(), getString(R.string.shuffle_on), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void setupShuffleQueue() {
        if (arrPlayList == null || arrPlayList.isEmpty()) return;
        shuffledIndices.clear();
        for (int i = 0; i < arrPlayList.size(); i++) {
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

    public void syncUIWithCurrentSong() {
        if (mediaPlayer != null && arrPlayList != null && !arrPlayList.isEmpty()) {
            // Safety Check: Ensure position is valid for the current list size
            if (position < 0 || position >= arrPlayList.size()) {
                position = 0; // Default to first if out of bounds
            }
            
            musicList_Structure currentSong = arrPlayList.get(position);
            if (songTitleTextView != null) songTitleTextView.setText(currentSong.songTitle);
            if (artist_name != null) artist_name.setText(currentSong.getCleanArtist());

            updateProfileImage(profile, currentSong);
            loadSyncedLyrics(currentSong.songPath);

            try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
                currentSong.isFavourite = db.isFavorite(currentSong.songPath);
            }

            if (favButton != null) favButton.setImageResource(currentSong.isFavourite ? R.drawable.fill_heart : R.drawable.boder_of_heart);

            if (seekBar != null) {
                seekBar.setMax(mediaPlayer.getDuration());
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
            }
            if (textSeek1 != null) textSeek1.setText(createTime(mediaPlayer.getCurrentPosition()));
            if (textSeek2 != null) textSeek2.setText(createTime(mediaPlayer.getDuration()));
            if (pause != null) pause.setImageResource(mediaPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);

            startSeekBarUpdate();
            applyLoopMode(false);
        }
    }
    /**
     * Updates internal views when a new color is received from MainActivity.
     * Fragment root is now transparent, relying on MainActivity container background.
     */
    public void updateInternalColors(int color) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            // Internal view updates can go here if needed (e.g. dynamic text/icon tinting)
            // But root background logic is removed to avoid redundancy.
        });
    }

    private void updateProfileImage(ShapeableImageView profile_imageView, musicList_Structure song) {
        if (profile_imageView == null || song == null) return;

        android.net.Uri sArtworkUri = android.net.Uri.parse("content://media/external/audio/albumart");
        android.net.Uri uri = android.content.ContentUris.withAppendedId(sArtworkUri, song.albumId);

        // Use Glide with Palette for dynamic background color extraction
        Glide.with(this)
                .asBitmap()
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        profile_imageView.setImageBitmap(resource);

                        ViewGroup.LayoutParams params = profile_imageView.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        profile_imageView.setLayoutParams(params);
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        // Not used
                    }

                    @Override
                    public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                        setDefaultProfileImage(profile_imageView);
                    }
                });
    }
    private void setDefaultProfileImage(ShapeableImageView profile_imageView) {
        profile_imageView.setImageResource(R.drawable.profile);

        // Set to 280dp for default image (main profile) as requested by user
        int sizeInPx = (int) (280 * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams params = profile_imageView.getLayoutParams();
        params.width = sizeInPx;
        params.height = sizeInPx;
        profile_imageView.setLayoutParams(params);
    }

    private void notifyActivity() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayer();
            ((MainActivity) getActivity()).updateRecyclerViewSelection();
        } else if (getActivity() instanceof LikedSongsActivity) {
            ((LikedSongsActivity) getActivity()).updateMiniPlayer();
            ((LikedSongsActivity) getActivity()).updateRecyclerViewSelection();
        }
    }

    private void playFromNextQueue() {
        if (arrPlayNext.isEmpty()) return;
        musicList_Structure nextSong = arrPlayNext.remove(0);

        musicList_Recycler_Adapter.currentItem = nextSong;

        if (arrPlayList != null) {
            for (int i = 0; i < arrPlayList.size(); i++) {
                if (arrPlayList.get(i).songPath.equals(nextSong.songPath)) {
                    position = i;
                    musicList_Recycler_Adapter.currentPosition = position;
                    
                    // Sync shuffle pointer if in shuffle mode
                    if (currentLoopMode == 3 && shuffledIndices.size() == arrPlayList.size()) {
                        for (int j = 0; j < shuffledIndices.size(); j++) {
                            if (shuffledIndices.get(j) == position) {
                                shufflePointer = j;
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }

        playSong();
    }

    public void playSong() {
        if (arrPlayList == null || arrPlayList.isEmpty()) return;

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }

        musicList_Structure currentSong = musicList_Recycler_Adapter.currentItem;
        if (currentSong == null) {
            currentSong = arrPlayList.get(position);
        }

        try {
            mediaPlayer = MediaPlayer.create(getContext(), Uri.parse(currentSong.songPath));
            if (mediaPlayer != null) {
                playingPosition = position;
                mediaPlayer.start();

                mediaPlayer.setOnCompletionListener(mp -> {
                    if (!arrPlayNext.isEmpty()) {
                        playFromNextQueue();
                    } else if (currentLoopMode == 1) {
                        playSong();
                    } else if (currentLoopMode == 2 || currentLoopMode == 3) {
                        playNext();
                    } else if (currentLoopMode == 0) {
                        if (pause != null) pause.setImageResource(R.drawable.play);
                        if (seekBar != null) seekBar.setProgress(mp.getDuration());
                        if (textSeek1 != null) textSeek1.setText(createTime(mp.getDuration()));
                        notifyActivity();
                    }
                });

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Toast.makeText(getContext(), getString(R.string.playback_error), Toast.LENGTH_SHORT).show();
                    return true;
                });

                if (songTitleTextView != null) songTitleTextView.setText(currentSong.songTitle);
                if (artist_name != null) artist_name.setText(currentSong.getCleanArtist());
                updateProfileImage(profile, currentSong);
                loadSyncedLyrics(currentSong.songPath);

                try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
                    currentSong.isFavourite = db.isFavorite(currentSong.songPath);
                }
                if (favButton != null) favButton.setImageResource(currentSong.isFavourite ? R.drawable.fill_heart : R.drawable.boder_of_heart);

                if (seekBar != null) {
                    seekBar.setMax(mediaPlayer.getDuration());
                }
                if (textSeek2 != null) textSeek2.setText(createTime(mediaPlayer.getDuration()));
                if (pause != null) pause.setImageResource(R.drawable.pause);
                startSeekBarUpdate();

                notifyActivity();
                applyLoopMode(false);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.error_message, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    public void playNext() {
        if (arrPlayList == null || arrPlayList.isEmpty()) return;

        if (!arrPlayNext.isEmpty()) {
            playFromNextQueue();
            return;
        }

        if (currentLoopMode == 3) { // Shuffle
            if (shuffledIndices.size() != arrPlayList.size()) setupShuffleQueue();
            shufflePointer = (shufflePointer + 1) % shuffledIndices.size();
            position = shuffledIndices.get(shufflePointer);
        } else {
            position = (position + 1) % arrPlayList.size();
        }

        musicList_Recycler_Adapter.currentPosition = position;
        musicList_Recycler_Adapter.currentItem = arrPlayList.get(position);
        playSong();
    }

    public void playPrevious() {
        if (arrPlayList == null || arrPlayList.isEmpty()) return;

        if (currentLoopMode == 3) { // Shuffle
            if (shuffledIndices.size() != arrPlayList.size()) setupShuffleQueue();
            shufflePointer = (shufflePointer - 1 + shuffledIndices.size()) % shuffledIndices.size();
            position = shuffledIndices.get(shufflePointer);
        } else {
            position = (position - 1 + arrPlayList.size()) % arrPlayList.size();
        }

        musicList_Recycler_Adapter.currentPosition = position;
        musicList_Recycler_Adapter.currentItem = arrPlayList.get(position);
        playSong();
    }

    private void loadSyncedLyrics(String path) {
        // BUG FIX: Reset everything immediately to avoid showing previous song lyrics
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
            if (!lyricLines.isEmpty()) {
                if (syncedLyricsTxt != null) {
                    syncedLyricsTxt.setVisibility(View.VISIBLE);
                }
                return;
            }
        }
    }

    private void updateSyncedLyrics(int currentMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentMs >= lyricLines.get(i).getTimeMs()) {
                index = i;
            } else {
                break;
            }
        }

        if (index != -1 && index != currentLyricIndex) {
            currentLyricIndex = index;
            String text = lyricLines.get(index).getText();
            if (syncedLyricsTxt != null) {
                syncedLyricsTxt.setText(text);
                
                // Spotify style slide up animation
                android.view.animation.Animation slideUp = android.view.animation.AnimationUtils.loadAnimation(getContext(), R.anim.slide_up);
                syncedLyricsTxt.startAnimation(slideUp);
            }
        }
    }

    private java.util.List<LyricLine> parseLRC(String lrc) {
        java.util.List<LyricLine> lines = new java.util.ArrayList<>();
        if (lrc == null) return lines;

        String[] split = lrc.split("\n");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");

        for (String line : split) {
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    long min = Long.parseLong(matcher.group(1));
                    long sec = Long.parseLong(matcher.group(2));
                    String msStrRaw = matcher.group(3);
                    long ms = Long.parseLong(msStrRaw);
                    if (msStrRaw.length() == 2) ms *= 10;

                    long time = (min * 60 * 1000) + (sec * 1000) + ms;
                    String text = matcher.group(4).trim();

                    if (!text.isEmpty()) {
                        lines.add(new LyricLine(time, text));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return lines;
    }

    public void updatePauseIcon() {
        if (mediaPlayer != null && pause != null) {
            pause.setImageResource(mediaPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);
        }
    }

    public String createTime(int ms) {
        int sec = ms / 1000;
        return getString(R.string.time_format, sec / 60, sec % 60);
    }

    public static PlayList_Fragment newInstance(String value1, String value2){
        PlayList_Fragment fragment = new PlayList_Fragment();
        Bundle bundle = new Bundle();
        bundle.putString(key1, value1);
        bundle.putString(key2, value2);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_play_list_, container, false);
        textSeek1 = view.findViewById(R.id.textSeek1);
        textSeek2 = view.findViewById(R.id.textSeek2);
        pause = view.findViewById(R.id.pause);
        previous = view.findViewById(R.id.previous);
        next = view.findViewById(R.id.next);
        favButton = view.findViewById(R.id.favButton);
        loopButton = view.findViewById(R.id.loopButton);
        logo = view.findViewById(R.id.logo);
        profile = view.findViewById(R.id.profile);
        rootLayout = view.findViewById(R.id.fragment_PlayList);
        seekBar = view.findViewById(R.id.seekbar);
        songTitleTextView = view.findViewById(R.id.songTitleTextView);
        songTitleTextView.setSelected(true);
        artist_name = view.findViewById(R.id.song_Artist_TextView);
        syncedLyricsTxt = view.findViewById(R.id.playList_Fragment_syncedLyricsTextView);

        songs = musicList_Recycler_Adapter.fullMusicList;
        arrPlayList = songs;
        position = musicList_Recycler_Adapter.currentPosition;

        if (arrPlayList != null && !arrPlayList.isEmpty()) {
            musicList_Structure current = arrPlayList.get(position);
            songTitleTextView.setText(current.songTitle);
            artist_name.setText(current.getCleanArtist());
            updateProfileImage(profile, current);
        }

        if (mediaPlayer != null && mediaPlayer.isPlaying() && playingPosition == position) {
            syncUIWithCurrentSong();
        } else {
            playSong();
        }

        pause.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    pause.setImageResource(R.drawable.play);
                    mediaPlayer.pause();
                } else {
                    pause.setImageResource(R.drawable.pause);
                    mediaPlayer.start();
                    startSeekBarUpdate();
                }
                notifyActivity();
            }
        });

        next.setOnClickListener(v -> playNext());
        previous.setOnClickListener(v -> playPrevious());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mediaPlayer != null) mediaPlayer.seekTo(progress);
                    textSeek1.setText(createTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                //textSeek1.setText(createTime(seekBar.getProgress()));
//                seekBar.setProgressDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rec_drw_seekprogress));
//                seekBar.setThumb(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rec_drw_seek_thumb));
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
               // textSeek1.setText(createTime(seekBar.getProgress()));
            }
        });

        loopButton.setOnClickListener(v -> {
            currentLoopMode = (currentLoopMode + 1) % 4;
            applyLoopMode(true);
        });

        favButton.setOnClickListener(v -> toggleFavourite());

        favButton.setOnLongClickListener(v -> {
            Intent intent = new Intent(getContext(), LikedSongsActivity.class);
            startActivity(intent);
            return true;
        });

        logo.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < 300) toggleFavourite();
            lastClickTime = clickTime;
        });

        return view;
    }
}
