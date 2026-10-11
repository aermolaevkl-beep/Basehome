package com.example.basehome;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleAddress {

    private static final Pattern KW_PATTERN = Pattern.compile(
            "(?i)(.*?)\\s*,?\\s*\\b(?:кв(?:артира)?|кв-ра|apt|apartment)\\.?\\s*(?:№|#)?\\s*[0-9]+[\\p{L}]?(?:[-/][0-9]+[\\p{L}]?)?"
    );
    private static final Pattern DASH_PATTERN = Pattern.compile(
            "(?i)^([^,;]*?\\b\\d+[\\p{L}]?(?:\\s*/\\s*\\d+)?(?:\\s*к(?:орп)?\\.?\\s*\\d+)?(?:\\s*-[а-яА-Яa-zA-Z])?)\\s*-\\s*([0-9]+[\\p{L}]?)(?:\\b|[\\s,;].*)"
    );
    private static final Pattern APT_KW_PATTERN = Pattern.compile(
            "(?i)\\b(?:кв(?:артира)?|кв-ра|apt|apartment)\\.?\\s*(?:№|#)?\\s*([0-9]+[\\p{L}]?(?:[-/][0-9]+[\\p{L}]?)?)"
    );
    private static final Pattern APT_DASH_PATTERN = Pattern.compile(
            "(?i)^[^,;]*?\\b\\d+[\\p{L}]?(?:\\s*/\\s*\\d+)?(?:\\s*к(?:орп)?\\.?\\s*\\d+)?(?:\\s*-[а-яА-Яa-zA-Z])?\\s*-\\s*([0-9]+[\\p{L}]?)(?:\\b|[\\s,;].*)"
    );
    private static final Pattern PREFIX_PATTERN = Pattern.compile(
            "(?i)\\b(?:ул|улица|д|дом|пер|переулок|пр-т|проспект)\\.?\\b"
    );
    private static final Pattern NON_ALPHANUM_PATTERN = Pattern.compile(
            "[^\\p{L}\\p{N}]"
    );
    private static final Pattern TRAILING_CLEAN_PATTERN = Pattern.compile(
            "[,;\\s-]+$"
    );
    private static final Pattern APT_PART_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:кв(?:артира)?|кв-ра|apt|apartment)\\b.*|^\\s*№?\\s*кв\\.?\\s*.*"
    );
    private static final Pattern HOUSE_PART_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:д(?:ом)?\\.?\\s*)?\\d+[\\p{L}]?(?:[-/ ]\\d+[\\p{L}]?)?\\s*$"
    );
    private static final Pattern DIGITS_PATTERN = Pattern.compile(
            ".*\\d+.*"
    );

    @SerializedName("street")
    private String street;

    private transient String cachedStreetHouse;
    private transient String cachedStreetHouseKey;
    private transient String cachedApartment;

    public GoogleAddress() {}

    private java.util.ArrayList<String> visits;

    public GoogleAddress(String street) {
        this.street = street;
    }

    public GoogleAddress(String street, java.util.ArrayList<String> visits) {
        this.street = street;
        this.visits = visits;
    }

    public GoogleAddress(String street, String streetHouse, String streetHouseKey, String apartment, java.util.ArrayList<String> visits) {
        this.street = street;
        this.cachedStreetHouse = streetHouse;
        this.cachedStreetHouseKey = streetHouseKey;
        this.cachedApartment = apartment;
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
        if (cachedStreetHouse != null) return cachedStreetHouse;

        String normalized = getStreet().replace('\u00A0', ' ').trim();
        if (normalized.isEmpty()) {
            cachedStreetHouse = "";
            return "";
        }

        // 1. Поиск явных ключевых слов квартиры (кв, квартира, кв-ра, apt)
        Matcher kwMatcher = KW_PATTERN.matcher(normalized);
        if (kwMatcher.find()) {
            String candidate = kwMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                cachedStreetHouse = cleanAddressString(candidate);
                return cachedStreetHouse;
            }
        }

        // 2. Поиск формата улица дом-квартира (например "кулешова 22-14" или "ул. Кулешова, 22-14")
        Matcher dashMatcher = DASH_PATTERN.matcher(normalized);
        if (dashMatcher.find()) {
            String candidate = dashMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                cachedStreetHouse = cleanAddressString(candidate);
                return cachedStreetHouse;
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
        cachedStreetHouse = cleanAddressString(result);
        return cachedStreetHouse;
    }

    private static String cleanAddressString(String input) {
        if (input == null) return "";
        String result = input.trim();
        return TRAILING_CLEAN_PATTERN.matcher(result).replaceAll("").trim();
    }

    public String getStreetHouseKey() {
        if (cachedStreetHouseKey != null) return cachedStreetHouseKey;

        String streetHouse = getStreetHouse().toLowerCase(java.util.Locale.ROOT);
        streetHouse = PREFIX_PATTERN.matcher(streetHouse).replaceAll("");
        cachedStreetHouseKey = NON_ALPHANUM_PATTERN.matcher(streetHouse).replaceAll("");
        return cachedStreetHouseKey;
    }

    /** Returns the apartment number when present in the source row. */
    public String getApartment() {
        if (cachedApartment != null) return cachedApartment;

        String normalized = getStreet().replace('\u00A0', ' ').trim();
        if (normalized.isEmpty()) {
            cachedApartment = "";
            return "";
        }

        // 1. Поиск явных ключевых слов квартиры (кв, квартира)
        Matcher kwMatcher = APT_KW_PATTERN.matcher(normalized);
        if (kwMatcher.find()) {
            String apt = kwMatcher.group(1);
            cachedApartment = apt != null ? apt.trim() : "";
            return cachedApartment;
        }

        // 2. Поиск формата дом-квартира ("кулешова 22-14")
        Matcher dashMatcher = APT_DASH_PATTERN.matcher(normalized);
        if (dashMatcher.find()) {
            String apt = dashMatcher.group(1);
            cachedApartment = apt != null ? apt.trim() : "";
            return cachedApartment;
        }

        cachedApartment = "";
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
        return APT_PART_PATTERN.matcher(value).matches();
    }

    private static boolean isHousePart(String value) {
        return HOUSE_PART_PATTERN.matcher(value).matches();
    }

    private static boolean containsHouseNumber(String value) {
        return DIGITS_PATTERN.matcher(value).matches();
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
