package com.example.alltimemusic;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
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
import androidx.appcompat.widget.Toolbar;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView alphabet;
    ArrayList<musicList_Structure> musicList = new ArrayList<>();
    private static final int PERMISSION_REQUEST_CODE = 1234;
    public static boolean isReturningFromLiked = false;
    public static boolean isOfflineMode = false;
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

    private final Handler miniPlayerHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Runnable miniPlayerRunnable = new Runnable() {
        @Override
        public void run() {
            if (PlayList_Fragment.mediaPlayer != null && miniPlayer.getVisibility() == VISIBLE) {
                try {
                    if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                        miniProgressBar.setProgress(PlayList_Fragment.mediaPlayer.getCurrentPosition());
                        miniPlayerHandler.postDelayed(this, 1000);
                    }
                } catch (Exception ignored) {}
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
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

        // Initialize Database Singleton
        FavoritesDatabase.getInstance(this);

        setupNetworkMonitoring();

        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        imgBackArrow.setOnClickListener(v -> handleBackAction());
        miniPlayer.setOnClickListener(v -> openPlayerLayout());
        miniPause.setOnClickListener(v -> toggleMusic());

        imgThreeDot.setOnClickListener(v -> {
            CustomPopupMenu popup = new CustomPopupMenu(this, v);
            popup.setItemTextColor(lastDynamicColor);
            popup.addMenuItem("Plain Lyrics");
            popup.addMenuItem("Synced Lyrics");
            popup.addMenuItem("Lyrics Editor");
            
            if (viewPager.getCurrentItem() == 1) {
                musicList_Structure current = musicList_Recycler_Adapter.currentItem;
                if (current != null) {
                    FavoritesDatabase db = FavoritesDatabase.getInstance(this);
                    String[] lyrics = db.getCachedLyrics(current.songPath);
                    boolean hasPlain = (lyrics != null && lyrics[0] != null && !lyrics[0].isEmpty());
                    boolean hasSynced = (lyrics != null && lyrics[1] != null && !lyrics[1].isEmpty());
                    if (!hasPlain || !hasSynced) popup.addMenuItem(getString(R.string.add_lyrics_title));
                    if (lyrics != null && (hasPlain || hasSynced)) popup.addMenuItem(getString(R.string.delete_lyrics_menu));
                }
            } else if (viewPager.getCurrentItem() == 0) {
                // Feature Requirement: Show Online/Offline mode only when in PlayList Fragment
                popup.addMenuItem(isOfflineMode ? "Offline Mode" : "Online Mode");
            }

            popup.setOnItemClickListener(title -> {
                switch (title) {
                    case "Plain Lyrics":
                    case "Synced Lyrics":
                        boolean isSynced = title.equals("Synced Lyrics");
                        for (Fragment f : getSupportFragmentManager().getFragments()) {
                            if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).toggleLyricsMode(isSynced);
                        }
                        break;
                    case "Add Lyrics":
                        for (Fragment f : getSupportFragmentManager().getFragments()) {
                            if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).openAddLyricsDialog();
                        }
                        break;
                    case "Delete Lyrics":
                        for (Fragment f : getSupportFragmentManager().getFragments()) {
                            if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).deleteLyricsFromDB();
                        }
                        break;
                    case "Lyrics Editor":
                        // BUG FIX: Handle directly in Activity to ensure availability regardless of Fragment lifecycle
                        openSyncedLyricsEditorDirectly();
                        break;
                    case "Offline Mode":
                    case "Online Mode":
                        Toast.makeText(MainActivity.this, isOfflineMode ? "You're Offline" : "You're Online", Toast.LENGTH_SHORT).show();
                        break;
                }
            });
            popup.show(v);
        });
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (musicList_LinLayOut.getVisibility() == VISIBLE) handleBackAction();
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        if (checkPermission()) {
            startFullProcess();
        } else {
            requestPermission();
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private String currentAlphabetLetter = "";
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition < musicList.size()) {
                    String title = musicList.get(firstVisibleItemPosition).songTitle;
                    if (title != null && !title.isEmpty()) {
                        String firstLetter = String.valueOf(title.charAt(0)).toUpperCase();
                        if (!firstLetter.equals(currentAlphabetLetter)) {
                            currentAlphabetLetter = firstLetter;
                            alphabet.setText(firstLetter);
                            alphabet.setVisibility(VISIBLE);
                        }
                    } else {
                        alphabet.setVisibility(GONE);
                        currentAlphabetLetter = "";
                    }
                }
            }
        });
    }

    private void startFullProcess() {
        executorService.execute(() -> {
            // 1. Try to load from Cache first for instant display
            ArrayList<musicList_Structure> cached = FavoritesDatabase.getInstance(this).getCachedSongs();
            if (!cached.isEmpty()) {
                updateUI(cached);
            }

            // 2. Scan and Update Database in background
            ArrayList<musicList_Structure> scanned = scanAllSongs();
            FavoritesDatabase.getInstance(this).cacheAllSongs(scanned);
            
            // 3. Final UI Sync from scanned data
            updateUI(scanned);

            // 4. MASTER PLAN: Start background pre-fetching of colors for all songs
            startBackgroundColorCaching(scanned);
        });
    }

    /**
     * Iterates through the song list and caches colors for those missing from DB.
     * Runs in a background thread to ensure zero UI lag.
     */
    private void startBackgroundColorCaching(ArrayList<musicList_Structure> songs) {
        executorService.execute(() -> {
            FavoritesDatabase db = FavoritesDatabase.getInstance(this);
            for (musicList_Structure song : songs) {
                // Check if color is already cached to avoid redundant processing
                if (db.getCachedColor(song.songPath) == 0) {
                    try {
                        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
                        Uri uri = ContentUris.withAppendedId(sArtworkUri, song.albumId);
                        
                        Bitmap bitmap = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            try {
                                bitmap = getContentResolver().loadThumbnail(uri, new Size(200, 200), null);
                            } catch (Exception ignored) {}
                        }
                        
                        if (bitmap == null) {
                            try (InputStream is = getContentResolver().openInputStream(uri)) {
                                if (is != null) {
                                    BitmapFactory.Options options = new BitmapFactory.Options();
                                    options.inSampleSize = 4; // Load small version for speed
                                    bitmap = BitmapFactory.decodeStream(is, null, options);
                                }
                            } catch (Exception ignored) {}
                        }

                        if (bitmap != null) {
                            Palette palette = Palette.from(bitmap).generate();
                            int color = extractBestColor(palette);
                            db.saveColor(song.songPath, color);
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Background caching failed for: " + song.songTitle);
                    }
                    
                    // Small delay to prevent CPU spike and keep app smooth
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
            }
            Log.d("MainActivity", "Master Color Cache pre-fetching completed.");
        });
    }

    private void updateUI(ArrayList<musicList_Structure> list) {
        HashSet<String> favPaths = new HashSet<>();
        for (musicList_Structure fav : FavoritesDatabase.favoriteList) favPaths.add(fav.songPath);
        for (musicList_Structure song : list) song.isFavourite = favPaths.contains(song.songPath);

        runOnUiThread(() -> {
            musicList = list;
            if (recyclerView.getAdapter() instanceof musicList_Recycler_Adapter) {
                ((musicList_Recycler_Adapter) recyclerView.getAdapter()).setMusicList(musicList);
            } else {
                recyclerView.setAdapter(new musicList_Recycler_Adapter(this, musicList));
            }
            if (!musicList.isEmpty()) {
                alphabet.setVisibility(VISIBLE);
                alphabet.setText(String.valueOf(musicList.get(0).songTitle.charAt(0)).toUpperCase());
            }
        });
    }

    private ArrayList<musicList_Structure> scanAllSongs() {
        ArrayList<musicList_Structure> tempList = new ArrayList<>();
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
                int displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);

                String lastLetter = "";
                do {
                    String title = cursor.getString(titleCol);
                    String path = cursor.getString(dataCol);
                    String artist = cursor.getString(artistCol);
                    long albumId = cursor.getLong(albumIdCol);
                    String displayName = cursor.getString(displayCol);

                    if (displayName != null && !displayName.isEmpty()) {
                        String name = displayName.toLowerCase();
                        if (name.endsWith(".mp3") && !name.startsWith(".") && !Character.isDigit(name.charAt(0)) && Character.isAlphabetic(name.charAt(0))) {
                            musicList_Structure song = new musicList_Structure(title, path, artist, albumId);
                            song.getCleanArtist(); // Process regex in background
                            if (song.songTitle != null && !song.songTitle.isEmpty()) {
                                String currentLetter = String.valueOf(song.songTitle.charAt(0)).toUpperCase();
                                if (!currentLetter.equals(lastLetter)) {
                                    song.showAlphabetHeader = true;
                                    song.alphabetHeader = currentLetter;
                                    lastLetter = currentLetter;
                                }
                            }
                            tempList.add(song);
                        }
                    }
                } while (cursor.moveToNext());
            }
        }
        return tempList;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (musicList_LinLayOut.getVisibility() == GONE) getWindow().setStatusBarColor(Color.parseColor("#9D201A"));
        if (!musicList.isEmpty()) {
            HashSet<String> favPaths = new HashSet<>();
            for (musicList_Structure fav : FavoritesDatabase.favoriteList) favPaths.add(fav.songPath);
            for (musicList_Structure song : musicList) song.isFavourite = favPaths.contains(song.songPath);
            if (recyclerView.getAdapter() instanceof musicList_Recycler_Adapter) {
                ((musicList_Recycler_Adapter) recyclerView.getAdapter()).setMusicList(musicList);
            }
        }
        updateMiniPlayer();

        if (isReturningFromLiked) {
            isReturningFromLiked = false;
            openPlayerLayout();
        }
    }

    private void handleBackAction() {
        // FLICKER FIX: Animate to the default red color (#9D201A) 
        // to maintain list theme continuity when closing the full player.
        animateStatusBarColor(Color.parseColor("#9D201A"));
        closePlayerLayout();
        updateMiniPlayer();
        updateRecyclerViewSelection();
    }

    private void animateStatusBarColor(int toColor) {
        int fromColor = getWindow().getStatusBarColor();
        ValueAnimator colorAnimation = ValueAnimator.ofObject(
                new ArgbEvaluator(), fromColor, toColor);
        colorAnimation.setDuration(300);
        colorAnimation.addUpdateListener(animator -> getWindow().setStatusBarColor((int) animator.getAnimatedValue()));
        colorAnimation.start();
    }

    private void toggleMusic() {
        if (PlayList_Fragment.mediaPlayer != null) {
            if (PlayList_Fragment.mediaPlayer.isPlaying()) PlayList_Fragment.mediaPlayer.pause();
            else PlayList_Fragment.mediaPlayer.start();
            updateMiniPlayer();
        }
    }

    public void switchToPlaylistTab() { if (viewPager != null) viewPager.setCurrentItem(0); }

    public void updateRecyclerViewSelection() {
        if (recyclerView != null && recyclerView.getAdapter() instanceof musicList_Recycler_Adapter) {
            ((musicList_Recycler_Adapter) recyclerView.getAdapter()).updateSelection(0);
        }
    }

    public void updateMiniPlayer() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            miniPlayerText.setText(current.songTitle);
            miniPlayer.setVisibility(VISIBLE);
            
            // MASTER CACHE CHECK: Try to apply cached color immediately for zero-lag switching
            int cachedColor = FavoritesDatabase.getInstance(this).getCachedColor(current.songPath);
            if (cachedColor != 0) {
                applyDynamicColorsToUI(cachedColor);
                Log.d("MainActivity", "Applied cached color for: " + current.songTitle);
            }
            // FLICKER FIX: Removed the immediate "else" reset to default red.
            // Keeping the current color until a new one is found prevents the "red flash" during transitions.
            
            updateMiniProfileImage(current);
            if (PlayList_Fragment.mediaPlayer != null) {
                miniProgressBar.setMax(PlayList_Fragment.mediaPlayer.getDuration());
                boolean isPlaying = PlayList_Fragment.mediaPlayer.isPlaying();
                miniPause.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play);
                if (isPlaying) updateMiniPlayerProgress();
                else miniProgressBar.setProgress(PlayList_Fragment.mediaPlayer.getCurrentPosition());
            }

            // Sync all active fragments with new metadata and playback state
            notifyFragments();
        }
    }

    /**
     * Iterates through all active fragments and triggers their respective sync methods.
     * This ensures that both Lyrics and Playlist fragments reflect the latest song data.
     */
    private void notifyFragments() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof Lyrics_Fragment) {
                ((Lyrics_Fragment) f).updateLyricsSync();
                ((Lyrics_Fragment) f).updateMiniPauseIcon();
            } else if (f instanceof PlayList_Fragment) {
                ((PlayList_Fragment) f).updatePauseIcon();
                ((PlayList_Fragment) f).syncUIWithCurrentSong();
            }
        }
    }

    private void updateMiniProfileImage(musicList_Structure song) {
        if (miniProfile == null || song == null) return;

        // BUG FIX: Explicitly clear the ImageView to avoid recycling artifacts
        Glide.with(this).clear(miniProfile);
        miniProfile.setImageResource(R.drawable.profile);

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
                // Dynamically set to Match Parent for real images to fill the mini player container (50dp)
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                miniProfile.setLayoutParams(params);
                if (resource instanceof BitmapDrawable) {
                    Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                    if (bitmap != null) {
                        // CACHE-FIRST RULE: Only extract color if not already in database
                        if (FavoritesDatabase.getInstance(MainActivity.this).getCachedColor(song.songPath) == 0) {
                            Palette.from(bitmap).setRegion(bitmap.getWidth()/4, bitmap.getHeight()/4, (3*bitmap.getWidth())/4, (3*bitmap.getHeight())/4).generate(palette -> {
                                if (palette != null) {
                                    int color = extractBestColor(palette);
                                    applyDynamicColorsToUI(color);
                                    // Save to Master Cache for future instant loads
                                    FavoritesDatabase.getInstance(MainActivity.this).saveColor(song.songPath, color);
                                }
                            });
                        } else {
                            Log.d("MainActivity", "Skipping Palette extraction, color already cached for: " + song.songTitle);
                        }
                    }
                }
            }
            @Override public void onLoadCleared(@Nullable Drawable placeholder) {
                miniProfile.setImageDrawable(placeholder);
            }

            @Override public void onLoadFailed(@Nullable Drawable errorDrawable) {
                miniProfile.setImageDrawable(errorDrawable);
                setDefaultMiniProfile();
                applyDynamicColorsToUI(Color.parseColor("#9D201A"));
            }
        });
    }

    public int extractBestColor(Palette palette) {
        int defaultValue = 0xFF9D201A;
        Palette.Swatch bestSwatch = palette.getVibrantSwatch();
        if (bestSwatch == null) bestSwatch = palette.getDominantSwatch();
        if (bestSwatch == null) bestSwatch = palette.getDarkVibrantSwatch();
        int targetColor = (bestSwatch != null) ? bestSwatch.getRgb() : defaultValue;
        float[] hsv = new float[3];
        Color.colorToHSV(targetColor, hsv);
        hsv[1] = Math.min(hsv[1] * 1.3f, 0.85f);
        hsv[2] = Math.max(Math.min(hsv[2], 0.45f), 0.18f);
        return Color.HSVToColor(hsv);
    }

    public void applyDynamicColorsToUI(int color) {
        lastDynamicColor = color;
        if (musicList_LinLayOut != null) musicList_LinLayOut.setBackgroundColor(color);
        if (mainLayout.getVisibility() == GONE) getWindow().setStatusBarColor(color);
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).updateInternalColors(color);
            else if (f instanceof PlayList_Fragment) ((PlayList_Fragment) f).updateInternalColors(color);
        }
    }

    private void setDefaultMiniProfile() {
        miniProfile.setImageResource(R.drawable.profile);
        int sizeInPx = (int) (25 * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams params = miniProfile.getLayoutParams();
        params.width = sizeInPx; params.height = sizeInPx;
        miniProfile.setLayoutParams(params);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        miniPlayerHandler.removeCallbacks(miniPlayerRunnable);
        executorService.shutdown();
        if (connectivityManager != null && networkCallback != null) connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> { if (isOfflineMode) { isOfflineMode = false; notifyLyricsOfNetworkChange(); } });
            }

            @Override public void onLost(@NonNull Network network) { runOnUiThread(() -> isOfflineMode = true); }
        };
        connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback);
    }

    private void notifyLyricsOfNetworkChange() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).retryFetchingIfEmpty();
        }
    }
    
    public void updateMiniPlayerProgress() {
        miniPlayerHandler.removeCallbacks(miniPlayerRunnable);
        miniPlayerHandler.post(miniPlayerRunnable);
    }

    public void openPlayerLayout() {
        if (miniPlayer != null) miniPlayer.setVisibility(GONE);
        mainLayout.setVisibility(GONE);
        musicList_LinLayOut.setVisibility(VISIBLE);
        applyDynamicColorsToUI(lastDynamicColor);
        displayTabLayOut();
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof PlayList_Fragment) ((PlayList_Fragment) f).updateSongFromAdapter();
            else if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).updateLyricsSync();
        }
    }

    private void closePlayerLayout() {
        Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down);
        slideDown.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) { mainLayout.setVisibility(VISIBLE); }
            @Override public void onAnimationEnd(Animation animation) { musicList_LinLayOut.setVisibility(GONE); }
            @Override public void onAnimationRepeat(Animation animation) {}
        });
        musicList_LinLayOut.startAnimation(slideDown);
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        else return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        String p = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{p}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startFullProcess();
    }

    public void displayTabLayOut(){
        if (viewPager.getAdapter() == null) {
            ViewPager2Adapter adapter = new ViewPager2Adapter(this);
            viewPager.setAdapter(adapter);

            tabItem1.setOnClickListener(v -> viewPager.setCurrentItem(0));
            tabItem2.setOnClickListener(v -> viewPager.setCurrentItem(1));

            tabItem1.post(() -> {
                indicator.getLayoutParams().width = tabItem1.getWidth();
                indicator.requestLayout();
            });

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                    indicator.setTranslationX((position + positionOffset) * tabItem1.getWidth());
                }
                @Override public void onPageSelected(int position) { updateTabStyles(position + 1); }
            });
        }
    }

    private void updateTabStyles(int tabNumber) {
        if (tabNumber == 1) {
            tabItem1.setTypeface(null, Typeface.BOLD);
            tabItem1.setTextColor(Color.WHITE);
            tabItem2.setTextColor(Color.parseColor("#99FFFFFF"));
            tabItem2.setTypeface(null, Typeface.NORMAL);
        }
        else {
            tabItem2.setTypeface(null, Typeface.BOLD);
            tabItem2.setTextColor(Color.WHITE);
            tabItem1.setTextColor(Color.parseColor("#99FFFFFF"));
            tabItem1.setTypeface(null, Typeface.NORMAL);
        }
    }


    /**
     * Directly opens the Synced Lyrics Editor by handling logic at the Activity level.
     * This ensures the editor works even if Lyrics_Fragment hasn't been created yet.
     */
    private void openSyncedLyricsEditorDirectly() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            FavoritesDatabase db = FavoritesDatabase.getInstance(this);
            String[] lyrics = db.getCachedLyrics(current.songPath);

            // Logic: If plain lyrics are missing, open Add Lyrics dialog via fragment trigger
            if (lyrics == null || lyrics[0] == null || lyrics[0].isEmpty()) {
                Toast.makeText(this, getString(R.string.please_add_plain_lyrics_first), Toast.LENGTH_SHORT).show();
                
                // Trigger Add Lyrics dialog by switching to Lyrics Fragment first
                viewPager.setCurrentItem(1, true);
                viewPager.postDelayed(() -> {
                    for (Fragment f : getSupportFragmentManager().getFragments()) {
                        if (f instanceof Lyrics_Fragment) ((Lyrics_Fragment) f).openAddLyricsDialog();
                    }
                }, 200);
            } else {
                Intent intent = new Intent(this, SyncedLyricsEditorActivity.class);
                startActivity(intent);
            }
        }
    }
}
