package com.samarthshukla.gallery;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class VideoChoiceDialogActivity extends AppCompatActivity {

    private Uri videoUri;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String uriString = getIntent().getStringExtra("video_uri");
        if (uriString == null) {
            finish();
            return;
        }

        videoUri = Uri.parse(uriString);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        showChoiceDialog();
    }

    private void showChoiceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Choose Video Player")
                .setMessage("Do you want to play video with the app's player or use the device's default player?")
                .setPositiveButton("In-App Player", (dialog, which) -> {
                    saveChoiceAndPlay("in_app");
                })
                .setNegativeButton("System Player", (dialog, which) -> {
                    saveChoiceAndPlay("system");
                })
                .setOnCancelListener(dialog -> {
                    // If user cancels, just finish
                    finish();
                })
                .show();
    }

    private void saveChoiceAndPlay(String choice) {
        prefs.edit().putString("video_player_choice", choice).apply();
        playVideoWithChoice(choice);
        finish();
    }

    private void playVideoWithChoice(String choice) {
        Intent intent;
        if ("in_app".equals(choice)) {
            intent = new Intent(this, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, videoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);  // <-- Added here
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(videoUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(intent);
    }
}