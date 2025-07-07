package com.samarthshukla.gallery;

import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Rational;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URI = "video_uri";

    private ExoPlayer player;
    private PlayerView playerView;

    private ImageButton fullscreenButton;
    private ImageButton orientationToggleButton;
    private ImageButton pipButton;

    private boolean isFullscreen = false;
    private final Handler visibilityHandler = new Handler();
    private final Runnable hideButtonsRunnable = this::hideExtraButtons;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        playerView = findViewById(R.id.playerView);
        fullscreenButton = findViewById(R.id.fullscreenButton);
        orientationToggleButton = findViewById(R.id.orientationToggleButton);
        pipButton = findViewById(R.id.pipButton);

        Uri videoUri = getIntent().getParcelableExtra(EXTRA_VIDEO_URI);
        if (videoUri == null) {
            finish();
            return;
        }

        // Initialize ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        // Handle fullscreen toggle
        fullscreenButton.setOnClickListener(v -> {
            if (isFullscreen) {
                exitFullscreen();
            } else {
                enterFullscreen();
            }
        });

        // Handle orientation toggle
        orientationToggleButton.setOnClickListener(v -> toggleOrientation());

        // Handle Picture-in-Picture (PiP) mode
        pipButton.setOnClickListener(v -> enterPipMode());

        // Hide buttons after 2 seconds in fullscreen
        isFullscreen = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        scheduleButtonHideIfFullscreen();

        // Hide buttons when controller hides
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (visibility == View.VISIBLE) {
                fullscreenButton.animate().alpha(1f).setDuration(300).withStartAction(() -> fullscreenButton.setVisibility(View.VISIBLE));
                orientationToggleButton.animate().alpha(1f).setDuration(300).withStartAction(() -> orientationToggleButton.setVisibility(View.VISIBLE));
                pipButton.animate().alpha(1f).setDuration(300).withStartAction(() -> pipButton.setVisibility(View.VISIBLE));
                scheduleButtonHideIfFullscreen();
            } else {
                fullscreenButton.animate().alpha(0f).setDuration(300).withEndAction(() -> fullscreenButton.setVisibility(View.GONE));
                orientationToggleButton.animate().alpha(0f).setDuration(300).withEndAction(() -> orientationToggleButton.setVisibility(View.GONE));
                pipButton.animate().alpha(0f).setDuration(300).withEndAction(() -> pipButton.setVisibility(View.GONE));
            }
        });

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterFullscreen();
        }

    }

    private void toggleFullscreen() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        isFullscreen = currentOrientation == Configuration.ORIENTATION_PORTRAIT;
        scheduleButtonHideIfFullscreen();
    }

    private void toggleOrientation() {
        toggleFullscreen(); // Same as fullscreen toggle
    }

    private void scheduleButtonHideIfFullscreen() {
        visibilityHandler.removeCallbacks(hideButtonsRunnable);
        if (isFullscreen && !isInPictureInPictureModeCompat()) {
            visibilityHandler.postDelayed(hideButtonsRunnable, 2000); // 2 seconds
        }
    }

    private void hideExtraButtons() {
        fullscreenButton.setVisibility(View.GONE);
        orientationToggleButton.setVisibility(View.GONE);
        pipButton.setVisibility(View.GONE);
    }

    private void showExtraButtons() {
        fullscreenButton.setVisibility(View.VISIBLE);
        orientationToggleButton.setVisibility(View.VISIBLE);
        pipButton.setVisibility(View.VISIBLE);
        scheduleButtonHideIfFullscreen();
    }

    private boolean isInPictureInPictureModeCompat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPipMode() {
        Rational aspectRatio = new Rational(playerView.getWidth(), playerView.getHeight());
        PictureInPictureParams pipParams = new PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build();
        enterPictureInPictureMode(pipParams);
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            // Immediately hide all extra buttons
            fullscreenButton.setVisibility(View.GONE);
            orientationToggleButton.setVisibility(View.GONE);
            pipButton.setVisibility(View.GONE); // Add this line if using pipButton
        } else {
            // Only show when not in PiP and fullscreen
            if (isFullscreen) {
                scheduleButtonHideIfFullscreen();
            } else {
                fullscreenButton.setVisibility(View.VISIBLE);
                orientationToggleButton.setVisibility(View.VISIBLE);
                pipButton.setVisibility(View.VISIBLE); // Show again on resume
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void exitFullscreen() {
        isFullscreen = false;
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

}