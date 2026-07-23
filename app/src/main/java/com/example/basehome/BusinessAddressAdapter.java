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

public class BusinessAddressAdapter extends RecyclerView.Adapter<BusinessAddressAdapter.ViewHolder> {

    private List<BusinessAddress> addresses;
    private final OnBusinessAddressClickListener listener;
    private final OnDeleteClickListener deleteListener;
    private boolean isAdmin = false;

    public interface OnBusinessAddressClickListener {
        void onBusinessAddressClick(BusinessAddress address);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(BusinessAddress address);
    }

    public BusinessAddressAdapter(List<BusinessAddress> addresses, OnBusinessAddressClickListener listener, OnDeleteClickListener deleteListener) {
        this.addresses = new ArrayList<>(addresses);
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        notifyDataSetChanged();
    }

    public void updateList(List<BusinessAddress> newAddresses) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return addresses.size();
            }

            @Override
            public int getNewListSize() {
                return newAddresses.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return addresses.get(oldItemPosition).getId().equals(newAddresses.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return addresses.get(oldItemPosition).equals(newAddresses.get(newItemPosition));
            }
        });
        this.addresses = new ArrayList<>(newAddresses);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_business_address, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BusinessAddress address = addresses.get(position);
        holder.tvCity.setText(address.getCity() != null ? address.getCity() : "Могилев");
        holder.tvStreet.setText(address.getStreet());
        
        // В карточке теперь есть бейдж "ДОМ", поэтому ставим только номер
        holder.tvHouse.setText(address.getHouse());
        
        holder.tvCenterName.setText(address.getCenterName());
        holder.tvCenterName.setVisibility(address.getCenterName() != null && !address.getCenterName().isEmpty() ? View.VISIBLE : View.GONE);

        holder.btnDelete.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBusinessAddressClick(address);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(address);
            }
        });
    }

    @Override
    public int getItemCount() {
        return addresses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStreet, tvHouse, tvCenterName, tvCity;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCity = itemView.findViewById(R.id.tvBusItemCity);
            tvStreet = itemView.findViewById(R.id.tvBusinessStreet);
            tvHouse = itemView.findViewById(R.id.tvBusinessHouse);
            tvCenterName = itemView.findViewById(R.id.tvCenterName);
            btnDelete = itemView.findViewById(R.id.btnDeleteBusinessItem);
        }
    }
}
