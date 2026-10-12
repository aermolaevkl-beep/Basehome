package com.example.basehome;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
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
    private Map<String, PhysicalAddressContext> physicalAddressContextMap = new HashMap<>();
    private final OnCreateAddressListener onCreateAddress;

    public interface OnCreateAddressListener {
        void onCreateAddress(String streetHouse);
    }

    public static class PhysicalAddressContext {
        public final String addressId;
        public final String addressLabel;
        public final String details;

        public PhysicalAddressContext(String addressId, String addressLabel, String details) {
            this.addressId = addressId;
            this.addressLabel = addressLabel;
            this.details = details;
        }
    }

    public TaskAdapter(OnCreateAddressListener onCreateAddress) {
        this.onCreateAddress = onCreateAddress;
    }

    public void setPhysicalAddressContextMap(Map<String, PhysicalAddressContext> map) {
        physicalAddressContextMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged();
    }

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

        String physicalAddressKey = GoogleAddress.parseHouseKey(item.getAddress());
        PhysicalAddressContext physicalContext = physicalAddressContextMap.get(physicalAddressKey);
        holder.cardPhysicalAddress.setVisibility(View.VISIBLE);
        if (physicalContext == null) {
            int accent = androidx.core.content.ContextCompat.getColor(context, R.color.task_address_missing_accent);
            holder.cardPhysicalAddress.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.task_card_neutral_bg));
            holder.cardPhysicalAddress.setStrokeColor(androidx.core.content.ContextCompat.getColor(context, R.color.task_card_neutral_stroke));
            holder.cardPhysicalAddress.setStrokeWidth(1);
            holder.ivPhysicalAddressStatus.setImageResource(android.R.drawable.ic_input_add);
            holder.ivPhysicalAddressStatus.setImageTintList(ColorStateList.valueOf(accent));
            holder.tvPhysicalAddressStatus.setVisibility(View.GONE);
            holder.tvPhysicalAddressTitle.setText(R.string.task_physical_address_missing_compact);
            holder.tvPhysicalAddressTitle.setTextColor(accent);
            holder.tvPhysicalAddressDetails.setVisibility(View.GONE);
            GoogleAddress parsedAddress = new GoogleAddress(item.getAddress());
            String streetHouse = parsedAddress.getStreetHouse();
            holder.cardPhysicalAddress.setOnClickListener(v -> {
                if (onCreateAddress != null && !streetHouse.trim().isEmpty()) {
                    onCreateAddress.onCreateAddress(streetHouse);
                } else {
                    Toast.makeText(context, R.string.task_physical_address_parse_error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            int background = androidx.core.content.ContextCompat.getColor(context, R.color.task_address_found_bg);
            int accent = androidx.core.content.ContextCompat.getColor(context, R.color.task_address_found_accent);
            int text = androidx.core.content.ContextCompat.getColor(context, R.color.task_address_found_text);
            holder.cardPhysicalAddress.setCardBackgroundColor(background);
            holder.cardPhysicalAddress.setStrokeColor(accent);
            holder.cardPhysicalAddress.setStrokeWidth(2);
            holder.ivPhysicalAddressStatus.setImageResource(android.R.drawable.checkbox_on_background);
            holder.ivPhysicalAddressStatus.setImageTintList(ColorStateList.valueOf(accent));
            holder.tvPhysicalAddressStatus.setVisibility(View.GONE);
            holder.tvPhysicalAddressTitle.setText(context.getString(
                    R.string.task_physical_address_matched, physicalContext.addressLabel));
            holder.tvPhysicalAddressTitle.setTextColor(text);
            holder.tvPhysicalAddressDetails.setVisibility(View.VISIBLE);
            holder.tvPhysicalAddressDetails.setText(physicalContext.details);
            holder.tvPhysicalAddressDetails.setTextColor(text);
            holder.cardPhysicalAddress.setOnClickListener(v -> {
                if (context instanceof TasksActivity) {
                    ((TasksActivity) context).openPhysicalAddressFromTask(physicalContext.addressId);
                } else {
                    Intent intent = new Intent(context, AddressDetailActivity.class);
                    intent.putExtra("addressId", physicalContext.addressId);
                    context.startActivity(intent);
                }
            });
        }
        
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
        TextView tvPhysicalAddressTitle, tvPhysicalAddressDetails, tvPhysicalAddressStatus;
        com.google.android.material.card.MaterialCardView cardPhysicalAddress;
        android.widget.ImageView ivPhysicalAddressStatus;
        MaterialButton btnCall, btnHistory;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvTaskType);
            tvDateTime = itemView.findViewById(R.id.tvTaskDateTime);
            tvAddress = itemView.findViewById(R.id.tvTaskAddress);
            tvClientName = itemView.findViewById(R.id.tvTaskClientName);
            cardPhysicalAddress = itemView.findViewById(R.id.cardTaskPhysicalAddress);
            tvPhysicalAddressTitle = itemView.findViewById(R.id.tvTaskPhysicalAddressTitle);
            tvPhysicalAddressDetails = itemView.findViewById(R.id.tvTaskPhysicalAddressDetails);
            tvPhysicalAddressStatus = itemView.findViewById(R.id.tvTaskPhysicalAddressStatus);
            ivPhysicalAddressStatus = itemView.findViewById(R.id.ivTaskPhysicalAddressStatus);
            tvPhysicalAddressStatus = itemView.findViewById(R.id.tvTaskPhysicalAddressStatus);
            ivPhysicalAddressStatus = itemView.findViewById(R.id.ivTaskPhysicalAddressStatus);
            tvAccount = itemView.findViewById(R.id.tvTaskAccount);
            tvTariffTech = itemView.findViewById(R.id.tvTaskTariffTech);
            btnCall = itemView.findViewById(R.id.btnTaskCall);
            btnHistory = itemView.findViewById(R.id.btnTaskHistory);
        }
    }
}
