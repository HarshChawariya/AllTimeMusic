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
        // Reference image has very tight spacing and rounded blocks
        // Using this.spacing to refer to the member variable
        float currentSpacing = Math.max(4f, this.spacing); 
        float barWidth = currentSpacing * 0.7f; // Wider bars relative to spacing for that "connected" look
        float screenCenterX = width / 2f;

        for (int i = 0; i < amplitudes.length; i++) {
            float x = screenCenterX + (i * currentSpacing) - scrollOffset;
            
            if (x > -currentSpacing && x < width + currentSpacing) {
                // Symmetrical height: Reduced multiplier to 0.6f to prevent match_parent look
                float barHeight = amplitudes[i] * (height * 0.6f);
                
                if (x < screenCenterX) {
                    paint.setColor(Color.WHITE);
                } else {
                    paint.setColor(Color.parseColor("#80FFFFFF"));
                }
                
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(barWidth);
                
                // Drawing thicker vertical bars that look like the reference blocks
                canvas.drawLine(x, centerY - barHeight / 2f, x, centerY + barHeight / 2f, paint);
            }
        }
    }
}
