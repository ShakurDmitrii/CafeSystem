import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import AddPersonForm from "./AddPersonForm";
import styles from "./PersonPage.module.css";
import EmployeeEditorModal from "./person-page/EmployeeEditorModal";
import PersonnelRoster from "./person-page/PersonnelRoster";
import TeamHero from "./person-page/TeamHero";
import {
    applySalaryPayment,
    calculateSalaryBalance,
    formatMoney
} from "./person-page/personUtils";

const API_PERSONS = `${API_BASE_URL}/api/persons`;
const API_SHIFTS = `${API_BASE_URL}/api/shifts`;
const SALARY_STORAGE_KEY = "salaryPayments";

const fetchArray = async (url) => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Ошибка сервера (${response.status})`);
    const data = await response.json();
    return Array.isArray(data) ? data : [];
};

export default function PersonPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [persons, setPersons] = useState([]);
    const [workDaysByPerson, setWorkDaysByPerson] = useState({});
    const [closedShifts, setClosedShifts] = useState(0);
    const [loading, setLoading] = useState(true);
    const [deletingPersonId, setDeletingPersonId] = useState(null);
    const [editingPerson, setEditingPerson] = useState(null);
    const [savingPerson, setSavingPerson] = useState(false);
    const [editorError, setEditorError] = useState("");
    const [message, setMessage] = useState(null);
    const [searchQuery, setSearchQuery] = useState(searchParams.get("q") || "");
    const [salaryPayments, setSalaryPayments] = useState(() => {
        try {
            const raw = localStorage.getItem(SALARY_STORAGE_KEY);
            return raw ? JSON.parse(raw) : {};
        } catch {
            return {};
        }
    });

    useEffect(() => {
        localStorage.setItem(SALARY_STORAGE_KEY, JSON.stringify(salaryPayments));
    }, [salaryPayments]);

    const loadPersons = async () => {
        setLoading(true);
        const [personsResult, shiftsResult] = await Promise.allSettled([
            fetchArray(API_PERSONS),
            fetchArray(API_SHIFTS)
        ]);

        if (personsResult.status === "fulfilled") {
            setPersons(personsResult.value);
        } else {
            console.error("Ошибка загрузки сотрудников:", personsResult.reason);
            setPersons([]);
            setMessage({ type: "error", text: "Не удалось загрузить команду. Обновите страницу." });
        }

        if (shiftsResult.status === "fulfilled") {
            const finishedShifts = shiftsResult.value.filter((shift) => shift?.endTime);
            const daysMap = new Map();

            finishedShifts.forEach((shift) => {
                const rawIds = Array.isArray(shift.personIds) && shift.personIds.length > 0
                    ? shift.personIds
                    : [shift.personCode];
                const workerIds = [...new Set(
                    rawIds
                        .map((id) => Number(id))
                        .filter((id) => Number.isInteger(id) && id >= 0)
                )];

                workerIds.forEach((personId) => {
                    daysMap.set(personId, (daysMap.get(personId) || 0) + 1);
                });
            });

            setClosedShifts(finishedShifts.length);
            setWorkDaysByPerson(Object.fromEntries(daysMap));
        } else {
            console.error("Ошибка загрузки смен:", shiftsResult.reason);
            setClosedShifts(0);
            setWorkDaysByPerson({});
            if (personsResult.status === "fulfilled") {
                setMessage({
                    type: "error",
                    text: "Команда загружена, но смены недоступны — начисления могут быть неполными."
                });
            }
        }

        setLoading(false);
    };

    useEffect(() => {
        loadPersons();
        // Первый запрос выполняется один раз при открытии страницы.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const roster = useMemo(() => persons.map((person) => {
        const workedDays = workDaysByPerson[person.personID] ?? (Number(person.numDays) || 0);
        const salaryPerDay = Number(person.salaryPerDay) || 0;
        const payment = salaryPayments[person.personID] || {};
        const salaryBalance = calculateSalaryBalance({
            workedDays,
            salaryPerDay,
            payment
        });

        return {
            ...person,
            workedDays,
            salaryPerDay,
            ...salaryBalance
        };
    }), [persons, salaryPayments, workDaysByPerson]);

    const filteredRoster = useMemo(() => {
        const query = searchQuery.trim().toLocaleLowerCase("ru-RU");
        if (!query) return roster;
        return roster.filter((person) =>
            String(person.name || "").toLocaleLowerCase("ru-RU").includes(query)
        );
    }, [roster, searchQuery]);

    const amountDue = useMemo(
        () => roster.reduce((total, person) => total + person.amountToPay, 0),
        [roster]
    );

    const handleSearchChange = (value) => {
        setSearchQuery(value);
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (value.trim()) next.set("q", value.trim());
            else next.delete("q");
            return next;
        }, { replace: true });
    };

    const handlePaySalary = (person, amount) => {
        const normalizedAmount = Math.round((Number(amount) || 0) * 100) / 100;
        const normalizedDue = Math.round(person.amountToPay * 100) / 100;
        if (normalizedAmount <= 0 || normalizedAmount > normalizedDue) {
            setMessage({
                type: "error",
                text: `Введите сумму от 0,01 ₽ до ${formatMoney(person.amountToPay)}.`
            });
            return false;
        }
        if (!window.confirm(
            `Выдать ${person.name} ${formatMoney(normalizedAmount)}? После выплаты останется ${formatMoney(normalizedDue - normalizedAmount)}.`
        )) return false;

        setSalaryPayments((current) => {
            const previous = current[person.personID] || {};
            return {
                ...current,
                [person.personID]: applySalaryPayment({
                    payment: previous,
                    amount: normalizedAmount,
                    workedDays: person.workedDays,
                    amountToPay: person.amountToPay
                })
            };
        });
        setMessage({
            type: "success",
            text: `${person.name}: выплата ${formatMoney(normalizedAmount)} отмечена.`
        });
        return true;
    };

    const handleOpenEditor = (person) => {
        setEditorError("");
        setEditingPerson(person);
    };

    const handleSavePerson = async (values) => {
        if (!editingPerson || savingPerson) return;
        setEditorError("");
        setSavingPerson(true);

        try {
            const response = await fetch(`${API_PERSONS}/${editingPerson.personID}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(values)
            });
            const text = await response.text();
            let updated = null;
            try {
                updated = text ? JSON.parse(text) : null;
            } catch {
                updated = null;
            }
            if (!response.ok) {
                throw new Error(updated?.message || updated?.error || text || `Ошибка сервера (${response.status})`);
            }

            setEditingPerson(null);
            await loadPersons();
            setMessage({
                type: "success",
                text: `Данные и зарплата сотрудника ${updated?.name || values.name} обновлены.`
            });
        } catch (error) {
            console.error("Ошибка обновления сотрудника:", error);
            setEditorError(error.message || "Не удалось сохранить изменения.");
        } finally {
            setSavingPerson(false);
        }
    };

    const handleDeletePerson = async (person) => {
        if (person?.personID === undefined || person?.personID === null) return;
        if (!window.confirm(
            `Отправить ${person.name} в архив? Сотрудник исчезнет из обычных списков, но история смен сохранится.`
        )) return;

        setMessage(null);
        setDeletingPersonId(person.personID);
        try {
            const response = await fetch(`${API_PERSONS}/${person.personID}`, { method: "DELETE" });
            const text = await response.text();
            if (!response.ok || text.trim() === "false") {
                let details = "";
                try {
                    const data = text ? JSON.parse(text) : null;
                    details = data?.message || data?.error || "";
                } catch {
                    details = text;
                }
                throw new Error(details || `Ошибка сервера (${response.status})`);
            }

            setSalaryPayments((current) => {
                const next = { ...current };
                delete next[person.personID];
                return next;
            });
            await loadPersons();
            setMessage({ type: "success", text: `${person.name} перемещён в архив.` });
        } catch (error) {
            console.error("Ошибка архивирования сотрудника:", error);
            setMessage({ type: "error", text: `Не удалось архивировать сотрудника: ${error.message}` });
        } finally {
            setDeletingPersonId(null);
        }
    };

    return (
        <div className={styles.page}>
            <TeamHero
                peopleCount={persons.length}
                closedShifts={closedShifts}
                amountDue={amountDue}
            />

            {message && (
                <div
                    className={`${styles.message} ${message.type === "error" ? styles.messageError : styles.messageSuccess}`}
                    role={message.type === "error" ? "alert" : "status"}
                >
                    {message.text}
                </div>
            )}

            <div className={styles.workspace}>
                <aside className={styles.registrationPanel}>
                    <div className={styles.panelHeading}>
                        <div>
                            <p className={styles.kicker}>Новый пропуск</p>
                            <h2>Добавить сотрудника</h2>
                        </div>
                        <span className={styles.stepMark}>01</span>
                    </div>
                    <p className={styles.panelLead}>
                        Регистрация создаст сотрудника и отдельный аккаунт для входа.
                    </p>
                    <AddPersonForm
                        onPersonAdded={async () => {
                            await loadPersons();
                            setMessage({ type: "success", text: "Сотрудник добавлен в команду." });
                        }}
                    />
                </aside>

                <PersonnelRoster
                    people={filteredRoster}
                    totalPeople={persons.length}
                    searchQuery={searchQuery}
                    loading={loading}
                    deletingPersonId={deletingPersonId}
                    onSearchChange={handleSearchChange}
                    onPaySalary={handlePaySalary}
                    onEdit={handleOpenEditor}
                    onArchive={handleDeletePerson}
                />
            </div>

            {editingPerson ? (
                <EmployeeEditorModal
                    person={editingPerson}
                    saving={savingPerson}
                    error={editorError}
                    onSave={handleSavePerson}
                    onClose={() => {
                        if (!savingPerson) setEditingPerson(null);
                    }}
                />
            ) : null}
        </div>
    );
}
