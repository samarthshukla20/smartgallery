package com.samarthshukla.gallery;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.graphics.Rect;

public class PhotosFragment extends Fragment {

    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final List<MediaGroupedItem> groupedItems = new ArrayList<>();
    private RecyclerView recyclerView;
    private PhotoAdapter adapter;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String[]> requestMultiplePermissions;
    private Button btnAll, btnDays, btnMonths, btnYears;
    private Button selectedButton;
    private TextView tvSortDate;
    private View selectedBackground;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos, container, false);

        TextView selectView = view.findViewById(R.id.selectView);
        btnAll = view.findViewById(R.id.btnAll);
        btnDays = view.findViewById(R.id.btnDays);
        btnMonths = view.findViewById(R.id.btnMonths);
        btnYears = view.findViewById(R.id.btnYears);
        tvSortDate = view.findViewById(R.id.tvSortDate);
        selectedBackground = view.findViewById(R.id.selectedBackground);

        selectView.setOnClickListener(v -> Toast.makeText(requireContext(), "Select clicked", Toast.LENGTH_SHORT).show());

        recyclerView = view.findViewById(R.id.photosRecyclerView);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), 3);
        recyclerView.setLayoutManager(gridLayoutManager);

        adapter = new PhotoAdapter(requireContext(), groupedItems);
        gridLayoutManager.setSpanSizeLookup(adapter.getSpanSizeLookup());
        recyclerView.setAdapter(adapter);

        setupPermissions();
        setupFilterButtons();

        return view;
    }

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) loadMedia();
            else Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
        });

        requestMultiplePermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean granted = result.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false)
                    && result.getOrDefault(Manifest.permission.READ_MEDIA_VIDEO, false);
            if (granted) loadMedia();
            else Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
        });

        if (hasPermission()) {
            loadMedia();
        } else {
            requestPermission();
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestMultiplePermissions.launch(new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            });
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void loadMedia() {
        new Thread(() -> {
            List<MediaItem> allItems = new ArrayList<>();

            allItems.addAll(fetchMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false));
            allItems.addAll(fetchMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true));

            // Sort by DATE_ADDED descending
            Collections.sort(allItems, (a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));

            mediaItems.clear();
            mediaItems.addAll(allItems);

            requireActivity().runOnUiThread(() -> buildGroupedList("ALL"));
        }).start();
    }

    private List<MediaItem> fetchMedia(Uri collection, boolean isVideo) {
        List<MediaItem> items = new ArrayList<>();

        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED
        };

        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[]{"%DCIM/Camera%"};
        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                collection, projection, selection, selectionArgs, sortOrder)) {

            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    long dateAdded = cursor.getLong(dateAddedCol);
                    Uri contentUri = ContentUris.withAppendedId(collection, id);

                    // Use dateAdded for both sorting and grouping
                    items.add(new MediaItem(contentUri, isVideo, dateAdded * 1000L, dateAdded * 1000L));
                }
            }
        }

        return items;
    }

    private void buildGroupedList(String mode) {
        groupedItems.clear();
        Map<String, List<MediaItem>> groupedMap = new LinkedHashMap<>();

        for (MediaItem item : mediaItems) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(item.getDateAdded());

            String key;
            switch (mode) {
                case "DAY":
                    key = DateFormat.format("MMMM dd, yyyy", cal).toString();
                    break;
                case "MONTH":
                    key = DateFormat.format("MMMM yyyy", cal).toString();
                    break;
                case "YEAR":
                    key = String.valueOf(cal.get(Calendar.YEAR));
                    break;
                default:
                    key = "All Photos";
            }

            groupedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<String, List<MediaItem>> entry : groupedMap.entrySet()) {
            if (!entry.getKey().equals("All Photos")) {
                groupedItems.add(new MediaGroupedItem(MediaGroupedItem.TYPE_HEADER, entry.getKey()));
            }
            for (MediaItem item : entry.getValue()) {
                groupedItems.add(new MediaGroupedItem(MediaGroupedItem.TYPE_MEDIA, item));
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void setupFilterButtons() {
        List<Button> allButtons = Arrays.asList(btnAll, btnDays, btnMonths, btnYears);

        View.OnClickListener listener = v -> {
            Button clickedButton = (Button) v;
            updateSortSelection(clickedButton);

            if (v == btnAll) {
                tvSortDate.setText("All Photos");
                buildGroupedList("ALL");
            } else if (v == btnDays) {
                tvSortDate.setText("Grouped by Day");
                buildGroupedList("DAY");
            } else if (v == btnMonths) {
                tvSortDate.setText("Grouped by Month");
                buildGroupedList("MONTH");
            } else if (v == btnYears) {
                tvSortDate.setText("Grouped by Year");
                buildGroupedList("YEAR");
            }
        };

        btnAll.setOnClickListener(listener);
        btnDays.setOnClickListener(listener);
        btnMonths.setOnClickListener(listener);
        btnYears.setOnClickListener(listener);

        btnAll.performClick();
    }

    private void updateSortSelection(Button selected) {
        selectedButton = selected;

        selectedBackground.setVisibility(View.VISIBLE);
        selectedBackground.post(() -> {
            Rect rect = new Rect();
            selected.getGlobalVisibleRect(rect);
            Rect parentRect = new Rect();
            ((View) selected.getParent()).getGlobalVisibleRect(parentRect);

            float translationX = rect.left - parentRect.left;
            selectedBackground.animate()
                    .x(translationX)
                    .setDuration(200)
                    .start();

            ViewGroup.LayoutParams params = selectedBackground.getLayoutParams();
            params.width = selected.getWidth();
            selectedBackground.setLayoutParams(params);
        });

        Button[] buttons = {btnAll, btnDays, btnMonths, btnYears};
        for (Button b : buttons) {
            b.setTextColor(b == selected ? Color.BLACK : Color.BLACK);
        }
    }
}