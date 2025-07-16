package com.samarthshukla.gallery;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.github.chrisbanes.photoview.PhotoView;

/**
 * Enhanced PhotoView that adds swipe gesture detection while preserving ALL existing functionality.
 */
public class EnhancedPhotoView extends PhotoView {

    private static final String TAG = "EnhancedPhotoView";

    // Gesture detection fields
    private float startY = 0f;
    private float startX = 0f;
    private boolean isVerticalSwipe = false;
    private boolean isDragging = false;
    private boolean isZoomed = false;
    
    // Thresholds for gesture detection
    private static final float SWIPE_THRESHOLD = 15f;
    
    // Callback interfaces
    public interface OnSwipeUpListener {
        void onSwipeUp(float deltaY);
        void onSwipeUpComplete(float totalDrag);
    }
    
    public interface OnSwipeDownListener {
        void onSwipeDown(float deltaY);
        void onSwipeDownComplete(float totalDrag);
    }
    
    private OnSwipeUpListener swipeUpListener;
    private OnSwipeDownListener swipeDownListener;

    public EnhancedPhotoView(Context context) {
        super(context);
        init();
    }

    public EnhancedPhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EnhancedPhotoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnViewTapListener(null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Allow swipes only if at minimum scale (fit-to-screen)
        float scale = getScale();
        float minScale = getMinimumScale();
        isZoomed = Math.abs(scale - minScale) > 0.01f;
        Log.d(TAG, "onTouchEvent: scale=" + scale + ", minScale=" + minScale + ", isZoomed=" + isZoomed);
        
        // If PhotoView is zoomed, let it handle everything
        if (isZoomed) {
            return super.onTouchEvent(event);
        }
        
        // Handle our swipe gestures
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getY();
                startX = event.getX();
                isVerticalSwipe = false;
                isDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getY() - startY;
                float deltaX = event.getX() - startX;
                
                // Check if this is a vertical swipe
                if (!isVerticalSwipe && Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > SWIPE_THRESHOLD) {
                    isVerticalSwipe = true;
                }
                
                if (isVerticalSwipe) {
                    isDragging = true;
                    if (deltaY > 0) {
                        // Swipe down
                        if (swipeDownListener != null) {
                            swipeDownListener.onSwipeDown(deltaY);
                        }
                        return true;
                    } else {
                        // Swipe up
                        if (swipeUpListener != null) {
                            swipeUpListener.onSwipeUp(Math.abs(deltaY));
                        }
                        return true;
                    }
                }
                
                // If not a vertical swipe, let PhotoView handle it
                return super.onTouchEvent(event);

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    float totalDrag = event.getY() - startY;
                    if (totalDrag > 0) {
                        // Swipe down complete
                        if (swipeDownListener != null) {
                            swipeDownListener.onSwipeDownComplete(totalDrag);
                        }
                    } else {
                        // Swipe up complete
                        if (swipeUpListener != null) {
                            swipeUpListener.onSwipeUpComplete(Math.abs(totalDrag));
                        }
                    }
                    isDragging = false;
                    isVerticalSwipe = false;
                    return true;
                }
                
                // Reset state
                isVerticalSwipe = false;
                
                // Let PhotoView handle the event if it wasn't our gesture
                return super.onTouchEvent(event);
        }
        
        return super.onTouchEvent(event);
    }

    // Setter methods for listeners
    public void setOnSwipeUpListener(OnSwipeUpListener listener) {
        this.swipeUpListener = listener;
    }

    public void setOnSwipeDownListener(OnSwipeDownListener listener) {
        this.swipeDownListener = listener;
    }

    // Public method to check if currently zoomed
    public boolean isCurrentlyZoomed() {
        return isZoomed;
    }

    // Public method to reset gesture state
    public void resetGestureState() {
        isDragging = false;
        isVerticalSwipe = false;
        startY = 0f;
        startX = 0f;
    }
} 