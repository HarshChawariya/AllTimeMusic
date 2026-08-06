package com.example.alltimemusic;

import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

@UnstableApi
public class Lyrics_Fragment extends Fragment {

    private TextView lyricsTxt, song_name, artist_name, miniSongTitle;
    private ImageView miniPause;
    private ShapeableImageView miniProfile;
    private ProgressBar miniProgressBar;
    private View  topFadeView, bottomFadeView;
    private ScrollView plainLyricsScroll;
    private RecyclerView lyricsRecycler;
    private LyricsAdapter lyricsAdapter;
    private MusicViewModel musicViewModel;
    private List<LyricLine> lyricLines = new ArrayList<>();
    private String currentPlainLyrics = "";
    private String currentSyncedLyrics = "";
    private String lastLoadedSongId = "";
    private boolean isSyncedMode = true;
    private final OkHttpClient client = new OkHttpClient();

    private int lastActiveIndex = -1;

    public Lyrics_Fragment() {}

    public static Lyrics_Fragment newInstance(String param1, String param2, String mParam3) {
        Lyrics_Fragment fragment = new Lyrics_Fragment();
        Bundle args = new Bundle();
        args.putString("p1", param1);
        args.putString("p2", param2);
        args.putString("p3", mParam3);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lyrics_, container, false);

        song_name = view.findViewById(R.id.song_name_txt);
        artist_name = view.findViewById(R.id.artist_txt);
        lyricsTxt = view.findViewById(R.id.lyrics_txt);
        plainLyricsScroll = view.findViewById(R.id.plain_lyrics_scroll);
        lyricsRecycler = view.findViewById(R.id.lyrics_recycler);
        topFadeView = view.findViewById(R.id.lyrics_top_fade_view);
        bottomFadeView = view.findViewById(R.id.lyrics_bottom_fade_view);
        
        lyricsAdapter = new LyricsAdapter();
        lyricsAdapter.setOnLyricClickListener(timeMs -> {if (musicViewModel != null) musicViewModel.seekTo(timeMs);});

        lyricsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        lyricsRecycler.setAdapter(lyricsAdapter);
        
        miniSongTitle = view.findViewById(R.id.dialog_txt);
        miniPause = view.findViewById(R.id.dialog_pause);
        miniProgressBar = view.findViewById(R.id.progressbar);
        miniProfile = view.findViewById(R.id.mini_profile);

        LinearLayout miniPlayerContainer = view.findViewById(R.id.dialog_res);

