package com.example.alltimemusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WaveformView extends View {
    private final Paint paint;
    private final android.graphics.Path wavePath;
    private float[] amplitudes = new float[0];
    private int width, height;
    private float scrollOffset = 0;
    private float spacing = 8f; // Dynamic spacing for zoom
    private OnWaveformScrollListener scrollListener;
    private float lastTouchX;
    private boolean isDragging = false;
    private final android.view.ScaleGestureDetector scaleGestureDetector;

    public interface OnWaveformScrollListener {
        void onWaveformScroll(float progress);
        void onWaveformDragStart();
        void onWaveformDragEnd();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4f);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        
        wavePath = new android.graphics.Path();

        scaleGestureDetector = new android.view.ScaleGestureDetector(context, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull android.view.ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float newSpacing = spacing * scaleFactor;
                
                // Limit zoom between 0.5x and 8x of base 8f spacing
                if (newSpacing >= 4f && newSpacing <= 64f) {
                    float oldTotalWidth = amplitudes.length * spacing;
                    float progress = scrollOffset / oldTotalWidth;
                    
                    spacing = newSpacing;
                    
                    float newTotalWidth = amplitudes.length * spacing;
                    scrollOffset = progress * newTotalWidth;
                    
                    invalidate();
                }
                return true;
            }
        });
    }

    public void setOnWaveformScrollListener(OnWaveformScrollListener listener) {
        this.scrollListener = listener;
    }

    public void setAmplitudes(float[] amplitudes) {
        this.amplitudes = amplitudes;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
    }

    public void updateScroll(float progress) {
        if (!isDragging) {
            float totalWidth = amplitudes.length * spacing;
            scrollOffset = progress * totalWidth;
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (amplitudes == null || amplitudes.length == 0) return false;
        
        scaleGestureDetector.onTouchEvent(event);
        
        if (scaleGestureDetector.isInProgress()) return true;

        float x = event.getX();
        float totalWidth = amplitudes.length * spacing;

        switch (event.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                isDragging = true;
                if (scrollListener != null) scrollListener.onWaveformDragStart();
                return true;

            case android.view.MotionEvent.ACTION_MOVE:
                float dx = lastTouchX - x;
                scrollOffset += dx;

                // Clamp scroll
                if (scrollOffset < 0) scrollOffset = 0;
                if (scrollOffset > totalWidth) scrollOffset = totalWidth;

                lastTouchX = x;
                invalidate();

                if (scrollListener != null) {
                    float progress = scrollOffset / totalWidth;
                    scrollListener.onWaveformScroll(progress);
                }
                return true;

            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                isDragging = false;
                if (scrollListener != null) scrollListener.onWaveformDragEnd();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(@androidx.annotation.NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (amplitudes == null || amplitudes.length == 0) return;

        float centerY = height / 2f;
        float currentSpacing = Math.max(4f, this.spacing);
        float screenCenterX = width / 2f;

        // 1. Build the path for a Perfectly Mirrored Bezier Waveform
        wavePath.reset();
        
        boolean firstPoint = true;
        float lastX = 0;
        float lastYTop = 0;

        // TOP HALF: Drawing from left to right
        for (int i = 0; i < amplitudes.length; i++) {
            float x = screenCenterX + (i * currentSpacing) - scrollOffset;
            
            // Only process points that are visible (plus a small buffer for smooth curves)
            if (x > -currentSpacing * 2 && x < width + currentSpacing * 2) {
                float barHeight = amplitudes[i] * (height * 0.6f);
                float yTop = centerY - barHeight / 2f;

                if (firstPoint) {
                    wavePath.moveTo(x, yTop);
                    firstPoint = false;
                } else {
                    // Use quadTo for smoothing
                    wavePath.quadTo((lastX + x) / 2f, lastYTop, x, yTop);
                }
                lastX = x;
                lastYTop = yTop;
            }
        }

        // BOTTOM HALF (Mirror): Drawing from right to left using the exact same coordinates
        // This ensures the bottom part is a 100% accurate reflection of the top
        for (int i = amplitudes.length - 1; i >= 0; i--) {
            float x = screenCenterX + (i * currentSpacing) - scrollOffset;
            if (x > -currentSpacing * 2 && x < width + currentSpacing * 2) {
                float barHeight = amplitudes[i] * (height * 0.6f);
                float yBottom = centerY + barHeight / 2f; // Mirrors yTop exactly
                wavePath.lineTo(x, yBottom);
            }
        }
        
        wavePath.close();

        // 2. Playback State Coloring (Fill the Path)
        canvas.save();
        // Left Side (Played): Full White
        canvas.clipRect(0, 0, screenCenterX, height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawPath(wavePath, paint);
        canvas.restore();

        canvas.save();
        // Right Side (Upcoming): Semi-transparent
        canvas.clipRect(screenCenterX, 0, width, height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#80FFFFFF"));
        canvas.drawPath(wavePath, paint);
        canvas.restore();
    }
}
