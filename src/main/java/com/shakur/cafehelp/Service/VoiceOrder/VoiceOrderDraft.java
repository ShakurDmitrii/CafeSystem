package com.shakur.cafehelp.Service.VoiceOrder;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Результат разбора фразы: черновик, который касса показывает кассиру до добавления в заказ.
 * Ничего из него не попадает в заказ автоматически.
 */
public record VoiceOrderDraft(
        String transcript,
        List<Line> lines,
        Integer personCount,
        List<String> unrecognized
) {

    public enum Status {
        MATCHED,
        AMBIGUOUS
    }

    /** Одна произнесённая позиция: что услышали, сколько и какие позиции меню подходят. */
    public record Line(String text, Status status, int qty, List<Candidate> candidates) {
    }

    /** Кандидат из меню с оценкой похожести от 0 до 1. */
    public record Candidate(String itemType, int id, String name, Double price, double score,
                            @JsonIgnore int nameWordCount) {
        static Candidate of(MenuItemRef item, double score, int nameWordCount) {
            return new Candidate(item.itemType(), item.id(), item.name(), item.price(), score, nameWordCount);
        }
    }
}
