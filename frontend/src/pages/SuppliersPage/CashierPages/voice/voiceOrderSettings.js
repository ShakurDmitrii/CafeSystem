// Настройки голосового набора: горячая клавиша хранится локально на кассовом ПК.
const HOTKEY_STORAGE_KEY = "cafehelp.voice.hotkey";

export const DEFAULT_VOICE_HOTKEY = "CapsLock";

export const VOICE_HOTKEY_OPTIONS = [
    { key: "CapsLock", label: "Caps Lock" },
    { key: "F2", label: "F2" },
    { key: "F4", label: "F4" },
    { key: "ScrollLock", label: "Scroll Lock" },
    { key: "Pause", label: "Pause" }
];

export const MAX_RECORDING_MS = 15000;

export function getVoiceHotkey() {
    try {
        const stored = window.localStorage.getItem(HOTKEY_STORAGE_KEY);
        if (stored && VOICE_HOTKEY_OPTIONS.some((option) => option.key === stored)) {
            return stored;
        }
    } catch {
        // localStorage может быть недоступен (приватный режим, политика WebView)
    }
    return DEFAULT_VOICE_HOTKEY;
}

export function setVoiceHotkey(key) {
    try {
        window.localStorage.setItem(HOTKEY_STORAGE_KEY, key);
    } catch {
        // игнорируем: настройка просто не сохранится между запусками
    }
}

export function hotkeyLabel(key) {
    return VOICE_HOTKEY_OPTIONS.find((option) => option.key === key)?.label || key;
}

/** Клавиша нажата в текстовом поле: голос не включаем, чтобы не мешать вводу. */
export function isTypingTarget(element) {
    if (!element) return false;
    const tag = String(element.tagName || "").toLowerCase();
    if (tag === "textarea" || tag === "select") return true;
    if (tag === "input") {
        const type = String(element.type || "text").toLowerCase();
        return !["button", "submit", "checkbox", "radio", "range", "reset", "file"].includes(type);
    }
    return Boolean(element.isContentEditable);
}
