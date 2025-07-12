package com.samarthshukla.gallery;

import android.net.Uri;
import android.os.Bundle;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.jsibbold.zoomage.ZoomageView;

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

        Transition enter = TransitionInflater.from(this)
                .inflateTransition(android.R.transition.move)
                .setDuration(300);
        Transition exit = TransitionInflater.from(this)
                .inflateTransition(android.R.transition.move)
                .setDuration(500);

        getWindow().setSharedElementEnterTransition(enter);
        getWindow().setSharedElementReturnTransition(exit);

        supportPostponeEnterTransition();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        viewPager = findViewById(R.id.viewPager);
        dimBackground = findViewById(R.id.dimBackground);

        List<Uri> imageUris = getIntent().getParcelableArrayListExtra(EXTRA_IMAGE_URIS);
        if (imageUris == null) imageUris = new ArrayList<>();

        int startPosition = getIntent().getIntExtra(EXTRA_START_POSITION, 0);
        transitionName = getIntent().getStringExtra("transition_name");

        PhotoAdapter adapter = PhotoAdapter.forViewer(this, imageUris); // Make sure it uses ZoomageView!
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(startPosition, false);

        viewPager.post(() -> {
            RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);
            recycler.post(() -> {
                RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(startPosition);
                if (vh instanceof PhotoAdapter.ViewerHolder) {
                    ZoomageView zv = ((PhotoAdapter.ViewerHolder) vh).zoomageView;
                    if (transitionName != null) {
                        ViewCompat.setTransitionName(zv, transitionName);
                    }

                    zv.getViewTreeObserver().addOnPreDrawListener(
                            new ViewTreeObserver.OnPreDrawListener() {
                                @Override
                                public boolean onPreDraw() {
                                    zv.getViewTreeObserver().removeOnPreDrawListener(this);
                                    supportStartPostponedEnterTransition();
                                    return true;
                                }
                            });
                } else {
                    supportStartPostponedEnterTransition();
                }
            });
        });

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
                        handleDrag(deltaY);
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        handleRelease(event.getY() - startY);
                        return true;
                    }
                    break;
            }
            return false;
        });
    }

    private void handleDrag(float deltaY) {
        int current = viewPager.getCurrentItem();
        RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(current);

        if (vh instanceof PhotoAdapter.ViewerHolder) {
            ZoomageView zv = ((PhotoAdapter.ViewerHolder) vh).zoomageView;

            zv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            zv.setTranslationY(deltaY);
            zv.setScaleX(1f - deltaY / 1000f);
            zv.setScaleY(1f - deltaY / 1000f);
            dimBackground.setAlpha(Math.max(0f, 1f - deltaY / DRAG_THRESHOLD));
        }
    }

    private void handleRelease(float totalDrag) {
        int current = viewPager.getCurrentItem();
        RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(current);

        if (vh instanceof PhotoAdapter.ViewerHolder) {
            ZoomageView zv = ((PhotoAdapter.ViewerHolder) vh).zoomageView;

            if (totalDrag > DRAG_THRESHOLD) {
                if (transitionName != null) {
                    ViewCompat.setTransitionName(zv, transitionName);
                }
                finishAfterTransition();
            } else {
                zv.animate().translationY(0).scaleX(1f).scaleY(1f).start();
                dimBackground.animate().alpha(1f).start();
            }
        }
    }
}