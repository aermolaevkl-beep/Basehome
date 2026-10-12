package com.example.basehome;

import java.util.Locale;

public class TaskItem {
    private String type;
    private String dateTime;
    private String installerName;
    private String address;
    private String accountNumber;
    private String clientName;
    private String clientPhone;
    private String tariff;
    private String technology;

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
            // Fallback if fewer parts
            return new TaskItem(parts[0], "", parts.length > 2 ? parts[2] : "", parts.length > 3 ? parts[3] : "",
                    "", parts.length > 5 ? parts[5] : "", parts.length > 6 ? parts[6] : "", "", "");
        }
        return null;
    }

    public String getInstallerSurname() {
        return extractSurname(installerName);
    }

    public static String extractSurname(String fullName) {
        if (fullName == null) return "";
        String clean = fullName.replaceAll("(?i)^[a-zа-яё]_*", "").trim();
        String[] parts = clean.split("[\\s_]+");
        if (parts.length > 0) {
            return parts[0].toLowerCase(Locale.ROOT);
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    public static boolean matchesUser(String installerName, String userName) {
        String s1 = extractSurname(installerName);
        String s2 = extractSurname(userName);
        return !s1.isEmpty() && !s2.isEmpty() && s1.equals(s2);
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
