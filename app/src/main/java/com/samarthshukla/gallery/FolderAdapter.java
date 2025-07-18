package com.samarthshukla.gallery;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(ImageFolder folder);
    }

    private final Context context;
    private final List<ImageFolder> folders;
    private final OnFolderClickListener clickListener;

    public FolderAdapter(Context context, List<ImageFolder> folders, OnFolderClickListener listener) {
        this.context = context;
        this.folders = folders;
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ImageFolder folder = folders.get(position);

        holder.folderName.setText(folder.getFolderName());
        holder.itemCount.setText(folder.getImageCount() + " items");

        Glide.with(context)
                .load(folder.getFirstImageUri())
                .centerCrop()
                .into(holder.thumbnail);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onFolderClick(folder);
            }
        });
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView folderName, itemCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.folderImage);
            folderName = itemView.findViewById(R.id.folderName);
            itemCount = itemView.findViewById(R.id.imageCount);
        }
    }
}