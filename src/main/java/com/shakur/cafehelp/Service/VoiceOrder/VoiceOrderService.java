package com.shakur.cafehelp.Service.VoiceOrder;

import com.shakur.cafehelp.Controller.PyController.PythonSpeechClient;
import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.DishSetDTO;
import com.shakur.cafehelp.Service.DishService;
import com.shakur.cafehelp.Service.DishSetService;
import com.shakur.cafehelp.exception.InvalidOrderRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Голосовой черновик заказа: аудио → текст (PyModule) → позиции меню (локальный разбор).
 * Сервис ничего не пишет в базу; результат показывается кассиру для подтверждения.
 */
@Service
public class VoiceOrderService {

    private static final int MAX_AUDIO_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TRANSCRIPT_CHARS = 2000;

    private final PythonSpeechClient speechClient;
    private final DishService dishService;
    private final DishSetService dishSetService;

    public VoiceOrderService(PythonSpeechClient speechClient, DishService dishService, DishSetService dishSetService) {
        this.speechClient = speechClient;
        this.dishService = dishService;
        this.dishSetService = dishSetService;
    }

    public VoiceOrderDraft draftFromAudio(byte[] audio, String filename, String contentType) {
        if (audio == null || audio.length == 0) {
            throw new InvalidOrderRequestException("Пустая аудиозапись");
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            throw new InvalidOrderRequestException("Слишком длинная запись");
        }
        List<MenuItemRef> menu = loadMenu();
        List<String> hints = menu.stream().map(MenuItemRef::name).toList();
        PythonSpeechClient.Transcription transcription = speechClient.transcribe(
                audio,
                filename == null || filename.isBlank() ? "phrase.webm" : filename,
                contentType == null || contentType.isBlank() ? "audio/webm" : contentType,
                hints
        );
        return new VoicePhraseParser(menu).parse(transcription.text());
    }

    public VoiceOrderDraft draftFromText(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidOrderRequestException("Пустая фраза");
        }
        if (text.length() > MAX_TRANSCRIPT_CHARS) {
            throw new InvalidOrderRequestException("Слишком длинная фраза");
        }
        return new VoicePhraseParser(loadMenu()).parse(text);
    }

    private List<MenuItemRef> loadMenu() {
        List<MenuItemRef> menu = new ArrayList<>();
        for (DishDTO dish : dishService.getAll()) {
            if (dish.getDishName() != null && !dish.getDishName().isBlank()) {
                menu.add(MenuItemRef.dish(dish.getDishId(), dish.getDishName().trim(), dish.getPrice()));
            }
        }
        for (DishSetDTO set : dishSetService.getAll()) {
            if (set.getSetId() != null && set.getSetName() != null && !set.getSetName().isBlank()) {
                menu.add(MenuItemRef.set(set.getSetId(), set.getSetName().trim(), set.getPrice()));
            }
        }
        return menu;
    }
}
