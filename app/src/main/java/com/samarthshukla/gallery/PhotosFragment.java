package com.samarthshukla.gallery;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
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

public class PhotosFragment extends Fragment {

    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final List<MediaGroupedItem> groupedItems = new ArrayList<>();

    private RecyclerView recyclerView;
    private PhotoAdapter adapter;

    private Button btnAll, btnDays, btnMonths, btnYears;
    private View selectedBackground;
    private TextView tvSortDate;

    private ActivityResultLauncher<String> singlePermissionLauncher;
    private ActivityResultLauncher<String[]> multiplePermissionLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos, container, false);

        // UI Setup
        recyclerView = view.findViewById(R.id.photosRecyclerView);
        btnAll = view.findViewById(R.id.btnAll);
        btnDays = view.findViewById(R.id.btnDays);
        btnMonths = view.findViewById(R.id.btnMonths);
        btnYears = view.findViewById(R.id.btnYears);
        tvSortDate = view.findViewById(R.id.tvSortDate);
        selectedBackground = view.findViewById(R.id.selectedBackground);

        // Set adapter
        adapter = PhotoAdapter.forGrid(requireContext(), groupedItems);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        layoutManager.setSpanSizeLookup(adapter.spanLookup());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        setupPermissionLaunchers();
        setupFilterButtons();

        if (hasPermission()) {
            loadMedia();
        } else {
            requestPermissions();
        }

        return view;
    }

    private void setupPermissionLaunchers() {
        singlePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted)
                        loadMedia();
                    else
                        Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                });

        multiplePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = result.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false)
                            && result.getOrDefault(Manifest.permission.READ_MEDIA_VIDEO, false);
                    if (granted)
                        loadMedia();
                    else
                        Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            multiplePermissionLauncher.launch(new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            });
        } else {
            singlePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void loadMedia() {
        new Thread(() -> {
            List<MediaItem> items = new ArrayList<>();
            items.addAll(fetchMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false));
            items.addAll(fetchMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true));

            // Sort by date descending
            Collections.sort(items, (a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));

            mediaItems.clear();
            mediaItems.addAll(items);

            requireActivity().runOnUiThread(() -> buildGroupedList("ALL"));
        }).start();
    }

    private List<MediaItem> fetchMedia(Uri uri, boolean isVideo) {
        List<MediaItem> list = new ArrayList<>();
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED
        };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[] { "%DCIM/Camera%" };

        try (Cursor cursor = requireContext().getContentResolver().query(uri, projection, selection, args, null)) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIndex);
                    long dateAdded = cursor.getLong(dateIndex) * 1000L;
                    Uri contentUri = ContentUris.withAppendedId(uri, id);

                    list.add(new MediaItem(contentUri, isVideo, dateAdded, dateAdded));
                }
            }
        }

        return list;
    }

    private void buildGroupedList(String mode) {
        groupedItems.clear();

        Map<String, List<MediaItem>> groupedMap = new LinkedHashMap<>();
        for (MediaItem item : mediaItems) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(item.getDateAdded());

            String key;
            switch (mode) {
                case "DAY":
                    key = DateFormat.format("MMMM dd, yyyy", calendar).toString();
                    break;
                case "MONTH":
                    key = DateFormat.format("MMMM yyyy", calendar).toString();
                    break;
                case "YEAR":
                    key = String.valueOf(calendar.get(Calendar.YEAR));
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

        View.OnClickListener clickListener = v -> {
            Button btn = (Button) v;
            updateSelectionUI(btn);

            if (btn == btnAll) {
                tvSortDate.setText("All Photos");
                buildGroupedList("ALL");
            } else if (btn == btnDays) {
                tvSortDate.setText("Grouped by Day");
                buildGroupedList("DAY");
            } else if (btn == btnMonths) {
                tvSortDate.setText("Grouped by Month");
                buildGroupedList("MONTH");
            } else if (btn == btnYears) {
                tvSortDate.setText("Grouped by Year");
                buildGroupedList("YEAR");
            }
        };

        for (Button b : allButtons)
            b.setOnClickListener(clickListener);
        btnAll.performClick(); // Default
    }

    private void updateSelectionUI(Button selected) {
        selectedBackground.setVisibility(View.VISIBLE);
        selectedBackground.post(() -> {
            Rect rect = new Rect();
            selected.getGlobalVisibleRect(rect);

            Rect parentRect = new Rect();
            ((View) selected.getParent()).getGlobalVisibleRect(parentRect);

            float x = rect.left - parentRect.left;

            selectedBackground.animate().x(x).setDuration(200).start();

            ViewGroup.LayoutParams params = selectedBackground.getLayoutParams();
            params.width = selected.getWidth();
            selectedBackground.setLayoutParams(params);
        });

        btnAll.setTextColor(Color.BLACK);
        btnDays.setTextColor(Color.BLACK);
        btnMonths.setTextColor(Color.BLACK);
        btnYears.setTextColor(Color.BLACK);
        selected.setTextColor(Color.BLACK);
    }
}