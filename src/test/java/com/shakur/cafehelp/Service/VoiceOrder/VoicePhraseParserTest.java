package com.shakur.cafehelp.Service.VoiceOrder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoicePhraseParserTest {

    private static final List<MenuItemRef> MENU = List.of(
            MenuItemRef.dish(11, "Филадельфия", 450.0),
            MenuItemRef.dish(4, "Запеченная Филадельфия", 480.0),
            MenuItemRef.dish(27, "Эби макки", 220.0),
            MenuItemRef.dish(25, "Икка макки", 210.0),
            MenuItemRef.dish(124, "Кофе Американо маленький", 90.0),
            MenuItemRef.dish(125, "Кофе Американо большой", 120.0),
            MenuItemRef.dish(127, "Кофе для дедушки", 100.0),
            MenuItemRef.dish(16, "Шаурма", 250.0),
            MenuItemRef.dish(240, "Шаурма двойное мясо", 320.0),
            MenuItemRef.dish(47, "Суши с томаго", 60.0),
            MenuItemRef.dish(56, "Суши с томаго", 65.0),
            MenuItemRef.dish(10, "ролл с лососем ", 300.0),
            MenuItemRef.dish(129, "Лимонад", 80.0),
            MenuItemRef.set(2, "Сет Американо", 1200.0)
    );

    private final VoicePhraseParser parser = new VoicePhraseParser(MENU);

    @Test
    void parsesTheAgreedPhraseFormat() {
        VoiceOrderDraft draft = parser.parse("Филадельфия, лимонад два, эби макки, три персоны.");

        assertThat(draft.personCount()).isEqualTo(3);
        assertThat(draft.unrecognized()).isEmpty();
        assertThat(draft.lines()).hasSize(3);

        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(11);
        assertThat(draft.lines().get(0).qty()).isEqualTo(1);

        assertThat(draft.lines().get(1).candidates().get(0).id()).isEqualTo(129);
        assertThat(draft.lines().get(1).qty()).isEqualTo(2);

        assertThat(draft.lines().get(2).candidates().get(0).id()).isEqualTo(27);
        assertThat(draft.lines().get(2).candidates().get(0).itemType()).isEqualTo("dish");
        assertThat(draft.lines().get(2).qty()).isEqualTo(1);
    }

    @Test
    void exactNameWinsOverLongerNameContainingIt() {
        VoiceOrderDraft draft = parser.parse("шаурма");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(16);
    }

    @Test
    void longerSpokenNameConsumesAllItsWords() {
        VoiceOrderDraft draft = parser.parse("шаурма двойное мясо два");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(240);
        assertThat(draft.lines().get(0).qty()).isEqualTo(2);
        assertThat(draft.unrecognized()).isEmpty();
    }

    @Test
    void partialNameIsAmbiguousWithCandidatesSortedByScore() {
        VoiceOrderDraft draft = parser.parse("кофе");

        assertThat(draft.lines()).hasSize(1);
        VoiceOrderDraft.Line line = draft.lines().get(0);
        assertThat(line.status()).isEqualTo(VoiceOrderDraft.Status.AMBIGUOUS);
        assertThat(line.candidates()).extracting(VoiceOrderDraft.Candidate::id)
                .containsExactly(127, 125, 124);
    }

    @Test
    void duplicateMenuNamesStayAmbiguous() {
        VoiceOrderDraft draft = parser.parse("суши томаго");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.AMBIGUOUS);
        assertThat(draft.lines().get(0).candidates()).extracting(VoiceOrderDraft.Candidate::id)
                .containsExactly(47, 56);
    }

    @Test
    void wordFormsAndStrayWhitespaceAreNormalized() {
        VoiceOrderDraft draft = parser.parse("две филадельфии и ролл с лососем");

        // Число перед первым блюдом не к чему привязать: оно попадает в unrecognized,
        // а блюда разбираются по обычным правилам.
        assertThat(draft.unrecognized()).containsExactly("две");
        assertThat(draft.lines()).extracting(line -> line.candidates().get(0).id()).containsExactly(11, 10);
    }

    @Test
    void unknownWordsAreReportedNotGuessed() {
        VoiceOrderDraft draft = parser.parse("филадельфия пицца маргарита 2");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).qty()).isEqualTo(1);
        assertThat(draft.unrecognized()).containsExactly("пицца", "маргарита", "2");
    }

    @Test
    void digitsAndPersonWordsAreSupported() {
        VoiceOrderDraft draft = parser.parse("лимонад 4 2 персоны");

        assertThat(draft.lines().get(0).qty()).isEqualTo(4);
        assertThat(draft.personCount()).isEqualTo(2);
        assertThat(draft.unrecognized()).isEmpty();
    }

    @Test
    void personWordFollowedByNumberIsAccepted() {
        VoiceOrderDraft draft = parser.parse("лимонад персон 2");

        assertThat(draft.lines().get(0).qty()).isEqualTo(1);
        assertThat(draft.personCount()).isEqualTo(2);
    }

    @Test
    void setsAreMatchedLikeDishes() {
        VoiceOrderDraft draft = parser.parse("сет американо");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(draft.lines().get(0).candidates().get(0).itemType()).isEqualTo("set");
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(2);
    }

    @Test
    void typoWithinOneEditStillMatches() {
        VoiceOrderDraft draft = parser.parse("филодельфия");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(11);
    }

    @Test
    void emptyPhraseGivesEmptyDraft() {
        VoiceOrderDraft draft = parser.parse("   ");

        assertThat(draft.lines()).isEmpty();
        assertThat(draft.unrecognized()).isEmpty();
        assertThat(draft.personCount()).isNull();
    }
}
