import OrderCard from "../OrderCard";
import styles from "../CashierPage.module.css";

const OrderGroup = ({ title, count, tone, orders, cardProps, action }) => (
    <section className={styles.orderGroup}>
        <header className={styles.groupHeader}>
            <div>
                <span className={`${styles.groupDot} ${styles[tone]}`} aria-hidden="true" />
                <h3>{title}</h3>
                <span>{count}</span>
            </div>
            {action}
        </header>
        <div className={styles.orderGroupList}>
            {orders.map((order) => (
                <OrderCard key={order.orderId} order={order} {...cardProps} />
            ))}
        </div>
    </section>
);

export default function OrdersBoard({
    orders,
    cookingOrders,
    readyOrders,
    issuedOrders,
    showIssuedOrders,
    isLoading,
    onReload,
    onToggleIssued,
    cardProps
}) {
    return (
        <section className={styles.ordersBoard}>
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.kicker}>Живая очередь</p>
                    <h2>Заказы смены</h2>
                </div>
                <button className={styles.utilityButton} type="button" onClick={onReload} disabled={isLoading}>
                    Обновить
                </button>
            </div>

            <div className={styles.queueSummary} aria-label="Сводка заказов">
                <div><span>Готовятся</span><strong>{cookingOrders.length}</strong></div>
                <div><span>К выдаче</span><strong>{readyOrders.length}</strong></div>
                <div><span>Выданы</span><strong>{issuedOrders.length}</strong></div>
            </div>

            {orders.length === 0 ? (
                <div className={styles.stateCard}>
                    <strong>Очередь пока пуста</strong>
                    <span>Первый созданный чек появится здесь и на кухонном экране.</span>
                </div>
            ) : (
                <div className={styles.orderGroups}>
                    {cookingOrders.length > 0 && (
                        <OrderGroup
                            title="Готовятся"
                            count={cookingOrders.length}
                            tone="cookingTone"
                            orders={cookingOrders}
                            cardProps={cardProps}
                        />
                    )}
                    {readyOrders.length > 0 && (
                        <OrderGroup
                            title="К выдаче"
                            count={readyOrders.length}
                            tone="readyTone"
                            orders={readyOrders}
                            cardProps={cardProps}
                        />
                    )}
                    {issuedOrders.length > 0 && (
                        <OrderGroup
                            title="Выданные"
                            count={issuedOrders.length}
                            tone="issuedTone"
                            orders={showIssuedOrders ? issuedOrders : []}
                            cardProps={cardProps}
                            action={(
                                <button className={styles.groupToggle} type="button" onClick={onToggleIssued}>
                                    {showIssuedOrders ? "Свернуть" : "Показать"}
                                </button>
                            )}
                        />
                    )}
                </div>
            )}
        </section>
    );
}
