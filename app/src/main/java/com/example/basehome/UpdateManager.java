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
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

public class UpdateManager {

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

        DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("Загрузка обновления")
                    .setDescription("Basehome скачивает новую версию...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(file))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true);
        } catch (Exception e) {
            Toast.makeText(context, "Некорректная ссылка на файл", Toast.LENGTH_SHORT).show();
            return;
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadId = downloadManager.enqueue(request);
            Toast.makeText(context, "Загрузка началась...", Toast.LENGTH_SHORT).show();
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    if (isDownloadSuccessful(downloadId)) {
                        installApk(file);
                    } else {
                        Toast.makeText(context, "Ошибка при загрузке обновления", Toast.LENGTH_LONG).show();
                    }
                    context.unregisterReceiver(this);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
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
                return statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void installApk(File file) {
        if (!file.exists()) {
            Toast.makeText(context, "Файл обновления не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(context, "Необходимо разрешить установку из неизвестных источников", Toast.LENGTH_LONG).show();
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
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка при запуске установки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