        miniPlayerContainer.setOnClickListener(v -> {if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).switchToPlaylistTab();});

        musicViewModel = new ViewModelProvider(requireActivity()).get(MusicViewModel.class);
        observeViewModel();
        
        updateInternalColors(MainActivity.lastDynamicColor);

        miniPause.setOnClickListener(v -> {
            if (musicViewModel != null) musicViewModel.togglePlayPause();
        });

        return view;
    }

    public void updateLyricsSync(musicList_Structure current) {
        if (current == null || song_name == null) return;
        if (current.songPath.equals(lastLoadedSongId)) return;

        currentPlainLyrics = "";
        currentSyncedLyrics = "";
        lyricLines.clear();
        lastLoadedSongId = current.songPath;

        song_name.setText(current.songTitle);
        artist_name.setText(current.getCleanArtist());
        updateProfileImage(miniProfile, current);
        
        FavoritesDatabase db = new FavoritesDatabase(getContext());
        String[] cached = db.getCachedLyrics(current.songPath);
        
        if (cached != null) {
            currentPlainLyrics = cached[0];
            currentSyncedLyrics = cached[1];
            if (isSyncedMode && !currentSyncedLyrics.isEmpty() && !currentSyncedLyrics.equalsIgnoreCase("null")) {
                updateLyricsUI(currentSyncedLyrics);
            } else {
                updateLyricsUI(currentPlainLyrics);
            }
        } else {
            if (MainActivity.isOfflineMode) {
                updateLyricsUI("Offline Lyrics Not Found.\nSwitch to Online Mode to fetch.");
            } else {
                fetchLyricsOnline(current.songTitle, current.getCleanArtist(), current.songPath);
            }
        }
        miniSongTitle.setText(current.songTitle);
    }

    public void updateInternalColors(int color) {
        View root = getView();
        if (root != null) {
            root.setBackgroundColor(color);
        }

        // Update Top Fade with dynamic gradient
        if (topFadeView != null) {
            topFadeView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{color, Color.TRANSPARENT}));
        }
        if (bottomFadeView != null) {
            bottomFadeView.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{color, Color.TRANSPARENT}));
        }
    }

    private void updateProfileImage(ShapeableImageView profile_imageView, musicList_Structure song) {
        if (profile_imageView == null || song == null) return;

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
                        // Programmatically set MATCH_PARENT to fill the 50dp container fully
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
                        // Set specific size for default icon to prevent overflow
                        int sizeInPx = (int) (25 * getResources().getDisplayMetrics().density);
                        ViewGroup.LayoutParams params = profile_imageView.getLayoutParams();
                        params.width = sizeInPx;
                        params.height = sizeInPx;
                        profile_imageView.setLayoutParams(params);
                    }
                });
    }

    private void fetchLyricsOnline(String title, String artist, final String targetSongPath) {
        if (lyricsTxt == null) return;
        updateLyricsUI("Loading lyrics...");
        
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/search"))
                .newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("q", artist + " " + title)
                .build();

        Request request = new Request
                .Builder()
                .url(url)
                .header("User-Agent", "AllTimeMusic/1.0")
                .build();

        client.newCall(request)
                .enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (targetSongPath.equals(lastLoadedSongId)) updateLyricsUI("Check internet connection.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        JSONArray jsonArray = new JSONArray(response.body().string());
                        if (jsonArray.length() > 0) {
                            JSONObject obj = jsonArray.getJSONObject(0);
                            String plain = obj.optString("plainLyrics", "");
                            String synced = obj.optString("syncedLyrics", "");

                            FavoritesDatabase db = new FavoritesDatabase(getContext());
                            db.saveLyrics(targetSongPath, plain, synced);

                            if (targetSongPath.equals(lastLoadedSongId)) {
                                currentPlainLyrics = plain;
                                currentSyncedLyrics = synced;
                                updateLyricsUI(isSyncedMode && !synced.isEmpty() ? synced : plain);
                            }
                        } else {
                            if (targetSongPath.equals(lastLoadedSongId))
                                updateLyricsUI("Lyrics not found.");
                        }
                    }
                } catch (Exception e) {
                    if (targetSongPath.equals(lastLoadedSongId))
                        updateLyricsUI("Error parsing lyrics.");
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
            if (lyricsRecycler != null) lyricsRecycler.setVisibility(View.GONE);
            if (plainLyricsScroll != null) {
                plainLyricsScroll.setVisibility(View.VISIBLE);
                lyricsTxt.setText(text + "\n\n\n\n\n\n");
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

    private void updateActiveLyricLine(int currentMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentMs >= lyricLines.get(i).getTimeMs()) index = i;
            else break;
        }
        if (index != -1 && index != lastActiveIndex) {
            lastActiveIndex = index;
            lyricsAdapter.setActiveIndex(index);
            LinearLayoutManager lm = (LinearLayoutManager) lyricsRecycler.getLayoutManager();
            if (lm != null) lm.scrollToPositionWithOffset(index, lyricsRecycler.getHeight() / 3);
        }
    }

    private void observeViewModel() {
        musicViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (song != null) updateLyricsSync(song);
        });

        musicViewModel.getIsPlaying().observe(getViewLifecycleOwner(), isPlaying -> {
            if (miniPause != null) miniPause.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play);
        });

        musicViewModel.getThemeColor().observe(getViewLifecycleOwner(), this::updateInternalColors);

        musicViewModel.getCurrentPosition().observe(getViewLifecycleOwner(), pos -> {
            if (miniProgressBar != null) miniProgressBar.setProgress(pos.intValue());
            if (isSyncedMode && !lyricLines.isEmpty()) updateActiveLyricLine(pos.intValue());
        });

        musicViewModel.getDuration().observe(getViewLifecycleOwner(), dur -> {
            if (miniProgressBar != null) miniProgressBar.setMax(dur.intValue());
        });
    }

    public void toggleLyricsMode(boolean synced) {
        this.isSyncedMode = synced;
        updateLyricsUI(synced && !currentSyncedLyrics.isEmpty() ? currentSyncedLyrics : currentPlainLyrics);
    }

    public void deleteLyricsFromDB() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            new FavoritesDatabase(getContext()).deleteLyrics(current.songPath);
            lastLoadedSongId = "";
            updateLyricsSync(current);
            Toast.makeText(getContext(), "Lyrics Deleted", Toast.LENGTH_SHORT).show();
        }
    }

    public void retryFetchingIfEmpty() {
        if (currentPlainLyrics.isEmpty() && currentSyncedLyrics.isEmpty()) {
            lastLoadedSongId = "";
            musicList_Structure current = musicList_Recycler_Adapter.currentItem;
            if (current != null) updateLyricsSync(current);
        }
    }

    public void openAddLyricsDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext(), R.style.BottomSheetDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_add_lyrics, null);
        dialog.setContentView(v);
        android.widget.EditText input = v.findViewById(R.id.lyrics_input);
        v.findViewById(R.id.btn_save_lyrics).setOnClickListener(view -> {
            String text = input.getText().toString().trim();
            musicList_Structure current = musicList_Recycler_Adapter.currentItem;
            if (current != null && !text.isEmpty()) {
                new FavoritesDatabase(getContext()).saveLyrics(current.songPath, text, "");
                lastLoadedSongId = "";
                updateLyricsSync(current);
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    public void openSyncedLyricsEditor() {
        musicList_Structure current = musicList_Recycler_Adapter.currentItem;
        if (current != null) {
            String[] lyrics = new FavoritesDatabase(getContext()).getCachedLyrics(current.songPath);

            if (lyrics == null || lyrics[0] == null || lyrics[0].isEmpty()) {
                openAddLyricsDialog();
            } else {
                Intent intent = new Intent(getContext(), SyncedLyricsEditorActivity.class);
                intent.putExtra("initial_color", MainActivity.lastDynamicColor);
                startActivity(intent);
            }
        }
    }
}
