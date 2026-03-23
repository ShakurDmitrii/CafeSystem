import { useState, useEffect } from "react";
import AddPersonForm from "./AddPersonForm";
import styles from "./PersonPage.module.css";

export default function PersonPage() {
    const [persons, setPersons] = useState([]);
    const [workDaysByPerson, setWorkDaysByPerson] = useState({});
    const [deletingPersonId, setDeletingPersonId] = useState(null);
    const [deleteError, setDeleteError] = useState("");
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
                    const workerIds = Array.isArray(s.personIds) && s.personIds.length > 0
                        ? s.personIds
                        : [s.personCode];

                    [...new Set(workerIds.map((id) => Number(id)).filter((id) => Number.isInteger(id) && id >= 0))]
                        .forEach((personId) => {
                            daysMap[personId] = (daysMap[personId] ?? 0) + 1;
                        });
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

    const handleDeletePerson = async (person) => {
        if (!person?.personID && person?.personID !== 0) return;
        if (!window.confirm(`Отправить сотрудника ${person.name} в архив? Он исчезнет из обычных списков, но история смен сохранится.`)) return;

        setDeleteError("");
        setDeletingPersonId(person.personID);
        try {
            const response = await fetch(`http://localhost:8080/api/persons/${person.personID}`, {
                method: "DELETE"
            });

            if (!response.ok) {
                const text = await response.text();
                let message = `Не удалось отправить сотрудника в архив (${response.status})`;
                if (text) {
                    try {
                        const parsed = JSON.parse(text);
                        message = parsed.message || parsed.error || message;
                    } catch {
                        message = text;
                    }
                }
                throw new Error(message);
            }

            setSalaryPayments((prev) => {
                const next = { ...prev };
                delete next[person.personID];
                return next;
            });
            await loadPersons();
        } catch (err) {
            console.error("Ошибка архивирования сотрудника:", err);
            setDeleteError(err.message || "Не удалось отправить сотрудника в архив");
        } finally {
            setDeletingPersonId(null);
        }
    };

    return (
        <div className={styles.page}>
            <section className={styles.hero}>
                <div>
                    <p className={styles.eyebrow}>Команда</p>
                    <h1 className={styles.title}>Персонал и выплаты</h1>
                    <p className={styles.subtitle}>
                        Управляйте сотрудниками в той же тёплой рабочей палитре: добавляйте новых, отслеживайте дни к выплате и архивируйте тех, кто больше не работает.
                    </p>
                </div>
                <div className={styles.heroNote}>
                    <strong>{persons.length}</strong>
                    <span>активных сотрудников сейчас в системе</span>
                </div>
            </section>

            <section className={styles.formSection}>
                <div className={styles.sectionHeading}>
                    <div>
                        <h2>Новый сотрудник</h2>
                        <p>Зарегистрируйте сотрудника и сразу создайте ему аккаунт для входа.</p>
                    </div>
                </div>
                <AddPersonForm onPersonAdded={() => loadPersons()} />
            </section>

            <div className={styles.tableCard}>
                <div className={styles.tableHeader}>
                    <h2>Список сотрудников</h2>
                    <span className={styles.counter}>Всего: {persons.length}</span>
                </div>

                {deleteError && (
                    <div className={styles.errorBanner}>
                        {deleteError}
                    </div>
                )}

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
                                        <div className={styles.actionButtons}>
                                            <button
                                                className={styles.payBtn}
                                                disabled={amountToPay <= 0 || deletingPersonId === p.personID}
                                                onClick={() => handlePaySalary(p, calculatedDays, amountToPay)}
                                            >
                                                Выдать ЗП
                                            </button>
                                            <button
                                                className={styles.deleteBtn}
                                                disabled={deletingPersonId === p.personID}
                                                onClick={() => handleDeletePerson(p)}
                                            >
                                                {deletingPersonId === p.personID ? "Архивируем..." : "В архив"}
                                            </button>
                                        </div>
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
