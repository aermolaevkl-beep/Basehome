package com.example.basehome;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private FirebaseRemoteConfig remoteConfig;
    private UpdateManager updateManager;

    private View vConnectionIndicator;
    private TextView tvConnectionStatus;
    private ChipGroup chipGroupCities;
    private DatabaseReference userStatusRef;
    private DatabaseReference connectedRef;
    private ValueEventListener connectionListener;
    
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private Runnable pingRunnable;
    private boolean isPingRunning = false;
    private long lastPing = -1;
    private boolean isOnline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        vConnectionIndicator = findViewById(R.id.vConnectionIndicator);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        chipGroupCities = findViewById(R.id.chipGroupCities);
        
        initIndicatorBackground();
        setupClickListeners();
        setupCitySelector();
        
        updateManager = new UpdateManager(this);
        setupRemoteConfig();
        checkUpdate();
        updateUI();
        
        setupPresenceAndConnection(); 
        calculateGlobalRanks(); 
        syncUser();
    }

    private void initIndicatorBackground() {
        if (!(vConnectionIndicator.getBackground() instanceof GradientDrawable)) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            vConnectionIndicator.setBackground(shape);
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            if (userStatusRef != null) userStatusRef.child("isOnline").setValue(false);
            auth.signOut();
            goToLogin();
        });

        findViewById(R.id.btnProfile).setOnClickListener(v -> {
            if (currentUser != null) {
                FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid()).child("isAdmin")
                        .get().addOnSuccessListener(snapshot -> {
                            Boolean isAdmin = snapshot.getValue(Boolean.class);
                            if (Boolean.TRUE.equals(isAdmin)) {
                                startActivity(new Intent(this, AdminActivity.class));
                            } else {
                                startActivity(new Intent(this, ProfileActivity.class));
                            }
                        }).addOnFailureListener(e -> startActivity(new Intent(this, ProfileActivity.class)));
            } else {
                startActivity(new Intent(this, ProfileActivity.class));
            }
        });
        findViewById(R.id.btnPhysicalSection).setOnClickListener(v -> startActivity(new Intent(this, PhysicalAddressActivity.class)));
        findViewById(R.id.btnBusinessSection).setOnClickListener(v -> startActivity(new Intent(this, BusinessAddressActivity.class)));
        findViewById(R.id.btnDatabase).setOnClickListener(v -> startActivity(new Intent(this, GoogleDatabaseActivity.class)));
    }

    private void setupCitySelector() {
        if (chipGroupCities == null) return;

        CityManager.syncCityFromFirebase(this, currentCity -> {
            CityManager.loadCitiesFromFirebase(cities -> {
                if (isFinishing()) return;

                chipGroupCities.removeAllViews();

                List<String> cityOptions = new ArrayList<>();
                cityOptions.add(CityManager.ALL_CITIES);
                for (String c : cities) {
                    if (!cityOptions.contains(c)) {
                        cityOptions.add(c);
                    }
                }

                Chip selectedChip = null;

                for (String city : cityOptions) {
                    Chip chip = new Chip(this);
                    boolean isAll = CityManager.ALL_CITIES.equalsIgnoreCase(city);
                    chip.setText(isAll ? "🌐 " + city : "📍 " + city);
                    chip.setCheckable(true);
                    chip.setClickable(true);

                    if (city.equalsIgnoreCase(currentCity)) {
                        selectedChip = chip;
                    }

                    chip.setOnClickListener(v -> {
                        CityManager.setSelectedCity(MainActivity.this, city);
                    });

                    chipGroupCities.addView(chip);
                }

                if (selectedChip != null) {
                    selectedChip.setChecked(true);
                } else if (chipGroupCities.getChildCount() > 0) {
                    ((Chip) chipGroupCities.getChildAt(0)).setChecked(true);
                }
            });
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void calculateGlobalRanks() {
        FirebaseDatabase.getInstance().getReference("addresses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Integer> counts = new HashMap<>();
                for (DataSnapshot item : snapshot.getChildren()) {
                    String uid = item.child("userId").getValue(String.class);
                    if (uid != null && !uid.isEmpty()) {
                        counts.merge(uid, 1, Integer::sum);
                    }
                }
                List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
                list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
                
                List<String> top3 = new ArrayList<>();
                for (int i = 0; i < Math.min(3, list.size()); i++) {
                    top3.add(list.get(i).getKey());
                }
                UserRank.Companion.setTop3UserIds(top3);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupPresenceAndConnection() {
        if (currentUser == null) return;
        userStatusRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");

        connectionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isOnline = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                if (isOnline) {
                    userStatusRef.child("isOnline").setValue(true);
                    userStatusRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP);
                    userStatusRef.child("isOnline").onDisconnect().setValue(false);
                    triggerPing(); 
                } else {
                    lastPing = -1;
                }
                refreshStatusUI();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        connectedRef.addValueEventListener(connectionListener);
    }

    private void refreshStatusUI() {
        runOnUiThread(() -> {
            if (vConnectionIndicator == null || tvConnectionStatus == null) return;

            int color;
            String statusText;

            if (!isOnline) {
                color = ContextCompat.getColor(this, android.R.color.holo_red_light);
                statusText = getString(R.string.status_offline);
            } else {
                color = ContextCompat.getColor(this, android.R.color.holo_green_light);
                String baseStatus = getString(R.string.status_online);
                
                if (lastPing >= 0) {
                    if (lastPing >= 500) color = ContextCompat.getColor(this, android.R.color.holo_red_light);
                    else if (lastPing >= 150) color = ContextCompat.getColor(this, android.R.color.holo_orange_light);
                    statusText = getString(R.string.ping_format, baseStatus, (int)lastPing);
                } else {
                    statusText = getString(R.string.status_measuring, baseStatus);
                }
            }

            tvConnectionStatus.setText(statusText);
            if (vConnectionIndicator.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) vConnectionIndicator.getBackground()).setColor(color);
            }
        });
    }

    private void triggerPing() {
        if (!isOnline || !isPingRunning) return;
        
        final long startTime = SystemClock.elapsedRealtime();
        FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset")
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isPingRunning) return;
                lastPing = SystemClock.elapsedRealtime() - startTime;
                refreshStatusUI();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void startPingMeasurement() {
        stopPingMeasurement(); // Очищаем старый цикл перед запуском нового
        isPingRunning = true;
        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPingRunning) return;
                if (isOnline) {
                    triggerPing();
                }
                pingHandler.postDelayed(this, 10000);
            }
        };
        pingHandler.post(pingRunnable);
    }

    private void stopPingMeasurement() {
        isPingRunning = false;
        if (pingRunnable != null) {
            pingHandler.removeCallbacks(pingRunnable);
            pingRunnable = null;
        }
    }

    private void syncUser() {
        if (currentUser != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
            userRef.child("email").setValue(currentUser.getEmail());
            userRef.child("appVersion").setValue(BuildConfig.VERSION_NAME);
        }
    }

    private void updateUI() {
        if (currentUser != null) {
            Button btnProfile = findViewById(R.id.btnProfile);
            FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid()).child("isAdmin")
                .get().addOnSuccessListener(snapshot -> {
                    if (isFinishing()) return;
                    Boolean isAdmin = snapshot.getValue(Boolean.class);
                    btnProfile.setText(Boolean.TRUE.equals(isAdmin) ? "АДМИН-ПАНЕЛЬ" : "ПРОФИЛЬ");
                });
        }
    }

    private void setupRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600).build();
        remoteConfig.setConfigSettingsAsync(configSettings);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("min_version_code", 1L);
        defaults.put("apk_download_url", "");
        defaults.put("force_update", false);
        defaults.put("latest_version_name", "1.0");
        remoteConfig.setDefaultsAsync(defaults);
    }

    private void checkUpdate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && !isFinishing()) {
                long minVersion = remoteConfig.getLong("min_version_code");
                String updateUrl = remoteConfig.getString("apk_download_url");
                if (minVersion > BuildConfig.VERSION_CODE && !updateUrl.isEmpty()) {
                    showUpdateDialog(updateUrl, remoteConfig.getBoolean("force_update"), remoteConfig.getString("latest_version_name"));
                }
            }
        });
    }

    private void showUpdateDialog(String url, boolean isForce, String versionName) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Доступна версия " + versionName)
                .setMessage("Пожалуйста, обновите приложение.")
                .setPositiveButton("Обновить", (d, w) -> updateManager.downloadAndInstall(url));
        if (isForce) builder.setCancelable(false);
        else builder.setNegativeButton("Позже", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth.getCurrentUser() != null && userStatusRef != null) {
            userStatusRef.child("isOnline").setValue(true);
        }
        startPingMeasurement();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPingMeasurement();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectedRef != null && connectionListener != null) {
            connectedRef.removeEventListener(connectionListener);
        }
        stopPingMeasurement();
    }
}
