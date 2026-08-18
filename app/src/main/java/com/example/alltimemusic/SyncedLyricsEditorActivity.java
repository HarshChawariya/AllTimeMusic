package com.example.alltimemusic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

import linc.com.amplituda.Amplituda;

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
    private boolean isEditingSynced = true; // Priority: Synced by default

    private final Stack<List<LyricLine>> undoStack = new Stack<>();
    private final Stack<List<LyricLine>> redoStack = new Stack<>();

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (PlayList_Fragment.mediaPlayer != null) {
                try {
                    int currentPos = PlayList_Fragment.mediaPlayer.getCurrentPosition();
                    int duration = PlayList_Fragment.mediaPlayer.getDuration();
                    
                    if (duration > 0) {
                        seekBar.setProgress(currentPos);
                        currentTimeTxt.setText(formatTime(currentPos));
                        
                        float progress = (float) currentPos / duration;
                        waveformView.updateScroll(progress);
                        
                        if (isPreviewMode) {
                            updatePreviewLyrics(currentPos);
                        }
                    }

                    if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                        updateHandler.postDelayed(this, 50);
                    }
                } catch (Exception e) {
                    // Handle state errors gracefully
                }
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

        // Setup UI Background Sync
        int dynamicColor = MainActivity.lastDynamicColor;
        findViewById(R.id.editor_root).setBackgroundColor(dynamicColor);
        getWindow().setStatusBarColor(dynamicColor);

        // Apply Dynamic Fades
        applyDynamicFades(dynamicColor);

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
                // Tap on lyrics to seek
                if (PlayList_Fragment.mediaPlayer != null) {
                    if (line.getTimeMs() > 0) {
                        int time = (int) line.getTimeMs();
                        PlayList_Fragment.mediaPlayer.seekTo(time);
                        
                        // Rule: Sync all UI components immediately
                        seekBar.setProgress(time);
                        currentTimeTxt.setText(formatTime(time));
                        
                        int duration = PlayList_Fragment.mediaPlayer.getDuration();
                        if (duration > 0) {
                            waveformView.updateScroll((float) time / duration);
                        }
                        
                        if (!PlayList_Fragment.mediaPlayer.isPlaying()) {
                            PlayList_Fragment.mediaPlayer.start();
                        }
                        updatePauseIcon();
                        
                        // Restart update loop for smooth progress
                        updateHandler.removeCallbacks(updateRunnable);
                        updateHandler.post(updateRunnable);
                    }
                }
                selectLine(position);
            }

            @Override
            public void onDeleteLine(int position) {
                vibrate(40);
                saveStateToUndo();
                
                // Safety Fix: If deleting the currently selected line, reset selection state
                if (selectedIndex == position) {
                    selectedIndex = -1;
                    adapter.setActiveIndex(-1);
                    currentLineDisplay.setText(getString(R.string.select_line_to_start));
                } else if (selectedIndex > position) {
                    selectedIndex--; // Maintain correct index after removal
                }

                lyricLines.remove(position);
                
                // SYNC FIX: Update adapter internal list via DiffUtil for clean animation
                adapter.setLyrics(lyricLines);
                
                // UI RECOVERY: If a different line was selected, ensure it stays highlighted
                if (selectedIndex != -1 && selectedIndex < lyricLines.size()) {
                    selectLine(selectedIndex);
                }

                Toast.makeText(SyncedLyricsEditorActivity.this, getString(R.string.line_deleted_toast), Toast.LENGTH_SHORT).show();

                /*
                // Manual notifications removed in favor of DiffUtil consistency
                // adapter.notifyItemRemoved(position);
                // adapter.notifyItemRangeChanged(position, lyricLines.size());
                */
            }

            @Override
            public void onAddLineAfter(int position) {
                // Add new lyric between lines
                addNewLineDialog(position + 1, "");
            }

            @Override
            public void onAddMusicNoteAfter(int position) {

                // Fetch current playback position for the musical note
                int currentPos = 0;
                if (PlayList_Fragment.mediaPlayer != null) {
                    try {
                        currentPos = PlayList_Fragment.mediaPlayer.getCurrentPosition();
                    } catch (IllegalStateException e) {
                        // Fallback to seekbar if player is in weird state
                        currentPos = seekBar.getProgress();
                    }
                } else {
                    currentPos = seekBar.getProgress();
                }

                // DUPLICATE PREVENTION: Check if a note/line already exists at this exact timestamp
                for (LyricLine line : lyricLines) {
                    if (line.getTimeMs() == currentPos) {
                        Toast.makeText(SyncedLyricsEditorActivity.this, "MusicalNote Already Exist", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // Add single music note (♪) with current timestamp between lines
                saveStateToUndo();
                LyricLine noteLine = new LyricLine(currentPos, "♪");
                lyricLines.add(position + 1, noteLine);

                // AUTOMATIC SORTING: Sort the list by timestamp to maintain chronological LRC order
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    lyricLines.sort((a, b) -> Long.compare(a.getTimeMs(), b.getTimeMs()));
                } else {
                    Collections.sort(lyricLines, (a, b) -> Long.compare(a.getTimeMs(), b.getTimeMs()));
                }

                // REFRESH ADAPTER: Sync activity list with adapter's internal copy via DiffUtil
                adapter.setLyrics(lyricLines);

                // UI SYNC: Find the new index after sorting and highlight it
                int newIndex = lyricLines.indexOf(noteLine);
                if (newIndex != -1) {
                    selectLine(newIndex);
                }

                /*
                // Add single music note (♪) between lines
                saveStateToUndo();
                lyricLines.add(position + 1, new LyricLine(0, "♪"));
                adapter.notifyItemInserted(position + 1);
                adapter.notifyItemRangeChanged(position + 1, lyricLines.size());
                */
            }

            @Override
            public void onEditLine(int position) {
                showEditDialog(position);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Setup Media Player Sync
        if (PlayList_Fragment.mediaPlayer != null) {
            int duration = PlayList_Fragment.mediaPlayer.getDuration();
            seekBar.setMax(duration);
            totalDurationTxt.setText(formatTime(duration));
            updatePauseIcon();
        }

        // Waveform Scroll Listener
        waveformView.setOnWaveformScrollListener(new WaveformView.OnWaveformScrollListener() {
            @Override
            public void onWaveformScroll(float progress) {
                if (PlayList_Fragment.mediaPlayer != null) {
                    int duration = PlayList_Fragment.mediaPlayer.getDuration();
                    int newPos = (int) (progress * duration);
                    PlayList_Fragment.mediaPlayer.seekTo(newPos);
                    seekBar.setProgress(newPos);
                    currentTimeTxt.setText(formatTime(newPos));
                    
                    if (isPreviewMode) {
                        updatePreviewLyrics(newPos);
                    }
                }
            }

            @Override
            public void onWaveformDragStart() {}

            @Override
            public void onWaveformDragEnd() {}
        });

        // Listeners
        backBtn.setOnClickListener(v -> finish());

        playPauseBtn.setOnClickListener(v -> {
            if (PlayList_Fragment.mediaPlayer != null) {
                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    PlayList_Fragment.mediaPlayer.pause();
                    updateHandler.removeCallbacks(updateRunnable);
                } else {
                    PlayList_Fragment.mediaPlayer.start();
                    updateHandler.removeCallbacks(updateRunnable);
                    updateHandler.post(updateRunnable);
                }
                updatePauseIcon();
                vibrate(30);
            }
        });

        btnSetTimestamp.setOnClickListener(v -> {
            if (selectedIndex != -1) {
                vibrate(50);
                saveStateToUndo();
                LyricLine currentLine = lyricLines.get(selectedIndex);
                
                // Toggle Logic: Set if 0, Clear if > 0
                if (currentLine.getTimeMs() > 0) {
                    lyricLines.set(selectedIndex, new LyricLine(0, currentLine.getText()));
                    updateTimestampButtonStyle(0);
                    Toast.makeText(this, "Timestamp Removed", Toast.LENGTH_SHORT).show();
                } else {
                    // BUG FIX: Ensure we can set timestamp even when paused
                    int currentPos = 0;
                    if (PlayList_Fragment.mediaPlayer != null) {
                        try {
                            currentPos = PlayList_Fragment.mediaPlayer.getCurrentPosition();
                        } catch (IllegalStateException e) {
                            // Fallback to seekbar if player is in weird state
                            currentPos = seekBar.getProgress();
                        }
                    } else {
                        currentPos = seekBar.getProgress();
                    }

                    lyricLines.set(selectedIndex, new LyricLine(currentPos, currentLine.getText()));
                    updateTimestampButtonStyle(currentPos);
                    
                    // Auto-scroll logic (Wait a bit so user sees the change)
                    if (selectedIndex < lyricLines.size() - 1) {
                        new Handler().postDelayed(() -> selectLine(selectedIndex + 1), 300);
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

        // Remove redundant Zoom Button listeners as we now use Gestures
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
            
            // BUG FIX: Instant Refresh in Fragments
            // This is a critical step to notify Fragments about data change
            for (androidx.fragment.app.Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof Lyrics_Fragment) {
                    ((Lyrics_Fragment) fragment).retryFetchingIfEmpty(); // Custom method for refresh
                }
            }
            // If they are static fragments in activity, we might need a more direct call or callback
            // Since I cannot modify MainActivity easily here, I'll rely on common fragment access.
        });

        optionsMenuBtn.setOnClickListener(this::showOptionsMenu);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && PlayList_Fragment.mediaPlayer != null) {
                    PlayList_Fragment.mediaPlayer.seekTo(progress);
                    currentTimeTxt.setText(formatTime(progress));
                    
                    // Rule: Sync waves with seekbar sliding
                    int duration = PlayList_Fragment.mediaPlayer.getDuration();
                    if (duration > 0) {
                        float waveProgress = (float) progress / duration;
                        waveformView.updateScroll(waveProgress);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updateHandler.post(updateRunnable);
    }

    private void applyDynamicFades(int color) {
        if (topFade != null) {
            GradientDrawable topGd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{color, Color.TRANSPARENT}
            );
            topFade.setBackground(topGd);
        }
        if (bottomFade != null) {
            GradientDrawable bottomGd = new GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{color, Color.TRANSPARENT}
            );
            bottomFade.setBackground(bottomGd);
        }
    }

    private void loadLyricsWithPriority() {
        FavoritesDatabase db = FavoritesDatabase.getInstance(this);
            String[] lyrics = db.getCachedLyrics(currentSong.songPath);
            lyricLines.clear();

            if (lyrics != null) {
                String synced = lyrics[1];
                String plain = lyrics[0];

                if (synced != null && !synced.isEmpty() && !synced.equalsIgnoreCase("null")) {
                    // Priority 1: Synced Lyrics
                    isEditingSynced = true;
                    parseLyricsToLines(synced);
                } else if (plain != null && !plain.isEmpty() && !plain.equalsIgnoreCase("null")) {
                    // Priority 2: Plain Lyrics
                    isEditingSynced = false;
                    parseLyricsToLines(plain);
                }
            }

    }

    private void parseLyricsToLines(String raw) {
        lyricLines.clear();
        if (raw == null || raw.isEmpty()) return;

        if (raw.contains("[")) {
            // It's synced format [mm:ss.xx]Text
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");
            String[] split = raw.split("\n");
            for (String line : split) {
                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    long min = Long.parseLong(Objects.requireNonNull(matcher.group(1)));
                    long sec = Long.parseLong(Objects.requireNonNull(matcher.group(2)));
                    String msStr = matcher.group(3);
                    long ms = Long.parseLong(Objects.requireNonNull(msStr));
                    if (msStr.length() == 2) ms *= 10;
                    long time = (min * 60 * 1000) + (sec * 1000) + ms;
                    lyricLines.add(new LyricLine(time, Objects.requireNonNull(matcher.group(4)).trim()));
                } else if (!line.trim().isEmpty()) {
                    // Fallback for lines without tags in synced file
                    lyricLines.add(new LyricLine(0, line.trim()));
                }
            }
        } else {
            // It's plain format
            String[] lines = raw.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    lyricLines.add(new LyricLine(0, line.trim()));
                }
            }
        }
        if (adapter != null) adapter.setLyrics(lyricLines);
    }

    private void showOptionsMenu(View view) {
        CustomPopupMenu popup = new CustomPopupMenu(this, view);
        popup.setItemTextColor(MainActivity.lastDynamicColor);
        popup.addMenuItem(getString(R.string.edit_plain_lyrics));
        popup.addMenuItem(getString(R.string.edit_synced_lyrics));
        
        popup.setOnItemClickListener(title -> {
            FavoritesDatabase db = FavoritesDatabase.getInstance(this);
                String[] lyrics = db.getCachedLyrics(currentSong.songPath);

                if (title.equals(getString(R.string.edit_plain_lyrics))) {
                    isEditingSynced = false;
                    if (lyrics != null) parseLyricsToLines(lyrics[0]);
                } else if (title.equals(getString(R.string.edit_synced_lyrics))) {
                    isEditingSynced = true;
                    if (lyrics != null) parseLyricsToLines(lyrics[1]);
                }

        });
        popup.show(view);
    }

    private void loadPlainLyrics() {
        FavoritesDatabase db = FavoritesDatabase.getInstance(this);
            String[] lyrics = db.getCachedLyrics(currentSong.songPath);
            if (lyrics != null && lyrics[0] != null && !lyrics[0].isEmpty()) {
                String plain = lyrics[0];
                String[] lines = plain.split("\n");
                lyricLines.clear();
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        lyricLines.add(new LyricLine(0, line.trim()));
                    }
                }
            }

    }

    private void selectLine(int index) {
        if (isPreviewMode) return;
        selectedIndex = index;
        adapter.setActiveIndex(index);
        LyricLine selectedLine = lyricLines.get(index);
        currentLineDisplay.setText(selectedLine.getText());
        
        updateTimestampButtonStyle(selectedLine.getTimeMs());
        
        // Center the active line vertically (Suggestion #2)
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            int offset = (recyclerView.getHeight() / 2) - 60; // Approximate center
            layoutManager.scrollToPositionWithOffset(index, offset);
        }
    }

    private void updateTimestampButtonStyle(long timeMs) {
        if (timeMs > 0) {
            btnSetTimestamp.setText(getString(R.string.clear_time_stamp));
            btnSetTimestamp.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        } else {
            btnSetTimestamp.setText(getString(R.string.set_time_stamp));
            btnSetTimestamp.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#9D201A")));
        }
    }

    private void vibrate(long ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }
    }

    private void updatePreviewLyrics(int currentMs) {
        int index = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (currentMs >= lyricLines.get(i).getTimeMs()) {
                index = i;
            } else {
                break;
            }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.edit_lyric_line_dialog));
        
        final EditText input = new EditText(this);
        input.setText(lyricLines.get(index).getText());
        input.setPadding(40, 40, 40, 40);
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.save_btn), (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty()) {
                LyricLine old = lyricLines.get(index);
                lyricLines.set(index, new LyricLine(old.getTimeMs(), newText));
                adapter.notifyItemChanged(index);
                if (index == selectedIndex) currentLineDisplay.setText(newText);
            }
        });
        builder.setNegativeButton(getString(R.string.cancel_btn), (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void addNewLineDialog(int index, String initialText) {
        saveStateToUndo();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.add_lyric_line_dialog));
        
        // UI FIX: Use the themed context from the builder to prevent styling warnings and leaks instead "this"
        //final EditText input = new EditText(this);
        final EditText input = new EditText(builder.getContext());
        input.setText(initialText);
        input.setPadding(40, 40, 40, 40);
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.add_btn), (dialog, which) -> {
            String newText = input.getText().toString().trim();
            if (!newText.isEmpty()) {
                lyricLines.add(index, new LyricLine(0, newText));
                
                // SYNC FIX: Use DiffUtil-powered setter instead of manual notifications
                // to prevent list mismatch and ensure immediate visibility.
                adapter.setLyrics(lyricLines);
                
                // UI FIX: Instantly highlight and scroll to the new line
                selectLine(index);

                /*
                // Manual notifications removed in favor of DiffUtil consistency
                // adapter.notifyItemInserted(index);
                // adapter.notifyItemRangeChanged(index, lyricLines.size());
                */
            }
        });
        builder.setNegativeButton(getString(R.string.cancel_btn), (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveStateToUndo() {
        List<LyricLine> copy = new ArrayList<>();
        for (LyricLine line : lyricLines) {
            copy.add(new LyricLine(line.getTimeMs(), line.getText()));
        }
        undoStack.push(copy);
        redoStack.clear();
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            List<LyricLine> current = new ArrayList<>();
            for (LyricLine line : lyricLines) {
                current.add(new LyricLine(line.getTimeMs(), line.getText()));
            }
            redoStack.push(current);
            
            lyricLines.clear();
            lyricLines.addAll(undoStack.pop());
            adapter.setLyrics(lyricLines);
            if (selectedIndex != -1) selectLine(Math.min(selectedIndex, lyricLines.size()-1));
        } else {
            Toast.makeText(this, getString(R.string.nothing_to_undo), Toast.LENGTH_SHORT).show();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            List<LyricLine> current = new ArrayList<>();
            for (LyricLine line : lyricLines) {
                current.add(new LyricLine(line.getTimeMs(), line.getText()));
            }
            undoStack.push(current);
            
            lyricLines.clear();
            lyricLines.addAll(redoStack.pop());
            adapter.setLyrics(lyricLines);
            if (selectedIndex != -1) selectLine(Math.min(selectedIndex, lyricLines.size()-1));
        } else {
            Toast.makeText(this, getString(R.string.nothing_to_redo), Toast.LENGTH_SHORT).show();
        }
    }

    private void scanAudioForWaveform() {
        // Show loading state
        runOnUiThread(() -> loadingLayout.setVisibility(View.VISIBLE));

        Amplituda amplituda = new Amplituda(this);
        amplituda.processAudio(currentSong.songPath)
                .get(result -> {
                    // Get amplitudes and scale them for our view
                    // We want around 3000-4000 points for KineMaster look
                    List<Integer> rawAmplitudes = result.amplitudesAsList();
                    int totalRaw = rawAmplitudes.size();
                    int targetPoints = 4000;
                    float[] finalPeaks = new float[targetPoints];

                    for (int i = 0; i < targetPoints; i++) {
                        int rawIndex = (int) ((i / (float) targetPoints) * totalRaw);
                        if (rawIndex < totalRaw) {
                            float val = rawAmplitudes.get(rawIndex) / 100f; // Amplituda usually returns 0-100
                            // Apply similar KineMaster-style boost
                            float exaggerated = (float) Math.pow(val, 0.9f); 
                            finalPeaks[i] = Math.max(0.015f, Math.min(1.0f, exaggerated * 1.2f));
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

    private void updatePauseIcon() {
        if (PlayList_Fragment.mediaPlayer != null) {
            playPauseBtn.setImageResource(PlayList_Fragment.mediaPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);
        }
    }

    private void saveToDatabase(boolean showToast) {
        StringBuilder syncedBuilder = new StringBuilder();
        for (LyricLine line : lyricLines) {
            long time = line.getTimeMs();
            long min = (time / 1000) / 60;
            long sec = (time / 1000) % 60;
            long ms = (time % 1000) / 10;
            // Standard LRC format [mm:ss.xx]
            String timestamp = String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, ms);
            syncedBuilder.append(timestamp).append(line.getText()).append("\n");
        }

        FavoritesDatabase db = FavoritesDatabase.getInstance(this);
            db.saveLyrics(currentSong.songPath, null, syncedBuilder.toString());
            if (showToast)
                Toast.makeText(this, getString(R.string.lyrics_synced_saved), Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateHandler.removeCallbacks(updateRunnable);
    }
}
