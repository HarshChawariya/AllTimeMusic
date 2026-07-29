package com.example.alltimemusic;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SyncedLyricsEditorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LyricsAdapter adapter;
    private List<LyricLine> lyricLines = new ArrayList<>();
    private TextView currentLineDisplay, currentTimeTxt, totalDurationTxt;
    private EditText lineEditText;
    private SeekBar seekBar;
    private ImageView playPauseBtn, previewBtn;
    private Button btnSetTimestamp, btnSave;
    private WaveformView waveformView;
    private View loadingLayout;
    private musicList_Structure currentSong;
    private int selectedIndex = -1;
    private boolean isPreviewMode = false;

    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milliseconds

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (PlayList_Fragment.mediaPlayer != null) {
                int currentPos = PlayList_Fragment.mediaPlayer.getCurrentPosition();
                int duration = PlayList_Fragment.mediaPlayer.getDuration();
                seekBar.setProgress(currentPos);
                currentTimeTxt.setText(formatTime(currentPos));
                
                if (duration > 0) {
                    float progress = (float) currentPos / duration;
                    waveformView.updateScroll(progress);
                    
                    if (isPreviewMode) {
                        updatePreviewLyrics(currentPos);
                    }
                }

                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    updateHandler.postDelayed(this, 50); // Faster updates for smooth scrolling
                }
            }
        }
    };

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synced_lyrics_editor);

        // Initialize Views
        recyclerView = findViewById(R.id.lyrics_edit_recycler);
        currentLineDisplay = findViewById(R.id.current_line_display);
        currentTimeTxt = findViewById(R.id.editor_current_time);
        totalDurationTxt = findViewById(R.id.editor_total_duration);
        lineEditText = findViewById(R.id.line_edit_text);
        seekBar = findViewById(R.id.editor_seekbar);
        playPauseBtn = findViewById(R.id.editor_play_pause);
        previewBtn = findViewById(R.id.btn_preview_lyrics);
        btnSetTimestamp = findViewById(R.id.btn_set_timestamp);
        btnSave = findViewById(R.id.btn_save_synced);
        waveformView = findViewById(R.id.waveform_view);
        loadingLayout = findViewById(R.id.waveform_loading_layout);
        ImageView backBtn = findViewById(R.id.back_btn_editor);

        currentSong = musicList_Recycler_Adapter.currentItem;

        if (currentSong == null) {
            finish();
            return;
        }

        // Setup UI Background Sync
        findViewById(R.id.editor_root).setBackgroundColor(MainActivity.lastDynamicColor);
        getWindow().setStatusBarColor(MainActivity.lastDynamicColor);

        // Load Lyrics
        loadPlainLyrics();
        
        // Start Waveform Scanning
        scanAudioForWaveform();

        // Setup RecyclerView
        adapter = new LyricsAdapter();
        adapter.setLyrics(lyricLines);
        adapter.setOnLyricClickListener(timeMs -> {
            long clickTime = System.currentTimeMillis();
            int clickedIndex = -1;
            for (int i = 0; i < lyricLines.size(); i++) {
                if (lyricLines.get(i).getTimeMs() == timeMs) {
                    clickedIndex = i;
                    break;
                }
            }

            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                // Double Click: Edit Text
                if (clickedIndex != -1) showEditDialog(clickedIndex);
            } else {
                // Single Click: Select Line
                if (clickedIndex != -1) selectLine(clickedIndex);
            }
            lastClickTime = clickTime;
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
            public void onWaveformDragStart() {
                // Optional: Pause player while dragging for better control
                // if (PlayList_Fragment.mediaPlayer != null && PlayList_Fragment.mediaPlayer.isPlaying()) {
                //    PlayList_Fragment.mediaPlayer.pause();
                //    updatePauseIcon();
                // }
            }

            @Override
            public void onWaveformDragEnd() {
                // Optional: Resume player if needed
            }
        });

        // Listeners
        backBtn.setOnClickListener(v -> finish());

        playPauseBtn.setOnClickListener(v -> {
            if (PlayList_Fragment.mediaPlayer != null) {
                if (PlayList_Fragment.mediaPlayer.isPlaying()) {
                    PlayList_Fragment.mediaPlayer.pause();
                } else {
                    PlayList_Fragment.mediaPlayer.start();
                    updateHandler.post(updateRunnable);
                }
                updatePauseIcon();
            }
        });

        btnSetTimestamp.setOnClickListener(v -> {
            if (selectedIndex != -1) {
                LyricLine currentLine = lyricLines.get(selectedIndex);
                
                // Toggle Logic: Set if 0, Clear if > 0
                if (currentLine.getTimeMs() > 0) {
                    lyricLines.set(selectedIndex, new LyricLine(0, currentLine.getText()));
                    updateTimestampButtonStyle(0);
                    Toast.makeText(this, "Timestamp Removed", Toast.LENGTH_SHORT).show();
                } else {
                    int currentPos = PlayList_Fragment.mediaPlayer != null ? PlayList_Fragment.mediaPlayer.getCurrentPosition() : 0;
                    lyricLines.set(selectedIndex, new LyricLine(currentPos, currentLine.getText()));
                    updateTimestampButtonStyle(currentPos);
                    
                    // Auto-scroll logic (Wait a bit so user sees the change)
                    if (selectedIndex < lyricLines.size() - 1) {
                        new Handler().postDelayed(() -> selectLine(selectedIndex + 1), 300);
                    }
                }
                adapter.notifyItemChanged(selectedIndex);
            } else {
                Toast.makeText(this, "Select a line first", Toast.LENGTH_SHORT).show();
            }
        });

        // Remove redundant Zoom Button listeners as we now use Gestures
        previewBtn.setOnClickListener(v -> {
            isPreviewMode = !isPreviewMode;
            if (isPreviewMode) {
                previewBtn.setColorFilter(Color.parseColor("#4CAF50"));
                btnSetTimestamp.setVisibility(View.GONE);
                Toast.makeText(this, "Preview Mode ON", Toast.LENGTH_SHORT).show();
            } else {
                previewBtn.clearColorFilter();
                btnSetTimestamp.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Preview Mode OFF", Toast.LENGTH_SHORT).show();
                selectedIndex = -1;
                adapter.setActiveIndex(-1);
                currentLineDisplay.setText("Select a line to start syncing");
            }
        });

        btnSave.setOnClickListener(v -> {
            saveToDatabase(true);
        });

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

    private void loadPlainLyrics() {
        FavoritesDatabase db = new FavoritesDatabase(this);
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
        
        recyclerView.smoothScrollToPosition(index);
    }

    private void updateTimestampButtonStyle(long timeMs) {
        if (timeMs > 0) {
            btnSetTimestamp.setText("Clear Time Stamp");
            btnSetTimestamp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.GRAY));
        } else {
            btnSetTimestamp.setText("Set Time Stamp");
            btnSetTimestamp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#9D201A")));
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
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Lyric Line");
        
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

    private void scanAudioForWaveform() {
        // Show loading state
        runOnUiThread(() -> loadingLayout.setVisibility(View.VISIBLE));

        // Run decoding in background to prevent UI lag
        new Thread(() -> {
            android.media.MediaExtractor extractor = new android.media.MediaExtractor();
            android.media.MediaCodec codec = null;
            try {
                extractor.setDataSource(currentSong.songPath);
                
                android.media.MediaFormat format = null;
                int trackIndex = -1;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    android.media.MediaFormat f = extractor.getTrackFormat(i);
                    if (f.getString(android.media.MediaFormat.KEY_MIME).startsWith("audio/")) {
                        format = f;
                        trackIndex = i;
                        extractor.selectTrack(i);
                        break;
                    }
                }

                if (format == null) return;

                // PROFESSIONAL STRATEGY: Full Decode Snapshots
                // Professional editors like Audacity or FL Studio look at "Peak-to-Peak" energy.
                codec = android.media.MediaCodec.createDecoderByType(format.getString(android.media.MediaFormat.KEY_MIME));
                codec.configure(format, null, null, 0);
                codec.start();

                long duration = format.getLong(android.media.MediaFormat.KEY_DURATION);
                int points = 1000;
                float[] peaks = new float[points];
                android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();

                for (int i = 0; i < points; i++) {
                    long seekTime = (long) ((i / (float) points) * duration);
                    extractor.seekTo(seekTime, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    
                    float maxInChunk = 0;
                    int decodedFrames = 0;
                    
                    // Increased decoding window to 6 frames to catch fast beats/transients better
                    while (decodedFrames < 6) { 
                        int inputIndex = codec.dequeueInputBuffer(2000);
                        if (inputIndex >= 0) {
                            java.nio.ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize > 0) {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                break;
                            }
                        }

                        int outputIndex = codec.dequeueOutputBuffer(info, 2000);
                        if (outputIndex >= 0) {
                            java.nio.ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                            
                            while (outputBuffer.remaining() >= 2) {
                                short sample = outputBuffer.getShort();
                                float absSample = Math.abs(sample) / 32768f;
                                if (absSample > maxInChunk) maxInChunk = absSample;
                            }
                            
                            codec.releaseOutputBuffer(outputIndex, false);
                            decodedFrames++;
                        } else if (outputIndex == android.media.MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break;
                        }
                    }
                    
                    // IMPROVED SCALING:
                    // If maxInChunk is very low (silence/no singing), keep it very small.
                    // If there's a beat/vocal, let it reach higher but without artificial capping.
                    if (maxInChunk < 0.01f) {
                        peaks[i] = 0.02f; // Near flat line for silence
                    } else {
                        // Using a stronger power (1.5 instead of 1.2) to make peaks pop out more
                        // from the background noise.
                        float exaggerated = (float) Math.pow(maxInChunk, 1.5f); 
                        peaks[i] = Math.max(0.02f, Math.min(1.0f, exaggerated * 4.0f));
                    }
                }

                float[] finalPeaks = peaks;
                runOnUiThread(() -> {
                    waveformView.setAmplitudes(finalPeaks);
                    loadingLayout.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (codec != null) {
                        codec.stop();
                        codec.release();
                    }
                    extractor.release();
                } catch (Exception ignored) {}
            }
        }).start();
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
            String timestamp = String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, ms);
            syncedBuilder.append(timestamp).append(line.getText()).append("\n");
        }

        FavoritesDatabase db = new FavoritesDatabase(this);
        db.saveLyrics(currentSong.songPath, null, syncedBuilder.toString());
        if (showToast) Toast.makeText(this, "Lyrics Synced & Saved!", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateHandler.removeCallbacks(updateRunnable);
    }
}
