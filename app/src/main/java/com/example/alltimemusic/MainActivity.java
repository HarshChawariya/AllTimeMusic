package com.example.alltimemusic;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView alphabet;
    ArrayList<musicList_Structure> musicList = new ArrayList<>();
    private static final int PERMISSION_REQUEST_CODE = 1234;
    public static boolean isReturningFromLiked = false;
    public static boolean isOfflineMode = false; // Flag for Offline Mode
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private TextView tabItem1, tabItem2;
    private View indicator;
    private ViewPager2 viewPager;
    private ImageView miniPause, imgThreeDot;
    private com.google.android.material.imageview.ShapeableImageView miniProfile;
    private ProgressBar miniProgressBar;
    LinearLayout mainLayout, musicList_LinLayOut, miniPlayer, TabLayout_LinearLayout;
    TextView miniPlayerText;

    private final Handler miniPlayerHandler = new Handler(Looper.getMainLooper());
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

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

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
        imgThreeDot = findViewById(R.id.img_three_dot);
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
        miniPause.setOnClickListener(v -> toggleMusic());

        imgThreeDot.setOnClickListener(v -> {
            CustomPopupMenu popup = new CustomPopupMenu(this, v);
            popup.addMenuItem("Plain Lyrics");
            popup.addMenuItem("Synced Lyrics");
            
            // Only show Add/Delete Lyrics if we are on the Lyrics tab (index 1)
            if (viewPager.getCurrentItem() == 1) {
                musicList_Structure current = musicList_Recycler_Adapter.currentItem;
                if (current != null) {
                    FavoritesDatabase db = new FavoritesDatabase(this);
                    String[] lyrics = db.getCachedLyrics(current.songPath);

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
            
            // Toggle between Online/Offline Mode labels
            String modeOption = isOfflineMode ? "Offline Mode" : "Online Mode";
            popup.addMenuItem(modeOption);

            popup.setOnItemClickListener(title -> {
                if (title.equals("Plain Lyrics") || title.equals("Synced Lyrics")) {
                    boolean isSynced = title.equals("Synced Lyrics");
                    for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                        if (fragment instanceof Lyrics_Fragment) {
                            ((Lyrics_Fragment) fragment).toggleLyricsMode(isSynced);
                        }
                    }
                } else if (title.equals("Add Lyrics")) {
                    for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                        if (fragment instanceof Lyrics_Fragment) {
                            ((Lyrics_Fragment) fragment).openAddLyricsDialog();
                        }
                    }
                } else if (title.equals("Delete Lyrics")) {
                    for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                        if (fragment instanceof Lyrics_Fragment) {
                            ((Lyrics_Fragment) fragment).deleteLyricsFromDB();
                        }
                    }
                } else if (title.equals("Offline Mode") || title.equals("Online Mode")) {
                    // Manual click: Just show the current state toast as requested
                    String message = isOfflineMode ? "You're Offline" : "You're Online";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (musicList != null && !musicList.isEmpty()) {
            new FavoritesDatabase(this);
            for (musicList_Structure song : musicList) {
                song.isFavourite = FavoritesDatabase.favoriteList.stream()
                        .anyMatch(fav -> Objects.equals(fav.songPath, song.songPath));
            }
            if (recyclerView.getAdapter() != null) {
                recyclerView.getAdapter().notifyDataSetChanged();
            }
        }
        updateMiniPlayer();
        
        if (isReturningFromLiked) {
            isReturningFromLiked = false;
            openPlayerLayout();
        }
    }

    private void handleBackAction() {
        closePlayerLayout();
        updateMiniPlayer();
        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private void toggleMusic() {
        if (PlayList_Fragment.mediaPlayer != null) {
            if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                PlayList_Fragment.mediaPlayer.pause();
                miniPause.setImageResource(R.drawable.play);
            } else {
                PlayList_Fragment.mediaPlayer.start();
                miniPause.setImageResource(R.drawable.pause);
                updateMiniPlayerProgress();
            }
        }
    }

    public void switchToPlaylistTab() {
        if (viewPager != null) viewPager.setCurrentItem(0);
    }

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
            
            updateMiniProfileImage(current);
            
            if (PlayList_Fragment.mediaPlayer != null) {
                miniProgressBar.setMax(PlayList_Fragment.mediaPlayer.getDuration());
                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    miniPause.setImageResource(R.drawable.pause);
                    updateMiniPlayerProgress();
                } else {
                    miniPause.setImageResource(R.drawable.play);
                    if (PlayList_Fragment.mediaPlayer.getCurrentPosition() >= PlayList_Fragment.mediaPlayer.getDuration() - 1000) {
                        miniProgressBar.setProgress(PlayList_Fragment.mediaPlayer.getDuration());
                    } else {
                        miniProgressBar.setProgress(PlayList_Fragment.mediaPlayer.getCurrentPosition());
                    }
                }
            }

            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof Lyrics_Fragment) {
                    ((Lyrics_Fragment) fragment).updateLyricsSync();
                }
            }
        }
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
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.drawable.Drawable resource, @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                        miniProfile.setImageDrawable(resource);
                        // Extract dominant color for Spotify-like gradient
                        /*Palette.from(resource).generate(palette -> {
                                    if (palette != null) {
                                        int dominantColor = palette.getVibrantColor(palette.getDominantColor(0xFF9D201A));
                                        TabLayout_LinearLayout.setBackgroundColor(dominantColor);
                                    }
                                });*/
                        ViewGroup.LayoutParams params = miniProfile.getLayoutParams();
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
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
                       // TabLayout_LinearLayout.setBackgroundColor(0xFF9D201A);
                    }
                });
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
        miniPlayerHandler.removeCallbacks(miniPlayerRunnable);
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
    
    public void updateMiniPlayerProgress() {
        miniPlayerHandler.removeCallbacks(miniPlayerRunnable);
        miniPlayerHandler.post(miniPlayerRunnable);
    }

    public void openPlayerLayout() {
        if (miniPlayer != null) miniPlayer.setVisibility(GONE);
        mainLayout.setVisibility(GONE);
        musicList_LinLayOut.setVisibility(VISIBLE);
        displayTabLayOut();

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof PlayList_Fragment) {
                ((PlayList_Fragment) fragment).updateSongFromAdapter();
            } else if (fragment instanceof Lyrics_Fragment) {
                ((Lyrics_Fragment) fragment).updateLyricsSync();
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
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
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
                int displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);

                new FavoritesDatabase(this);

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
