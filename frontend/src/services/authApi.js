import { API_BASE_URL } from "../auth";

export async function login(credentials) {
    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials)
    });

    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(
            payload?.message
            || "Не удалось войти. Проверьте логин и пароль."
        );
    }

    return payload;
}
