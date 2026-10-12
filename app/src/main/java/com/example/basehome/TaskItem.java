package com.example.basehome;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
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
        String[] rawParts = row.split(",");
        List<String> parts = new ArrayList<>();
        for (String p : rawParts) {
            parts.add(p.trim());
        }

        if (parts.size() >= 9) {
            int accountIdx = -1;
            int phoneIdx = -1;

            for (int i = 2; i < parts.size(); i++) {
                String p = parts.get(i);
                if (accountIdx == -1 && p.matches("\\d{10,14}")) {
                    accountIdx = i;
                } else if (phoneIdx == -1 && (p.matches("375\\d{9}") || (p.matches("\\d{9,12}") && i > 3))) {
                    phoneIdx = i;
                }
            }

            if (accountIdx != -1 && phoneIdx != -1 && phoneIdx > accountIdx) {
                String type = parts.get(0);
                String dateTime = parts.get(1);
                
                // Installer is usually index 2, but if address parts shifted, let's find installer or use index 2
                String installer = parts.get(2);

                StringBuilder addr = new StringBuilder();
                for (int i = 3; i < accountIdx; i++) {
                    if (addr.length() > 0) addr.append(", ");
                    addr.append(parts.get(i));
                }

                String account = parts.get(accountIdx);

                StringBuilder clientName = new StringBuilder();
                for (int i = accountIdx + 1; i < phoneIdx; i++) {
                    if (clientName.length() > 0) clientName.append(", ");
                    clientName.append(parts.get(i));
                }

                String phone = parts.get(phoneIdx);
                String tariff = phoneIdx + 1 < parts.size() ? parts.get(phoneIdx + 1) : "";
                String tech = phoneIdx + 2 < parts.size() ? parts.get(phoneIdx + 2) : "";

                return new TaskItem(type, dateTime, installer, addr.toString(), account, clientName.toString(), phone, tariff, tech);
            }
        }

        // Fallback
        return new TaskItem(
                parts.size() > 0 ? parts.get(0) : "",
                parts.size() > 1 ? parts.get(1) : "",
                parts.size() > 2 ? parts.get(2) : "",
                parts.size() > 3 ? parts.get(3) : "",
                parts.size() > 4 ? parts.get(4) : "",
                parts.size() > 5 ? parts.get(5) : "",
                parts.size() > 6 ? parts.get(6) : "",
                parts.size() > 7 ? parts.get(7) : "",
                parts.size() > 8 ? parts.get(8) : ""
        );
    }

    public static boolean matchesUser(String installerName, String userName) {
        if (installerName == null || userName == null) return false;

        String rawInstClean = installerName.replaceAll("(?i)^[a-zа-яё0-9]+_", "").trim().toLowerCase(Locale.ROOT);
        String instClean = rawInstClean.replaceAll("\\d+", "").trim();
        String userClean = userName.trim().toLowerCase(Locale.ROOT);

        String[] instWords = instClean.split("[\\s_]+");
        String[] userWords = userClean.split("[\\s_@-]+");

        Log.d(TAG, "Matching user - userName: '" + userName + "' with installerName: '" + installerName + "'");

        for (String uw : userWords) {
            if (uw.length() >= 3) {
                if (uw.equals("gmail") || uw.equals("mail") || uw.equals("yandex") || uw.equals("admin")) {
                    continue;
                }
                for (String iw : instWords) {
                    if (iw.length() >= 3 && (iw.contains(uw) || uw.contains(iw))) {
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
