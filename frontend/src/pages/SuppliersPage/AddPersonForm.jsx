import { useState } from "react";
import styles from "./AddPersonForm.module.css";

const API = "http://localhost:8080/api/persons/register";

export default function AddPersonForm({ onPersonAdded }) {
    const [name, setName] = useState("");
    const [salary, setSalary] = useState("");
    const [numDays, setNumDays] = useState("");
    const [salaryPerDay, setSalaryPerDay] = useState("");
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [role, setRole] = useState("WORKER");
    const [isActive, setIsActive] = useState(true);
    const [error, setError] = useState("");

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");

        const newPerson = {
            name: name.trim(),
            salary: parseFloat(salary || "0"),
            numDays: parseInt(numDays || "0"),
            salaryPerDay: parseFloat(salaryPerDay || "0"),
            username: username.trim(),
            password,
            role,
            isActive
        };

        try {
            const res = await fetch(API, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(newPerson)
            });
            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data?.message || `Ошибка (${res.status})`);
            }
            const created = await res.json().catch(() => null);

            // Сброс формы
            setName("");
            setSalary("");
            setNumDays("");
            setSalaryPerDay("");
            setUsername("");
            setPassword("");
            setRole("WORKER");
            setIsActive(true);

            if (onPersonAdded) onPersonAdded(created);
        } catch (err) {
            console.error("Ошибка создания сотрудника:", err);
            setError(err.message || "Ошибка создания сотрудника");
        }
    };

    return (
        <form className={styles.form} onSubmit={handleSubmit}>
            <h2>Регистрация сотрудника</h2>

            <label>
                Имя:
                <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                />
            </label>

            <label>
                Зарплата:
                <input
                    type="number"
                    value={salary}
                    onChange={(e) => setSalary(e.target.value)}
                    required
                />
            </label>

            <label>
                Количество дней:
                <input
                    type="number"
                    value={numDays}
                    onChange={(e) => setNumDays(e.target.value)}
                    required
                />
            </label>

            <label>
                Зарплата в день:
                <input
                    type="number"
                    value={salaryPerDay}
                    onChange={(e) => setSalaryPerDay(e.target.value)}
                    required
                />
            </label>

            <label>
                Логин:
                <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                />
            </label>

            <label>
                Пароль:
                <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    minLength={6}
                    required
                />
            </label>

            <label>
                Роль:
                <select value={role} onChange={(e) => setRole(e.target.value)}>
                    <option value="WORKER">WORKER</option>
                    <option value="OWNER">OWNER</option>
                </select>
            </label>

            <label>
                <input
                    type="checkbox"
                    checked={isActive}
                    onChange={(e) => setIsActive(e.target.checked)}
                />
                Аккаунт активен
            </label>

            {error && <div className={styles.error}>{error}</div>}

            <button type="submit" className={styles.btn}>
                Зарегистрировать
            </button>
        </form>
    );
}
