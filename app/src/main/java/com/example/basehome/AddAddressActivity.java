package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_address);

        databaseReference = FirebaseDatabase.getInstance().getReference("addresses");
        auth = FirebaseAuth.getInstance();

        etStreet = findViewById(R.id.etStreet);
        etHouse = findViewById(R.id.etHouse);
        spinnerCity = findViewById(R.id.spinnerCity);
        Button btnSave = findViewById(R.id.btnSave);

        // Настройка спиннера городов
        String[] cities = {"Могилев", "Бобруйск"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(adapter);
        spinnerCity.setSelection(0); // Могилев по умолчанию

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

        String street = capitalize(streetInput);
        String house = houseInput; 
        String city = spinnerCity.getSelectedItem().toString();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String id = databaseReference.push().getKey();
        
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name").get()
            .addOnSuccessListener(snapshot -> {
                String userName = snapshot.getValue(String.class);
                if (userName == null) userName = user.getEmail();
                
                Address address = new Address(street, house, city, user.getUid(), userName);
                if (id != null) {
                    databaseReference.child(id).setValue(address)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(AddAddressActivity.this, "Адрес успешно создан!", Toast.LENGTH_SHORT).show();
                                
                                Intent intent = new Intent(AddAddressActivity.this, AddressDetailActivity.class);
                                intent.putExtra("addressId", id);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(AddAddressActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
    }
}
