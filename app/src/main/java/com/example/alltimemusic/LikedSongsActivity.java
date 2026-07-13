package com.example.alltimemusic;

import android.content.ContentUris;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class LikedSongsActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    ImageView backBtn;
    ArrayList<musicList_Structure> likedSongs;

    private LinearLayout miniPlayer;
    private TextView miniPlayerText;
    private ImageView miniPause;
    private com.google.android.material.imageview.ShapeableImageView miniProfile;
    private ProgressBar miniProgressBar;

    private final Handler miniPlayerHandler = new Handler(Looper.getMainLooper());
    private final Runnable miniPlayerRunnable = new Runnable() {
        @Override
        public void run() {
            if (PlayList_Fragment.mediaPlayer != null && miniPlayer.getVisibility() == View.VISIBLE) {
                try {
                    int currentPos = PlayList_Fragment.mediaPlayer.getCurrentPosition();
                    int duration = PlayList_Fragment.mediaPlayer.getDuration();
                    
                    miniProgressBar.setProgress(currentPos);
                    
                    // Update Icon based on playing state
                    if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                        miniPause.setImageResource(R.drawable.pause);
                    } else {
                        miniPause.setImageResource(R.drawable.play);
                        // If song ended, ensure progress bar is 100%
                        if (currentPos >= duration - 1000) {
                            miniProgressBar.setProgress(duration);
                        }
                    }
                } catch (Exception ignored) {}
            }
            miniPlayerHandler.postDelayed(this, 1000); // Always run while activity is active
        }
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

        loadLikedSongs();
    }

    private void toggleMusic() {
        if (PlayList_Fragment.mediaPlayer != null) {
            if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                PlayList_Fragment.mediaPlayer.pause();
                miniPause.setImageResource(R.drawable.play);
            } else {
                PlayList_Fragment.mediaPlayer.start();
                miniPause.setImageResource(R.drawable.pause);
            }
        }
    }

    public void updateMiniPlayer() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            miniPlayerText.setText(current.songTitle);
            miniPlayer.setVisibility(View.VISIBLE);
            
            // Dynamic mini profile image update
            updateMiniProfileImage(current);
            
            if (PlayList_Fragment.mediaPlayer != null) {
                miniProgressBar.setMax(PlayList_Fragment.mediaPlayer.getDuration());
                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    miniPause.setImageResource(R.drawable.pause);
                } else {
                    miniPause.setImageResource(R.drawable.play);
                }
            }
        } else {
            miniPlayer.setVisibility(View.GONE);
        }
    }

    private void updateMiniProfileImage(musicList_Structure song) {
        if (miniProfile == null || song == null) return;

        android.net.Uri sArtworkUri = android.net.Uri.parse("content://media/external/audio/albumart");
        android.net.Uri uri = android.content.ContentUris.withAppendedId(sArtworkUri, song.albumId);

        // Use Glide for efficient loading in mini player
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.drawable.Drawable resource, @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                        miniProfile.setImageDrawable(resource);
                        // Dynamically set to Match Parent for real images to fill the mini player container (50dp)
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        );
                        miniProfile.setLayoutParams(params);
                    }

                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                        miniProfile.setImageDrawable(placeholder);
                    }

                    @Override
                    public void onLoadFailed(@androidx.annotation.Nullable android.graphics.drawable.Drawable errorDrawable) {
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
