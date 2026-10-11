package com.example.basehome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleAddressAdapter extends RecyclerView.Adapter<GoogleAddressAdapter.ViewHolder> {

    private final List<GoogleAddress> addresses = new ArrayList<>();
    private final Pattern datePattern = Pattern.compile("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b");
    private final boolean groupedAddresses;
    private final OnAddressClickListener clickListener;

    public interface OnAddressClickListener {
        void onAddressClick(GoogleAddress address);
    }

    public GoogleAddressAdapter(List<GoogleAddress> addresses) {
        this(addresses, false, null);
    }

    public GoogleAddressAdapter(List<GoogleAddress> addresses, boolean groupedAddresses,
                                OnAddressClickListener clickListener) {
        this.addresses.addAll(addresses);
        this.groupedAddresses = groupedAddresses;
        this.clickListener = clickListener;
    }

    public void updateList(List<GoogleAddress> newList) {
        this.addresses.clear();
        this.addresses.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_google_address, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GoogleAddress item = addresses.get(position);
        String fullText = item.getStreet();
        if (groupedAddresses) {
            holder.tvStreetHouse.setText(item.getStreetHouse());
            holder.tvNameTech.setVisibility(View.GONE);
            holder.tvDate.setVisibility(View.GONE);
            holder.tvOtherInfo.setVisibility(View.VISIBLE);
            holder.tvOtherInfo.setText("Квартир: " + item.getApartmentCount()
                    + "  •  Выездов: " + item.getVisits().size());
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onAddressClick(item);
            });
            return;
        }
        holder.itemView.setOnClickListener(null);

        // 1. Извлекаем дату
        Matcher matcher = datePattern.matcher(fullText);
        String date = "";
        String textWithoutDate = fullText;
        if (matcher.find()) {
            date = matcher.group(1);
            textWithoutDate = fullText.replace(date, "").trim();
        }
        holder.tvDate.setText(date);
        holder.tvDate.setVisibility(date.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvStreetHouse.setText(item.getStreetHouse());

        // 2. Разделяем на части по запятой и ДЕДУПЛИЦИРУЕМ
        String[] rawParts = textWithoutDate.split(",");
        Set<String> uniqueParts = new LinkedHashSet<>(); // Сохраняет порядок и убирает дубликаты
        for (String p : rawParts) {
            String trimmed = p.trim().replace('\u00A0', ' ');
            if (!trimmed.isEmpty()) {
                uniqueParts.add(trimmed);
            }
        }
        
        List<String> cleanParts = new ArrayList<>(uniqueParts);

        // 3. Распределяем по полям
        // Заголовок (Адрес)
        if (!cleanParts.isEmpty()) {
            holder.tvStreetHouse.setText(item.getStreetHouse());
            cleanParts.remove(0);
        } else {
            holder.tvStreetHouse.setText(item.getStreetHouse());
        }

        // Вторая строка (ФИО/Техник)
        if (!cleanParts.isEmpty()) {
            holder.tvNameTech.setText(cleanParts.get(0));
            holder.tvNameTech.setVisibility(View.VISIBLE);
            cleanParts.remove(0);
        } else {
            holder.tvNameTech.setVisibility(View.GONE);
        }

        // Подвал (Всё остальное без повторов)
        if (!cleanParts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cleanParts.size(); i++) {
                sb.append(cleanParts.get(i));
                if (i < cleanParts.size() - 1) sb.append(", ");
            }
            holder.tvOtherInfo.setText(sb.toString());
            holder.tvOtherInfo.setVisibility(View.VISIBLE);
        } else {
            holder.tvOtherInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return addresses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStreetHouse, tvNameTech, tvOtherInfo, tvDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStreetHouse = itemView.findViewById(R.id.tvGoogleStreetHouse);
            tvNameTech = itemView.findViewById(R.id.tvGoogleNameTech);
            tvOtherInfo = itemView.findViewById(R.id.tvGoogleOtherInfo);
            tvDate = itemView.findViewById(R.id.tvGoogleDate);
        }
    }
}
