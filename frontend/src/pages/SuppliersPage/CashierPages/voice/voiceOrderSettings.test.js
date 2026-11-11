import { DEFAULT_VOICE_HOTKEY, getVoiceHotkey, hotkeyLabel, isTypingTarget, setVoiceHotkey } from "./voiceOrderSettings";

afterEach(() => {
    window.localStorage.clear();
});

test("default hotkey is Caps Lock and unknown stored values are ignored", () => {
    expect(getVoiceHotkey()).toBe("CapsLock");
    window.localStorage.setItem("cafehelp.voice.hotkey", "KeyQ");
    expect(getVoiceHotkey()).toBe(DEFAULT_VOICE_HOTKEY);
});

test("chosen hotkey is persisted and labelled", () => {
    setVoiceHotkey("F2");
    expect(getVoiceHotkey()).toBe("F2");
    expect(hotkeyLabel("CapsLock")).toBe("Caps Lock");
});

test("hotkey is ignored while typing in text fields but not on buttons", () => {
    const text = document.createElement("input");
    const search = document.createElement("input");
    search.type = "search";
    const button = document.createElement("button");
    const number = document.createElement("input");
    number.type = "number";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    const textarea = document.createElement("textarea");

    expect(isTypingTarget(text)).toBe(true);
    expect(isTypingTarget(search)).toBe(true);
    expect(isTypingTarget(number)).toBe(true);
    expect(isTypingTarget(textarea)).toBe(true);
    expect(isTypingTarget(button)).toBe(false);
    expect(isTypingTarget(checkbox)).toBe(false);
    expect(isTypingTarget(document.body)).toBe(false);
    expect(isTypingTarget(null)).toBe(false);
});
