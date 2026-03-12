// OrderCard.jsx
import React, { useState, useEffect, useRef } from "react";
import { API_BASE_URL } from "../../../auth";
import styles from "./CashierPage.module.css";

async function loadOrderDishes(orderId) {
    if (!orderId) {
        return [];
    }
    try {
        console.log("Загружаем блюда для orderId:", orderId);
        const res = await fetch(`${API_BASE_URL}/api/shifts/getDish/${orderId}`);
        if (!res.ok) throw new Error(`Ошибка загрузки блюд ${res.status}`);
        const text = await res.text();
        const dishes = text ? JSON.parse(text) : [];
        console.log("Получили блюда с сервера:", dishes);
        return Array.isArray(dishes) ? dishes : [];
    } catch (e) {
        console.error("Ошибка при fetch блюд:", e);
        return [];
    }
}

// Функция для расчета статуса долга
const getDebtStatus = (debtPaymentDate) => {
    if (!debtPaymentDate) return null;

    try {
        const today = new Date();
        today.setHours(0, 0, 0, 0); // Устанавливаем начало дня для точного сравнения

        const payment = new Date(debtPaymentDate);
        payment.setHours(0, 0, 0, 0);

        const diffTime = payment.getTime() - today.getTime();
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

        if (diffDays < 0) {
            return {
                status: 'overdue',
                days: Math.abs(diffDays),
                text: `${Math.abs(diffDays)} дн. назад`
            };
        }
        if (diffDays === 0) {
            return {
                status: 'today',
                days: 0,
                text: 'Сегодня!'
            };
        }
        return {
            status: 'future',
            days: diffDays,
            text: `Через ${diffDays} дн.`
        };
    } catch (error) {
        console.error("Ошибка расчета статуса долга:", error);
        return null;
    }
};

