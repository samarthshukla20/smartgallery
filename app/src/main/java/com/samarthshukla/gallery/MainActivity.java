package com.samarthshukla.gallery;

import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(navListener);

        applyBottomBarColors();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PhotosFragment())
                    .commit();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Check if current fragment is PhotosFragment and handle selection mode
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof PhotosFragment) {
            PhotosFragment photosFragment = (PhotosFragment) currentFragment;
            if (photosFragment.isInSelectionMode()) {
                photosFragment.exitSelectionMode();
                return;
            }
        }
        
        super.onBackPressed();
    }


    private final BottomNavigationView.OnItemSelectedListener navListener = item -> {
        Fragment selectedFragment = null;
        int id = item.getItemId();

        if (id == R.id.nav_photos) {
            selectedFragment = new PhotosFragment();
        } else if (id == R.id.nav_albums) {
            selectedFragment = new AlbumsFragment();
        } else if (id == R.id.nav_favourites) {
            selectedFragment = new FavouritesFragment();
        } else if (id == R.id.nav_search) {
            selectedFragment = new SearchFragment();
        }

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
            return true;
        }
        return false;
    };

    private void applyBottomBarColors() {
        boolean isDark = isNightMode();

        int selectedColor = 0xFF007AFF;
        int unselectedColor = isDark ? 0xFFFFFFFF : 0xFF000000;
        int bgColor = isDark ? 0xFF000000 : 0xFFFFFFFF;

        ColorStateList iconColors = new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] { -android.R.attr.state_checked }
                },
                new int[] {
                        selectedColor,
                        unselectedColor
                });

        bottomNav.setItemIconTintList(iconColors);
        bottomNav.setItemTextColor(iconColors);
        bottomNav.setBackgroundColor(bgColor);
    }

    private boolean isNightMode() {
        int currentNightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public void refreshGallery() {
        // Find the current PhotosFragment and refresh it
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof PhotosFragment) {
            PhotosFragment photosFragment = (PhotosFragment) currentFragment;
            photosFragment.loadMedia();
        }
    }
}