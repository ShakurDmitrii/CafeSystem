import styles from "../ClientsPage.module.css";
import {
    formatDateTime,
    formatMoney,
    formatPaymentType,
    getInitials
} from "./clientUtils";

export default function ClientProfile({
    client,
    dishes,
    orders,
    ordersLoading,
    expandedOrders,
    orderItemsById,
    orderItemsLoadingById,
    vkCodeLoading,
    vkLinkCode,
    onBack,
    onCreateVkCode,
    onToggleOrder
}) {
    return (
        <section className={styles.profile}>
            <button className={styles.backButton} type="button" onClick={onBack}>
                <span aria-hidden="true">←</span> К гостевой книге
            </button>

            <header className={styles.profileHeader}>
                <div className={styles.profileIdentity}>
                    <div className={styles.profileStamp} aria-hidden="true">
                        {getInitials(client.fullName)}
                    </div>
                    <div>
                        <p className={styles.kicker}>Профиль гостя · #{client.clientId}</p>
                        <h2>{client.fullName || "Без имени"}</h2>
                        <a
                            href={client.number ? `tel:${client.number.replace(/[^\d+]/g, "")}` : undefined}
                            aria-disabled={!client.number}
                        >
                            {client.number || "Телефон не указан"}
                        </a>
                    </div>
                </div>

                <div className={styles.vkPass}>
                    <span className={styles.vkPassLabel}>Связь с VK</span>
                    {vkLinkCode ? (
                        <div className={styles.vkCodeResult} aria-live="polite">
                            <strong>{vkLinkCode.code}</strong>
                            <span>Действует до {formatDateTime(vkLinkCode.expiresAt)}</span>
                        </div>
                    ) : (
                        <p>Одноразовый код поможет гостю связать профиль с ботом.</p>
                    )}
                    <button
                        className={styles.vkButton}
                        type="button"
                        onClick={onCreateVkCode}
                        disabled={vkCodeLoading}
                    >
                        {vkCodeLoading ? "Выдаём код…" : vkLinkCode ? "Выдать новый код" : "Выдать код"}
                    </button>
                </div>
            </header>

            <div className={styles.profileColumns}>
                <section className={styles.favoritePanel}>
                    <div className={styles.sectionHeading}>
                        <div>
                            <p className={styles.kicker}>Предпочтения</p>
                            <h3>Любимые блюда</h3>
                        </div>
                        <span className={styles.countBadge}>{dishes.length}</span>
                    </div>
                    {dishes.length === 0 ? (
                        <div className={styles.compactEmpty}>Появятся после первых заказов.</div>
                    ) : (
                        <ol className={styles.favoriteList}>
                            {dishes.map((dish, index) => (
                                <li key={dish.dishId}>
                                    <span>{String(index + 1).padStart(2, "0")}</span>
                                    <strong>{dish.dishName || "Блюдо без названия"}</strong>
                                </li>
                            ))}
                        </ol>
                    )}
                </section>

                <section className={styles.visitPanel}>
                    <div className={styles.sectionHeading}>
                        <div>
                            <p className={styles.kicker}>Хронология</p>
                            <h3>История визитов</h3>
                        </div>
                        <span className={styles.countBadge}>{orders.length}</span>
                    </div>

                    {ordersLoading ? (
                        <div className={styles.compactEmpty} role="status">Загружаем визиты…</div>
                    ) : orders.length === 0 ? (
                        <div className={styles.compactEmpty}>У гостя пока нет заказов.</div>
                    ) : (
                        <div className={styles.visitList}>
                            {orders.map((order) => {
                                const expanded = Boolean(expandedOrders[order.orderId]);
                                const items = orderItemsById[order.orderId] || [];
                                return (
                                    <article key={order.orderId} className={styles.visitCard}>
                                        <div className={styles.visitMain}>
                                            <div>
                                                <span className={styles.cardId}>
                                                    {formatDateTime(order.created_at || order.createdAt || order.date)}
                                                </span>
                                                <h4>Заказ #{order.orderId}</h4>
                                            </div>
                                            <strong>{formatMoney(order.amount)}</strong>
                                        </div>
                                        <div className={styles.visitMeta}>
                                            <span>{order.status ? "Готов" : "Готовится"}</span>
                                            <span>{order.type ? "Доставка" : "В заведении"}</span>
                                            <span>{formatPaymentType(order.paymentType, order.paid)}</span>
                                            {order.duty && <span className={styles.debtChip}>Долг</span>}
                                            {Number(order.timeDelay || 0) > 0 && (
                                                <span className={styles.warningChip}>+{order.timeDelay} мин</span>
                                            )}
                                        </div>
                                        <button
                                            className={styles.expandButton}
                                            type="button"
                                            aria-expanded={expanded}
                                            onClick={() => onToggleOrder(order.orderId)}
                                        >
                                            {expanded ? "Скрыть состав" : "Показать состав"}
                                        </button>

                                        {expanded && (
                                            <div className={styles.orderItems}>
                                                {orderItemsLoadingById[order.orderId] ? (
                                                    <span role="status">Загружаем состав…</span>
                                                ) : items.length === 0 ? (
                                                    <span>Нет данных по блюдам.</span>
                                                ) : (
                                                    items.map((item, index) => (
                                                        <div key={`${order.orderId}-${item.dishName || "dish"}-${index}`}>
                                                            <strong>{item.dishName || "Блюдо без названия"}</strong>
                                                            <span>× {item.qty || 0}</span>
                                                            <span>
                                                                {formatMoney(item.sum ?? ((item.price || 0) * (item.qty || 0)))}
                                                            </span>
                                                        </div>
                                                    ))
                                                )}
                                            </div>
                                        )}
                                    </article>
                                );
                            })}
                        </div>
                    )}
                </section>
            </div>
        </section>
    );
}
