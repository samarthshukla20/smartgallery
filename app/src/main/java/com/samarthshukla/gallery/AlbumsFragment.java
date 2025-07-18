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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_albums, container, false);

        albumRecyclerView = view.findViewById(R.id.albumsRecyclerView);
        albumRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<ImageFolder> folderList = loadImageFolders();
        List<Object> mixedItems = new ArrayList<>();
        mixedItems.add(folderList);  // Only adding one section for now

        MainAlbumAdapter adapter = new MainAlbumAdapter(requireContext(), mixedItems, folder -> {
            Intent intent = new Intent(getContext(), AlbumsActivity.class);
            intent.putExtra("bucketId", folder.getBucketId());
            intent.putExtra("bucketName", folder.getFolderName());
            startActivity(intent);
        });

        albumRecyclerView.setAdapter(adapter);

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
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID // <-- Add this
        };

        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                collectionUri, projection, null, null, sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                int bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                int bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String bucketName = cursor.getString(bucketNameColumn);
                    String bucketId = cursor.getString(bucketIdColumn);
                    Uri contentUri = ContentUris.withAppendedId(collectionUri, id);

                    if (folderMap.containsKey(bucketId)) {
                        ImageFolder folder = folderMap.get(bucketId);
                        folderMap.put(bucketId, new ImageFolder(
                                folder.getFolderName(),
                                folder.getFirstImageUri(),
                                folder.getImageCount() + 1,
                                bucketId
                        ));
                    } else {
                        folderMap.put(bucketId, new ImageFolder(
                                bucketName,
                                contentUri,
                                1,
                                bucketId
                        ));
                    }
                }
            }
        }
    }
}