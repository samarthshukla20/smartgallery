package com.samarthshukla.gallery;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
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
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.jsibbold.zoomage.ZoomageView;

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public enum Mode {
        GRID, VIEWER
    }

    public static final int TYPE_HEADER = MediaGroupedItem.TYPE_HEADER;
    public static final int TYPE_MEDIA = MediaGroupedItem.TYPE_MEDIA;

    private final Mode mode;
    private final Context context;
    private final List<MediaGroupedItem> grouped;
    private final List<Uri> viewerUris;
    private final int fullW, fullH;

    public static PhotoAdapter forGrid(Context ctx, List<MediaGroupedItem> items) {
        return new PhotoAdapter(ctx, Mode.GRID, items, null);
    }

    public static PhotoAdapter forViewer(Context ctx, List<Uri> uris) {
        return new PhotoAdapter(ctx, Mode.VIEWER, null, uris);
    }

    private PhotoAdapter(Context ctx, Mode mode,
                         List<MediaGroupedItem> gridList,
                         List<Uri> viewerList) {

        this.context = ctx;
        this.mode = mode;
        this.grouped = gridList;
        this.viewerUris = viewerList;

        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        fullW = dm.widthPixels * 2;
        fullH = dm.heightPixels * 2;
    }

    @Override
    public int getItemCount() {
        return mode == Mode.GRID ? grouped.size() : viewerUris.size();
    }

    @Override
    public int getItemViewType(int pos) {
        return mode == Mode.GRID ? grouped.get(pos).getType() : TYPE_MEDIA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        LayoutInflater inf = LayoutInflater.from(context);

        if (mode == Mode.GRID) {
            return (viewType == TYPE_HEADER)
                    ? new HeaderHolder(inf.inflate(R.layout.item_header, parent, false))
                    : new GridHolder(inf.inflate(R.layout.item_photo, parent, false));
        }
        return new ViewerHolder(inf.inflate(R.layout.item_photo_viewer_page, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        if (mode == Mode.GRID) {
            MediaGroupedItem item = grouped.get(pos);
            if (item.getType() == TYPE_HEADER) {
                ((HeaderHolder) h).headerText.setText(item.getHeader());
            } else {
                bindGrid((GridHolder) h, item.getMedia(), pos);
            }
        } else {
            bindViewer((ViewerHolder) h, viewerUris.get(pos));
        }
    }

    private void bindGrid(GridHolder holder, MediaItem media, int pos) {
        Uri uri = media.getUri();

        Glide.with(context)
                .load(uri)
                .override(700, 700)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.imageView);

        Glide.with(context)
                .load(uri)
                .override(700, 700)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload();

        boolean video = media.isVideo();
        holder.playIcon.setVisibility(video ? View.VISIBLE : View.GONE);
        holder.videoOverlay.setVisibility(video ? View.VISIBLE : View.GONE);

        String tn = "media_" + pos;
        ViewCompat.setTransitionName(holder.imageView, tn);

        holder.imageView.setOnClickListener(v -> {
            if (video) {
                openVideo(uri);
            } else {
                openViewer(pos, tn, holder.imageView);
            }
        });
    }

    private void bindViewer(ViewerHolder vh, Uri uri) {
        RequestBuilder<Drawable> thumb = Glide.with(context)
                .load(uri)
                .override(700, 700)
                .dontTransform()
                .diskCacheStrategy(DiskCacheStrategy.ALL);

        Glide.with(context)
                .load(uri)
                .override(fullW, fullH)
                .thumbnail(thumb)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable res,
                                                @androidx.annotation.Nullable Transition<? super Drawable> trans) {

                        vh.zoomageView.setImageDrawable(res);
                        vh.zoomageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }

                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable Drawable placeholder) {
                    }
                });
    }

    private void openVideo(Uri uri) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
        String choice = p.getString("video_player_choice", null);

        try {
            Intent i;
            if (choice == null) {
                i = new Intent(context, VideoChoiceDialogActivity.class);
                i.putExtra("video_uri", uri.toString());
            } else if ("in_app".equals(choice)) {
                i = new Intent(context, VideoPlayerActivity.class);
                i.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "video/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            context.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(context, "Error opening video: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openViewer(int adapterPos, String transitionName, ImageView shared) {
        ArrayList<Uri> imgs = new ArrayList<>();
        int clickIdx = -1;

        for (int i = 0; i < grouped.size(); i++) {
            MediaGroupedItem g = grouped.get(i);
            if (g.getType() == TYPE_MEDIA && !g.getMedia().isVideo()) {
                if (i == adapterPos)
                    clickIdx = imgs.size();
                imgs.add(g.getMedia().getUri());
            }
        }

        Intent it = new Intent(context, PhotoViewActivity.class);
        it.putParcelableArrayListExtra(PhotoViewActivity.EXTRA_IMAGE_URIS, imgs);
        it.putExtra(PhotoViewActivity.EXTRA_START_POSITION, clickIdx);
        it.putExtra("transition_name", transitionName);

        ActivityOptions opts = ActivityOptions.makeSceneTransitionAnimation(
                (Activity) context, shared, transitionName);
        context.startActivity(it, opts.toBundle());
    }

    public GridLayoutManager.SpanSizeLookup spanLookup() {
        if (mode != Mode.GRID)
            return null;
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int pos) {
                return getItemViewType(pos) == TYPE_HEADER ? 3 : 1;
            }
        };
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView headerText;

        HeaderHolder(@NonNull View v) {
            super(v);
            headerText = v.findViewById(R.id.headerText);
        }
    }

    static class GridHolder extends RecyclerView.ViewHolder {
        ImageView imageView, playIcon;
        View videoOverlay;

        GridHolder(@NonNull View v) {
            super(v);
            imageView = v.findViewById(R.id.imageView);
            playIcon = v.findViewById(R.id.playIcon);
            videoOverlay = v.findViewById(R.id.videoOverlay);
        }
    }

    static class ViewerHolder extends RecyclerView.ViewHolder {
        ZoomageView zoomageView;

        ViewerHolder(@NonNull View v) {
            super(v);
            zoomageView = v.findViewById(R.id.zoomImageView);
        }
    }
}