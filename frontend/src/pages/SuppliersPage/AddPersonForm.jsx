import { useState } from "react";
import { API_BASE_URL } from "../../auth";
import styles from "./AddPersonForm.module.css";

const API_PERSON_REGISTER = `${API_BASE_URL}/api/persons/register`;

const initialForm = {
    name: "",
    numDays: "",
    salaryPerDay: "",
    username: "",
    password: "",
    role: "WORKER",
    isActive: true
};

export default function AddPersonForm({ onPersonAdded }) {
    const [form, setForm] = useState(initialForm);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const updateField = (field, value) => {
        setForm((current) => ({ ...current, [field]: value }));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (submitting) return;
        setError("");
        setSubmitting(true);

        try {
            const response = await fetch(API_PERSON_REGISTER, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name: form.name.trim(),
                    numDays: Number.parseInt(form.numDays || "0", 10),
                    salaryPerDay: Number(form.salaryPerDay || 0),
                    username: form.username.trim(),
                    password: form.password,
                    role: form.role,
                    isActive: form.isActive
                })
            });

            const text = await response.text();
            const created = text ? JSON.parse(text) : null;
            if (!response.ok) {
                throw new Error(created?.message || created?.error || `Ошибка сервера (${response.status})`);
            }

            setForm(initialForm);
            await onPersonAdded?.(created);
        } catch (submitError) {
            console.error("Ошибка создания сотрудника:", submitError);
            setError(submitError.message || "Не удалось зарегистрировать сотрудника");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <form className={styles.form} onSubmit={handleSubmit}>
            <fieldset className={styles.fieldset}>
                <legend>Сотрудник</legend>
                <label className={styles.wideField} htmlFor="person-name">
                    Имя и фамилия
                    <input
                        id="person-name"
                        name="name"
                        type="text"
                        autoComplete="name"
                        value={form.name}
                        onChange={(event) => updateField("name", event.target.value)}
                        placeholder="Например, Мария Орлова"
                        required
                    />
                </label>
                <label className={styles.wideField} htmlFor="person-salary-per-day">
                    Ставка за день
                    <span className={styles.inputWithUnit}>
                        <input
                            id="person-salary-per-day"
                            name="salaryPerDay"
                            type="number"
                            min="0"
                            step="0.01"
                            inputMode="decimal"
                            value={form.salaryPerDay}
                            onChange={(event) => updateField("salaryPerDay", event.target.value)}
                            placeholder="2500"
                            required
                        />
                        <span>₽</span>
                    </span>
                </label>
                <label htmlFor="person-days">
                    Начислено дней
                    <input
                        id="person-days"
                        name="numDays"
                        type="number"
                        min="0"
                        step="1"
                        inputMode="numeric"
                        value={form.numDays}
                        onChange={(event) => updateField("numDays", event.target.value)}
                        placeholder="0"
                        required
                    />
                </label>
            </fieldset>

            <fieldset className={styles.fieldset}>
                <legend>Доступ в систему</legend>
                <label htmlFor="person-login">
                    Логин
                    <input
                        id="person-login"
                        name="username"
                        type="text"
                        autoComplete="username"
                        value={form.username}
                        onChange={(event) => updateField("username", event.target.value)}
                        placeholder="m.orlova"
                        required
                    />
                </label>
                <label htmlFor="person-password">
                    Временный пароль
                    <input
                        id="person-password"
                        name="password"
                        type="password"
                        autoComplete="new-password"
                        value={form.password}
                        onChange={(event) => updateField("password", event.target.value)}
                        minLength={6}
                        placeholder="Не менее 6 символов"
                        required
                    />
                </label>
                <label htmlFor="person-role">
                    Роль
                    <select
                        id="person-role"
                        name="role"
                        value={form.role}
                        onChange={(event) => updateField("role", event.target.value)}
                    >
                        <option value="WORKER">Сотрудник</option>
                        <option value="OWNER">Владелец</option>
                    </select>
                </label>
                <label className={styles.switchField}>
                    <input
                        name="isActive"
                        type="checkbox"
                        checked={form.isActive}
                        onChange={(event) => updateField("isActive", event.target.checked)}
                    />
                    <span>
                        <strong>Аккаунт активен</strong>
                        <small>Сотрудник сможет войти сразу после регистрации.</small>
                    </span>
                </label>
            </fieldset>

            {error && <div className={styles.error} role="alert">{error}</div>}

            <button type="submit" className={styles.submitButton} disabled={submitting}>
                {submitting ? "Создаём аккаунт…" : "Добавить в команду"}
            </button>
        </form>
    );
}
