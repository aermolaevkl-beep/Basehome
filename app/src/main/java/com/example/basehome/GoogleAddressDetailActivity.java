package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GoogleAddressDetailActivity extends AppCompatActivity {
    public static final String EXTRA_ADDRESS = "google_address";
    public static final String EXTRA_VISITS = "google_visits";
    public static final String EXTRA_APARTMENT = "google_apartment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_address_detail);

        String address = getIntent().getStringExtra(EXTRA_ADDRESS);
        String selectedApartment = getIntent().getStringExtra(EXTRA_APARTMENT);
        ArrayList<String> visits = getIntent().getStringArrayListExtra(EXTRA_VISITS);
        if (visits == null) visits = new ArrayList<>();

        TextView title = findViewById(R.id.tvGoogleAddressDetailTitle);
        TextView count = findViewById(R.id.tvGoogleAddressVisitCount);
        RecyclerView recyclerView = findViewById(R.id.rvGoogleAddressVisits);
        ImageButton back = findViewById(R.id.btnBackGoogleAddressDetail);

        title.setText(address == null || address.trim().isEmpty() ? "Адрес" : address);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (selectedApartment != null) {
            title.setText((address == null ? "Адрес" : address) + ", кв. " + selectedApartment);
            count.setText("Выездов: " + visits.size());
            GoogleAddressAdapter adapter = new GoogleAddressAdapter(new ArrayList<>());
            recyclerView.setAdapter(adapter);
            ArrayList<GoogleAddress> visitItems = new ArrayList<>();
            for (String visit : visits) visitItems.add(new GoogleAddress(visit));
            adapter.updateList(visitItems);
        } else {
            Map<String, ArrayList<String>> apartments = groupVisitsByApartment(visits);
            count.setText("Квартир: " + apartments.size() + "  •  Выездов: " + visits.size());
            recyclerView.setAdapter(new ApartmentAdapter(apartments, apartment -> {
                Intent intent = new Intent(this, GoogleAddressDetailActivity.class);
                intent.putExtra(EXTRA_ADDRESS, address);
                intent.putExtra(EXTRA_APARTMENT, apartment);
                intent.putStringArrayListExtra(EXTRA_VISITS, apartments.get(apartment));
                startActivity(intent);
            }));
        }
        back.setOnClickListener(v -> finish());
    }

    private Map<String, ArrayList<String>> groupVisitsByApartment(List<String> visits) {
        Map<String, ArrayList<String>> result = new LinkedHashMap<>();
        for (String visit : visits) {
            String apartment = new GoogleAddress(visit).getApartment();
            if (apartment.isEmpty()) apartment = "Без номера";
            result.computeIfAbsent(apartment, ignored -> new ArrayList<>()).add(visit);
        }

        List<Map.Entry<String, ArrayList<String>>> entries = new ArrayList<>(result.entrySet());
        entries.sort((e1, e2) -> compareApartments(e1.getKey(), e2.getKey()));

        Map<String, ArrayList<String>> sortedResult = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayList<String>> entry : entries) {
            sortedResult.put(entry.getKey(), entry.getValue());
        }
        return sortedResult;
    }

    private static int compareApartments(String a1, String a2) {
        if (a1.equals("Без номера")) return 1;
        if (a2.equals("Без номера")) return -1;
        try {
            int n1 = Integer.parseInt(a1.replaceAll("\\D+", ""));
            int n2 = Integer.parseInt(a2.replaceAll("\\D+", ""));
            if (n1 != n2) return Integer.compare(n1, n2);
        } catch (Exception ignored) {}
        return a1.compareToIgnoreCase(a2);
    }

    private static class ApartmentAdapter extends RecyclerView.Adapter<ApartmentAdapter.ViewHolder> {
        private final List<Map.Entry<String, ArrayList<String>>> apartments;
        private final OnApartmentClick listener;

        ApartmentAdapter(Map<String, ArrayList<String>> apartments, OnApartmentClick listener) {
            this.apartments = new ArrayList<>(apartments.entrySet());
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_google_apartment, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map.Entry<String, ArrayList<String>> apartment = apartments.get(position);
            holder.title.setText(apartment.getKey().equals("Без номера")
                    ? "Квартира без номера" : "Квартира " + apartment.getKey());
            holder.subtitle.setText("Выездов: " + apartment.getValue().size());
            holder.itemView.setOnClickListener(v -> listener.onClick(apartment.getKey()));
        }

        @Override
        public int getItemCount() {
            return apartments.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvGoogleApartmentTitle);
                subtitle = itemView.findViewById(R.id.tvGoogleApartmentVisits);
            }
        }

        interface OnApartmentClick {
            void onClick(String apartment);
        }
    }
}
