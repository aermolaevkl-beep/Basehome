package com.example.basehome;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
                if (accountIdx == -1 && p.matches("\\d{6,14}")) {
                    accountIdx = i;
                } else if (phoneIdx == -1 && (p.matches("375\\d{9}") || (p.matches("\\d{9,12}") && i > 3))) {
                    phoneIdx = i;
                }
            }

            if (accountIdx != -1 && phoneIdx != -1 && phoneIdx > accountIdx) {
                String type = parts.get(0);
                String dateTime = parts.get(1);
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
                !parts.isEmpty() ? parts.get(0) : "",
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

    private static final Set<String> FIRST_NAMES = new HashSet<>(Arrays.asList(
        // Male Cyrillic
        "александр", "алексей", "анатолий", "андрей", "антон", "аркадий", "арсен", "арсений",
        "артем", "артём", "артур", "богдан", "борис", "вадим", "валентин", "валерий", "василий",
        "виктор", "виталий", "влад", "владимир", "владислав", "вячеслав", "геннадий", "георгий",
        "глеб", "даниил", "данил", "данияр", "денис", "дмитрий", "евгений", "егорий", "егорка",
        "егор", "ефим", "захар", "иван", "игорь", "илья", "иннокентий", "кирилл", "константин",
        "лев", "леонид", "максим", "марк", "матвей", "михаил", "мирон", "назар", "никита",
        "николай", "олег", "павел", "петр", "пётр", "родион", "роман", "руслан", "рустам",
        "семен", "семён", "сергей", "станислав", "степан", "тимур", "тимофей",
        "федор", "фёдор", "феликс", "филипп", "эдуард", "юрий", "ярослав", "яков",

        // Female Cyrillic
        "алёна", "алена", "алина", "алла", "анастасия", "ангелина", "анна", "валентина",
        "валерия", "варвара", "вера", "виктория", "галина", "дарья", "диана", "евгения",
        "екатерина", "елена", "елизавета", "жанна", "злата", "зинаида", "инна", "ирина",
        "карина", "кристина", "ксения", "лариса", "любовь", "людмила", "маргарита", "марина",
        "мария", "надежда", "наталия", "наталья", "нина", "оксана", "олеся", "ольга",
        "полина", "светлана", "софия", "софья", "тамара", "татьяна", "ульяна", "юлия", "яна",

        // Latin
        "alexander", "alex", "alexey", "alexei", "anatoly", "andrey", "andrei", "anton",
        "artem", "artur", "boris", "vadim", "valery", "vasily", "victor", "viktor",
        "vitaly", "vlad", "vladimir", "vladislav", "vyacheslav", "denis", "dmitry",
        "dmitrii", "dmitry", "eugen", "eugene", "evgeny", "evgeniy", "egor", "ivan",
        "igor", "ilya", "kirill", "konstantin", "leo", "leonid", "maxim", "maksim",
        "mark", "matvey", "mikhail", "nikita", "nikolay", "nikolai", "oleg", "pavel",
        "petr", "peter", "roman", "ruslan", "sergey", "sergei", "stanislav", "stepan",
        "timur", "timofey", "fedor", "edward", "yury", "yuri", "yaroslav"
    ));

    private static boolean isPatronymic(String word) {
        if (word == null || word.length() < 5) return false;
        String w = word.toLowerCase(Locale.ROOT);
        return w.endsWith("евич") || w.endsWith("ович") || w.endsWith("ич")
                || w.endsWith("евна") || w.endsWith("овна") || w.endsWith("чна");
    }

    private static String transliterateToLatin(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'а': sb.append("a"); break;
                case 'б': sb.append("b"); break;
                case 'в': sb.append("v"); break;
                case 'г': sb.append("g"); break;
                case 'д': sb.append("d"); break;
                case 'е': case 'ё': case 'э': sb.append("e"); break;
                case 'ж': sb.append("zh"); break;
                case 'з': sb.append("z"); break;
                case 'и': case 'й': sb.append("i"); break;
                case 'к': sb.append("k"); break;
                case 'л': sb.append("l"); break;
                case 'м': sb.append("m"); break;
                case 'н': sb.append("n"); break;
                case 'о': sb.append("o"); break;
                case 'п': sb.append("p"); break;
                case 'р': sb.append("r"); break;
                case 'с': sb.append("s"); break;
                case 'т': sb.append("t"); break;
                case 'у': sb.append("u"); break;
                case 'ф': sb.append("f"); break;
                case 'х': sb.append("kh"); break;
                case 'ц': sb.append("ts"); break;
                case 'ч': sb.append("ch"); break;
                case 'ш': sb.append("sh"); break;
                case 'щ': sb.append("shch"); break;
                case 'ы': sb.append("y"); break;
                case 'ю': sb.append("yu"); break;
                case 'я': sb.append("ya"); break;
                case 'ь': case 'ъ': break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static boolean wordsMatch(String w1, String w2) {
        if (w1 == null || w2 == null) return false;
        String s1 = w1.toLowerCase(Locale.ROOT).trim();
        String s2 = w2.toLowerCase(Locale.ROOT).trim();
        if (s1.length() < 3 || s2.length() < 3) return false;

        if (s1.equals(s2) || s1.contains(s2) || s2.contains(s1)) {
            return true;
        }

        String lat1 = transliterateToLatin(s1);
        String lat2 = transliterateToLatin(s2);
        if (lat1.length() >= 3 && lat2.length() >= 3) {
            if (lat1.equals(lat2) || lat1.contains(lat2) || lat2.contains(lat1)) {
                return true;
            }
        }

        return false;
    }

    public static boolean matchesUser(String installerName, String userName) {
        if (installerName == null || userName == null) return false;

        String rawInstClean = installerName.replaceAll("(?i)^[a-zа-яё0-9]+_", "").trim().toLowerCase(Locale.ROOT);
        String instClean = rawInstClean.replaceAll("\\d+", " ").replaceAll("[^a-zа-яё0-9\\s]", " ").trim();

        String userClean = userName.toLowerCase(Locale.ROOT);
        if (userClean.contains("@")) {
            userClean = userClean.substring(0, userClean.indexOf('@'));
        }
        userClean = userClean.replaceAll("[^a-zа-яё0-9\\s]", " ").trim();

        String[] instWords = instClean.split("\\s+");
        String[] userWords = userClean.split("\\s+");

        List<String> userSurnames = new ArrayList<>();
        List<String> userFirstNames = new ArrayList<>();

        for (String uw : userWords) {
            if (uw.length() < 3) continue;
            if (uw.equals("gmail") || uw.equals("mail") || uw.equals("yandex") || uw.equals("admin") || uw.equals("user")) {
                continue;
            }
            if (isPatronymic(uw) || FIRST_NAMES.contains(uw)) {
                userFirstNames.add(uw);
            } else {
                userSurnames.add(uw);
            }
        }

        // If user has surname(s) specified (e.g. "Ермолаев" in "Александр Ермолаев"), we MUST match surname!
        if (!userSurnames.isEmpty()) {
            for (String us : userSurnames) {
                for (String iw : instWords) {
                    if (iw.length() >= 3 && wordsMatch(us, iw)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Fallback: If no surname was identified in user profile (e.g. user profile name is only "Александр"), match any valid word
        if (!userFirstNames.isEmpty()) {
            for (String ufn : userFirstNames) {
                for (String iw : instWords) {
                    if (iw.length() >= 3 && wordsMatch(ufn, iw)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getType() { return type; }

    public String getDateTime() {
        if (dateTime == null || dateTime.trim().isEmpty()) return "";
        String trimmed = dateTime.trim();
        String[] patterns = {"dd.MM.yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd.MM.yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                sdf.setLenient(true);
                Date date = sdf.parse(trimmed);
                if (date != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    cal.add(Calendar.HOUR_OF_DAY, 2);
                    SimpleDateFormat outSdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                    return outSdf.format(cal.getTime());
                }
            } catch (Exception ignored) {}
        }
        return trimmed;
    }

    public String getInstallerName() { return installerName; }
    public String getAddress() { return address; }
    public String getAccountNumber() { return accountNumber; }
    public String getClientName() { return clientName; }
    public String getClientPhone() { return clientPhone; }
    public String getTariff() { return tariff; }
    public String getTechnology() { return technology; }
}
