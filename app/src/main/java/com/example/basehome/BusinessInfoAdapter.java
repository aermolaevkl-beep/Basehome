package com.example.basehome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;

public class BusinessInfoAdapter extends RecyclerView.Adapter<BusinessInfoAdapter.ViewHolder> {

    private final List<BusinessInfoEntry> entries;
    private final OnDeleteClickListener deleteListener;
    private final OnEditClickListener editListener;
    private boolean isAdmin;
    private final String currentUserId;

    public interface OnDeleteClickListener {
        void onDeleteClick(BusinessInfoEntry entry);
    }

    public interface OnEditClickListener {
        void onEditClick(BusinessInfoEntry entry);
    }

    public BusinessInfoAdapter(List<BusinessInfoEntry> entries, boolean isAdmin, OnDeleteClickListener deleteListener, OnEditClickListener editListener) {
        this.entries = entries;
        this.isAdmin = isAdmin;
        this.deleteListener = deleteListener;
        this.editListener = editListener;
        this.currentUserId = FirebaseAuth.getInstance().getUid();
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_business_info, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BusinessInfoEntry entry = entries.get(position);
        holder.tvText.setText(entry.getText());
        
        // Разрешаем всем авторизованным пользователям редактировать и удалять отметки
        boolean isLogged = currentUserId != null;
        
        holder.btnDelete.setVisibility(isLogged ? View.VISIBLE : View.GONE);
        holder.btnEdit.setVisibility(isLogged ? View.VISIBLE : View.GONE);

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(entry);
        });
        
        holder.btnEdit.setOnClickListener(v -> {
            if (editListener != null) editListener.onEditClick(entry);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        ImageButton btnDelete, btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvInfoText);
            btnDelete = itemView.findViewById(R.id.btnDeleteInfo);
            btnEdit = itemView.findViewById(R.id.btnEditInfo);
        }
    }
}
