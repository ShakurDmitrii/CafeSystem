package com.shakur.cafehelp.Service.VoiceOrder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разбор на снимке реального меню кафе (ноябрь 2026): проверяет, что согласованная
 * с кассой фраза даёт ожидаемые позиции при настоящих названиях с их дублями и опечатками.
 */
class VoicePhraseParserRealMenuTest {

    private static final List<MenuItemRef> MENU = List.of(
            MenuItemRef.dish(4, "Запеченная Филадельфия", 0.0),
            MenuItemRef.dish(11, "Филадельфия", 0.0),
            MenuItemRef.dish(60, "Филадельфия спешл", 0.0),
            MenuItemRef.dish(230, "Запеченная Филадельфия с креветками", 0.0),
            MenuItemRef.dish(12, "чай", 0.0),
            MenuItemRef.dish(25, "Икка макки", 0.0),
            MenuItemRef.dish(26, "Магуро макки", 0.0),
            MenuItemRef.dish(27, "Эби макки", 0.0),
            MenuItemRef.dish(28, "Кани макки", 0.0),
            MenuItemRef.dish(29, "Унаги макки", 0.0),
            MenuItemRef.dish(78, "Тори макки", 0.0),
            MenuItemRef.dish(100, "Эби сяки", 0.0),
            MenuItemRef.dish(101, "Эби темпура", 0.0),
            MenuItemRef.dish(124, "Кофе Американо маленький", 0.0),
            MenuItemRef.dish(125, "Кофе Американо большой", 0.0),
            MenuItemRef.dish(126, "Кофе Американо  маленький с молоком", 0.0),
            MenuItemRef.dish(127, "Кофе для дедушки", 0.0),
            MenuItemRef.dish(128, "Большой кофе Американо с молоком", 0.0),
            MenuItemRef.dish(16, "Шаурма", 0.0),
            MenuItemRef.dish(240, "Шаурма двойное мясо", 0.0),
            MenuItemRef.dish(10, "ролл с лососем ", 0.0),
            MenuItemRef.dish(42, "Суши лосось", 0.0),
            MenuItemRef.dish(51, "Суши лосось(сп)", 0.0),
            MenuItemRef.dish(108, "Суши с лососем(зп)", 0.0),
            MenuItemRef.dish(221, "Поке с лососем", 0.0),
            MenuItemRef.dish(236, "Тортилья с лососем", 0.0),
            MenuItemRef.dish(102, "Салмон люкс", 0.0),
            MenuItemRef.dish(241, "Запеченные мидии 5шт", 0.0),
            MenuItemRef.dish(246, "Ким-чи 200 г", 0.0),
            MenuItemRef.dish(247, "Ким-чи 50 г", 0.0),
            MenuItemRef.set(2, "Сет Американо", 0.0),
            MenuItemRef.set(4, "Сет номер 1", 0.0),
            MenuItemRef.set(8, "Сет номер 2", 0.0),
            MenuItemRef.set(12, "сет номер 3", 0.0),
            MenuItemRef.set(13, "сет на двоих", 0.0)
    );

    private final VoicePhraseParser parser = new VoicePhraseParser(MENU);

    @Test
    void cashierExamplePhrase() {
        VoiceOrderDraft draft = parser.parse("филадельфия кофе два эби макки три персоны");

        assertThat(draft.personCount()).isEqualTo(3);
        assertThat(draft.unrecognized()).isEmpty();
        assertThat(draft.lines()).hasSize(3);

        VoiceOrderDraft.Line philadelphia = draft.lines().get(0);
        assertThat(philadelphia.status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(philadelphia.candidates().get(0).id()).isEqualTo(11);
        assertThat(philadelphia.qty()).isEqualTo(1);

        VoiceOrderDraft.Line coffee = draft.lines().get(1);
        assertThat(coffee.status()).isEqualTo(VoiceOrderDraft.Status.AMBIGUOUS);
        assertThat(coffee.qty()).isEqualTo(2);
        assertThat(coffee.candidates()).extracting(VoiceOrderDraft.Candidate::id).contains(124, 125, 127);

        VoiceOrderDraft.Line ebi = draft.lines().get(2);
        assertThat(ebi.status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(ebi.candidates().get(0).id()).isEqualTo(27);
    }

    @Test
    void qualifiedCoffeeIsMatchedDirectly() {
        VoiceOrderDraft draft = parser.parse("кофе американо большой два");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(125);
        assertThat(draft.lines().get(0).qty()).isEqualTo(2);
    }

    @Test
    void numberedSetsAreDistinguishedByTheirNumber() {
        VoiceOrderDraft draft = parser.parse("сет номер два");

        // «два» здесь часть названия, а не количество: слово-число проверяется как часть окна.
        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(8);
        assertThat(draft.lines().get(0).qty()).isEqualTo(1);
    }

    @Test
    void bakedPhiladelphiaIsNotConfusedWithPlainOne() {
        VoiceOrderDraft draft = parser.parse("запеченная филадельфия");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.MATCHED);
        assertThat(draft.lines().get(0).candidates().get(0).id()).isEqualTo(4);
    }

    @Test
    void genericWordWithManyVariantsAsksForClarification() {
        VoiceOrderDraft draft = parser.parse("лосось");

        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).status()).isEqualTo(VoiceOrderDraft.Status.AMBIGUOUS);
        assertThat(draft.lines().get(0).candidates()).hasSizeLessThanOrEqualTo(5);
    }
}
