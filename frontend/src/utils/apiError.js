// Единое чтение ошибок API: сервер отвечает {"code", "message", "details"},
// а при сбое сети/прокси может прийти HTML или пустое тело.

const STATUS_TEXT = {
    400: "сервер отклонил данные запроса",
    401: "сессия истекла, войдите снова",
    403: "недостаточно прав для этого действия",
    404: "запись не найдена (возможно, её уже удалили)",
    409: "данные уже изменились, обновите страницу и повторите",
    413: "слишком большой файл или запрос",
    429: "слишком много запросов, повторите через несколько секунд",
    500: "внутренняя ошибка сервера",
    502: "сервер недоступен (ошибка шлюза)",
    503: "сервер временно недоступен",
    504: "сервер не ответил вовремя"
};

export function describeHttpStatus(status) {
    return STATUS_TEXT[status] || (status >= 500 ? "ошибка сервера" : `запрос отклонён (код ${status})`);
}

export async function readResponseBody(response) {
    const text = await response.text().catch(() => "");
    if (!text) return { data: null, text: "" };
    try {
        return { data: JSON.parse(text), text };
    } catch {
        return { data: null, text };
    }
}

export function extractErrorMessage(data, text) {
    if (data && typeof data === "object") {
        const message = data.message || data.detail || data.error || data.errorMessage;
        // «Bad Request» / «Internal Server Error» — это не причина, а название статуса.
        if (message && !/^(Bad Request|Internal Server Error|Not Found|Conflict|Forbidden)$/i.test(message)) {
            return String(message);
        }
    }
    if (typeof text === "string" && text && !text.trim().startsWith("<") && text.length < 500) {
        return text.trim();
    }
    return "";
}

export class ApiError extends Error {
    constructor(message, { status, code, details } = {}) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.code = code;
        this.details = details;
    }
}

/**
 * Проверяет ответ и возвращает распарсенное тело. При ошибке бросает ApiError
 * с текстом «<действие>: <причина от сервера или расшифровка статуса>».
 */
export async function ensureOk(response, action) {
    const { data, text } = await readResponseBody(response);
    if (response.ok) return data;
    const reason = extractErrorMessage(data, text) || describeHttpStatus(response.status);
    throw new ApiError(action ? `${action}: ${reason}` : reason, {
        status: response.status,
        code: data?.code,
        details: data?.details
    });
}

export function errorText(error, fallback = "Операция не выполнена") {
    return error?.message || fallback;
}

export function parseJsonSafe(text) {
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}
