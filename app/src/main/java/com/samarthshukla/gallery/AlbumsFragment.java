package com.samarthshukla.gallery;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlbumsFragment extends Fragment {

    private RecyclerView albumRecyclerView;
    private FolderAdapter folderAdapter;
    private List<ImageFolder> folderList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_albums, container, false);

        albumRecyclerView = view.findViewById(R.id.albumsRecyclerView);
        albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        folderList = loadImageFolders();
        folderAdapter = new FolderAdapter(requireContext(), folderList, folder -> {
            Intent intent = new Intent(requireContext(), AlbumsActivity.class);
            intent.putExtra("bucketName", folder.getFolderName());
            startActivity(intent);
        });

        albumRecyclerView.setAdapter(folderAdapter);

        return view;
    }

    private List<ImageFolder> loadImageFolders() {
        Map<String, ImageFolder> folderMap = new LinkedHashMap<>();
        loadFromMediaStore(folderMap, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        loadFromMediaStore(folderMap, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        return new ArrayList<>(folderMap.values());
    }

    private void loadFromMediaStore(Map<String, ImageFolder> folderMap, Uri collectionUri) {
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                collectionUri, projection, null, null, sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String bucketName = cursor.getString(bucketColumn);
                    Uri contentUri = ContentUris.withAppendedId(collectionUri, id);

                    if (folderMap.containsKey(bucketName)) {
                        ImageFolder folder = folderMap.get(bucketName);
                        folderMap.put(bucketName, new ImageFolder(
                                folder.getFolderName(),
                                folder.getFirstImageUri(), // Keep existing thumbnail
                                folder.getImageCount() + 1
                        ));
                    } else {
                        folderMap.put(bucketName, new ImageFolder(
                                bucketName,
                                contentUri,
                                1
                        ));
                    }
                }
            }
        }
    }
}