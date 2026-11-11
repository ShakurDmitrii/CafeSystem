package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.VoiceOrder.VoiceOrderDraft;
import com.shakur.cafehelp.Service.VoiceOrder.VoiceOrderService;
import com.shakur.cafehelp.exception.InvalidOrderRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Голосовой набор заказа. Возвращает только черновик: касса показывает его кассиру,
 * и в заказ позиции попадают после подтверждения обычным путём ({@code POST /api/orders}).
 */
@RestController
@RequestMapping("/api/voice-orders")
public class VoiceOrderController {

    public record ParseRequest(String text) {
    }

    private final VoiceOrderService voiceOrderService;

    public VoiceOrderController(VoiceOrderService voiceOrderService) {
        this.voiceOrderService = voiceOrderService;
    }

    /** Аудио фразы кассира (multipart, поле {@code audio}) → черновик позиций. */
    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> draftFromAudio(@RequestPart("audio") MultipartFile audio) throws IOException {
        try {
            VoiceOrderDraft draft = voiceOrderService.draftFromAudio(
                    audio.getBytes(),
                    audio.getOriginalFilename(),
                    audio.getContentType()
            );
            return ResponseEntity.ok(draft);
        } catch (InvalidOrderRequestException e) {
            return error(e.getMessage());
        }
    }

    /** Уже распознанный текст → черновик. Нужен для отладки и повторного разбора без аудио. */
    @PostMapping(value = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> draftFromText(@RequestBody ParseRequest request) {
        try {
            return ResponseEntity.ok(voiceOrderService.draftFromText(request == null ? null : request.text()));
        } catch (InvalidOrderRequestException e) {
            return error(e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "INVALID_VOICE_REQUEST", "message", message));
    }
}
