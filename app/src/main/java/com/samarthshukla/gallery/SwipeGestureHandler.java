package com.samarthshukla.gallery;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.net.Uri;
import com.samarthshukla.gallery.MediaInfo;

/**
 * Handles swipe gestures and animations for the photo viewer.
 * This class manages the swipe up/down gestures while preserving all existing functionality.
 */
public class SwipeGestureHandler {

    private static final float DRAG_THRESHOLD = 250f;
    
    private View dimBackground;
    private LinearLayout imageInfoOverlay;
    private SwipeablePhotoView photoView;
    private PhotoViewActivity activity;
    
    private boolean isImageInfoVisible = false;

    public SwipeGestureHandler(View dimBackground, LinearLayout imageInfoOverlay, PhotoViewActivity activity) {
        this.dimBackground = dimBackground;
        this.imageInfoOverlay = imageInfoOverlay;
        this.activity = activity;
    }

    public void setPhotoView(SwipeablePhotoView photoView) {
        this.photoView = photoView;
        setupGestureListeners();
    }

    private void setupGestureListeners() {
        if (photoView == null) return;

        // Set up swipe down listener
        photoView.setOnSwipeDownListener(new SwipeablePhotoView.OnSwipeDownListener() {
            @Override
            public void onSwipeDown(float deltaY) {
                handleDragDown(deltaY);
            }

            @Override
            public void onSwipeDownComplete(float totalDrag) {
                handleReleaseDown(totalDrag);
            }
        });

        // Set up swipe up listener
        photoView.setOnSwipeUpListener(new SwipeablePhotoView.OnSwipeUpListener() {
            @Override
            public void onSwipeUp(float deltaY) {
                handleDragUp(deltaY);
            }

            @Override
            public void onSwipeUpComplete(float totalDrag) {
                handleReleaseUp(totalDrag);
            }
        });
    }

    private void handleDragDown(float deltaY) {
        if (photoView == null) return;

        // More responsive scaling and movement
        photoView.setScale(1f, false);
        photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoView.setTranslationY(deltaY * 0.8f); // More responsive movement
        photoView.setScaleX(1f - deltaY / 800f); // More responsive scaling
        photoView.setScaleY(1f - deltaY / 800f);
        dimBackground.setAlpha(Math.max(0f, 1f - deltaY / DRAG_THRESHOLD));
    }

    private void handleDragUp(float deltaY) {
        // Show image info overlay with smooth animation
        if (imageInfoOverlay != null) {
            float progress = Math.min(deltaY / 80f, 1f); // Much more responsive threshold
            imageInfoOverlay.setVisibility(View.VISIBLE);
            imageInfoOverlay.setTranslationY(-deltaY * 0.8f); // More responsive movement
            imageInfoOverlay.setAlpha(progress);
            
            // Update info if not already visible
            if (!isImageInfoVisible && activity != null) {
                Uri currentUri = activity.getCurrentImageUri();
                if (currentUri != null) {
                    MediaInfo mediaInfo = MediaInfo.fromUri(activity, currentUri);
                    updateImageInfo(mediaInfo);
                }
            }
        }
    }

    private void handleReleaseDown(float totalDrag) {
        if (photoView == null) return;

        if (totalDrag > DRAG_THRESHOLD) {
            // Dismiss the activity
            if (photoView.getContext() instanceof android.app.Activity) {
                ((android.app.Activity) photoView.getContext()).finishAfterTransition();
            }
        } else {
            // Reset PhotoView to normal state
            photoView.animate().translationY(0).scaleX(1f).scaleY(1f).start();
            photoView.setScale(1f, false);
            photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            dimBackground.animate().alpha(1f).start();
        }
    }

    private void handleReleaseUp(float totalDrag) {
        if (totalDrag > 30) { // Much more sensitive threshold
            // Show image info permanently
            showImageInfo();
        } else {
            // Hide image info
            hideImageInfo();
        }
    }

    private void showImageInfo() {
        if (isImageInfoVisible || imageInfoOverlay == null) return;
        
        imageInfoOverlay.setVisibility(View.VISIBLE);
        imageInfoOverlay.setAlpha(0f);
        imageInfoOverlay.setTranslationY(-100f);
        
        imageInfoOverlay.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        
        isImageInfoVisible = true;
    }

    private void hideImageInfo() {
        if (!isImageInfoVisible || imageInfoOverlay == null) return;
        
        imageInfoOverlay.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        imageInfoOverlay.setVisibility(View.GONE);
                        isImageInfoVisible = false;
                    }
                })
                .start();
    }

    public void resetState() {
        if (photoView != null) {
            photoView.resetGestureState();
        }
        if (imageInfoOverlay != null) {
            imageInfoOverlay.setVisibility(View.GONE);
            isImageInfoVisible = false;
        }
    }

    public boolean isImageInfoVisible() {
        return isImageInfoVisible;
    }

    /* ───────── Helper: update image info display ───────── */
    private void updateImageInfo(MediaInfo mediaInfo) {
        if (imageInfoOverlay == null) return;
        
        android.widget.TextView tvFileName = imageInfoOverlay.findViewById(R.id.tvFileName);
        android.widget.TextView tvDate = imageInfoOverlay.findViewById(R.id.tvDate);
        android.widget.TextView tvTime = imageInfoOverlay.findViewById(R.id.tvTime);
        android.widget.TextView tvSize = imageInfoOverlay.findViewById(R.id.tvSize);
        
        if (tvFileName != null) tvFileName.setText(mediaInfo.getFileName());
        if (tvDate != null) tvDate.setText(mediaInfo.getFormattedDate());
        if (tvTime != null) tvTime.setText(mediaInfo.getFormattedTime());
        if (tvSize != null) tvSize.setText(mediaInfo.getFormattedSize());
    }
} 