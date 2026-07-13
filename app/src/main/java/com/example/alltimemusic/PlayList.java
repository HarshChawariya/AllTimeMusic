package com.example.alltimemusic;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class PlayList extends AppCompatActivity {
    SeekBar seekBar;
    TextView songTitleTextView, textSeek1, textSeek2;
    ImageView pause, next, previous, loopButton, favButton;
    Toolbar toolbar2;
    ArrayList<musicList_Structure> songs;
    MediaPlayer mediaPlayer;
    LinearLayout logo;
    int position;

    private int currentLoopMode = 0; // 0: No Loop, 1: Single Loop, 2: Playlist Loop
    private boolean isFavourite = false;
    private long lastClickTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_list);

        // Initialize UI components
        songTitleTextView = findViewById(R.id.songTitleTextView);
        songTitleTextView.setSelected(true);
        textSeek1 = findViewById(R.id.textSeek1);
        textSeek2 = findViewById(R.id.textSeek2);
        pause = findViewById(R.id.pause);
        previous = findViewById(R.id.previous);
        next = findViewById(R.id.next);
        favButton = findViewById(R.id.favButton);
        loopButton = findViewById(R.id.loopButton);
        logo = findViewById(R.id.logo);
        toolbar2 = findViewById(R.id.toolbar2);
        seekBar = findViewById(R.id.seekbar);

        setSupportActionBar(toolbar2);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Get data from Intent
        Intent intent = getIntent();
        if (intent != null) {
            songs = (ArrayList<musicList_Structure>) intent.getSerializableExtra("songList");
            position = intent.getIntExtra("position", 0);
            if (songs != null && !songs.isEmpty()) {
                playSong();
            }
        }

        // Controls
        pause.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    pause.setImageResource(R.drawable.play);
                    mediaPlayer.pause();
                } else {
                    pause.setImageResource(R.drawable.pause);
                    mediaPlayer.start();
                    seekBar.post(updateSeekBar);
                }
            }
        });

        next.setOnClickListener(v -> playNext());
        previous.setOnClickListener(v -> playPrevious());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                //textSeek1.setText(createTime(seekBar.getProgress()));
//                seekBar.setProgressDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rec_drw_seekprogress));
//                seekBar.setThumb(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.rec_drw_seek_thumb));
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
               // textSeek1.setText(createTime(seekBar.getProgress()));
            }
        });

        // Toggle loop modes on loopButton click
        loopButton.setOnClickListener(v -> {
            currentLoopMode = (currentLoopMode + 1) % 3;
            applyLoopMode();
        });

        // Toggle favourite state
        favButton.setOnClickListener(v -> toggleFavourite());

        // Detect double click on logo to toggle favourite
        logo.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < 300) { // 300ms threshold for double click
                toggleFavourite();
            }
            lastClickTime = clickTime;
        });
    }

    private void toggleFavourite() {
        isFavourite = !isFavourite;
        if (isFavourite) {
            favButton.setImageResource(R.drawable.fill_heart);
            Toast.makeText(this, "Added To Favourite", Toast.LENGTH_SHORT).show();
        } else {
            favButton.setImageResource(R.drawable.boder_of_heart);
            Toast.makeText(this, "Removed From Favourite", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyLoopMode() {
        if (mediaPlayer == null) return;

        switch (currentLoopMode) {
            case 0: // No Loop
                mediaPlayer.setLooping(false);
                mediaPlayer.setOnCompletionListener(null);
                loopButton.setImageResource(R.drawable.no_loop);
                Toast.makeText(this, "Loop Off", Toast.LENGTH_SHORT).show();
                break;
            case 1: // Single Loop
                mediaPlayer.setLooping(true);
                mediaPlayer.setOnCompletionListener(null);
                loopButton.setImageResource(R.drawable.single_loop);
                Toast.makeText(this, "Single Loop", Toast.LENGTH_SHORT).show();
                break;
            case 2: // Playlist Loop
                mediaPlayer.setLooping(false);
                mediaPlayer.setOnCompletionListener(mp -> playNext());
                loopButton.setImageResource(R.drawable.play_list_loop);
                Toast.makeText(this, "Playlist Loop", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void playSong() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        
        musicList_Structure currentSong = songs.get(position);
        mediaPlayer = MediaPlayer.create(this, Uri.parse(currentSong.songPath));
        if (mediaPlayer != null) {
            mediaPlayer.start();
            songTitleTextView.setText(currentSong.songTitle);
            seekBar.setMax(mediaPlayer.getDuration());
            textSeek2.setText(createTime(mediaPlayer.getDuration()));
            pause.setImageResource(R.drawable.pause);
            seekBar.post(updateSeekBar);
            
            // Re-apply current loop mode settings to the new MediaPlayer instance
            applyLoopMode();
        } else {
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show();
        }
    }

    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
                textSeek1.setText(createTime(mediaPlayer.getCurrentPosition()));
                seekBar.postDelayed(this, 1000);
            }
        }
    };

    private void playNext() {
        if (songs == null || songs.isEmpty()) return;
        if (position < songs.size() - 1) {
            position = position + 1;
        } else {
            position = 0;
        }
        playSong();
    }

    private void playPrevious() {
        if (songs == null || songs.isEmpty()) return;
        if (position > 0) {
            position = position - 1;
        } else {
            position = songs.size() - 1;
        }
        playSong();
    }

    public String createTime(int ms) {
        int sec = ms / 1000;
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        seekBar.removeCallbacks(updateSeekBar);
    }

}
