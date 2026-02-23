import { useState } from "react";
import { API_BASE_URL, setAuth } from "../../auth";
import styles from "./LoginPage.module.css";

export default function LoginPage({ onSuccess }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");

        if (!username.trim() || !password.trim()) {
            setError("Введите логин и пароль");
            return;
        }

        setLoading(true);
        try {
            const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    username: username.trim(),
                    password
                })
            });

            const payload = await res.json().catch(() => ({}));
            if (!res.ok) {
                throw new Error(payload?.message || "Ошибка входа");
            }

            setAuth(payload);
            onSuccess(payload);
        } catch (err) {
            setError(err.message || "Ошибка входа");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.page}>
            <form className={styles.card} onSubmit={handleSubmit}>
                <h2 className={styles.title}>Вход в Cafehelp</h2>

                <label className={styles.label} htmlFor="username">Логин</label>
                <input
                    id="username"
                    className={styles.input}
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                />

                <label className={styles.label} htmlFor="password">Пароль</label>
                <input
                    id="password"
                    type="password"
                    className={styles.input}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                />

                {error && <div className={styles.error}>{error}</div>}

                <button className={styles.button} type="submit" disabled={loading}>
                    {loading ? "Входим..." : "Войти"}
                </button>
            </form>
        </div>
    );
}

