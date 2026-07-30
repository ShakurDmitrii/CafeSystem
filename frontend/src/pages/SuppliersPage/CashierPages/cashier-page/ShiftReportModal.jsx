import CashierModal from "./CashierModal";
import styles from "../CashierPage.module.css";
import { formatMoney } from "./cashierUtils";

export default function ShiftReportModal({ report, loading, onClose }) {
    return (
        <CashierModal
            title="Отчёт по смене"
            description="Выручка, задержки и позиции в одном журнале."
            onClose={onClose}
            wide
            actions={(
                <button className={styles.primaryButton} type="button" onClick={onClose}>
                    Закрыть отчёт
                </button>
            )}
        >
            {loading ? (
                <div className={styles.stateCard} role="status">Загружаем отчёт…</div>
            ) : report?.error ? (
                <div className={styles.errorBanner} role="alert">{report.error}</div>
            ) : report ? (
                <div className={styles.shiftReport}>
                    <section className={styles.reportIdentity}>
                        <div><span>Смена</span><strong>#{report.shiftId}</strong></div>
                        <div><span>Дата</span><strong>{report.date || "—"}</strong></div>
                        <div><span>Открытие</span><strong>{report.startTime || "—"}</strong></div>
                        <div><span>Закрытие</span><strong>{report.endTime || "—"}</strong></div>
                        <p>Команда: {(report.workers || []).join(", ") || "Не указана"}</p>
                    </section>

                    <section className={styles.reportTotals}>
                        <div><span>Заказов</span><strong>{report.totals?.ordersCount ?? 0}</strong></div>
                        <div><span>Оплачено</span><strong>{report.totals?.paidOrdersCount ?? 0}</strong></div>
                        <div><span>Не оплачено</span><strong>{report.totals?.unpaidOrdersCount ?? 0}</strong></div>
                        <div><span>Выручка</span><strong>{formatMoney(report.totals?.revenue)}</strong></div>
                        <div><span>Не оплачено на сумму</span><strong>{formatMoney(report.totals?.unpaidAmount)}</strong></div>
                        <div><span>Себестоимость</span><strong>{formatMoney(report.totals?.cost)}</strong></div>
                        <div><span>Расходы</span><strong>{formatMoney(report.totals?.expenses)}</strong></div>
                        <div><span>Прибыль</span><strong>{formatMoney(report.totals?.profit)}</strong></div>
                        <div><span>Доставка</span><strong>{formatMoney(report.totals?.deliveryExpense)}</strong></div>
                        <div><span>С задержкой</span><strong>{report.totals?.delayedOrdersCount ?? 0}</strong></div>
                    </section>

                    <section className={styles.reportSection}>
                        <h3>Популярные позиции</h3>
                        {(report.topPositions || []).length === 0 ? (
                            <div className={styles.compactEmpty}>Нет данных по позициям.</div>
                        ) : (
                            <ol>
                                {(report.topPositions || []).slice(0, 10).map((position) => (
                                    <li key={position.dishName}>
                                        <strong>{position.dishName}</strong>
                                        <span>{position.qty} шт.</span>
                                        <span>{formatMoney(position.amount)}</span>
                                    </li>
                                ))}
                            </ol>
                        )}
                    </section>

                    <section className={styles.reportSection}>
                        <h3>Заказы</h3>
                        {(report.orders || []).length === 0 ? (
                            <div className={styles.compactEmpty}>В этой смене нет заказов.</div>
                        ) : (
                            <div className={styles.reportOrders}>
                                {(report.orders || []).map((order) => (
                                    <article key={order.orderId}>
                                        <header>
                                            <strong>#{order.orderId}</strong>
                                            <span>{order.isDelivery ? "Доставка" : "В заведении"}</span>
                                            <span>{order.isPaid ? "Оплачен" : "Не оплачен"}</span>
                                            <strong>{formatMoney(order.orderAmount)}</strong>
                                        </header>
                                        <p>
                                            {order.clientName || "Гость без профиля"}
                                            {order.clientPhone ? ` · ${order.clientPhone}` : ""}
                                        </p>
                                        {Number(order.delayMinutes || 0) > 0 && (
                                            <span className={styles.delayChip}>
                                                Задержка {Number(order.delayMinutes).toFixed(0)} мин
                                            </span>
                                        )}
                                    </article>
                                ))}
                            </div>
                        )}
                    </section>
                </div>
            ) : (
                <div className={styles.compactEmpty}>Отчёт не выбран.</div>
            )}
        </CashierModal>
    );
}
