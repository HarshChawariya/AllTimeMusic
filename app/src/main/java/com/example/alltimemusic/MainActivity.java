package com.example.alltimemusic;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.imageview.ShapeableImageView;

import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView alphabet;
    ArrayList<musicList_Structure> musicList = new ArrayList<>();
    private static final int PERMISSION_REQUEST_CODE = 1234;
    public static boolean isReturningFromLiked = false;
    public static boolean isOfflineMode = false; // Flag for Offline Mode
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public static int lastDynamicColor = 0xFF9D201A;
    private TextView tabItem1, tabItem2;
    private View indicator;
    private ViewPager2 viewPager;
    private ImageView miniPause;
    private ShapeableImageView miniProfile;
    private ProgressBar miniProgressBar;
    LinearLayout mainLayout, musicList_LinLayOut, miniPlayer, TabLayout_LinearLayout;
    TextView miniPlayerText;
    public MusicViewModel musicViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        alphabet = findViewById(R.id.alphabet);
        recyclerView = findViewById(R.id.recycler);
        tabItem1 = findViewById(R.id.tabItem1);
        tabItem2 = findViewById(R.id.tabItem2);
        indicator = findViewById(R.id.indicator);
        viewPager = findViewById(R.id.viewPager);
        mainLayout = findViewById(R.id.mainLayOut);
        musicList_LinLayOut = findViewById(R.id.fragment_contained_linLayout);
        TabLayout_LinearLayout = findViewById(R.id.tabLay_LinearLayout);
        ImageView imgBackArrow = findViewById(R.id.img_back_arrow);
        ImageView imgThreeDot = findViewById(R.id.img_three_dot);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniPlayerText = findViewById(R.id.dialog_txt);
        miniPause = findViewById(R.id.dialog_pause);
        miniProgressBar = findViewById(R.id.progressbar);
        miniProfile = findViewById(R.id.mini_profile);

        setupNetworkMonitoring();

        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        imgBackArrow.setOnClickListener(v -> handleBackAction());
        miniPlayer.setOnClickListener(v -> openPlayerLayout());
        miniPause.setOnClickListener(v -> { if (musicViewModel != null) musicViewModel.togglePlayPause(); });

        musicViewModel = new ViewModelProvider(this).get(MusicViewModel.class);
        musicViewModel.initController(this);
        musicViewModel.setThemeColor(lastDynamicColor);
        observeViewModel();

        imgThreeDot.setOnClickListener(v -> {
            CustomPopupMenu popup = new CustomPopupMenu(this, v);
            popup.setItemTextColor(lastDynamicColor);
            popup.addMenuItem("Plain Lyrics");
            popup.addMenuItem("Synced Lyrics");
            popup.addMenuItem("Lyrics Editor");
            
            // Only show Add/Delete Lyrics if we are on the Lyrics tab (index 1)
            if (viewPager.getCurrentItem() == 1) {
                musicList_Structure currentItem = musicList_Recycler_Adapter.currentItem;
                if (currentItem != null) {
                    try (FavoritesDatabase db = new FavoritesDatabase(this)) {
                        String[] lyrics = db.getCachedLyrics(currentItem.songPath);

                        // Logic: Show "Add Lyrics" if either type is missing
                        boolean hasPlain = (lyrics != null && lyrics[0] != null && !lyrics[0].isEmpty());
                        boolean hasSynced = (lyrics != null && lyrics[1] != null && !lyrics[1].isEmpty());

                        if (!hasPlain || !hasSynced) {
                            popup.addMenuItem("Add Lyrics");
                        }

                        // Logic: Show "Delete Lyrics" if any lyrics exist in DB
                        if (lyrics != null && (hasPlain || hasSynced)) {
                            popup.addMenuItem("Delete Lyrics");
                        }
                    }
                }
            }
            
            // Toggle between Online/Offline Mode labels
            String modeOption = isOfflineMode ? "Offline Mode" : "Online Mode";
            popup.addMenuItem(modeOption);

            popup.setOnItemClickListener(title -> {
                switch (title) {
                    case "Plain Lyrics":
                    case "Synced Lyrics":
                        boolean isSynced = title.equals("Synced Lyrics");
                        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                            if (fragment instanceof Lyrics_Fragment) {
                                ((Lyrics_Fragment) fragment).toggleLyricsMode(isSynced);
                            }
                        }
                        break;
                    case "Add Lyrics":
                        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                            if (fragment instanceof Lyrics_Fragment) {
                                ((Lyrics_Fragment) fragment).openAddLyricsDialog();
                            }
                        }
                        break;
                    case "Delete Lyrics":
                        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                            if (fragment instanceof Lyrics_Fragment) {
                                ((Lyrics_Fragment) fragment).deleteLyricsFromDB();
                            }
                        }
                        break;
                    case "Lyrics Editor":
                        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                            if (fragment instanceof Lyrics_Fragment) {
                                ((Lyrics_Fragment) fragment).openSyncedLyricsEditor();
                            }
                        }
                        break;
                    case "Offline Mode":
                    case "Online Mode":
                        // Manual click: Just show the current state toast as requested
                        String message = isOfflineMode ? "You're Offline" : "You're Online";
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        break;
                }
            });
            popup.show(v);
        });
        

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (musicList_LinLayOut.getVisibility() == VISIBLE) {
                    handleBackAction();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        if (checkPermission()) {
            if (mainLayout.getVisibility() == VISIBLE && musicList_LinLayOut.getVisibility() == GONE) {
                displaySongs();
            } else if (musicList_LinLayOut.getVisibility() == VISIBLE) {
                displayTabLayOut();
            }
        } else {
            requestPermission();
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition < musicList.size()) {
                    String title = musicList.get(firstVisibleItemPosition).songTitle;
                    if (title != null && !title.isEmpty()) {
                        alphabet.setText(String.valueOf(title.charAt(0)).toUpperCase());
                        alphabet.setVisibility(VISIBLE);
                    } else {
                        alphabet.setVisibility(GONE);
                    }
                }
            }
        });
    }

    private void observeViewModel() {
        musicViewModel.getCurrentSong().observe(this, song -> {
            if (song != null) {
                miniPlayerText.setText(song.songTitle);
                miniPlayer.setVisibility(VISIBLE);
                
                // HIGHLIGHT SYNC: Update RecyclerView selection instantly
                if (recyclerView != null && recyclerView.getAdapter() instanceof musicList_Recycler_Adapter) {
                    ((musicList_Recycler_Adapter) recyclerView.getAdapter()).updateHighlightedSong(song);
                }
                
                // CRITICAL FIX: Ensure MiniPlayer is visible if a song exists,
                // BUT only if the main list is currently showing (Player is hidden)
                if (musicList_LinLayOut.getVisibility() == GONE) {
                    miniPlayer.setVisibility(VISIBLE);
                }
                
                // INSTANT DIRECT SYNC: Bypass LiveData delay for background
                Integer cached = MusicViewModel.colorCache.get(song.songPath);
                if (cached != null) {
                    applyDynamicColorsToUI(cached);
                }
                
                updateMiniProfileImage(song);
            }
        });

        musicViewModel.getIsPlaying().observe(this, isPlaying -> miniPause.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play));

        musicViewModel.getCurrentPosition().observe(this, position -> miniProgressBar.setProgress(position.intValue()));

        musicViewModel.getDuration().observe(this, duration -> miniProgressBar.setMax(duration.intValue()));

        musicViewModel.getThemeColor().observe(this, color -> {
            applyDynamicColorsToUI(color);
            lastDynamicColor = color;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // BUG FIX: Reset Status Bar to Red if main list is visible
        if (musicList_LinLayOut.getVisibility() == GONE) {
            getWindow().setStatusBarColor(Color.parseColor("#9D201A"));
        }

        if (musicList != null && !musicList.isEmpty()) {
            AppDatabase.databaseExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                List<SongEntity> favs = db.musicDao().getAllFavorites();
                
                runOnUiThread(() -> {
                    for (musicList_Structure song : musicList) {
                        song.isFavourite = favs.stream()
                                .anyMatch(fav -> Objects.equals(fav.songPath, song.songPath));
                    }
                    if (recyclerView.getAdapter() != null) {
                        recyclerView.getAdapter().notifyDataSetChanged();
                    }
                });
            });
        }
        
        if (isReturningFromLiked) {
            isReturningFromLiked = false;
            openPlayerLayout();
        }
    }

    private void handleBackAction() {
        // Feature: Animate Status Bar transition back to default color (300ms fade)
        animateStatusBarColor(Color.parseColor("#9D201A"));
        
        closePlayerLayout();

        // BUG FIX: Re-show MiniPlayer when returning to list if a song is active
        if (musicViewModel != null && musicViewModel.getCurrentSong().getValue() != null) {
            miniPlayer.setVisibility(VISIBLE);
        }

        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    /**
     * Animates the status bar color from its current state to a target color.
     * Synchronized with fragment exit animations for a premium feel.
     */
    private void animateStatusBarColor(int toColor) {

        int fromColor = getWindow().getStatusBarColor();

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);

        colorAnimation.setDuration(300); // 300ms duration as requested
        colorAnimation.addUpdateListener(animator -> getWindow().setStatusBarColor((int) animator.getAnimatedValue()));
        colorAnimation.start();
    }

    public void switchToPlaylistTab() {
        if (viewPager != null) viewPager.setCurrentItem(0);
    }

    private void updateMiniProfileImage(musicList_Structure song) {
        if (miniProfile == null || song == null) return;

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, song.albumId);

        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transition(DrawableTransitionOptions.withCrossFade())
                .transform(new CenterCrop())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        miniProfile.setImageDrawable(resource);

                        // OPTIMIZED EXTRACTION: Unified logic with Cache
                        if (resource instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                            if (bitmap != null) {
                                Integer cached = MusicViewModel.colorCache.get(song.songPath);
                                if (cached != null) {
                                    musicViewModel.setThemeColor(cached);
                                } else {
                                    new Thread(() -> {
                                        int finalColor = MusicViewModel.extractThemeColor(bitmap);
                                        MusicViewModel.colorCache.put(song.songPath, finalColor);
                                        musicViewModel.setThemeColor(finalColor);
                                    }).start();
                                }
                            }
                        }
