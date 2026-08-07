package com.example.alltimemusic;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import linc.com.amplituda.Amplituda;

@UnstableApi
public class SyncedLyricsEditorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditorLyricsAdapter adapter;
    private final List<LyricLine> lyricLines = new ArrayList<>();
    private TextView currentLineDisplay, currentTimeTxt;
    private SeekBar seekBar;
    private ImageView playPauseBtn, previewBtn, btnUndo, btnRedo;
    private Button btnSetTimestamp;
    private View centerLine, topFade, bottomFade, loadingLayout;
    private WaveformView waveformView;
    private musicList_Structure currentSong;
    private int selectedIndex = -1;
    private boolean isPreviewMode = false;
    private boolean isEditingSynced = true; 

    private MusicViewModel musicViewModel;

    private final Stack<List<LyricLine>> undoStack = new Stack<>();
    private final Stack<List<LyricLine>> redoStack = new Stack<>();

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (musicViewModel != null && musicViewModel.getIsPlaying().getValue() != null && musicViewModel.getIsPlaying().getValue()) {
                long currentPos = musicViewModel.getCurrentPosition().getValue() != null ? musicViewModel.getCurrentPosition().getValue() : 0;
                long duration = musicViewModel.getDuration().getValue() != null ? musicViewModel.getDuration().getValue() : 1;
                
                if (duration > 0) {
                    seekBar.setProgress((int) currentPos);
                    currentTimeTxt.setText(formatTime((int) currentPos));
                    waveformView.updateScroll((float) currentPos / duration);
                    if (isPreviewMode) updatePreviewLyrics((int) currentPos);
                }
                updateHandler.postDelayed(this, 50); // High-precision sync
            }
        }
    };

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synced_lyrics_editor);

        // BUG FIX: Immediate UI apply to prevent white/red flash
        int initialColor = getIntent().getIntExtra("initial_color", 0xFF9D201A);
        applyThemeColorToUI(initialColor);

        // Initialize Views
        recyclerView = findViewById(R.id.lyrics_edit_recycler);
        currentLineDisplay = findViewById(R.id.current_line_display);
        currentTimeTxt = findViewById(R.id.editor_current_time);
        TextView totalDurationTxt = findViewById(R.id.editor_total_duration);
        seekBar = findViewById(R.id.editor_seekbar);
        playPauseBtn = findViewById(R.id.editor_play_pause);
        previewBtn = findViewById(R.id.btn_preview_lyrics);
        btnSetTimestamp = findViewById(R.id.btn_set_timestamp);
        Button btnSave = findViewById(R.id.btn_save_synced);
        ImageView optionsMenuBtn = findViewById(R.id.editor_options_menu);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        centerLine = findViewById(R.id.center_line_indicator);
        topFade = findViewById(R.id.editor_top_fade);
        bottomFade = findViewById(R.id.editor_bottom_fade);
        waveformView = findViewById(R.id.waveform_view);
        loadingLayout = findViewById(R.id.waveform_loading_layout);
        ImageView backBtn = findViewById(R.id.back_btn_editor);

        currentSong = musicList_Recycler_Adapter.currentItem;

        if (currentSong == null) {
            finish();
            return;
        }

        musicViewModel = new ViewModelProvider(this).get(MusicViewModel.class);
        musicViewModel.initController(this);
        
        // Ensure ViewModel has the initial color before observing
        musicViewModel.setThemeColor(initialColor);

        observeViewModel();

        // Priority Loading
        loadLyricsWithPriority();
        
        // Start Waveform Scanning
        scanAudioForWaveform();

        // Setup RecyclerView
        adapter = new EditorLyricsAdapter();
        adapter.setLyrics(lyricLines);
        adapter.setListener(new EditorLyricsAdapter.OnEditorLyricActionListener() {
            @Override
            public void onLyricClick(int position, LyricLine line) {
                if (musicViewModel != null && line.getTimeMs() > 0) {
                    musicViewModel.seekTo(line.getTimeMs());
                }
                selectLine(position);
            }

            @Override
            public void onDeleteLine(int position) {
                vibrate(40);
                saveStateToUndo();
                if (selectedIndex == position) {
                    selectedIndex = -1;
                    adapter.setActiveIndex(-1);
                    currentLineDisplay.setText("Select a line to start syncing");
                } else if (selectedIndex > position) {
                    selectedIndex--;
                }
                lyricLines.remove(position);
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, lyricLines.size());
                Toast.makeText(SyncedLyricsEditorActivity.this, "Line Deleted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAddLineAfter(int position) {
                addNewLineDialog(position + 1, "");
            }

            @Override
            public void onAddMusicNoteAfter(int position) {
                saveStateToUndo();
                lyricLines.add(position + 1, new LyricLine(0, "♪"));
                adapter.notifyItemInserted(position + 1);
                adapter.notifyItemRangeChanged(position + 1, lyricLines.size());
            }

            @Override
            public void onEditLine(int position) {
                showEditDialog(position);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        waveformView.setOnWaveformScrollListener(new WaveformView.OnWaveformScrollListener() {
            @Override
            public void onWaveformScroll(float progress) {
                if (musicViewModel != null && musicViewModel.getDuration().getValue() != null) {
                    long duration = musicViewModel.getDuration().getValue();
                    musicViewModel.seekTo((long) (progress * duration));
                }
            }
            @Override public void onWaveformDragStart() {}
            @Override public void onWaveformDragEnd() {}
        });

        backBtn.setOnClickListener(v -> finish());

        playPauseBtn.setOnClickListener(v -> {
            if (musicViewModel != null) {
                musicViewModel.togglePlayPause();
                vibrate(30);
            }
        });

        btnSetTimestamp.setOnClickListener(v -> {
            if (selectedIndex != -1) {
                vibrate(50);
                saveStateToUndo();
                LyricLine currentLine = lyricLines.get(selectedIndex);
                if (currentLine.getTimeMs() > 0) {
                    lyricLines.set(selectedIndex, new LyricLine(0, currentLine.getText()));
                    updateTimestampButtonStyle(0);
                } else {
                    int currentPos = 0;
                    if (musicViewModel != null && musicViewModel.getCurrentPosition().getValue() != null) {
                        currentPos = musicViewModel.getCurrentPosition().getValue().intValue();
                    } else {
                        currentPos = seekBar.getProgress();
                    }
                    lyricLines.set(selectedIndex, new LyricLine(currentPos, currentLine.getText()));
                    updateTimestampButtonStyle(currentPos);
                    if (selectedIndex < lyricLines.size() - 1) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> selectLine(selectedIndex + 1), 300);
                    }
                }
                adapter.notifyItemChanged(selectedIndex);
            } else {
                Toast.makeText(this, "Please! Select Any Line", Toast.LENGTH_SHORT).show();
            }
        });

        btnUndo.setOnClickListener(v -> {
            vibrate(20);
            undo();
        });
        btnRedo.setOnClickListener(v -> {
            vibrate(20);
            redo();
        });

        previewBtn.setOnClickListener(v -> {
            vibrate(30);
            isPreviewMode = !isPreviewMode;
            if (isPreviewMode) {
                previewBtn.setImageResource(R.drawable.hide_preview);
                btnSetTimestamp.setVisibility(View.GONE);
                Toast.makeText(this, "Preview Mode ON", Toast.LENGTH_SHORT).show();
            } else {
                previewBtn.setImageResource(R.drawable.preview);
                btnSetTimestamp.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Preview Mode OFF", Toast.LENGTH_SHORT).show();
                selectedIndex = -1;
                adapter.setActiveIndex(-1);
                currentLineDisplay.setText("Select a line to start syncing");
            }
        });

        btnSave.setOnClickListener(v -> {
            saveToDatabase(true);
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof Lyrics_Fragment) {
                    ((Lyrics_Fragment) fragment).retryFetchingIfEmpty();
                }
            }
        });

        optionsMenuBtn.setOnClickListener(this::showOptionsMenu);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicViewModel != null) {
                    musicViewModel.seekTo(progress);
                    // BUG FIX: Instant Waveform Sync during user interaction
                    long duration = musicViewModel.getDuration().getValue() != null ? musicViewModel.getDuration().getValue() : 1;
                    waveformView.updateScroll((float) progress / duration);
                    currentTimeTxt.setText(formatTime(progress));
                    if (isPreviewMode) updatePreviewLyrics(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void observeViewModel() {
        // Core Metadata Sync
        musicViewModel.getCurrentSong().observe(this, song -> {
            if (song != null) {
                currentSong = song;
                loadLyricsWithPriority();
                scanAudioForWaveform();
                extractColorFromSong(song);
            }
        });

        // Dynamic Color Sync (Consolidated Single Observer)
        musicViewModel.getThemeColor().observe(this, this::applyThemeColorToUI);

        musicViewModel.getIsPlaying().observe(this, isPlaying -> {
            playPauseBtn.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play);
            if (isPlaying) {
                updateHandler.removeCallbacks(updateRunnable);
                updateHandler.post(updateRunnable);
            } else {
                updateHandler.removeCallbacks(updateRunnable);
            }
        });

        musicViewModel.getCurrentPosition().observe(this, pos -> {
            if (musicViewModel.getIsPlaying().getValue() == null || !musicViewModel.getIsPlaying().getValue()) {
                int currentPos = pos.intValue();
                seekBar.setProgress(currentPos);
                currentTimeTxt.setText(formatTime(currentPos));
                long duration = musicViewModel.getDuration().getValue() != null ? musicViewModel.getDuration().getValue() : 1;
                if (duration == 0) duration = 1;
                waveformView.updateScroll((float) currentPos / duration);
                if (isPreviewMode) updatePreviewLyrics(currentPos);
            }
        });

        musicViewModel.getDuration().observe(this, dur -> {
            seekBar.setMax(dur.intValue());
            TextView totalDurationTxt = findViewById(R.id.editor_total_duration);
            if (totalDurationTxt != null) totalDurationTxt.setText(formatTime(dur.intValue()));
        });
    }

    private void applyThemeColorToUI(int color) {
        View root = findViewById(R.id.editor_root);
        if (root != null) root.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);
        applyDynamicFades(color);
        if (seekBar != null) {
            seekBar.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            seekBar.getThumb().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }

    private void extractColorFromSong(musicList_Structure song) {
        if (song == null) return;
        
        // PIGGYBACK: Check cache first to avoid async extraction lag
        Integer cached = MusicViewModel.colorCache.get(song.songPath);
        if (cached != null) {
            musicViewModel.setThemeColor(cached);
            return; 
        }

        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, song.albumId);

        Glide.with(this)
                .load(uri)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        if (resource instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                            if (bitmap != null) {
                                int width = bitmap.getWidth();
                                int height = bitmap.getHeight();
                                // BUG FIX: Synchronized extraction region for perfect color consistency
                                Palette.from(bitmap)
                                    .setRegion(width/4, height/4, (3*width)/4, (3*height)/4)
                                    .generate(palette -> {
                                        if (palette != null) {
                                            Palette.Swatch swatch = palette.getVibrantSwatch();
                                            if (swatch == null) swatch = palette.getDominantSwatch();
                                            if (swatch != null) {
                                                float[] hsv = new float[3];
                                                Color.colorToHSV(swatch.getRgb(), hsv);
                                                hsv[1] = Math.min(hsv[1] * 1.3f, 0.85f);
                                                hsv[2] = Math.max(Math.min(hsv[2], 0.45f), 0.18f);
                                                musicViewModel.setThemeColor(Color.HSVToColor(hsv));
                                            }
                                        }
                                    });
                            }
                        }
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void applyDynamicFades(int color) {
        if (topFade != null) {
            GradientDrawable topGd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{color, android.graphics.Color.TRANSPARENT});
            topFade.setBackground(topGd);
        }
        if (bottomFade != null) {
            GradientDrawable bottomGd = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{color, Color.TRANSPARENT});
            bottomFade.setBackground(bottomGd);
        }
    }

    private void loadLyricsWithPriority() {
        AppDatabase.databaseExecutor.execute(() -> {
            LyricEntity lyrics = AppDatabase.getInstance(this).musicDao().getLyrics(currentSong.songPath);
            runOnUiThread(() -> {
                lyricLines.clear();
                if (lyrics != null) {
                    if (lyrics.syncedLyrics != null && !lyrics.syncedLyrics.isEmpty() && !lyrics.syncedLyrics.equalsIgnoreCase("null")) {
                        isEditingSynced = true;
                        parseLyricsToLines(lyrics.syncedLyrics);
                    } else if (lyrics.plainLyrics != null && !lyrics.plainLyrics.isEmpty() && !lyrics.plainLyrics.equalsIgnoreCase("null")) {
                        isEditingSynced = false;
                        parseLyricsToLines(lyrics.plainLyrics);
                    }
                }
            });
        });
    }

    private void parseLyricsToLines(String raw) {
        lyricLines.clear();
        if (raw == null || raw.isEmpty()) return;
        if (raw.contains("[")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");
            String[] split = raw.split("\n");
            for (String line : split) {
                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    long min = Long.parseLong(matcher.group(1));
                    long sec = Long.parseLong(matcher.group(2));
                    String msStr = matcher.group(3);
                    long ms = Long.parseLong(msStr);
                    if (msStr.length() == 2) ms *= 10;
                    long time = (min * 60 * 1000) + (sec * 1000) + ms;
                    lyricLines.add(new LyricLine(time, matcher.group(4).trim()));
                } else if (!line.trim().isEmpty()) {
                    lyricLines.add(new LyricLine(0, line.trim()));
                }
            }
        } else {
            String[] lines = raw.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) lyricLines.add(new LyricLine(0, line.trim()));
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showOptionsMenu(View view) {
        CustomPopupMenu popup = new CustomPopupMenu(this, view);
        popup.setItemTextColor(MainActivity.lastDynamicColor);
        popup.addMenuItem("Edit Plain Lyrics");
        popup.addMenuItem("Edit Synced Lyrics");
        
        popup.setOnItemClickListener(title -> {
            AppDatabase.databaseExecutor.execute(() -> {
                LyricEntity lyrics = AppDatabase.getInstance(this).musicDao().getLyrics(currentSong.songPath);
                runOnUiThread(() -> {
                    if (title.equals("Edit Plain Lyrics")) {
                        isEditingSynced = false;
                        if (lyrics != null) parseLyricsToLines(lyrics.plainLyrics);
                    } else if (title.equals("Edit Synced Lyrics")) {
                        isEditingSynced = true;
                        if (lyrics != null) parseLyricsToLines(lyrics.syncedLyrics);
                    }
                });
            });
        });
        popup.show(view);
    }

    private void selectLine(int index) {
        if (isPreviewMode) return;
        selectedIndex = index;
        adapter.setActiveIndex(index);
        LyricLine selectedLine = lyricLines.get(index);
        currentLineDisplay.setText(selectedLine.getText());
        updateTimestampButtonStyle(selectedLine.getTimeMs());
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(index, (recyclerView.getHeight() / 2) - 60);
        }
    }

    private void updateTimestampButtonStyle(long timeMs) {
        if (timeMs > 0) {
            btnSetTimestamp.setText("Clear Time Stamp");
            btnSetTimestamp.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        } else {
            btnSetTimestamp.setText("Set Time Stamp");
            btnSetTimestamp.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9D201A")));
        }
    }

    private void vibrate(long ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }
    }

    private void updatePreviewLyrics(int currentMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentMs >= lyricLines.get(i).getTimeMs()) index = i;
            else break;
        }
        if (index != -1 && index != selectedIndex) {
            selectedIndex = index;
            adapter.setActiveIndex(index);
            currentLineDisplay.setText(lyricLines.get(index).getText());
            recyclerView.smoothScrollToPosition(index);
        }
    }

    private void showEditDialog(int index) {
        saveStateToUndo();
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Please! Edit Your Lyric.");
        final EditText input = new EditText(this);
        input.setText(lyricLines.get(index).getText());
        input.setPadding(40, 40, 40, 40);
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty()) {
                LyricLine old = lyricLines.get(index);
                lyricLines.set(index, new LyricLine(old.getTimeMs(), newText));
                adapter.notifyItemChanged(index);
                if (index == selectedIndex) currentLineDisplay.setText(newText);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void addNewLineDialog(int index, String initialText) {
        saveStateToUndo();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Please! Enter Your Lyric.");
        final EditText input = new EditText(this);
        input.setText(initialText);
        input.setPadding(40, 40, 40, 40);
        builder.setView(input);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty()) {
                lyricLines.add(index, new LyricLine(0, newText));
                adapter.notifyItemInserted(index);
                adapter.notifyItemRangeChanged(index, lyricLines.size());
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveStateToUndo() {
        List<LyricLine> copy = new ArrayList<>();
        for (LyricLine line : lyricLines) copy.add(new LyricLine(line.getTimeMs(), line.getText()));
        undoStack.push(copy);
        redoStack.clear();
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            List<LyricLine> current = new ArrayList<>();
            for (LyricLine line : lyricLines) current.add(new LyricLine(line.getTimeMs(), line.getText()));
            redoStack.push(current);
            lyricLines.clear();
            lyricLines.addAll(undoStack.pop());
            adapter.notifyDataSetChanged();
            if (selectedIndex != -1) selectLine(Math.min(selectedIndex, lyricLines.size()-1));
        } else {
            Toast.makeText(this, "Nothing To Undo", Toast.LENGTH_SHORT).show();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            List<LyricLine> current = new ArrayList<>();
            for (LyricLine line : lyricLines) current.add(new LyricLine(line.getTimeMs(), line.getText()));
            undoStack.push(current);
            lyricLines.clear();
            lyricLines.addAll(redoStack.pop());
            adapter.notifyDataSetChanged();
            if (selectedIndex != -1) selectLine(Math.min(selectedIndex, lyricLines.size()-1));
        } else {
            Toast.makeText(this, "Nothing to Redo", Toast.LENGTH_SHORT).show();
        }
    }

    private void scanAudioForWaveform() {
        runOnUiThread(() -> loadingLayout.setVisibility(View.VISIBLE));
        Amplituda amplituda = new Amplituda(this);
        amplituda.processAudio(currentSong.songPath)
                .get(result -> {
                    List<Integer> rawAmplitudes = result.amplitudesAsList();
                    int totalRaw = rawAmplitudes.size();
                    int targetPoints = 4000;
                    float[] finalPeaks = new float[targetPoints];
                    for (int i = 0; i < targetPoints; i++) {
                        int rawIndex = (int) ((i / (float) targetPoints) * totalRaw);
                        if (rawIndex < totalRaw) {
                            float val = rawAmplitudes.get(rawIndex) / 100f;
                            finalPeaks[i] = Math.max(0.015f, Math.min(1.0f, (float) Math.pow(val, 0.9f) * 1.2f));
                        } else {
                            finalPeaks[i] = 0.015f;
                        }
                    }
                    runOnUiThread(() -> {
                        waveformView.setAmplitudes(finalPeaks);
                        loadingLayout.setVisibility(View.GONE);
                        if (centerLine != null) centerLine.setVisibility(View.VISIBLE);
                    });
                }, exception -> {
                    exception.printStackTrace();
                    runOnUiThread(() -> {
                        loadingLayout.setVisibility(View.GONE);
                        Toast.makeText(this, "Failed to load waveform", Toast.LENGTH_SHORT).show();
                    });
                });
    }

    private void saveToDatabase(boolean showToast) {
        StringBuilder syncedBuilder = new StringBuilder();
        for (LyricLine line : lyricLines) {
            long time = line.getTimeMs();
            long min = (time / 1000) / 60;
            long sec = (time / 1000) % 60;
            long ms = (time % 1000) / 10;
            syncedBuilder.append(String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, ms)).append(line.getText()).append("\n");
        }
        
        AppDatabase.databaseExecutor.execute(() -> {
            AppDatabase.getInstance(this).musicDao().insertLyrics(new LyricEntity(currentSong.songPath, null, syncedBuilder.toString()));
            if (showToast) {
                runOnUiThread(() -> Toast.makeText(this, "Lyrics Synced & Saved!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateHandler.removeCallbacks(updateRunnable);
    }
}
