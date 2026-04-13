package com.samarthshukla.gallery;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;
import com.jsibbold.zoomage.ZoomageView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PhotoViewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URIS = "image_uris";
    public static final String EXTRA_START_POSITION = "start_position";
    private static final float DRAG_THRESHOLD = 250f;

    private ViewPager2 viewPager;
    private View dimBackground;
    private String transitionName;

    // New fields for enhanced functionality
    private LinearLayout imageInfoOverlay;
    private LinearLayout selectionActionMenu;
    private boolean isSelectionMode = false;
    private List<Uri> selectedImages = new ArrayList<>();
    private RecycleBinManager recycleBinManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        /* ───────── Shared-element transition with custom durations ───────── */
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);

        Transition enter = TransitionInflater.from(this)
                .inflateTransition(android.R.transition.move)
                .setDuration(300); // open = 300 ms
        Transition exit = TransitionInflater.from(this)
                .inflateTransition(android.R.transition.move)
                .setDuration(300); // close = 300 ms

        getWindow().setSharedElementEnterTransition(enter);
        getWindow().setSharedElementReturnTransition(exit);
        enterImmersiveMode();
        /* ─────────────────────────────────────────────────────────────────── */

        supportPostponeEnterTransition(); // wait until first image is ready
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        // Link the AI Cutout FAB
        View fabAiCutout = findViewById(R.id.fabAiCutout);
        if (fabAiCutout != null) {
            fabAiCutout.setOnClickListener(v -> {
                performPhotoCutout();
            });
        }

        viewPager = findViewById(R.id.viewPager);
        dimBackground = findViewById(R.id.dimBackground);

        // Initialize new views
        imageInfoOverlay = findViewById(R.id.imageInfoOverlay);
        selectionActionMenu = findViewById(R.id.selectionActionMenu);
        recycleBinManager = new RecycleBinManager(this);

        // Initialize overlays
        if (imageInfoOverlay != null) {
            imageInfoOverlay.setVisibility(View.GONE);
        }
        if (selectionActionMenu != null) {
            selectionActionMenu.setVisibility(View.GONE);
        }

        /* ───────── Set up adapter & pager ───────── */
        List<Uri> imageUris = SharedData.currentImageUris;
        if (imageUris == null) {
            imageUris = new ArrayList<>();
        }

        int startPosition = getIntent().getIntExtra(EXTRA_START_POSITION, 0);
        transitionName = getIntent().getStringExtra("transition_name");

        PhotoAdapter adapter = PhotoAdapter.forViewer(this, imageUris);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(startPosition, false);

        /* ───────── Postpone transition until the shared element is drawn ───────── */
        viewPager.post(() -> {
            RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);
            recycler.post(() -> {
                RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(startPosition);

                if (vh instanceof PhotoAdapter.ViewerHolder) {
                    PhotoView pv = ((PhotoAdapter.ViewerHolder) vh).photoView;

                    if (transitionName != null) {
                        ViewCompat.setTransitionName(pv, transitionName);
                    }

                    pv.getViewTreeObserver().addOnPreDrawListener(
                            new ViewTreeObserver.OnPreDrawListener() {
                                @Override
                                public boolean onPreDraw() {
                                    pv.getViewTreeObserver().removeOnPreDrawListener(this);
                                    supportStartPostponedEnterTransition();
                                    return true;
                                }
                            });
                } else {
                    supportStartPostponedEnterTransition();
                }
            });
        });

        /* ───────── Enhanced touch handling for swipe-down and swipe-up ───────── */
        // Set up page change callback to connect gesture handlers to EnhancedPhotoViews
        viewPager.registerOnPageChangeCallback(new OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                // Connect gesture handlers to the current EnhancedPhotoView
                setupGestureHandlersForCurrentPage();
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        // Set up initial gesture handlers
        setupGestureHandlersForCurrentPage();
    }

    private void setupGestureHandlersForCurrentPage() {
        int current = viewPager.getCurrentItem();
        RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);

        // Use a delayed approach to ensure ViewHolder is available
        recycler.post(() -> {
            RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(current);

            if (vh instanceof PhotoAdapter.ViewerHolder) {
                EnhancedPhotoView pv = ((PhotoAdapter.ViewerHolder) vh).photoView;

                // Set up swipe down listener
                pv.setOnSwipeDownListener(new EnhancedPhotoView.OnSwipeDownListener() {
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
                pv.setOnSwipeUpListener(new EnhancedPhotoView.OnSwipeUpListener() {
                    @Override
                    public void onSwipeUp(float deltaY) {
                        handleDragUp(deltaY);
                    }

                    @Override
                    public void onSwipeUpComplete(float totalDrag) {
                        handleReleaseUp(totalDrag);
                    }
                });
            } else {
                // Try again after a short delay
                recycler.postDelayed(() -> setupGestureHandlersForCurrentPage(), 100);
            }
        });
    }

    private void handleDragDown(float deltaY) {
        int current = viewPager.getCurrentItem();
        RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(current);

        if (vh instanceof PhotoAdapter.ViewerHolder) {
            EnhancedPhotoView pv = ((PhotoAdapter.ViewerHolder) vh).photoView;

            // More responsive scaling and movement
            pv.setScale(1f, false);
            pv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            pv.setTranslationY(deltaY * 0.8f);
            pv.setScaleX(1f - deltaY / 800f);
            pv.setScaleY(1f - deltaY / 800f);
            dimBackground.setAlpha(Math.max(0f, 1f - deltaY / DRAG_THRESHOLD));
        }
    }

    private void handleDragUp(float deltaY) {
        if (imageInfoOverlay != null) {
            float progress = Math.min(deltaY / 80f, 1f);
            imageInfoOverlay.setVisibility(View.VISIBLE);
            imageInfoOverlay.setTranslationY(-deltaY * 0.8f);
            imageInfoOverlay.setAlpha(progress);

            // Update info
            Uri currentUri = getCurrentImageUri();
            if (currentUri != null) {
                MediaInfo mediaInfo = MediaInfo.fromUri(this, currentUri);
                updateImageInfo(mediaInfo);
            }
        }
    }

    private void handleReleaseDown(float totalDrag) {
        int current = viewPager.getCurrentItem();
        RecyclerView recycler = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(current);

        if (vh instanceof PhotoAdapter.ViewerHolder) {
            EnhancedPhotoView pv = ((PhotoAdapter.ViewerHolder) vh).photoView;

            if (totalDrag > DRAG_THRESHOLD) {
                if (transitionName != null) {
                    ViewCompat.setTransitionName(pv, transitionName);
                }
                finishAfterTransition();
            } else {
                // Reset PhotoView to normal state
                pv.animate().translationY(0).scaleX(1f).scaleY(1f).start();
                pv.setScale(1f, false);
                pv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                dimBackground.animate().alpha(1f).start();
            }
        }
    }

    private void handleReleaseUp(float totalDrag) {
        if (totalDrag > 30) {
            // Show image info permanently
            showImageInfo();
        } else {
            // Hide image info
            hideImageInfo();
        }
    }

    private void showImageInfo() {
        if (imageInfoOverlay == null) return;

        Uri currentUri = getCurrentImageUri();
        if (currentUri != null) {
            MediaInfo mediaInfo = MediaInfo.fromUri(this, currentUri);
            updateImageInfo(mediaInfo);
        }

        imageInfoOverlay.setVisibility(View.VISIBLE);
        imageInfoOverlay.setAlpha(0f);
        imageInfoOverlay.setTranslationY(-100f);

        imageInfoOverlay.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void hideImageInfo() {
        if (imageInfoOverlay == null) return;

        imageInfoOverlay.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        imageInfoOverlay.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    /* ───────── Helper: update image info display ───────── */
    private void updateImageInfo(MediaInfo mediaInfo) {
        if (imageInfoOverlay == null) return;

        TextView tvFileName = imageInfoOverlay.findViewById(R.id.tvFileName);
        TextView tvDate = imageInfoOverlay.findViewById(R.id.tvDate);
        TextView tvTime = imageInfoOverlay.findViewById(R.id.tvTime);
        TextView tvSize = imageInfoOverlay.findViewById(R.id.tvSize);

        if (tvFileName != null) tvFileName.setText(mediaInfo.getFileName());
        if (tvDate != null) tvDate.setText(mediaInfo.getFormattedDate());
        if (tvTime != null) tvTime.setText(mediaInfo.getFormattedTime());
        if (tvSize != null) tvSize.setText(mediaInfo.getFormattedSize());
    }

    /* ───────── Helper: get current image URI ───────── */
    /* ───────── Helper: get current image URI ───────── */
    public Uri getCurrentImageUri() {
        // Look in SharedData instead of the Intent!
        List<Uri> imageUris = SharedData.currentImageUris;

        if (imageUris != null && !imageUris.isEmpty()) {
            int currentPosition = viewPager.getCurrentItem();
            if (currentPosition >= 0 && currentPosition < imageUris.size()) {
                return imageUris.get(currentPosition);
            }
        }
        return null;
    }
    private void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    /* ========================================================================================= */
    /* AI PHOTO CUTOUT FEATURES                                   */
    /* ========================================================================================= */

    /**
     * Call this method from your UI (e.g., an "AI Edit" Floating Action Button)
     * to trigger the cutout process for the currently viewed image.
     */
    public void performPhotoCutout() {
        Uri currentUri = getCurrentImageUri();
        if (currentUri == null) return;

        try {
            // Phase A: Prepare the input image
            InputImage image = InputImage.fromFilePath(this, currentUri);

            // Phase B: Configure the Segmenter
            SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build();
            SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);

            // Phase C: Process the image asynchronously
            Toast.makeText(this, "Processing AI Cutout...", Toast.LENGTH_SHORT).show();

            segmenter.process(image)
                    .addOnSuccessListener(result -> {
                        Bitmap foregroundBitmap = result.getForegroundBitmap();
                        if (foregroundBitmap != null) {
                            showCutoutResultDialog(foregroundBitmap);
                        } else {
                            Toast.makeText(this, "No subject detected.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Cutout failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCutoutResultDialog(Bitmap cutoutBitmap) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cutout_result, null);
        ImageView ivPreview = dialogView.findViewById(R.id.ivCutoutPreview);
        Button btnDiscard = dialogView.findViewById(R.id.btnDiscard);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        // --- NEW CODE: Safely scale down the image for the UI preview ---
        int maxSize = 1024; // Max width or height for the preview
        float scale = Math.min(((float) maxSize / cutoutBitmap.getWidth()), ((float) maxSize / cutoutBitmap.getHeight()));

        Bitmap previewBitmap;
        if (scale < 1f) {
            // Shrink it down so the Canvas can draw it
            previewBitmap = Bitmap.createScaledBitmap(cutoutBitmap,
                    (int)(cutoutBitmap.getWidth() * scale),
                    (int)(cutoutBitmap.getHeight() * scale), true);
        } else {
            previewBitmap = cutoutBitmap;
        }

        // Set the smaller image to the ImageView
        ivPreview.setImageBitmap(previewBitmap);
        // ----------------------------------------------------------------

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnDiscard.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            // We pass the ORIGINAL high-res cutoutBitmap to be saved, not the preview!
            saveCutoutToGallery(cutoutBitmap);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveCutoutToGallery(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "SmartGallery_Cutout_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartGallery");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            if (uri != null) {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                    Toast.makeText(this, "Cutout saved successfully!", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save cutout.", Toast.LENGTH_SHORT).show();
        }
    }
}