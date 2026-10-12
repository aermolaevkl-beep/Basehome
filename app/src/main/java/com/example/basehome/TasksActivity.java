package com.example.basehome;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TasksActivity extends AppCompatActivity {

    private LinearLayout llLoadingContainer;
    private TextView tvLoadingMessage, tvEmptyTasks, tvToolbarTitle;
    private ImageButton btnRefreshTasks;
    private RecyclerView rvTasks;
    private TaskAdapter taskAdapter;
    private TaskDateAdapter dateAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FirebaseAuth auth;
    private String currentUserName = "";
    private boolean isShowingTasksForDate = false;
    private volatile boolean hasDisplayedTasks = false;
    private boolean hasCachedTasks = false;
    private boolean manualRefreshInProgress = false;
    private final java.util.concurrent.atomic.AtomicBoolean fetchInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);
    private Call activeTasksCall;
    private Bundle savedInstanceStateRef;

    private static List<TaskDateGroup> cachedDateGroups = null;
    private static Map<String, ArrayList<String>> cachedAddressHistoryMap = null;
    private static String cachedUserNameSession = "";

    public static void clearSessionMemoryCache() {
        cachedDateGroups = null;
        cachedAddressHistoryMap = null;
        cachedUserNameSession = "";
    }

    private static final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzBs8rnv_krynJVIXX3jDWF2RlWm4f0vS852NIGt23gxUMPZB0JACVcDWa4WCNhkjxO/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        savedInstanceStateRef = savedInstanceState;
        setContentView(R.layout.activity_tasks);
        SwipeBackHelper.attach(this);

        auth = FirebaseAuth.getInstance();

        ImageButton btnBack = findViewById(R.id.btnBackTasks);
        btnRefreshTasks = findViewById(R.id.btnRefreshTasks);
        llLoadingContainer = findViewById(R.id.llLoadingContainerTasks);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessageTasks);
        tvEmptyTasks = findViewById(R.id.tvEmptyTasks);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitleTasks);
        rvTasks = findViewById(R.id.rvTasks);

        rvTasks.setLayoutManager(new LinearLayoutManager(this));

        taskAdapter = new TaskAdapter();
        dateAdapter = new TaskDateAdapter(dateGroup -> {
            // Show tasks for selected date
            isShowingTasksForDate = true;
            if (tvToolbarTitle != null) {
                tvToolbarTitle.setText(dateGroup.getDate());
            }
            taskAdapter.updateTasks(dateGroup.getTasks());
            rvTasks.setAdapter(taskAdapter);
            tvEmptyTasks.setVisibility(dateGroup.getTasks().isEmpty() ? View.VISIBLE : View.GONE);
            rvTasks.setVisibility(dateGroup.getTasks().isEmpty() ? View.GONE : View.VISIBLE);
        });

        rvTasks.setAdapter(dateAdapter);

        if (savedInstanceState != null) {
            isShowingTasksForDate = savedInstanceState.getBoolean("isShowingTasksForDate", false);
            String savedTitle = savedInstanceState.getString("toolbarTitle", "Мои задачи");
            if (tvToolbarTitle != null) {
                tvToolbarTitle.setText(savedTitle);
            }
        }

        btnBack.setOnClickListener(v -> handleBackAction());
        btnRefreshTasks.setOnClickListener(v -> {
            if (fetchInProgress.get()) {
                Toast.makeText(this, "Загрузка уже выполняется", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentUserName.isEmpty()) {
                Toast.makeText(this, "Не удалось определить пользователя", Toast.LENGTH_SHORT).show();
                return;
            }
            clearSessionMemoryCache();
            manualRefreshInProgress = true;
            btnRefreshTasks.setEnabled(false);
            tvLoadingMessage.setText("Обновление задач...");
            llLoadingContainer.setVisibility(View.VISIBLE);
            if (auth.getCurrentUser() != null) {
                loadUserProfileAndTasks();
            } else {
                fetchTasksFromGoogleSheets();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackAction();
            }
        });

        currentUserName = getSharedPreferences("user_prefs", MODE_PRIVATE).getString("saved_user_name", "");
        hasDisplayedTasks = false;

        if (cachedDateGroups != null && !cachedDateGroups.isEmpty() && cachedUserNameSession.equals(currentUserName)) {
            // Instant session memory cache display (0 wait, 0 white screen, 0 loading)
            hasDisplayedTasks = true;
            llLoadingContainer.setVisibility(View.GONE);
            taskAdapter.setAddressHistoryMap(cachedAddressHistoryMap);
            dateAdapter.updateDateGroups(cachedDateGroups);

            if (isShowingTasksForDate && tvToolbarTitle != null) {
                String currentTitle = tvToolbarTitle.getText().toString();
                TaskDateGroup targetGroup = null;
                for (TaskDateGroup group : cachedDateGroups) {
                    if (group.getDate().equals(currentTitle)) {
                        targetGroup = group;
                        break;
                    }
                }
                if (targetGroup != null) {
                    taskAdapter.updateTasks(targetGroup.getTasks());
                    rvTasks.setAdapter(taskAdapter);
                    tvEmptyTasks.setVisibility(targetGroup.getTasks().isEmpty() ? View.VISIBLE : View.GONE);
                    rvTasks.setVisibility(targetGroup.getTasks().isEmpty() ? View.GONE : View.VISIBLE);
                } else {
                    rvTasks.setAdapter(dateAdapter);
                    tvEmptyTasks.setVisibility(cachedDateGroups.isEmpty() ? View.VISIBLE : View.GONE);
                    rvTasks.setVisibility(cachedDateGroups.isEmpty() ? View.GONE : View.VISIBLE);
                }
            } else {
                rvTasks.setAdapter(dateAdapter);
                if (cachedDateGroups.isEmpty()) {
                    tvEmptyTasks.setText("Нет задач для вашего профиля");
                    tvEmptyTasks.setVisibility(View.VISIBLE);
                    rvTasks.setVisibility(View.GONE);
                } else {
                    tvEmptyTasks.setVisibility(View.GONE);
                    rvTasks.setVisibility(View.VISIBLE);
                }
            }
        } else {
            String cachedContent = getSharedPreferences("tasks_cache", MODE_PRIVATE)
                    .getString("raw_rows_json", null);
            hasCachedTasks = cachedContent != null && !cachedContent.isEmpty();

            if (hasCachedTasks) {
                llLoadingContainer.setVisibility(View.VISIBLE);
                tvLoadingMessage.setText("Загрузка сохранённых задач...");
                loadCachedTasksIfAvailable();
            } else {
                llLoadingContainer.setVisibility(View.VISIBLE);
                tvLoadingMessage.setText("Загрузка задач...");
                loadUserProfileAndTasks();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isShowingTasksForDate", isShowingTasksForDate);
        if (tvToolbarTitle != null) {
            outState.putString("toolbarTitle", tvToolbarTitle.getText().toString());
        }
    }

    @Override
    protected void onDestroy() {
        if (activeTasksCall != null) {
            activeTasksCall.cancel();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void handleBackAction() {
        if (isShowingTasksForDate) {
            // Return to date list view
            isShowingTasksForDate = false;
            if (tvToolbarTitle != null) {
                tvToolbarTitle.setText("Мои задачи");
            }
            rvTasks.setAdapter(dateAdapter);
            tvEmptyTasks.setVisibility(dateAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            rvTasks.setVisibility(dateAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        } else {
            finish();
        }
    }

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private void loadUserProfileAndTasks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Сначала войдите в систему", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!hasDisplayedTasks && !hasCachedTasks) {
            llLoadingContainer.setVisibility(View.VISIBLE);
            tvLoadingMessage.setText("Загрузка задач...");
        }

        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        String resolvedName = name != null ? name : (user.getEmail() != null ? user.getEmail() : "");
                        if (!resolvedName.isEmpty()) {
                            currentUserName = resolvedName;
                            getSharedPreferences("user_prefs", MODE_PRIVATE).edit().putString("saved_user_name", resolvedName).apply();
                        }
                        fetchTasksFromGoogleSheets();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (currentUserName.isEmpty()) {
                            currentUserName = user.getEmail() != null ? user.getEmail() : "";
                        }
                        if (!hasDisplayedTasks) {
                            loadCachedTasksIfAvailable();
                        }
                        fetchTasksFromGoogleSheets();
                    }
                });
    }

    private static List<GoogleAddressEntity> memoryCachedEntities = null;
    private static final Object addressCacheLock = new Object();

    public static void clearAddressMemoryCache() {
        synchronized (addressCacheLock) {
            memoryCachedEntities = null;
        }
    }

    private void loadCachedTasksIfAvailable() {
        String cachedContent = getSharedPreferences("tasks_cache", MODE_PRIVATE).getString("raw_rows_json", null);
        if (cachedContent != null && !cachedContent.isEmpty()) {
            // Load cache on background thread to prevent any ANR / main thread blocking
            executor.execute(() -> {
                try {
                    List<String> rawRows = new ArrayList<>();
                    try (JsonReader reader = new JsonReader(new StringReader(cachedContent))) {
                        reader.setLenient(true);
                        recursiveParse(reader, rawRows);
                    }
                    processAndDisplayTasks(rawRows);
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        llLoadingContainer.setVisibility(View.GONE);
                        rvTasks.setVisibility(View.GONE);
                        tvEmptyTasks.setText("Не удалось прочитать сохранённые задачи. Нажмите обновить.");
                        tvEmptyTasks.setVisibility(View.VISIBLE);
                    });
                }
            });
        } else {
            llLoadingContainer.setVisibility(View.GONE);
            tvEmptyTasks.setVisibility(View.VISIBLE);
        }
    }

    private String buildTasksUrl() {
        return Uri.parse(SCRIPT_URL)
                .buildUpon()
                .appendQueryParameter("city", "выезда")
                .appendQueryParameter("sheet", "выезда")
                .build()
                .toString();
    }

    private void fetchTasksFromGoogleSheets() {
        if (!fetchInProgress.compareAndSet(false, true)) return;
        if (!hasDisplayedTasks && !hasCachedTasks) {
            llLoadingContainer.setVisibility(View.VISIBLE);
            tvLoadingMessage.setText("Загрузка задач...");
        } else if (manualRefreshInProgress) {
            tvLoadingMessage.setText("Обновление задач...");
        }

        String url = buildTasksUrl();
        activeTasksCall = HTTP_CLIENT.newCall(new Request.Builder().url(url).build());
        activeTasksCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    fetchInProgress.set(false);
                    if (manualRefreshInProgress) {
                        finishManualRefresh("Не удалось обновить данные. Сохранённая версия оставлена.");
                    } else if (!hasDisplayedTasks) {
                        llLoadingContainer.setVisibility(View.GONE);
                        rvTasks.setVisibility(View.GONE);
                        tvEmptyTasks.setText("Не удалось загрузить задачи. Проверьте интернет и нажмите обновить.");
                        tvEmptyTasks.setVisibility(View.VISIBLE);
                        Toast.makeText(TasksActivity.this, "Ошибка сети: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> {
                        fetchInProgress.set(false);
                        if (manualRefreshInProgress) {
                            finishManualRefresh("Не удалось обновить данные. Сохранённая версия оставлена.");
                        } else if (!hasDisplayedTasks) {
                            llLoadingContainer.setVisibility(View.GONE);
                            rvTasks.setVisibility(View.GONE);
                            tvEmptyTasks.setText("Сервер задач недоступен. Нажмите обновить позже.");
                            tvEmptyTasks.setVisibility(View.VISIBLE);
                            Toast.makeText(TasksActivity.this, "Ошибка сервера Google: " + response.code(), Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        mainHandler.post(() -> {
                            fetchInProgress.set(false);
                            if (manualRefreshInProgress) {
                                finishManualRefresh("Не удалось обновить данные. Сохранённая версия оставлена.");
                            } else if (!hasDisplayedTasks) {
                                llLoadingContainer.setVisibility(View.GONE);
                                tvEmptyTasks.setVisibility(View.VISIBLE);
                            }
                        });
                        return;
                    }

                    String rawContent = body.string();
                    List<String> rawRows = new ArrayList<>();
                    try (JsonReader reader = new JsonReader(new StringReader(rawContent))) {
                        reader.setLenient(true);
                        recursiveParse(reader, rawRows);
                    }
                    getSharedPreferences("tasks_cache", MODE_PRIVATE).edit().putString("raw_rows_json", rawContent).apply();

                    executor.execute(() -> processAndDisplayTasks(rawRows));

                } catch (Exception e) {
                    mainHandler.post(() -> {
                        fetchInProgress.set(false);
                        if (manualRefreshInProgress) {
                            finishManualRefresh("Ошибка обработки обновлённых данных");
                        } else if (!hasDisplayedTasks) {
                            llLoadingContainer.setVisibility(View.GONE);
                            Toast.makeText(TasksActivity.this, "Ошибка обработки данных: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            tvEmptyTasks.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    private void finishManualRefresh(String message) {
        manualRefreshInProgress = false;
        btnRefreshTasks.setEnabled(true);
        fetchInProgress.set(false);
        llLoadingContainer.setVisibility(View.GONE);
        if (!hasDisplayedTasks) {
            rvTasks.setVisibility(View.GONE);
            tvEmptyTasks.setText("Не удалось загрузить задачи. Проверьте интернет и нажмите обновить.");
            tvEmptyTasks.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void processAndDisplayTasks(List<String> rawRows) {
        List<TaskItem> matchedTasks = new ArrayList<>();
        for (String row : rawRows) {
            TaskItem item = TaskItem.fromRowString(row);
            if (item != null) {
                if (TaskItem.matchesUser(item.getInstallerName(), currentUserName)) {
                    matchedTasks.add(item);
                }
            }
        }

        List<TaskDateGroup> dateGroups = groupTasksByDate(matchedTasks);
        Map<String, ArrayList<String>> addressHistoryMap = buildAddressHistoryMap(matchedTasks);

        mainHandler.post(() -> applyProcessedTasks(matchedTasks, dateGroups, addressHistoryMap));
    }

    private Map<String, ArrayList<String>> buildAddressHistoryMap(List<TaskItem> matchedTasks) {
        Map<String, ArrayList<String>> addressHistoryMap = new HashMap<>();
        try {
            Set<String> targetHouseKeys = new HashSet<>();
            for (TaskItem task : matchedTasks) {
                String hk = GoogleAddress.parseHouseKey(task.getAddress());
                if (!hk.isEmpty()) {
                    targetHouseKeys.add(hk);
                }
            }

            if (!targetHouseKeys.isEmpty()) {
                synchronized (addressCacheLock) {
                    if (memoryCachedEntities == null) {
                        AppDatabase db = AppDatabase.getInstance(TasksActivity.this);
                        memoryCachedEntities = db.googleAddressDao().getAllEntities();
                    }
                }
                List<GoogleAddressEntity> entities = memoryCachedEntities;
                if (entities != null) {
                    for (GoogleAddressEntity entity : entities) {
                        String fullText = entity.getFullText();
                        if (fullText == null || fullText.trim().isEmpty()) continue;

                        String entityHouseKey = entity.getStreetHouseKey();
                        boolean isRelevant = false;
                        for (String targetKey : targetHouseKeys) {
                            if ((entityHouseKey != null && entityHouseKey.contains(targetKey))
                                    || fullText.toLowerCase(Locale.ROOT).contains(targetKey)) {
                                isRelevant = true;
                                break;
                            }
                        }

                        if (isRelevant) {
                            String fullKey = GoogleAddress.parseFullAddressKey(fullText);
                            if (!fullKey.isEmpty()) {
                                ArrayList<String> visits = addressHistoryMap.get(fullKey);
                                if (visits == null) {
                                    visits = new ArrayList<>();
                                    addressHistoryMap.put(fullKey, visits);
                                }
                                visits.add(fullText);
                            }

                            String houseKey = GoogleAddress.parseHouseKey(fullText);
                            if (!houseKey.isEmpty() && !houseKey.equals(fullKey)) {
                                ArrayList<String> houseVisits = addressHistoryMap.get(houseKey);
                                if (houseVisits == null) {
                                    houseVisits = new ArrayList<>();
                                    addressHistoryMap.put(houseKey, houseVisits);
                                }
                                houseVisits.add(fullText);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return addressHistoryMap;
    }

    private void applyProcessedTasks(List<TaskItem> matchedTasks, List<TaskDateGroup> dateGroups, Map<String, ArrayList<String>> addressHistoryMap) {
        hasDisplayedTasks = true;
        fetchInProgress.set(false);

        // Save to session memory cache
        cachedDateGroups = dateGroups;
        cachedAddressHistoryMap = addressHistoryMap;
        cachedUserNameSession = currentUserName;

        if (manualRefreshInProgress) {
            manualRefreshInProgress = false;
            btnRefreshTasks.setEnabled(true);
            Toast.makeText(this, "Актуальная версия загружена", Toast.LENGTH_SHORT).show();
        }
        llLoadingContainer.setVisibility(View.GONE);
        taskAdapter.setAddressHistoryMap(addressHistoryMap);
        dateAdapter.updateDateGroups(dateGroups);

        if (isShowingTasksForDate && tvToolbarTitle != null) {
            String currentTitle = tvToolbarTitle.getText().toString();
            TaskDateGroup targetGroup = null;
            for (TaskDateGroup group : dateGroups) {
                if (group.getDate().equals(currentTitle)) {
                    targetGroup = group;
                    break;
                }
            }
            if (targetGroup != null) {
                taskAdapter.updateTasks(targetGroup.getTasks());
                rvTasks.setAdapter(taskAdapter);
                tvEmptyTasks.setVisibility(targetGroup.getTasks().isEmpty() ? View.VISIBLE : View.GONE);
                rvTasks.setVisibility(targetGroup.getTasks().isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                rvTasks.setAdapter(dateAdapter);
                tvEmptyTasks.setVisibility(dateGroups.isEmpty() ? View.VISIBLE : View.GONE);
                rvTasks.setVisibility(dateGroups.isEmpty() ? View.GONE : View.VISIBLE);
            }
        } else {
            rvTasks.setAdapter(dateAdapter);
            if (dateGroups.isEmpty()) {
                tvEmptyTasks.setText("Нет задач для вашего профиля");
                tvEmptyTasks.setVisibility(View.VISIBLE);
                rvTasks.setVisibility(View.GONE);
            } else {
                tvEmptyTasks.setVisibility(View.GONE);
                rvTasks.setVisibility(View.VISIBLE);
            }
        }
    }

    private List<TaskDateGroup> groupTasksByDate(List<TaskItem> tasks) {
        tasks.sort(Comparator.comparing(TaskItem::getDateTime));

        Map<String, List<TaskItem>> map = new LinkedHashMap<>();
        for (TaskItem task : tasks) {
            String dt = task.getDateTime();
            String dateKey = (dt.length() >= 10) ? dt.substring(0, 10) : "Другие";
            List<TaskItem> list = map.get(dateKey);
            if (list == null) {
                list = new ArrayList<>();
                map.put(dateKey, list);
            }
            list.add(task);
        }
        List<TaskDateGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<TaskItem>> entry : map.entrySet()) {
            groups.add(new TaskDateGroup(entry.getKey(), entry.getValue()));
        }
        groups.sort((g1, g2) -> g1.getDate().compareTo(g2.getDate()));
        return groups;
    }

    private void recursiveParse(JsonReader reader, List<String> list) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                recursiveParse(reader, list);
            }
            reader.endArray();
        } else if (token == JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            while (reader.hasNext()) {
                reader.nextName();
                recursiveParse(reader, list);
            }
            reader.endObject();
        } else if (token == JsonToken.STRING) {
            String val = reader.nextString();
            if (val != null && !val.trim().isEmpty()) {
                list.add(val.trim());
            }
        } else if (token == JsonToken.NUMBER) {
            list.add(String.valueOf(reader.nextDouble()));
        } else if (token == JsonToken.BOOLEAN) {
            list.add(String.valueOf(reader.nextBoolean()));
        } else if (token == JsonToken.NULL) {
            reader.nextNull();
        } else {
            reader.skipValue();
        }
    }
}
