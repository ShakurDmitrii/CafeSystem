import { useEffect } from "react";
import { isTypingTarget } from "./voiceOrderSettings";

/**
 * Горячая клавиша голосового набора в режиме переключения: первое нажатие включает
 * запись, второе — завершает. Esc во время записи отменяет её.
 *
 * Слушатель висит на фазе перехвата, чтобы Esc при записи не дошёл до окна выбора блюд
 * и не закрыл его. В текстовых полях клавиша не перехватывается.
 */
export function useVoiceHotkey({ enabled, hotkey, isRecording, onToggle, onCancel }) {
    useEffect(() => {
        if (!enabled) return undefined;

        const handleKeyDown = (event) => {
            if (event.repeat) return;
            if (event.key === "Escape" && isRecording) {
                event.preventDefault();
                event.stopPropagation();
                onCancel?.();
                return;
            }
            if (event.key !== hotkey) return;
            if (isTypingTarget(document.activeElement)) return;
            event.preventDefault();
            event.stopPropagation();
            onToggle?.();
        };

        document.addEventListener("keydown", handleKeyDown, true);
        return () => document.removeEventListener("keydown", handleKeyDown, true);
    }, [enabled, hotkey, isRecording, onToggle, onCancel]);
}
