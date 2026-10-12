package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddAddressActivity extends AppCompatActivity {

    private AutoCompleteTextView etStreet;
    private EditText etHouse, etTsh, etSid;
    private Spinner spinnerCity;
    private DatabaseReference databaseReference;
    private ValueEventListener suggestionsListener;
    private Query suggestionsQuery;
    private final Handler suggestionHandler = new Handler(Looper.getMainLooper());
    private final List<Address> suggestedAddresses = new ArrayList<>();
    private final Runnable searchSuggestions = () -> loadAddressSuggestions();
    private FirebaseAuth auth;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_address);
        SwipeBackHelper.attach(this);

        databaseReference = FirebaseDatabase.getInstance().getReference("addresses");
        auth = FirebaseAuth.getInstance();

        etStreet = findViewById(R.id.etStreet);
        etHouse = findViewById(R.id.etHouse);
        etTsh = findViewById(R.id.etTsh);
        etSid = findViewById(R.id.etSid);
        spinnerCity = findViewById(R.id.spinnerCity);
        btnSave = findViewById(R.id.btnSave);
        ImageButton btnBack = findViewById(R.id.btnBackAddAddress);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String activeCity = CityManager.getSelectedCity(this);

        CityManager.loadCitiesFromFirebase(cities -> {
            if (isFinishing()) return;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCity.setAdapter(adapter);
            spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    suggestionHandler.removeCallbacks(searchSuggestions);
                    suggestionHandler.post(searchSuggestions);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
            });

            if (!CityManager.ALL_CITIES.equalsIgnoreCase(activeCity)) {
                int index = cities.indexOf(activeCity);
                if (index >= 0) {
                    spinnerCity.setSelection(index);
                }
            }
        });

        etStreet.setThreshold(2);
        etStreet.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>()));
        etStreet.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                suggestionHandler.removeCallbacks(searchSuggestions);
                if (s.toString().trim().length() >= 2) {
                    suggestionHandler.postDelayed(searchSuggestions, 300);
                } else {
                    clearSuggestions();
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        etStreet.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= suggestedAddresses.size()) return;
            Address selectedAddress = suggestedAddresses.get(position);
            Intent intent = new Intent(AddAddressActivity.this, AddressDetailActivity.class);
            intent.putExtra("addressId", selectedAddress.getId());
            startActivity(intent);
            finish();
        });

        btnSave.setOnClickListener(v -> saveAddress());
    }

    private void loadAddressSuggestions() {
        if (isFinishing() || spinnerCity.getSelectedItem() == null) return;
        String input = etStreet.getText().toString().trim();
        if (input.length() < 2) {
            clearSuggestions();
            return;
        }

        String city = String.valueOf(spinnerCity.getSelectedItem());
        String prefix = capitalize(input);
        if (suggestionsListener != null && suggestionsQuery != null) {
            suggestionsQuery.removeEventListener(suggestionsListener);
        }
        suggestionsQuery = databaseReference.orderByChild("street")
                .startAt(prefix)
                .endAt(prefix + "\uf8ff")
                .limitToFirst(20);
        suggestionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (isFinishing() || !city.equals(String.valueOf(spinnerCity.getSelectedItem())) ||
                        !input.equals(etStreet.getText().toString().trim())) return;
                suggestedAddresses.clear();
                List<String> labels = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Address address = child.getValue(Address.class);
                    if (address == null || address.getStreet() == null || address.getHouse() == null) continue;
                    if (address.getCity() != null && !city.equalsIgnoreCase(address.getCity())) continue;
                    address.setId(child.getKey());
                    suggestedAddresses.add(address);
                    labels.add(address.getStreet() + ", дом " + address.getHouse());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        AddAddressActivity.this,
                        android.R.layout.simple_dropdown_item_1line,
                        labels
                );
                etStreet.setAdapter(adapter);
                if (!labels.isEmpty() && etStreet.hasFocus()) etStreet.showDropDown();
                else etStreet.dismissDropDown();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                clearSuggestions();
            }
        };
        suggestionsQuery.addListenerForSingleValueEvent(suggestionsListener);
    }

    private void clearSuggestions() {
        suggestedAddresses.clear();
        if (etStreet != null) etStreet.dismissDropDown();
    }

    @Override
    protected void onDestroy() {
        suggestionHandler.removeCallbacks(searchSuggestions);
        if (suggestionsListener != null && suggestionsQuery != null) {
            suggestionsQuery.removeEventListener(suggestionsListener);
        }
        super.onDestroy();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void saveAddress() {
        String streetInput = etStreet.getText().toString().trim();
        String houseInput = etHouse.getText().toString().trim();
        String tshInput = etTsh.getText().toString().trim();
        String sidInput = etSid.getText().toString().trim();
        
        if (streetInput.isEmpty() || houseInput.isEmpty()) {
            Toast.makeText(this, "Заполните улицу и номер дома", Toast.LENGTH_SHORT).show();
            return;
        }

        // Блокируем кнопку, чтобы избежать дубликатов
        btnSave.setEnabled(false);
        btnSave.setText("Сохранение...");

        String street = capitalize(streetInput);
        String city = spinnerCity.getSelectedItem().toString();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            btnSave.setEnabled(true);
            btnSave.setText("Создать адрес");
            return;
        }

        String id = databaseReference.push().getKey();
        
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name").get()
            .addOnSuccessListener(snapshot -> {
                String userName = snapshot.getValue(String.class);
                if (userName == null) userName = user.getEmail();
                
                Address address = new Address(street, houseInput, city, user.getUid(), userName, tshInput, sidInput);
                if (id != null) {
                    databaseReference.child(id).setValue(address)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(AddAddressActivity.this, "Адрес успешно создан!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(AddAddressActivity.this, AddressDetailActivity.class);
                                intent.putExtra("addressId", id);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSave.setEnabled(true);
                                btnSave.setText("Создать адрес");
                                Toast.makeText(AddAddressActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                }
            })
            .addOnFailureListener(e -> {
                btnSave.setEnabled(true);
                btnSave.setText("Создать адрес");
            });
    }
}
