import { useState } from "react";
import styles from "../PersonPage.module.css";
import { formatDateTime, formatMoney, getInitials } from "./personUtils";

const toPaymentInput = (value) => {
    const rounded = Math.round((Number(value) || 0) * 100) / 100;
    return rounded > 0 ? String(rounded) : "";
};

function SalaryPaymentControls({ person, disabled, onPaySalary }) {
    const [amount, setAmount] = useState("");
    const numericAmount = Number(amount);
    const isValid = Number.isFinite(numericAmount)
        && numericAmount > 0
        && numericAmount <= person.amountToPay;

    const handleSubmit = (event) => {
        event.preventDefault();
        if (!isValid || disabled) return;
        const paymentAccepted = onPaySalary(person, numericAmount);
        if (paymentAccepted) setAmount("");
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
                        disabled={disabled || person.amountToPay <= 0}
                        aria-describedby={`salary-balance-${person.personID}`}
                    />
                    <span>₽</span>
                </span>
                <button
                    className={styles.fillSalaryButton}
                    type="button"
                    onClick={() => setAmount(toPaymentInput(person.amountToPay))}
                    disabled={disabled || person.amountToPay <= 0}
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
                disabled={disabled || !isValid}
            >
                Выдать
            </button>
        </form>
    );
}

export default function PersonnelRoster({
    people,
    totalPeople,
    searchQuery,
    loading,
    deletingPersonId,
    onSearchChange,
    onPaySalary,
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
                        const isBusy = deletingPersonId === person.personID;

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
                                            Ставка {formatMoney(person.salaryPerDay)} / день
                                        </span>
                                    </div>
                                </div>

                                <dl className={styles.personMetrics}>
                                    <div>
                                        <dt>Дней к выплате</dt>
                                        <dd>{person.unpaidDays}</dd>
                                    </div>
                                    <div>
                                        <dt>Осталось выдать</dt>
                                        <dd>{formatMoney(person.amountToPay)}</dd>
                                    </div>
                                    <div>
                                        <dt>Выплачено всего</dt>
                                        <dd>{formatMoney(person.totalPaid)}</dd>
                                    </div>
                                    <div>
                                        <dt>Последняя выплата</dt>
                                        <dd>{formatDateTime(person.lastPaidAt)}</dd>
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
                            </article>
                        );
                    })}
                </div>
            )}
        </section>
    );
}
