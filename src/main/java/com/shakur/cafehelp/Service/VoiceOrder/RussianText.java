package com.shakur.cafehelp.Service.VoiceOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Лёгкая нормализация русского текста для сопоставления речи с меню:
 * регистр, «ё», знаки препинания и грубое отсечение окончаний.
 * Это не морфология, а предсказуемая эвристика: «шаурмы» и «шаурма» дают одну основу.
 */
final class RussianText {

    /** Служебные слова в названиях блюд, которые кассир обычно не произносит. */
    static final Set<String> STOP_WORDS = Set.of("с", "и", "на", "для", "по", "в", "из", "со");

    private static final String[] ENDINGS = {
            "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими", "ях", "ах",
            "ой", "ей", "ую", "юю", "ая", "яя", "ые", "ие", "ом", "ем", "ам", "ям", "ов", "ев",
            "а", "я", "у", "ю", "ы", "и", "е", "о", "й", "ь"
    };

    /** Числительные, которые кассир произносит вместо цифр. */
    static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("один", 1), Map.entry("одна", 1), Map.entry("одну", 1), Map.entry("одно", 1), Map.entry("одной", 1),
            Map.entry("два", 2), Map.entry("две", 2), Map.entry("двух", 2), Map.entry("пара", 2), Map.entry("пару", 2),
            Map.entry("три", 3), Map.entry("трех", 3), Map.entry("четыре", 4), Map.entry("четырех", 4),
            Map.entry("пять", 5), Map.entry("пяти", 5), Map.entry("шесть", 6), Map.entry("шести", 6),
            Map.entry("семь", 7), Map.entry("семи", 7), Map.entry("восемь", 8), Map.entry("восьми", 8),
            Map.entry("девять", 9), Map.entry("девяти", 9), Map.entry("десять", 10), Map.entry("десяти", 10)
    );

    private RussianText() {
    }

    /** Число из слова («два») или короткой записи цифрами («2»), иначе {@code null}. */
    static Integer parseNumber(String token) {
        Integer word = NUMBER_WORDS.get(token);
        if (word != null) {
            return word;
        }
        if (!token.isEmpty() && token.length() <= 3 && token.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(token);
        }
        return null;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .trim();
    }

    static List<String> tokens(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    static String stem(String token) {
        if (token.length() <= 3 || token.chars().anyMatch(Character::isDigit)) {
            return token;
        }
        for (String ending : ENDINGS) {
            if (token.endsWith(ending) && token.length() - ending.length() >= 3) {
                return token.substring(0, token.length() - ending.length());
            }
        }
        return token;
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Похожи ли два слова после отсечения окончаний (с допуском на одну-две опечатки). */
    static boolean similar(String spoken, String menuWord) {
        if (spoken.equals(menuWord)) {
            return true;
        }
        // «сет номер два» против «Сет номер 2»: числительное равно цифре в названии.
        Integer spokenNumber = NUMBER_WORDS.get(spoken);
        if (spokenNumber != null) {
            return spokenNumber.equals(parseNumber(menuWord));
        }
        String a = stem(spoken);
        String b = stem(menuWord);
        if (a.equals(b)) {
            return true;
        }
        int shortest = Math.min(a.length(), b.length());
        if (shortest < 4) {
            return false;
        }
        int allowed = shortest >= 8 ? 2 : 1;
        return levenshtein(a, b) <= allowed;
    }
}
