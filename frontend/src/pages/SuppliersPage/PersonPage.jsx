import { useState, useEffect } from "react";
import AddPersonForm from "./AddPersonForm";

export default function PersonPage() {
    const [persons, setPersons] = useState([]);

    useEffect(() => {
        loadPersons();
    }, []);

    const loadPersons = () => {
        fetch("http://localhost:8080/api/persons")
            .then(res => res.json())
            .then(data => {
                // Принудительно проверяем, что это массив
                if (Array.isArray(data)) {
                    setPersons(data);
                } else {
                    setPersons([]);
                    console.error("Ожидался массив, пришло:", data);
                }
            })
            .catch(err => console.error("Ошибка загрузки сотрудников:", err));
    };
    return (
        <div>
            <AddPersonForm onPersonAdded={() => loadPersons()} />

            <h2>Список сотрудников</h2>
            <ul>
                {Array.isArray(persons) && persons.map(p => (
                    <li key={p.personID}>
                        {p.name} — {p.salary} ₽ — {p.numDays} дней — {p.salaryPerDay} ₽/день
                    </li>
                ))}
            </ul>
        </div>
    );
}
