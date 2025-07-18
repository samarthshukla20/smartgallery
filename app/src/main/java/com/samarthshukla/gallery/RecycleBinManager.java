package com.samarthshukla.gallery;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RecycleBinManager {
    private static final String PREF_NAME = "recycle_bin";
    private static final String KEY_DELETED_ITEMS = "deleted_items";
    private static final String TAG = "RecycleBinManager";

    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;

    public RecycleBinManager(Context context) {
        this.context = context;
        if (context != null) {
            this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        } else {
            this.preferences = null;
        }
        this.gson = new Gson();
    }

    public static class DeletedItem {
        private final String originalPath;
        private final String recyclePath;
        private final long deletedTime;
        private final String fileName;
        private final boolean isVideo;

        public DeletedItem(String originalPath, String recyclePath, long deletedTime, String fileName, boolean isVideo) {
            this.originalPath = originalPath;
            this.recyclePath = recyclePath;
            this.deletedTime = deletedTime;
            this.fileName = fileName;
            this.isVideo = isVideo;
        }

        public String getOriginalPath() { return originalPath; }
        public String getRecyclePath() { return recyclePath; }
        public long getDeletedTime() { return deletedTime; }
        public String getFileName() { return fileName; }
        public boolean isVideo() { return isVideo; }
    }



    public boolean moveToRecycleBin(Uri uri, boolean isVideo) {
        try {
            Log.d(TAG, "Starting moveToRecycleBin for URI: " + uri);
            
            // Copy file to recycle bin first
            File recycleFile = copyToRecycleBin(uri);
            if (recycleFile == null) {
                Log.e(TAG, "Failed to copy file to recycle bin");
                return false;
            }
            
            Log.d(TAG, "File copied to recycle bin: " + recycleFile.getAbsolutePath());
            
            // Try multiple deletion methods
            boolean deleted = false;
            
            // Method 1: Try ContentResolver delete
            try {
                int deletedRows = context.getContentResolver().delete(uri, null, null);
                Log.d(TAG, "ContentResolver delete result: " + deletedRows + " rows deleted");
                if (deletedRows > 0) {
                    deleted = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "ContentResolver delete failed", e);
            }
            
            // Method 2: Try getting file path and deleting directly
            if (!deleted) {
                try {
                    String filePath = getPathFromUri(uri);
                    if (filePath != null) {
                        File file = new File(filePath);
                        if (file.exists() && file.delete()) {
                            Log.d(TAG, "Direct file delete successful: " + filePath);
                            deleted = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Direct file delete failed", e);
                }
            }
            
            // Method 3: Try MediaStore ID deletion
            if (!deleted) {
                try {
                    String mediaId = getMediaIdFromUri(uri);
                    if (mediaId != null) {
                        if (deleteFromMediaStoreById(mediaId, isVideo)) {
                            Log.d(TAG, "MediaStore ID delete successful");
                            deleted = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "MediaStore ID delete failed", e);
                }
            }
            
            // Method 4: Try direct file deletion using SAF
            if (!deleted) {
                try {
                    if (deleteFileDirectly(uri)) {
                        Log.d(TAG, "Direct URI delete successful");
                        deleted = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Direct URI delete failed", e);
                }
            }
            
            if (deleted) {
                // Save to recycle bin
                DeletedItem item = new DeletedItem(
                    uri.toString(),
                    recycleFile.getAbsolutePath(),
                    System.currentTimeMillis(),
                    recycleFile.getName(),
                    isVideo
                );
                addDeletedItem(item);
                
                // Refresh MediaStore
                refreshMediaStore();
                
                Log.d(TAG, "Successfully moved file to recycle bin");
                return true;
            } else {
                // If all deletion methods failed, remove copied file
                recycleFile.delete();
                Log.e(TAG, "All deletion methods failed for URI: " + uri);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error moving file to recycle bin", e);
            return false;
        }
    }

    private File copyToRecycleBin(Uri uri) {
        try {
            // Create recycle bin directory
            File recycleDir = new File(context.getExternalFilesDir(null), "recycle_bin");
            if (!recycleDir.exists()) {
                if (!recycleDir.mkdirs()) {
                    Log.e(TAG, "Failed to create recycle bin directory");
                    return null;
                }
            }

            // Get the original file name and extension
            String originalFileName = getFileNameFromUri(uri);
            String extension = ".jpg"; // default extension
            
            if (originalFileName != null) {
                int lastDot = originalFileName.lastIndexOf('.');
                if (lastDot > 0) {
                    extension = originalFileName.substring(lastDot);
                }
            }
            
            // Generate unique filename with proper extension
            String fileName = "recycled_" + System.currentTimeMillis() + extension;
            File recycleFile = new File(recycleDir, fileName);
            
            Log.d(TAG, "Copying file to: " + recycleFile.getAbsolutePath());
            
            // Copy the file
            try (java.io.InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(recycleFile)) {
                
                if (inputStream == null) {
                    Log.e(TAG, "Could not open input stream for URI: " + uri);
                    return null;
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                Log.d(TAG, "File copied successfully to: " + recycleFile.getAbsolutePath() + " (" + totalBytes + " bytes)");
                return recycleFile;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying file to recycle bin", e);
            return null;
        }
    }

    private String getFileNameFromUri(Uri uri) {
        try {
            // Try to get the file name from the URI
            String uriString = uri.toString();
            if (uriString.contains("/")) {
                String fileName = uriString.substring(uriString.lastIndexOf("/") + 1);
                if (fileName.contains("?")) {
                    fileName = fileName.substring(0, fileName.indexOf("?"));
                }
                return fileName;
            }
            
            // If we can't get it from URI, try to get it from MediaStore
            String[] projection = { android.provider.MediaStore.MediaColumns.DISPLAY_NAME };
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                    return cursor.getString(columnIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name from URI", e);
        }
        return null;
    }

    public boolean restoreFromRecycleBin(DeletedItem item) {
        try {
            Log.d(TAG, "Starting restore for item: " + item.getFileName());
            
            File recycleFile = new File(item.getRecyclePath());
            if (!recycleFile.exists()) {
                Log.e(TAG, "Recycle file does not exist: " + item.getRecyclePath());
                return false;
            }
            
            Log.d(TAG, "Recycle file exists: " + recycleFile.getAbsolutePath() + ", size: " + recycleFile.length());
            
            // Try to restore using MediaStore API
            boolean restored = restoreToMediaStore(recycleFile, item.isVideo());
            
            if (restored) {
                // Remove from recycle bin
                recycleFile.delete();
                removeDeletedItem(item);
                Log.d(TAG, "Successfully restored file: " + item.getFileName());
                
                // Force refresh MediaStore
                refreshMediaStore();
                return true;
            } else {
                Log.e(TAG, "Failed to restore file to MediaStore: " + item.getFileName());
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring file from recycle bin", e);
        }
        return false;
    }

    private boolean restoreToMediaStore(File sourceFile, boolean isVideo) {
        try {
            // Create MediaStore entry
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.getName());
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, isVideo ? "video/*" : "image/*");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/");
            
            Uri mediaUri;
            if (isVideo) {
                mediaUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else {
                mediaUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
            
            // Insert the file into MediaStore
            Uri insertedUri = context.getContentResolver().insert(mediaUri, values);
            if (insertedUri == null) {
                Log.e(TAG, "Failed to insert into MediaStore");
                return false;
            }
            
            Log.d(TAG, "Inserted into MediaStore: " + insertedUri);
            
            // Copy the file content to the MediaStore URI
            try (java.io.InputStream inputStream = new java.io.FileInputStream(sourceFile);
                 java.io.OutputStream outputStream = context.getContentResolver().openOutputStream(insertedUri)) {
                
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream for MediaStore URI");
                    return false;
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                Log.d(TAG, "Successfully copied " + totalBytes + " bytes to MediaStore");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring to MediaStore", e);
            return false;
        }
    }

    public boolean permanentlyDelete(DeletedItem item) {
        try {
            Log.d(TAG, "Starting permanent delete for: " + item.getFileName());
            
            // First, try to delete from MediaStore if the original file still exists
            String originalPath = item.getOriginalPath();
            if (originalPath != null && !originalPath.startsWith("content://")) {
                File originalFile = new File(originalPath);
                if (originalFile.exists()) {
                    Log.d(TAG, "Original file still exists, deleting from MediaStore");
                    // Try to delete using MediaStore
                    Uri mediaUri = item.isVideo() ? 
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI :
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    
                    String selection = android.provider.MediaStore.MediaColumns.DATA + "=?";
                    String[] selectionArgs = { originalPath };
                    
                    int deletedRows = context.getContentResolver().delete(mediaUri, selection, selectionArgs);
                    Log.d(TAG, "MediaStore delete result: " + deletedRows + " rows deleted");
                    
                    // Also try direct file deletion
                    if (originalFile.delete()) {
                        Log.d(TAG, "Successfully deleted original file directly");
                    }
                }
            }
            
            // Delete from recycle bin
            File recycleFile = new File(item.getRecyclePath());
            if (recycleFile.exists() && recycleFile.delete()) {
                removeDeletedItem(item);
                Log.d(TAG, "Successfully permanently deleted: " + item.getFileName());
                
                // Force refresh MediaStore
                refreshMediaStore();
                return true;
            } else {
                Log.e(TAG, "Failed to delete recycle file: " + item.getRecyclePath());
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error permanently deleting file", e);
        }
        return false;
    }

    public List<DeletedItem> getDeletedItems() {
        if (preferences == null) {
            return new ArrayList<>();
        }
        String json = preferences.getString(KEY_DELETED_ITEMS, "[]");
        Type type = new TypeToken<ArrayList<DeletedItem>>(){}.getType();
        return gson.fromJson(json, type);
    }

    private void addDeletedItem(DeletedItem item) {
        List<DeletedItem> items = getDeletedItems();
        items.add(item);
        saveDeletedItems(items);
    }

    private void removeDeletedItem(DeletedItem item) {
        List<DeletedItem> items = getDeletedItems();
        items.removeIf(i -> i.getRecyclePath().equals(item.getRecyclePath()));
        saveDeletedItems(items);
    }

    private void saveDeletedItems(List<DeletedItem> items) {
        if (preferences == null) return;
        String json = gson.toJson(items);
        preferences.edit().putString(KEY_DELETED_ITEMS, json).apply();
    }

    public void clearRecycleBin() {
        try {
            File recycleDir = new File(context.getExternalFilesDir(null), "recycle_bin");
            if (recycleDir.exists()) {
                File[] files = recycleDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            preferences.edit().remove(KEY_DELETED_ITEMS).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing recycle bin", e);
        }
    }

    private String getPathFromUri(Uri uri) {
        try {
            Log.d(TAG, "Getting path from URI: " + uri);
            
            if ("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                Log.d(TAG, "File URI path: " + path);
                return path;
            } else if ("content".equals(uri.getScheme())) {
                // Try to get the file path from content URI using MediaStore
                String[] projection = { android.provider.MediaStore.MediaColumns.DATA };
                try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA);
                        String path = cursor.getString(columnIndex);
                        Log.d(TAG, "MediaStore path: " + path);
                        return path;
                    }
                }
                
                // If MediaStore query fails, try to get the path from the URI itself
                String uriPath = uri.getPath();
                if (uriPath != null && uriPath.contains("/")) {
                    // Try to extract the actual file path from content URI
                    String[] parts = uriPath.split("/");
                    if (parts.length > 2) {
                        // Look for DCIM or Pictures in the path
                        for (int i = 0; i < parts.length - 1; i++) {
                            if ("DCIM".equals(parts[i]) || "Pictures".equals(parts[i])) {
                                StringBuilder pathBuilder = new StringBuilder("/");
                                for (int j = i; j < parts.length; j++) {
                                    pathBuilder.append(parts[j]);
                                    if (j < parts.length - 1) {
                                        pathBuilder.append("/");
                                    }
                                }
                                String reconstructedPath = pathBuilder.toString();
                                Log.d(TAG, "Reconstructed path: " + reconstructedPath);
                                return reconstructedPath;
                            }
                        }
                    }
                }
                
                Log.w(TAG, "Could not extract path from content URI: " + uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting path from URI: " + uri, e);
        }
        return null;
    }

    private String getMediaIdFromUri(Uri uri) {
        try {
            String uriString = uri.toString();
            // Extract ID from content://media/external/images/media/12345
            if (uriString.contains("/media/")) {
                String[] parts = uriString.split("/");
                if (parts.length > 0) {
                    return parts[parts.length - 1];
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting MediaStore ID from URI", e);
            return null;
        }
    }

    private boolean deleteFromMediaStoreById(String mediaId, boolean isVideo) {
        try {
            Uri mediaUri;
            if (isVideo) {
                mediaUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else {
                mediaUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
            
            String selection = android.provider.MediaStore.MediaColumns._ID + "=?";
            String[] selectionArgs = { mediaId };
            
            // First, try to get the file path before deleting
            String[] projection = { android.provider.MediaStore.MediaColumns.DATA };
            try (android.database.Cursor cursor = context.getContentResolver().query(mediaUri, projection, selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA);
                    String filePath = cursor.getString(columnIndex);
                    Log.d(TAG, "Found file path: " + filePath);
                }
            }
            
            // Delete from MediaStore
            int deletedRows = context.getContentResolver().delete(mediaUri, selection, selectionArgs);
            Log.d(TAG, "MediaStore delete result: " + deletedRows + " rows deleted for ID: " + mediaId);
            
            if (deletedRows > 0) {
                // Force refresh MediaStore to update gallery
                refreshMediaStore();
                return true;
            } else {
                // If no rows were deleted, try using the original URI
                Log.w(TAG, "No rows deleted by ID, trying alternative methods");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting from MediaStore by ID", e);
            return false;
        }
    }

    private boolean deleteFileDirectly(Uri uri) {
        try {
            // Try to get the file path and delete directly
            String path = getPathFromUri(uri);
            if (path != null) {
                File file = new File(path);
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "Successfully deleted file directly: " + path);
                    // Force refresh MediaStore
                    refreshMediaStore();
                    return true;
                }
            }
            
            // If direct file delete fails, try using ContentResolver
            int deletedRows = context.getContentResolver().delete(uri, null, null);
            Log.d(TAG, "ContentResolver delete result: " + deletedRows + " rows deleted");
            
            if (deletedRows > 0) {
                // Force refresh MediaStore
                refreshMediaStore();
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file directly", e);
            return false;
        }
    }

    private void saveToRecycleBin(Uri originalUri, File recycleFile, boolean isVideo) {
        try {
            String originalPath = getPathFromUri(originalUri);
            DeletedItem item = new DeletedItem(
                originalPath != null ? originalPath : originalUri.toString(),
                recycleFile.getAbsolutePath(),
                System.currentTimeMillis(),
                recycleFile.getName(),
                isVideo
            );
            addDeletedItem(item);
            Log.d(TAG, "Saved to recycle bin: " + originalUri);
            
            // Force refresh MediaStore
            refreshMediaStore();
        } catch (Exception e) {
            Log.e(TAG, "Error saving to recycle bin", e);
        }
    }

    private boolean verifyFileDeleted(Uri uri) {
        try {
            // Try to access the file - if it's deleted, this should fail
            try (java.io.InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    Log.d(TAG, "File successfully deleted from gallery: " + uri);
                    return true;
                } else {
                    Log.w(TAG, "File still exists in gallery: " + uri);
                    return false;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "File successfully deleted from gallery (exception on access): " + uri);
            return true;
        }
    }

    private void refreshMediaStore() {
        try {
            Log.d(TAG, "Refreshing MediaStore...");
            
            // Force MediaStore to refresh for both images and videos
            context.getContentResolver().notifyChange(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null);
            context.getContentResolver().notifyChange(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
            
            Log.d(TAG, "MediaStore refresh completed");
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing MediaStore", e);
        }
    }

    public boolean testRecycleBinFunctionality() {
        try {
            // Test if we can create the recycle bin directory
            File recycleDir = new File(context.getExternalFilesDir(null), "recycle_bin");
            if (!recycleDir.exists()) {
                if (!recycleDir.mkdirs()) {
                    Log.e(TAG, "Test failed: Cannot create recycle bin directory");
                    return false;
                }
            }
            
            // Test if we can write to the directory
            File testFile = new File(recycleDir, "test_" + System.currentTimeMillis() + ".txt");
            try (java.io.FileWriter writer = new java.io.FileWriter(testFile)) {
                writer.write("test");
            }
            
            if (testFile.exists()) {
                testFile.delete();
                Log.d(TAG, "Test passed: Recycle bin directory is writable");
                return true;
            } else {
                Log.e(TAG, "Test failed: Cannot write to recycle bin directory");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception", e);
            return false;
        }
    }

    public boolean hasDeletePermissions() {
        try {
            // Test if we can access MediaStore
            Uri testUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = { android.provider.MediaStore.MediaColumns._ID };
            try (android.database.Cursor cursor = context.getContentResolver().query(testUri, projection, null, null, null, null)) {
                if (cursor != null) {
                    Log.d(TAG, "Test passed: Can access MediaStore");
                    return true;
                } else {
                    Log.e(TAG, "Test failed: Cannot access MediaStore");
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception", e);
            return false;
        }
    }

    public boolean testFileCopying() {
        try {
            // Create a test file
            File testDir = new File(context.getExternalFilesDir(null), "test_copy");
            if (!testDir.exists()) {
                testDir.mkdirs();
            }
            
            File sourceFile = new File(testDir, "test_source.txt");
            try (java.io.FileWriter writer = new java.io.FileWriter(sourceFile)) {
                writer.write("test content");
            }
            
            File destFile = new File(testDir, "test_dest.txt");
            
            // Test copying
            try (java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            if (destFile.exists() && destFile.length() == sourceFile.length()) {
                // Clean up
                sourceFile.delete();
                destFile.delete();
                testDir.delete();
                Log.d(TAG, "Test passed: File copying works");
                return true;
            } else {
                Log.e(TAG, "Test failed: File copying failed");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception", e);
            return false;
        }
    }

    public boolean testRestoreFunctionality() {
        try {
            Log.d(TAG, "Testing restore functionality...");
            
            // Create a test file in recycle bin
            File recycleDir = new File(context.getExternalFilesDir(null), "recycle_bin");
            if (!recycleDir.exists()) {
                recycleDir.mkdirs();
            }
            
            File testFile = new File(recycleDir, "test_restore.jpg");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(testFile)) {
                // Write some test data
                byte[] testData = "test image data".getBytes();
                fos.write(testData);
            }
            
            Log.d(TAG, "Created test file: " + testFile.getAbsolutePath());
            
            // Try to restore it
            boolean restored = restoreToMediaStore(testFile, false);
            
            if (restored) {
                Log.d(TAG, "Test passed: Restore functionality works");
                return true;
            } else {
                Log.e(TAG, "Test failed: Restore functionality failed");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception", e);
            return false;
        }
    }

    public boolean testMediaStoreDeletion() {
        try {
            Log.d(TAG, "Testing MediaStore deletion...");
            
            // Create a test file in MediaStore
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "test_delete.jpg");
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/*");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/");
            
            Uri insertedUri = context.getContentResolver().insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
            if (insertedUri == null) {
                Log.e(TAG, "Failed to create test file in MediaStore");
                return false;
            }
            
            Log.d(TAG, "Created test file in MediaStore: " + insertedUri);
            
            // Try to delete it
            int deletedRows = context.getContentResolver().delete(insertedUri, null, null);
            
            if (deletedRows > 0) {
                Log.d(TAG, "Test passed: MediaStore deletion works");
                return true;
            } else {
                Log.e(TAG, "Test failed: MediaStore deletion failed");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception", e);
            return false;
        }
    }

    public boolean testSimpleRecycleBin() {
        try {
            Log.d(TAG, "Testing simple recycle bin functionality...");
            
            // Create a test file
            File testFile = new File(context.getExternalFilesDir(null), "test_simple.txt");
            try (java.io.FileWriter writer = new java.io.FileWriter(testFile)) {
                writer.write("test content");
            }
            
            if (!testFile.exists()) {
                Log.e(TAG, "Failed to create test file");
                return false;
            }
            
            // Create recycle bin directory
            File recycleDir = new File(context.getExternalFilesDir(null), "recycle_bin");
            if (!recycleDir.exists()) {
                if (!recycleDir.mkdirs()) {
                    Log.e(TAG, "Failed to create recycle bin directory");
                    return false;
                }
            }
            
            // Copy to recycle bin
            File recycleFile = new File(recycleDir, "test_simple_copy.txt");
            try (java.io.FileInputStream fis = new java.io.FileInputStream(testFile);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(recycleFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            if (recycleFile.exists()) {
                // Clean up
                testFile.delete();
                recycleFile.delete();
                Log.d(TAG, "Test passed: Simple recycle bin functionality works");
                return true;
            } else {
                Log.e(TAG, "Test failed: Could not copy to recycle bin");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception", e);
            return false;
        }
    }

    public boolean testCompleteDeleteProcess() {
        try {
            Log.d(TAG, "=== TESTING COMPLETE DELETE PROCESS ===");
            
            // Create a test file in DCIM
            File testDir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "Camera");
            if (!testDir.exists()) {
                testDir.mkdirs();
            }
            
            File testFile = new File(testDir, "test_delete_" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileWriter writer = new java.io.FileWriter(testFile)) {
                writer.write("test image content");
            }
            
            Log.d(TAG, "Created test file: " + testFile.getAbsolutePath());
            
            // Create a MediaStore entry for the test file
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, testFile.getName());
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/");
            
            Uri insertedUri = context.getContentResolver().insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
            if (insertedUri == null) {
                Log.e(TAG, "Failed to create MediaStore entry for test file");
                testFile.delete();
                return false;
            }
            
            Log.d(TAG, "Created MediaStore entry: " + insertedUri);
            
            // Wait a moment for MediaStore to update
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Test the delete process
            boolean deleteResult = moveToRecycleBin(insertedUri, false);
            
            Log.d(TAG, "Delete test result: " + (deleteResult ? "PASSED" : "FAILED"));
            
            // Clean up test file if it still exists
            if (testFile.exists()) {
                testFile.delete();
            }
            
            return deleteResult;
        } catch (Exception e) {
            Log.e(TAG, "Complete delete process test failed", e);
            return false;
        }
    }

    public boolean testActualDelete() {
        try {
            Log.d(TAG, "=== TESTING ACTUAL DELETE FUNCTIONALITY ===");
            
            // Get a real image from the gallery
            Uri testUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = { android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DATA };
            String sortOrder = android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC";
            
            try (android.database.Cursor cursor = context.getContentResolver().query(testUri, projection, null, null, sortOrder)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID);
                    int dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA);
                    
                    long id = cursor.getLong(idColumn);
                    String data = cursor.getString(dataColumn);
                    
                    Uri imageUri = android.content.ContentUris.withAppendedId(testUri, id);
                    Log.d(TAG, "Testing with real image: " + imageUri);
                    Log.d(TAG, "Image path: " + data);
                    
                    // Test the delete process
                    boolean deleteResult = moveToRecycleBin(imageUri, false);
                    
                    Log.d(TAG, "Actual delete test result: " + (deleteResult ? "PASSED" : "FAILED"));
                    return deleteResult;
                } else {
                    Log.e(TAG, "No images found in gallery for testing");
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Actual delete test failed", e);
            return false;
        }
    }
} 