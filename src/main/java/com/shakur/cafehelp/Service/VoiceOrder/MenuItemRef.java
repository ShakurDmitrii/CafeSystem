package com.shakur.cafehelp.Service.VoiceOrder;

/**
 * Позиция меню, против которой сопоставляется произнесённая фраза.
 *
 * @param itemType {@code dish} или {@code set}
 * @param id       идентификатор блюда или набора
 * @param name     отображаемое название
 * @param price    цена для предпросмотра в кассе
 */
public record MenuItemRef(String itemType, int id, String name, Double price) {

    public static final String DISH = "dish";
    public static final String SET = "set";

    public static MenuItemRef dish(int id, String name, Double price) {
        return new MenuItemRef(DISH, id, name, price);
    }

    public static MenuItemRef set(int id, String name, Double price) {
        return new MenuItemRef(SET, id, name, price);
    }
}
