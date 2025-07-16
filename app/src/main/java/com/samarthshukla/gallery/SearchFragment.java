package com.samarthshukla.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SearchFragment extends Fragment {
    
    private RecyclerView recyclerView;
    private RecycleBinAdapter adapter;
    private RecycleBinManager recycleBinManager;
    private LinearLayout emptyStateLayout;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        
        recycleBinManager = new RecycleBinManager(requireContext());
        
        setupRecyclerView();
        setupRecycleBinCard(view);
        loadRecycleBinItems();
        
        return view;
    }
    
    private void setupRecyclerView() {
        adapter = new RecycleBinAdapter(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        adapter.setOnItemClickListener(new RecycleBinAdapter.OnItemClickListener() {
            @Override
            public void onRestoreClick(RecycleBinManager.DeletedItem item) {
                android.util.Log.d("SearchFragment", "Attempting to restore: " + item.getFileName());
                if (recycleBinManager.restoreFromRecycleBin(item)) {
                    Toast.makeText(requireContext(), "File restored", Toast.LENGTH_SHORT).show();
                    android.util.Log.d("SearchFragment", "Restore successful: " + item.getFileName());
                    loadRecycleBinItems();
                    
                    // Notify the main activity to refresh the gallery
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshGallery();
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to restore file", Toast.LENGTH_SHORT).show();
                    android.util.Log.e("SearchFragment", "Restore failed: " + item.getFileName());
                }
            }
            
            @Override
            public void onDeleteClick(RecycleBinManager.DeletedItem item) {
                android.util.Log.d("SearchFragment", "Attempting to permanently delete: " + item.getFileName());
                if (recycleBinManager.permanentlyDelete(item)) {
                    Toast.makeText(requireContext(), "File permanently deleted", Toast.LENGTH_SHORT).show();
                    android.util.Log.d("SearchFragment", "Permanent delete successful: " + item.getFileName());
                    loadRecycleBinItems();
                } else {
                    Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show();
                    android.util.Log.e("SearchFragment", "Permanent delete failed: " + item.getFileName());
                }
            }
        });
    }
    
    private void setupRecycleBinCard(View view) {
        View recycleBinCard = view.findViewById(R.id.recycleBinCard);
        if (recycleBinCard != null) {
            recycleBinCard.setOnClickListener(v -> {
                // Show recycle bin content
                showRecycleBinContent();
            });
        }
    }
    
    private void showRecycleBinContent() {
        // Hide the card and show the recycler view
        View recycleBinCard = getView().findViewById(R.id.recycleBinCard);
        if (recycleBinCard != null) {
            recycleBinCard.setVisibility(View.GONE);
        }
        
        recyclerView.setVisibility(View.VISIBLE);
        loadRecycleBinItems();
    }
    
    private void loadRecycleBinItems() {
        List<RecycleBinManager.DeletedItem> items = recycleBinManager.getDeletedItems();
        
        if (items.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            adapter.setItems(items);
        }
    }
}