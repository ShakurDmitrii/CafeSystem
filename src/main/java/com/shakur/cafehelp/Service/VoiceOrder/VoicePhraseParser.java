package com.shakur.cafehelp.Service.VoiceOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Детерминированный разбор фразы кассира формата
 * {@code <блюдо> [количество] <блюдо> [количество] … [N персон]}.
 *
 * <p>Правила:
 * <ul>
 *   <li>число относится к блюду, произнесённому перед ним; без числа — одна штука;</li>
 *   <li>число перед словом «персон/персоны/человек» задаёт количество персон;</li>
 *   <li>названия ищутся жадно: от самого длинного окна слов к короткому;</li>
 *   <li>если под окно подходят несколько позиций с близкой оценкой — строка становится {@code AMBIGUOUS};</li>
 *   <li>слова, которые ни с чем не совпали, возвращаются в {@code unrecognized}.</li>
 * </ul>
 * Никакого обучения и внешних сервисов: результат при одинаковом меню всегда одинаков.
 */
public final class VoicePhraseParser {

    private static final Set<String> PERSON_WORDS = Set.of(
            "персон", "персона", "персоны", "персону", "человек", "человека", "чел", "гостя", "гостей", "гость"
    );

    private static final Set<String> FILLER_WORDS = Set.of("штук", "штуки", "штука", "порции", "порция", "порций", "и", "еще", "ещё");

    private static final int MAX_WINDOW = 6;
    private static final int MAX_QTY = 99;
    private static final int MAX_PERSONS = 1000;
    private static final double AMBIGUITY_MARGIN = 0.15;
    private static final int MAX_CANDIDATES = 5;

    private record IndexedItem(MenuItemRef item, List<String> words, int totalWords) {
    }

    private final List<IndexedItem> index;

    public VoicePhraseParser(List<MenuItemRef> menu) {
        List<IndexedItem> built = new ArrayList<>(menu.size());
        for (MenuItemRef item : menu) {
            List<String> words = RussianText.tokens(item.name()).stream()
                    .filter(word -> !RussianText.STOP_WORDS.contains(word))
                    .toList();
            if (!words.isEmpty()) {
                built.add(new IndexedItem(item, words, words.size()));
            }
        }
        this.index = List.copyOf(built);
    }

    public VoiceOrderDraft parse(String transcript) {
        List<String> tokens = RussianText.tokens(transcript);
        List<VoiceOrderDraft.Line> lines = new ArrayList<>();
        List<String> unrecognized = new ArrayList<>();
        Integer personCount = null;
        // Индекс последней строки текущего сегмента, которой ещё не назначено число.
        int pendingLine = -1;

        int i = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            Integer number = parseNumber(token);

            if (number != null) {
                int next = i + 1;
                if (next < tokens.size() && PERSON_WORDS.contains(tokens.get(next))) {
                    personCount = Math.min(number, MAX_PERSONS);
                    i += 2;
                    continue;
                }
                if (pendingLine >= 0) {
                    VoiceOrderDraft.Line line = lines.get(pendingLine);
                    lines.set(pendingLine, new VoiceOrderDraft.Line(
                            line.text(), line.status(), Math.max(1, Math.min(number, MAX_QTY)), line.candidates()
                    ));
                    pendingLine = -1;
                } else {
                    unrecognized.add(token);
                }
                i++;
                continue;
            }

            if (PERSON_WORDS.contains(token)) {
                // «персон» без числа перед ним: пробуем число после («персон три»).
                int next = i + 1;
                Integer following = next < tokens.size() ? parseNumber(tokens.get(next)) : null;
                if (following != null) {
                    personCount = Math.min(following, MAX_PERSONS);
                    i += 2;
                } else {
                    unrecognized.add(token);
                    i++;
                }
                continue;
            }

            if (FILLER_WORDS.contains(token)) {
                i++;
                continue;
            }

            Match match = matchAt(tokens, i);
            if (match == null) {
                // Непонятное слово разрывает привязку: число после него относится уже не к прошлому блюду.
                unrecognized.add(token);
                pendingLine = -1;
                i++;
                continue;
            }
            lines.add(match.line());
            pendingLine = lines.size() - 1;
            i += match.length();
        }

