import styles from "./CashierPage.module.css";

export default function CashierPage() {
    const orders = [
        { id: 101, total: 890, status: "ГОТОВИТСЯ" },
        { id: 102, total: 450, status: "ГОТОВ" },
    ];

    const currentOrder = [
        { name: "Филадельфия", qty: 2, price: 450 },
        { name: "Кола", qty: 1, price: 120 },
    ];

    const total = currentOrder.reduce((s, i) => s + i.qty * i.price, 0);

    return (
        <div className={styles.page}>

            {/* HEADER */}
            <header className={styles.header}>
                <div className={styles.brand}>🍣 СушиСакура</div>

                <div className={styles.shift}>
                    <span className={styles.shiftOpen}>Смена открыта</span>
                    <button className={`${styles.btn} ${styles.danger}`}>Закрыть смену</button>
                </div>
            </header>

            {/* BODY */}
            <div className={styles.body}>

                {/* CHECK */}
                <section className={styles.orderPanel}>
                    <h2>Текущий заказ</h2>

                    <div className={styles.items}>
                        {currentOrder.map((i, idx) => (
                            <div key={idx} className={styles.item}>
                                <span className={styles.name}>{i.name}</span>
                                <span>{i.qty} × {i.price} ₽</span>
                                <span className={styles.sum}>{i.qty * i.price} ₽</span>
                            </div>
                        ))}
                    </div>

                    <div className={styles.total}>
                        <span>ИТОГО</span>
                        <span>{total} ₽</span>
                    </div>

                    <div className={styles.actions}>
                        <button className={`${styles.btn} ${styles.danger}`}>Отмена</button>
                        <button className={`${styles.btn} ${styles.secondary}`}>Сохранить</button>
                        <button className={`${styles.btn} ${styles.primary}`}>Принять</button>
                    </div>
                </section>

                {/* ORDERS */}
                <section className={styles.ordersPanel}>
                    <h2>Заказы</h2>

                    {orders.map(o => (
                        <div
                            key={o.id}
                            className={`${styles.orderCard} ${
                                o.status === "ГОТОВ"
                                    ? styles.ready
                                    : styles.cooking
                            }`}
                        >
                            <div>
                                <strong>#{o.id}</strong>
                                <div>{o.total} ₽</div>
                            </div>

                            <div className={styles.status}>{o.status}</div>

                            <button className={`${styles.btn} ${styles.edit}`}>✏</button>
                        </div>
                    ))}
                </section>

            </div>
        </div>
    );
}
