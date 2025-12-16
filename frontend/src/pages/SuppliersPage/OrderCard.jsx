// OrderCard.jsx
import React, { useState, useEffect } from "react";
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

    return (
        <div className={`${styles.orderCard} ${order.status ? styles.ready : styles.cooking}`}>
            <div>
                <b>№{order.orderId}</b>
                <div>
                    {items.length > 0
                        ? items.map((i, idx) => (
                            <div key={idx}>
                                {i.dishName || "Без названия"} × {i.qty || 1} = {(i.price || 0) * (i.qty || 1)} ₽
                            </div>
                        ))
                        : "Загрузка блюд..."}
                </div>
                <div>Итого: {order.amount} ₽</div>
                <div>Тип: {order.type ? "Доставка" : "По месту"}</div>
                <div>Статус: {order.status ? "ГОТОВ" : "ГОТОВИТСЯ"}</div>
            </div>
            {!order.status && (
                <button
                    className={`${styles.btn} ${styles.primary}`}
                    onClick={() => markOrderReady(order.orderId)}
                >
                    ГОТОВО
                </button>
            )}
        </div>
    );
}
