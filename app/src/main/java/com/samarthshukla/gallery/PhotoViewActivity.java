package com.samarthshukla.gallery;

import android.net.Uri;
import android.os.Bundle;
import android.transition.TransitionInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.widget.ImageView;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.List;

public class PhotoViewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URIS = "image_uris";
    public static final String EXTRA_START_POSITION = "start_position";

    private static final float DRAG_THRESHOLD = 250f;

    private ViewPager2 viewPager;
    private View dimBackground;

    private float startY = 0f;
    private boolean isDragging = false;
    private String transitionName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        getWindow().setSharedElementEnterTransition(
                TransitionInflater.from(this).inflateTransition(android.R.transition.move));
        getWindow().setSharedElementReturnTransition(
                TransitionInflater.from(this).inflateTransition(android.R.transition.move));
        supportPostponeEnterTransition();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        viewPager = findViewById(R.id.viewPager);
        dimBackground = findViewById(R.id.dimBackground);

        List<Uri> imageUris = getIntent().getParcelableArrayListExtra(EXTRA_IMAGE_URIS);
        int startPosition = getIntent().getIntExtra(EXTRA_START_POSITION, 0);
        transitionName = getIntent().getStringExtra("transition_name");

        if (imageUris == null)
            imageUris = new ArrayList<>();

        PhotoViewerAdapter adapter = new PhotoViewerAdapter(this, imageUris);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(startPosition, false);

        // Ensure correct shared element on enter
        viewPager.post(() -> {
            RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(startPosition);
            if (viewHolder instanceof PhotoViewerAdapter.ViewHolder) {
                View photoView = ((PhotoViewerAdapter.ViewHolder) viewHolder).photoView;
                if (transitionName != null) {
                    ViewCompat.setTransitionName(photoView, transitionName);
                }

                ViewTreeObserver observer = photoView.getViewTreeObserver();
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        observer.removeOnPreDrawListener(this);
                        supportStartPostponedEnterTransition();
                        return true;
                    }
                });
            } else {
                supportStartPostponedEnterTransition();
            }
        });

        // Handle swipe-down to dismiss
        viewPager.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    isDragging = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    float deltaY = event.getY() - startY;
                    if (deltaY > 0) {
                        isDragging = true;

                        int currentPosition = viewPager.getCurrentItem();
                        RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
                        RecyclerView.ViewHolder viewHolder = recyclerView
                                .findViewHolderForAdapterPosition(currentPosition);
                        if (viewHolder instanceof PhotoViewerAdapter.ViewHolder) {
                            PhotoView photoView = ((PhotoViewerAdapter.ViewHolder) viewHolder).photoView;

                            // Disable zoom and scale image
                            photoView.setScale(1f, false);
                            photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            photoView.setTranslationY(deltaY);
                            photoView.setScaleX(1f - deltaY / 1000f);
                            photoView.setScaleY(1f - deltaY / 1000f);

                            dimBackground.setAlpha(Math.max(0f, 1f - deltaY / DRAG_THRESHOLD));
                        }

                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        float totalDrag = event.getY() - startY;
                        int currentPosition = viewPager.getCurrentItem();
                        RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
                        RecyclerView.ViewHolder viewHolder = recyclerView
                                .findViewHolderForAdapterPosition(currentPosition);

                        if (viewHolder instanceof PhotoViewerAdapter.ViewHolder) {
                            PhotoView photoView = ((PhotoViewerAdapter.ViewHolder) viewHolder).photoView;

                            if (totalDrag > DRAG_THRESHOLD) {
                                // Attach transition name and finish
                                if (transitionName != null) {
                                    ViewCompat.setTransitionName(photoView, transitionName);
                                }
                                finishAfterTransition();
                            } else {
                                // Animate back
                                photoView.animate().translationY(0).scaleX(1f).scaleY(1f).start();
                                dimBackground.animate().alpha(1f).start();
                            }
                        }

                        return true;
                    }
                    break;
            }
            return false;
        });
    }
}