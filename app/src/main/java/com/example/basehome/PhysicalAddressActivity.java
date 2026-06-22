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

public class PhysicalAddressActivity extends AppCompatActivity {
    private EditText etSearch;
    private AddressAdapter adapter;
    private RecyclerView rvAddresses;
    private TextView tvNoResults, tvSectionTitle;
    private final List<Address> allAddresses = new ArrayList<>();
    private final List<Address> filteredAddresses = new ArrayList<>();
    private DatabaseReference databaseReference;
    private Button btnAddAddress, btnViewAll;
    private int currentLimit = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physical_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("addresses");

        rvAddresses = findViewById(R.id.rvAddresses);
        etSearch = findViewById(R.id.etSearch);
        tvNoResults = findViewById(R.id.tvNoResults);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);
        btnAddAddress = findViewById(R.id.btnAddAddress);
        btnViewAll = findViewById(R.id.btnViewAll);
        ImageButton btnBack = findViewById(R.id.btnBackPhysical);

        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new AddressAdapter(filteredAddresses, 
            address -> {
                Intent intent = new Intent(PhysicalAddressActivity.this, AddressDetailActivity.class);
                intent.putExtra("addressId", address.getId());
                startActivity(intent);
            },
            this::confirmDeleteAddress
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

        btnAddAddress.setOnClickListener(v -> startActivity(new Intent(PhysicalAddressActivity.this, AddAddressActivity.class)));
        btnViewAll.setOnClickListener(v -> {
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
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void filterAddresses(String query) {
        filteredAddresses.clear();
        String lowerQuery = query.toLowerCase().trim();
        if (lowerQuery.isEmpty()) {
            int limit = Math.min(currentLimit, allAddresses.size());
            for (int i = 0; i < limit; i++) filteredAddresses.add(allAddresses.get(i));
            tvSectionTitle.setText("Последние " + limit + " адресов:");
            btnViewAll.setVisibility(allAddresses.size() > currentLimit ? View.VISIBLE : View.GONE);
            rvAddresses.setVisibility(filteredAddresses.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            btnViewAll.setVisibility(View.GONE);
            tvSectionTitle.setText("Результаты поиска:");
            for (Address address : allAddresses) {
                if (address.getStreet().toLowerCase().contains(lowerQuery) || address.getHouse().toLowerCase().contains(lowerQuery)) {
                    filteredAddresses.add(address);
                }
            }
            rvAddresses.setVisibility(filteredAddresses.isEmpty() ? View.GONE : View.VISIBLE);
            tvNoResults.setVisibility(filteredAddresses.isEmpty() ? View.VISIBLE : View.GONE);
        }
        adapter.notifyDataSetChanged();
    }
}
