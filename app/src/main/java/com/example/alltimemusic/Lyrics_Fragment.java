package com.example.alltimemusic;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Lyrics_Fragment extends Fragment {

    private TextView lyricsTxt, song_name, artist_name, miniSongTitle;
    private ImageView miniPause;
    private ShapeableImageView miniProfile;
    private ProgressBar miniProgressBar;
    private View  topFadeView, bottomFadeView;
    private ScrollView plainLyricsScroll;
    private RecyclerView lyricsRecycler;
    private LyricsAdapter lyricsAdapter;
    private List<LyricLine> lyricLines = new ArrayList<>();
    private String currentPlainLyrics = "";
    private String currentSyncedLyrics = "";
    private String lastLoadedSongId = "";
    private boolean isSyncedMode = true;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler lyricsHandler = new Handler(Looper.getMainLooper());
    private final Runnable lyricsRunnable = new Runnable() {
        @Override
        public void run() {
            if (PlayList_Fragment.mediaPlayer != null) {
                try {
                    int currentPos = PlayList_Fragment.mediaPlayer.getCurrentPosition();
                    miniProgressBar.setProgress(currentPos);

                    if (isSyncedMode && !lyricLines.isEmpty()) {
                        updateActiveLyricLine(currentPos);
                    }

                    if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                        lyricsHandler.postDelayed(this, 200); // More frequent updates for smooth sync
                    }
                } catch (Exception e) {
                    Log.e("Lyrics_Fragment", "Error in lyricsRunnable", e);
                }
            }
        }
    };

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static final String ARG_PARAM3 = "param3";


    private String mParam1, mParam2, mParam3;

    public Lyrics_Fragment() {
        // Required empty public constructor
    }
    public static Lyrics_Fragment newInstance(String param1, String param2, String mParam3) {
        Lyrics_Fragment fragment = new Lyrics_Fragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        args.putString(ARG_PARAM3, mParam3);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
            mParam3 = getArguments().getString(ARG_PARAM3);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // MASTER SYNC FIX: Ensure lyrics are re-fetched from database if they were updated in the Editor.
        // This resolves the bug where old lyrics continued to show after editing.
        if (SyncedLyricsEditorActivity.shouldRefreshOnReturn) {
            SyncedLyricsEditorActivity.shouldRefreshOnReturn = false; // Reset the global signal flag
            lastLoadedSongId = ""; // Clear cached ID to force a fresh database fetch
            updateLyricsSync();
            Log.d("Lyrics_Fragment", "Force refreshed lyrics from database after Editor return.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        lyricsHandler.removeCallbacks(lyricsRunnable);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_lyrics_, container, false);

        song_name = view.findViewById(R.id.song_name_txt);
        artist_name = view.findViewById(R.id.artist_txt);
        lyricsTxt = view.findViewById(R.id.lyrics_txt);
        plainLyricsScroll = view.findViewById(R.id.plain_lyrics_scroll);
        lyricsRecycler = view.findViewById(R.id.lyrics_recycler);
        topFadeView = view.findViewById(R.id.lyrics_top_fade_view);
        bottomFadeView = view.findViewById(R.id.lyrics_bottom_fade_view);

        lyricsAdapter = new LyricsAdapter();
        lyricsAdapter.setOnLyricClickListener(timeMs -> {
            if (PlayList_Fragment.mediaPlayer != null) {
                PlayList_Fragment.mediaPlayer.seekTo(timeMs);
                if (!PlayList_Fragment.mediaPlayer.isPlaying()) {
                    PlayList_Fragment.mediaPlayer.start();
                    // Sync play/pause icons
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateMiniPlayer();
                    }
                    updateMiniPauseIcon();
                }
            }
        });
        lyricsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        lyricsRecycler.setAdapter(lyricsAdapter);

        // Initialize Mini Player views from included layout
        miniSongTitle = view.findViewById(R.id.dialog_txt);
        miniPause = view.findViewById(R.id.dialog_pause);
        miniProgressBar = view.findViewById(R.id.progressbar);
        miniProfile = view.findViewById(R.id.mini_profile);

        LinearLayout miniPlayerContainer = view.findViewById(R.id.dialog_res);
        miniPlayerContainer.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToPlaylistTab();
            }
        });

        song_name.setText(mParam1);
        artist_name.setText(mParam2);
        lyricsTxt.setText(mParam3);

        // Feature: Copy lyrics on long press (Plain Lyrics)
        lyricsTxt.setOnLongClickListener(v -> {
            copyToClipboard(lyricsTxt.getText().toString());
            return true;
        });

        // Feature: Copy lyrics on long press (Synced Lyrics)
        // Copy entire synced lyrics with timestamps as stored in DB
        lyricsAdapter.setOnLyricLongClickListener(text -> copyToClipboard(currentSyncedLyrics));

        // BUG FIX: Immediately sync fades with the current global dynamic color on view creation
        updateInternalColors(MainActivity.lastDynamicColor);

        updateMiniPlayerUI();

        miniPause.setOnClickListener(v -> {
            if (PlayList_Fragment.mediaPlayer != null) {
                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    PlayList_Fragment.mediaPlayer.pause();
                } else {
                    PlayList_Fragment.mediaPlayer.start();
                }
                // Sync all UI components
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).updateMiniPlayer();
                }
            }
        });

        return view;
    }

    public void updateLyricsSync() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null && song_name != null) {
            // BUG FIX: Prevent redundant fetches if the same song is re-clicked
            if (current.songPath.equals(lastLoadedSongId)) return;

            // Reset lyrics state for the new song
            currentPlainLyrics = "";
            currentSyncedLyrics = "";
            lyricLines.clear();
            lastLoadedSongId = current.songPath; // Using path as unique ID

            // Display the song title as is (preserving _ and -)
            song_name.setText(current.songTitle);

            // Display the smart cleaned artist
            String displayArtist = current.getCleanArtist();
            artist_name.setText(displayArtist);

            // BUG FIX: Ensure fades are synced even if the fragment was already created
            updateInternalColors(MainActivity.lastDynamicColor);

            //Display the cover art
            updateProfileImage(miniProfile, current);
            // Get duration if available
            int durationSeconds = 0;
            if (PlayList_Fragment.mediaPlayer != null) {
                durationSeconds = PlayList_Fragment.mediaPlayer.getDuration() / 1000;
            }

            // FEATURE: Check Offline Database Cache
            FavoritesDatabase db = FavoritesDatabase.getInstance(getContext());
            String[] cached = db.getCachedLyrics(current.songPath);

            if (cached != null) {
                // Use cached lyrics if available
                currentPlainLyrics = cached[0];
                currentSyncedLyrics = cached[1];

                if (isSyncedMode && !currentSyncedLyrics.isEmpty() && !currentSyncedLyrics.equalsIgnoreCase("null")) {
                    updateLyricsUI(currentSyncedLyrics);
                } else {
                    updateLyricsUI(currentPlainLyrics);
                }
            } else {
                // Check if in Offline Mode
                if (MainActivity.isOfflineMode) {
                    updateLyricsUI(getString(R.string.offline_lyrics_not_found_instructions));
                    showToast(getString(R.string.offline_lyrics_not_found));
                } else {
                    // Fetch lyrics online with a specific ID check
                    fetchLyricsOnline(current.songTitle, displayArtist, durationSeconds, current.songPath);
                }
            }

            miniSongTitle.setText(current.songTitle);
            if (PlayList_Fragment.mediaPlayer != null) {
                miniProgressBar.setMax(PlayList_Fragment.mediaPlayer.getDuration());
                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    miniPause.setImageResource(R.drawable.pause);
                } else {
                    miniPause.setImageResource(R.drawable.play);
                }
                startProgressUpdate();
            }
        }
    }

    /**
     * Called from MainActivity when a new color is extracted.
     * Updates internal views like Fades and backgrounds to maintain sync.
     */
    public void updateInternalColors(int color) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            // Update Top Fade with dynamic gradient
            if (topFadeView != null) {
                GradientDrawable topGd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {color, android.graphics.Color.TRANSPARENT}
                );
                topFadeView.setBackground(topGd);
            }
            // Update Bottom Fade with dynamic gradient
            if (bottomFadeView != null) {
                GradientDrawable bottomGd = new GradientDrawable(
                        GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[] {color, android.graphics.Color.TRANSPARENT}
                );
                bottomFadeView.setBackground(bottomGd);
            }
            // Roots are handled by MainActivity for simplicity
        });
    }

    private void updateProfileImage(ShapeableImageView profile_imageView, musicList_Structure song) {
        if (profile_imageView == null || song == null) return;

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, song.albumId);
        // Use Glide for efficient metadata image loading in lyrics mini player
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        profile_imageView.setImageDrawable(resource);
                        // Dynamically set to Match Parent for real images to fill the mini player container (50dp)
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        );
                        profile_imageView.setLayoutParams(params);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        profile_imageView.setImageDrawable(placeholder);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        profile_imageView.setImageDrawable(errorDrawable);
                        setDefaultProfileImage(profile_imageView);
                    }
                });
    }

    private void setDefaultProfileImage(ShapeableImageView profile_imageView) {
        profile_imageView.setImageResource(R.drawable.profile);

        // Dynamically set to 25dp for default image (lyrics_mini_profile) as requested by user
        int sizeInPx = (int) (25 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
        profile_imageView.setLayoutParams(params);
    }

    private void fetchLyricsOnline(String title, String artist, int duration, final String targetSongPath) {
        if (lyricsTxt == null) return;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (lyricsRecycler != null) lyricsRecycler.setVisibility(View.GONE);
                if (plainLyricsScroll != null) plainLyricsScroll.setVisibility(View.VISIBLE);
                lyricsTxt.setText(R.string.searching_for_lyrics);
            });
        }

        // Step 1: Accurate cleaning for search
        String cleanTitle = cleanString(title);
        String cleanArtist = cleanString(artist);
        Log.d("LyricsFetch", "🚀 [START] Fetching lyrics for: " + cleanTitle + " | Artist: " + cleanArtist + " | Duration: " + duration + "s");

        // Smart Split: If artist is empty/generic, try extracting from title
        if (cleanArtist.isEmpty() || cleanArtist.equalsIgnoreCase("unknown")) {
            String[] delims = {" _ ", " | ", " — ", " - ", " : ", " ~ "};
            for (String d : delims) {
                if (cleanTitle.contains(d)) {
                    String[] parts = cleanTitle.split(java.util.regex.Pattern.quote(d));
                    if (parts.length >= 2) {
                        cleanArtist = cleanString(parts[parts.length - 1]);
                        cleanTitle = cleanString(parts[0]);
                        Log.d("LyricsFetch", "💡 [SMART-SPLIT] Separated artist from title: " + cleanTitle + " | " + cleanArtist);
                        break;
                    }
                }
            }
        }

        final String finalArtist = cleanArtist;
        final String finalTitle = cleanTitle;

        // MULTI-STAGE FETCHING LOGIC
        // Priority 1: Exact matching with Duration (Only if Artist exists)
        if (!finalArtist.isEmpty() && !finalArtist.equalsIgnoreCase("unknown")) {
            Log.d("LyricsFetch", "🔍 [STAGE 1] Attempting Exact Match (api/get)...");
            HttpUrl getUrl = Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/get"))
                    .newBuilder()
                    .addQueryParameter("artist_name", finalArtist)
                    .addQueryParameter("track_name", finalTitle)
                    .addQueryParameter("duration", String.valueOf(duration))
                    .build();

            Request getRequest = new Request.Builder()
                    .url(getUrl)
                    .header("User-Agent", "AllTimeMusic/1.7")
                    .build();

            client.newCall(getRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d("LyricsFetch", "⚠️ [FAILED] Stage 1 (Network/Timeout). Moving to Stage 2...");
                    performBroadSearch(finalTitle, finalArtist, duration, targetSongPath);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response resp = response) {
                        if (resp.isSuccessful()) {
                            // IDE Fix: response.body() is guaranteed not null in OkHttp 4/5 for successful responses
                            String body = resp.body().string();
                            if (body.isEmpty()) {
                                Log.d("LyricsFetch", "⚠️ Stage 1 returned empty body");
                                performBroadSearch(finalTitle, finalArtist, duration, targetSongPath);
                                return;
                            }
                            JSONObject obj = new JSONObject(body);
                            String plain = obj.optString("plainLyrics", "");
                            String synced = obj.optString("syncedLyrics", "");

                            // NEW: Check Language AND enforce Synced Lyrics requirement
                            if (isSupportedLanguage(plain + synced)) {
                                if (!synced.isEmpty() && !synced.equalsIgnoreCase("null")) {
                                    Log.d("LyricsFetch", "✅ [SUCCESS] Stage 1: Synced Lyrics Found!");
                                    processLrcResult(obj, targetSongPath);
                                } else {
                                    Log.d("LyricsFetch", "⚠️ [PARTIAL] Stage 1 got result but NO Synced Lyrics. Trying Stage 2 Search...");
                                    performBroadSearch(finalTitle, finalArtist, duration, targetSongPath);
                                }
                            } else {
                                Log.d("LyricsFetch", "🚫 [REJECTED] Stage 1 result is in an unsupported language. Moving to Stage 2...");
                                performBroadSearch(finalTitle, finalArtist, duration, targetSongPath);
                            }
                        } else {
                            Log.d("LyricsFetch", "⚠️ [FAILED] Stage 1 Response Code: " + resp.code() + ". Moving to Stage 2...");
                            performBroadSearch(finalTitle, finalArtist, duration, targetSongPath);
                        }
                    } catch (Exception e) {
                        Log.e("LyricsFetch", "❌ [ERROR] Stage 1 Exception", e);
                        performBroadSearch(finalTitle, finalArtist, duration, targetSongPath);
                    }
                }
            });
        } else {
            Log.d("LyricsFetch", "⏭️ [SKIP] No Artist metadata. Skipping Stage 1, starting Stage 2...");
            performBroadSearch(finalTitle, "", duration, targetSongPath);
        }
    }

    private void performBroadSearch(String title, String artist, int targetDuration, String targetPath) {
        // Priority 2: Broad Search (Artist + Title or Title only)
        Log.d("LyricsFetch", "🔍 [STAGE 2] Broad Searching for: " + (artist + " " + title).trim());
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/search")).newBuilder();

        if (!artist.isEmpty()) {
            urlBuilder.addQueryParameter("artist_name", artist);
            urlBuilder.addQueryParameter("track_name", title);
        }
        urlBuilder.addQueryParameter("q", (artist + " " + title).trim());

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "AllTimeMusic/1.7")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("LyricsFetch", "❌ [FAILED] Stage 2 Network Error");
                fetchByTitleAndDuration(title, targetDuration, targetPath);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful()) {
                        JSONArray array = new JSONArray(resp.body().string());
                        Log.d("LyricsFetch", "📊 [RESULTS] Stage 2 returned " + array.length() + " matches. Filtering...");
                        if (array.length() > 0) {
                            JSONObject bestMatch = null;

                            // 1. High Priority: Duration Match +/- 2s AND Synced AND Supported Language
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject item = array.getJSONObject(i);
                                int itemDuration = item.optInt("duration", 0);
                                String synced = item.optString("syncedLyrics", "");
                                String plain = item.optString("plainLyrics", "");

                                if (Math.abs(itemDuration - targetDuration) <= 2 &&
                                        !synced.isEmpty() && !synced.equalsIgnoreCase("null") &&
                                        isSupportedLanguage(plain + synced)) {
                                    Log.d("LyricsFetch", "🎯 [FOUND] Best Match: Precision Duration (+/- 2s) + Synced Lyrics + Supported Language");
                                    bestMatch = item;
                                    break;
                                }
                            }

                            // 2. Medium Priority: Any result that has Synced Lyrics AND Supported Language
                            if (bestMatch == null) {
                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    String synced = item.optString("syncedLyrics", "");
                                    String plain = item.optString("plainLyrics", "");
                                    if (!synced.isEmpty() && !synced.equalsIgnoreCase("null") &&
                                            isSupportedLanguage(plain + synced)) {
                                        Log.d("LyricsFetch", "✅ [FOUND] Alternative Match: Synced Lyrics + Supported Language (Duration mismatch)");
                                        bestMatch = item;
                                        break;
                                    }
                                }
                            }

                            if (bestMatch != null) {
                                processLrcResult(bestMatch, targetPath);
                            } else {
                                Log.d("LyricsFetch", "🚫 [REJECTED] Stage 2 results failed language check. Moving to Title+Duration...");
                                fetchByTitleAndDuration(title, targetDuration, targetPath);
                            }
                        } else {
                            Log.d("LyricsFetch", "⚠️ [NO-RESULTS] Stage 2 empty. Moving to Title+Duration...");
                            fetchByTitleAndDuration(title, targetDuration, targetPath);
                        }
                    } else {
                        fetchByTitleAndDuration(title, targetDuration, targetPath);
                    }
                } catch (Exception e) {
                    Log.e("LyricsFetch", "❌ [ERROR] Stage 2 Processing Error", e);
                    fetchByTitleAndDuration(title, targetDuration, targetPath);
                }
            }
        });
    }

    private void fetchByTitleAndDuration(String title, int targetDuration, final String targetPath) {
        // Feature: Search using only title and duration (Useful when artist metadata is wrong)
        Log.d("LyricsFetch", "🔄 [STAGE 3] Title + Duration search for: " + title + " (" + targetDuration + "s)");
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/search"))
                .newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("q", title)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AllTimeMusic/1.7")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                fetchByTitleOnlyFallback(title, targetPath);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful()) {
                        JSONArray array = new JSONArray(resp.body().string());
                        JSONObject bestMatch = null;
                        
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.getJSONObject(i);
                            int itemDuration = item.optInt("duration", 0);
                            String plain = item.optString("plainLyrics", "");
                            String synced = item.optString("syncedLyrics", "");

                            // Filter by duration +/- 2 seconds (Strict) AND language AND enforce Synced
                            if (Math.abs(itemDuration - targetDuration) <= 2 && 
                                !synced.isEmpty() && !synced.equalsIgnoreCase("null") &&
                                isSupportedLanguage(plain + synced)) {
                                bestMatch = item;
                                Log.d("LyricsFetch", "🎯 [FOUND] Stage 3 Match: Title + Duration + Synced matched!");
                                break;
                            }
                        }

                        if (bestMatch != null) {
                            processLrcResult(bestMatch, targetPath);
                        } else {
                            fetchByTitleOnlyFallback(title, targetPath);
                        }
                    } else {
                        fetchByTitleOnlyFallback(title, targetPath);
                    }
                } catch (Exception e) {
                    fetchByTitleOnlyFallback(title, targetPath);
                }
            }
        });
    }


    private void fetchByTitleOnlyFallback(String title, final String targetPath) {
        // Feature: Final fallback stage for extreme cases
        Log.d("LyricsFetch", "🔄 [STAGE 3] Title-only fallback for: " + title);
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/search"))
                .newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("q", title)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AllTimeMusic/1.5")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (targetPath.equals(lastLoadedSongId)) updateLyricsUI(getString(R.string.lyrics_not_found_ui));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful()) {
                        JSONArray array = new JSONArray(resp.body().string());
                        if (array.length() > 0) {
                            // Prioritize synced even in title fallback
                            JSONObject bestMatch = null;
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject item = array.getJSONObject(i);
                                String plain = item.optString("plainLyrics", "");
                                String synced = item.optString("syncedLyrics", "");

                                if (!synced.isEmpty() && isSupportedLanguage(plain + synced)) {
                                    bestMatch = item;
                                    break;
                                }
                            }

                            // If no synced, find first supported plain
                            if (bestMatch == null) {
                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    if (isSupportedLanguage(item.optString("plainLyrics", "") + item.optString("syncedLyrics", ""))) {
                                        bestMatch = item;
                                        break;
                                    }
                                }
                            }

                            if (bestMatch != null) {
                                processLrcResult(bestMatch, targetPath);
                            } else {
                                if (targetPath.equals(lastLoadedSongId)) updateLyricsUI(getString(R.string.lyrics_unsupported_ui));
                            }
                        } else {
                            if (targetPath.equals(lastLoadedSongId))
                                updateLyricsUI(getString(R.string.lyrics_not_found_ui));
                        }
                    }
                } catch (Exception e) {
                    if (targetPath.equals(lastLoadedSongId))
                        updateLyricsUI(getString(R.string.lyrics_not_found_ui));
                }
            }
        });
    }

    private void processLrcResult(JSONObject obj, String targetPath) {
        String synced = obj.optString("syncedLyrics", "");
        String plain = obj.optString("plainLyrics", "");

        // Clean up "null" strings
        String finalPlain = (plain.isEmpty() || plain.equalsIgnoreCase("null")) ? "" : plain;
        String finalSynced = (synced.isEmpty() || synced.equalsIgnoreCase("null")) ? "" : synced;

        if (finalPlain.isEmpty() && finalSynced.isEmpty()) {
            if (targetPath.equals(lastLoadedSongId) && getActivity() != null) {
                getActivity().runOnUiThread(() -> updateLyricsUI(getString(R.string.lyrics_not_available_ui)));
            }
            return;
        }

        // Save to Database
        FavoritesDatabase db = FavoritesDatabase.getInstance(getContext());
        db.saveLyrics(targetPath, finalPlain, finalSynced);

        // Update UI if still active
        if (targetPath.equals(lastLoadedSongId) && getActivity() != null) {
            currentPlainLyrics = finalPlain;
            currentSyncedLyrics = finalSynced;
            getActivity().runOnUiThread(() -> {
                if (isSyncedMode && !currentSyncedLyrics.isEmpty()) {
                    updateLyricsUI(currentSyncedLyrics);
                } else if (!currentPlainLyrics.isEmpty()) {
                    if (isSyncedMode) showToast(getString(R.string.synced_lyrics_not_available_toast));
                    updateLyricsUI(currentPlainLyrics);
                }
            });
        }
    }

    private boolean isSupportedLanguage(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        // Remove all common symbols, digits, punctuation, and lyrics-specific characters (like music notes)
        // We only care about letters to determine the language.
        String clean = text.replaceAll("[\\s\\d\\p{P}\\p{S}♪\\[\\]\\.:\\-—_|~]", "");

        if (clean.isEmpty()) return true; // Just music notes or numbers is okay

        int supportedCount = 0;
        int unsupportedCount = 0;

        for (int i = 0; i < clean.length(); i++) {
            int codePoint = clean.codePointAt(i);
            
            // Script Detection:
            // 1. Latin (English, Hinglish, Spanish, etc.)
            // 2. Devanagari (Hindi, Marathi, etc.)
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            
            if (script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.DEVANAGARI) {
                supportedCount++;
            } else if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) {
                // If it's a specific other script like Japanese, Chinese, Cyrillic, Arabic etc.
                unsupportedCount++;
            }
            
            if (Character.isSupplementaryCodePoint(codePoint)) i++;
        }

        if (supportedCount == 0 && unsupportedCount > 0) return false;

        // Strict Threshold: At least 90% of characters must be from supported scripts
        // and we specifically block if there are more than 5 unsupported script characters
        boolean isValid = (float) supportedCount / (supportedCount + unsupportedCount) > 0.90 && unsupportedCount < 10;
        
        if (!isValid) {
            Log.d("LyricsFetch", "🚫 [REJECTED] Language Check Failed. Supported: " + supportedCount + ", Unsupported: " + unsupportedCount);
        }
        return isValid;
    }

    private void updateLyricsUI(String text) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (isSyncedMode && text != null && text.contains("[")) {
                // It's synced lyrics
                lyricLines = parseLRC(text);
                if (!lyricLines.isEmpty()) {
                    lastActiveIndex = -1;
                    if (plainLyricsScroll != null) plainLyricsScroll.setVisibility(View.GONE);
                    if (lyricsRecycler != null) {
                        lyricsRecycler.setVisibility(View.VISIBLE);
                        lyricsAdapter.setLyrics(lyricLines);
                    }
                    return;
                }
            }

            // Fallback to plain text
            if (lyricsRecycler != null) lyricsRecycler.setVisibility(View.GONE);
            if (plainLyricsScroll != null) {
                plainLyricsScroll.setVisibility(View.VISIBLE);
                String lyricsWithSpace = (text != null ? text : "") + "\n\n\n\n\n\n";
                lyricsTxt.setText(lyricsWithSpace);
            }
        });
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
                    String minStr = matcher.group(1);
                    String secStr = matcher.group(2);
                    String msStrRaw = matcher.group(3);
                    String textStr = matcher.group(4);

                    if (minStr == null || secStr == null || msStrRaw == null || textStr == null) continue;

                    long min = Long.parseLong(minStr);
                    long sec = Long.parseLong(secStr);
                    long ms = Long.parseLong(msStrRaw);
                    if (msStrRaw.length() == 2) ms *= 10;

                    long currentLineStartTime = (min * 60 * 1000) + (sec * 1000) + ms;
                    String text = textStr.trim();

                    if (!text.isEmpty()) {
                        lines.add(new LyricLine(currentLineStartTime, text));
                    }
                } catch (NumberFormatException e) {
                    Log.e("Lyrics_Fragment", "Error parsing LRC time", e);
                }
            }
        }
        return lines;
    }

    private int lastActiveIndex = -1;
    private void updateActiveLyricLine(int currentMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentMs >= lyricLines.get(i).getTimeMs()) {
                index = i;
            } else {
                break;
            }
        }

        if (index != -1 && index != lastActiveIndex) {
            lastActiveIndex = index;
            lyricsAdapter.setActiveIndex(index);

            // Scroll to position with an offset to keep it near center
            LinearLayoutManager layoutManager = (LinearLayoutManager) lyricsRecycler.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(index, lyricsRecycler.getHeight() / 3);
            }
        }
    }

    private String cleanString(String input) {
        if (input == null || input.equalsIgnoreCase("<unknown>")) return "";

        return input.replaceAll("(?i)\\[.*?]", "") // Remove everything in []
                .replaceAll("(?i)\\(.*?\\)", "") // Remove everything in ()
                .replaceAll("(?i)official audio|official video|full video|full audio|lyrical|audio|video|hd|4k|lyric video", "")
                .replaceAll("(?i)\\d{4}", "") // removes years
                .replaceAll("(?i)\\.mp3|\\.m4a|\\.wav|\\.flac", "")
                .replaceAll("(?i)\\.com|\\.to|\\.org|\\.net|\\.info|\\.me|\\.biz|\\.io", "") // Clean domains
                .replaceAll("(?i)PagalWorld\\.com|PagalWorld\\.pw|PagalWorld\\.com\\.se|PagalWorld|PaglaSongs|Pagalworld\\.org|PagalNew|KoshalWorld\\.Com", "")
                .replaceAll("^[-_ ]+|[-_ ]+$", "") // Remove leading/trailing hyphens, underscores, spaces
                .replaceAll("\\s+", " ") // Replace multiple spaces with one
                .trim();
    }

    public void toggleLyricsMode(boolean synced) {
        this.isSyncedMode = synced;
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (synced) {
                if (!currentSyncedLyrics.isEmpty() && !currentSyncedLyrics.equalsIgnoreCase("null")) {
                    updateLyricsUI(currentSyncedLyrics);
                    showToast(getString(R.string.synced_lyrics_enabled_toast));
                } else if (!currentPlainLyrics.isEmpty() && !currentPlainLyrics.equalsIgnoreCase("null")) {
                    updateLyricsUI(currentPlainLyrics);
                    showToast(getString(R.string.synced_lyrics_not_available_toast));
                } else {
                    showToast(getString(R.string.lyrics_not_available_ui));
                }
            } else {
                if (!currentPlainLyrics.isEmpty() && !currentPlainLyrics.equalsIgnoreCase("null")) {
                    updateLyricsUI(currentPlainLyrics);
                    showToast(getString(R.string.plain_lyrics_enabled_toast));
                } else {
                    showToast(getString(R.string.lyrics_not_available_ui));
                }
            }
        });
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            android.app.Activity activity = getActivity();
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
        }
    }

    private void updateMiniPlayerUI() {
        updateLyricsSync();
    }

    public void updateMiniPauseIcon() {
        if (PlayList_Fragment.mediaPlayer != null && miniPause != null) {
            miniPause.setImageResource(PlayList_Fragment.mediaPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);
            if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                startProgressUpdate();
            }
        }
    }

    private void startProgressUpdate() {
        lyricsHandler.removeCallbacks(lyricsRunnable);
        lyricsHandler.post(lyricsRunnable);
    }

    public void deleteLyricsFromDB() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            FavoritesDatabase db = FavoritesDatabase.getInstance(getContext());
            db.deleteLyrics(current.songPath);

            // Clear current state and UI
            currentPlainLyrics = "";
            currentSyncedLyrics = "";
            lyricLines.clear();

            if (MainActivity.isOfflineMode) {
                // 2. Offline Mode: Show manual instructions
                updateLyricsUI(getString(R.string.add_lyrics_manually_online_instructions));
            } else {
                // 1. Online Mode: Show loading and fetch again
                updateLyricsUI(getString(R.string.searching_for_lyrics));
                String displayArtist = current.getCleanArtist();
                int durationSeconds = 0;
                if (PlayList_Fragment.mediaPlayer != null) {
                    durationSeconds = PlayList_Fragment.mediaPlayer.getDuration() / 1000;
                }
                fetchLyricsOnline(current.songTitle, displayArtist, durationSeconds, current.songPath);
            }

            showToast(getString(R.string.lyrics_deleted));
        }
    }

    public void retryFetchingIfEmpty() {
        if (currentPlainLyrics.isEmpty() && currentSyncedLyrics.isEmpty()) {
            // Force a retry by clearing lastLoadedId and calling update
            lastLoadedSongId = "";
            updateLyricsSync();
        }
    }

    /**
     * Copies the provided text to the system clipboard and shows a toast confirmation.
     * 
     * @param text The text to be copied.
     * Uses: ClipboardManager, Toast feedback.
     */
    private void copyToClipboard(String text) {
        if (getContext() == null || text == null || text.isEmpty()) return;
        
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Lyrics", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            showToast("Lyrics Copied");
        }
    }

    // Open a professional BottomSheetDialog to manually add lyrics
    public void openAddLyricsDialog() {
        if (getContext() == null) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext(), R.style.BottomSheetDialogTheme);
        // BUG FIX: Prevent accidental closure by clicking outside or back button
        // This ensures the user doesn't lose their typed lyrics.
        bottomSheetDialog.setCanceledOnTouchOutside(false);
        bottomSheetDialog.setCancelable(false);

        // Note: Passing null for root is acceptable for BottomSheetDialog content, but we can use findViewById(android.R.id.content) if needed to silence the warning.
        // For fragments, it's safer to just let the dialog handle the layout.
        @SuppressLint("InflateParams")
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_lyrics, null);
        bottomSheetDialog.setContentView(dialogView);

        EditText input = dialogView.findViewById(R.id.lyrics_input);
        Button btnSave = dialogView.findViewById(R.id.btn_save_lyrics);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_lyrics);

        // Apply Dynamic Colors to Buttons
        int dynamicColor = MainActivity.lastDynamicColor;
        btnSave.setTextColor(dynamicColor);
        btnCancel.setTextColor(dynamicColor);
        // Note: User asked for textColor to be dynamic. 
        // If we want the background to be dynamic instead, we would use setBackgroundTintList.
        // But following user rule to keep text color dynamic.

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                showToast(getString(R.string.please_enter_some_lyrics));
                return;
            }

            // Language check for manual entry
            if (!isSupportedLanguage(text)) {
                showToast(getString(R.string.error_unsupported_language));
                return;
            }

            musicList_Structure current = musicList_Recycler_Adapter.currentItem;
            if (current != null && getContext() != null) {
                FavoritesDatabase db = FavoritesDatabase.getInstance(getContext());
                // Smart Logic: If text contains typical LRC timestamps, treat as Synced
                if (text.contains("[") && text.contains("]")) {
                    db.saveLyrics(current.songPath, "", text); // Save as Synced
                    showToast(getString(R.string.synced_lyrics_added));
                } else {
                    db.saveLyrics(current.songPath, text, ""); // Save as Plain
                    showToast(getString(R.string.plain_lyrics_added));
                }

                // Force UI refresh by resetting lastLoadedId
                lastLoadedSongId = "";
                updateLyricsSync();
                bottomSheetDialog.dismiss();
            }
        });

        bottomSheetDialog.show();
    }
}
