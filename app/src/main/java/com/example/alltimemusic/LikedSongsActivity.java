package com.example.alltimemusic;

import android.content.ContentUris;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.imageview.ShapeableImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

@UnstableApi
public class LikedSongsActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    ImageView backBtn;
    ArrayList<musicList_Structure> likedSongs;

    private LinearLayout miniPlayer;
    private TextView miniPlayerText;
    private ImageView miniPause;
    private ShapeableImageView miniProfile;
    private ProgressBar miniProgressBar;
    private MusicViewModel musicViewModel;

    private final Handler miniPlayerHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniPlayerRunnable = () -> {
        // Logic moved to ViewModel observation
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liked_songs);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        recyclerView = findViewById(R.id.liked_recycler);
        backBtn = findViewById(R.id.back_btn_liked);

        // Mini Player views
        miniPlayer = findViewById(R.id.miniPlayer);
        miniPlayerText = findViewById(R.id.dialog_txt);
        miniPause = findViewById(R.id.dialog_pause);
        miniProgressBar = findViewById(R.id.progressbar);
        miniProfile = findViewById(R.id.mini_profile);

        backBtn.setOnClickListener(v -> finish());

        miniPlayer.setOnClickListener(v -> {
            MainActivity.isReturningFromLiked = true;
            finish();
        });

        miniPause.setOnClickListener(v -> toggleMusic());

        musicViewModel = new ViewModelProvider(this).get(MusicViewModel.class);
        musicViewModel.initController(this);
        observeViewModel();

        loadLikedSongs();
    }

    private void observeViewModel() {
        musicViewModel.getCurrentSong().observe(this, song -> {
            if (song != null) {
                miniPlayerText.setText(song.songTitle);
                miniPlayer.setVisibility(View.VISIBLE);
                updateMiniProfileImage(song);
            }
        });

        musicViewModel.getIsPlaying().observe(this, isPlaying -> miniPause.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play));

        musicViewModel.getCurrentPosition().observe(this, pos -> miniProgressBar.setProgress(pos.intValue()));

        musicViewModel.getDuration().observe(this, dur -> miniProgressBar.setMax(dur.intValue()));
    }

    private void toggleMusic() {
        if (musicViewModel != null) {
            musicViewModel.togglePlayPause();
        }
    }

    public void updateMiniPlayer() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            miniPlayerText.setText(current.songTitle);
            miniPlayer.setVisibility(View.VISIBLE);
            
            // Dynamic mini profile image update
            updateMiniProfileImage(current);
        } else {
            miniPlayer.setVisibility(View.GONE);
        }
    }

    private void updateMiniProfileImage(musicList_Structure song) {
        if (miniProfile == null || song == null) return;

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, song.albumId);

        // Use Glide for efficient loading in mini player
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        miniProfile.setImageDrawable(resource);
                        // Dynamically set to Match Parent for real images to fill the mini player container (50dp)
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        );
                        miniProfile.setLayoutParams(params);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        miniProfile.setImageDrawable(placeholder);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        miniProfile.setImageDrawable(errorDrawable);
                        setDefaultMiniProfile();
                    }
                });
    }

    private void setDefaultMiniProfile() {
        miniProfile.setImageResource(R.drawable.profile);

        // Dynamically set to 25dp for default image as requested by user
        int sizeInPx = (int) (25 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
        miniProfile.setLayoutParams(params);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLikedSongs();
        updateMiniPlayer();
        miniPlayerHandler.post(miniPlayerRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        miniPlayerHandler.removeCallbacks(miniPlayerRunnable);
    }

    public void updateRecyclerViewSelection() {
        if (recyclerView != null && recyclerView.getAdapter() instanceof musicList_Recycler_Adapter) {
            ((musicList_Recycler_Adapter) recyclerView.getAdapter()).updateSelection(0);
        }
    }

    private void loadLikedSongs() {
        likedSongs = FavoritesDatabase.favoriteList;
        if (likedSongs != null) {
            musicList_Recycler_Adapter adapter = new musicList_Recycler_Adapter(this, likedSongs);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
            updateRecyclerViewSelection();
        }
    }

    public void openPlayerLayout() {
        finish(); 
    }
}
