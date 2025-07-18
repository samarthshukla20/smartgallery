package com.samarthshukla.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AlbumsActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_albums);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_folders);

        if (bottomNav != null) bottomNav.setVisibility(View.GONE);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_photos) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_folders) {
                return true; // already here
            } else if (id == R.id.nav_favourites) {
                Intent intent = new Intent(this, FavouritesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            String bucketId = getIntent().getStringExtra("bucketId");
            String bucketName = getIntent().getStringExtra("bucketName");

            if (bucketId != null) {
                // Open AlbumContentFragment with bucketId and name
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.albumFragmentContainer, AlbumContentFragment.newInstance(bucketId, bucketName))
                        .commit();
            } else {
                // Show full folder list
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.albumFragmentContainer, new AlbumsFragment())
                        .commit();
            }
        }
    }
}