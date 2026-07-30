import styles from "../ClientsPage.module.css";
import { formatDateTime, formatMoney, getClientDebt } from "./clientUtils";

export default function DebtLedger({
    dutyClients,
    loading,
    totalDebt,
    onPayAll,
    onPayOrder
}) {
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
                                                <button
                                                    className={styles.secondaryButton}
                                                    type="button"
                                                    onClick={() => onPayOrder(order.orderId, client.clientId, order.amount)}
                                                >
                                                    Оплачен
                                                </button>
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
