package com.example.basehome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.ViewHolder> {

    private final List<Address> addresses;
    private final OnAddressClickListener listener;
    private final OnDeleteClickListener deleteListener;
    private boolean isAdmin = false;

    public interface OnAddressClickListener {
        void onAddressClick(Address address);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Address address);
    }

    public AddressAdapter(List<Address> addresses, OnAddressClickListener listener, OnDeleteClickListener deleteListener) {
        this.addresses = addresses;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Address address = addresses.get(position);
        holder.tvCity.setText(address.getCity() != null ? address.getCity() : "Могилев");
        holder.tvStreet.setText(address.getStreet());
        holder.tvHouse.setText(holder.itemView.getContext().getString(R.string.house_format, address.getHouse()));
        
        holder.tvEntrance.setVisibility(View.GONE);
        holder.btnDelete.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAddressClick(address);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(address);
        });
    }

    @Override
    public int getItemCount() {
        return addresses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStreet, tvHouse, tvEntrance, tvCity;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCity = itemView.findViewById(R.id.tvItemCity);
            tvStreet = itemView.findViewById(R.id.tvStreet);
            tvHouse = itemView.findViewById(R.id.tvHouse);
            tvEntrance = itemView.findViewById(R.id.tvEntrance);
            btnDelete = itemView.findViewById(R.id.btnDeleteAddressItem);
        }
    }
}
