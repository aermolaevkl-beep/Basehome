package com.example.basehome;

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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    
    private static List<String> cachedRows = null;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private Future<?> searchTask;
    private AppDatabase db;

    private static final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbwKWZ4aC1wkE7yvkijqdNZVaOPx0K25JWwh6BmV7G_DvryUmqE3hsuzakqdrSVeHTWR/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_database);

        db = AppDatabase.getInstance(this);
        etSearch = findViewById(R.id.etSearchDatabase);
        llLoadingContainer = findViewById(R.id.llLoadingContainer);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);
        RecyclerView rvResults = findViewById(R.id.rvDatabaseResults);
        ImageButton btnBack = findViewById(R.id.btnBackDatabase);
        ImageButton btnUpdate = findViewById(R.id.btnUpdateDatabase);

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GoogleAddressAdapter(new ArrayList<>());
        rvResults.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnUpdate.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Обновление")
                .setMessage("Загрузить свежую базу из Google Таблиц? Это может занять время.")
                .setPositiveButton("Да", (d, w) -> loadDataFromNetwork())
                .setNegativeButton("Отмена", null)
                .show();
        });

        if (cachedRows != null && !cachedRows.isEmpty()) {
            llLoadingContainer.setVisibility(View.GONE);
            etSearch.setEnabled(true);
        } else {
            loadFromLocalDb();
        }

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
            List<String> localData = db.googleAddressDao().getAllAddresses();
            runOnUiThread(() -> {
                if (localData != null && !localData.isEmpty()) {
                    cachedRows = localData;
                    llLoadingContainer.setVisibility(View.GONE);
                    etSearch.setEnabled(true);
                } else {
                    loadDataFromNetwork();
                }
            });
        });
    }

    private void loadDataFromNetwork() {
        llLoadingContainer.setVisibility(View.VISIBLE);
        tvLoadingMessage.setText("Загрузка данных из Google Таблиц...");
        etSearch.setEnabled(false);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        client.newCall(new Request.Builder().url(SCRIPT_URL).build()).enqueue(new Callback() {
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
                    if (body == null) return;

                    List<String> temp = new ArrayList<>(65000);
                    try (JsonReader reader = new JsonReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                        reader.setLenient(true);
                        recursiveParse(reader, temp);
                    }

                    // Сохраняем в Room в фоновом потоке
                    executor.execute(() -> {
                        db.googleAddressDao().deleteAll();
                        List<GoogleAddressEntity> entities = new ArrayList<>();
                        for (String row : temp) {
                            entities.add(new GoogleAddressEntity(row));
                        }
                        db.googleAddressDao().insertAll(entities);
                        
                        cachedRows = temp;
                        runOnUiThread(() -> {
                            llLoadingContainer.setVisibility(View.GONE);
                            etSearch.setEnabled(true);
                            Toast.makeText(GoogleDatabaseActivity.this, "База обновлена и сохранена!", Toast.LENGTH_SHORT).show();
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
                if (inner == JsonToken.BEGIN_OBJECT) {
                    StringBuilder sb = new StringBuilder();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        reader.nextName();
                        appendValue(reader, sb);
                    }
                    reader.endObject();
                    if (sb.length() > 0) list.add(sb.toString());
                } else if (inner == JsonToken.BEGIN_ARRAY) {
                    StringBuilder sb = new StringBuilder();
                    reader.beginArray();
                    while (reader.hasNext()) appendValue(reader, sb);
                    reader.endArray();
                    if (sb.length() > 0) list.add(sb.toString());
                } else if (inner == JsonToken.STRING || inner == JsonToken.NUMBER) {
                    String val = reader.nextString();
                    if (val != null && !val.isEmpty()) list.add(val);
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

    private void performSimpleSearch(String query) {
        if (searchTask != null) searchTask.cancel(true);
        if (cachedRows == null) return;

        final String input = query.trim().toLowerCase(Locale.getDefault());
        
        searchTask = executor.submit(() -> {
            List<GoogleAddress> result = new ArrayList<>();
            if (!input.isEmpty()) {
                String[] words = input.split("\\s+");
                for (String row : cachedRows) {
                    if (Thread.interrupted()) return;
                    
                    int limit = Math.min(row.length(), 70);
                    String searchArea = row.substring(0, limit).toLowerCase(Locale.getDefault());
                    
                    boolean match = true;
                    for (int i = 0; i < words.length; i++) {
                        String word = words[i];
                        if (word.isEmpty()) continue;
                        
                        int pos = searchArea.indexOf(word);

                        if (i == 0 && (pos == -1 || pos > 35)) {
                            match = false;
                            break;
                        }

                        if (word.matches("\\d+")) {
                            boolean foundValid = false;
                            while (pos != -1) {
                                if (isValidHouseNumber(searchArea, word, pos)) {
                                    foundValid = true;
                                    break;
                                }
                                pos = searchArea.indexOf(word, pos + 1);
                            }
                            if (!foundValid) { match = false; break; }
                        } else {
                            if (pos == -1) { match = false; break; }
                        }
                    }
                    if (match) {
                        result.add(new GoogleAddress(row));
                        if (result.size() >= 500) break;
                    }
                }
            }
            mainHandler.post(() -> adapter.updateList(result));
        });
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
        super.onDestroy();
        executor.shutdownNow();
    }
}
