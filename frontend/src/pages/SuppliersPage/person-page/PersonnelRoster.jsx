import { useState } from "react";
import styles from "../PersonPage.module.css";
import { formatDateTime, formatMoney, getInitials } from "./personUtils";

const toPaymentInput = (value) => {
    const rounded = Math.round((Number(value) || 0) * 100) / 100;
    return rounded > 0 ? String(rounded) : "";
};

function SalaryPaymentControls({ person, disabled, onPaySalary }) {
    const [amount, setAmount] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const numericAmount = Number(amount);
    const isValid = Number.isFinite(numericAmount)
        && numericAmount > 0
        && numericAmount <= person.amountToPay;

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!isValid || disabled || submitting) return;
        setSubmitting(true);
        try {
            const paymentAccepted = await onPaySalary(person, numericAmount);
            if (paymentAccepted) setAmount("");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <form className={styles.salaryPayment} onSubmit={handleSubmit}>
            <label htmlFor={`salary-payment-${person.personID}`}>Сумма выплаты</label>
            <div className={styles.salaryAmountRow}>
                <span className={styles.salaryInputWithUnit}>
                    <input
                        id={`salary-payment-${person.personID}`}
                        name={`salaryPayment-${person.personID}`}
                        type="number"
                        min="0.01"
                        max={person.amountToPay}
                        step="0.01"
                        inputMode="decimal"
                        value={amount}
                        onChange={(event) => setAmount(event.target.value)}
                        placeholder="0"
                        disabled={disabled || submitting || !person.payrollAvailable || person.amountToPay <= 0}
                        aria-describedby={`salary-balance-${person.personID}`}
                    />
                    <span>₽</span>
                </span>
                <button
                    className={styles.fillSalaryButton}
                    type="button"
                    onClick={() => setAmount(toPaymentInput(person.amountToPay))}
                    disabled={disabled || submitting || !person.payrollAvailable || person.amountToPay <= 0}
                >
                    Выдать всё
                </button>
            </div>
            <span id={`salary-balance-${person.personID}`} className={styles.salaryBalance}>
                Остаток: {formatMoney(person.amountToPay)}
            </span>
            <button
                className={styles.payButton}
                type="submit"
                disabled={disabled || submitting || !person.payrollAvailable || !isValid}
            >
                {submitting ? "Проводим…" : "Выдать"}
            </button>
        </form>
    );
}

function PaymentHistory({
    person,
    items,
    loading,
    reversingPaymentId,
    onReversePayment
}) {
    const reversedPaymentIds = new Set(
        items
            .filter((entry) => entry.entryType === "REVERSAL")
            .map((entry) => entry.relatedPaymentId)
    );

    if (loading) {
        return <div className={styles.paymentHistoryState} role="status">Загружаем общий журнал…</div>;
    }
    if (items.length === 0) {
        return <div className={styles.paymentHistoryState}>Выплат пока не было.</div>;
    }

    return (
        <div className={styles.paymentHistoryList}>
            {items.map((entry) => {
                const isReversal = entry.entryType === "REVERSAL";
                const isReversed = !isReversal && reversedPaymentIds.has(entry.paymentId);
                return (
                    <div key={entry.paymentId} className={styles.paymentHistoryRow}>
                        <div>
                            <strong>{isReversal ? "Отмена выплаты" : "Выплата"}</strong>
                            <span>{formatDateTime(entry.createdAt)} · {entry.authorName}</span>
                            {entry.comment ? <small>{entry.comment}</small> : null}
                        </div>
                        <div className={styles.paymentHistoryAmount}>
                            <strong>{isReversal ? "+" : "−"}{formatMoney(entry.amount)}</strong>
                            <span>Остаток {formatMoney(entry.balanceAfter)}</span>
                        </div>
                        {!isReversal ? (
                            <button
                                type="button"
                                className={styles.reversePaymentButton}
                                disabled={isReversed || reversingPaymentId === entry.paymentId}
                                onClick={() => onReversePayment(person, entry)}
                            >
                                {isReversed
                                    ? "Отменена"
                                    : reversingPaymentId === entry.paymentId
                                        ? "Отменяем…"
                                        : "Отменить"}
                            </button>
                        ) : null}
                    </div>
                );
            })}
        </div>
    );
}

