package com.example.alltimemusic;

import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
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
import java.util.Locale;

public class PlayList_Fragment extends Fragment {

    private static final String key1 = "ARG1";
    private static final String key2 = "ARG2";
    private TextView songTitleTextView, artist_name, textSeek1, textSeek2;
    private SeekBar seekBar;
    private ImageView pause, next, previous, loopButton, favButton;
    private static com.google.android.material.imageview.ShapeableImageView profile;
    private LinearLayout rootLayout;
    private ArrayList<musicList_Structure> songs;
    public static ArrayList<musicList_Structure> arrPlayNext = new ArrayList<>();
    public static ArrayList<musicList_Structure> arrPlayList = new ArrayList<>();
    private static final ArrayList<Integer> shuffledIndices = new ArrayList<>();
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
                    if (mediaPlayer.isPlaying()) {
                        seekBar.setProgress(mediaPlayer.getCurrentPosition());
                        textSeek1.setText(createTime(mediaPlayer.getCurrentPosition()));
                        seekBarHandler.postDelayed(this, 1000);
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
        musicList_Structure currentSong = arrPlayList.get(position);

        try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
            if (!db.isFavorite(currentSong.songPath)) {
                db.addFavorite(currentSong);
                currentSong.isFavourite = true;
                favButton.setImageResource(R.drawable.fill_heart);
                Toast.makeText(getContext(), "Added To Favourite", Toast.LENGTH_SHORT).show();
            } else {
                db.removeFavorite(currentSong.songPath);
                currentSong.isFavourite = false;
                favButton.setImageResource(R.drawable.boder_of_heart);
                Toast.makeText(getActivity(), "Removed From Favourite", Toast.LENGTH_SHORT).show();
            }
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateRecyclerViewSelection();
        } else if (getActivity() instanceof LikedSongsActivity) {
            ((LikedSongsActivity) getActivity()).updateRecyclerViewSelection();
        }
    }

    public void applyLoopMode(boolean showToast) {
        if (mediaPlayer == null) return;

        switch (currentLoopMode) {
            case 0: // No Loop
                mediaPlayer.setLooping(false);
                loopButton.setImageResource(R.drawable.no_loop);
                if (showToast) Toast.makeText(getContext(), "Loop Off", Toast.LENGTH_SHORT).show();
                break;
            case 1: // Single Loop
                // Don't use mediaPlayer.setLooping(true) so onCompletionListener can handle queue
                mediaPlayer.setLooping(false); 
                loopButton.setImageResource(R.drawable.single_loop);
                if (showToast) Toast.makeText(getContext(), "Single Loop", Toast.LENGTH_SHORT).show();
                break;
            case 2: // Playlist Loop
                mediaPlayer.setLooping(false);
                loopButton.setImageResource(R.drawable.play_list_loop);
                if (showToast) Toast.makeText(getContext(), "Playlist Loop", Toast.LENGTH_SHORT).show();
                break;
            case 3: // Shuffle
                mediaPlayer.setLooping(false);
                loopButton.setImageResource(R.drawable.shuffle);
                if (shuffledIndices.size() != (arrPlayList != null ? arrPlayList.size() : 0)) {
                    setupShuffleQueue();
                }
                if (showToast) Toast.makeText(getContext(), "Shuffle On", Toast.LENGTH_SHORT).show();
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
            musicList_Structure currentSong = arrPlayList.get(position);
            songTitleTextView.setText(currentSong.songTitle);
            artist_name.setText(currentSong.getCleanArtist());

            updateProfileImage(profile, currentSong);

            try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
                currentSong.isFavourite = db.isFavorite(currentSong.songPath);
            }

            favButton.setImageResource(currentSong.isFavourite ? R.drawable.fill_heart : R.drawable.boder_of_heart);

            seekBar.setMax(mediaPlayer.getDuration());
            seekBar.setProgress(mediaPlayer.getCurrentPosition());
            textSeek1.setText(createTime(mediaPlayer.getCurrentPosition()));
            textSeek2.setText(createTime(mediaPlayer.getDuration()));
            pause.setImageResource(mediaPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);

            startSeekBarUpdate();
            applyLoopMode(false);
        }
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

                        // Extract dominant color for Spotify-like gradient
                        /*Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                int dominantColor = palette.getVibrantColor(palette.getDominantColor(0xFF9D201A));
                                updateBackgroundGradient(dominantColor);
                            }
                        });*/

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
                        //updateBackgroundGradient(0xFF9D201A);
                    }
                });
    }

    /*private void updateBackgroundGradient(int startColor) {
        if (rootLayout == null) return;

        // Dynamic gradient mimic of rec_draw.xml
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {startColor, 0xFF000000} // Dynamic Start to Black End
        );
        gd.setCornerRadius(0f);
        rootLayout.setBackground(gd);
    }*/

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
                        pause.setImageResource(R.drawable.play);
                        seekBar.setProgress(mp.getDuration());
                        textSeek1.setText(createTime(mp.getDuration()));
                        notifyActivity();
                    }
                });

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Toast.makeText(getContext(), "Playback Error", Toast.LENGTH_SHORT).show();
                    return true;
                });

                songTitleTextView.setText(currentSong.songTitle);
                artist_name.setText(currentSong.getCleanArtist());
                updateProfileImage(profile, currentSong);

                try (FavoritesDatabase db = new FavoritesDatabase(getContext())) {
                    currentSong.isFavourite = db.isFavorite(currentSong.songPath);
                }
                favButton.setImageResource(currentSong.isFavourite ? R.drawable.fill_heart : R.drawable.boder_of_heart);

                seekBar.setMax(mediaPlayer.getDuration());
                textSeek2.setText(createTime(mediaPlayer.getDuration()));
                pause.setImageResource(R.drawable.pause);
                startSeekBarUpdate();

                notifyActivity();
                applyLoopMode(false);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    public String createTime(int ms) {
        int sec = ms / 1000;
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
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

        songs = musicList_Recycler_Adapter.fullMusicList;
        arrPlayList = songs;
        position = musicList_Recycler_Adapter.currentPosition;

        if (songs != null && !songs.isEmpty()) {
            musicList_Structure current = songs.get(position);
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
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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
