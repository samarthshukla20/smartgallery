package com.samarthshukla.gallery;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.format.DateFormat;

import java.io.File;
import java.util.Date;

public class MediaInfo {
    private final Uri uri;
    private final long dateTaken;
    private final long fileSize;
    private final String fileName;
    private final String mimeType;

    public MediaInfo(Uri uri, long dateTaken, long fileSize, String fileName, String mimeType) {
        this.uri = uri;
        this.dateTaken = dateTaken;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public Uri getUri() {
        return uri;
    }

    public long getDateTaken() {
        return dateTaken;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFormattedDate() {
        return DateFormat.format("MMM dd, yyyy", new Date(dateTaken)).toString();
    }

    public String getFormattedTime() {
        return DateFormat.format("HH:mm", new Date(dateTaken)).toString();
    }

    public String getFormattedSize() {
        return formatFileSize(fileSize);
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public static MediaInfo fromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return new MediaInfo(uri, System.currentTimeMillis(), 0, "Unknown", "image/*");
        }
        
        try {
            String[] projection = {
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.Images.Media.DATE_TAKEN
            };

            android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    String fileName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
                    long fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE));
                    String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE));
                    long dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN));
                    
                    cursor.close();
                    return new MediaInfo(uri, dateTaken, fileSize, fileName, mimeType);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (cursor != null) cursor.close();
                }
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Fallback to file-based info
        try {
            if (uri.getPath() != null) {
                File file = new File(uri.getPath());
                if (file.exists()) {
                    return new MediaInfo(uri, System.currentTimeMillis(), file.length(), file.getName(), "image/*");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return new MediaInfo(uri, System.currentTimeMillis(), 0, "Unknown", "image/*");
    }
} 