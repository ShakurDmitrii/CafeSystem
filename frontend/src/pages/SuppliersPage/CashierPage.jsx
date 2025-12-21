import { useEffect, useState } from "react";
import styles from "./CashierPage.module.css";
import DishModal from "./DishModal";
import OrderCard from "./OrderCard";

const API_ORDERS = "http://localhost:8080/api/orders";
const API_SHIFTS = "http://localhost:8080/api/shifts";
const API_PERSONS = "http://localhost:8080/api/persons";
const API_DISHES = "http://localhost:8080/api/dishes";

export default function CashierPage() {
    const [orders, setOrders] = useState([]);
    const [currentOrderItems, setCurrentOrderItems] = useState([]);
    const [shiftOpen, setShiftOpen] = useState(false);
    const [currentShift, setCurrentShift] = useState(null);

    const [persons, setPersons] = useState([]);
    const [selectedPerson, setSelectedPerson] = useState(null);
    const [isDebt, setIsDebt] = useState(false);
    const [allDishes, setAllDishes] = useState([]);
    const [modalOpen, setModalOpen] = useState(false);
    const [orderType, setOrderType] = useState(false);
    const [allShifts, setAllShifts] = useState([]);
    const [preparationTime, setPreparationTime] = useState(30); // время приготовления в минутах

    useEffect(() => {
        fetch(API_PERSONS)
            .then(r => r.json())
            .then(d => setPersons(Array.isArray(d) ? d : []));

        fetch(API_DISHES)
            .then(r => r.json())
            .then(d => setAllDishes(Array.isArray(d) ? d : []));

        fetchShifts();
    }, []);

    useEffect(() => {
        if (shiftOpen && currentShift) {
            loadOrders();
            const interval = setInterval(loadOrders, 5000);
            return () => clearInterval(interval);
        }
    }, [shiftOpen, currentShift]);

    const fetchShifts = () => {
        fetch(API_SHIFTS)
            .then(r => r.json())
            .then(d => setAllShifts(Array.isArray(d) ? d : []));
    };


    const loadOrders = () => {
        if (!currentShift) return;

        fetch(API_ORDERS)
            .then(r => r.json())
            .then(d => {
                const filtered = d.filter(o => o.shiftId === currentShift.shiftId);
                setOrders(filtered);
            })
            .catch(e => console.error("Ошибка загрузки заказов", e));
    };

    const createShift = () => {
        if (!selectedPerson) return;

        fetch(`${API_SHIFTS}/create`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                data: new Date().toISOString().slice(0, 10),
                startTime: new Date().toTimeString().slice(0, 8),
                endTime: null,
                income: 0,
                profit: 0,
                expenses: 0,
                personCode: selectedPerson.personCode
            })
        })
            .then(r => r.json())
            .then(shift => {
                setCurrentShift(shift);
                setShiftOpen(true);
                loadOrders();
                fetchShifts();
            });
    };

    const openExistingShift = (shift) => {
        setCurrentShift(shift);
        setShiftOpen(true);
        loadOrders();
    };

    const closeShift = () => {
        if (!currentShift) return;

        const shiftOrders = orders.filter(o => o.shiftId === currentShift.shiftId);

        const income = shiftOrders.reduce(
            (sum, o) => sum + (o.items || []).reduce((s, i) => s + (i.price || 0) * (i.qty || 1), 0),
            0
        );

        const totalCost = shiftOrders.reduce(
            (sum, o) => sum + (o.items || []).reduce((s, i) => s + (i.firstCost || 0) * (i.qty || 1), 0),
            0
        );

        const profit = income - totalCost - (currentShift.expenses || 0);

        fetch(`${API_SHIFTS}/${currentShift.shiftId}/update`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ...currentShift,
                endTime: new Date().toTimeString().slice(0, 8),
                income,
                profit
            })
        }).then(() => {
            setShiftOpen(false);
            setCurrentShift(null);
            setOrders([]);
            setSelectedPerson(null);
            setCurrentOrderItems([]);
            fetchShifts();
        }).catch(e => console.error("Ошибка закрытия смены:", e));
    };

    const createOrder = async () => {
        if (currentOrderItems.length === 0 || !currentShift) return;

        const total = currentOrderItems.reduce((s, i) => s + (i.qty || 1) * i.price, 0);

        try {
            // Формируем payload для OrderDTO
            const orderPayload = {
                clientId: 1,
                shiftId: currentShift.shiftId,
                date: new Date().toISOString().slice(0, 10), // YYYY-MM-DD
                amount: total,
                status: false,
                time: preparationTime,
                duty: isDebt,
                type: orderType,
                items: currentOrderItems.map(i => ({
                    dishID: i.dishId,
                    qty: i.qty || 1
                }))
            };

            console.log("Отправляем заказ на сервер:", orderPayload);

            // Создаем заказ сразу с блюдами
            const orderResponse = await fetch(API_ORDERS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(orderPayload)
            });

            const order = await orderResponse.json();
            console.log("Создан заказ с orderId:", order.orderId);

            // Обновляем состояние фронтенда
            setOrders(prev => [order, ...prev]);
            setCurrentOrderItems([]);
            setOrderType(false);
            setPreparationTime(30);
            setIsDebt(false);

            const dishPayload = currentOrderItems.map(i => ({
                dishID: i.dishId,
                qty: i.qty || 1
            }));

            fetch(`${API_ORDERS}/orderToDish?orderId=${order.orderId}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(dishPayload)
            })
                .then(r => r.json())
                .then(res => console.log("Ответ от orderToDish:", res))
                .catch(e => console.error("Ошибка отправки на orderToDish:", e));

        } catch (e) {
            console.error("Ошибка создания заказа:", e);
        }
    };



    const markOrderReady = (orderId) => {
        fetch(`${API_ORDERS}/${orderId}/status`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status: true })
        }).then(() => {
            setOrders(prev => prev.map(o => o.orderId === orderId ? { ...o, status: true } : o));
        });

    };

    return (
        <div className={styles.page}>
            <header className={styles.header}>
                <div className={styles.brand}>🍣 СушиСакура</div>
                <span className={shiftOpen ? styles.shiftOpen : styles.shiftClosed}>
          {shiftOpen ? "Смена открыта" : "Смена закрыта"}
        </span>
            </header>

            {!shiftOpen && (
                <div className={styles.closedMessage}>
                    <h2>Смены</h2>
                    {allShifts.map(s => (
                        <div key={s.shiftId} className={styles.orderCard}>
                            <div>
                                <b>Смена #{s.shiftId}</b>
                                <div>Статус: {!s.endTime ? "Открыта" : "Закрыта"}</div>
                            </div>
                            {!s.endTime && (
                                <button className={`${styles.btn} ${styles.primary}`} onClick={() => openExistingShift(s)}>
                                    Войти
                                </button>
                            )}
                        </div>
                    ))}

                    <div className={styles.selectEmployeeWrapper}>
                        <select
                            className={styles.selectEmployeeBtn}
                            value={selectedPerson?.personID || ""}
                            onChange={e => {
                                const p = persons.find(x => x.personID === Number(e.target.value));
                                setSelectedPerson(p || null);
                            }}
                        >
                            <option value="">Выберите сотрудника</option>
                            {persons.map(p => (
                                <option key={p.personID} value={p.personID}>{p.name}</option>
                            ))}
                        </select>

                        {selectedPerson && (
                            <div className={styles.selectedPerson}>
                                Выбран сотрудник: <b>{selectedPerson.name}</b>
                            </div>
                        )}

                        <button
                            className={`${styles.btn} ${styles.primary}`}
                            disabled={!selectedPerson}
                            onClick={createShift}
                        >
                            Открыть новую смену
                        </button>
                    </div>
                </div>
            )}

            {shiftOpen && (
                <div className={styles.body}>
                    <section className={styles.orderPanel}>
                        <h2>Новый заказ</h2>

                        {currentOrderItems.map((item, idx) => (
                            <div key={idx} className={styles.item}>
                                <span>{item.dishName}</span>

                                <div className={styles.qtyControls}>
                                    <button
                                        onClick={() =>
                                            setCurrentOrderItems(prev =>
                                                prev.map((it, i) => i === idx ? { ...it, qty: Math.max(1, it.qty - 1) } : it)
                                            )
                                        }
                                    >−</button>
                                    <span>{item.qty}</span>
                                    <button
                                        onClick={() =>
                                            setCurrentOrderItems(prev =>
                                                prev.map((it, i) => i === idx ? { ...it, qty: it.qty + 1 } : it)
                                            )
                                        }
                                    >+</button>
                                </div>

                                <span>{(item.qty || 1) * item.price} ₽</span>

                                <button
                                    className={`${styles.btn} ${styles.danger}`}
                                    onClick={() => setCurrentOrderItems(currentOrderItems.filter((_, i) => i !== idx))}
                                >
                                    ❌
                                </button>
                            </div>
                        ))}

                        <button className={`${styles.btn} ${styles.secondary}`} onClick={() => setModalOpen(true)}>
                            Добавить позицию
                        </button>

                        <div className={styles.switchWrapper}>
                            <label className={styles.switch}>
                                <input type="checkbox" checked={orderType} onChange={() => setOrderType(!orderType)} />
                                <span className={styles.slider}></span>
                            </label>
                            <span>{orderType ? "Доставка" : "По месту"}</span>
                        </div>


                        <div className={styles.debtCheckbox}>
                            <label>
                                <input
                                    type="checkbox"
                                    checked={isDebt}
                                    onChange={(e) => setIsDebt(e.target.checked)}
                                />
                                <span>Долг</span>
                            </label>
                        </div>




                        <div className={styles.timeInput}>
                            <label>Время приготовления (минут):</label>
                            <input
                                type="number"
                                min="0"
                                max="600"
                                value={preparationTime}
                                onChange={(e) => setPreparationTime(Math.max(1, parseInt(e.target.value) || 30))}
                            />
                        </div>

                        <div className={styles.total}>
                            <span>ИТОГО</span>
                            <span>{currentOrderItems.reduce((s, i) => s + (i.qty || 1) * i.price, 0)} ₽</span>
                        </div>

                        <button className={`${styles.btn} ${styles.primary}`} onClick={createOrder} disabled={currentOrderItems.length === 0}>
                            Создать заказ
                        </button>

                        <button className={`${styles.btn} ${styles.danger}`} onClick={closeShift}>
                            Закрыть смену
                        </button>
                    </section>

                    <section className={styles.ordersPanel}>
                        <h2>Заказы</h2>
                        {orders.length === 0 && <div className={styles.empty}>Нет заказов</div>}
                        {orders.map(o => (
                            <OrderCard key={o.orderId} order={o} markOrderReady={markOrderReady} />
                        ))}
                    </section>
                </div>
            )}

            <DishModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                dishes={allDishes}
                onAddDish={d => setCurrentOrderItems(prev => [...prev, { ...d, qty: 1 }])}
            />
        </div>
    );
}
