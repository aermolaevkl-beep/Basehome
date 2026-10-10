package com.example.basehome;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CityManager {

    public static final String ALL_CITIES = "Все города";
    private static final String PREF_NAME = "basehome_prefs";
    private static final String KEY_SELECTED_CITY = "selected_city";

    public interface OnCitiesLoadedListener {
        void onCitiesLoaded(List<String> cities);
    }

    public interface OnCityLoadedListener {
        void onCityLoaded(String city);
    }

    public static String getSelectedCity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_CITY, ALL_CITIES);
    }

    public static void setSelectedCity(Context context, String city) {
        String targetCity = (city == null || city.trim().isEmpty()) ? ALL_CITIES : city;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SELECTED_CITY, targetCity).apply();

        // Синхронизация с Firebase в профиле пользователя
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(currentUser.getUid());
            userRef.child("selectedCity").setValue(targetCity);
        }
    }

    public static void syncCityFromFirebase(Context context, OnCityLoadedListener listener) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            if (listener != null) listener.onCityLoaded(getSelectedCity(context));
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUser.getUid())
                .child("selectedCity");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String firebaseCity = snapshot.getValue(String.class);
                if (firebaseCity != null && !firebaseCity.trim().isEmpty()) {
                    SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putString(KEY_SELECTED_CITY, firebaseCity).apply();
                    if (listener != null) listener.onCityLoaded(firebaseCity);
                } else {
                    if (listener != null) listener.onCityLoaded(getSelectedCity(context));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (listener != null) listener.onCityLoaded(getSelectedCity(context));
            }
        });
    }

    public static void loadCitiesFromFirebase(OnCitiesLoadedListener listener) {
        DatabaseReference citiesRef = FirebaseDatabase.getInstance().getReference("cities");

        citiesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> cities = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String cityName = child.getValue(String.class);
                        if (cityName != null && !cityName.trim().isEmpty() && !cities.contains(cityName)) {
                            cities.add(cityName);
                        }
                    }
                }

                if (cities.isEmpty()) {
                    cities.addAll(Arrays.asList("Могилев", "Бобруйск"));
                }

                if (listener != null) listener.onCitiesLoaded(cities);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                List<String> fallback = Arrays.asList("Могилев", "Бобруйск");
                if (listener != null) listener.onCitiesLoaded(fallback);
            }
        });
    }
}
