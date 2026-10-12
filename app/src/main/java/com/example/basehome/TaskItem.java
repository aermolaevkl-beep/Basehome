package com.example.basehome;

import android.util.Log;
import java.util.Locale;

public class TaskItem {
    private static final String TAG = "TaskItem";
    private final String type;
    private final String dateTime;
    private final String installerName;
    private final String address;
    private final String accountNumber;
    private final String clientName;
    private final String clientPhone;
    private final String tariff;
    private final String technology;

    public TaskItem(String type, String dateTime, String installerName, String address,
                    String accountNumber, String clientName, String clientPhone,
                    String tariff, String technology) {
        this.type = type != null ? type.trim() : "";
        this.dateTime = dateTime != null ? dateTime.trim() : "";
        this.installerName = installerName != null ? installerName.trim() : "";
        this.address = address != null ? address.trim() : "";
        this.accountNumber = accountNumber != null ? accountNumber.trim() : "";
        this.clientName = clientName != null ? clientName.trim() : "";
        this.clientPhone = clientPhone != null ? clientPhone.trim() : "";
        this.tariff = tariff != null ? tariff.trim() : "";
        this.technology = technology != null ? technology.trim() : "";
    }

    public static TaskItem fromRowString(String row) {
        if (row == null) return null;
        String[] parts = row.split(",");
        if (parts.length >= 9) {
            return new TaskItem(
                    parts[0], parts[1], parts[2], parts[3],
                    parts[4], parts[5], parts[6], parts[7], parts[8]
            );
        } else if (parts.length > 0) {
            return new TaskItem(
                    parts[0],
                    parts.length > 1 ? parts[1] : "",
                    parts.length > 2 ? parts[2] : "",
                    parts.length > 3 ? parts[3] : "",
                    parts.length > 4 ? parts[4] : "",
                    parts.length > 5 ? parts[5] : "",
                    parts.length > 6 ? parts[6] : "",
                    parts.length > 7 ? parts[7] : "",
                    parts.length > 8 ? parts[8] : ""
            );
        }
        return null;
    }

    public static boolean matchesUser(String installerName, String userName) {
        if (installerName == null || userName == null) return false;

        String rawInstClean = installerName.replaceAll("(?i)^[a-zа-яё0-9]+_", "").trim().toLowerCase(Locale.ROOT);
        String instClean = rawInstClean.replaceAll("\\d+", "").trim();
        String userClean = userName.trim().toLowerCase(Locale.ROOT);

        String[] instWords = instClean.split("[\\s_]+");
        String[] userWords = userClean.split("[\\s_@-]+");

        Log.d(TAG, "Matching user - userName: '" + userName + "' (clean: '" + userClean + "') with installerName: '" + installerName + "' (clean: '" + instClean + "')");

        for (String uw : userWords) {
            if (uw.length() >= 3) {
                if (uw.equals("gmail") || uw.equals("mail") || uw.equals("yandex") || uw.equals("admin")) {
                    continue;
                }
                for (String iw : instWords) {
                    if (iw.length() >= 3 && (iw.contains(uw) || uw.contains(iw))) {
                        Log.d(TAG, "Match found! User word '" + uw + "' matches installer word '" + iw + "'");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String getType() { return type; }
    public String getDateTime() { return dateTime; }
    public String getInstallerName() { return installerName; }
    public String getAddress() { return address; }
    public String getAccountNumber() { return accountNumber; }
    public String getClientName() { return clientName; }
    public String getClientPhone() { return clientPhone; }
    public String getTariff() { return tariff; }
    public String getTechnology() { return technology; }
}
