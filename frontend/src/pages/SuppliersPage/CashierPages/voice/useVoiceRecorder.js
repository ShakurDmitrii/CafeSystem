import { useCallback, useEffect, useRef, useState } from "react";
import { MAX_RECORDING_MS } from "./voiceOrderSettings";

const PREFERRED_MIME_TYPES = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus", "audio/ogg"];

function pickMimeType() {
    if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") return "";
    return PREFERRED_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type)) || "";
}

export function isVoiceRecordingSupported() {
    return typeof window !== "undefined"
        && typeof MediaRecorder !== "undefined"
        && Boolean(navigator.mediaDevices?.getUserMedia);
}

function describeMediaError(error) {
    switch (error?.name) {
        case "NotAllowedError":
        case "SecurityError":
            return "Нет доступа к микрофону. Разрешите его в настройках системы.";
        case "NotFoundError":
        case "OverconstrainedError":
            return "Микрофон не найден.";
        case "NotReadableError":
            return "Микрофон занят другим приложением.";
        default:
            return "Не удалось включить микрофон.";
    }
}

/**
 * Запись фразы через MediaRecorder: start() включает микрофон, stop() возвращает Blob,
 * cancel() выбрасывает запись. Дорожка микрофона освобождается после каждой записи.
 */
export function useVoiceRecorder() {
    const [isRecording, setIsRecording] = useState(false);
    const recorderRef = useRef(null);
    const chunksRef = useRef([]);
    const timerRef = useRef(null);
    const stopResolverRef = useRef(null);

    const releaseStream = useCallback(() => {
        const recorder = recorderRef.current;
        recorder?.stream?.getTracks().forEach((track) => track.stop());
        recorderRef.current = null;
        if (timerRef.current) {
            window.clearTimeout(timerRef.current);
            timerRef.current = null;
        }
    }, []);

    const stop = useCallback(() => new Promise((resolve) => {
        const recorder = recorderRef.current;
        if (!recorder || recorder.state === "inactive") {
            setIsRecording(false);
            resolve(null);
            return;
        }
        stopResolverRef.current = resolve;
        recorder.stop();
    }), []);

    const cancel = useCallback(() => {
        const recorder = recorderRef.current;
        stopResolverRef.current = null;
        if (recorder && recorder.state !== "inactive") {
            recorder.ondataavailable = null;
            recorder.onstop = null;
            recorder.stop();
        }
        chunksRef.current = [];
        releaseStream();
        setIsRecording(false);
    }, [releaseStream]);

    const start = useCallback(async () => {
        if (recorderRef.current) return;
        if (!isVoiceRecordingSupported()) {
            throw new Error("Запись звука не поддерживается в этом окружении.");
        }
        let stream;
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                audio: { channelCount: 1, noiseSuppression: true, echoCancellation: true }
            });
        } catch (error) {
            throw new Error(describeMediaError(error));
        }
        const mimeType = pickMimeType();
        const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
        chunksRef.current = [];
        recorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) chunksRef.current.push(event.data);
        };
        recorder.onstop = () => {
            const blob = new Blob(chunksRef.current, { type: recorder.mimeType || mimeType || "audio/webm" });
            chunksRef.current = [];
            releaseStream();
            setIsRecording(false);
            const resolve = stopResolverRef.current;
            stopResolverRef.current = null;
            resolve?.(blob.size > 0 ? blob : null);
        };
        recorderRef.current = recorder;
        recorder.start();
        setIsRecording(true);
        timerRef.current = window.setTimeout(() => {
            // Защита от забытого микрофона: длинная запись останавливается сама.
            if (recorderRef.current === recorder && recorder.state === "recording") {
                stop();
            }
        }, MAX_RECORDING_MS);
    }, [releaseStream, stop]);

    useEffect(() => () => {
        const recorder = recorderRef.current;
        if (recorder && recorder.state !== "inactive") {
            recorder.ondataavailable = null;
            recorder.onstop = null;
            recorder.stop();
        }
        releaseStream();
    }, [releaseStream]);

    return { isRecording, start, stop, cancel };
}
