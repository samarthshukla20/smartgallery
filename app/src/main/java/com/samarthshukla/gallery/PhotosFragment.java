package com.samarthshukla.gallery;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import java.util.HashSet;

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
import android.os.Vibrator;
import android.os.VibrationEffect;
import com.google.android.material.snackbar.Snackbar;

public class PhotosFragment extends Fragment {

    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final List<MediaGroupedItem> groupedItems = new ArrayList<>();

    private RecyclerView recyclerView;
    private PhotoAdapter adapter;

    private Button btnAll, btnDays, btnMonths, btnYears;
    private View selectedBackground;
    private TextView tvSortDate;
    
    // Selection functionality
    private View bottomActionBar;
    private TextView tvSelectionCount;
    private RecycleBinManager recycleBinManager;

    private ActivityResultLauncher<String> singlePermissionLauncher;
    private ActivityResultLauncher<String[]> multiplePermissionLauncher;

    private View floatingActionBoxInclude;
    private View fabDimOverlay;
    private View fabActionBox;
    private View fabShare;
    private View fabDelete;

    private int lastSelectionCount = 0;
    private boolean selectionSnackbarShown = false;

    private View headerContainer;
    private View filterContainer;

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
        headerContainer = view.findViewById(R.id.headerContainer);
        filterContainer = view.findViewById(R.id.filterContainer);

        floatingActionBoxInclude = view.findViewById(R.id.floatingActionBoxInclude);
        if (floatingActionBoxInclude != null) {
            fabDimOverlay = floatingActionBoxInclude.findViewById(R.id.fab_dim_overlay);
            fabActionBox = floatingActionBoxInclude.findViewById(R.id.fab_action_box);
            fabShare = floatingActionBoxInclude.findViewById(R.id.fab_share);
            fabDelete = floatingActionBoxInclude.findViewById(R.id.fab_delete);
            if (fabShare != null) {
                fabShare.setOnClickListener(v -> shareSelectedImages());
            }
            if (fabDelete != null) {
                fabDelete.setOnClickListener(v -> deleteSelectedImages());
            }
        }

