import { useEffect, useMemo, useState } from "react";
import { API_BASE_URL, hasRole } from "../../auth";
import styles from "./HomePage.module.css";

const API_ORDERS = `${API_BASE_URL}/api/orders`;
const API_SHIFTS = `${API_BASE_URL}/api/shifts`;
const API_PERSONS = `${API_BASE_URL}/api/persons`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;

const toNum = (v) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
};

const toTodayLocal = () => {
    const d = new Date();
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}`;
};

const normalizeDate = (value) => String(value || "").slice(0, 10);

const formatMoney = (n) => `${toNum(n).toFixed(2)} ₽`;

const getStockThreshold = (unit) => {
    const u = String(unit || "").toLowerCase();
    if (u === "kg" || u === "l") return 2;
    if (u === "g" || u === "ml") return 200;
    return 10; // pcs and fallback
};

export default function HomePage({ auth }) {
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [data, setData] = useState({
        today: toTodayLocal(),
        workers: [],
        revenue: 0,
        ordersCount: 0,
        avgCheck: 0,
        deliveryCount: 0,
        deliverySum: 0,
        unpaidCount: 0,
        avgPrepMinutes: 0,
        delayedOrdersCount: 0,
        topDishes: [],
        criticalStocks: []
    });

    useEffect(() => {
        let cancelled = false;
        const isOwner = hasRole(auth, ["OWNER"]);

        const fetchJson = async (url) => {
            const res = await fetch(url);
            if (!res.ok) throw new Error(`${url} -> ${res.status}`);
            return res.json();
        };

        const load = async () => {
            setLoading(true);
            setError("");
            try {
                const today = toTodayLocal();
                const [ordersRaw, shiftsRaw, personsRaw, productsRaw, warehousesRaw] = await Promise.all([
                    fetchJson(API_ORDERS),
                    fetchJson(API_SHIFTS),
                    fetchJson(API_PERSONS),
                    fetchJson(API_PRODUCTS),
                    isOwner ? fetchJson(API_WAREHOUSES) : Promise.resolve([])
                ]);

                const orders = Array.isArray(ordersRaw) ? ordersRaw : [];
                const shifts = Array.isArray(shiftsRaw) ? shiftsRaw : [];
                const persons = Array.isArray(personsRaw) ? personsRaw : [];
                const products = Array.isArray(productsRaw) ? productsRaw : [];
                const warehouses = Array.isArray(warehousesRaw) ? warehousesRaw : [];

                const personsById = new Map(
                    persons.map((p) => [Number(p.personID), p.name || `ID ${p.personID}`])
                );

                const availableOrderDates = orders
                    .map((o) => normalizeDate(o.date))
                    .filter(Boolean);
                const availableShiftDates = shifts
                    .map((s) => normalizeDate(s.data))
                    .filter(Boolean);
                const latestBusinessDate = [...availableOrderDates, ...availableShiftDates]
                    .sort()
                    .at(-1) || today;
                const dashboardDate = availableOrderDates.includes(today) || availableShiftDates.includes(today)
                    ? today
                    : latestBusinessDate;

                const dashboardOrders = orders.filter((o) => normalizeDate(o.date) === dashboardDate);
                const dashboardShifts = shifts.filter((s) => normalizeDate(s.data) === dashboardDate);
                const workers = dashboardShifts
                    .flatMap((shift) => {
                        if (Array.isArray(shift.personNames) && shift.personNames.length > 0) {
                            return shift.personNames;
                        }
                        if (Array.isArray(shift.personIds) && shift.personIds.length > 0) {
                            return shift.personIds
                                .map((id) => personsById.get(Number(id)) || `Сотр. #${id}`);
                        }
                        return [personsById.get(Number(shift.personCode)) || `Сотр. #${shift.personCode}`];
                    })
                    .filter(Boolean);

                const dishRowsByOrderId = new Map();
                await Promise.all(
                    dashboardOrders.map(async (o) => {
                        if (!o?.orderId) {
                            dishRowsByOrderId.set(o?.orderId, []);
                            return;
                        }
                        try {
                            const rows = await fetchJson(`${API_BASE_URL}/api/shifts/getDish/${o.orderId}`);
                            dishRowsByOrderId.set(o.orderId, Array.isArray(rows) ? rows : []);
                        } catch {
                            dishRowsByOrderId.set(o.orderId, []);
                        }
                    })
                );

                const revenue = dashboardOrders.reduce((sum, o) => sum + toNum(o.amount), 0);
                const ordersCount = dashboardOrders.length;
                const avgCheck = ordersCount > 0 ? revenue / ordersCount : 0;

                const deliveryOrders = dashboardOrders.filter((o) => o.type === true);
                const deliveryCount = deliveryOrders.length;
                const deliverySum = deliveryOrders.reduce((sum, o) => {
                    const explicit = toNum(o.deliveryCost);
                    if (explicit > 0) return sum + explicit;

                    const items = dishRowsByOrderId.get(o.orderId) || [];
                    const itemsTotal = items.reduce((s, d) => s + (toNum(d.price) * toNum(d.qty || 1)), 0);
                    const estimatedDelivery = Math.max(0, toNum(o.amount) - itemsTotal);
                    return sum + estimatedDelivery;
                }, 0);

                const unpaidCount = dashboardOrders.filter((o) => {
                    const p = String(o.paymentType || "").toLowerCase();
                    return !(o.paid === true || p === "cash" || p === "transfer");
                }).length;

                const avgPrepMinutes = ordersCount > 0
                    ? dashboardOrders.reduce((s, o) => s + toNum(o.time), 0) / ordersCount
                    : 0;

                const delayedOrdersCount = dashboardOrders.filter((o) => toNum(o.timeDelay) > 0).length;

                const topMap = new Map();
                for (const rows of dishRowsByOrderId.values()) {
                    rows.forEach((d) => {
                        const key = String(d.dishName || `Блюдо #${d.dishId}`);
                        const prev = topMap.get(key) || 0;
                        topMap.set(key, prev + toNum(d.qty || 1));
                    });
                }
                const topDishes = [...topMap.entries()]
                    .map(([name, qty]) => ({ name, qty }))
                    .sort((a, b) => b.qty - a.qty)
                    .slice(0, 5);

                let criticalStocks = [];
                if (isOwner) {
                    const whRows = await Promise.all(
                        warehouses.map(async (w) => {
                            try {
                                const list = await fetchJson(`${API_WAREHOUSES}/${w.warehouseId}/products`);
                                return Array.isArray(list)
                                    ? list.map((row) => ({
                                        ...row,
                                        warehouseName: w.warehouseName || `Склад #${w.warehouseId}`,
                                        warehouseId: Number(w.warehouseId),
                                        isMain: Boolean(w.isMain)
                                    }))
                                    : [];
                            } catch {
                                return [];
                            }
                        })
                    );
                    const allWhProducts = whRows.flat();
                    const productById = new Map(
                        products.map((p) => [Number(p.productId), p])
                    );

                    criticalStocks = allWhProducts
                        .map((row) => {
                            const pid = Number(row.productId);
                            const product = productById.get(pid);
                            if (!product) return null;

                            const qty = toNum(row.quantity);
                            const unit = product.baseUnit || product.unit || "pcs";
                            const threshold = getStockThreshold(unit);
                            const level = qty <= threshold ? "critical" : (qty <= threshold * 2 ? "warning" : "normal");
                            return {
                                key: `${pid}-${row.warehouseId}`,
                                productId: pid,
                                warehouseId: Number(row.warehouseId),
                                warehouseName: row.warehouseName || `Склад #${row.warehouseId}`,
                                isMain: Boolean(row.isMain),
                                productName: product.productName || `Товар #${pid}`,
                                qty,
                                unit,
                                threshold,
                                level
                            };
                        })
                        .filter(Boolean)
                        .filter((x) => x.level !== "normal")
                        .sort((a, b) => {
                            if (a.isMain !== b.isMain) return a.isMain ? -1 : 1;
                            const rank = { critical: 0, warning: 1 };
                            if (rank[a.level] !== rank[b.level]) return rank[a.level] - rank[b.level];
                            return a.qty - b.qty;
                        })
                        .slice(0, 8);
                }

                if (!cancelled) {
                    setData({
                        today: dashboardDate,
                        workers: [...new Set(workers)],
                        revenue,
                        ordersCount,
                        avgCheck,
                        deliveryCount,
                        deliverySum,
                        unpaidCount,
                        avgPrepMinutes,
                        delayedOrdersCount,
                        topDishes,
                        criticalStocks
                    });
                }
            } catch (e) {
                if (!cancelled) setError(e?.message || "Ошибка загрузки дашборда");
            } finally {
                if (!cancelled) setLoading(false);
            }
        };

        load();
        return () => {
            cancelled = true;
        };
    }, [auth]);

    const greeting = useMemo(() => {
        const name = auth?.personName || auth?.username || "Команда";
        return `CafeHelp Control • ${name}`;
    }, [auth]);

    if (loading) return <div className={styles.state}>Загрузка домашней страницы...</div>;
    if (error) return <div className={`${styles.state} ${styles.error}`}>Ошибка: {error}</div>;

    return (
        <div className={styles.page}>
            <section className={styles.hero}>
                <div>
                    <p className={styles.kicker}>Операционный центр</p>
                    <h1 className={styles.title}>{greeting}</h1>
                    <p className={styles.subTitle}>
                        Метрики за {data.today} • Работники: {data.workers.length ? data.workers.join(", ") : "не указаны"}
                    </p>
                </div>
            </section>

            <section className={styles.kpiGrid}>
                <article className={`${styles.kpiCard} ${styles.ok}`}>
                    <p className={styles.kpiLabel}>Выручка</p>
                    <p className={styles.kpiValue}>{formatMoney(data.revenue)}</p>
                </article>

                <article className={`${styles.kpiCard} ${styles.neutral}`}>
                    <p className={styles.kpiLabel}>Кол-во заказов</p>
                    <p className={styles.kpiValue}>{data.ordersCount}</p>
                </article>

                <article className={`${styles.kpiCard} ${styles.neutral}`}>
                    <p className={styles.kpiLabel}>Средний чек</p>
                    <p className={styles.kpiValue}>{formatMoney(data.avgCheck)}</p>
                </article>

                <article className={`${styles.kpiCard} ${styles.neutral}`}>
                    <p className={styles.kpiLabel}>Доставки</p>
                    <p className={styles.kpiValue}>{data.deliveryCount} / {formatMoney(data.deliverySum)}</p>
                </article>

                <article className={`${styles.kpiCard} ${data.unpaidCount > 0 ? styles.warn : styles.ok}`}>
                    <p className={styles.kpiLabel}>Неоплаченные</p>
                    <p className={styles.kpiValue}>{data.unpaidCount}</p>
                </article>

                <article className={`${styles.kpiCard} ${data.delayedOrdersCount > 0 ? styles.warn : styles.ok}`}>
                    <p className={styles.kpiLabel}>Время / Задержки</p>
                    <p className={styles.kpiValue}>
                        {data.avgPrepMinutes.toFixed(1)} мин / {data.delayedOrdersCount}
                    </p>
                </article>
            </section>

            <section className={styles.columns}>
                <article className={styles.panel}>
                    <h2 className={styles.panelTitle}>Топ-5 блюд за день</h2>
                    {data.topDishes.length === 0 ? (
                        <p className={styles.empty}>Пока нет данных по блюдам.</p>
                    ) : (
                        <ul className={styles.list}>
                            {data.topDishes.map((d, idx) => (
                                <li key={d.name} className={styles.listItem}>
                                    <span className={styles.badge}>{idx + 1}</span>
                                    <span className={styles.itemName}>{d.name}</span>
                                    <span className={styles.itemValue}>{d.qty} шт</span>
                                </li>
                            ))}
                        </ul>
                    )}
                </article>

                <article className={styles.panel}>
                    <h2 className={styles.panelTitle}>Критичные остатки</h2>
                    {data.criticalStocks.length === 0 ? (
                        <p className={styles.empty}>Критичных остатков нет.</p>
                    ) : (
                        <ul className={styles.list}>
                            {data.criticalStocks.map((x) => (
                                <li key={x.key || `${x.productId}-${x.warehouseId || "wh"}`} className={styles.listItem}>
                                    <span className={`${styles.stockDot} ${x.level === "critical" ? styles.dangerDot : styles.warnDot}`}></span>
                                    <div className={styles.itemMeta}>
                                        <span className={styles.itemName}>{x.productName}</span>
                                        <span className={styles.itemSubtext}>
                                            {x.isMain
                                                ? `Главный склад: ${x.warehouseName || "без названия"}`
                                                : x.warehouseName || "Склад не указан"}
                                        </span>
                                    </div>
                                    <span className={styles.itemValue}>
                                        {x.qty.toFixed(2)} {x.unit}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </article>
            </section>
        </div>
    );
}
