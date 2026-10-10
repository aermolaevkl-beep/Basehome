package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AddBusinessAddressActivity extends AppCompatActivity {

    private EditText etStreet, etHouse, etCenter;
    private Spinner spinnerCity;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_business_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("business_addresses");
        auth = FirebaseAuth.getInstance();

        etStreet = findViewById(R.id.etBusinessStreet);
        etHouse = findViewById(R.id.etBusinessHouse);
        etCenter = findViewById(R.id.etCenterName);
        spinnerCity = findViewById(R.id.spinnerBusinessCity);
        btnSave = findViewById(R.id.btnSaveBusiness);
        ImageButton btnBack = findViewById(R.id.btnBackAddBusiness);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String activeCity = CityManager.getSelectedCity(this);

        CityManager.loadCitiesFromFirebase(cities -> {
            if (isFinishing()) return;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCity.setAdapter(adapter);

            if (!CityManager.ALL_CITIES.equalsIgnoreCase(activeCity)) {
                int index = cities.indexOf(activeCity);
                if (index >= 0) {
                    spinnerCity.setSelection(index);
                }
            }
        });

        btnSave.setOnClickListener(v -> saveBusinessAddress());
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void saveBusinessAddress() {
        String streetInput = etStreet.getText().toString().trim();
        String houseInput = etHouse.getText().toString().trim();
        String centerInput = etCenter.getText().toString().trim();
        String city = spinnerCity.getSelectedItem().toString();

        if (streetInput.isEmpty() || houseInput.isEmpty() || centerInput.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        // Блокируем кнопку
        btnSave.setEnabled(false);
        btnSave.setText("Сохранение...");

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            btnSave.setEnabled(true);
            btnSave.setText("Сохранить");
            finish();
            return;
        }

        String street = capitalize(streetInput);
        String center = capitalize(centerInput);
        String id = databaseReference.push().getKey();
        
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name").get()
            .addOnSuccessListener(snapshot -> {
                String userName = snapshot.getValue(String.class);
                if (userName == null) userName = user.getEmail();
                
                BusinessAddress address = new BusinessAddress(street, houseInput, city, center, user.getUid(), userName);
                if (id != null) {
                    databaseReference.child(id).setValue(address)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Запись создана", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, BusinessAddressDetailActivity.class);
                                intent.putExtra("addressId", id);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSave.setEnabled(true);
                                btnSave.setText("Сохранить");
                                Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                }
            })
            .addOnFailureListener(e -> {
                btnSave.setEnabled(true);
                btnSave.setText("Сохранить");
            });
    }
}
