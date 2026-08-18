package com.example.alltimemusic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class Splash_Screen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        Intent intentHome = new Intent(Splash_Screen.this, MainActivity.class);

        new Handler().postDelayed(() -> {
            startActivity(intentHome);
            finish();
        }, 4000);
    }
}