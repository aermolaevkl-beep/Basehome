package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class GoogleDatabaseActivity extends AppCompatActivity {

    private EditText etSearch;
    private LinearLayout llLoadingContainer;
    private TextView tvLoadingMessage;
    private TextView tvDatabaseEmptyState;
    private GoogleAddressAdapter adapter;
    
    private static volatile List<GoogleAddress> cachedGroupedAddresses = null;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicInteger parsedAddressCount = new AtomicInteger();
    private final AtomicInteger skippedAddressCount = new AtomicInteger();
    private final AtomicInteger apartmentCount = new AtomicInteger();
    private final AtomicInteger missingAddressCount = new AtomicInteger();
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
        tvDatabaseEmptyState = findViewById(R.id.tvDatabaseEmptyState);
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
                .setMessage("Загрузить свежую базу для г. " + targetCity + " из Firebase?")
                .setPositiveButton("Да", (d, w) -> loadDataFromNetwork())
                .setNegativeButton("Отмена", null)
                .show();
        });

        if (cachedGroupedAddresses != null) {
            llLoadingContainer.setVisibility(View.GONE);
            etSearch.setEnabled(true);
        } else {
            tvDatabaseEmptyState.setText(R.string.database_loading_local);
            tvDatabaseEmptyState.setVisibility(View.VISIBLE);
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
        skippedAddressCount.set(0);
        apartmentCount.set(0);
        missingAddressCount.set(0);
        tvLoadingMessage.setText("Получено адресов: 0");
        mainHandler.removeCallbacks(progressUpdater);
        mainHandler.postDelayed(progressUpdater, 150);
        etSearch.setEnabled(false);

        DatabaseReference cityReference = FirebaseDatabase.getInstance()
                .getReference("database")
                .child(getFirebaseCityKey(targetCity));

        cityReference.get()
                .addOnSuccessListener(snapshot -> executor.execute(() -> {
                    long parseStartMs = android.os.SystemClock.elapsedRealtime();
                    int sourceRowCount = (int) snapshot.getChildrenCount();
                    List<GoogleAddressEntity> entities = new ArrayList<>(sourceRowCount);
                    for (DataSnapshot rowSnapshot : snapshot.getChildren()) {
                        String address = getChildText(rowSnapshot, "address");
                        if (address.isEmpty()) {
                            missingAddressCount.incrementAndGet();
                            continue;
                        }

                        String date = getChildText(rowSnapshot, "date");
                        String installer = getChildText(rowSnapshot, "installer");
                        String workType = getChildText(rowSnapshot, "work_type");
                        String account = getChildText(rowSnapshot, "account");
                        String apartment = getChildText(rowSnapshot, "apartment");
                        String addressWithApartment = address;
                        if (!apartment.isEmpty() && new GoogleAddress(address).getApartment().isEmpty()) {
                            addressWithApartment = address + ", кв. " + apartment;
                        }
                        String fullText = joinNonEmpty(addressWithApartment, date, installer, workType, account);

                        GoogleAddress parsedAddress = new GoogleAddress(addressWithApartment);
                        String house = parsedAddress.getStreetHouse();
                        String key = parsedAddress.getStreetHouseKey();
                        String parsedApartment = parsedAddress.getApartment();
                        if (house.isEmpty() || key.isEmpty()) {
                            skippedAddressCount.incrementAndGet();
                            continue;
                        }

                        entities.add(new GoogleAddressEntity(targetCity, fullText, house, key,
                                parsedApartment.isEmpty() ? apartment : parsedApartment));
                        if (!(parsedApartment.isEmpty() && apartment.isEmpty())) {
                            apartmentCount.incrementAndGet();
                        }
                        int count = parsedAddressCount.incrementAndGet();
                        if (count % 2000 == 0) {
                            mainHandler.post(() -> tvLoadingMessage.setText("Получено адресов: " + count));
                        }
                    }

                    long parseDurationMs = android.os.SystemClock.elapsedRealtime() - parseStartMs;
                    mainHandler.post(() -> tvLoadingMessage.setText(
                            "Загружено " + entities.size() + " адресов. Сохранение в базу..."));
                    long saveStartMs = android.os.SystemClock.elapsedRealtime();
                    db.runInTransaction(() -> {
                        db.googleAddressDao().deleteForCity(targetCity);
                        db.googleAddressDao().insertAll(entities);
                    });
                    long saveDurationMs = android.os.SystemClock.elapsedRealtime() - saveStartMs;
                    int savedCount = db.googleAddressDao().getCountForCity(targetCity);
                    TasksActivity.clearAddressMemoryCache();
                    cachedGroupedAddresses = groupAddressesFromEntities(entities);
                    final int finalSkippedCount = skippedAddressCount.get();
                    final int finalMissingAddressCount = missingAddressCount.get();
                    final int finalApartmentCount = apartmentCount.get();
                    final long finalParseDurationMs = parseDurationMs;
                    final long finalSaveDurationMs = saveDurationMs;
                    runOnUiThread(() -> {
                        mainHandler.removeCallbacks(progressUpdater);
                        llLoadingContainer.setVisibility(View.GONE);
                        etSearch.setEnabled(true);
                        performSimpleSearch(etSearch.getText().toString());
                        new AlertDialog.Builder(GoogleDatabaseActivity.this)
                                .setTitle("Отчёт обновления базы")
                                .setMessage("Город: " + targetCity
                                        + "\nСтрок в Firebase: " + sourceRowCount
                                        + "\nАдресов сохранено: " + savedCount
                                        + "\nИз них с квартирой: " + finalApartmentCount
                                        + "\nПропущено — не удалось разобрать адрес: " + finalSkippedCount
                                        + "\nПропущено — пустое поле address: " + finalMissingAddressCount
                                        + "\nРазбор: " + finalParseDurationMs + " мс"
                                        + "\nСохранение: " + finalSaveDurationMs + " мс")
                                .setPositiveButton("ОК", null)
                                .show();
                    });
                }))
                .addOnFailureListener(error -> showError("Не удалось загрузить базу Firebase: "
                        + error.getLocalizedMessage()))
                .addOnCanceledListener(() -> showError("Загрузка базы Firebase отменена"));
    }

    private String getFirebaseCityKey(String city) {
        String normalized = city == null ? "" : city.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("могилев") || normalized.equals("могилёв")) return "mogilev";
        if (normalized.equals("бобруйск")) return "bobruisk";
        if (normalized.equals("горки")) return "gorki";
        if (normalized.equals("орша")) return "orsha";

        StringBuilder sb = new StringBuilder();
        for (char c : normalized.replace('ё', 'е').toCharArray()) {
            switch (c) {
                case 'а': sb.append("a"); break;
                case 'б': sb.append("b"); break;
                case 'в': sb.append("v"); break;
                case 'г': sb.append("g"); break;
                case 'д': sb.append("d"); break;
                case 'е': case 'э': sb.append("e"); break;
                case 'ж': sb.append("zh"); break;
                case 'з': sb.append("z"); break;
                case 'и': case 'й': sb.append("i"); break;
                case 'к': sb.append("k"); break;
                case 'л': sb.append("l"); break;
                case 'м': sb.append("m"); break;
                case 'н': sb.append("n"); break;
                case 'о': sb.append("o"); break;
                case 'п': sb.append("p"); break;
                case 'р': sb.append("r"); break;
                case 'с': sb.append("s"); break;
                case 'т': sb.append("t"); break;
                case 'у': sb.append("u"); break;
                case 'ф': sb.append("f"); break;
                case 'х': sb.append("kh"); break;
                case 'ц': sb.append("ts"); break;
                case 'ч': sb.append("ch"); break;
                case 'ш': sb.append("sh"); break;
                case 'щ': sb.append("shch"); break;
                case 'ы': sb.append("y"); break;
                case 'ю': sb.append("yu"); break;
                case 'я': sb.append("ya"); break;
                case 'ь': case 'ъ': break;
                default:
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(c);
                    } else {
                        sb.append("_");
                    }
                    break;
            }
        }
        return sb.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
    }

    private String getChildText(DataSnapshot snapshot, String childName) {
        Object value = snapshot.child(childName).getValue();
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String joinNonEmpty(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(", ");
            result.append(value.trim().replace('\u00A0', ' '));
        }
        return result.toString();
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
        if (source == null) {
            adapter.updateList(new ArrayList<>());
            tvDatabaseEmptyState.setText(R.string.database_loading_local);
            tvDatabaseEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        final String input = query.trim().toLowerCase(Locale.ROOT);
        if (source.isEmpty()) {
            adapter.updateList(new ArrayList<>());
            tvDatabaseEmptyState.setText(R.string.database_empty);
            tvDatabaseEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        if (input.isEmpty()) {
            adapter.updateList(new ArrayList<>());
            tvDatabaseEmptyState.setText(R.string.database_search_prompt);
            tvDatabaseEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        tvDatabaseEmptyState.setVisibility(View.GONE);
        searchTask = executor.submit(() -> {
            List<GoogleAddress> result = new ArrayList<>();
            String[] words = input.split("\\s+");
            for (GoogleAddress address : source) {
                if (Thread.currentThread().isInterrupted()) return;
                String searchArea = address.getStreetHouse().toLowerCase(Locale.ROOT);
                boolean match = true;

                for (String word : words) {
                    if (word.isEmpty()) continue;

                    if (word.contains("-")) {
                        String[] parts = word.split("-");
                        for (String part : parts) {
                            if (part.matches("\\d+")) {
                                if (!containsWholeHouseNumber(searchArea, part)
                                        && !containsVisitNumber(address.getVisits(), part)) {
                                    match = false;
                                    break;
                                }
                            } else if (!searchArea.contains(part)) {
                                match = false;
                                break;
                            }
                        }
                    } else if (word.matches("\\d+")) {
                        if (!containsWholeHouseNumber(searchArea, word)) {
                            match = false;
                            break;
                        }
                    } else if (!searchArea.contains(word)) {
                        match = false;
                        break;
                    }
                }
                if (match) result.add(address);
            }

            result.sort((a1, a2) -> compareHouses(a1.getStreetHouse(), a2.getStreetHouse()));
            mainHandler.post(() -> {
                adapter.updateList(result);
                if (!result.isEmpty()) {
                    tvDatabaseEmptyState.setVisibility(View.GONE);
                } else {
                    tvDatabaseEmptyState.setText(R.string.database_no_results);
                    tvDatabaseEmptyState.setVisibility(View.VISIBLE);
                }
            });
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
        if (pos > 0 && Character.isDigit(text.charAt(pos - 1))) return false;
        if (end < text.length()) {
            char next = text.charAt(end);
            // A matching base house number also includes numbered buildings like 68.1/68.2.
            if (Character.isDigit(next)) return false;
            if (next == '.' && end + 1 < text.length()
                    && Character.isDigit(text.charAt(end + 1))) return true;
        }
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
