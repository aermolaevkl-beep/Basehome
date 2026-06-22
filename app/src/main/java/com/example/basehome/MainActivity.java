package com.example.basehome;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private FirebaseRemoteConfig remoteConfig;
    private UpdateManager updateManager;

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

        ImageButton btnLogout = findViewById(R.id.btnLogout);
        Button btnProfile = findViewById(R.id.btnProfile);
        Button btnPhysicalSection = findViewById(R.id.btnPhysicalSection);
        Button btnBusinessSection = findViewById(R.id.btnBusinessSection);

        updateManager = new UpdateManager(this);
        setupRemoteConfig();
        checkUpdate();
        updateUI(btnProfile);

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        btnProfile.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        btnPhysicalSection.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PhysicalAddressActivity.class)));
        btnBusinessSection.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, BusinessAddressActivity.class)));
        
        syncUser();
    }

    private void setupRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0) 
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);

        Map<String, Object> defaultValues = new HashMap<>();
        defaultValues.put("min_version_code", "1");
        defaultValues.put("apk_download_url", "");
        defaultValues.put("force_update", "false");
        defaultValues.put("latest_version_name", "1.0");
        remoteConfig.setDefaultsAsync(defaultValues);
    }

    private void checkUpdate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                String minVersionStr = remoteConfig.getString("min_version_code");
                String updateUrl = remoteConfig.getString("apk_download_url");
                String forceUpdateStr = remoteConfig.getString("force_update");
                String latestName = remoteConfig.getString("latest_version_name");

                int currentVersion = BuildConfig.VERSION_CODE;
                try {
                    String digitsOnly = minVersionStr.replaceAll("[^0-9]", "");
                    if (!digitsOnly.isEmpty()) {
                        int minVersion = Integer.parseInt(digitsOnly);
                        boolean isForce = forceUpdateStr.equalsIgnoreCase("true");

                        if (minVersion > currentVersion && !updateUrl.isEmpty()) {
                            showUpdateDialog(updateUrl, isForce, latestName);
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void showUpdateDialog(String url, boolean isForce, String versionName) {
        String cleanName = "новая";
        if (versionName != null && !versionName.isEmpty()) {
            String firstLine = versionName.split("\n")[0].trim();
            cleanName = firstLine.replace("BaseHome:", "").replace("Value:", "").trim();
            if (cleanName.length() > 10) cleanName = "1.1"; 
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Доступна версия " + cleanName)
                .setMessage("Пожалуйста, обновите приложение для продолжения работы.")
                .setPositiveButton("Обновить", (dialog, which) -> {
                    if (url != null && !url.trim().isEmpty()) {
                        updateManager.downloadAndInstall(url.trim());
                    } else {
                        Toast.makeText(this, "Ссылка на обновление пуста", Toast.LENGTH_SHORT).show();
                    }
                });

        if (isForce) {
            builder.setCancelable(false);
        } else {
            builder.setNegativeButton("Позже", null);
        }

        builder.show();
    }

    private void syncUser() {
        if (currentUser != null) {
            FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid())
                .child("email").setValue(currentUser.getEmail());
        }
    }

    private void updateUI(Button btnProfile) {
        if (currentUser != null) {
            btnProfile.setVisibility(View.VISIBLE);
            FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid()).child("isAdmin")
                .get().addOnSuccessListener(snapshot -> {
                    Boolean isAdmin = snapshot.getValue(Boolean.class);
                    btnProfile.setText(isAdmin != null && isAdmin ? "АДМИН-ПАНЕЛЬ" : "ПРОФИЛЬ");
                });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
}