/*hsv[1] = Math.min(hsv[1] * 1.1f, 0.75f);
hsv[2] = Math.max(Math.min(hsv[2], 0.35f), 0.15f);*/
                        ViewGroup.LayoutParams params = miniProfile.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
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
                        
                        // Set Default Theme Color if album art is missing
                        if (musicViewModel != null) {
                            musicViewModel.setThemeColor(Color.parseColor("#9D201A"));
                        }
                    }
                });
    }

    /**
     * Applies the extracted color to the main container and all active fragments.
     * Uses a smooth animation to permanently eliminate the "flicking" effect.
     */
    private void applyDynamicColorsToUI(int targetColor) {
        if (musicList_LinLayOut == null) return;
        
        int fromColor = lastDynamicColor;
        lastDynamicColor = targetColor;

        // Animate the background transition for a premium feel
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, targetColor);
        anim.setDuration(300); // Smooth 300ms transition
        anim.addUpdateListener(animation -> {
            int animatedColor = (int) animation.getAnimatedValue();
            musicList_LinLayOut.setBackgroundColor(animatedColor);
            
            // Sync status bar ONLY if player is visible to avoid affecting list view prematurely
            if (musicList_LinLayOut.getVisibility() == VISIBLE) {
                getWindow().setStatusBarColor(animatedColor);
            }
        });
        anim.start();
    }

    private void setDefaultMiniProfile() {
        miniProfile.setImageResource(R.drawable.profile);
        int sizeInPx = (int) (25 * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams params = miniProfile.getLayoutParams();
        params.width = sizeInPx;
        params.height = sizeInPx;
        miniProfile.setLayoutParams(params);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
        // Initial Check
        checkCurrentNetworkStatus();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (isOfflineMode) {
                        isOfflineMode = false;
                        // Only show toast if user is currently looking at Lyrics tab (Index 1)
                        if (viewPager != null && viewPager.getCurrentItem() == 1) {
                            Toast.makeText(MainActivity.this, "You're Online", Toast.LENGTH_SHORT).show();
                        }
                        notifyLyricsOfNetworkChange();
                    }
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (!isOfflineMode) {
                        isOfflineMode = true;
                        // Only show toast if user is currently looking at Lyrics tab (Index 1)
                        if (viewPager != null && viewPager.getCurrentItem() == 1) {
                            Toast.makeText(MainActivity.this, "You're Offline", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        };

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    private void checkCurrentNetworkStatus() {
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

            boolean hasInternet = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

            isOfflineMode = !hasInternet;
        }
    }

    private void notifyLyricsOfNetworkChange() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof Lyrics_Fragment) {
                // If lyrics were not found earlier, try fetching them again
                ((Lyrics_Fragment) fragment).retryFetchingIfEmpty();
            }
        }
    }

    public void openPlayerLayout() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            // INSTANT DIRECT SYNC: Apply color before making layout visible
            Integer cached = MusicViewModel.colorCache.get(current.songPath);
            if (cached != null) {
                // Apply cached color IMMEDIATELY to eliminate "9D201A" flash
                lastDynamicColor = cached;
                musicList_LinLayOut.setBackgroundColor(cached);
                musicViewModel.setThemeColor(cached);
                getWindow().setStatusBarColor(cached);
            } else {
                musicViewModel.updateThemeColorInstant(current.songPath);
            }
            
            // PUSH METADATA IMMEDIATELY: Don't wait for MediaController transition
            musicViewModel.setCurrentSong(current);
        }

        if (miniPlayer != null) miniPlayer.setVisibility(GONE);
        mainLayout.setVisibility(GONE);
        musicList_LinLayOut.setVisibility(VISIBLE);

        displayTabLayOut();

        // Trigger Playback if needed
        if (musicViewModel != null && musicList_Recycler_Adapter.fullMusicList != null) {
            musicViewModel.playPlaylist(
                musicList_Recycler_Adapter.fullMusicList,
                musicList_Recycler_Adapter.currentPosition
            );
        }

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof Lyrics_Fragment) {
                if (current != null) {
                    ((Lyrics_Fragment) fragment).updateLyricsSync(current);
                }
            }
        }
    }

    private void closePlayerLayout() {
        Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down);
        slideDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mainLayout.setVisibility(VISIBLE);
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                musicList_LinLayOut.setVisibility(GONE);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        musicList_LinLayOut.startAnimation(slideDown);
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean audio = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean notifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            return audio && notifications;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                displaySongs();
            } else {
                Toast.makeText(this, "Permission required to access music", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void displaySongs() {
        musicList.clear();
        ContentResolver contentResolver = getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        try (Cursor cursor = contentResolver.query(songUri, null, selection, null, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);

                new FavoritesDatabase(this);

                do {
                    String title = cursor.getString(titleCol);
                    String path = cursor.getString(dataCol);
                    String artist = cursor.getString(artistCol);
                    long albumId = cursor.getLong(albumIdCol);
                    long durationMs = cursor.getLong(durationCol);
                    String displayName = cursor.getString(displayCol);

                    if (displayName != null && !displayName.isEmpty()) {
                        String name = displayName.toLowerCase();
                        if (name.endsWith(".mp3") && !name.startsWith(".") && !Character.isDigit(name.charAt(0)) && Character.isAlphabetic(name.charAt(0))) {
                            musicList_Structure song = new musicList_Structure(title, path, artist, albumId, durationMs);
                            song.isFavourite = FavoritesDatabase.favoriteList.stream().anyMatch(f -> Objects.equals(f.songPath, path));
                            musicList.add(song);
                        }
                    }
                } while (cursor.moveToNext());
            }
        }

        musicList.sort((o1, o2) -> o1.songTitle.compareToIgnoreCase(o2.songTitle));
        recyclerView.setAdapter(new musicList_Recycler_Adapter(this, musicList));
        if (!musicList.isEmpty()) {
            alphabet.setText(String.valueOf(musicList.get(0).songTitle.charAt(0)).toUpperCase());
        } else {
            alphabet.setVisibility(GONE);
        }
    }

    public void displayTabLayOut(){
        if (viewPager.getAdapter() == null) {
            ViewPager2Adapter adapter = new ViewPager2Adapter(this);
            viewPager.setAdapter(adapter);
            
            // BUG FIX: Preload both tabs so menu actions (Lyrics Editor, etc.) work from the start
            viewPager.setOffscreenPageLimit(1);

            tabItem1.setOnClickListener(v -> viewPager.setCurrentItem(0));
            tabItem2.setOnClickListener(v -> viewPager.setCurrentItem(1));

            tabItem1.post(() -> {
                indicator.getLayoutParams().width = tabItem1.getWidth();
                indicator.requestLayout();
            });

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                    float translationX = (position + positionOffset) * tabItem1.getWidth();
                    indicator.setTranslationX(translationX);
                }
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    updateTabStyles(position + 1);
                }
            });
        }
    }

    private void updateTabStyles(int tabNumber) {
        if (tabNumber == 1) {
            tabItem1.setTypeface(null, Typeface.BOLD);
            tabItem1.setTextColor(Color.WHITE);
            tabItem2.setTextColor(Color.parseColor("#99FFFFFF"));
            tabItem2.setTypeface(null, Typeface.NORMAL);
        } else {
            tabItem2.setTypeface(null, Typeface.BOLD);
            tabItem2.setTextColor(Color.WHITE);
            tabItem1.setTextColor(Color.parseColor("#99FFFFFF"));
            tabItem1.setTypeface(null, Typeface.NORMAL);
        }
    }
}
