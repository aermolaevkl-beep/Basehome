package com.example.basehome;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final List<TaskItem> tasks = new ArrayList<>();
    private Map<String, ArrayList<String>> addressHistoryMap = new HashMap<>();

    public void updateTasks(List<TaskItem> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    public void setAddressHistoryMap(Map<String, ArrayList<String>> map) {
        this.addressHistoryMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskItem item = tasks.get(position);
        Context context = holder.itemView.getContext();

        holder.tvType.setText(item.getType());
        holder.tvDateTime.setText(item.getDateTime());
        holder.tvAddress.setText(item.getAddress());
        holder.tvClientName.setText(item.getClientName());
        
        String accountText = "Л/с: " + item.getAccountNumber();
        holder.tvAccount.setText(accountText);
        
        // Copy account number on click
        holder.tvAccount.setOnClickListener(v -> {
            String acc = item.getAccountNumber();
            if (acc != null && !acc.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Лицевой счет", acc);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Лицевой счет скопирован: " + acc, Toast.LENGTH_SHORT).show();
                }
            }
        });

        holder.tvTariffTech.setText(item.getTariff() + " | " + item.getTechnology());

        // Phone call and copy
        String phone = item.getClientPhone();
        if (phone != null && !phone.trim().isEmpty()) {
            holder.btnCall.setVisibility(View.VISIBLE);
            holder.btnCall.setOnClickListener(v -> {
                String dialPhone = formatPhoneNumber(phone);

                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Номер телефона", dialPhone);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                }

                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialPhone));
                context.startActivity(intent);
                Toast.makeText(context, "Номер скопирован и вызов: " + dialPhone, Toast.LENGTH_SHORT).show();
            });
        } else {
            holder.btnCall.setVisibility(View.GONE);
        }

        // Address History
        String rawAddress = item.getAddress();
        String fullKey = GoogleAddress.parseFullAddressKey(rawAddress);
        String houseKey = GoogleAddress.parseHouseKey(rawAddress);

        ArrayList<String> pastVisits = addressHistoryMap.get(fullKey);
        boolean isApartmentSpecific = pastVisits != null && !pastVisits.isEmpty();

        if (!isApartmentSpecific && houseKey != null && !houseKey.isEmpty()) {
            pastVisits = addressHistoryMap.get(houseKey);
        }

        if (pastVisits != null && !pastVisits.isEmpty()) {
            holder.btnHistory.setVisibility(View.VISIBLE);
            holder.btnHistory.setText("История выездов (" + pastVisits.size() + ")");
            final ArrayList<String> visitsToPass = pastVisits;
            final boolean isAptMatch = isApartmentSpecific;
            holder.btnHistory.setOnClickListener(v -> {
                Intent intent = new Intent(context, GoogleAddressDetailActivity.class);
                GoogleAddress ga = new GoogleAddress(rawAddress);
                intent.putExtra(GoogleAddressDetailActivity.EXTRA_ADDRESS, ga.getStreetHouse());
                if (isAptMatch) {
                    String apt = ga.getApartment();
                    if (apt != null && !apt.isEmpty()) {
                        intent.putExtra(GoogleAddressDetailActivity.EXTRA_APARTMENT, apt);
                    }
                }
                intent.putStringArrayListExtra(GoogleAddressDetailActivity.EXTRA_VISITS, visitsToPass);
                context.startActivity(intent);
            });
        } else {
            holder.btnHistory.setVisibility(View.GONE);
        }
    }

    private String formatPhoneNumber(String phone) {
        if (phone == null) return "";
        String clean = phone.trim().replaceAll("[^0-9+]", "");
        if (!clean.startsWith("+") && (clean.startsWith("375") || clean.startsWith("7") || clean.startsWith("380"))) {
            return "+" + clean;
        }
        return clean;
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvDateTime, tvAddress, tvClientName, tvAccount, tvTariffTech;
        MaterialButton btnCall, btnHistory;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvTaskType);
            tvDateTime = itemView.findViewById(R.id.tvTaskDateTime);
            tvAddress = itemView.findViewById(R.id.tvTaskAddress);
            tvClientName = itemView.findViewById(R.id.tvTaskClientName);
            tvAccount = itemView.findViewById(R.id.tvTaskAccount);
            tvTariffTech = itemView.findViewById(R.id.tvTaskTariffTech);
            btnCall = itemView.findViewById(R.id.btnTaskCall);
            btnHistory = itemView.findViewById(R.id.btnTaskHistory);
        }
    }
}