export default function PersonnelRoster({
    people,
    totalPeople,
    searchQuery,
    loading,
    deletingPersonId,
    payingPersonId,
    expandedHistoryId,
    loadingHistoryId,
    reversingPaymentId,
    paymentHistory,
    onSearchChange,
    onPaySalary,
    onToggleHistory,
    onReversePayment,
    onEdit,
    onArchive
}) {
    return (
        <section className={styles.rosterPanel}>
            <header className={styles.rosterHeader}>
                <div>
                    <p className={styles.kicker}>Сменный состав</p>
                    <h2>Команда кафе</h2>
                    <p>Можно выдать часть начисленной суммы или заполнить весь остаток одной кнопкой.</p>
                </div>
                <span className={styles.countBadge}>{totalPeople}</span>
            </header>

            <label className={styles.searchField} htmlFor="person-search">
                <span>Поиск по команде</span>
                <input
                    id="person-search"
                    name="q"
                    type="search"
                    autoComplete="off"
                    value={searchQuery}
                    onChange={(event) => onSearchChange(event.target.value)}
                    placeholder="Имя сотрудника…"
                />
            </label>

            {loading ? (
                <div className={styles.stateCard} role="status">Собираем сменную доску…</div>
            ) : people.length === 0 ? (
                <div className={styles.stateCard}>
                    <strong>{searchQuery ? "Сотрудник не найден" : "Команда пока не собрана"}</strong>
                    <span>
                        {searchQuery
                            ? "Проверьте запрос или очистите поле поиска."
                            : "Добавьте первого сотрудника через форму регистрации."}
                    </span>
                </div>
            ) : (
                <div className={styles.rosterList}>
                    {people.map((person) => {
                        const isBusy = deletingPersonId === person.personID
                            || payingPersonId === person.personID;
                        const historyOpen = expandedHistoryId === person.personID;

                        return (
                            <article key={person.personID} className={styles.personCard}>
                                <div className={styles.personIdentity}>
                                    <div className={styles.personBadge} aria-hidden="true">
                                        {getInitials(person.name)}
                                    </div>
                                    <div>
                                        <span className={styles.personCode}>Сотрудник #{person.personID}</span>
                                        <h3>{person.name || "Без имени"}</h3>
                                        <span className={styles.rate}>
                                            Ставка {formatMoney(person.salaryPerDay)} / смену
                                        </span>
                                    </div>
                                </div>

                                <dl className={styles.personMetrics}>
                                    <div>
                                        <dt>Начислено смен</dt>
                                        <dd>{person.accruedShifts}</dd>
                                    </div>
                                    <div>
                                        <dt>Начислено всего</dt>
                                        <dd>{formatMoney(person.accruedAmount)}</dd>
                                    </div>
                                    <div>
                                        <dt>Выплачено</dt>
                                        <dd>{formatMoney(person.totalPaid)}</dd>
                                    </div>
                                    <div>
                                        <dt>Осталось выдать</dt>
                                        <dd>{formatMoney(person.amountToPay)}</dd>
                                    </div>
                                </dl>

                                <div className={styles.personActions}>
                                    <SalaryPaymentControls
                                        person={person}
                                        disabled={isBusy}
                                        onPaySalary={onPaySalary}
                                    />
                                    <div className={styles.personSecondaryActions}>
                                        <button
                                            className={styles.historyButton}
                                            type="button"
                                            onClick={() => onToggleHistory(person.personID)}
                                            aria-expanded={historyOpen}
                                        >
                                            {historyOpen ? "Скрыть историю" : "История выплат"}
                                        </button>
                                        <button
                                            className={styles.editButton}
                                            type="button"
                                            disabled={isBusy}
                                            onClick={() => onEdit(person)}
                                        >
                                            Редактировать
                                        </button>
                                        <button
                                            className={styles.archiveButton}
                                            type="button"
                                            disabled={isBusy}
                                            onClick={() => onArchive(person)}
                                        >
                                            {isBusy ? "Архивируем…" : "В архив"}
                                        </button>
                                    </div>
                                </div>
                                {historyOpen ? (
                                    <PaymentHistory
                                        person={person}
                                        items={paymentHistory[person.personID] || []}
                                        loading={loadingHistoryId === person.personID}
                                        reversingPaymentId={reversingPaymentId}
                                        onReversePayment={onReversePayment}
                                    />
                                ) : null}
                            </article>
                        );
                    })}
                </div>
            )}
        </section>
    );
}
