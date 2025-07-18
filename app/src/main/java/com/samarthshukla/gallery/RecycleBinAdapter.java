package com.samarthshukla.gallery;

import android.content.Context;
import android.net.Uri;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RecycleBinAdapter extends RecyclerView.Adapter<RecycleBinAdapter.ViewHolder> {

    private final Context context;
    private List<RecycleBinManager.DeletedItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onRestoreClick(RecycleBinManager.DeletedItem item);
        void onDeleteClick(RecycleBinManager.DeletedItem item);
    }

    public RecycleBinAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<RecycleBinManager.DeletedItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_recycle_bin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecycleBinManager.DeletedItem item = items.get(position);
        
        // Load thumbnail
        File file = new File(item.getRecyclePath());
        if (file.exists()) {
            Glide.with(context)
                    .load(file)
                    .override(100, 100)
                    .centerCrop()
                    .into(holder.imageView);
        }
        
        // Set file info
        holder.tvFileName.setText(item.getFileName());
        holder.tvDeletedDate.setText("Deleted: " + DateFormat.format("MMM dd, yyyy HH:mm", new Date(item.getDeletedTime())));
        
        // Set file type icon
        if (item.isVideo()) {
            holder.fileTypeIcon.setImageResource(R.drawable.ic_play_circle);
        } else {
            holder.fileTypeIcon.setImageResource(R.drawable.ic_photos);
        }
        
        // Set click listeners
        holder.btnRestore.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRestoreClick(item);
            }
        });
        
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView, fileTypeIcon;
        TextView tvFileName, tvDeletedDate;
        View btnRestore, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            fileTypeIcon = itemView.findViewById(R.id.fileTypeIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvDeletedDate = itemView.findViewById(R.id.tvDeletedDate);
            btnRestore = itemView.findViewById(R.id.btnRestore);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
} 