        // Set adapter
        adapter = PhotoAdapter.forGrid(requireContext(), groupedItems);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        layoutManager.setSpanSizeLookup(adapter.spanLookup());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        
        // Initialize selection functionality
        bottomActionBar = view.findViewById(R.id.bottomActionBar);
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount);
        recycleBinManager = new RecycleBinManager(requireContext());
        
        // Test basic functionality
        if (recycleBinManager.testRecycleBinFunctionality()) {
            android.util.Log.d("PhotosFragment", "Recycle bin functionality test passed");
        } else {
            android.util.Log.e("PhotosFragment", "Recycle bin functionality test failed");
        }
        
        // Test delete permissions
        if (recycleBinManager.hasDeletePermissions()) {
            android.util.Log.d("PhotosFragment", "Delete permissions test passed");
        } else {
            android.util.Log.e("PhotosFragment", "Delete permissions test failed");
        }
        
        // Set selection listener
        adapter.setOnSelectionChangedListener(new PhotoAdapter.OnSelectionChangedListener() {
            @Override
            public void onSelectionChanged(List<Uri> selectedItems) {
                updateSelectionMenu(selectedItems);
            }
            
            @Override
            public void onSelectionModeChanged(boolean isSelectionMode) {
                if (isSelectionMode) {
                    showSelectionMenu();
                    TextView selectView = getView().findViewById(R.id.selectView);
                    if (selectView != null) {
                        selectView.setText("Cancel");
                        selectView.setOnClickListener(v -> {
                            adapter.clearSelection();
                        });
                    }
                } else {
                    hideSelectionMenu();
                    TextView selectView = getView().findViewById(R.id.selectView);
                    if (selectView != null) {
                        selectView.setText("Select");
                        selectView.setOnClickListener(v -> {
                            adapter.enterSelectionMode();
                        });
                    }
                }
            }
        });

        setupPermissionLaunchers();
        setupFilterButtons();
        setupSelectionMenu();
        TextView selectView = view.findViewById(R.id.selectView);
        if (selectView != null) {
            if (adapter.isSelectionMode()) {
                selectView.setText("Cancel");
                selectView.setOnClickListener(v -> adapter.clearSelection());
            } else {
                selectView.setText("Select");
                selectView.setOnClickListener(v -> adapter.enterSelectionMode());
            }
        }

        try {
            if (hasPermission()) {
                loadMedia();
            } else {
                requestPermissions();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error loading media: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        // --- Gesture-based multi-select ---
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            boolean isSelecting = false;
            boolean longPressTriggered = false;
            int initialPosition = -1;
            int lastToggledPosition = -1;
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable longPressRunnable;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        longPressTriggered = false;
                        isSelecting = false;
                        initialPosition = -1;
                        lastToggledPosition = -1;
                        View child = rv.findChildViewUnder(e.getX(), e.getY());
                        if (child != null) {
                            initialPosition = rv.getChildAdapterPosition(child);
                            longPressRunnable = () -> {
                                if (initialPosition != RecyclerView.NO_POSITION && !adapter.isSelectionMode()) {
                                    adapter.enterSelectionMode();
                                    adapter.toggleSelectionByPosition(initialPosition);
                                    lastToggledPosition = initialPosition;
                                    isSelecting = true;
                                    longPressTriggered = true;
                                }
                            };
                            handler.postDelayed(longPressRunnable, 250); // Fast long-press
                        }
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (adapter.isSelectionMode() && (isSelecting || longPressTriggered)) {
                            View child = rv.findChildViewUnder(e.getX(), e.getY());
                            if (child != null) {
                                int pos = rv.getChildAdapterPosition(child);
                                if (pos != RecyclerView.NO_POSITION && pos != lastToggledPosition) {
                                    adapter.toggleSelectionByPosition(pos);
                                    lastToggledPosition = pos;
                                }
                            }
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        handler.removeCallbacks(longPressRunnable);
                        isSelecting = false;
                        longPressTriggered = false;
                        lastToggledPosition = -1;
                        break;
                    }
                }
                return false;
            }
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}
            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Do NOT clear selection on resume - this was causing the bar to disappear
        // Selection should only be cleared after share/delete or manual cancellation
    }

    public boolean isInSelectionMode() {
        return adapter != null && adapter.isSelectionMode();
    }
    
    public void exitSelectionMode() {
        if (adapter != null) {
            adapter.clearSelection();
        }
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
            boolean hasReadPermission = ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
            
            return hasReadPermission;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private boolean hasDeletePermission() {
        // For basic deletion, we just need write permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // On Android 11+, we can delete files in our app's directory without special permissions
            return true;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissions = new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
            multiplePermissionLauncher.launch(permissions);
        } else {
            singlePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    public void loadMedia() {
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
    
    /* ───────── Selection functionality ───────── */
    
    private void setupSelectionMenu() {
        if (bottomActionBar != null) {
            // Find the buttons in the included layout
            View cancelButton = bottomActionBar.findViewById(R.id.btnCancel);
            View shareButton = bottomActionBar.findViewById(R.id.btnShare);
            View deleteButton = bottomActionBar.findViewById(R.id.btnDelete);
            
            if (cancelButton != null) {
                cancelButton.setOnClickListener(v -> {
                    // Exit selection mode
                    adapter.clearSelection();
                });
            }
            
            if (shareButton != null) {
                shareButton.setOnClickListener(v -> {
                    shareSelectedImages();
                });
            }
            
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    deleteSelectedImages();
                });
            }
        }
    }
    
    private void showSelectionMenu() {
        if (bottomActionBar != null) {
            // Cancel any ongoing animations
            bottomActionBar.animate().cancel();
            
            bottomActionBar.setVisibility(View.VISIBLE);
            bottomActionBar.setAlpha(0f);
            bottomActionBar.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setListener(null) // Remove any previous listeners
                    .start();
            
            android.util.Log.d("PhotosFragment", "Selection menu shown");
        }
    }
    
    private void hideSelectionMenu() {
        if (bottomActionBar != null) {
            // Cancel any ongoing animations
            bottomActionBar.animate().cancel();
            
            bottomActionBar.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (bottomActionBar != null) {
                                bottomActionBar.setVisibility(View.GONE);
                                android.util.Log.d("PhotosFragment", "Selection menu hidden");
                            }
                        }
                    })
                    .start();
        }
    }
    

    
    private void updateSelectionMenu(List<Uri> selectedItems) {
        // Update menu based on selection count
        if (tvSelectionCount != null) {
            tvSelectionCount.setText(selectedItems.size() + " selected");
        }
        
        // Ensure the menu stays visible when items are selected
        if (!selectedItems.isEmpty() && bottomActionBar != null && bottomActionBar.getVisibility() != View.VISIBLE) {
            showSelectionMenu();
        }
        
        // Keep the menu visible as long as there are selected items
        if (selectedItems.isEmpty() && bottomActionBar != null && bottomActionBar.getVisibility() == View.VISIBLE) {
            // Only hide if we're not in selection mode
            if (!adapter.isSelectionMode()) {
                hideSelectionMenu();
            }
        }

        // Floating action box logic with animation
        if (floatingActionBoxInclude != null && fabActionBox != null && fabDimOverlay != null) {
            if (selectedItems.size() == 1) {
                if (fabActionBox.getVisibility() != View.VISIBLE) {
                    fabActionBox.setVisibility(View.VISIBLE);
                    fabDimOverlay.setVisibility(View.VISIBLE);
                    // Entry animation
                    fabActionBox.setScaleX(0.8f);
                    fabActionBox.setScaleY(0.8f);
                    fabActionBox.setAlpha(0f);
                    fabActionBox.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
                    fabDimOverlay.setAlpha(0f);
                    fabDimOverlay.animate().alpha(1f).setDuration(200).start();
                }
                if (bottomActionBar != null) bottomActionBar.setVisibility(View.GONE);
            } else {
                if (fabActionBox.getVisibility() == View.VISIBLE) {
                    // Exit animation
                    fabActionBox.animate().scaleX(0.8f).scaleY(0.8f).alpha(0f).setDuration(150).withEndAction(() -> fabActionBox.setVisibility(View.GONE)).start();
                    fabDimOverlay.animate().alpha(0f).setDuration(150).withEndAction(() -> fabDimOverlay.setVisibility(View.GONE)).start();
                } else {
                    fabActionBox.setVisibility(View.GONE);
                    fabDimOverlay.setVisibility(View.GONE);
                }
                // Bottom bar fade in/out
                if (!selectedItems.isEmpty() && bottomActionBar != null) {
                    if (bottomActionBar.getVisibility() != View.VISIBLE) {
                        bottomActionBar.setAlpha(0f);
                        bottomActionBar.setVisibility(View.VISIBLE);
                        bottomActionBar.animate().alpha(1f).setDuration(200).start();
                    }
                } else if (selectedItems.isEmpty() && bottomActionBar != null && bottomActionBar.getVisibility() == View.VISIBLE) {
                    bottomActionBar.animate().alpha(0f).setDuration(150).withEndAction(() -> bottomActionBar.setVisibility(View.GONE)).start();
                }
            }
        }
        // Animate selection count
        if (tvSelectionCount != null) {
            int newCount = selectedItems.size();
            tvSelectionCount.setText(newCount + " selected");
            if (lastSelectionCount != newCount) {
                tvSelectionCount.setScaleX(1.15f);
                tvSelectionCount.setScaleY(1.15f);
                tvSelectionCount.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            }
        }
        // Snackbar for selection mode entry/clear
        if (!selectionSnackbarShown && selectedItems.size() > 0) {
            View root = getView();
            if (root != null) {
                Snackbar.make(root, "Selection mode enabled. Tap or drag to select more.", Snackbar.LENGTH_SHORT).show();
                selectionSnackbarShown = true;
            }
        }
        if (selectionSnackbarShown && selectedItems.isEmpty()) {
            View root = getView();
            if (root != null) {
                Snackbar.make(root, "Selection cleared.", Snackbar.LENGTH_SHORT).show();
            }
            selectionSnackbarShown = false;
        }
        // Haptic feedback on selection
        if (selectedItems.size() > lastSelectionCount) {
            try {
                Vibrator v = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(30);
                    }
                }
            } catch (Exception ignore) {}
        }
        lastSelectionCount = selectedItems.size();

        // Only hide/show filter bar based on selection mode
        if (filterContainer != null) {
            if (selectedItems.size() > 0) {
                filterContainer.setVisibility(View.GONE);
            } else {
                filterContainer.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private void shareSelectedImages() {
        List<Uri> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "No images selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.setType("image/*");
            
            // Add the URIs to the intent
            ArrayList<Uri> imageUris = new ArrayList<>(selectedItems);
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Shared Images");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Sharing " + selectedItems.size() + " image(s)");
            
            // Add read permission for the URIs
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Create chooser and start activity
            Intent chooser = Intent.createChooser(shareIntent, "Share Images");
            if (shareIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(chooser);
                // Clear selection after sharing
                adapter.clearSelection();
            } else {
                Toast.makeText(requireContext(), "No app available to share images", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error sharing images: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void deleteSelectedImages() {
        List<Uri> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "No images selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("PhotosFragment", "Attempting to delete " + selectedItems.size() + " images");
        
        // Check permissions first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            // Show permission request dialog
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Permission Required")
                    .setMessage("To delete images, this app needs 'Manage all files' permission.\n\n" +
                            "1. Tap 'Grant Permission'\n" +
                            "2. Find this app in the list\n" +
                            "3. Enable 'Allow management of all files'\n" +
                            "4. Return to this app and try deleting again")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            // Fallback to general storage settings
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Images")
                .setMessage("Are you sure you want to delete " + selectedItems.size() + " image(s)?\n\nImages will be moved to Recycle Bin.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    performDelete(selectedItems);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void performDelete(List<Uri> selectedItems) {
        Toast.makeText(requireContext(), "Moving images to Recycle Bin...", Toast.LENGTH_SHORT).show();
        
        // Check if we have delete permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                // Show dialog to guide user to settings
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Permission Required")
                        .setMessage("To delete images, this app needs 'Manage all files' permission.\n\n" +
                                "1. Tap 'Open Settings'\n" +
                                "2. Find this app in the list\n" +
                                "3. Enable 'Allow management of all files'\n" +
                                "4. Return to this app and try deleting again")
                        .setPositiveButton("Open Settings", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                // Fallback to general storage settings
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }
        
        // Run deletion on background thread
        new Thread(() -> {
            int successCount = 0;
            int totalCount = selectedItems.size();
            
            for (int i = 0; i < selectedItems.size(); i++) {
                Uri uri = selectedItems.get(i);
                android.util.Log.d("PhotosFragment", "Attempting to delete image " + (i + 1) + " of " + totalCount + ": " + uri);
                
                if (recycleBinManager.moveToRecycleBin(uri, false)) {
                    successCount++;
                    android.util.Log.d("PhotosFragment", "Successfully deleted image " + (i + 1));
                } else {
                    android.util.Log.e("PhotosFragment", "Failed to delete image " + (i + 1) + ": " + uri);
                }
                
                // Add delay between deletions to prevent conflicts
                if (i < selectedItems.size() - 1) {
                    try {
                        Thread.sleep(500); // 500ms delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            final int finalSuccessCount = successCount;
            final int finalTotalCount = totalCount;
            
            // Update UI on main thread
            requireActivity().runOnUiThread(() -> {
                if (finalSuccessCount > 0) {
                    if (finalSuccessCount == finalTotalCount) {
                        Toast.makeText(requireContext(), "All " + finalSuccessCount + " image(s) moved to Recycle Bin", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), finalSuccessCount + " of " + finalTotalCount + " image(s) moved to Recycle Bin", Toast.LENGTH_LONG).show();
                    }
                    adapter.clearSelection();
                    loadMedia(); // Refresh the gallery
                } else {
                    Toast.makeText(requireContext(), "Failed to delete any images. Check logs for details.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
    



    
    private List<Uri> getAllImageUris() {
        List<Uri> uris = new ArrayList<>();
        for (MediaGroupedItem item : groupedItems) {
            if (item.getType() == MediaGroupedItem.TYPE_MEDIA) {
                uris.add(item.getMedia().getUri());
            }
        }
        return uris;
    }




}