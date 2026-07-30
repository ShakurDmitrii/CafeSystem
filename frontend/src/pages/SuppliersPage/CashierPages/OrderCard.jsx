import { useEffect, useMemo, useRef, useState } from "react";
import { API_BASE_URL } from "../../../auth";
import styles from "./CashierPage.module.css";
import { formatDate, formatMoney } from "./cashier-page/cashierUtils";

async function loadOrderDishes(orderId) {
    if (!orderId) return [];

    try {
        const orderResponse = await fetch(`${API_BASE_URL}/api/orders/${orderId}`);
        if (orderResponse.ok) {
            const payload = await orderResponse.json();
            if (Array.isArray(payload?.items) && payload.items.length > 0) return payload.items;
        }

        const fallbackResponse = await fetch(`${API_BASE_URL}/api/shifts/getDish/${orderId}`);
        if (!fallbackResponse.ok) throw new Error(`HTTP ${fallbackResponse.status}`);
        const payload = await fallbackResponse.json();
        return Array.isArray(payload) ? payload : [];
    } catch (error) {
        console.error("Не удалось загрузить позиции заказа:", error);
        return [];
    }
}

async function saveDelay(orderId, delayMinutes) {
    try {
        const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}/timeDelay`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ delayMinutes })
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
    } catch (error) {
        console.error("Не удалось сохранить задержку заказа:", error);
    }
}

function getOrderStartTimestamp(order) {
    const rawDate = order.created_at || order.createdAt;
    if (!rawDate) return Date.now();

    const raw = String(rawDate).trim();
    const hasTimezone = /[zZ]|[+-]\d{2}:\d{2}$/.test(raw);
    const normalized = raw.replace(/\.(\d{3})\d+/, ".$1").replace(" ", "T");
    const timestamp = new Date(hasTimezone ? normalized : `${normalized}Z`).getTime();
    return Number.isFinite(timestamp) ? timestamp : Date.now();
}

function getDebtStatus(dateString) {
    if (!dateString) return null;
    const paymentDate = new Date(dateString);
    const today = new Date();
    paymentDate.setHours(0, 0, 0, 0);
    today.setHours(0, 0, 0, 0);
    const days = Math.round((paymentDate - today) / 86400000);

    if (days < 0) return { tone: "overdue", label: `${Math.abs(days)} дн. просрочки` };
    if (days === 0) return { tone: "today", label: "Оплатить сегодня" };
    return { tone: "future", label: `Через ${days} дн.` };
}

export default function OrderCard({
    order,
    markOrderReady,
    onPrintOrderNumber,
    onPrintOrderDetails,
    onUpdatePayment,
    onIssueOrder
}) {
    const hasInlineItems = Array.isArray(order.items)
        && order.items.some((item) => item?.dishName || item?.name || item?.title);
    const [items, setItems] = useState(() => (Array.isArray(order.items) ? order.items : []));
    const [itemsLoaded, setItemsLoaded] = useState(hasInlineItems);
    const [secondsPassed, setSecondsPassed] = useState(0);
    const [delayMinutes, setDelayMinutes] = useState(Number(order.timeDelay || 0));
    const [busyAction, setBusyAction] = useState("");
    const [message, setMessage] = useState(null);
    const previousDelayRef = useRef(Number(order.timeDelay || 0));

    const isIssued = Boolean(order.date_issue || order.dateIssue);
    const isReady = Boolean(order.status);
    const isPaid = order.paid === true;
    const isDelayed = !isReady && Number(order.time || 0) > 0
        && secondsPassed >= Number(order.time) * 60;
    const debtStatus = getDebtStatus(order.debtPaymentDate);
    const startedAt = useMemo(
        () => getOrderStartTimestamp({
            createdAt: order.createdAt,
            created_at: order.created_at
        }),
        [order.createdAt, order.created_at]
    );

    useEffect(() => {
        if (hasInlineItems) {
            setItems(order.items);
            setItemsLoaded(true);
            return;
        }

        let cancelled = false;
        setItemsLoaded(false);
        loadOrderDishes(order.orderId).then((result) => {
            if (!cancelled) {
                setItems(result);
                setItemsLoaded(true);
            }
        });
        return () => { cancelled = true; };
    }, [hasInlineItems, order.items, order.orderId]);

    useEffect(() => {
        setDelayMinutes(Number(order.timeDelay || 0));
        previousDelayRef.current = Number(order.timeDelay || 0);
    }, [order.orderId, order.timeDelay]);

    useEffect(() => {
        if (isReady) {
            setSecondsPassed(0);
            return undefined;
        }

        const updateElapsed = () => {
            setSecondsPassed(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)));
        };
        updateElapsed();
        const interval = window.setInterval(updateElapsed, 1000);
        return () => window.clearInterval(interval);
    }, [isReady, startedAt]);

    useEffect(() => {
        if (isReady || !order.time) return;
        const nextDelay = Math.max(0, Math.floor(secondsPassed / 60) - Math.floor(Number(order.time)));
        if (nextDelay === previousDelayRef.current) return;
        previousDelayRef.current = nextDelay;
        setDelayMinutes(nextDelay);
        saveDelay(order.orderId, nextDelay);
    }, [isReady, order.orderId, order.time, secondsPassed]);

    const timer = useMemo(() => {
        if (isReady || !order.time) return null;
        if (isDelayed) {
            const delaySeconds = Math.max(0, secondsPassed - Number(order.time) * 60);
            return {
                delayed: true,
                value: `+${Math.floor(delaySeconds / 60).toString().padStart(2, "0")}:${(delaySeconds % 60).toString().padStart(2, "0")}`
            };
        }
        const remaining = Math.max(0, Number(order.time) * 60 - secondsPassed);
        return {
            delayed: false,
            value: `${Math.floor(remaining / 60).toString().padStart(2, "0")}:${Math.floor(remaining % 60).toString().padStart(2, "0")}`
        };
    }, [isDelayed, isReady, order.time, secondsPassed]);

    const paymentType = String(order.paymentType || "").toLowerCase();
    const paymentLabel = paymentType === "cash"
        ? "Наличные"
        : paymentType === "transfer"
            ? "Перевод"
            : "Не оплачено";

    const runAction = async (actionName, action, successText) => {
        if (!action || busyAction) return;
        setBusyAction(actionName);
        setMessage(null);
        try {
            await action();
            setMessage({ type: "success", text: successText });
        } catch (error) {
            setMessage({ type: "error", text: error.message || "Операция не выполнена" });
        } finally {
            setBusyAction("");
        }
    };

    return (
        <article
            className={[
                styles.orderCard,
                isReady ? styles.ready : styles.cooking,
                isDelayed ? styles.orderCardDelayed : "",
                !isPaid ? styles.orderCardUnpaid : "",
                isIssued ? styles.orderCardIssued : ""
            ].filter(Boolean).join(" ")}
        >
            <header className={styles.orderCardHeader}>
                <div>
                    <span className={styles.orderCardKicker}>{isIssued ? "Выдан" : isReady ? "К выдаче" : "На кухне"}</span>
                    <strong className={styles.orderCardNumber}>#{order.orderId}</strong>
                </div>
                <div className={styles.orderCardBadges}>
                    {order.duty && <span className={styles.debtBadge}>Долг</span>}
                    <span className={isPaid ? styles.paidBadge : styles.unpaidBadge}>
                        {isPaid ? paymentLabel : "Ждёт оплаты"}
                    </span>
                </div>
            </header>

            {timer && (
                <div className={timer.delayed ? styles.timerDelayed : styles.timerRunning}>
                    <span>{timer.delayed ? "Задержка" : "Осталось"}</span>
                    <strong>{timer.value}</strong>
                    <small>План: {order.time} мин</small>
                </div>
            )}

            {order.duty && order.debtPaymentDate && (
                <div className={`${styles.debtDue} ${styles[debtStatus?.tone || "future"]}`}>
                    <span>Оплата до {formatDate(order.debtPaymentDate)}</span>
                    <strong>{debtStatus?.label}</strong>
                </div>
            )}

            <div className={styles.orderCardItems}>
                {items.length > 0 ? items.map((item, index) => {
                    const qty = Number(item.qty || 1);
                    const price = Number(item.price || item.cost || 0);
                    return (
                        <div key={`${item.dishId || item.id || index}-${index}`}>
                            <span>{item.dishName || item.name || "Позиция"} <b>× {qty}</b></span>
                            <strong>{formatMoney(price * qty)}</strong>
                        </div>
                    );
                }) : (
                    <span className={styles.orderCardEmpty}>
                        {itemsLoaded ? "В заказе нет позиций" : "Загружаем позиции…"}
                    </span>
                )}
            </div>

            <div className={styles.orderCardTotal}>
                <span>Итого</span>
                <strong>{formatMoney(order.amount)}</strong>
            </div>

            <dl className={styles.orderCardMeta}>
                <div><dt>Получение</dt><dd>{order.type ? "Доставка" : "В заведении"}</dd></div>
                {order.clientName && <div><dt>Гость</dt><dd>{order.clientName}</dd></div>}
                {order.clientPhone && <div><dt>Телефон</dt><dd>{order.clientPhone}</dd></div>}
                {Number(delayMinutes) > 0 && <div><dt>Задержка</dt><dd>{delayMinutes} мин</dd></div>}
            </dl>

            <div className={styles.orderCardActions}>
                <button
                    className={styles.orderActionQuiet}
                    type="button"
                    disabled={Boolean(busyAction)}
                    onClick={() => runAction("number", () => onPrintOrderNumber?.(order), "Номер отправлен на печать")}
                >
                    {busyAction === "number" ? "Печатаем…" : "Номер"}
                </button>
                <button
                    className={styles.orderActionQuiet}
                    type="button"
                    disabled={Boolean(busyAction)}
                    onClick={() => runAction("details", () => onPrintOrderDetails?.(order, items), "Чек отправлен на печать")}
                >
                    {busyAction === "details" ? "Печатаем…" : "Чек"}
                </button>

                {!isReady && (
                    <button className={styles.orderActionPrimary} type="button" onClick={() => markOrderReady(order.orderId)}>
                        Готово
                    </button>
                )}

                {!isPaid && (
                    <>
                        <button
                            className={styles.orderActionPrimary}
                            type="button"
                            disabled={Boolean(busyAction)}
                            onClick={() => runAction(
                                "cash",
                                () => onUpdatePayment?.(order.orderId, "cash"),
                                "Оплата наличными отмечена"
                            )}
                        >
                            Наличные
                        </button>
                        <button
                            className={styles.orderActionQuiet}
                            type="button"
                            disabled={Boolean(busyAction)}
                            onClick={() => runAction(
                                "transfer",
                                () => onUpdatePayment?.(order.orderId, "transfer"),
                                "Оплата переводом отмечена"
                            )}
                        >
                            Перевод
                        </button>
                    </>
                )}

                {isReady && !isIssued && (
                    <button className={styles.orderActionIssue} type="button" onClick={() => onIssueOrder?.(order.orderId)}>
                        Выдать
                    </button>
                )}
                {isIssued && <span className={styles.issuedLabel}>Выдан</span>}
            </div>

            {message && (
                <div
                    className={message.type === "success" ? styles.actionMessageSuccess : styles.actionMessageError}
                    role="status"
                    aria-live="polite"
                >
                    {message.text}
                </div>
            )}
        </article>
    );
}
