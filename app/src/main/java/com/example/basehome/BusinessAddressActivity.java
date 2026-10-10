package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusinessAddressActivity extends AppCompatActivity {
    private EditText etSearch;
    private BusinessAddressAdapter adapter;
    private RecyclerView rvAddresses;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvNoResults, tvSectionTitle;
    private final List<BusinessAddress> allAddresses = new ArrayList<>();
    private DatabaseReference databaseReference;
    private Button btnAddAddress, btnViewMore;
    private int currentLimit = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("business_addresses");

        rvAddresses = findViewById(R.id.rvBusinessAddresses);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshBusiness);
        etSearch = findViewById(R.id.etSearchBusiness);
        tvNoResults = findViewById(R.id.tvNoResultsBusiness);
        tvSectionTitle = findViewById(R.id.tvSectionTitleBusiness);
        btnAddAddress = findViewById(R.id.btnAddBusinessAddress);
        btnViewMore = findViewById(R.id.btnViewMoreBusiness);
        ImageButton btnBack = findViewById(R.id.btnBackBusiness);

        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new BusinessAddressAdapter(new ArrayList<>(), 
            address -> {
                Intent intent = new Intent(BusinessAddressActivity.this, BusinessAddressDetailActivity.class);
                intent.putExtra("addressId", address.getId());
                startActivity(intent);
            },
            this::confirmDeleteBusinessAddress
        );
        
        rvAddresses.setAdapter(adapter);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        checkAdminStatus();
        loadAddresses();

        // Pull-to-Refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadAddresses();
            swipeRefreshLayout.setRefreshing(false);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAddresses(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAddAddress.setOnClickListener(v -> startActivity(new Intent(BusinessAddressActivity.this, AddBusinessAddressActivity.class)));

        btnViewMore.setOnClickListener(v -> {
            currentLimit += 5;
            filterAddresses(etSearch.getText().toString());
        });
    }

    private void checkAdminStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("isAdmin").get()
                .addOnSuccessListener(snapshot -> {
                    Boolean isAdmin = snapshot.getValue(Boolean.class);
                    adapter.setAdmin(Boolean.TRUE.equals(isAdmin));
                });
    }

    private void confirmDeleteBusinessAddress(BusinessAddress address) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление")
                .setMessage("Удалить Юр. адрес " + address.getCenterName() + " со всеми данными?")
                .setPositiveButton("Да", (dialog, which) -> {
                    databaseReference.child(address.getId()).removeValue()
                        .addOnSuccessListener(aVoid -> Toast.makeText(BusinessAddressActivity.this, "Удалено", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Нет", null)
                .show();
    }

    private void loadAddresses() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allAddresses.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    BusinessAddress address = data.getValue(BusinessAddress.class);
                    if (address != null) {
                        address.setId(data.getKey());
                        allAddresses.add(address);
                    }
                }
                Collections.reverse(allAddresses);
                filterAddresses(etSearch.getText().toString());
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void filterAddresses(String query) {
        String selectedCity = CityManager.getSelectedCity(this);

        List<BusinessAddress> cityAddresses = new ArrayList<>();
        for (BusinessAddress address : allAddresses) {
            String city = address.getCity() != null ? address.getCity() : "Могилев";
            if (CityManager.ALL_CITIES.equalsIgnoreCase(selectedCity) || city.equalsIgnoreCase(selectedCity)) {
                cityAddresses.add(address);
            }
        }

        List<BusinessAddress> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            int limit = Math.min(currentLimit, cityAddresses.size());
            for (int i = 0; i < limit; i++) {
                filtered.add(cityAddresses.get(i));
            }
            tvSectionTitle.setText("Последние записи:");
            btnViewMore.setVisibility(cityAddresses.size() > currentLimit ? View.VISIBLE : View.GONE);
        } else {
            btnViewMore.setVisibility(View.GONE);
            tvSectionTitle.setText("Результаты поиска:");
            for (BusinessAddress address : cityAddresses) {
                if (matches(address, lowerQuery)) {
                    filtered.add(address);
                }
            }
        }
        
        boolean isEmpty = filtered.isEmpty();
        tvNoResults.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            tvNoResults.setText(lowerQuery.isEmpty() ? "Записей еще нет" : "Ничего не найдено");
            rvAddresses.setVisibility(View.GONE);
        } else {
            rvAddresses.setVisibility(View.VISIBLE);
        }

        adapter.updateList(filtered);
    }

    @Override
    protected void onResume() {
        super.onResume();
        filterAddresses(etSearch.getText().toString());
    }

    private boolean matches(BusinessAddress address, String query) {
        String street = address.getStreet() != null ? address.getStreet().toLowerCase() : "";
        String house = address.getHouse() != null ? address.getHouse().toLowerCase() : "";
        String center = address.getCenterName() != null ? address.getCenterName().toLowerCase() : "";
        return street.contains(query) || house.contains(query) || center.contains(query);
    }
}
