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



export default function OrderCard({ order, markOrderReady }) {
    const [items, setItems] = useState([]);
    const [secondsPassed, setSecondsPassed] = useState(0);
    const [isDelayed, setIsDelayed] = useState(false);
    const [delayMinutes, setDelayMinutes] = useState(order.timeDelay || 0);
    const timerRef = useRef(null);
    const startTimeRef = useRef(Date.now());

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
    const formatTime = () => {
        if (!order.time) return null;

        const elapsedMinutes = Math.floor(secondsPassed / 60);
        const elapsedSeconds = secondsPassed % 60;

        if (isDelayed) {
            return (
                <div style={{
                    color: "#ff0000",
                    fontWeight: "bold",
                    backgroundColor: "#ffebee",
                    padding: "5px",
                    borderRadius: "4px",
                    margin: "5px 0"
                }}>
                    ⚠ ЗАДЕРЖКА: +{delayMinutes} мин
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

    // Определяем стиль карточки
    const getCardStyle = () => {
        if (order.status) {
            return { borderLeft: "4px solid #4caf50", backgroundColor: "#e8f5e9" };
        }
        if (isDelayed) {
            return {
                borderLeft: "4px solid #f44336",
                backgroundColor: "#ffebee",
                animation: "pulse 1.5s infinite"
            };
        }
        return { borderLeft: "4px solid #2196f3", backgroundColor: "#e3f2fd" };
    };

    return (
        <div
            className={`${styles.orderCard} ${order.status ? styles.ready : styles.cooking}`}
            style={getCardStyle()}
        >
            <div>
                <b>№{order.orderId}</b>

                {/* Таймер */}
                {formatTime()}

                {/* Информация о времени */}
                <div style={{ fontSize: "0.9em", color: "#666", marginBottom: "10px" }}>
                    Время приготовления: {order.time || 0} мин
                    {delayMinutes > 0 && ` | Задержка: ${delayMinutes} мин`}
                </div>

                <div>
                    {items.length > 0
                        ? items.map((i, idx) => (
                            <div key={idx} style={{ fontSize: "0.9em", marginBottom: "3px" }}>
                                {i.dishName || "Без названия"} × {i.qty} = {(i.price || 0) * (i.qty || 1)} ₽
                            </div>
                        ))
                        : "Загрузка блюд..."}
                </div>
                <div>Итого: {order.amount} ₽</div>
                <div>Тип: {order.type ? "Доставка" : "По месту"}</div>
                <div>Статус: {order.status ? "✅ ГОТОВ" : "👨‍🍳 ГОТОВИТСЯ"}</div>
            </div>
            {!order.status && (
                <button
                    className={`${styles.btn} ${styles.primary}`}
                    onClick={() => markOrderReady(order.orderId)}
                    style={{ marginTop: "10px" }}
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
            `}</style>
        </div>
    );
}