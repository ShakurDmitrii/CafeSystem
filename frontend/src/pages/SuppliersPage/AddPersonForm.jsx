import { useState } from "react";
import styles from "./AddPersonForm.module.css";

const API = "http://localhost:8080/api/persons";

export default function AddPersonForm({ onPersonAdded }) {
    const [name, setName] = useState("");
    const [salary, setSalary] = useState("");
    const [numDays, setNumDays] = useState("");
    const [salaryPerDay, setSalaryPerDay] = useState("");

    const handleSubmit = async (e) => {
        e.preventDefault();

        const newPerson = {
            name,
            salary: parseFloat(salary),
            numDays: parseInt(numDays),
            salaryPerDay: parseFloat(salaryPerDay)
        };

        try {
            const res = await fetch(API, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(newPerson)
            });
            const created = await res.json();

            // Сброс формы
            setName("");
            setSalary("");
            setNumDays("");
            setSalaryPerDay("");

            if (onPersonAdded) onPersonAdded(created);
        } catch (err) {
            console.error("Ошибка создания сотрудника:", err);
        }
    };

    return (
        <form className={styles.form} onSubmit={handleSubmit}>
            <h2>Создать сотрудника</h2>

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

            <button type="submit" className={styles.btn}>
                Создать
            </button>
        </form>
    );
}
