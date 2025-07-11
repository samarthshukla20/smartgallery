package com.samarthshukla.gallery;

import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startLogCapture();
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

    private void startLogCapture() {
        try {
            File logFile = new File(getExternalFilesDir(null), "log.txt");
            Runtime.getRuntime().exec("logcat -c");
            Runtime.getRuntime().exec(new String[] { "logcat", "-f", logFile.getAbsolutePath(), "*:V" });
        } catch (IOException ignored) {
        }
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
}