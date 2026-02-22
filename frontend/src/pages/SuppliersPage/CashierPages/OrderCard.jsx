// OrderCard.jsx
import React, { useState, useEffect, useRef } from "react";
import styles from "./CashierPage.module.css";

async function loadOrderDishes(orderId) {
    try {
        console.log("Загружаем блюда для orderId:", orderId);
        const res = await fetch(`http://localhost:8080/api/shifts/getDish/${orderId}`);
        if (!res.ok) throw new Error(`Ошибка загрузки блюд ${res.status}`);
        const dishes = await res.json();
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

export default function OrderCard({ order, markOrderReady }) {
    const [items, setItems] = useState([]);
    const [secondsPassed, setSecondsPassed] = useState(0);
    const [isDelayed, setIsDelayed] = useState(false);
    const [delayMinutes, setDelayMinutes] = useState(order.timeDelay || 0);
    const timerRef = useRef(null);
    const startTimeRef = useRef(Date.now());

    // Расчет статуса долга
    const debtStatus = getDebtStatus(order.debtPaymentDate);

    // Загрузка блюд
    useEffect(() => {
        let cancelled = false;
        loadOrderDishes(order.orderId).then(dishes => {
            if (!cancelled) {
                console.log(`Устанавливаем items для orderId=${order.orderId}:`, dishes);
                setItems(dishes);
            }
        });
        return () => { cancelled = true; };
    }, [order.orderId]);

    // Функция обновления задержки на сервере
    const updateDelayTime = async (orderId, delayMinutes) => {
        try {
            await fetch(`http://localhost:8080/api/orders/${orderId}/timeDelay`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ delayMinutes })
            });
            console.log(`Обновлена задержка для заказа ${orderId}: ${delayMinutes} мин`);
        } catch (error) {
            console.error("Ошибка при обновлении задержки:", error);
        }
    };

    // Таймер приготовления
    useEffect(() => {
        if (order.status) return; // если заказ готов, останавливаем таймер

        // Пытаемся восстановить время из localStorage
        const savedTime = localStorage.getItem(`order_${order.orderId}_start`);
        if (savedTime) {
            startTimeRef.current = parseInt(savedTime);
        } else {
            startTimeRef.current = Date.now();
            localStorage.setItem(`order_${order.orderId}_start`, startTimeRef.current.toString());
        }

        // Рассчитываем сколько секунд уже прошло
        const initialElapsedSeconds = Math.floor((Date.now() - startTimeRef.current) / 1000);
        setSecondsPassed(initialElapsedSeconds);

        // Проверяем, не началась ли уже задержка
        const initialMinutes = Math.floor(initialElapsedSeconds / 60);
        if (order.time && initialMinutes >= order.time) {
            setIsDelayed(true);
            const initialDelay = initialMinutes - order.time;
            setDelayMinutes(initialDelay);

            // Обновляем задержку на сервере
            updateDelayTime(order.orderId, initialDelay);
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
    }, [order.status]);

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
            const currentDelay = elapsedMinutes - order.time;
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

    const formatTime = () => {
        if (!order.time) return null;

        const elapsedMinutes = Math.floor(secondsPassed / 60);
        const elapsedSeconds = secondsPassed % 60;

        if (isDelayed) {
            const { minutes, seconds } = getDelayParts();
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
            const remainingMinutes = Math.max(0, order.time - elapsedMinutes - (elapsedSeconds > 0 ? 1 : 0));
            const remainingSeconds = 60 - elapsedSeconds;

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
        if (order.status) {
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
                    {delayMinutes > 0 && (() => {
                        const { minutes, seconds } = getDelayParts();
                        return ` | Задержка: ${minutes.toString().padStart(2, "0")}:${seconds
                            .toString()
                            .padStart(2, "0")} (${minutes} мин)`;
                    })()}
                </div>

                <div>
                    {items.length > 0
                        ? items.map((i, idx) => (
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
                        : "Загрузка блюд..."}
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

            {!order.status && (
                <button
                    className={`${styles.btn} ${styles.primary}`}
                    onClick={() => markOrderReady(order.orderId)}
                    style={{
                        marginTop: "10px",
                        alignSelf: "flex-end"
                    }}
                >
                    ГОТОВО
                </button>
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
