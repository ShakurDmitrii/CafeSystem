import { useCallback, useEffect, useRef, useState } from "react";
import { applyVoiceDraft, candidateToCartItem, mergeIntoCart } from "./voiceDraft";
import { requestVoiceDraft } from "./voiceOrderApi";
import { getVoiceHotkey, setVoiceHotkey } from "./voiceOrderSettings";
import { useVoiceHotkey } from "./useVoiceHotkey";
import { isVoiceRecordingSupported, useVoiceRecorder } from "./useVoiceRecorder";

/**
 * Связывает запись, горячую клавишу, запрос к серверу и корзину окна выбора блюд.
 * Фазы: idle → recording → processing → idle. Результат последнего распознавания
 * хранится до нового запроса или до закрытия окна.
 */
export function useVoiceOrder({ active, disabled, catalogByKey, setCartItems }) {
    const supported = isVoiceRecordingSupported();
    const [hotkey, setHotkeyState] = useState(getVoiceHotkey);
    const [phase, setPhase] = useState("idle");
    const [error, setError] = useState(null);
    const [result, setResult] = useState(null);
    const [personCount, setPersonCount] = useState(null);
    const recorder = useVoiceRecorder();
    const abortRef = useRef(null);

    const reset = useCallback(() => {
        abortRef.current?.abort();
        abortRef.current = null;
        recorder.cancel();
        setPhase("idle");
        setError(null);
        setResult(null);
        setPersonCount(null);
    }, [recorder]);

    useEffect(() => {
        if (!active) reset();
    }, [active, reset]);

    const finishRecording = useCallback(async () => {
        setPhase("processing");
        const blob = await recorder.stop();
        if (!blob) {
            setPhase("idle");
            setError("Запись оказалась пустой. Попробуйте ещё раз.");
            return;
        }
        const controller = new AbortController();
        abortRef.current = controller;
        try {
            const draft = await requestVoiceDraft(blob, { signal: controller.signal });
            if (controller.signal.aborted) return;
            setCartItems((current) => {
                const applied = applyVoiceDraft(current, draft, catalogByKey);
                setResult({
                    transcript: applied.transcript,
                    added: applied.added,
                    ambiguous: applied.ambiguous,
                    unrecognized: applied.unrecognized,
                    personCount: applied.personCount
                });
                if (applied.personCount) setPersonCount(applied.personCount);
                return applied.cartItems;
            });
            setError(null);
        } catch (requestError) {
            if (requestError?.name === "AbortError") return;
            setError(requestError?.message || "Не удалось распознать запись.");
        } finally {
            if (abortRef.current === controller) abortRef.current = null;
            setPhase("idle");
        }
    }, [recorder, catalogByKey, setCartItems]);

    const toggle = useCallback(async () => {
        if (disabled || !supported) return;
        if (phase === "processing") return;
        if (phase === "recording") {
            await finishRecording();
            return;
        }
        setError(null);
        setResult(null);
        try {
            await recorder.start();
            setPhase("recording");
        } catch (startError) {
            setError(startError?.message || "Не удалось включить микрофон.");
            setPhase("idle");
        }
    }, [disabled, supported, phase, finishRecording, recorder]);

    const cancel = useCallback(() => {
        recorder.cancel();
        setPhase("idle");
    }, [recorder]);

    useVoiceHotkey({
        enabled: active && supported,
        hotkey,
        isRecording: phase === "recording",
        onToggle: toggle,
        onCancel: cancel
    });

    const pickCandidate = useCallback((entry, candidate) => {
        setCartItems((current) => mergeIntoCart(current, candidateToCartItem(candidate, entry.qty, catalogByKey)));
        setResult((current) => {
            if (!current) return current;
            return {
                ...current,
                added: [...current.added, { name: candidate.name, qty: entry.qty }],
                ambiguous: current.ambiguous.filter((item) => item !== entry)
            };
        });
    }, [catalogByKey, setCartItems]);

    const changeHotkey = useCallback((key) => {
        setVoiceHotkey(key);
        setHotkeyState(key);
    }, []);

    return {
        supported,
        phase,
        hotkey,
        error,
        result,
        personCount,
        toggle,
        cancel,
        pickCandidate,
        dismiss: () => setResult(null),
        changeHotkey
    };
}
