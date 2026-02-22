import { useState, useEffect } from "react";
import AddPersonForm from "./AddPersonForm";
import styles from "./PersonPage.module.css";

export default function PersonPage() {
    const [persons, setPersons] = useState([]);
    const [workDaysByPerson, setWorkDaysByPerson] = useState({});
    const [salaryPayments, setSalaryPayments] = useState(() => {
        try {
            const raw = localStorage.getItem("salaryPayments");
            return raw ? JSON.parse(raw) : {};
        } catch {
            return {};
        }
    });

    useEffect(() => {
        loadPersons();
    }, []);

    useEffect(() => {
        localStorage.setItem("salaryPayments", JSON.stringify(salaryPayments));
    }, [salaryPayments]);

    const loadPersons = async () => {
        try {
            const [personsRes, shiftsRes] = await Promise.all([
                fetch("http://localhost:8080/api/persons"),
                fetch("http://localhost:8080/api/shifts")
            ]);

            const personsData = await personsRes.json();
            const shiftsData = await shiftsRes.json().catch(() => []);

            if (!Array.isArray(personsData)) {
                setPersons([]);
                setWorkDaysByPerson({});
                console.error("Ожидался массив сотрудников, пришло:", personsData);
                return;
            }

            setPersons(personsData);

            const shiftsArray = Array.isArray(shiftsData) ? shiftsData : [];
            const daysMap = {};

            shiftsArray
                .filter(s => s && s.endTime) // считаем только закрытые смены
                .forEach(s => {
                    const personKey = s.personCode;
                    if (personKey == null) return;
                    daysMap[personKey] = (daysMap[personKey] ?? 0) + 1;
                });

            setWorkDaysByPerson(daysMap);
        } catch (err) {
            console.error("Ошибка загрузки сотрудников/смен:", err);
            setPersons([]);
            setWorkDaysByPerson({});
        }
    };

    const formatMoney = (value) => {
        const amount = Number(value) || 0;
        return amount.toLocaleString("ru-RU");
    };

    const formatDateTime = (iso) => {
        if (!iso) return "—";
        return new Date(iso).toLocaleString("ru-RU");
    };

    const handlePaySalary = (person, workedDays, amountToPay) => {
        if (amountToPay <= 0) return;
        if (!window.confirm(`Выдать ЗП сотруднику ${person.name}: ${formatMoney(amountToPay)} ₽?`)) return;

        setSalaryPayments(prev => {
            const current = prev[person.personID] ?? {};
            return {
                ...prev,
                [person.personID]: {
                    paidDays: workedDays,
                    totalPaid: (Number(current.totalPaid) || 0) + amountToPay,
                    lastPaidAt: new Date().toISOString()
                }
            };
        });
    };

    return (
        <div className={styles.page}>
            <AddPersonForm onPersonAdded={() => loadPersons()} />

            <div className={styles.tableCard}>
                <div className={styles.tableHeader}>
                    <h2>Список сотрудников</h2>
                    <span className={styles.counter}>Всего: {persons.length}</span>
                </div>

                <div className={styles.tableWrap}>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>Сотрудник</th>
                            <th>ЗП/день</th>
                            <th>Дней к выплате</th>
                            <th>К выплате</th>
                            <th>Выплачено всего</th>
                            <th>Последняя выплата</th>
                            <th>Действие</th>
                        </tr>
                        </thead>
                        <tbody>
                        {Array.isArray(persons) && persons.length > 0 ? persons.map(p => {
                            const calculatedDays = workDaysByPerson[p.personID] ?? p.numDays ?? 0;
                            const salaryPerDay = Number(p.salary) || Number(p.salaryPerDay) || 0;
                            const paidDays = Math.min(
                                Number(salaryPayments[p.personID]?.paidDays) || 0,
                                calculatedDays
                            );
                            const unpaidDays = Math.max(0, calculatedDays - paidDays);
                            const amountToPay = salaryPerDay * unpaidDays;
                            const totalPaid = Number(salaryPayments[p.personID]?.totalPaid) || 0;
                            const lastPaidAt = salaryPayments[p.personID]?.lastPaidAt;
                            return (
                                <tr key={p.personID}>
                                    <td className={styles.nameCell}>{p.name}</td>
                                    <td>{formatMoney(salaryPerDay)} ₽</td>
                                    <td>
                                        <span className={styles.daysBadge}>{unpaidDays}</span>
                                    </td>
                                    <td className={styles.totalCell}>{formatMoney(amountToPay)} ₽</td>
                                    <td>{formatMoney(totalPaid)} ₽</td>
                                    <td>{formatDateTime(lastPaidAt)}</td>
                                    <td>
                                        <button
                                            className={styles.payBtn}
                                            disabled={amountToPay <= 0}
                                            onClick={() => handlePaySalary(p, calculatedDays, amountToPay)}
                                        >
                                            Выдать ЗП
                                        </button>
                                    </td>
                                </tr>
                            );
                        }) : (
                            <tr>
                                <td colSpan="8" className={styles.emptyRow}>Сотрудники пока не добавлены</td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
