package com.samarthshukla.gallery;

import android.net.Uri;

public class MediaItem {
    private final Uri uri;
    private final boolean isVideo;
    private final long dateTaken;
    private final long dateAdded; // ✅ add this

    public MediaItem(Uri uri, boolean isVideo, long dateTaken, long dateAdded) {
        this.uri = uri;
        this.isVideo = isVideo;
        this.dateTaken = dateTaken;
        this.dateAdded = dateAdded;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public long getDateTaken() {
        return dateTaken;
    }

    public long getDateAdded() {
        return dateAdded;
    }
}