        return new VoiceOrderDraft(transcript == null ? "" : transcript.trim(), List.copyOf(lines), personCount, List.copyOf(unrecognized));
    }

    private record Match(VoiceOrderDraft.Line line, int length) {
    }

    private Match matchAt(List<String> tokens, int start) {
        int maxLength = Math.min(MAX_WINDOW, tokens.size() - start);
        for (int length = maxLength; length >= 1; length--) {
            List<String> window = tokens.subList(start, start + length);
            // Числительное внутри окна допустимо: оно может быть частью названия («сет номер два»).
            // Если ни одно название его не содержит, окно просто не найдёт кандидатов.
            if (window.stream().anyMatch(PERSON_WORDS::contains)) {
                continue;
            }
            List<VoiceOrderDraft.Candidate> candidates = rank(window);
            if (candidates.isEmpty()) {
                continue;
            }
            String text = String.join(" ", window);
            VoiceOrderDraft.Status status = isUnambiguous(candidates, spokenWordCount(window))
                    ? VoiceOrderDraft.Status.MATCHED
                    : VoiceOrderDraft.Status.AMBIGUOUS;
            List<VoiceOrderDraft.Candidate> shown = status == VoiceOrderDraft.Status.MATCHED
                    ? List.of(candidates.get(0))
                    : candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size()));
            return new Match(new VoiceOrderDraft.Line(text, status, 1, shown), length);
        }
        return null;
    }

    /**
     * Строка однозначна, если кандидат один, либо произнесено название целиком
     * и ближайший конкурент заметно хуже. Частично произнесённое название («кофе»)
     * при нескольких вариантах всегда требует уточнения.
     */
    private static boolean isUnambiguous(List<VoiceOrderDraft.Candidate> candidates, int spokenWords) {
        if (candidates.size() == 1) {
            return true;
        }
        VoiceOrderDraft.Candidate best = candidates.get(0);
        boolean fullName = best.nameWordCount() == spokenWords;
        return fullName && best.score() - candidates.get(1).score() >= AMBIGUITY_MARGIN;
    }

    private static int spokenWordCount(List<String> window) {
        return (int) window.stream().filter(word -> !RussianText.STOP_WORDS.contains(word)).count();
    }

    /**
     * Оценка окна слов против каждой позиции меню. Каждое произнесённое слово обязано
     * совпасть с отдельным словом названия, иначе позиция не подходит. Оценка —
     * доля покрытых слов названия, с бонусом за точное совпадение всего названия.
     */
    private List<VoiceOrderDraft.Candidate> rank(List<String> window) {
        List<String> spoken = window.stream().filter(word -> !RussianText.STOP_WORDS.contains(word)).toList();
        if (spoken.isEmpty()) {
            return List.of();
        }
        List<VoiceOrderDraft.Candidate> result = new ArrayList<>();
        for (IndexedItem entry : index) {
            if (spoken.size() > entry.totalWords()) {
                continue;
            }
            boolean[] used = new boolean[entry.totalWords()];
            int exact = 0;
            boolean allMatched = true;
            for (String word : spoken) {
                int found = -1;
                for (int k = 0; k < entry.words().size(); k++) {
                    if (used[k]) {
                        continue;
                    }
                    String menuWord = entry.words().get(k);
                    if (word.equals(menuWord)) {
                        found = k;
                        exact++;
                        break;
                    }
                    if (found < 0 && RussianText.similar(word, menuWord)) {
                        found = k;
                    }
                }
                if (found < 0) {
                    allMatched = false;
                    break;
                }
                used[found] = true;
            }
            if (!allMatched) {
                continue;
            }
            double coverage = (double) spoken.size() / entry.totalWords();
            double precision = (double) exact / spoken.size();
            double score = coverage * (0.85 + 0.15 * precision);
            result.add(VoiceOrderDraft.Candidate.of(entry.item(), round(score), entry.totalWords()));
        }
        result.sort(Comparator.comparingDouble(VoiceOrderDraft.Candidate::score).reversed()
                .thenComparing(VoiceOrderDraft.Candidate::name)
                .thenComparingInt(VoiceOrderDraft.Candidate::id));
        return result;
    }

    private static Integer parseNumber(String token) {
        return RussianText.parseNumber(token);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
