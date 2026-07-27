import { useRef, useState } from "react";
import { setAuth } from "../../auth";
import { login } from "../../services/authApi";
import styles from "./LoginPage.module.css";

const serviceSteps = [
    {
        label: "Касса",
        description: "Смена и заказы",
        marker: "01"
    },
    {
        label: "Кухня",
        description: "Приготовление",
        marker: "02"
    },
    {
        label: "Склад",
        description: "Остатки и закупки",
        marker: "03"
    }
];

export default function LoginPage({ onSuccess }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [passwordVisible, setPasswordVisible] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [errorField, setErrorField] = useState("");
    const usernameRef = useRef(null);
    const passwordRef = useRef(null);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError("");
        setErrorField("");

        if (!username.trim()) {
            setError("Введите логин, который выдал владелец заведения.");
            setErrorField("username");
            usernameRef.current?.focus();
            return;
        }

        if (!password.trim()) {
            setError("Введите пароль от учётной записи.");
            setErrorField("password");
            passwordRef.current?.focus();
            return;
        }

        setLoading(true);
        try {
            const auth = await login({
                username: username.trim(),
                password
            });
            setAuth(auth);
            onSuccess(auth);
        } catch (loginError) {
            const message = loginError instanceof TypeError
                ? "Нет соединения с сервером. Проверьте сеть и повторите попытку."
                : loginError.message;
            setError(message || "Не удалось войти. Проверьте данные и повторите попытку.");
            setErrorField("credentials");
            passwordRef.current?.focus();
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.page}>
            <section className={styles.story} aria-labelledby="welcome-title">
                <div className={styles.brand}>
                    <span className={styles.brandMark} aria-hidden="true">C</span>
                    <span>
                        <strong translate="no">CafeHelp</strong>
                        <small>Рабочая система кафе</small>
                    </span>
                </div>

                <div className={styles.storyCopy}>
                    <p className={styles.kicker}>Всё заведение в одном ритме</p>
                    <h2 id="welcome-title">Смена начинается здесь</h2>
                    <p>
                        Заказы, кухня и остатки связаны в одном рабочем пространстве.
                    </p>
                </div>

                <ol className={styles.serviceFlow} aria-label="Рабочие разделы CafeHelp">
                    {serviceSteps.map((step) => (
                        <li key={step.label} className={styles.serviceStep}>
                            <span className={styles.stepMarker}>{step.marker}</span>
                            <span>
                                <strong>{step.label}</strong>
                                <small>{step.description}</small>
                            </span>
                        </li>
                    ))}
                </ol>

                <p className={styles.storyFooter}>
                    Доступ к разделам зависит от роли сотрудника.
                </p>
            </section>

            <section className={styles.formPane} aria-labelledby="login-title">
                <div className={styles.formWrap}>
                    <div className={styles.mobileBrand}>
                        <span className={styles.brandMark} aria-hidden="true">C</span>
                        <span>
                            <strong translate="no">CafeHelp</strong>
                            <small>Рабочая система кафе</small>
                        </span>
                    </div>

                    <header className={styles.formHeader}>
                        <p className={styles.kicker}>Вход для сотрудников</p>
                        <h1 id="login-title">Войдите в рабочее пространство</h1>
                        <p>
                            Используйте логин и пароль, выданные владельцем заведения.
                        </p>
                    </header>

                    <form className={styles.form} onSubmit={handleSubmit} noValidate>
                        <div className={styles.field}>
                            <label htmlFor="username">Логин</label>
                            <input
                                ref={usernameRef}
                                id="username"
                                name="username"
                                type="text"
                                value={username}
                                onChange={(event) => {
                                    setUsername(event.target.value);
                                    if (errorField) {
                                        setError("");
                                        setErrorField("");
                                    }
                                }}
                                autoComplete="username"
                                autoCapitalize="none"
                                spellCheck={false}
                                enterKeyHint="next"
                                aria-invalid={errorField === "username" || errorField === "credentials"}
                                aria-describedby={error ? "login-error" : undefined}
                                placeholder="Например, cashier01…"
                            />
                        </div>

                        <div className={styles.field}>
                            <label htmlFor="password">Пароль</label>
                            <div className={styles.passwordControl}>
                                <input
                                    ref={passwordRef}
                                    id="password"
                                    name="password"
                                    type={passwordVisible ? "text" : "password"}
                                    value={password}
                                    onChange={(event) => {
                                        setPassword(event.target.value);
                                        if (errorField) {
                                            setError("");
                                            setErrorField("");
                                        }
                                    }}
                                    autoComplete="current-password"
                                    enterKeyHint="go"
                                    aria-invalid={errorField === "password" || errorField === "credentials"}
                                    aria-describedby={error ? "login-error" : undefined}
                                    placeholder="Введите пароль…"
                                />
                                <button
                                    type="button"
                                    className={styles.passwordToggle}
                                    aria-label={passwordVisible ? "Скрыть пароль" : "Показать пароль"}
                                    aria-pressed={passwordVisible}
                                    onClick={() => setPasswordVisible((visible) => !visible)}
                                >
                                    {passwordVisible ? "Скрыть" : "Показать"}
                                </button>
                            </div>
                        </div>

                        <div className={styles.messageSlot} aria-live="polite">
                            {error && (
                                <div id="login-error" className={styles.error} role="alert">
                                    <span aria-hidden="true">!</span>
                                    <p>{error}</p>
                                </div>
                            )}
                        </div>

                        <button className={styles.submitButton} type="submit" disabled={loading}>
                            {loading && <span className={styles.spinner} aria-hidden="true" />}
                            <span>{loading ? "Входим…" : "Войти"}</span>
                            {!loading && <span className={styles.submitArrow} aria-hidden="true">→</span>}
                        </button>
                    </form>

                    <p className={styles.helpText}>
                        Нет доступа? Обратитесь к владельцу заведения.
                    </p>
                </div>
            </section>
        </div>
    );
}
