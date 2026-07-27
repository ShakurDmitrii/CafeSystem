import { useCallback, useEffect, useState } from "react";
import { API_BASE_URL, hasRole } from "../../../auth";

const API_ORDERS = `${API_BASE_URL}/api/orders`;
const API_SHIFTS = `${API_BASE_URL}/api/shifts`;
const API_PERSONS = `${API_BASE_URL}/api/persons`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;

const initialDashboard = {
    today: "",
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
};

function toNum(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : 0;
}

function toTodayLocal() {
    const date = new Date();
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function normalizeDate(value) {
    return String(value || "").slice(0, 10);
}

function getStockThreshold(unit) {
    const normalizedUnit = String(unit || "").toLowerCase();
    if (normalizedUnit === "kg" || normalizedUnit === "l") return 2;
    if (normalizedUnit === "g" || normalizedUnit === "ml") return 200;
    return 10;
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`${url} → ${response.status}`);
    return response.json();
}

export default function useHomeDashboard(auth) {
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [data, setData] = useState(() => ({
        ...initialDashboard,
        today: toTodayLocal()
    }));
    const [requestVersion, setRequestVersion] = useState(0);

    const reload = useCallback(() => {
        setRequestVersion((version) => version + 1);
    }, []);

    useEffect(() => {
        let cancelled = false;
        const isOwner = hasRole(auth, ["OWNER"]);

        async function loadDashboard() {
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
                    persons.map((person) => [
                        Number(person.personID),
                        person.name || `ID ${person.personID}`
                    ])
                );

                const availableOrderDates = orders
                    .map((order) => normalizeDate(order.date))
                    .filter(Boolean);
                const availableShiftDates = shifts
                    .map((shift) => normalizeDate(shift.data))
                    .filter(Boolean);
                const latestBusinessDate = [...availableOrderDates, ...availableShiftDates]
                    .sort()
                    .at(-1) || today;
                const dashboardDate = availableOrderDates.includes(today) || availableShiftDates.includes(today)
                    ? today
                    : latestBusinessDate;

                const dashboardOrders = orders.filter(
                    (order) => normalizeDate(order.date) === dashboardDate
                );
                const dashboardShifts = shifts.filter(
                    (shift) => normalizeDate(shift.data) === dashboardDate
                );
                const workers = dashboardShifts
                    .flatMap((shift) => {
                        if (Array.isArray(shift.personNames) && shift.personNames.length > 0) {
                            return shift.personNames;
                        }
                        if (Array.isArray(shift.personIds) && shift.personIds.length > 0) {
                            return shift.personIds.map(
                                (id) => personsById.get(Number(id)) || `Сотрудник #${id}`
                            );
                        }
                        return [
                            personsById.get(Number(shift.personCode))
                            || `Сотрудник #${shift.personCode}`
                        ];
                    })
                    .filter(Boolean);

                const dishRowsByOrderId = new Map();
                await Promise.all(
                    dashboardOrders.map(async (order) => {
                        if (!order?.orderId) {
                            dishRowsByOrderId.set(order?.orderId, []);
                            return;
                        }

                        try {
                            const rows = await fetchJson(
                                `${API_BASE_URL}/api/shifts/getDish/${order.orderId}`
                            );
                            dishRowsByOrderId.set(
                                order.orderId,
                                Array.isArray(rows) ? rows : []
                            );
                        } catch {
                            dishRowsByOrderId.set(order.orderId, []);
                        }
                    })
                );

                const revenue = dashboardOrders.reduce(
                    (sum, order) => sum + toNum(order.amount),
                    0
                );
                const ordersCount = dashboardOrders.length;
                const avgCheck = ordersCount > 0 ? revenue / ordersCount : 0;
                const deliveryOrders = dashboardOrders.filter((order) => order.type === true);
                const deliveryCount = deliveryOrders.length;
                const deliverySum = deliveryOrders.reduce((sum, order) => {
                    const explicitDeliveryCost = toNum(order.deliveryCost);
                    if (explicitDeliveryCost > 0) return sum + explicitDeliveryCost;

                    const items = dishRowsByOrderId.get(order.orderId) || [];
                    const itemsTotal = items.reduce(
                        (itemsSum, dish) => (
                            itemsSum + (toNum(dish.price) * toNum(dish.qty || 1))
                        ),
                        0
                    );
                    return sum + Math.max(0, toNum(order.amount) - itemsTotal);
                }, 0);

                const unpaidCount = dashboardOrders.filter((order) => {
                    const paymentType = String(order.paymentType || "").toLowerCase();
                    return !(
                        order.paid === true
                        || paymentType === "cash"
                        || paymentType === "transfer"
                    );
                }).length;
                const avgPrepMinutes = ordersCount > 0
                    ? dashboardOrders.reduce(
                        (sum, order) => sum + toNum(order.time),
                        0
                    ) / ordersCount
                    : 0;
                const delayedOrdersCount = dashboardOrders.filter(
                    (order) => toNum(order.timeDelay) > 0
                ).length;

                const topMap = new Map();
                for (const rows of dishRowsByOrderId.values()) {
                    rows.forEach((dish) => {
                        const name = String(dish.dishName || `Блюдо #${dish.dishId}`);
                        topMap.set(name, (topMap.get(name) || 0) + toNum(dish.qty || 1));
                    });
                }
                const topDishes = [...topMap.entries()]
                    .map(([name, quantity]) => ({ name, qty: quantity }))
                    .sort((left, right) => right.qty - left.qty)
                    .slice(0, 5);

                let criticalStocks = [];
                if (isOwner) {
                    const warehouseRows = await Promise.all(
                        warehouses.map(async (warehouse) => {
                            try {
                                const list = await fetchJson(
                                    `${API_WAREHOUSES}/${warehouse.warehouseId}/products`
                                );
                                return Array.isArray(list)
                                    ? list.map((row) => ({
                                        ...row,
                                        warehouseName: warehouse.warehouseName
                                            || `Склад #${warehouse.warehouseId}`,
                                        warehouseId: Number(warehouse.warehouseId),
                                        isMain: Boolean(warehouse.isMain)
                                    }))
                                    : [];
                            } catch {
                                return [];
                            }
                        })
                    );
                    const productById = new Map(
                        products.map((product) => [Number(product.productId), product])
                    );

                    criticalStocks = warehouseRows
                        .flat()
                        .map((row) => {
                            const productId = Number(row.productId);
                            const product = productById.get(productId);
                            if (!product) return null;

                            const quantity = toNum(row.quantity);
                            const unit = product.baseUnit || product.unit || "pcs";
                            const threshold = getStockThreshold(unit);
                            const level = quantity <= threshold
                                ? "critical"
                                : (quantity <= threshold * 2 ? "warning" : "normal");

                            return {
                                key: `${productId}-${row.warehouseId}`,
                                productId,
                                warehouseId: Number(row.warehouseId),
                                warehouseName: row.warehouseName || `Склад #${row.warehouseId}`,
                                isMain: Boolean(row.isMain),
                                productName: product.productName || `Товар #${productId}`,
                                qty: quantity,
                                unit,
                                threshold,
                                level
                            };
                        })
                        .filter(Boolean)
                        .filter((stock) => stock.level !== "normal")
                        .sort((left, right) => {
                            if (left.isMain !== right.isMain) return left.isMain ? -1 : 1;
                            const rank = { critical: 0, warning: 1 };
                            if (rank[left.level] !== rank[right.level]) {
                                return rank[left.level] - rank[right.level];
                            }
                            return left.qty - right.qty;
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
            } catch (loadError) {
                if (!cancelled) {
                    setError(
                        loadError?.message
                        || "Не удалось загрузить сводку. Проверьте соединение и повторите попытку."
                    );
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        loadDashboard();
        return () => {
            cancelled = true;
        };
    }, [auth, requestVersion]);

    return { data, loading, error, reload };
}
