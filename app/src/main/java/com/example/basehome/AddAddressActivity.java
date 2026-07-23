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

public class AddAddressActivity extends AppCompatActivity {

    private EditText etStreet, etHouse;
    private Spinner spinnerCity;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("addresses");
        auth = FirebaseAuth.getInstance();

        etStreet = findViewById(R.id.etStreet);
        etHouse = findViewById(R.id.etHouse);
        spinnerCity = findViewById(R.id.spinnerCity);
        btnSave = findViewById(R.id.btnSave);
        ImageButton btnBack = findViewById(R.id.btnBackAddAddress);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String[] cities = {"Могилев", "Бобруйск"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(adapter);
        spinnerCity.setSelection(0);

        btnSave.setOnClickListener(v -> saveAddress());
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void saveAddress() {
        String streetInput = etStreet.getText().toString().trim();
        String houseInput = etHouse.getText().toString().trim();
        
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
                
                Address address = new Address(street, houseInput, city, user.getUid(), userName);
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
