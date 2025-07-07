package com.samarthshukla.gallery;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PhotoGridAdapter extends RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder> {

    private final Context context;
    private final List<MediaItem> mediaItems;

    public PhotoGridAdapter(Context context, List<MediaItem> mediaItems) {
        this.context = context;
        this.mediaItems = mediaItems;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        Uri uri = item.getUri();

        Glide.with(context)
                .load(uri)
                .centerCrop()
                .into(holder.imageView);

        holder.playIcon.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        holder.videoOverlay.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);

        String transitionName = "media_" + position;
        ViewCompat.setTransitionName(holder.imageView, transitionName);

        holder.imageView.setOnClickListener(v -> {
            // Launch video or image logic (same as before)
            Intent intent = new Intent(context, item.isVideo() ? VideoPlayerActivity.class : PhotoViewActivity.class);
            intent.putExtra("video_uri", uri.toString());
            intent.setData(uri);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView, playIcon;
        View videoOverlay;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            playIcon = itemView.findViewById(R.id.playIcon);
            videoOverlay = itemView.findViewById(R.id.videoOverlay);
        }
    }
}