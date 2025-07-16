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
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.List;

/** Single adapter – GRID & VIEWER. */
public class PhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /* ---------- modes ---------- */
    public enum Mode {
        GRID, VIEWER
    }

    /* ---------- view-type ids ---------- */
    public static final int TYPE_HEADER = MediaGroupedItem.TYPE_HEADER;
    public static final int TYPE_MEDIA = MediaGroupedItem.TYPE_MEDIA;

    /* ---------- members ---------- */
    private final Mode mode;
    private final Context context;
    private final List<MediaGroupedItem> grouped; // grid
    private final List<Uri> viewerUris; // viewer
    private final int fullW, fullH; // 3× screen
    
    // Selection functionality
    private final List<Uri> selectedItems = new ArrayList<>();
    private OnSelectionChangedListener selectionListener;
    private boolean isSelectionMode = false;

    /* ---------- factories ---------- */
    public static PhotoAdapter forGrid(Context ctx, List<MediaGroupedItem> items) {
        return new PhotoAdapter(ctx, Mode.GRID, items, null);
    }

    public static PhotoAdapter forViewer(Context ctx, List<Uri> uris) {
        return new PhotoAdapter(ctx, Mode.VIEWER, null, uris);
    }

    /* ---------- ctor (private) ---------- */
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

    /* ---------- basics ---------- */
    @Override
    public int getItemCount() {
        return mode == Mode.GRID ? grouped.size() : viewerUris.size();
    }

    @Override
    public int getItemViewType(int pos) {
        return mode == Mode.GRID ? grouped.get(pos).getType() : TYPE_MEDIA;
    }

    /* ---------- create holders ---------- */
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
        return new ViewerHolder(inf.inflate(R.layout.item_photo_viewer_page_enhanced, parent, false));
    }

    /* ---------- bind ---------- */
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

    /* ===== GRID ===== */
    private void bindGrid(GridHolder holder, MediaItem media, int pos) {
        Uri uri = media.getUri();

        // 700-px thumb into cell
        Glide.with(context)
                .load(uri)
                .override(700, 700)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.imageView);

        // Warm-up cache for instant viewer preview
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

        // Update selection state
        updateSelectionState(holder, uri);

        holder.imageView.setOnClickListener(v -> {
            if (isSelectionMode) {
                // Add visual feedback
                v.setScaleX(0.95f);
                v.setScaleY(0.95f);
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start();
                
                toggleSelection(uri, holder);
            } else {
                if (video) {
                    openVideo(uri);
                } else {
                    openViewer(pos, tn, holder.imageView);
                }
            }
        });

        holder.imageView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                enterSelectionMode();
                toggleSelection(uri, holder);
                return true;
            }
            return false;
        });
    }

    /* ===== VIEWER ===== */
    private void bindViewer(ViewerHolder vh, Uri uri) {

        // Configure PhotoView to allow vertical swipes to pass through
        vh.photoView.setMinimumScale(1.0f);
        vh.photoView.setMaximumScale(3.0f);
        vh.photoView.setMediumScale(1.5f);
        
        // Set up PhotoView to allow parent to handle certain gestures
        vh.photoView.setOnViewTapListener((view, x, y) -> {
            // Handle tap events if needed
        });

        // 1) 700px request (will be instant from cache)
        RequestBuilder<Drawable> thumb = Glide.with(context)
                .load(uri)
                .override(700, 700)
                .dontTransform()
                .diskCacheStrategy(DiskCacheStrategy.ALL);

        // 2) Full-res with cross-fade & scaling fix
        Glide.with(context)
                .load(uri)
                .override(fullW, fullH)
                .thumbnail(thumb)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable res,
                            @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super Drawable> trans) {

                        vh.photoView.setImageDrawable(res);
                        // Always reset to minimum scale and fit center
                        float minScale = vh.photoView.getMinimumScale();
                        vh.photoView.setScale(minScale, true); // reset any zoom
                        vh.photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }

                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable Drawable placeholder) {
                    }
                });
    }

    /* ---------- Selection functionality ---------- */
    
    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<Uri> selectedItems);
        void onSelectionModeChanged(boolean isSelectionMode);
    }
    
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }
    
    private void updateSelectionState(GridHolder holder, Uri uri) {
        if (selectedItems.contains(uri)) {
            holder.selectionOverlay.setVisibility(View.VISIBLE);
            holder.checkIcon.setVisibility(View.VISIBLE);

            // Stronger pop animation for selection
            holder.selectionOverlay.setAlpha(0f);
            holder.selectionOverlay.setScaleX(0.85f);
            holder.selectionOverlay.setScaleY(0.85f);
            holder.selectionOverlay.animate()
                    .alpha(1f)
                    .scaleX(1.08f).scaleY(1.08f)
                    .setDuration(100)
                    .withEndAction(() -> holder.selectionOverlay.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(80)
                            .start())
                    .start();

            holder.checkIcon.setScaleX(0.6f);
            holder.checkIcon.setScaleY(0.6f);
            holder.checkIcon.animate()
                    .scaleX(1.2f).scaleY(1.2f)
                    .setDuration(120)
                    .withEndAction(() -> holder.checkIcon.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(80)
                            .start())
                    .start();
        } else {
            // Animate out
            holder.selectionOverlay.animate()
                    .alpha(0f)
                    .scaleX(0.85f).scaleY(0.85f)
                    .setDuration(120)
                    .withEndAction(() -> holder.selectionOverlay.setVisibility(View.GONE))
                    .start();
            holder.checkIcon.animate()
                    .scaleX(0.6f).scaleY(0.6f)
                    .setDuration(120)
                    .withEndAction(() -> holder.checkIcon.setVisibility(View.GONE))
                    .start();
        }
    }
    
    public void selectItem(Uri uri) {
        if (!selectedItems.contains(uri)) {
            selectedItems.add(uri);
            notifyDataSetChanged();
        }
    }
    
    private void toggleSelection(Uri uri, GridHolder holder) {
        if (selectedItems.contains(uri)) {
            selectedItems.remove(uri);
        } else {
            selectedItems.add(uri);
        }
        updateSelectionState(holder, uri);
        
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItems);
        }
        
        // Only exit selection mode if the user has deselected all items AND we're already in selection mode
        // Add a longer delay to prevent accidental exit during rapid selection
        if (selectedItems.isEmpty() && isSelectionMode) {
            holder.itemView.postDelayed(() -> {
                if (selectedItems.isEmpty() && isSelectionMode) {
                    // Double-check that we're still in selection mode and no items are selected
                    if (selectedItems.isEmpty() && isSelectionMode) {
                        exitSelectionMode();
                    }
                }
            }, 300); // Increased delay to 300ms
        }
    }
    
    public void enterSelectionMode() {
        isSelectionMode = true;
        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(true);
        }
    }
    
    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedItems.clear();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItems);
            selectionListener.onSelectionModeChanged(false);
        }
        notifyDataSetChanged();
    }
    
    public List<Uri> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }
    
    public void clearSelection() {
        selectedItems.clear();
        isSelectionMode = false;
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItems);
            selectionListener.onSelectionModeChanged(false);
        }
        notifyDataSetChanged();
    }
    
    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void selectItemByPosition(int position) {
        if (mode != Mode.GRID || position < 0 || position >= grouped.size()) return;
        MediaGroupedItem item = grouped.get(position);
        if (item.getType() == TYPE_MEDIA) {
            Uri uri = item.getMedia().getUri();
            if (!selectedItems.contains(uri)) {
                selectedItems.add(uri);
                notifyItemChanged(position);
                if (selectionListener != null) selectionListener.onSelectionChanged(selectedItems);
            }
        }
    }

    public void toggleSelectionByPosition(int position) {
        if (mode != Mode.GRID || position < 0 || position >= grouped.size()) return;
        MediaGroupedItem item = grouped.get(position);
        if (item.getType() == TYPE_MEDIA) {
            Uri uri = item.getMedia().getUri();
            if (selectedItems.contains(uri)) {
                selectedItems.remove(uri);
            } else {
                selectedItems.add(uri);
            }
            notifyItemChanged(position);
            if (selectionListener != null) selectionListener.onSelectionChanged(selectedItems);
        }
    }

    /* ---------- helpers ---------- */

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

    /* ---------- span-helper ---------- */
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

    /* ---------- view-holders ---------- */
    static class HeaderHolder extends RecyclerView.ViewHolder {
        TextView headerText;

        HeaderHolder(@NonNull View v) {
            super(v);
            headerText = v.findViewById(R.id.headerText);
        }
    }

    static class GridHolder extends RecyclerView.ViewHolder {
        ImageView imageView, playIcon, checkIcon;
        View videoOverlay, selectionOverlay;

        GridHolder(@NonNull View v) {
            super(v);
            imageView = v.findViewById(R.id.imageView);
            playIcon = v.findViewById(R.id.playIcon);
            videoOverlay = v.findViewById(R.id.videoOverlay);
            checkIcon = v.findViewById(R.id.checkIcon);
            selectionOverlay = v.findViewById(R.id.selectionOverlay);
        }
    }

    static class ViewerHolder extends RecyclerView.ViewHolder {
        EnhancedPhotoView photoView;

        ViewerHolder(@NonNull View v) {
            super(v);
            photoView = v.findViewById(R.id.photoView);
        }
    }
}