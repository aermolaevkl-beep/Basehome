package com.example.basehome;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusinessAddressDetailActivity extends AppCompatActivity {

    private TextView tvStreet, tvHouse, tvCenter, tvCity;
    private DatabaseReference databaseReference;
    private String addressId;
    
    private RecyclerView rvSockets, rvReserves;
    private BusinessInfoAdapter socketAdapter, reserveAdapter;
    private final List<BusinessInfoEntry> socketEntries = new ArrayList<>();
    private final List<BusinessInfoEntry> reserveEntries = new ArrayList<>();
    private BusinessAddress currentBusinessAddress;
    private ImageButton btnDelete, btnEdit;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_address_detail);

        addressId = getIntent().getStringExtra("addressId");
        if (addressId == null) {
            finish();
            return;
        }

        databaseReference = FirebaseDatabase.getInstance().getReference("business_addresses").child(addressId);

        tvCity = findViewById(R.id.tvBusCity);
        tvStreet = findViewById(R.id.tvBusStreet);
        tvHouse = findViewById(R.id.tvBusHouse);
        tvCenter = findViewById(R.id.tvBusCenterName);
        btnDelete = findViewById(R.id.btnDeleteBusinessAddress);
        btnEdit = findViewById(R.id.btnEditBusinessAddress);
        
        rvSockets = findViewById(R.id.rvSockets);
        rvReserves = findViewById(R.id.rvReserves);

        btnDelete.setVisibility(View.GONE);
        btnEdit.setVisibility(View.GONE);

        setupRecyclerViews();
        checkAdminStatus();
        loadData();

        findViewById(R.id.btnSocket).setOnClickListener(v -> showAddDialog("Добавить розетку", "sockets"));
        findViewById(R.id.btnReserve).setOnClickListener(v -> showAddDialog("Добавить запас", "reserves"));
        btnEdit.setOnClickListener(v -> showEditBusinessAddressDialog());
        btnDelete.setOnClickListener(v -> confirmDeleteAddress());
    }

    private void checkAdminStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseDatabase.getInstance().getReference("users").child(uid).child("isAdmin").get()
                .addOnSuccessListener(snapshot -> {
                    isAdmin = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                    updateActionButtonsVisibility();
                });
    }

    private void updateActionButtonsVisibility() {
        String currentUid = FirebaseAuth.getInstance().getUid();
        boolean isAuthor = currentBusinessAddress != null && currentUid != null && currentUid.equals(currentBusinessAddress.getUserId());
        
        if (isAdmin || isAuthor) {
            btnDelete.setVisibility(View.VISIBLE);
            btnEdit.setVisibility(View.VISIBLE);
        }
    }

    private void setupRecyclerViews() {
        rvSockets.setLayoutManager(new LinearLayoutManager(this));
        socketAdapter = new BusinessInfoAdapter(socketEntries, isAdmin, 
                entry -> deleteEntry("sockets", entry.getId()),
                entry -> showEditEntryDialog("Редактировать розетку", "sockets", entry));
        rvSockets.setAdapter(socketAdapter);

        rvReserves.setLayoutManager(new LinearLayoutManager(this));
        reserveAdapter = new BusinessInfoAdapter(reserveEntries, isAdmin, 
                entry -> deleteEntry("reserves", entry.getId()),
                entry -> showEditEntryDialog("Редактировать запас", "reserves", entry));
        rvReserves.setAdapter(reserveAdapter);
    }

    private void loadData() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentBusinessAddress = snapshot.getValue(BusinessAddress.class);
                if (currentBusinessAddress != null) {
                    tvCity.setText(currentBusinessAddress.getCity() != null ? currentBusinessAddress.getCity() : "Могилев");
                    tvStreet.setText(currentBusinessAddress.getStreet());
                    tvHouse.setText(getString(R.string.house_label, currentBusinessAddress.getHouse()));
                    tvCenter.setText(currentBusinessAddress.getCenterName());
                    updateActionButtonsVisibility();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        databaseReference.child("sockets").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                socketEntries.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    BusinessInfoEntry entry = data.getValue(BusinessInfoEntry.class);
                    if (entry != null) {
                        entry.setId(data.getKey());
                        socketEntries.add(entry);
                    }
                }
                socketAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        databaseReference.child("reserves").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reserveEntries.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    BusinessInfoEntry entry = data.getValue(BusinessInfoEntry.class);
                    if (entry != null) {
                        entry.setId(data.getKey());
                        reserveEntries.add(entry);
                    }
                }
                reserveAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void showEditBusinessAddressDialog() {
        if (currentBusinessAddress == null) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.activity_add_business_address, null);
        EditText etStreet = dialogView.findViewById(R.id.etBusinessStreet);
        EditText etHouse = dialogView.findViewById(R.id.etBusinessHouse);
        EditText etCenter = dialogView.findViewById(R.id.etCenterName);
        Spinner spinnerCity = dialogView.findViewById(R.id.spinnerBusinessCity);
        Button btnSave = dialogView.findViewById(R.id.btnSaveBusiness);

        String[] cities = {"Могилев", "Бобруйск"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(adapter);

        etStreet.setText(currentBusinessAddress.getStreet());
        etHouse.setText(currentBusinessAddress.getHouse());
        etCenter.setText(currentBusinessAddress.getCenterName());
        int cityIndex = "Бобруйск".equals(currentBusinessAddress.getCity()) ? 1 : 0;
        spinnerCity.setSelection(cityIndex);
        
        btnSave.setVisibility(View.GONE);

        new AlertDialog.Builder(this)
                .setTitle("Редактировать Юр. адрес")
                .setView(dialogView)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String streetInput = etStreet.getText().toString().trim();
                    String houseInput = etHouse.getText().toString().trim();
                    String centerInput = etCenter.getText().toString().trim();
                    String city = spinnerCity.getSelectedItem().toString();

                    if (!streetInput.isEmpty() && !houseInput.isEmpty() && !centerInput.isEmpty()) {
                        String street = capitalize(streetInput);
                        String center = capitalize(centerInput);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("street", street);
                        updates.put("house", houseInput);
                        updates.put("centerName", center);
                        updates.put("city", city);

                        databaseReference.updateChildren(updates)
                                .addOnSuccessListener(aVoid -> Toast.makeText(BusinessAddressDetailActivity.this, "Обновлено", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmDeleteAddress() {
        new AlertDialog.Builder(this)
                .setTitle("Удаление адреса")
                .setMessage("Вы уверены, что хотите полностью удалить этот Юр. адрес?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    databaseReference.removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showAddDialog(String title, String nodeKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_simple_input, null);
        EditText etInput = view.findViewById(R.id.etSimpleInput);
        builder.setView(view);

        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String text = etInput.getText().toString().trim();
            String uid = FirebaseAuth.getInstance().getUid();
            if (!text.isEmpty() && uid != null) {
                String id = databaseReference.child(nodeKey).push().getKey();
                BusinessInfoEntry entry = new BusinessInfoEntry(id, text, System.currentTimeMillis(), uid);
                if (id != null) {
                    databaseReference.child(nodeKey).child(id).setValue(entry);
                }
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showEditEntryDialog(String title, String nodeKey, BusinessInfoEntry entry) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_simple_input, null);
        EditText etInput = view.findViewById(R.id.etSimpleInput);
        etInput.setText(entry.getText());
        builder.setView(view);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                databaseReference.child(nodeKey).child(entry.getId()).child("text").setValue(text);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void deleteEntry(String nodeKey, String entryId) {
        new AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setMessage("Удалить эту запись?")
            .setPositiveButton("Да", (dialog, which) -> 
                databaseReference.child(nodeKey).child(entryId).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(BusinessAddressDetailActivity.this, "Удалено", Toast.LENGTH_SHORT).show())
            )
            .setNegativeButton("Нет", null)
            .show();
    }
}
