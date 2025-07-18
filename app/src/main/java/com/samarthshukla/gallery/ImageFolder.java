package com.samarthshukla.gallery;

import android.net.Uri;

public class ImageFolder {

    private final String folderName;
    private final Uri firstImageUri;
    private final int imageCount;
    private final String bucketId; // <- add this

    public ImageFolder(String folderName, Uri firstImageUri, int imageCount, String bucketId) {
        this.folderName = folderName;
        this.firstImageUri = firstImageUri;
        this.imageCount = imageCount;
        this.bucketId = bucketId;
    }

    public String getFolderName() {
        return folderName;
    }

    public Uri getFirstImageUri() {
        return firstImageUri;
    }

    public int getImageCount() {
        return imageCount;
    }

    public String getBucketId() {
        return bucketId;
    }
}