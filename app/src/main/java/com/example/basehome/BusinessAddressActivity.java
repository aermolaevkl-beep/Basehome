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
    private TextView tvNoResults, tvSectionTitle;
    private final List<BusinessAddress> allAddresses = new ArrayList<>();
    private final List<BusinessAddress> filteredAddresses = new ArrayList<>();
    private DatabaseReference databaseReference;
    private Button btnAddAddress, btnViewMore;
    private int currentLimit = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("business_addresses");

        rvAddresses = findViewById(R.id.rvBusinessAddresses);
        etSearch = findViewById(R.id.etSearchBusiness);
        tvNoResults = findViewById(R.id.tvNoResultsBusiness);
        tvSectionTitle = findViewById(R.id.tvSectionTitleBusiness);
        btnAddAddress = findViewById(R.id.btnAddBusinessAddress);
        btnViewMore = findViewById(R.id.btnViewMoreBusiness);
        ImageButton btnBack = findViewById(R.id.btnBackBusiness);

        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new BusinessAddressAdapter(filteredAddresses, 
            address -> {
                Intent intent = new Intent(BusinessAddressActivity.this, BusinessAddressDetailActivity.class);
                intent.putExtra("addressId", address.getId());
                startActivity(intent);
            },
            this::confirmDeleteBusinessAddress
        );
        
        rvAddresses.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        checkAdminStatus();
        loadAddresses();

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
                    boolean isAdmin = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                    adapter.setAdmin(isAdmin);
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
        filteredAddresses.clear();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            int limit = Math.min(currentLimit, allAddresses.size());
            for (int i = 0; i < limit; i++) {
                filteredAddresses.add(allAddresses.get(i));
            }
            tvSectionTitle.setText("Последние " + limit + " записей:");
            btnViewMore.setVisibility(allAddresses.size() > currentLimit ? View.VISIBLE : View.GONE);
            rvAddresses.setVisibility(filteredAddresses.isEmpty() ? View.GONE : View.VISIBLE);
            tvNoResults.setVisibility(View.GONE);
        } else {
            btnViewMore.setVisibility(View.GONE);
            tvSectionTitle.setText("Результаты поиска:");
            for (BusinessAddress address : allAddresses) {
                if (matches(address, lowerQuery)) {
                    filteredAddresses.add(address);
                }
            }
            rvAddresses.setVisibility(filteredAddresses.isEmpty() ? View.GONE : View.VISIBLE);
            tvNoResults.setVisibility(filteredAddresses.isEmpty() ? View.VISIBLE : View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matches(BusinessAddress address, String query) {
        String street = address.getStreet() != null ? address.getStreet().toLowerCase() : "";
        String house = address.getHouse() != null ? address.getHouse().toLowerCase() : "";
        String center = address.getCenterName() != null ? address.getCenterName().toLowerCase() : "";
        return street.contains(query) || house.contains(query) || center.contains(query);
    }
}
