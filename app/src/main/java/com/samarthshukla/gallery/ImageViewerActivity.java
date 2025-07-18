package com.samarthshukla.gallery;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.github.chrisbanes.photoview.PhotoView;

public class ImageViewerActivity extends AppCompatActivity {

    private PhotoView photoView;
    private boolean isSystemUiVisible = false;

    private long lastTapTime = 0;
    private static final int DOUBLE_TAP_TIMEOUT = 200;
    private int tapCount = 0;
    private Handler tapHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Force fullscreen *before* content view is set
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        } else {
            getWindow().setDecorFitsSystemWindows(false);
        }

        setContentView(R.layout.item_photo_viewer_page);
        photoView = findViewById(R.id.photoView);

        // Load image
        String imageUri = getIntent().getStringExtra("imageUri");
        if (imageUri != null) {
            photoView.setImageURI(Uri.parse(imageUri));
        }

        // Tap listener
        photoView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                toggleSystemUI();
            }
            return false; // Let PhotoView handle zoom
        });

        hideSystemUI(); // Initial fullscreen
    }

    private void handleTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
            tapCount++;
        } else {
            tapCount = 1;
        }
        lastTapTime = now;

        tapHandler.removeCallbacksAndMessages(null);
        tapHandler.postDelayed(() -> {
            if (tapCount == 1) {
                toggleSystemUI();
            }
            tapCount = 0;
        }, DOUBLE_TAP_TIMEOUT);
    }

    private void toggleSystemUI() {
        if (isSystemUiVisible) {
            Log.d("ImageViewer", "Hiding system UI");
            hideSystemUI();
        } else {
            Log.d("ImageViewer", "Showing system UI");
            showSystemUI();
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
        isSystemUiVisible = false;
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
        isSystemUiVisible = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isSystemUiVisible) {
            hideSystemUI();
        }
    }
}