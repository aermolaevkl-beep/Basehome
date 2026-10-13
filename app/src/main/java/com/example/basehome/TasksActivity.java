package com.example.basehome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
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

    private static final String TAG = "TasksActivity";

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
    private boolean refreshAddressContextOnResume = false;
    private boolean addressContextRefreshInProgress = false;
    private final java.util.concurrent.atomic.AtomicBoolean fetchInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);
    private Call activeTasksCall;
    private Bundle savedInstanceStateRef;

    private static List<TaskDateGroup> cachedDateGroups = null;
    private static Map<String, ArrayList<String>> cachedAddressHistoryMap = null;
    private static Map<String, TaskAdapter.PhysicalAddressContext> cachedPhysicalAddressContextMap = null;
    private static String cachedUserNameSession = "";

    public static void clearSessionMemoryCache() {
        cachedDateGroups = null;
        cachedAddressHistoryMap = null;
        cachedPhysicalAddressContextMap = null;
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

        taskAdapter = new TaskAdapter(this::createPhysicalAddressFromTask);
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
            if (manualRefreshInProgress) {
                Toast.makeText(this, "Обновление уже выполняется", Toast.LENGTH_SHORT).show();
                return;
            }
            clearSessionMemoryCache();
            manualRefreshInProgress = true;
            btnRefreshTasks.setEnabled(false);
            tvLoadingMessage.setText("Проверка профиля и обновление задач...");
            llLoadingContainer.setVisibility(View.VISIBLE);
            refreshTasksForSignedInUser();
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
            taskAdapter.setPhysicalAddressContextMap(cachedPhysicalAddressContextMap);
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
    protected void onResume() {
        super.onResume();
        if (refreshAddressContextOnResume && isShowingTasksForDate && !addressContextRefreshInProgress) {
            refreshAddressContextOnResume = false;
            refreshPhysicalAddressContext();
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

    private void refreshTasksForSignedInUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            currentUserName = "";
            manualRefreshInProgress = false;
            fetchInProgress.set(false);
            btnRefreshTasks.setEnabled(true);
            llLoadingContainer.setVisibility(View.GONE);
            Toast.makeText(this, "Сессия входа не найдена. Войдите снова.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        final long firebaseStartTime = SystemClock.elapsedRealtime();
        Log.i(TAG, "Refresh profile lookup started; uid=" + user.getUid()
                + ", email=" + user.getEmail());
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        Log.i(TAG, "Timing: Firebase profile lookup for refresh = "
                                + (SystemClock.elapsedRealtime() - firebaseStartTime) + " ms");
                        String profileName = snapshot.getValue(String.class);
                        String resolvedName = profileName != null && !profileName.trim().isEmpty()
                                ? profileName.trim()
                                : (user.getEmail() != null ? user.getEmail() : "");
                        if (resolvedName.isEmpty()) {
                            finishManualRefresh("Не удалось определить имя пользователя. Обновление отменено.");
                            return;
                        }

                        currentUserName = resolvedName;
                        getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                                .putString("saved_user_name", resolvedName)
                                .apply();
                        Log.i(TAG, "Refreshing tasks for signed-in uid=" + user.getUid()
                                + ", profileName=" + resolvedName);
                        fetchTasksFromGoogleSheets();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;
                        Log.w(TAG, "Refresh profile lookup cancelled: " + error.getMessage());
                        finishManualRefresh("Не удалось проверить профиль. Обновление отменено.");
                    }
                });
    }

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

        final long firebaseStartTime = SystemClock.elapsedRealtime();
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.i(TAG, "Timing: Firebase profile lookup = "
                                + (SystemClock.elapsedRealtime() - firebaseStartTime) + " ms");
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
                        Log.i(TAG, "Timing: Firebase profile lookup failed after "
                                + (SystemClock.elapsedRealtime() - firebaseStartTime) + " ms");
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

    private static final String DEFAULT_ADDRESS_DATABASE_CITY = "Могилев";

    public static void clearAddressMemoryCache() {
        // History records are fetched from Room by indexed house keys for every task refresh.
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
                    processAndDisplayTasks(rawRows, SystemClock.elapsedRealtime());
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
        final long httpStartTime = SystemClock.elapsedRealtime();
        activeTasksCall = HTTP_CLIENT.newCall(new Request.Builder().url(url).build());
        activeTasksCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.i(TAG, "Timing: HTTP request to Google Apps Script failed after "
                        + (SystemClock.elapsedRealtime() - httpStartTime) + " ms");
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
                Log.i(TAG, "Timing: HTTP request to Google Apps Script = "
                        + (SystemClock.elapsedRealtime() - httpStartTime) + " ms (status "
                        + response.code() + ")");
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

                final long responseProcessingStartTime = SystemClock.elapsedRealtime();
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        Log.i(TAG, "Timing: response processing = 0 ms (empty body)");
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

                    long bodyReadStartTime = SystemClock.elapsedRealtime();
                    String rawContent = body.string();
                    long bodyReadMs = SystemClock.elapsedRealtime() - bodyReadStartTime;
                    long jsonParseStartTime = SystemClock.elapsedRealtime();
                    List<String> rawRows = new ArrayList<>();
                    try (JsonReader reader = new JsonReader(new StringReader(rawContent))) {
                        reader.setLenient(true);
                        recursiveParse(reader, rawRows);
                    }
                    long jsonParseMs = SystemClock.elapsedRealtime() - jsonParseStartTime;
                    long cacheWriteStartTime = SystemClock.elapsedRealtime();
                    getSharedPreferences("tasks_cache", MODE_PRIVATE).edit().putString("raw_rows_json", rawContent).apply();
                    long cacheWriteMs = SystemClock.elapsedRealtime() - cacheWriteStartTime;
                    Log.i(TAG, "Timing: response bytes=" + rawContent.length()
                            + ", rows=" + rawRows.size()
                            + ", bodyRead=" + bodyReadMs + " ms"
                            + ", jsonParse=" + jsonParseMs + " ms"
                            + ", cacheWrite=" + cacheWriteMs + " ms");

                    executor.execute(() -> processAndDisplayTasks(rawRows, responseProcessingStartTime));

                } catch (Exception e) {
                    Log.i(TAG, "Timing: response processing failed after "
                            + (SystemClock.elapsedRealtime() - responseProcessingStartTime) + " ms");
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
        fetchInProgress.set(false);
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

    private void processAndDisplayTasks(List<String> rawRows, long responseProcessingStartTime) {
        long rowParseStartTime = SystemClock.elapsedRealtime();
        int parsedRows = 0;
        int matchedRows = 0;
        List<TaskItem> matchedTasks = new ArrayList<>();
        for (String row : rawRows) {
            TaskItem item = TaskItem.fromRowString(row);
            if (item != null) {
                parsedRows++;
                if (TaskItem.matchesUser(item.getInstallerName(), currentUserName)) {
                    String selectedCity = CityManager.getSelectedCity(this);
                    matchedTasks.add(item.withCity(TaskItem.resolveCity(item.getCity(), selectedCity)));
                    matchedRows++;
                }
            }
        }
        long rowParseAndFilterMs = SystemClock.elapsedRealtime() - rowParseStartTime;

        long groupingStartTime = SystemClock.elapsedRealtime();
        List<TaskDateGroup> dateGroups = groupTasksByDate(matchedTasks);
        long groupingMs = SystemClock.elapsedRealtime() - groupingStartTime;

        long historyStartTime = SystemClock.elapsedRealtime();
        Map<String, ArrayList<String>> addressHistoryMap = buildAddressHistoryMap(matchedTasks);
        long historyMs = SystemClock.elapsedRealtime() - historyStartTime;
        Map<String, TaskAdapter.PhysicalAddressContext> physicalAddressContextMap =
                buildPhysicalAddressContextMap(matchedTasks);

        Log.i(TAG, "Timing: rowParseAndFilter=" + rowParseAndFilterMs + " ms"
                + ", parsedRows=" + parsedRows + ", matchedTasks=" + matchedRows
                + ", grouping=" + groupingMs + " ms"
                + ", addressHistory=" + historyMs + " ms");
        mainHandler.post(() -> applyProcessedTasks(
                matchedTasks, dateGroups, addressHistoryMap, physicalAddressContextMap,
                responseProcessingStartTime));
    }

    private Map<String, ArrayList<String>> buildAddressHistoryMap(List<TaskItem> matchedTasks) {
        Map<String, ArrayList<String>> addressHistoryMap = new HashMap<>();
        long historyBuildStartTime = SystemClock.elapsedRealtime();
        long roomQueryMs = 0L;
        int targetHouseKeyCount = 0;
        int queriedEntityCount = 0;
        try {
            Set<String> targetHouseKeys = new HashSet<>();
            for (TaskItem task : matchedTasks) {
                String houseKey = GoogleAddress.parseStreetHouseKey(task.getAddress());
                if (!houseKey.isEmpty()) {
                    targetHouseKeys.add(houseKey);
                }
            }

            targetHouseKeyCount = targetHouseKeys.size();
            if (targetHouseKeys.isEmpty()) {
                Log.i(TAG, "Timing: addressHistory skipped; tasks=" + matchedTasks.size()
                        + ", no parseable house keys");
                return addressHistoryMap;
            }

            long roomQueryStartTime = SystemClock.elapsedRealtime();
            List<GoogleAddressEntity> entities = new ArrayList<>();
            Map<String, List<String>> houseKeysByCity = new LinkedHashMap<>();
            for (TaskItem task : matchedTasks) {
                String houseKey = GoogleAddress.parseStreetHouseKey(task.getAddress());
                if (houseKey.isEmpty()) continue;
                String city = TaskItem.resolveCity(task.getCity(), CityManager.getSelectedCity(this));
                houseKeysByCity.computeIfAbsent(city, ignored -> new ArrayList<>()).add(houseKey);
            }
            for (Map.Entry<String, List<String>> entry : houseKeysByCity.entrySet()) {
                List<GoogleAddressEntity> cityEntities = AppDatabase.getInstance(getApplicationContext())
                        .googleAddressDao()
                        .getEntitiesForCityAndHouseKeys(entry.getKey(), entry.getValue());
                if (cityEntities != null) entities.addAll(cityEntities);
            }
            roomQueryMs = SystemClock.elapsedRealtime() - roomQueryStartTime;
            queriedEntityCount = entities.size();

            for (GoogleAddressEntity entity : entities) {
                List<String> cityHouseKeys = houseKeysByCity.get(entity.getCity());
                if (cityHouseKeys == null) continue;
                String fullText = entity.getFullText();
                if (fullText == null || fullText.trim().isEmpty()) continue;

                // The database import already stores normalized street+house and
                // apartment fields. Use those instead of re-parsing every historical
                // fullText row (which also contains date, installer and other metadata).
                String houseKey = entity.getStreetHouseKey();
                if (houseKey.isEmpty() || !cityHouseKeys.contains(houseKey)) continue;
                String fullKey = GoogleAddress.getFullAddressKey(houseKey, entity.getApartment());
                String cityKey = TaskItem.cityAddressKey(entity.getCity(), houseKey);

                addAddressVisit(addressHistoryMap, TaskItem.cityAddressKey(entity.getCity(), fullKey), fullText);
                addAddressVisit(addressHistoryMap, cityKey, fullText);
            }
        } catch (Exception e) {
            Log.w(TAG, "Address history lookup failed", e);
        } finally {
            Log.i(TAG, "Timing: addressHistoryBuild="
                    + (SystemClock.elapsedRealtime() - historyBuildStartTime) + " ms"
                    + ", roomQuery=" + roomQueryMs + " ms"
                    + ", targetHouseKeys=" + targetHouseKeyCount
                    + ", queriedEntities=" + queriedEntityCount
                    + ", matchedHistoryKeys=" + addressHistoryMap.size());
        }
        return addressHistoryMap;
    }

    private void addAddressVisit(Map<String, ArrayList<String>> historyMap, String key, String visit) {
        if (key == null || key.isEmpty()) return;
        ArrayList<String> visits = historyMap.get(key);
        if (visits == null) {
            visits = new ArrayList<>();
            historyMap.put(key, visits);
        }
        visits.add(visit);
    }

    private void createPhysicalAddressFromTask(String streetHouse, String taskCity) {
        refreshAddressContextOnResume = true;
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.task_physical_address_create_no_user, Toast.LENGTH_LONG).show();
            return;
        }

        GoogleAddress parsed = new GoogleAddress(streetHouse);
        String cleanStreetHouse = parsed.getStreetHouse();
        if (cleanStreetHouse.isEmpty() || !GoogleAddress.isValidAddressRow(cleanStreetHouse)) {
            Toast.makeText(this, R.string.task_physical_address_parse_error, Toast.LENGTH_LONG).show();
            return;
        }

        String parsedStreet = cleanStreetHouse;
        String parsedHouse = "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(.*?)[, ]+((?:дом\\s*)?\\d+[\\p{L}]?(?:[-/]\\d+[\\p{L}]?)?)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(cleanStreetHouse.trim());
        if (matcher.matches()) {
            parsedStreet = matcher.group(1) == null ? "" : matcher.group(1).trim();
            parsedHouse = matcher.group(2) == null ? "" : matcher.group(2).trim().replaceFirst("(?i)^дом\\s*", "");
        }
        if (parsedStreet.isEmpty() || parsedHouse.isEmpty()) {
            Toast.makeText(this, R.string.task_physical_address_parse_error, Toast.LENGTH_LONG).show();
            return;
        }

        String city = TaskItem.normalizeExportCity(taskCity);
        if (city.isEmpty() || CityManager.ALL_CITIES.equalsIgnoreCase(city)) {
            city = TaskItem.normalizeExportCity(CityManager.getSelectedCity(this));
        }
        if (city.isEmpty() || CityManager.ALL_CITIES.equalsIgnoreCase(city)) {
            Toast.makeText(this, R.string.task_physical_address_city_missing, Toast.LENGTH_LONG).show();
            return;
        }
        final String selectedCity = city;
        final String street = parsedStreet;
        final String house = parsedHouse;
        final String displayAddress = street + ", " + house;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.task_physical_address_create_title)
                .setMessage(getString(R.string.task_physical_address_confirm, displayAddress, selectedCity))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String houseKey = GoogleAddress.parseStreetHouseKey(street, house);
                    FirebaseDatabase.getInstance().getReference("addresses").get()
                            .addOnSuccessListener(snapshot -> {
                                if (isFinishing() || isDestroyed()) return;
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    Address existing = child.getValue(Address.class);
                                    if (existing == null) continue;
                                    String existingCity = existing.getCity() == null
                                            ? DEFAULT_ADDRESS_DATABASE_CITY : existing.getCity();
                                    String existingKey = GoogleAddress.parseStreetHouseKey(
                                            existing.getStreet(), existing.getHouse());
                                    if (selectedCity.equalsIgnoreCase(existingCity)
                                            && houseKey.equals(existingKey)) {
                                        openPhysicalAddress(child.getKey());
                                        return;
                                    }
                                }
                                createPhysicalAddress(street, house, selectedCity, user.getUid());
                            })
                            .addOnFailureListener(error -> Toast.makeText(this,
                                    getString(R.string.task_physical_address_create_failed,
                                            error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage()),
                                    Toast.LENGTH_LONG).show());
                })
                .show();
    }

    private void createPhysicalAddress(String street, String house, String city, String userId) {
        DatabaseReference addresses = FirebaseDatabase.getInstance().getReference("addresses");
        String addressId = addresses.push().getKey();
        if (addressId == null) return;

        FirebaseDatabase.getInstance().getReference("users").child(userId).child("name").get()
                .addOnSuccessListener(userSnapshot -> {
                    String userName = userSnapshot.getValue(String.class);
                    FirebaseUser currentUser = auth.getCurrentUser();
                    if (userName == null || userName.trim().isEmpty()) {
                        userName = currentUser != null && currentUser.getEmail() != null
                                ? currentUser.getEmail() : getString(R.string.anonymous);
                    }
                    Address address = new Address(street, house, city, userId, userName);
                    addresses.child(addressId).setValue(address)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, R.string.task_physical_address_created, Toast.LENGTH_SHORT).show();
                                openPhysicalAddress(addressId);
                            })
                            .addOnFailureListener(error -> Toast.makeText(this,
                                    getString(R.string.task_physical_address_create_failed,
                                            error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage()),
                                    Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        getString(R.string.task_physical_address_create_failed,
                                error.getLocalizedMessage() == null ? "" : error.getLocalizedMessage()),
                        Toast.LENGTH_LONG).show());
    }

    public void openPhysicalAddressFromTask(String addressId) {
        openPhysicalAddress(addressId);
    }

    private void openPhysicalAddress(String addressId) {
        refreshAddressContextOnResume = true;
        Intent intent = new Intent(this, AddressDetailActivity.class);
        intent.putExtra("addressId", addressId);
        startActivity(intent);
    }

    private void refreshPhysicalAddressContext() {
        if (addressContextRefreshInProgress || isFinishing() || isDestroyed()) return;
        addressContextRefreshInProgress = true;
        List<TaskItem> visibleTasks = getVisibleTaskItems();
        new Thread(() -> {
            Map<String, TaskAdapter.PhysicalAddressContext> refreshed =
                    buildPhysicalAddressContextMap(visibleTasks);
            mainHandler.post(() -> {
                addressContextRefreshInProgress = false;
                if (isFinishing() || isDestroyed()) return;
                cachedPhysicalAddressContextMap = refreshed;
                taskAdapter.setPhysicalAddressContextMap(refreshed);
            });
        }, "address-context-refresh").start();
    }

    private List<TaskItem> getVisibleTaskItems() {
        List<TaskItem> tasks = new ArrayList<>();
        if (cachedDateGroups != null) {
            for (TaskDateGroup group : cachedDateGroups) {
                tasks.addAll(group.getTasks());
            }
        }
        String selectedCity = CityManager.getSelectedCity(this);
        List<TaskItem> normalizedTasks = new ArrayList<>(tasks.size());
        for (TaskItem task : tasks) {
            normalizedTasks.add(task.withCity(TaskItem.resolveCity(task.getCity(), selectedCity)));
        }
        return normalizedTasks;
    }

    private Map<String, TaskAdapter.PhysicalAddressContext> buildPhysicalAddressContextMap(
            List<TaskItem> matchedTasks) {
        Map<String, TaskAdapter.PhysicalAddressContext> result = new HashMap<>();
        Map<String, Set<String>> taskHouseKeysByCity = new LinkedHashMap<>();
        for (TaskItem task : matchedTasks) {
            String key = GoogleAddress.parseStreetHouseKey(task.getAddress());
            if (key.isEmpty()) continue;
            String city = TaskItem.resolveCity(task.getCity(), CityManager.getSelectedCity(this));
            taskHouseKeysByCity.computeIfAbsent(city, ignored -> new HashSet<>()).add(key);
        }
        if (taskHouseKeysByCity.isEmpty()) return result;

        try {
            DataSnapshot addressSnapshot = Tasks.await(
                    FirebaseDatabase.getInstance().getReference("addresses").get(),
                    10, TimeUnit.SECONDS);
            Map<String, Address> matchedAddresses = new LinkedHashMap<>();
            for (DataSnapshot child : addressSnapshot.getChildren()) {
                Address address = child.getValue(Address.class);
                if (address == null) continue;
                String key = GoogleAddress.parseStreetHouseKey(address.getStreet(), address.getHouse());
                if (key.isEmpty()) continue;
                boolean addressHasCity = address.getCity() != null && !address.getCity().trim().isEmpty();
                String city = findMatchingTaskCity(taskHouseKeysByCity, address, key);
                if (city == null) continue;
                if (!addressHasCity) address.setCity(city);
                address.setId(child.getKey());
                String cityAddressKey = TaskItem.cityAddressKey(city, key);
                matchedAddresses.put(cityAddressKey, address);
            }

            Map<String, com.google.android.gms.tasks.Task<DataSnapshot>> entranceTasks = new LinkedHashMap<>();
            Map<String, com.google.android.gms.tasks.Task<DataSnapshot>> commentTasks = new LinkedHashMap<>();
            for (Map.Entry<String, Address> entry : matchedAddresses.entrySet()) {
                String id = entry.getValue().getId();
                if (id == null || id.isEmpty()) continue;
                entranceTasks.put(entry.getKey(), FirebaseDatabase.getInstance().getReference("entrances")
                        .child(id).get());
                commentTasks.put(entry.getKey(), FirebaseDatabase.getInstance().getReference("comments")
                        .child(id).get());
            }
            for (com.google.android.gms.tasks.Task<DataSnapshot> task : entranceTasks.values()) {
                task.addOnFailureListener(error -> Log.w(TAG, "Could not load address entrances", error));
            }
            for (com.google.android.gms.tasks.Task<DataSnapshot> task : commentTasks.values()) {
                task.addOnFailureListener(error -> Log.w(TAG, "Could not load address comments", error));
            }

            for (Map.Entry<String, Address> entry : matchedAddresses.entrySet()) {
                String key = entry.getKey();
                Address address = entry.getValue();
                String id = address.getId();
                if (id == null || id.isEmpty()) continue;
                com.google.android.gms.tasks.Task<DataSnapshot> entranceTask = entranceTasks.get(key);
                com.google.android.gms.tasks.Task<DataSnapshot> commentTask = commentTasks.get(key);
                if (entranceTask == null || commentTask == null) continue;
                DataSnapshot entrances;
                DataSnapshot comments;
                try {
                    entrances = Tasks.await(entranceTask, 10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Log.w(TAG, "Could not load entrances for address " + id, e);
                    entrances = null;
                }
                try {
                    comments = Tasks.await(commentTask, 10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Log.w(TAG, "Could not load comments for address " + id, e);
                    comments = null;
                }

                List<String> entranceDetails = new ArrayList<>();
                for (DataSnapshot entranceNode : entrances == null
                        ? new ArrayList<DataSnapshot>() : entrances.getChildren()) {
                    Entrance entrance = entranceNode.getValue(Entrance.class);
                    if (entrance == null) continue;
                    StringBuilder detail = new StringBuilder("Подъезд ")
                            .append(entrance.getNumber() == null ? "?" : entrance.getNumber());
                    if (entrance.getIntercomCode() != null && !entrance.getIntercomCode().trim().isEmpty()) {
                        detail.append(" · домофон: ").append(entrance.getIntercomCode().trim());
                    }
                    if (entrance.getCrossFloor() != null && !entrance.getCrossFloor().trim().isEmpty()) {
                        detail.append(" · этаж кросса: ").append(entrance.getCrossFloor().trim());
                    }
                    if (entrance.getBasementKey() != null && !entrance.getBasementKey().trim().isEmpty()) {
                        detail.append(" · подвал: ").append(entrance.getBasementKey().trim());
                    }
                    entranceDetails.add(detail.toString());
                }
                entranceDetails.sort(String::compareToIgnoreCase);

                List<Comment> addressComments = new ArrayList<>();
                for (DataSnapshot commentNode : comments == null
                        ? new ArrayList<DataSnapshot>() : comments.getChildren()) {
                    Comment comment = commentNode.getValue(Comment.class);
                    if (comment != null && comment.getText() != null && !comment.getText().trim().isEmpty()) {
                        addressComments.add(comment);
                    }
                }
                addressComments.sort((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()));
                List<String> detailLines = new ArrayList<>(entranceDetails);
                int commentsToShow = Math.min(addressComments.size(), 2);
                for (int i = 0; i < commentsToShow; i++) {
                    String text = addressComments.get(i).getText().trim().replaceAll("\\s+", " ");
                    if (text.length() > 100) text = text.substring(0, 97) + "…";
                    detailLines.add("Комментарий: " + text);
                }
                if (detailLines.isEmpty()) detailLines.add("Подъезды и комментарии пока не добавлены");

                String label = (address.getStreet() == null ? "" : address.getStreet()) + ", "
                        + (address.getHouse() == null ? "" : address.getHouse());
                TaskAdapter.PhysicalAddressContext context = new TaskAdapter.PhysicalAddressContext(
                        id, label, android.text.TextUtils.join("\n", detailLines));
                result.put(key, context);
            }
        } catch (Exception e) {
            Log.w(TAG, "Physical address lookup failed", e);
        }
        return result;
    }

    private String findMatchingTaskCity(Map<String, Set<String>> houseKeysByCity, Address address, String houseKey) {
        String addressCity = TaskItem.normalizeExportCity(address.getCity());
        for (Map.Entry<String, Set<String>> entry : houseKeysByCity.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(addressCity)
                    && entry.getValue().contains(houseKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void applyProcessedTasks(List<TaskItem> matchedTasks, List<TaskDateGroup> dateGroups,
                                     Map<String, ArrayList<String>> addressHistoryMap,
                                     Map<String, TaskAdapter.PhysicalAddressContext> physicalAddressContextMap,
                                     long responseProcessingStartTime) {
        Log.i(TAG, "Timing: response processing (parse + task filtering/grouping + display prep) = "
                + (SystemClock.elapsedRealtime() - responseProcessingStartTime) + " ms");
        hasDisplayedTasks = true;
        fetchInProgress.set(false);

        // Save to session memory cache
        cachedDateGroups = dateGroups;
        cachedAddressHistoryMap = addressHistoryMap;
        cachedPhysicalAddressContextMap = physicalAddressContextMap;
        cachedUserNameSession = currentUserName;

        if (manualRefreshInProgress) {
            manualRefreshInProgress = false;
            btnRefreshTasks.setEnabled(true);
            Toast.makeText(this, "Актуальная версия загружена", Toast.LENGTH_SHORT).show();
        }
        llLoadingContainer.setVisibility(View.GONE);
        taskAdapter.setAddressHistoryMap(addressHistoryMap);
        taskAdapter.setPhysicalAddressContextMap(physicalAddressContextMap);
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
