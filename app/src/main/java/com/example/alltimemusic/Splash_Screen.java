package com.example.alltimemusic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Splash_Screen extends AppCompatActivity {
    TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        Intent intentHome = new Intent(Splash_Screen.this, MainActivity.class);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                /*textView = findViewById(R.id.textView);
                Animation scale = AnimationUtils.loadAnimation(splash.this, R.anim.splash_screen_txt);
                textView.startAnimation(scale);*/
                startActivity(intentHome);
                finish();
            }
        }, 4000);
    }
}