export default function OrderCard({
    order,
    markOrderReady,
    onPrintOrderNumber,
    onPrintOrderDetails,
    onUpdatePayment
}) {
    const [items, setItems] = useState(() => (Array.isArray(order.items) ? order.items : []));
    const [itemsLoaded, setItemsLoaded] = useState(
        Array.isArray(order.items) && order.items.length > 0
    );
    const [secondsPassed, setSecondsPassed] = useState(0);
    const [isDelayed, setIsDelayed] = useState(false);
    const [delayMinutes, setDelayMinutes] = useState(order.timeDelay || 0);
    const [isPrintingNumber, setIsPrintingNumber] = useState(false);
    const [isPrintingDetails, setIsPrintingDetails] = useState(false);
    const [printMessage, setPrintMessage] = useState(null);
    const [isUpdatingPayment, setIsUpdatingPayment] = useState(false);
    const timerRef = useRef(null);
    const startTimeRef = useRef(Date.now());

    // Расчет статуса долга
    const debtStatus = getDebtStatus(order.debtPaymentDate);

    // Загрузка блюд
    useEffect(() => {
        if (itemsLoaded) return;
        let cancelled = false;
        loadOrderDishes(order.orderId).then(dishes => {
            if (!cancelled) {
                console.log(`Устанавливаем items для orderId=${order.orderId}:`, dishes);
                setItems(dishes);
                setItemsLoaded(true);
            }
        });
        return () => { cancelled = true; };
    }, [order.orderId, itemsLoaded]);

    // Функция обновления задержки на сервере
    const updateDelayTime = async (orderId, delayMinutes) => {
        try {
            const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}/timeDelay`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ delayMinutes })
            });
            if (!response.ok) {
                let details = "";
                try {
                    details = await response.text();
                } catch {
                    details = "";
                }
                throw new Error(`HTTP ${response.status}${details ? `: ${details}` : ""}`);
            }
            console.log(`Задержка сохранена для заказа ${orderId}: ${delayMinutes} мин`);
        } catch (error) {
            console.error("Ошибка при сохранении задержки на сервере:", error);
        }
    };

    // Держим локальное состояние в синхронизации с тем, что приходит с сервера.
    useEffect(() => {
        setDelayMinutes(Number(order.timeDelay || 0));
    }, [order.orderId, order.timeDelay]);

    const getOrderStartTimestamp = () => {
        const rawDate = order.created_at || order.createdAt || null;
        if (!rawDate) return Date.now();

        // Backend sends LocalDateTime (no timezone) from Docker/UTC.
        // Treat no-zone timestamps as UTC to avoid +3h false delay in browser.
        const raw = String(rawDate).trim();
        const hasTimezone = /[zZ]|[+\-]\d{2}:\d{2}$/.test(raw);
        const normalized = raw
            .replace(/\.(\d{3})\d+/, ".$1")
            .replace(" ", "T");
        const iso = hasTimezone ? normalized : `${normalized}Z`;
        const parsed = new Date(iso).getTime();
        return Number.isFinite(parsed) ? parsed : Date.now();
    };

    // Таймер приготовления
    useEffect(() => {
        if (order.status) {
            setSecondsPassed(0);
            setIsDelayed(false);
            return; // если заказ готов, останавливаем таймер
        }

        // Берем старт строго из даты создания заказа на сервере
        startTimeRef.current = getOrderStartTimestamp();

        // Рассчитываем сколько секунд уже прошло
        const initialElapsedSeconds = Math.floor((Date.now() - startTimeRef.current) / 1000);
        setSecondsPassed(initialElapsedSeconds);

        // Проверяем, не началась ли уже задержка
        const initialMinutes = Math.floor(initialElapsedSeconds / 60);
        if (order.time && initialMinutes >= order.time) {
            setIsDelayed(true);
            const initialDelay = Math.max(0, initialMinutes - Math.floor(order.time));
            setDelayMinutes(initialDelay);

            // Обновляем задержку на сервере
            updateDelayTime(order.orderId, initialDelay);
        } else {
            setIsDelayed(false);
        }

        // Запуск таймера
        timerRef.current = setInterval(() => {
            const elapsed = Math.floor((Date.now() - startTimeRef.current) / 1000);
            setSecondsPassed(elapsed);
        }, 1000);

        return () => {
            if (timerRef.current) {
                clearInterval(timerRef.current);
            }
        };
    }, [order.orderId, order.status, order.time, order.created_at, order.createdAt]);

    // Проверка задержки
    useEffect(() => {
        if (!order.time || order.status) return;

        const elapsedMinutes = Math.floor(secondsPassed / 60);

        // Если время приготовления прошло
        if (elapsedMinutes >= order.time && !isDelayed) {
            setIsDelayed(true);
        }

        // Расчет задержки
        if (isDelayed) {
            const currentDelay = Math.max(0, elapsedMinutes - Math.floor(order.time));
            if (currentDelay > delayMinutes) {
                setDelayMinutes(currentDelay);

                // Отправляем на сервер при увеличении задержки
                updateDelayTime(order.orderId, currentDelay);
            }
        }
    }, [secondsPassed, order.time, order.status, isDelayed, delayMinutes, order.orderId]);

    // Форматирование времени
    const getDelayParts = () => {
        if (!order.time) return { minutes: 0, seconds: 0 };
        const totalDelaySeconds = Math.max(0, secondsPassed - order.time * 60);
        return {
            minutes: Math.floor(totalDelaySeconds / 60),
            seconds: totalDelaySeconds % 60
        };
    };

    const getDisplayDelayParts = () => {
        const storedMinutes = Math.max(0, Math.floor(Number(delayMinutes || 0)));
        const live = getDelayParts();
        // Для активного заказа всегда показываем "живую" задержку,
        // чтобы старые/битые сохраненные значения не ломали отображение.
        if (!order.status) {
            return live;
        }
        return { minutes: storedMinutes, seconds: storedMinutes > 0 ? 0 : live.seconds };
    };

    const formatTime = () => {
        if (!order.time) return null;

        const delay = getDisplayDelayParts();

        if (delay.minutes > 0 || (isDelayed && !order.status)) {
            const { minutes, seconds } = delay;
            return (
                <div style={{
                    color: "#ff0000",
                    fontWeight: "bold",
                    backgroundColor: "#ffebee",
                    padding: "5px",
                    borderRadius: "4px",
                    margin: "5px 0"
                }}>
                    ⚠ ЗАДЕРЖКА: +{minutes.toString().padStart(2, "0")}:
                    {seconds.toString().padStart(2, "0")} ({minutes} мин)
                </div>
            );
        } else {
            const totalCookSeconds = Math.max(0, Math.floor(Number(order.time || 0) * 60));
            const remainingTotalSeconds = Math.max(0, totalCookSeconds - secondsPassed);
            const remainingMinutes = Math.floor(remainingTotalSeconds / 60);
            const remainingSeconds = remainingTotalSeconds % 60;

            return (
                <div style={{
                    margin: "5px 0",
                    padding: "5px",
                    backgroundColor: "#e3f2fd",
                    borderRadius: "4px"
                }}>
                    ⏱ Осталось: {remainingMinutes.toString().padStart(2, '0')}:
                    {remainingSeconds.toString().padStart(2, '0')}
                </div>
            );
        }
    };

    // Форматирование даты для отображения
    const formatDate = (dateString) => {
        if (!dateString) return "";

        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('ru-RU', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric'
            });
        } catch (error) {
            return dateString;
        }
    };

    // Определяем стиль карточки
    const getCardStyle = () => {
        const unpaid = order.paid !== true;

        if (order.status) {
            if (unpaid) {
                return {
                    borderLeft: "4px solid #f59e0b",
                    backgroundColor: "#fffbeb",
                    position: "relative"
                };
            }
            return {
                borderLeft: "4px solid #4caf50",
                backgroundColor: "#e8f5e9",
                position: "relative"
            };
        }
        if (isDelayed) {
            return {
                borderLeft: "4px solid #f44336",
                backgroundColor: "#ffebee",
                animation: "pulse 1.5s infinite",
                position: "relative"
            };
        }
        if (unpaid) {
            return {
                borderLeft: "4px solid #f59e0b",
                backgroundColor: "#fffbeb",
                position: "relative"
            };
        }
        return {
            borderLeft: "4px solid #2196f3",
            backgroundColor: "#e3f2fd",
            position: "relative"
        };
    };

    // Стиль для баджа долга
    const getDebtBadgeStyle = () => {
        if (!debtStatus) return {};

        switch(debtStatus.status) {
            case 'overdue':
                return {
                    background: "linear-gradient(135deg, #ff5252, #ff1744)",
                    color: "white",
                    fontWeight: "bold"
                };
            case 'today':
                return {
                    background: "linear-gradient(135deg, #ff9800, #ff5722)",
                    color: "white",
                    fontWeight: "bold"
                };
            case 'future':
                return {
                    background: "linear-gradient(135deg, #4caf50, #2e7d32)",
                    color: "white"
                };
            default:
                return {};
        }
    };

    // Чиним старое некорректное значение задержки, если заказ еще не просрочен.
    useEffect(() => {
        if (!order.time || order.status) return;
        const elapsedMinutes = Math.floor(secondsPassed / 60);
        if (elapsedMinutes < order.time && Number(delayMinutes || 0) > 0) {
            setDelayMinutes(0);
            updateDelayTime(order.orderId, 0);
        }
    }, [secondsPassed, order.time, order.status, delayMinutes, order.orderId]);

    const handlePrintNumber = async () => {
        if (!onPrintOrderNumber || isPrintingNumber) return;
        setIsPrintingNumber(true);
        setPrintMessage(null);
        try {
            await onPrintOrderNumber(order);
            setPrintMessage({ type: "success", text: "Номер заказа отправлен на печать" });
        } catch (e) {
            setPrintMessage({ type: "error", text: e.message || "Ошибка печати номера заказа" });
        } finally {
            setIsPrintingNumber(false);
        }
    };

    const handlePrintDetails = async () => {
        if (!onPrintOrderDetails || isPrintingDetails) return;
        setIsPrintingDetails(true);
        setPrintMessage(null);
        try {
            await onPrintOrderDetails(order, items);
            setPrintMessage({ type: "success", text: "Чек заказа отправлен на печать" });
        } catch (e) {
            setPrintMessage({ type: "error", text: e.message || "Ошибка печати чека заказа" });
        } finally {
            setIsPrintingDetails(false);
        }
    };

    const paymentType = (order.paymentType || "").toLowerCase();
    const isPaid = order.paid === true;
    const paymentText = paymentType === "cash"
        ? "Наличка"
        : paymentType === "transfer"
            ? "Перевод"
            : "Не оплачен";

    const handleMarkPaid = async (nextType) => {
        if (!onUpdatePayment || isUpdatingPayment) return;
        setIsUpdatingPayment(true);
        setPrintMessage(null);
        try {
            await onUpdatePayment(order.orderId, nextType);
            setPrintMessage({ type: "success", text: `Оплата отмечена: ${nextType === "cash" ? "наличка" : "перевод"}` });
        } catch (e) {
            setPrintMessage({ type: "error", text: e.message || "Ошибка обновления оплаты" });
        } finally {
            setIsUpdatingPayment(false);
        }
    };

    return (
        <div
            className={`${styles.orderCard} ${order.status ? styles.ready : styles.cooking}`}
            style={getCardStyle()}
        >
            {/* Бейдж "Долг" если заказ в долг */}
            {order.duty && (
                <div style={{
                    position: "absolute",
                    top: "10px",
                    right: "10px",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "flex-end",
                    gap: "5px"
                }}>
                    <span style={{
                        padding: "4px 8px",
                        borderRadius: "12px",
                        fontSize: "12px",
                        fontWeight: "600",
                        backgroundColor: "#ff9800",
                        color: "white",
                        boxShadow: "0 2px 4px rgba(0,0,0,0.1)"
                    }}>
                        💳 ДОЛГ
                    </span>

                    {/* Информация о дате погашения */}
                    {order.debtPaymentDate && (
                        <div style={{
                            padding: "4px 8px",
                            borderRadius: "8px",
                            fontSize: "11px",
                            ...getDebtBadgeStyle(),
                            boxShadow: "0 2px 4px rgba(0,0,0,0.1)",
                            textAlign: "center",
                            minWidth: "120px"
                        }}>
                            <div style={{
                                display: "flex",
                                alignItems: "center",
                                gap: "4px",
                                justifyContent: "center"
                            }}>
                                <span>📅</span>
                                <span>До {formatDate(order.debtPaymentDate)}</span>
                            </div>
                            {debtStatus && (
                                <div style={{
                                    fontSize: "10px",
                                    marginTop: "2px",
                                    opacity: 0.9
                                }}>
                                    {debtStatus.text}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            <div style={{ marginRight: order.duty ? "100px" : "0" }}>
                <b>№{order.orderId}</b>

                {/* Таймер */}
                {formatTime()}

                {/* Информация о времени */}
                <div style={{ fontSize: "0.9em", color: "#666", marginBottom: "10px" }}>
                    Время приготовления: {order.time || 0} мин
                    {getDisplayDelayParts().minutes > 0 && (() => {
                        const { minutes, seconds } = getDisplayDelayParts();
                        return ` | Задержка: ${minutes.toString().padStart(2, "0")}:${seconds
                            .toString()
                            .padStart(2, "0")} (${minutes} мин)`;
                    })()}
                </div>

                <div>
                    {items.length > 0 ? (
                        items.map((i, idx) => (
                            <div key={idx} style={{
                                fontSize: "0.9em",
                                marginBottom: "3px",
                                display: "flex",
                                justifyContent: "space-between",
                                alignItems: "center"
                            }}>
                                <span>{i.dishName || "Без названия"} × {i.qty}</span>
                                <span style={{ fontWeight: "bold" }}>
                                    {(i.price || 0) * (i.qty || 1)} ₽
                                </span>
                            </div>
                        ))
                    ) : (
                        <span>{itemsLoaded ? "Нет позиций" : "Загрузка блюд..."}</span>
                    )}
                </div>

                <div style={{
                    marginTop: "10px",
                    paddingTop: "10px",
                    borderTop: "1px dashed #ccc",
                    display: "flex",
                    justifyContent: "space-between",
                    fontWeight: "bold"
                }}>
                    <span>Итого:</span>
                    <span>{order.amount} ₽</span>
                </div>

                <div style={{
                    display: "flex",
                    gap: "15px",
                    marginTop: "8px",
                    fontSize: "0.85em",
                    color: "#555"
                }}>
                    <span>Тип: {order.type ? "🚚 Доставка" : "🏠 По месту"}</span>
                    <span>Статус: {order.status ? "✅ ГОТОВ" : "👨‍🍳 ГОТОВИТСЯ"}</span>
                    <span>Оплата: {isPaid ? `✅ ${paymentText}` : "❗ Не оплачен"}</span>
                </div>

                {/* Информация о клиенте если есть */}
                {order.clientName && (
                    <div style={{
                        marginTop: "8px",
                        padding: "6px",
                        backgroundColor: "#f5f5f5",
                        borderRadius: "4px",
                        fontSize: "0.85em"
                    }}>
                        <strong>👤 Клиент:</strong> {order.clientName}
                        {order.clientPhone && ` (${order.clientPhone})`}
                    </div>
                )}
            </div>

            <div style={{ display: "flex", gap: "8px", marginTop: "10px", flexWrap: "wrap" }}>
                <button
                    className={`${styles.btn} ${styles.secondary}`}
                    onClick={handlePrintNumber}
                    style={{ minWidth: "140px" }}
                >
                    {isPrintingNumber ? "Печать..." : "Печать номера"}
                </button>

                <button
                    className={`${styles.btn} ${styles.secondary}`}
                    onClick={handlePrintDetails}
                    style={{ minWidth: "140px" }}
                >
                    {isPrintingDetails ? "Печать..." : "Печать заказа"}
                </button>

                {!order.status && (
                    <button
                        className={`${styles.btn} ${styles.primary}`}
                        onClick={() => markOrderReady(order.orderId)}
                        style={{ minWidth: "120px" }}
                    >
                        ГОТОВО
                    </button>
                )}

                {!isPaid && (
                    <>
                        <button
                            className={`${styles.btn} ${styles.primary}`}
                            onClick={() => handleMarkPaid("cash")}
                            disabled={isUpdatingPayment}
                            style={{ minWidth: "150px" }}
                        >
                            {isUpdatingPayment ? "Обновление..." : "Оплатил наличкой"}
                        </button>
                        <button
                            className={`${styles.btn} ${styles.secondary}`}
                            onClick={() => handleMarkPaid("transfer")}
                            disabled={isUpdatingPayment}
                            style={{ minWidth: "150px" }}
                        >
                            {isUpdatingPayment ? "Обновление..." : "Оплатил переводом"}
                        </button>
                    </>
                )}
            </div>

            {printMessage && (
                <div
                    style={{
                        marginTop: "8px",
                        fontSize: "12px",
                        color: printMessage.type === "success" ? "#166534" : "#991b1b",
                        background: printMessage.type === "success" ? "#dcfce7" : "#fee2e2",
                        border: `1px solid ${printMessage.type === "success" ? "#bbf7d0" : "#fecaca"}`,
                        borderRadius: "6px",
                        padding: "6px 8px"
                    }}
                >
                    {printMessage.text}
                </div>
            )}

            {/* Добавить CSS анимацию в стили */}
            <style>{`
                @keyframes pulse {
                    0% { opacity: 1; }
                    50% { opacity: 0.8; }
                    100% { opacity: 1; }
                }
                @keyframes blink {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }
                .debt-overdue {
                    animation: blink 1s infinite;
                }
            `}</style>
        </div>
    );
}
