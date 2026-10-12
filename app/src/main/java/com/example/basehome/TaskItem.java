package com.example.basehome;

import java.util.Locale;

public class TaskItem {
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
        
        // Clean installer name (e.g. "КГ_Исаенко Антон_375297458764" -> "Исаенко Антон")
        String instClean = installerName.replaceAll("(?i)^[a-zа-яё0-9]+_", "").trim();
        String[] instParts = instClean.split("_");
        String instName = instParts.length > 1 ? instParts[1] : instParts[0];

        String userClean = userName.replaceAll("(?i)^[a-zа-яё0-9]+_", "").trim();

        String[] instWords = instName.toLowerCase(Locale.ROOT).split("[\\s_]+");
        String[] userWords = userClean.toLowerCase(Locale.ROOT).split("[\\s_]+");

        // Check if surname (first word of name part) matches
        if (instWords.length > 0 && userWords.length > 0) {
            String instSurname = instWords[0];
            for (String uw : userWords) {
                if (uw.equals(instSurname)) {
                    return true;
                }
            }
        }

        // Fallback: check any word match
        for (String uw : userWords) {
            if (uw.length() > 2) {
                for (String iw : instWords) {
                    if (uw.equals(iw)) {
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
