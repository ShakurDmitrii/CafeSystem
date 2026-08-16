import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import AddPersonForm from "./AddPersonForm";
import styles from "./PersonPage.module.css";
import EmployeeEditorModal from "./person-page/EmployeeEditorModal";
import PersonnelRoster from "./person-page/PersonnelRoster";
import TeamHero from "./person-page/TeamHero";
import { formatMoney } from "./person-page/personUtils";

const API_PERSONS = `${API_BASE_URL}/api/persons`;
const API_SHIFTS = `${API_BASE_URL}/api/shifts`;
const API_PAYROLL = `${API_BASE_URL}/api/v1/payroll`;
const LEGACY_SALARY_STORAGE_KEY = "salaryPayments";

const fetchArray = async (url) => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Ошибка сервера (${response.status})`);
    const data = await response.json();
    return Array.isArray(data) ? data : [];
};

const parseResponse = async (response) => {
    const text = await response.text();
    let data = null;
    try {
        data = text ? JSON.parse(text) : null;
    } catch {
        data = null;
    }
    if (!response.ok) {
        throw new Error(data?.message || data?.detail || data?.error || text || `Ошибка сервера (${response.status})`);
    }
    return data;
};

const createIdempotencyKey = (prefix) => {
    const value = window.crypto?.randomUUID?.()
        || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    return `${prefix}-${value}`;
};

export default function PersonPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [persons, setPersons] = useState([]);
    const [payrollByPerson, setPayrollByPerson] = useState({});
    const [closedShifts, setClosedShifts] = useState(0);
    const [loading, setLoading] = useState(true);
    const [deletingPersonId, setDeletingPersonId] = useState(null);
    const [editingPerson, setEditingPerson] = useState(null);
    const [savingPerson, setSavingPerson] = useState(false);
    const [editorError, setEditorError] = useState("");
    const [message, setMessage] = useState(null);
    const [searchQuery, setSearchQuery] = useState(searchParams.get("q") || "");
    const [payingPersonId, setPayingPersonId] = useState(null);
    const [expandedHistoryId, setExpandedHistoryId] = useState(null);
    const [loadingHistoryId, setLoadingHistoryId] = useState(null);
    const [reversingPaymentId, setReversingPaymentId] = useState(null);
    const [paymentHistory, setPaymentHistory] = useState({});

    const loadPersons = async () => {
        setLoading(true);
        const [personsResult, shiftsResult, payrollResult] = await Promise.allSettled([
            fetchArray(API_PERSONS),
            fetchArray(API_SHIFTS),
            fetchArray(`${API_PAYROLL}/employees`)
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
            setClosedShifts(finishedShifts.length);
        } else {
            console.error("Ошибка загрузки смен:", shiftsResult.reason);
            setClosedShifts(0);
            if (personsResult.status === "fulfilled") {
                setMessage({
                    type: "error",
                    text: "Команда загружена, но смены недоступны — начисления могут быть неполными."
                });
            }
        }

        if (payrollResult.status === "fulfilled") {
            setPayrollByPerson(Object.fromEntries(
                payrollResult.value.map((summary) => [summary.personId, summary])
            ));
            localStorage.removeItem(LEGACY_SALARY_STORAGE_KEY);
        } else {
            console.error("Ошибка загрузки выплат:", payrollResult.reason);
            setPayrollByPerson({});
            setMessage({
                type: "error",
                text: "Не удалось загрузить серверные начисления. Выплаты временно недоступны."
            });
        }

        setLoading(false);
    };

    useEffect(() => {
        loadPersons();
        // Первый запрос выполняется один раз при открытии страницы.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const roster = useMemo(() => persons.map((person) => {
        const payroll = payrollByPerson[person.personID];

        return {
            ...person,
            salaryPerDay: Number(payroll?.dailyRate ?? person.salaryPerDay) || 0,
            accruedShifts: Number(payroll?.accruedShifts) || 0,
            accruedAmount: Number(payroll?.accruedAmount) || 0,
            amountToPay: Number(payroll?.balance) || 0,
            totalPaid: Number(payroll?.paidAmount) || 0,
            lastPaidAt: payroll?.lastPaidAt || null,
            payrollAvailable: Boolean(payroll)
        };
    }), [persons, payrollByPerson]);

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

    const handlePaySalary = async (person, amount) => {
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

        setPayingPersonId(person.personID);
        setMessage(null);
        try {
            const response = await fetch(`${API_PAYROLL}/employees/${person.personID}/payments`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    amount: normalizedAmount,
                    idempotencyKey: createIdempotencyKey(`salary-${person.personID}`)
                })
            });
            await parseResponse(response);
            await loadPersons();
            if (expandedHistoryId === person.personID) {
                await loadPaymentHistory(person.personID, true);
            }
            setMessage({
                type: "success",
                text: `${person.name}: выплата ${formatMoney(normalizedAmount)} сохранена в общем журнале.`
            });
            return true;
        } catch (error) {
            console.error("Ошибка выплаты зарплаты:", error);
            setMessage({ type: "error", text: error.message || "Не удалось провести выплату." });
            return false;
        } finally {
            setPayingPersonId(null);
        }
    };

    const loadPaymentHistory = async (personId, force = false) => {
        if (!force && paymentHistory[personId]) return;
        setLoadingHistoryId(personId);
        try {
            const response = await fetch(`${API_PAYROLL}/employees/${personId}/payments?page=0&size=50`);
            const data = await parseResponse(response);
            setPaymentHistory((current) => ({ ...current, [personId]: data?.items || [] }));
        } catch (error) {
            console.error("Ошибка загрузки истории выплат:", error);
            setMessage({ type: "error", text: error.message || "Не удалось загрузить историю выплат." });
        } finally {
            setLoadingHistoryId(null);
        }
    };

    const handleToggleHistory = async (personId) => {
        if (expandedHistoryId === personId) {
            setExpandedHistoryId(null);
            return;
        }
        setExpandedHistoryId(personId);
        await loadPaymentHistory(personId);
    };

    const handleReversePayment = async (person, payment) => {
        const reason = window.prompt(
            `Укажите причину отмены выплаты ${formatMoney(payment.amount)} сотруднику ${person.name}.`
        );
        if (!reason?.trim()) return;
        if (!window.confirm("Создать обратную проводку? Исходная запись останется в истории.")) return;

        setReversingPaymentId(payment.paymentId);
        setMessage(null);
        try {
            const response = await fetch(`${API_PAYROLL}/payments/${payment.paymentId}/reversals`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    idempotencyKey: createIdempotencyKey(`salary-reversal-${payment.paymentId}`),
                    comment: reason.trim()
                })
            });
            await parseResponse(response);
            await Promise.all([loadPersons(), loadPaymentHistory(person.personID, true)]);
            setMessage({ type: "success", text: "Выплата отменена обратной проводкой." });
        } catch (error) {
            console.error("Ошибка отмены выплаты:", error);
            setMessage({ type: "error", text: error.message || "Не удалось отменить выплату." });
        } finally {
            setReversingPaymentId(null);
        }
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
                    payingPersonId={payingPersonId}
                    expandedHistoryId={expandedHistoryId}
                    loadingHistoryId={loadingHistoryId}
                    reversingPaymentId={reversingPaymentId}
                    paymentHistory={paymentHistory}
                    onSearchChange={handleSearchChange}
                    onPaySalary={handlePaySalary}
                    onToggleHistory={handleToggleHistory}
                    onReversePayment={handleReversePayment}
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
