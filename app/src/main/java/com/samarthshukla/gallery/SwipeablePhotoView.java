package com.samarthshukla.gallery;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.github.chrisbanes.photoview.PhotoView;

/**
 * Enhanced PhotoView that adds swipe gesture detection while preserving ALL existing functionality.
 * This class extends PhotoView and properly handles gesture conflicts.
 */
public class SwipeablePhotoView extends PhotoView {

    // Gesture detection fields
    private float startY = 0f;
    private float startX = 0f;
    private long startTime = 0;
    private boolean isVerticalSwipe = false;
    private boolean isDragging = false;
    private boolean isZoomed = false;
    private boolean isSwipeGestureActive = false;
    
    // Thresholds for gesture detection
    private static final float SWIPE_THRESHOLD = 20f;
    private static final float VERTICAL_SWIPE_THRESHOLD = 50f;
    
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

    public SwipeablePhotoView(Context context) {
        super(context);
        init();
    }

    public SwipeablePhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwipeablePhotoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize with proper gesture handling
        setOnViewTapListener(null); // Disable tap listener to prevent conflicts
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Check if PhotoView is zoomed
        isZoomed = getScale() > 1.0f;
        
        // If PhotoView is zoomed, let it handle everything (preserve zoom/pan)
        if (isZoomed) {
            return super.onTouchEvent(event);
        }
        
        // Handle our swipe gestures
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getY();
                startX = event.getX();
                startTime = System.currentTimeMillis();
                isVerticalSwipe = false;
                isDragging = false;
                isSwipeGestureActive = false;
                return true; // Consume the event

            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getY() - startY;
                float deltaX = event.getX() - startX;
                long deltaTime = System.currentTimeMillis() - startTime;
                float velocity = Math.abs(deltaY) / Math.max(deltaTime, 1);
                
                // Check if this is a vertical swipe with velocity
                if (!isVerticalSwipe && Math.abs(deltaY) > Math.abs(deltaX) && 
                    Math.abs(deltaY) > SWIPE_THRESHOLD && velocity > 0.5f) {
                    isVerticalSwipe = true;
                    isSwipeGestureActive = true;
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
                if (!isSwipeGestureActive) {
                    return super.onTouchEvent(event);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    float totalDrag = event.getY() - startY;
                    long totalTime = System.currentTimeMillis() - startTime;
                    float finalVelocity = Math.abs(totalDrag) / Math.max(totalTime, 1);
                    
                    // Use velocity to determine if gesture should complete
                    if (finalVelocity > 0.3f || Math.abs(totalDrag) > 30) {
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
                    }
                    isDragging = false;
                    isVerticalSwipe = false;
                    isSwipeGestureActive = false;
                    return true;
                }
                
                // Reset state
                isVerticalSwipe = false;
                isSwipeGestureActive = false;
                
                // Let PhotoView handle the event if it wasn't our gesture
                if (!isSwipeGestureActive) {
                    return super.onTouchEvent(event);
                }
                return true;
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

    // Public method to check if currently zoomed (for external use)
    public boolean isCurrentlyZoomed() {
        return isZoomed;
    }

    // Public method to reset gesture state (for external use)
    public void resetGestureState() {
        isDragging = false;
        isVerticalSwipe = false;
        isSwipeGestureActive = false;
        startY = 0f;
        startX = 0f;
        startTime = 0;
    }
    
    // Method to check if swipe gesture is currently active
    public boolean isSwipeGestureActive() {
        return isSwipeGestureActive;
    }
} 