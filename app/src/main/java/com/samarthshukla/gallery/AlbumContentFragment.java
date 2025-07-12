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
import android.graphics.Rect;
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
import java.util.Locale;
import java.util.Map;

public class AlbumContentFragment extends Fragment {

    private static final String ARG_FOLDER_NAME = "folder_name";

    public static AlbumContentFragment newInstance(String folderName) {
        AlbumContentFragment fragment = new AlbumContentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FOLDER_NAME, folderName);
        fragment.setArguments(args);
        return fragment;
    }

    private String folderName;
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final List<MediaGroupedItem> groupedItems = new ArrayList<>();
    private RecyclerView recyclerView;
    private PhotoAdapter adapter;
    private Button btnAll, btnDays, btnMonths, btnYears;
    private View selectedBackground;
    private TextView tvSortDate;
    private ActivityResultLauncher<String[]> requestPermissions;
    public enum SortMode {ALL, DAYS, MONTHS, YEARS}

    private SortMode currentSort = SortMode.ALL;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photos, container, false);

        folderName = getArguments() != null ? getArguments().getString(ARG_FOLDER_NAME) : "";

        TextView titleView = view.findViewById(R.id.titleView);
        titleView.setText(folderName);
        btnAll = view.findViewById(R.id.btnAll);
        btnDays = view.findViewById(R.id.btnDays);
        btnMonths = view.findViewById(R.id.btnMonths);
        btnYears = view.findViewById(R.id.btnYears);
        selectedBackground = view.findViewById(R.id.selectedBackground);
        tvSortDate = view.findViewById(R.id.tvSortDate);

        recyclerView = view.findViewById(R.id.photosRecyclerView);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        recyclerView.setLayoutManager(layoutManager);
        adapter = PhotoAdapter.forGrid(requireContext(), groupedItems);
        layoutManager.setSpanSizeLookup(adapter.spanLookup());
        recyclerView.setAdapter(adapter);

        setupFilterButtons();
        setupPermissions();

        return view;
    }

    private void setupPermissions() {
        requestPermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean granted = true;
            for (Boolean isGranted : result.values()) {
                granted &= isGranted;
            }

            if (granted) {
                loadMediaFromFolder();
            } else {
                Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        });

        if (hasPermission()) {
            loadMediaFromFolder();
        } else {
            requestPermissions.launch(getRequiredPermissions());
        }
    }

    private boolean hasPermission() {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        } else {
            return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
    }

    private void loadMediaFromFolder() {
        new Thread(() -> {
            List<MediaItem> allItems = new ArrayList<>();
            allItems.addAll(fetchFrom(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false));
            allItems.addAll(fetchFrom(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true));
            Collections.sort(allItems, (a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));

            mediaItems.clear();
            mediaItems.addAll(allItems);

            requireActivity().runOnUiThread(() -> buildGroupedList(currentSort));
        }).start();
    }

    private List<MediaItem> fetchFrom(Uri collection, boolean isVideo) {
        List<MediaItem> list = new ArrayList<>();

        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };

        String selection = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{folderName};
        String order = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                collection, projection, selection, selectionArgs, order)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    long date = cursor.getLong(dateCol) * 1000L;
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    list.add(new MediaItem(uri, isVideo, date, date));
                }
            }
        }

        return list;
    }

    private void buildGroupedList(SortMode mode) {
        groupedItems.clear();
        Map<String, List<MediaItem>> groupedMap = new LinkedHashMap<>();

        for (MediaItem item : mediaItems) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(item.getDateAdded());

            String key;
            switch (mode) {
                case DAYS:
                    key = DateFormat.format("MMMM dd, yyyy", cal).toString();
                    break;
                case MONTHS:
                    key = DateFormat.format("MMMM yyyy", cal).toString();
                    break;
                case YEARS:
                    key = String.valueOf(cal.get(Calendar.YEAR));
                    break;
                default:
                    key = "All Photos";
            }

            groupedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<String, List<MediaItem>> entry : groupedMap.entrySet()) {
            String header = entry.getKey();
            if (!"All Photos".equals(header)) {
                groupedItems.add(new MediaGroupedItem(MediaGroupedItem.TYPE_HEADER, header));
            }

            for (MediaItem item : entry.getValue()) {
                groupedItems.add(new MediaGroupedItem(MediaGroupedItem.TYPE_MEDIA, item));
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void setupFilterButtons() {
        List<Button> buttons = Arrays.asList(btnAll, btnDays, btnMonths, btnYears);

        View.OnClickListener listener = v -> {
            Button clicked = (Button) v;
            updateSortSelection(clicked);

            if (v == btnAll) {
                currentSort = SortMode.ALL;
                tvSortDate.setText("All Photos");
            } else if (v == btnDays) {
                currentSort = SortMode.DAYS;
                tvSortDate.setText("Days");
            } else if (v == btnMonths) {
                currentSort = SortMode.MONTHS;
                tvSortDate.setText("Months");
            } else if (v == btnYears) {
                currentSort = SortMode.YEARS;
                tvSortDate.setText("Years");
            }

            buildGroupedList(currentSort);
        };

        for (Button b : buttons) b.setOnClickListener(listener);
        btnAll.performClick(); // Default selected
    }

    private void updateSortSelection(Button selected) {
        selectedBackground.setVisibility(View.VISIBLE);
        selectedBackground.post(() -> {
            Rect rect = new Rect();
            selected.getGlobalVisibleRect(rect);
            Rect parentRect = new Rect();
            ((View) selected.getParent()).getGlobalVisibleRect(parentRect);

            float translationX = rect.left - parentRect.left;
            selectedBackground.animate().x(translationX).setDuration(200).start();

            ViewGroup.LayoutParams params = selectedBackground.getLayoutParams();
            params.width = selected.getWidth();
            selectedBackground.setLayoutParams(params);
        });

        for (Button b : Arrays.asList(btnAll, btnDays, btnMonths, btnYears)) {
            b.setTextColor(b == selected ? Color.BLACK : Color.BLACK);
        }
    }
}