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

        const response = await originalFetch(input, { ...init, headers });

        if (response.status === 401) {
            clearAuth();
            window.dispatchEvent(new CustomEvent("auth:unauthorized"));
        }

        return response;
    };
}

