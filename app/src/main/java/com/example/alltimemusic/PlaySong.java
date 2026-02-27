package com.example.alltimemusic;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PlaySong extends AppCompatActivity {
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (seekBar != null) {
            seekBar.removeCallbacks(updateSeekBar);
        }
    }

    SeekBar seekBar;
    TextView songTitleTextView, textSeek1, textSeek2, lyricsTextView;
    ImageView pause, next, previous;
    Toolbar toolbar2;
    ArrayList<File> songs;
    MediaPlayer mediaPlayer;
    String textContext;
    int position;
    
    OkHttpClient client = new OkHttpClient();

    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                seekBar.setProgress(currentPosition);
                textSeek1.setText(createTime(currentPosition));
                if (mediaPlayer.isPlaying()) {
                    seekBar.postDelayed(this, 1000);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_song);
        songTitleTextView = findViewById(R.id.songTitleTextView);
        lyricsTextView = findViewById(R.id.lyricsTextView);
        textSeek1 = findViewById(R.id.textSeek1);
        textSeek2 = findViewById(R.id.textSeek2);
        pause = findViewById(R.id.pause);
        previous = findViewById(R.id.previous);
        next = findViewById(R.id.next);
        toolbar2 = findViewById(R.id.toolbar2);
        setSupportActionBar(toolbar2);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        seekBar = findViewById(R.id.seekbar);
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        songs = (ArrayList) bundle.getParcelableArrayList("songList");
        textContext = intent.getStringExtra("currentSong");
        songTitleTextView.setText(textContext);
        songTitleTextView.setSelected(true);
        position = intent.getIntExtra("position", 0);

        playSong();

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    textSeek1.setText(createTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.stop();
                mediaPlayer.release();
                if (position != 0) {
                    position = position - 1;
                } else {
                    position = songs.size() - 1;
                }
                playSong();
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.stop();
                mediaPlayer.release();
                if (position != songs.size() - 1) {
                    position = position + 1;
                } else {
                    position = 0;
                }
                playSong();
            }
        });
    }

    private void playSong() {
        Uri uri = Uri.parse(songs.get(position).toString());
        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();
        seekBar.setMax(mediaPlayer.getDuration());
        textContext = songs.get(position).getName();
        songTitleTextView.setText(textContext);
        pause.setImageResource(R.drawable.pause);
        textSeek2.setText(createTime(mediaPlayer.getDuration()));
        seekBar.post(updateSeekBar);
        
        fetchLyrics(textContext.replace(".mp3", ""));
    }

    private void fetchLyrics(String songName) {
        lyricsTextView.setText("Fetching lyrics...");
        String url = "https://lrclib.net/api/get?track_name=" + Uri.encode(songName);

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> lyricsTextView.setText("Lyrics not found."));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseData);
                        String lyrics = jsonObject.optString("plainLyrics", "No lyrics available.");
                        runOnUiThread(() -> lyricsTextView.setText(lyrics));
                    } catch (JSONException e) {
                        runOnUiThread(() -> lyricsTextView.setText("Error parsing lyrics."));
                    }
                } else {
                    runOnUiThread(() -> lyricsTextView.setText("Lyrics not found."));
                }
            }
        });
    }

    public String createTime(int milliseconds) {
        String time = "";
        int min = milliseconds / 1000 / 60;
        int sec = milliseconds / 1000 % 60;

        time += min + ":";
        if (sec < 10) {
            time += "0";
        }
        time += sec;
        return time;
    }
}
