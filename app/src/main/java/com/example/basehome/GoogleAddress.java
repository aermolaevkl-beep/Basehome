package com.example.basehome;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class GoogleAddress {
    @SerializedName("street")
    private String street;

    public GoogleAddress() {}

    private java.util.ArrayList<String> visits;

    public GoogleAddress(String street) {
        this.street = street;
    }

    public GoogleAddress(String street, java.util.ArrayList<String> visits) {
        this.street = street;
        this.visits = visits;
    }

    public java.util.ArrayList<String> getVisits() {
        return visits == null ? new java.util.ArrayList<>() : visits;
    }

    public String getStreet() {
        return street != null ? street : "";
    }

    public void setStreet(String street) {
        this.street = street;
    }

    /** Returns the leading street + house portion, excluding an apartment number. */
    public String getStreetHouse() {
        String normalized = getStreet().replace('\u00A0', ' ').trim();
        if (normalized.isEmpty()) return "";

        // 1. Поиск явных ключевых слов квартиры (кв, квартира, кв-ра, apt)
        java.util.regex.Matcher kwMatcher = java.util.regex.Pattern.compile(
                "(?i)(.*?)\\s*,?\\s*\\b(?:кв(?:артира)?|кв-ра|apt|apartment)\\.?\\s*(?:№|#)?\\s*[0-9]+[\\p{L}]?(?:[-/][0-9]+[\\p{L}]?)?"
        ).matcher(normalized);
        if (kwMatcher.find()) {
            String candidate = kwMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return cleanAddressString(candidate);
            }
        }

        // 2. Поиск формата улица дом-квартира (например "кулешова 22-14" или "ул. Кулешова, 22-14")
        java.util.regex.Matcher dashMatcher = java.util.regex.Pattern.compile(
                "(?i)^([^,;]*?\\b\\d+[\\p{L}]?(?:\\s*/\\s*\\d+)?(?:\\s*к(?:орп)?\\.?\\s*\\d+)?(?:\\s*-[а-яА-Яa-zA-Z])?)\\s*-\\s*([0-9]+[\\p{L}]?)(?:\\b|[\\s,;].*)"
        ).matcher(normalized);
        if (dashMatcher.find()) {
            String candidate = dashMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                return cleanAddressString(candidate);
            }
        }

        // 3. Резервный разбор по запятым
        String[] parts = normalized.split(",");
        StringBuilder address = new StringBuilder();
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty() || isApartmentPart(part)) break;
            if (address.length() > 0 && !isHousePart(part)) break;
            if (address.length() > 0) address.append(", ");
            address.append(part);
            if (containsHouseNumber(address.toString())) break;
        }

        String result = address.length() == 0 ? parts[0].trim() : address.toString();
        result = result.replaceAll("(?i)\\b(?:кв(?:артира)?|кв-ра|apt|apartment)\\.?\\s*#?\\s*[^,;]*", "").trim();
        return cleanAddressString(result);
    }

    private static String cleanAddressString(String input) {
        if (input == null) return "";
        String result = input.trim();
        return result.replaceAll("[,;\\s-]+$", "").trim();
    }

    public String getStreetHouseKey() {
        String streetHouse = getStreetHouse().toLowerCase(java.util.Locale.ROOT);
        streetHouse = streetHouse.replaceAll("(?i)\\b(?:ул|улица|д|дом|пер|переулок|пр-т|проспект)\\.?\\b", "");
        return streetHouse.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Returns the apartment number when present in the source row. */
    public String getApartment() {
        String normalized = getStreet().replace('\u00A0', ' ').trim();
        if (normalized.isEmpty()) return "";

        // 1. Поиск явных ключевых слов квартиры (кв, квартира)
        java.util.regex.Matcher kwMatcher = java.util.regex.Pattern.compile(
                "(?i)\\b(?:кв(?:артира)?|кв-ра|apt|apartment)\\.?\\s*(?:№|#)?\\s*([0-9]+[\\p{L}]?(?:[-/][0-9]+[\\p{L}]?)?)"
        ).matcher(normalized);
        if (kwMatcher.find()) {
            String apt = kwMatcher.group(1);
            return apt != null ? apt.trim() : "";
        }

        // 2. Поиск формата дом-квартира ("кулешова 22-14")
        java.util.regex.Matcher dashMatcher = java.util.regex.Pattern.compile(
                "(?i)^[^,;]*?\\b\\d+[\\p{L}]?(?:\\s*/\\s*\\d+)?(?:\\s*к(?:орп)?\\.?\\s*\\d+)?(?:\\s*-[а-яА-Яa-zA-Z])?\\s*-\\s*([0-9]+[\\p{L}]?)(?:\\b|[\\s,;].*)"
        ).matcher(normalized);
        if (dashMatcher.find()) {
            String apt = dashMatcher.group(1);
            return apt != null ? apt.trim() : "";
        }

        return "";
    }

    /** Pre-filters rows coming from Google Sheets to exclude empty, header or non-address rows. */
    public static boolean isValidAddressRow(String row) {
        if (row == null) return false;
        String trimmed = row.replace('\u00A0', ' ').trim();
        if (trimmed.length() < 3) return false;

        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("адрес") || lower.equals("улица") || lower.equals("фио")
                || lower.startsWith("адрес,") || lower.startsWith("улица,")
                || lower.startsWith("список") || lower.startsWith("№ п/п")
                || lower.startsWith("дата выезда") || lower.startsWith("таблица")) {
            return false;
        }

        GoogleAddress ga = new GoogleAddress(trimmed);
        String streetHouse = ga.getStreetHouse();
        if (streetHouse.isEmpty()) return false;

        return containsHouseNumber(streetHouse);
    }

    public int getApartmentCount() {
        java.util.Set<String> apartments = new java.util.LinkedHashSet<>();
        for (String visit : getVisits()) {
            String apartment = new GoogleAddress(visit).getApartment();
            if (!apartment.isEmpty()) apartments.add(apartment.toLowerCase(java.util.Locale.ROOT));
        }
        return apartments.size();
    }

    private static boolean isApartmentPart(String value) {
        return value.matches("(?i)^\\s*(?:кв(?:артира)?|кв-ра|apt|apartment)\\b.*")
                || value.matches("(?i)^\\s*№?\\s*кв\\.?\\s*.*");
    }

    private static boolean isHousePart(String value) {
        return value.matches("(?i)^\\s*(?:д(?:ом)?\\.?\\s*)?\\d+[\\p{L}]?(?:[-/ ]\\d+[\\p{L}]?)?\\s*$");
    }

    private static boolean containsHouseNumber(String value) {
        return value.matches("(?i).*\\d+.*");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoogleAddress that = (GoogleAddress) o;
        return Objects.equals(street, that.street);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street);
    }
}
