package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GoogleDatabaseActivity extends AppCompatActivity {

    private EditText etSearch;
    private LinearLayout llLoadingContainer;
    private TextView tvLoadingMessage;
    private GoogleAddressAdapter adapter;
    
    private static volatile List<GoogleAddress> cachedGroupedAddresses = null;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicInteger parsedAddressCount = new AtomicInteger();
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (llLoadingContainer != null
                    && llLoadingContainer.getVisibility() == View.VISIBLE) {
                tvLoadingMessage.setText("Получено адресов: " + parsedAddressCount.get());
                mainHandler.postDelayed(this, 150);
            }
        }
    };
    private Future<?> searchTask;
    private AppDatabase db;
    private String targetCity = "Могилев";

    private static final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzBs8rnv_krynJVIXX3jDWF2RlWm4f0vS852NIGt23gxUMPZB0JACVcDWa4WCNhkjxO/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_database);

        db = AppDatabase.getInstance(this);
        String extraCity = getIntent().getStringExtra("city");
        if (extraCity != null && !extraCity.trim().isEmpty() && !CityManager.ALL_CITIES.equalsIgnoreCase(extraCity)) {
            targetCity = extraCity.trim();
        }

        etSearch = findViewById(R.id.etSearchDatabase);
        llLoadingContainer = findViewById(R.id.llLoadingContainer);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);
        RecyclerView rvResults = findViewById(R.id.rvDatabaseResults);
        ImageButton btnBack = findViewById(R.id.btnBackDatabase);
        ImageButton btnUpdate = findViewById(R.id.btnUpdateDatabase);

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GoogleAddressAdapter(new ArrayList<>(), true, this::showAddressVisits);
        rvResults.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnUpdate.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Обновление")
                .setMessage("Загрузить свежую базу для г. " + targetCity + " из Google Таблиц?")
                .setPositiveButton("Да", (d, w) -> loadDataFromNetwork())
                .setNegativeButton("Отмена", null)
                .show();
        });

        if (cachedGroupedAddresses != null) {
            llLoadingContainer.setVisibility(View.GONE);
            etSearch.setEnabled(true);
        } else {
            loadFromLocalDb();
        }
        etSearch.post(() -> performSimpleSearch(etSearch.getText().toString()));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mainHandler.removeCallbacksAndMessages(null);
                mainHandler.postDelayed(() -> performSimpleSearch(s.toString()), 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadFromLocalDb() {
        llLoadingContainer.setVisibility(View.VISIBLE);
        tvLoadingMessage.setText("Чтение локальной базы...");
        etSearch.setEnabled(false);

        executor.execute(() -> {
            List<GoogleAddressEntity> entities = db.googleAddressDao().getEntitiesForCity(targetCity);
            if (entities != null && !entities.isEmpty()) {
                List<GoogleAddressEntity> validEntities = new ArrayList<>(entities.size());
                for (GoogleAddressEntity entity : entities) {
                    String fullText = entity.getFullText();
                    String key = entity.getStreetHouseKey();
                    String house = entity.getStreetHouse();
                    if (key.isEmpty() || house.isEmpty()) {
                        GoogleAddress ga = new GoogleAddress(fullText);
                        house = ga.getStreetHouse();
                        key = ga.getStreetHouseKey();
                        if (!house.isEmpty() && !key.isEmpty()) {
                            entity.setStreetHouse(house);
                            entity.setStreetHouseKey(key);
                            entity.setApartment(ga.getApartment());
                        }
                    }
                    if (!key.isEmpty()) {
                        validEntities.add(entity);
                    }
                }
                cachedGroupedAddresses = groupAddressesFromEntities(validEntities);
                runOnUiThread(() -> {
                    llLoadingContainer.setVisibility(View.GONE);
                    etSearch.setEnabled(true);
                    performSimpleSearch(etSearch.getText().toString());
                });
            } else {
                runOnUiThread(this::loadDataFromNetwork);
            }
        });
    }

    private void loadDataFromNetwork() {
        llLoadingContainer.setVisibility(View.VISIBLE);
        parsedAddressCount.set(0);
        tvLoadingMessage.setText("Получено адресов: 0");
        mainHandler.removeCallbacks(progressUpdater);
        mainHandler.postDelayed(progressUpdater, 150);
        etSearch.setEnabled(false);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        String url = SCRIPT_URL;
        try {
            url = SCRIPT_URL + "?city=" + java.net.URLEncoder.encode(targetCity, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showError("Ошибка сети: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    showError("Ошибка сервера Google: " + response.code());
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        showError("Пустой ответ от сервера Google");
                        return;
                    }

                    String rawContent = body.string();
                    String trimmed = rawContent.trim();
                    if (trimmed.startsWith("<") || trimmed.toLowerCase(Locale.ROOT).contains("<html")) {
                        showError("Гугл Скрипт вернул HTML-страницу вместо JSON.\nПроверьте права доступа в Google Apps Script: должно стоять 'Кто имеет доступ' -> 'Все пользователи' (Anyone).");
                        return;
                    }

                    List<String> temp = new ArrayList<>(65000);
                    try (JsonReader reader = new JsonReader(new StringReader(trimmed))) {
                        reader.setLenient(true);
                        recursiveParse(reader, temp);
                    }

                    int totalRows = temp.size();
                    mainHandler.removeCallbacks(progressUpdater);
                    mainHandler.post(() -> tvLoadingMessage.setText(
                            "Получено " + totalRows + " адресов. Сохранение в базу..."));

                    executor.execute(() -> {
                        List<GoogleAddressEntity> entities = new ArrayList<>(temp.size());
                        for (String row : temp) {
                            GoogleAddress ga = new GoogleAddress(row);
                            String house = ga.getStreetHouse();
                            if (house.isEmpty()) continue;
                            String key = ga.getStreetHouseKey();
                            if (key.isEmpty()) continue;
                            entities.add(new GoogleAddressEntity(targetCity, row, house, key, ga.getApartment()));
                        }

                        db.runInTransaction(() -> {
                            db.googleAddressDao().deleteForCity(targetCity);
                            db.googleAddressDao().insertAll(entities);
                        });
                        
                        cachedGroupedAddresses = groupAddressesFromEntities(entities);
                        runOnUiThread(() -> {
                            llLoadingContainer.setVisibility(View.GONE);
                            etSearch.setEnabled(true);
                            performSimpleSearch(etSearch.getText().toString());
                            Toast.makeText(GoogleDatabaseActivity.this, "База для г. " + targetCity + " обновлена!", Toast.LENGTH_SHORT).show();
                        });
                    });

                } catch (Exception e) {
                    showError("Ошибка данных: " + e.getMessage());
                }
            }
        });
    }

    private void recursiveParse(JsonReader reader, List<String> list) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                JsonToken inner = reader.peek();
                if (inner == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    StringBuilder sb = new StringBuilder();
                    while (reader.hasNext()) {
                        JsonToken t = reader.peek();
                        if (t == JsonToken.STRING || t == JsonToken.NUMBER) {
                            String s = reader.nextString();
                            if (s != null && !s.trim().isEmpty()) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(s.trim().replace('\u00A0', ' '));
                            }
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endArray();
                    if (sb.length() > 0) addParsedRow(list, sb.toString());
                } else if (inner == JsonToken.BEGIN_OBJECT) {
                    StringBuilder sb = new StringBuilder();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        reader.nextName();
                        appendValue(reader, sb);
                    }
                    reader.endObject();
                    if (sb.length() > 0) addParsedRow(list, sb.toString());
                } else if (inner == JsonToken.STRING || inner == JsonToken.NUMBER) {
                    String val = reader.nextString();
                    if (val != null && !val.trim().isEmpty()) addParsedRow(list, val.trim());
                } else {
                    reader.skipValue();
                }
            }
            reader.endArray();
        } else if (token == JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                recursiveParse(reader, list);
                if (!list.isEmpty()) break;
            }
            reader.endObject();
        } else {
            reader.skipValue();
        }
    }

    private void addParsedRow(List<String> list, String row) {
        if (row == null) return;
        String trimmed = row.trim();
        if (trimmed.length() < 3) return;

        list.add(trimmed);
        int count = parsedAddressCount.incrementAndGet();
        if (count % 2000 == 0) {
            mainHandler.post(() -> tvLoadingMessage.setText("Получено адресов: " + count));
        }
    }

    private void appendValue(JsonReader reader, StringBuilder sb) throws IOException {
        JsonToken token = reader.peek();
        String val = null;
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
            val = reader.nextString();
        } else if (token == JsonToken.BOOLEAN) {
            val = String.valueOf(reader.nextBoolean());
        } else if (token == JsonToken.NULL) {
            reader.nextNull();
        } else {
            reader.skipValue();
        }
        if (sb != null && val != null && !val.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(val.trim().replace('\u00A0', ' '));
        }
    }

    private void showAddressVisits(GoogleAddress address) {
        Intent intent = new Intent(this, GoogleAddressDetailActivity.class);
        intent.putExtra(GoogleAddressDetailActivity.EXTRA_ADDRESS, address.getStreetHouse());
        intent.putStringArrayListExtra(GoogleAddressDetailActivity.EXTRA_VISITS, address.getVisits());
        startActivity(intent);
    }

    private List<GoogleAddress> groupAddressesFromEntities(List<GoogleAddressEntity> entities) {
        java.util.LinkedHashMap<String, GoogleAddress> grouped = new java.util.LinkedHashMap<>();
        for (GoogleAddressEntity entity : entities) {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<>();
            String addressKey = entity.getStreetHouseKey();
            if (addressKey.isEmpty()) continue;

            GoogleAddress address = grouped.get(addressKey);
            if (address == null) {
                address = new GoogleAddress(entity.getFullText(), entity.getStreetHouse(),
                        entity.getStreetHouseKey(), entity.getApartment(), new ArrayList<>());
                grouped.put(addressKey, address);
            }
            address.getVisits().add(entity.getFullText());
        }
        return new ArrayList<>(grouped.values());
    }

    private void performSimpleSearch(String query) {
        if (searchTask != null) searchTask.cancel(true);
        List<GoogleAddress> source = cachedGroupedAddresses;
        if (source == null) return;

        final String input = query.trim().toLowerCase(Locale.ROOT);
        searchTask = executor.submit(() -> {
            List<GoogleAddress> result = new ArrayList<>();
            if (!input.isEmpty()) {
                String[] words = input.split("\\s+");
                for (GoogleAddress address : source) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String searchArea = address.getStreetHouse().toLowerCase(Locale.ROOT);
                    boolean match = true;
                    
                    for (String word : words) {
                        if (word.isEmpty()) continue;
                        
                        if (word.contains("-")) {
                            String[] parts = word.split("-");
                            for (String p : parts) {
                                if (p.matches("\\d+")) {
                                    if (!containsWholeHouseNumber(searchArea, p) && !containsVisitNumber(address.getVisits(), p)) {
                                        match = false;
                                        break;
                                    }
                                } else if (!searchArea.contains(p)) {
                                    match = false;
                                    break;
                                }
                            }
                        } else if (word.matches("\\d+")) {
                            if (!containsWholeHouseNumber(searchArea, word)) {
                                match = false;
                                break;
                            }
                        } else {
                            if (!searchArea.contains(word)) {
                                match = false;
                                break;
                            }
                        }
                    }
                    if (match) result.add(address);
                }
                
                result.sort((a1, a2) -> compareHouses(a1.getStreetHouse(), a2.getStreetHouse()));
            }
            mainHandler.post(() -> adapter.updateList(result));
        });
    }

    private static int compareHouses(String h1, String h2) {
        String street1 = h1.replaceAll("\\d+.*", "").trim();
        String street2 = h2.replaceAll("\\d+.*", "").trim();
        int streetComp = street1.compareToIgnoreCase(street2);
        if (streetComp != 0) return streetComp;

        int num1 = extractHouseNumber(h1);
        int num2 = extractHouseNumber(h2);
        if (num1 != num2) return Integer.compare(num1, num2);

        return h1.compareToIgnoreCase(h2);
    }

    private static int extractHouseNumber(String address) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d+)\\b").matcher(address);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private boolean containsVisitNumber(List<String> visits, String number) {
        for (String v : visits) {
            if (containsWholeHouseNumber(v.toLowerCase(Locale.ROOT), number)) return true;
        }
        return false;
    }

    private boolean containsWholeHouseNumber(String text, String word) {
        int pos = text.indexOf(word);
        while (pos >= 0) {
            if (isValidHouseNumber(text, word, pos)) return true;
            pos = text.indexOf(word, pos + 1);
        }
        return false;
    }

    private boolean isValidHouseNumber(String text, String word, int pos) {
        int end = pos + word.length();
        if (pos > 0 && text.charAt(pos - 1) == '.') return false;
        if (end < text.length() && text.charAt(end) == '.') return false;
        if (pos > 0 && Character.isDigit(text.charAt(pos - 1))) return false;
        if (end < text.length() && Character.isDigit(text.charAt(end))) return false;
        return true;
    }

    private void showError(String msg) {
        runOnUiThread(() -> {
            llLoadingContainer.setVisibility(View.GONE);
            etSearch.setEnabled(true);
            new AlertDialog.Builder(this)
                .setTitle("Загрузка")
                .setMessage(msg)
                .setPositiveButton("ОК", null)
                .show();
        });
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(progressUpdater);
        super.onDestroy();
        executor.shutdownNow();
    }
}
