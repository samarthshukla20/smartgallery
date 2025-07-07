package com.samarthshukla.gallery;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private final List<MediaGroupedItem> groupedItems;

    public static final int TYPE_HEADER = MediaGroupedItem.TYPE_HEADER;
    public static final int TYPE_MEDIA = MediaGroupedItem.TYPE_MEDIA;

    public PhotoAdapter(Context context, List<MediaGroupedItem> groupedItems) {
        this.context = context;
        this.groupedItems = groupedItems;
    }

    @Override
    public int getItemViewType(int position) {
        return groupedItems.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return groupedItems.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
            return new PhotoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MediaGroupedItem item = groupedItems.get(position);

        if (item.getType() == TYPE_HEADER) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.headerText.setText(item.getHeader());
        } else {
            MediaItem media = item.getMedia();
            PhotoViewHolder photoHolder = (PhotoViewHolder) holder;

            Uri uri = media.getUri();
            boolean isVideo = media.isVideo();

            Glide.with(context)
                    .load(uri)
                    .centerCrop()
                    .into(photoHolder.imageView);

            photoHolder.playIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);
            photoHolder.videoOverlay.setVisibility(isVideo ? View.VISIBLE : View.GONE);

            String transitionName = "media_" + position;
            ViewCompat.setTransitionName(photoHolder.imageView, transitionName);

            photoHolder.imageView.setOnClickListener(v -> {
                if (isVideo) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    String choice = prefs.getString("video_player_choice", null);

                    try {
                        if (choice == null) {
                            Intent choiceIntent = new Intent(context, VideoChoiceDialogActivity.class);
                            choiceIntent.putExtra("video_uri", uri.toString());
                            context.startActivity(choiceIntent);
                        } else if ("in_app".equals(choice)) {
                            Intent intent = new Intent(context, VideoPlayerActivity.class);
                            intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(intent);
                        } else {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(uri, "video/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(intent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(context, "Error opening video: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    // Open image viewer
                    Intent intent = new Intent(context, PhotoViewActivity.class);
                    ArrayList<Uri> imageUris = new ArrayList<>();
                    for (MediaGroupedItem grouped : groupedItems) {
                        if (grouped.getType() == TYPE_MEDIA && !grouped.getMedia().isVideo()) {
                            imageUris.add(grouped.getMedia().getUri());
                        }
                    }

                    int imagePosition = 0;
                    for (int i = 0; i < position; i++) {
                        MediaGroupedItem grouped = groupedItems.get(i);
                        if (grouped.getType() == TYPE_MEDIA && !grouped.getMedia().isVideo()) {
                            imagePosition++;
                        }
                    }

                    intent.putParcelableArrayListExtra(PhotoViewActivity.EXTRA_IMAGE_URIS, imageUris);
                    intent.putExtra(PhotoViewActivity.EXTRA_START_POSITION, imagePosition);
                    intent.putExtra("transition_name", transitionName);

                    ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                            (Activity) context, photoHolder.imageView, transitionName);
                    context.startActivity(intent, options.toBundle());
                }
            });
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerText);
        }
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageView playIcon;
        View videoOverlay;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            playIcon = itemView.findViewById(R.id.playIcon);
            videoOverlay = itemView.findViewById(R.id.videoOverlay);
        }
    }

    // Span size logic for GridLayoutManager
    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_HEADER ? 3 : 1;
            }
        };
    }
}