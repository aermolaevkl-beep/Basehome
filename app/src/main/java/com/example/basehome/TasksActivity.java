package com.example.basehome;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TasksActivity extends AppCompatActivity {

    private LinearLayout llLoadingContainer;
    private TextView tvLoadingMessage, tvEmptyTasks;
    private RecyclerView rvTasks;
    private TaskAdapter taskAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FirebaseAuth auth;
    private String currentUserName = "";

    private static final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzBs8rnv_krynJVIXX3jDWF2RlWm4f0vS852NIGt23gxUMPZB0JACVcDWa4WCNhkjxO/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        auth = FirebaseAuth.getInstance();

        ImageButton btnBack = findViewById(R.id.btnBackTasks);
        llLoadingContainer = findViewById(R.id.llLoadingContainerTasks);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessageTasks);
        tvEmptyTasks = findViewById(R.id.tvEmptyTasks);
        rvTasks = findViewById(R.id.rvTasks);

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter();
        rvTasks.setAdapter(taskAdapter);

        btnBack.setOnClickListener(v -> finish());

        loadUserProfileAndTasks();
    }

    private void loadUserProfileAndTasks() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Сначала войдите в систему", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        llLoadingContainer.setVisibility(View.VISIBLE);
        tvLoadingMessage.setText("Загрузка профиля...");

        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        currentUserName = name != null ? name : (user.getEmail() != null ? user.getEmail() : "");
                        fetchTasksFromGoogleSheets();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        currentUserName = user.getEmail() != null ? user.getEmail() : "";
                        fetchTasksFromGoogleSheets();
                    }
                });
    }

    private void fetchTasksFromGoogleSheets() {
        tvLoadingMessage.setText("Загрузка задач...");

        OkHttpClient client = new OkHttpClient.Builder().build();
        String url = SCRIPT_URL;
        try {
            url = SCRIPT_URL + "?sheet=" + java.net.URLEncoder.encode("выезда", StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    llLoadingContainer.setVisibility(View.GONE);
                    Toast.makeText(TasksActivity.this, "Ошибка сети: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> {
                        llLoadingContainer.setVisibility(View.GONE);
                        Toast.makeText(TasksActivity.this, "Ошибка сервера Google: " + response.code(), Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        mainHandler.post(() -> {
                            llLoadingContainer.setVisibility(View.GONE);
                            tvEmptyTasks.setVisibility(View.VISIBLE);
                        });
                        return;
                    }

                    String rawContent = body.string();
                    List<String> rawRows = new ArrayList<>();
                    try (JsonReader reader = new JsonReader(new StringReader(rawContent))) {
                        reader.setLenient(true);
                        recursiveParse(reader, rawRows);
                    }

                    executor.execute(() -> {
                        List<TaskItem> matchedTasks = new ArrayList<>();
                        for (String row : rawRows) {
                            TaskItem item = TaskItem.fromRowString(row);
                            if (item != null) {
                                if (TaskItem.matchesUser(item.getInstallerName(), currentUserName)) {
                                    matchedTasks.add(item);
                                }
                            }
                        }

                        mainHandler.post(() -> {
                            llLoadingContainer.setVisibility(View.GONE);
                            taskAdapter.updateTasks(matchedTasks);
                            if (matchedTasks.isEmpty()) {
                                tvEmptyTasks.setVisibility(View.VISIBLE);
                                rvTasks.setVisibility(View.GONE);
                            } else {
                                tvEmptyTasks.setVisibility(View.GONE);
                                rvTasks.setVisibility(View.VISIBLE);
                            }
                        });
                    });

                } catch (Exception e) {
                    mainHandler.post(() -> {
                        llLoadingContainer.setVisibility(View.GONE);
                        Toast.makeText(TasksActivity.this, "Ошибка обработки данных: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        tvEmptyTasks.setVisibility(View.VISIBLE);
                    });
                }
            }
        });
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
        } else if (token != JsonToken.NULL) {
            reader.skipValue();
        }
    }
}
