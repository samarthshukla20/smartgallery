package com.samarthshukla.gallery;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlbumsActivity extends AppCompatActivity {

    private RecyclerView albumRecyclerView;
    private FolderAdapter folderAdapter;
    private List<ImageFolder> folderList;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_albums);

        albumRecyclerView = findViewById(R.id.albumRecyclerView);
        albumRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        folderList = loadImageFolders();
        folderAdapter = new FolderAdapter(this, folderList);
        albumRecyclerView.setAdapter(folderAdapter);

        // Setup bottom nav
        bottomNav = findViewById(R.id.bottomNav);

        // ✅ Force correct tab to be selected
        bottomNav.setSelectedItemId(R.id.nav_folders);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_photos) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }

            else if (id == R.id.nav_folders) {
                // Already in AlbumsActivity
                return true;
            } else if (id == R.id.nav_favourites) {
                Intent intent = new Intent(this, FavouritesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private List<ImageFolder> loadImageFolders() {
        Map<String, ImageFolder> folderMap = new LinkedHashMap<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        String selection = MediaStore.Images.Media.MIME_TYPE + "=? OR " +
                MediaStore.Images.Media.MIME_TYPE + "=?";
        String[] selectionArgs = {"image/jpeg", "image/png"};
        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        try (Cursor cursor = getContentResolver().query(
                collection, projection, selection, selectionArgs, sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String bucketName = cursor.getString(bucketColumn);

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);

                    if (folderMap.containsKey(bucketName)) {
                        ImageFolder folder = folderMap.get(bucketName);
                        folderMap.put(bucketName, new ImageFolder(
                                folder.getFolderName(),
                                folder.getFirstImageUri(),
                                folder.getImageCount() + 1
                        ));
                    } else {
                        folderMap.put(bucketName, new ImageFolder(bucketName, contentUri, 1));
                    }
                }
            }
        }

        return new ArrayList<>(folderMap.values());
    }
}
