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

public class PhysicalAddressActivity extends AppCompatActivity {
    private EditText etSearch;
    private AddressAdapter adapter;
    private RecyclerView rvAddresses;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvNoResults, tvSectionTitle;
    private final List<Address> allAddresses = new ArrayList<>();
    private DatabaseReference databaseReference;
    private Button btnAddAddress, btnViewAll;
    private int currentLimit = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physical_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("addresses");

        rvAddresses = findViewById(R.id.rvAddresses);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshPhysical);
        etSearch = findViewById(R.id.etSearch);
        tvNoResults = findViewById(R.id.tvNoResults);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);
        btnAddAddress = findViewById(R.id.btnAddAddress);
        btnViewAll = findViewById(R.id.btnViewAll);
        ImageButton btnBack = findViewById(R.id.btnBackPhysical);

        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new AddressAdapter(new ArrayList<>(), 
            address -> {
                Intent intent = new Intent(PhysicalAddressActivity.this, AddressDetailActivity.class);
                intent.putExtra("addressId", address.getId());
                startActivity(intent);
            },
            this::confirmDeleteAddress
        );
        
        rvAddresses.setAdapter(adapter);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        
        checkAdminStatus();
        loadAddresses();

        // Настройка Pull-to-Refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Поскольку у нас ValueEventListener, данные и так обновляются в реальном времени.
            // Но мы имитируем обновление для удобства пользователя.
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

        btnAddAddress.setOnClickListener(v -> startActivity(new Intent(PhysicalAddressActivity.this, AddAddressActivity.class)));
        btnViewAll.setOnClickListener(v -> {
            currentLimit += 10;
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

    private void confirmDeleteAddress(Address address) {
        new AlertDialog.Builder(this)
                .setTitle("Удаление")
                .setMessage("Удалить адрес " + address.getStreet() + " со всеми данными?")
                .setPositiveButton("Да", (dialog, which) -> {
                    String id = address.getId();
                    databaseReference.child(id).removeValue();
                    FirebaseDatabase.getInstance().getReference("entrances").child(id).removeValue();
                    FirebaseDatabase.getInstance().getReference("comments").child(id).removeValue();
                    Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
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
                    Address address = data.getValue(Address.class);
                    if (address != null) {
                        address.setId(data.getKey());
                        allAddresses.add(address);
                    }
                }
                Collections.reverse(allAddresses);
                filterAddresses(etSearch.getText().toString());
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PhysicalAddressActivity.this, "Ошибка БД", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterAddresses(String query) {
        String selectedCity = CityManager.getSelectedCity(this);

        List<Address> cityAddresses = new ArrayList<>();
        for (Address address : allAddresses) {
            String city = address.getCity() != null ? address.getCity() : "Могилев";
            if (CityManager.ALL_CITIES.equalsIgnoreCase(selectedCity) || city.equalsIgnoreCase(selectedCity)) {
                cityAddresses.add(address);
            }
        }

        List<Address> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();
        
        if (lowerQuery.isEmpty()) {
            int limit = Math.min(currentLimit, cityAddresses.size());
            for (int i = 0; i < limit; i++) {
                filtered.add(cityAddresses.get(i));
            }
            tvSectionTitle.setText(cityAddresses.isEmpty() ? "" : "Последние записи:");
            btnViewAll.setVisibility(cityAddresses.size() > currentLimit ? View.VISIBLE : View.GONE);
        } else {
            btnViewAll.setVisibility(View.GONE);
            tvSectionTitle.setText("Результаты поиска:");
            for (Address address : cityAddresses) {
                String street = address.getStreet() != null ? address.getStreet().toLowerCase() : "";
                String house = address.getHouse() != null ? address.getHouse().toLowerCase() : "";
                if (street.contains(lowerQuery) || house.contains(lowerQuery)) {
                    filtered.add(address);
                }
            }
        }
        
        boolean isEmpty = filtered.isEmpty();
        tvNoResults.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            tvNoResults.setText(lowerQuery.isEmpty() ? "Адресов еще нет" : "Ничего не найдено");
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
}
