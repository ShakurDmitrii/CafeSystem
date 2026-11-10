import { API_BASE_URL } from "../../../../auth";

const API_VOICE_DRAFT = `${API_BASE_URL}/api/voice-orders/draft`;

export class VoiceOrderError extends Error {
    constructor(message, { status, code } = {}) {
        super(message);
        this.name = "VoiceOrderError";
        this.status = status;
        this.code = code;
    }
}

function describeFailure(status, body) {
    if (body?.message) return body.message;
    if (status === 503 || status === 504) return "Распознавание речи сейчас недоступно";
    if (status === 413) return "Запись слишком длинная";
    return `Не удалось распознать запись (${status})`;
}

/** Отправляет запись фразы на сервер и возвращает черновик позиций. */
export async function requestVoiceDraft(blob, { signal } = {}) {
    const extension = blob.type.includes("ogg") ? "ogg" : blob.type.includes("wav") ? "wav" : "webm";
    const form = new FormData();
    form.append("audio", blob, `phrase.${extension}`);

    const response = await fetch(API_VOICE_DRAFT, { method: "POST", body: form, signal });
    let body = null;
    try {
        body = await response.json();
    } catch {
        body = null;
    }
    if (!response.ok) {
        throw new VoiceOrderError(describeFailure(response.status, body), { status: response.status, code: body?.code });
    }
    return body;
}
