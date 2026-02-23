const AUTH_STORAGE_KEY = "cafehelp_auth";

export const API_BASE_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

export function getAuth() {
    try {
        const raw = localStorage.getItem(AUTH_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (!parsed?.accessToken) return null;
        return parsed;
    } catch {
        return null;
    }
}

export function setAuth(auth) {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
}

export function clearAuth() {
    localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function getAccessToken() {
    return getAuth()?.accessToken || null;
}

export function hasRole(auth, roles = []) {
    if (!auth?.role) return false;
    return roles.includes(auth.role);
}

