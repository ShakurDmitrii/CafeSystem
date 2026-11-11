import { API_BASE_URL, clearAuth, getAccessToken } from "./auth";

function isBackendRequest(url) {
    if (typeof url !== "string") return false;
    if (url.startsWith(API_BASE_URL)) return true;
    return (
        url.startsWith("/api/") ||
        url.startsWith("/warehouses") ||
        url.startsWith("/movements")
    );
}

export function installAuthFetchInterceptor() {
    if (window.__AUTH_FETCH_INTERCEPTOR_INSTALLED__) return;
    window.__AUTH_FETCH_INTERCEPTOR_INSTALLED__ = true;

    const originalFetch = window.fetch.bind(window);

    window.fetch = async (input, init = {}) => {
        const url = typeof input === "string" ? input : input?.url || "";
        const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));

        if (isBackendRequest(url) && !headers.has("Authorization")) {
            const token = getAccessToken();
            if (token) {
                headers.set("Authorization", `Bearer ${token}`);
            }
        }

        let response;
        try {
            response = await originalFetch(input, { ...init, headers });
        } catch (error) {
            if (error?.name === "AbortError" || !isBackendRequest(url)) throw error;
            // Вместо «Failed to fetch» объясняем, что именно не так.
            const networkError = new Error(
                "Нет связи с сервером CafeHelp: проверьте, что сервер запущен и компьютер подключён к сети"
            );
            networkError.cause = error;
            throw networkError;
        }

        if (response.status === 401) {
            clearAuth();
            window.dispatchEvent(new CustomEvent("auth:unauthorized"));
        }

        return response;
    };
}
