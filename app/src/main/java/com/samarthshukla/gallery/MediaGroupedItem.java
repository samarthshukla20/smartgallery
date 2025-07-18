package com.samarthshukla.gallery;

public class MediaGroupedItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_MEDIA = 1;

    private final int type;
    private final String header;
    private final MediaItem media;

    public MediaGroupedItem(int type, String header) {
        this.type = type;
        this.header = header;
        this.media = null;
    }

    public MediaGroupedItem(int type, MediaItem media) {
        this.type = type;
        this.media = media;
        this.header = null;
    }

    public int getType() {
        return type;
    }

    public String getHeader() {
        return header;
    }

    public MediaItem getMedia() {
        return media;
    }
}