package com.example.basehome;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
    private DatabaseReference userStatusRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        vConnectionIndicator = findViewById(R.id.vConnectionIndicator);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        vConnectionIndicator.setBackground(shape);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            if (userStatusRef != null) userStatusRef.child("isOnline").setValue(false);
            auth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.btnProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btnPhysicalSection).setOnClickListener(v -> startActivity(new Intent(this, PhysicalAddressActivity.class)));
        findViewById(R.id.btnBusinessSection).setOnClickListener(v -> startActivity(new Intent(this, BusinessAddressActivity.class)));
        findViewById(R.id.btnDatabase).setOnClickListener(v -> startActivity(new Intent(this, GoogleDatabaseActivity.class)));
        
        updateManager = new UpdateManager(this);
        setupRemoteConfig();
        checkUpdate();
        updateUI(findViewById(R.id.btnProfile));
        observeConnectionStatus();
        setupPresence(); 
        calculateGlobalRanks(); 
        syncUser();
    }

    private void calculateGlobalRanks() {
        FirebaseDatabase.getInstance().getReference("addresses").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Integer> counts = new HashMap<>();
                for (DataSnapshot item : snapshot.getChildren()) {
                    String uid = item.child("userId").getValue(String.class);
                    if (uid != null && !uid.isEmpty()) {
                        counts.put(uid, counts.getOrDefault(uid, 0) + 1);
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

    private void setupPresence() {
        if (currentUser == null) return;
        userStatusRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                if (connected) {
                    userStatusRef.child("isOnline").setValue(true);
                    userStatusRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP);
                    userStatusRef.child("isOnline").onDisconnect().setValue(false);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void observeConnectionStatus() {
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                if (connected) {
                    ((GradientDrawable)vConnectionIndicator.getBackground()).setColor(
                            ContextCompat.getColor(MainActivity.this, android.R.color.holo_green_light));
                    tvConnectionStatus.setText("Онлайн");
                } else {
                    ((GradientDrawable)vConnectionIndicator.getBackground()).setColor(
                            ContextCompat.getColor(MainActivity.this, android.R.color.holo_red_light));
                    tvConnectionStatus.setText("Офлайн");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void syncUser() {
        if (currentUser != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
            userRef.child("email").setValue(currentUser.getEmail());
            userRef.child("appVersion").setValue(BuildConfig.VERSION_NAME);
        }
    }

    private void updateUI(Button btnProfile) {
        if (currentUser != null) {
            FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid()).child("isAdmin")
                .get().addOnSuccessListener(snapshot -> {
                    Boolean isAdmin = snapshot.getValue(Boolean.class);
                    btnProfile.setText(isAdmin != null && isAdmin ? "АДМИН-ПАНЕЛЬ" : "ПРОФИЛЬ");
                });
        }
    }

    private void setupRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0).build();
        remoteConfig.setConfigSettingsAsync(configSettings);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("min_version_code", "1");
        defaults.put("apk_download_url", "");
        defaults.put("force_update", "false");
        defaults.put("latest_version_name", "1.0");
        remoteConfig.setDefaultsAsync(defaults);
    }

    private void checkUpdate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                String minVersionStr = remoteConfig.getString("min_version_code");
                String updateUrl = remoteConfig.getString("apk_download_url");
                int currentVersion = BuildConfig.VERSION_CODE;
                try {
                    int minVersion = Integer.parseInt(minVersionStr.replaceAll("[^0-9]", ""));
                    if (minVersion > currentVersion && !updateUrl.isEmpty()) {
                        showUpdateDialog(updateUrl, remoteConfig.getBoolean("force_update"), remoteConfig.getString("latest_version_name"));
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void showUpdateDialog(String url, boolean isForce, String versionName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
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
    }
}
