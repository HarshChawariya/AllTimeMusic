package com.example.alltimemusic;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
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
                    e.printStackTrace();
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
            FavoritesDatabase db = new FavoritesDatabase(getContext());
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
                    updateLyricsUI("Offline Lyrics Not Found.\nSwitch to Online Mode to fetch.");
                    showToast("Offline Lyrics Not Found");
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
                android.graphics.drawable.GradientDrawable topGd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {color, android.graphics.Color.TRANSPARENT}
                );
                topFadeView.setBackground(topGd);
            }
            // Update Bottom Fade with dynamic gradient
            if (bottomFadeView != null) {
                android.graphics.drawable.GradientDrawable bottomGd = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                        new int[] {color, android.graphics.Color.TRANSPARENT}
                );
                bottomFadeView.setBackground(bottomGd);
            }
            // Roots are handled by MainActivity for simplicity
        });
    }

    private void updateProfileImage(ShapeableImageView profile_imageView, musicList_Structure song) {
        if (profile_imageView == null || song == null) return;

        android.net.Uri sArtworkUri = android.net.Uri.parse("content://media/external/audio/albumart");
        android.net.Uri uri = android.content.ContentUris.withAppendedId(sArtworkUri, song.albumId);
        // Use Glide for efficient metadata image loading in lyrics mini player
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .transform(new CenterCrop())
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.drawable.Drawable resource, @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                        profile_imageView.setImageDrawable(resource);
                        // Dynamically set to Match Parent for real images to fill the mini player container (50dp)
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        );
                        profile_imageView.setLayoutParams(params);
                    }

                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                        profile_imageView.setImageDrawable(placeholder);
                    }

                    @Override
                    public void onLoadFailed(@androidx.annotation.Nullable android.graphics.drawable.Drawable errorDrawable) {
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
                lyricsTxt.setText(R.string.lyrics_will_be_appearing);
            });
        }
        
        // Use cleanString for API search processing
        String cleanTitle = cleanString(title);
        String cleanArtist = cleanString(artist);
        
        // Smart Split: If artist is empty/unknown/generic, try to get it from title
        if (cleanArtist.isEmpty() || cleanArtist.equalsIgnoreCase("unknown") || cleanArtist.contains("Unknown Artist")) {
            String[] delims = {" _ ", " | ", " — ", " - ", " : ", " ~ "};
            for (String d : delims) {
                if (cleanTitle.contains(d)) {
                    String[] parts = cleanTitle.split(java.util.regex.Pattern.quote(d));
                    if (parts.length >= 2) {
                        // Take the last part as Artist and remaining as Title
                        cleanArtist = cleanString(parts[parts.length - 1]);
                        
                        StringBuilder titleBuilder = new StringBuilder();
                        for (int i = 0; i < parts.length - 1; i++) {
                            titleBuilder.append(parts[i]);
                            if (i < parts.length - 2) titleBuilder.append(d);
                        }
                        cleanTitle = cleanString(titleBuilder.toString());
                        break;
                    }
                }
            }
        }
        
        final String finalArtist = cleanArtist;
        final String finalTitle = cleanTitle;

        // API search logic
        HttpUrl baseUrl = HttpUrl.parse("https://lrclib.net/api/search");
        if (baseUrl == null) {
            updateLyricsUI("Error: Invalid API URL");
            return;
        }
        
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
        
        // Super-clean titles only for the API request
        String apiTitle = finalTitle.replace("_", " ").replace("|", " ").replace("-", " ");
        String apiArtist = finalArtist.replace("_", " ").replace("|", " ").replace("-", " ");
        
        urlBuilder.addQueryParameter("track_name", apiTitle);
        if (!apiArtist.isEmpty()) {
            urlBuilder.addQueryParameter("artist_name", apiArtist);
        }
        
        // Add duration to improve matching accuracy
        if (duration > 0) {
            urlBuilder.addQueryParameter("duration", String.valueOf(duration));
        }

        urlBuilder.addQueryParameter("q", (apiArtist + " " + apiTitle).trim());
        
        HttpUrl url = urlBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AllTimeMusic/1.0 (https://github.com/HarshChawariya/AllTimeMusic)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (targetSongPath.equals(lastLoadedSongId)) {
                    // Show offline instructions instead of generic connection error
                    updateLyricsUI("Add lyrics manually.\n\t\tor\nGo to online mode.");
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String jsonData = response.body().string();
                        JSONArray jsonArray = new JSONArray(jsonData);
                        
                        if (jsonArray.length() > 0) {
                            String tempPlain = "";
                            String tempSynced = "";
                            
                            // 1. If Synced Mode is ON, search for a result that HAS synced lyrics
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject obj = jsonArray.getJSONObject(i);
                                String synced = obj.optString("syncedLyrics", "");
                                if (!synced.isEmpty() && !synced.equalsIgnoreCase("null")) {
                                    tempSynced = synced;
                                    tempPlain = obj.optString("plainLyrics", "");
                                    Log.d("Lyrics_Fragment Method fetchLyricsOnline()", "Found Synced Lyrics: " + tempSynced);
                                    break;
                                }
                            }
                            
                            // 2. Fallback: If no synced lyrics found take first plain
                            if (tempPlain.isEmpty() || tempPlain.equalsIgnoreCase("null")) {
                                JSONObject obj = jsonArray.getJSONObject(0);
                                tempPlain = obj.optString("plainLyrics", "");
                                tempSynced = obj.optString("syncedLyrics", "");
                            }
                            
                            if ((tempPlain.isEmpty() || tempPlain.equalsIgnoreCase("null")) && 
                                (tempSynced.isEmpty() || tempSynced.equalsIgnoreCase("null"))) {
                                if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Lyrics found but text is empty.");
                            } else {
                                // Clean up data before saving (replace "null" string with actual empty string)
                                String finalPlain = tempPlain.equalsIgnoreCase("null") ? "" : tempPlain;
                                String finalSynced = tempSynced.equalsIgnoreCase("null") ? "" : tempSynced;

                                // IMPORTANT: Save using the path this request was intended for
                                FavoritesDatabase db = new FavoritesDatabase(getContext());
                                db.saveLyrics(targetSongPath, finalPlain, finalSynced);

                                // Update UI ONLY if this is still the active song
                                if (targetSongPath.equals(lastLoadedSongId)) {
                                    currentPlainLyrics = finalPlain;
                                    currentSyncedLyrics = finalSynced;
                                    if (isSyncedMode && !currentSyncedLyrics.isEmpty()) {
                                        updateLyricsUI(currentSyncedLyrics);
                                    } else {
                                        if (isSyncedMode) showToast("Synced lyrics not available");
                                        updateLyricsUI(currentPlainLyrics);
                                    }
                                }
                            }
                        } else {
                            fetchLyricsByTitleOnly(finalTitle, targetSongPath);
                        }
                    } else {
                        if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Lyrics not available.");
                    }
                } catch (Exception e) {
                    if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Error parsing lyrics.");
                } finally {
                    if (response.body() != null) response.close();
                }
            }
        });
    }

    private void fetchLyricsByTitleOnly(String title, final String targetSongPath) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/search"))
                .newBuilder()
                .addQueryParameter("q", title)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AllTimeMusic/1.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Lyrics not found.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONArray array = new JSONArray(response.body().string());
                        if (array.length() > 0) {
                            JSONObject obj = array.getJSONObject(0);
                            String synced = obj.optString("syncedLyrics", "");
                            String plain = obj.optString("plainLyrics", "");

                            Log.d("Lyrics_Fragment Method fetchLyricsByTitleOnly()", "Found Synced Lyrics: " + synced);

                            // Clean up "null" strings before saving
                            String finalPlain = plain.equalsIgnoreCase("null") ? "" : plain;
                            String finalSynced = synced.equalsIgnoreCase("null") ? "" : synced;

                            // Save to correct path
                            FavoritesDatabase db = new FavoritesDatabase(getContext());
                            db.saveLyrics(targetSongPath, finalPlain, finalSynced);

                            if (targetSongPath.equals(lastLoadedSongId)) {
                                currentSyncedLyrics = finalSynced;
                                currentPlainLyrics = finalPlain;
                                if (isSyncedMode && !currentSyncedLyrics.isEmpty()) {
                                    updateLyricsUI(currentSyncedLyrics);
                                } else if (!currentPlainLyrics.isEmpty()) {
                                    if (isSyncedMode) showToast("Synced lyrics not available");
                                    updateLyricsUI(currentPlainLyrics);
                                } else {
                                    updateLyricsUI("Lyrics not available.");
                                }
                            }
                        } else {
                            if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Lyrics not found.");
                        }
                    } else {
                        if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Lyrics not found.");
                    }
                } catch (Exception e) {
                    if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Lyrics not found.");
                } finally {
                    if (response.body() != null) response.close();
                }
            }
        });
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
                    e.printStackTrace();
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
                    showToast("Synced Lyrics Enabled");
                } else if (!currentPlainLyrics.isEmpty() && !currentPlainLyrics.equalsIgnoreCase("null")) {
                    updateLyricsUI(currentPlainLyrics);
                    showToast("Synced lyrics not available");
                } else {
                    showToast("Lyrics not available");
                }
            } else {
                if (!currentPlainLyrics.isEmpty() && !currentPlainLyrics.equalsIgnoreCase("null")) {
                    updateLyricsUI(currentPlainLyrics);
                    showToast("Plain Lyrics Enabled");
                } else {
                    showToast("Lyrics not available");
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
            FavoritesDatabase db = new FavoritesDatabase(getContext());
            db.deleteLyrics(current.songPath);

            // Clear current state and UI
            currentPlainLyrics = "";
            currentSyncedLyrics = "";
            lyricLines.clear();
            
            if (MainActivity.isOfflineMode) {
                // 2. Offline Mode: Show manual instructions
                updateLyricsUI("Add lyrics manually.\n\t\t\tor\nGo to online mode.");
            } else {
                // 1. Online Mode: Show loading and fetch again
                updateLyricsUI(getString(R.string.lyrics_will_be_appearing));
                String displayArtist = current.getCleanArtist();
                int durationSeconds = 0;
                if (PlayList_Fragment.mediaPlayer != null) {
                    durationSeconds = PlayList_Fragment.mediaPlayer.getDuration() / 1000;
                }
                fetchLyricsOnline(current.songTitle, displayArtist, durationSeconds, current.songPath);
            }

            showToast("Lyrics Deleted");
        }
    }

    public void retryFetchingIfEmpty() {
        if (currentPlainLyrics.isEmpty() && currentSyncedLyrics.isEmpty()) {
            // Force a retry by clearing lastLoadedId and calling update
            lastLoadedSongId = "";
            updateLyricsSync();
        }
    }

    // Open a professional BottomSheetDialog to manually add lyrics
    public void openAddLyricsDialog() {
        if (getContext() == null) return;

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext(), R.style.BottomSheetDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_lyrics, null);
        bottomSheetDialog.setContentView(dialogView);

        android.widget.EditText input = dialogView.findViewById(R.id.lyrics_input);
        android.widget.Button btnSave = dialogView.findViewById(R.id.btn_save_lyrics);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel_lyrics);

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                showToast("Please enter some lyrics");
                return;
            }

            musicList_Structure current = musicList_Recycler_Adapter.currentItem;
            if (current != null && getContext() != null) {
                FavoritesDatabase db = new FavoritesDatabase(getContext());
                
                // Smart Logic: If text contains typical LRC timestamps, treat as Synced
                if (text.contains("[") && text.contains("]")) {
                    db.saveLyrics(current.songPath, "", text); // Save as Synced
                    showToast("Synced Lyrics Added");
                } else {
                    db.saveLyrics(current.songPath, text, ""); // Save as Plain
                    showToast("Plain Lyrics Added");
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
