package com.samarthshukla.gallery;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainAlbumAdapter extends RecyclerView.Adapter<MainAlbumAdapter.AlbumSectionViewHolder> {

    private final Context context;
    private final List<Object> items;
    private final FolderAdapter.OnFolderClickListener listener;

    public MainAlbumAdapter(Context context, List<Object> items, FolderAdapter.OnFolderClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlbumSectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_album_section, parent, false);
        return new AlbumSectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumSectionViewHolder holder, int position) {
        List<ImageFolder> folderList = (List<ImageFolder>) items.get(position);

        FolderAdapter adapter = new FolderAdapter(context, folderList, listener);
        holder.albumRecyclerView.setLayoutManager(
                new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        );
        holder.albumRecyclerView.setAdapter(adapter);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AlbumSectionViewHolder extends RecyclerView.ViewHolder {
        RecyclerView albumRecyclerView;

        AlbumSectionViewHolder(@NonNull View itemView) {
            super(itemView);
            albumRecyclerView = itemView.findViewById(R.id.horizontalAlbumList);
        }
    }
}