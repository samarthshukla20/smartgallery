package com.samarthshukla.gallery;

import android.net.Uri;

public class ImageFolder {

    private final String folderName;
    private final Uri firstImageUri;
    private final int imageCount;

    public ImageFolder(String folderName, Uri firstImageUri, int imageCount) {
        this.folderName = folderName;
        this.firstImageUri = firstImageUri;
        this.imageCount = imageCount;
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
}
