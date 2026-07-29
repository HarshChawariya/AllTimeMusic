package com.example.alltimemusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

public class WaveformView extends View {
    private Paint paint;
    private android.graphics.Path wavePath;
    private float[] amplitudes = new float[0];
    private int width, height;
    private float scrollOffset = 0;
    private float spacing = 8f; // Dynamic spacing for zoom
    private OnWaveformScrollListener scrollListener;
    private float lastTouchX;
    private boolean isDragging = false;
    private android.view.ScaleGestureDetector scaleGestureDetector;

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
            public boolean onScale(android.view.ScaleGestureDetector detector) {
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

        // 1. Draw Professional Symmetrical Bezier Curve Waveform
        wavePath.reset();
        
        boolean firstPoint = true;
        float lastX = -1, lastYTop = -1, lastYBottom = -1;

        for (int i = 0; i < amplitudes.length; i++) {
            float x = screenCenterX + (i * currentSpacing) - scrollOffset;
            
            // Only process if within or near visible bounds for performance
            if (x > -currentSpacing * 2 && x < width + currentSpacing * 2) {
                float barHeight = amplitudes[i] * (height * 0.6f);
                float yTop = centerY - barHeight / 2f;
                float yBottom = centerY + barHeight / 2f;

                if (firstPoint) {
                    wavePath.moveTo(x, yTop);
                    firstPoint = false;
                } else {
                    // Bezier Smoothing for top curve
                    float controlX = (lastX + x) / 2f;
                    wavePath.quadTo(controlX, lastYTop, x, yTop);
                }
                lastX = x;
                lastYTop = yTop;
                lastYBottom = yBottom;
            }
        }

        // Draw bottom curve in reverse to close the path symmetrically
        for (int i = amplitudes.length - 1; i >= 0; i--) {
            float x = screenCenterX + (i * currentSpacing) - scrollOffset;
            if (x > -currentSpacing * 2 && x < width + currentSpacing * 2) {
                float barHeight = amplitudes[i] * (height * 0.6f);
                float yBottom = centerY + barHeight / 2f;
                
                float controlX = (lastX + x) / 2f;
                wavePath.quadTo(controlX, lastYBottom, x, yBottom);
                
                lastX = x;
                lastYBottom = yBottom;
            }
        }
        
        wavePath.close();

        // 2. Playback State Coloring (Clip then fill)
        canvas.save();
        
        // Played Part (Left)
        canvas.clipRect(0, 0, screenCenterX, height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawPath(wavePath, paint);
        canvas.restore();

        // Upcoming Part (Right)
        canvas.save();
        canvas.clipRect(screenCenterX, 0, width, height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#80FFFFFF"));
        canvas.drawPath(wavePath, paint);
        canvas.restore();
        
        // Optional: Draw outline for extra sharpness
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(Color.parseColor("#33FFFFFF"));
        canvas.drawPath(wavePath, paint);
    }
}
