package com.example.basehome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.ViewHolder> {

    private List<Address> addresses;
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
        this.addresses = new ArrayList<>(addresses);
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        notifyDataSetChanged();
    }

    public void updateList(List<Address> newAddresses) {
        final List<Address> oldList = this.addresses;
        final List<Address> newList = new ArrayList<>(newAddresses);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                String oldId = oldList.get(oldItemPosition).getId();
                String newId = newList.get(newItemPosition).getId();
                return Objects.equals(oldId, newId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(oldList.get(oldItemPosition), newList.get(newItemPosition));
            }
        });
        this.addresses = newList;
        diffResult.dispatchUpdatesTo(this);
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
        
        // Теперь в tvHouse ставим только номер, так как "ДОМ" уже есть в разметке
        holder.tvHouse.setText(address.getHouse());
        
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
