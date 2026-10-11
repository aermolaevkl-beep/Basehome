package com.example.basehome;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private final Context context;
    private long downloadId;

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void downloadAndInstall(String url) {
        String fileName = "Basehome_Update.apk";
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
        
        if (file.exists()) {
            file.delete();
        }

        Log.d(TAG, "Starting download from URL: " + url);

        DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Загрузка обновления")
                    .setDescription("Basehome скачивает новую версию...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true);
        } catch (Exception e) {
            Log.e(TAG, "Invalid download URL", e);
            Toast.makeText(context, "Некорректная ссылка на файл", Toast.LENGTH_SHORT).show();
            return;
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadId = downloadManager.enqueue(request);
            Toast.makeText(context, "Загрузка началась...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "DownloadManager недоступен", Toast.LENGTH_SHORT).show();
            return;
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    if (isDownloadSuccessful(downloadId)) {
                        Log.d(TAG, "Download successful, starting APK installation");
                        installApk(file);
                    } else {
                        Log.e(TAG, "Download failed");
                        Toast.makeText(context, "Ошибка при загрузке обновления", Toast.LENGTH_LONG).show();
                    }
                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception ignored) {}
                }
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register download complete receiver", e);
        }
    }

    private boolean isDownloadSuccessful(long id) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) return false;
        
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                if (statusIndex != -1) {
                    int status = cursor.getInt(statusIndex);
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        return true;
                    } else if (reasonIndex != -1) {
                        Log.e(TAG, "Download failed with status: " + status + ", reason: " + cursor.getInt(reasonIndex));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying download status", e);
            return false;
        }
        return false;
    }

    private void installApk(File file) {
        if (!file.exists()) {
            Toast.makeText(context, "Файл обновления не найден", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "APK file does not exist at: " + file.getAbsolutePath());
            return;
        }

        // Check if file is too small (e.g. downloaded HTML error page instead of APK)
        if (file.length() < 1024 * 50) { // less than 50 KB cannot be a valid APK
            Toast.makeText(context, "Ошибка: скачанный файл поврежден или не является APK", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Downloaded file is too small: " + file.length() + bytesToString(file.length()));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(context, "Разрешите установку приложений из неизвестных источников", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + context.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        }

        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Installation intent started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting APK installation", e);
            Toast.makeText(context, "Ошибка при запуске установки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String bytesToString(long bytes) {
        return bytes + " bytes";
    }
}
