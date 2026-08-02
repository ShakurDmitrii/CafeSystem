import { useState } from "react";
import styles from "../ClientsPage.module.css";
import { formatDateTime, formatMoney, getClientDebt } from "./clientUtils";

export default function DebtLedger({
    dutyClients,
    loading,
    totalDebt,
    onPayAll,
    onPayOrder
}) {
    const [amounts, setAmounts] = useState({});
    const [paymentTypes, setPaymentTypes] = useState({});
    const [busyOrderId, setBusyOrderId] = useState(null);

    return (
        <section className={styles.ledgerPanel}>
            <div className={styles.ledgerHeading}>
                <div>
                    <p className={styles.kicker}>Долговая лента</p>
                    <h2>Счета к закрытию</h2>
                    <p>Каждое списание требует подтверждения и сохраняет историю заказа.</p>
                </div>
                <div className={styles.ledgerTotal}>
                    <span>Всего к оплате</span>
                    <strong>{formatMoney(totalDebt)}</strong>
                </div>
            </div>

            {loading ? (
                <div className={styles.stateCard} role="status">Проверяем открытые счета…</div>
            ) : dutyClients.length === 0 ? (
                <div className={`${styles.stateCard} ${styles.successState}`}>
                    <strong>Все счета закрыты</strong>
                    <span>Сейчас в гостевой книге нет неоплаченных заказов.</span>
                </div>
            ) : (
                <div className={styles.debtList}>
                    {dutyClients.map((clientWithDuty, index) => {
                        const client = clientWithDuty.client || clientWithDuty;
                        const orders = clientWithDuty.dutyOrders || [];
                        const total = getClientDebt(clientWithDuty);

                        return (
                            <article key={client.clientId || index} className={styles.debtCard}>
                                <header className={styles.debtCardHeader}>
                                    <div>
                                        <span className={styles.cardId}>Гость #{client.clientId}</span>
                                        <h3>{client.fullName || "Без имени"}</h3>
                                        <p>{client.number || "Телефон не указан"}</p>
                                    </div>
                                    <div className={styles.debtSummary}>
                                        <span>Открытый баланс</span>
                                        <strong>{formatMoney(total)}</strong>
                                        <button
                                            className={styles.dangerButton}
                                            type="button"
                                            onClick={() => onPayAll(client.clientId)}
                                        >
                                            Закрыть весь долг
                                        </button>
                                    </div>
                                </header>

                                {orders.length > 0 && (
                                    <div className={styles.debtOrders}>
                                        {orders.map((order) => (
                                            <div key={order.orderId} className={styles.debtOrder}>
                                                <div className={styles.orderCode}>
                                                    <span>Заказ</span>
                                                    <strong>#{order.orderId}</strong>
                                                </div>
                                                <div className={styles.debtOrderMeta}>
                                                    <span>{formatDateTime(order.date || order.createdAt || order.created_at)}</span>
                                                    {Number(order.timeDelay || 0) > 0 && (
                                                        <span className={styles.warningChip}>
                                                            Задержка {order.timeDelay} мин
                                                        </span>
                                                    )}
                                                </div>
                                                <strong className={styles.debtOrderAmount}>
                                                    {formatMoney(order.amount)}
                                                </strong>
                                                <div className={styles.debtPaymentControls}>
                                                    <label>
                                                        <span>Сумма платежа</span>
                                                        <input
                                                            type="number"
                                                            min="0.01"
                                                            step="0.01"
                                                            max={Number(order.amount) || undefined}
                                                            value={amounts[order.orderId] ?? ""}
                                                            placeholder={String(Number(order.amount) || "")}
                                                            onChange={(event) => setAmounts((current) => ({
                                                                ...current,
                                                                [order.orderId]: event.target.value
                                                            }))}
                                                        />
                                                    </label>
                                                    <select
                                                        aria-label={`Способ оплаты заказа ${order.orderId}`}
                                                        value={paymentTypes[order.orderId] || "cash"}
                                                        onChange={(event) => setPaymentTypes((current) => ({
                                                            ...current,
                                                            [order.orderId]: event.target.value
                                                        }))}
                                                    >
                                                        <option value="cash">Наличные</option>
                                                        <option value="transfer">Перевод</option>
                                                        <option value="card">Карта</option>
                                                    </select>
                                                    <button
                                                        className={styles.textButton}
                                                        type="button"
                                                        onClick={() => setAmounts((current) => ({
                                                            ...current,
                                                            [order.orderId]: String(Number(order.amount) || "")
                                                        }))}
                                                    >
                                                        Вся сумма
                                                    </button>
                                                    <button
                                                        className={styles.secondaryButton}
                                                        type="button"
                                                        disabled={busyOrderId !== null}
                                                        onClick={async () => {
                                                            setBusyOrderId(order.orderId);
                                                            try {
                                                                await onPayOrder(
                                                                    order.orderId,
                                                                    client.clientId,
                                                                    amounts[order.orderId] || order.amount,
                                                                    paymentTypes[order.orderId] || "cash"
                                                                );
                                                            } finally {
                                                                setBusyOrderId(null);
                                                            }
                                                        }}
                                                    >
                                                        {busyOrderId === order.orderId ? "Проводим…" : "Внести платёж"}
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </article>
                        );
                    })}
                </div>
            )}
        </section>
    );
}
