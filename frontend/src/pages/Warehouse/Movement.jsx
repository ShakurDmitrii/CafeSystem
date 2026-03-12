import { useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "../../auth";
import styles from "./Movement.module.css";

const API_MOVEMENTS = `${API_BASE_URL}/movements`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;

const formatNumber = (value) => {
    const num = Number(value ?? 0);
    return Number.isFinite(num) ? num.toLocaleString("ru-RU") : "0";
};

const formatDate = (value) => {
    if (!value) return "—";
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return value;
    return d.toLocaleString("ru-RU");
};

const toDateTimeLocalValue = (value) => {
    if (!value) return "";
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

export default function MovementPage() {
    const [movements, setMovements] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const [filterWarehouse, setFilterWarehouse] = useState("");
    const [filterProduct, setFilterProduct] = useState("");
    const [filterProductName, setFilterProductName] = useState("");
    const [filterType, setFilterType] = useState("");
    const [sortByProductName, setSortByProductName] = useState("");
    const [sortByDate, setSortByDate] = useState("desc");
    const [editDates, setEditDates] = useState({});
    const [savingDateId, setSavingDateId] = useState(null);
    const [showReport, setShowReport] = useState(false);
    const [reportProductId, setReportProductId] = useState("");
    const [reportDateFrom, setReportDateFrom] = useState("");
    const [reportDateTo, setReportDateTo] = useState("");
    const [reportRows, setReportRows] = useState([]);
    const [reportLoading, setReportLoading] = useState(false);
    const [reportError, setReportError] = useState("");
    const [showTurnoverReport, setShowTurnoverReport] = useState(false);
    const [turnoverProductName, setTurnoverProductName] = useState("");
    const [turnoverDateFrom, setTurnoverDateFrom] = useState("");
    const [turnoverDateTo, setTurnoverDateTo] = useState("");
    const [turnoverRows, setTurnoverRows] = useState([]);
    const [turnoverLoading, setTurnoverLoading] = useState(false);
    const [turnoverError, setTurnoverError] = useState("");

    const warehouseMap = useMemo(() => {
        const m = {};
        warehouses.forEach(w => {
            m[w.warehouseId] = w.warehouseName;
        });
        return m;
    }, [warehouses]);

    const productMap = useMemo(() => {
        const m = {};
        products.forEach(p => {
            m[p.productId] = p.productName;
        });
        return m;
    }, [products]);

    const uniqueProducts = useMemo(() => {
        const byName = new Map();
        products.forEach(p => {
            const rawName = String(p.productName ?? "").trim();
            if (!rawName) return;
            const key = rawName.toLowerCase();
            if (!byName.has(key)) byName.set(key, rawName);
        });
        return Array.from(byName.values()).sort((a, b) => a.localeCompare(b, "ru"));
    }, [products]);

    const loadData = async () => {
        setLoading(true);
        setError("");
        try {
            const [movRes, whRes, prodRes] = await Promise.all([
                fetch(API_MOVEMENTS),
                fetch(API_WAREHOUSES),
                fetch(API_PRODUCTS)
            ]);

            if (!movRes.ok) throw new Error("Не удалось загрузить движения");
            if (!whRes.ok) throw new Error("Не удалось загрузить склады");
            if (!prodRes.ok) throw new Error("Не удалось загрузить товары");

            const movData = await movRes.json();
            const whData = await whRes.json();
            const prodData = await prodRes.json();

            setMovements(Array.isArray(movData) ? movData : []);
            setWarehouses(Array.isArray(whData) ? whData : []);
            setProducts(Array.isArray(prodData) ? prodData : []);
            setEditDates(
                (Array.isArray(movData) ? movData : []).reduce((acc, m) => {
                    acc[m.id] = toDateTimeLocalValue(m.docDate);
                    return acc;
                }, {})
            );
        } catch (e) {
            console.error(e);
            setError(e.message || "Ошибка загрузки");
            setMovements([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const saveMovementDate = async (movementId) => {
        const dateVal = editDates[movementId];
        if (!dateVal) {
            alert("Выберите дату");
            return;
        }

        try {
            setSavingDateId(movementId);
            const res = await fetch(`${API_MOVEMENTS}/${movementId}/date`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ docDate: dateVal })
            });
            if (!res.ok) throw new Error("Не удалось обновить дату");
            await loadData();
        } catch (e) {
            console.error(e);
            alert(e.message || "Ошибка обновления даты");
        } finally {
            setSavingDateId(null);
        }
    };

    const loadReport = async () => {
        if (!reportProductId || !reportDateFrom || !reportDateTo) {
            alert("Выберите товар и период");
            return;
        }

        try {
            setReportLoading(true);
            setReportError("");
            const params = new URLSearchParams({
                productId: String(reportProductId),
                dateFrom: reportDateFrom,
                dateTo: reportDateTo
            });
            const res = await fetch(`${API_MOVEMENTS}/report?${params.toString()}`);
            if (!res.ok) throw new Error("Не удалось загрузить отчет");
            const data = await res.json();
            setReportRows(Array.isArray(data) ? data : []);
        } catch (e) {
            console.error(e);
            setReportRows([]);
            setReportError(e.message || "Ошибка отчета");
        } finally {
            setReportLoading(false);
        }
    };

    const loadTurnoverReport = async () => {
        if (!turnoverDateFrom || !turnoverDateTo) {
            alert("Выберите период");
            return;
        }

        try {
            setTurnoverLoading(true);
            setTurnoverError("");

            const params = new URLSearchParams({
                dateFrom: turnoverDateFrom,
                dateTo: turnoverDateTo
            });
            if (turnoverProductName) {
                params.set("productName", turnoverProductName);
            }

            const res = await fetch(`${API_MOVEMENTS}/turnover-report?${params.toString()}`);
            if (!res.ok) throw new Error("Не удалось загрузить отчет приход/уход");

            const data = await res.json();
            setTurnoverRows(Array.isArray(data) ? data : []);
        } catch (e) {
            console.error(e);
            setTurnoverRows([]);
            setTurnoverError(e.message || "Ошибка отчета");
        } finally {
            setTurnoverLoading(false);
        }
    };

    const filteredMovements = useMemo(() => {
        const filtered = movements.filter(m => {
            const byWarehouse = !filterWarehouse
                || String(m.fromWarehouseId) === filterWarehouse
                || String(m.toWarehouseId) === filterWarehouse;
            const byProduct = !filterProduct || String(m.productId).includes(filterProduct.trim());
            const productName = (productMap[m.productId] || `Товар #${m.productId}`).toLowerCase();
            const byProductName = !filterProductName || productName.includes(filterProductName.trim().toLowerCase());
            const byType = !filterType || m.docType === filterType;
            return byWarehouse && byProduct && byProductName && byType;
        });

        if (sortByDate) {
            filtered.sort((a, b) => {
                const aTime = new Date(a.docDate || 0).getTime();
                const bTime = new Date(b.docDate || 0).getTime();
                return sortByDate === "asc" ? aTime - bTime : bTime - aTime;
            });
        }

        if (sortByProductName) {
            filtered.sort((a, b) => {
                const aName = (productMap[a.productId] || `Товар #${a.productId}`).toLowerCase();
                const bName = (productMap[b.productId] || `Товар #${b.productId}`).toLowerCase();
                return sortByProductName === "asc"
                    ? aName.localeCompare(bName, "ru")
                    : bName.localeCompare(aName, "ru");
            });
        }

        return filtered;
    }, [movements, filterWarehouse, filterProduct, filterProductName, filterType, sortByDate, sortByProductName, productMap]);

    const uniqueTypes = useMemo(() => {
        return [...new Set(movements.map(m => m.docType).filter(Boolean))];
    }, [movements]);

    const reportProducts = useMemo(() => {
        const byId = new Map();
        movements
            .filter(m => m.docType === "receipt" && m.productId != null)
            .forEach(m => {
                const pid = Number(m.productId);
                if (!byId.has(pid)) {
                    byId.set(pid, productMap[pid] ?? `Товар #${pid}`);
                }
            });

        return Array.from(byId.entries())
            .map(([productId, productName]) => ({ productId, productName }))
            .sort((a, b) => a.productName.localeCompare(b.productName, "ru"));
    }, [movements, productMap]);

    const turnoverTotals = useMemo(() => {
        return turnoverRows.reduce((acc, row) => ({
            qtyIn: acc.qtyIn + Number(row.qtyIn ?? 0),
            qtyOutMovement: acc.qtyOutMovement + Number(row.qtyOutMovement ?? 0),
            qtyWriteoff: acc.qtyWriteoff + Number(row.qtyWriteoff ?? 0),
            qtyOutTotal: acc.qtyOutTotal + Number(row.qtyOutTotal ?? 0),
            amountIn: acc.amountIn + Number(row.amountIn ?? 0),
            amountOutMovement: acc.amountOutMovement + Number(row.amountOutMovement ?? 0),
            amountWriteoff: acc.amountWriteoff + Number(row.amountWriteoff ?? 0),
            amountOutTotal: acc.amountOutTotal + Number(row.amountOutTotal ?? 0)
        }), {
            qtyIn: 0,
            qtyOutMovement: 0,
            qtyWriteoff: 0,
            qtyOutTotal: 0,
            amountIn: 0,
            amountOutMovement: 0,
            amountWriteoff: 0,
            amountOutTotal: 0
        });
    }, [turnoverRows]);

    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <h2>Движения товаров</h2>
                <div className={styles.headerActions}>
                    <button className={styles.refreshBtn} onClick={loadData}>Обновить</button>
                    <button className={styles.refreshBtn} onClick={() => setShowReport(prev => !prev)}>
                        {showReport ? "Скрыть отчет" : "Отчет по закупкам"}
                    </button>
                    <button className={styles.refreshBtn} onClick={() => setShowTurnoverReport(prev => !prev)}>
                        {showTurnoverReport ? "Скрыть приход/уход" : "Отчет приход/уход"}
                    </button>
                </div>
            </div>

            {showReport && (
                <div className={styles.reportCard}>
                    <h3>Отчет по динамике закупок</h3>
                    <div className={styles.reportControls}>
                        <select
                            className={styles.input}
                            value={reportProductId}
                            onChange={e => setReportProductId(e.target.value)}
                        >
                            <option value="">Выберите товар</option>
                            {reportProducts.map(p => (
                                <option key={p.productId} value={p.productId}>
                                    {p.productName}
                                </option>
                            ))}
                        </select>
                        <input
                            type="date"
                            className={styles.input}
                            value={reportDateFrom}
                            onChange={e => setReportDateFrom(e.target.value)}
                        />
                        <input
                            type="date"
                            className={styles.input}
                            value={reportDateTo}
                            onChange={e => setReportDateTo(e.target.value)}
                        />
                        <button className={styles.refreshBtn} onClick={loadReport} disabled={reportLoading}>
                            {reportLoading ? "Загрузка..." : "Показать"}
                        </button>
                    </div>
                    {reportError && <p className={styles.error}>{reportError}</p>}
                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Дата</th>
                                <th>Кол-во</th>
                                <th>Цена</th>
                                <th>Сумма</th>
                                <th>Изм. цены</th>
                                <th>Изм. кол-ва</th>
                            </tr>
                            </thead>
                            <tbody>
                            {reportRows.length > 0 ? reportRows.map(r => (
                                <tr key={`${r.documentId}-${r.docDate}`}>
                                    <td>{formatDate(r.docDate)}</td>
                                    <td>{formatNumber(r.quantity)}</td>
                                    <td>{r.unitPrice != null ? `${formatNumber(r.unitPrice)} ₽` : "—"}</td>
                                    <td>{r.lineTotal != null ? `${formatNumber(r.lineTotal)} ₽` : "—"}</td>
                                    <td className={Number(r.priceDelta) > 0 ? styles.deltaUp : Number(r.priceDelta) < 0 ? styles.deltaDown : ""}>
                                        {r.priceDelta != null ? `${Number(r.priceDelta) > 0 ? "+" : ""}${formatNumber(r.priceDelta)} ₽` : "—"}
                                    </td>
                                    <td className={Number(r.quantityDelta) > 0 ? styles.deltaUp : Number(r.quantityDelta) < 0 ? styles.deltaDown : ""}>
                                        {r.quantityDelta != null ? `${Number(r.quantityDelta) > 0 ? "+" : ""}${formatNumber(r.quantityDelta)}` : "—"}
                                    </td>
                                </tr>
                            )) : (
                                <tr>
                                    <td colSpan="6" className={styles.empty}>Нет данных за выбранный период</td>
                                </tr>
                            )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {showTurnoverReport && (
                <div className={styles.reportCard}>
                    <h3>Отчет по приходу / уходу / списанию</h3>
                    <div className={styles.reportControls}>
                        <select
                            className={styles.input}
                            value={turnoverProductName}
                            onChange={e => setTurnoverProductName(e.target.value)}
                        >
                            <option value="">Все товары</option>
                            {uniqueProducts.map(productName => (
                                <option key={productName} value={productName}>
                                    {productName}
                                </option>
                            ))}
                        </select>
                        <input
                            type="date"
                            className={styles.input}
                            value={turnoverDateFrom}
                            onChange={e => setTurnoverDateFrom(e.target.value)}
                        />
                        <input
                            type="date"
                            className={styles.input}
                            value={turnoverDateTo}
                            onChange={e => setTurnoverDateTo(e.target.value)}
                        />
                        <button className={styles.refreshBtn} onClick={loadTurnoverReport} disabled={turnoverLoading}>
                            {turnoverLoading ? "Загрузка..." : "Показать"}
                        </button>
                    </div>

                    {turnoverError && <p className={styles.error}>{turnoverError}</p>}

                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Товар</th>
                                <th>Приход, кол-во</th>
                                <th>Уход, кол-во</th>
                                <th>Списание, кол-во</th>
                                <th>Итого уход, кол-во</th>
                                <th>Приход, сумма</th>
                                <th>Уход, сумма</th>
                                <th>Списание, сумма</th>
                                <th>Итого уход, сумма</th>
                            </tr>
                            </thead>
                            <tbody>
                            {turnoverRows.length > 0 ? (
                                <>
                                    {turnoverRows.map(r => (
                                        <tr key={`${r.productName ?? "unknown"}-${r.productId ?? "none"}`}>
                                            <td>{r.productName ?? productMap[r.productId] ?? `Товар #${r.productId}`}</td>
                                            <td className={styles.turnoverIn}>{formatNumber(r.qtyIn)}</td>
                                            <td className={styles.turnoverOut}>{formatNumber(r.qtyOutMovement)}</td>
                                            <td className={styles.turnoverWriteoff}>{formatNumber(r.qtyWriteoff)}</td>
                                            <td className={styles.turnoverTotal}>{formatNumber(r.qtyOutTotal)}</td>
                                            <td className={styles.turnoverIn}>{formatNumber(r.amountIn)} ₽</td>
                                            <td className={styles.turnoverOut}>{formatNumber(r.amountOutMovement)} ₽</td>
                                            <td className={styles.turnoverWriteoff}>{formatNumber(r.amountWriteoff)} ₽</td>
                                            <td className={styles.turnoverTotal}>{formatNumber(r.amountOutTotal)} ₽</td>
                                        </tr>
                                    ))}
                                    <tr className={styles.totalRow}>
                                        <td>ИТОГО</td>
                                        <td>{formatNumber(turnoverTotals.qtyIn)}</td>
                                        <td>{formatNumber(turnoverTotals.qtyOutMovement)}</td>
                                        <td>{formatNumber(turnoverTotals.qtyWriteoff)}</td>
                                        <td>{formatNumber(turnoverTotals.qtyOutTotal)}</td>
                                        <td>{formatNumber(turnoverTotals.amountIn)} ₽</td>
                                        <td>{formatNumber(turnoverTotals.amountOutMovement)} ₽</td>
                                        <td>{formatNumber(turnoverTotals.amountWriteoff)} ₽</td>
                                        <td>{formatNumber(turnoverTotals.amountOutTotal)} ₽</td>
                                    </tr>
                                </>
                            ) : (
                                <tr>
                                    <td colSpan="9" className={styles.empty}>Нет данных за выбранный период</td>
                                </tr>
                            )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            <div className={styles.filters}>
                <select
                    className={styles.input}
                    value={filterWarehouse}
                    onChange={e => setFilterWarehouse(e.target.value)}
                >
                    <option value="">Все склады</option>
                    {warehouses.map(w => (
                        <option key={w.warehouseId} value={w.warehouseId}>
                            {w.warehouseName}
                        </option>
                    ))}
                </select>

                <input
                    className={styles.input}
                    type="text"
                    placeholder="Фильтр по ID товара"
                    value={filterProduct}
                    onChange={e => setFilterProduct(e.target.value)}
                />

                <input
                    className={styles.input}
                    type="text"
                    placeholder="Фильтр по имени товара"
                    value={filterProductName}
                    onChange={e => setFilterProductName(e.target.value)}
                />

                <select
                    className={styles.input}
                    value={filterType}
                    onChange={e => setFilterType(e.target.value)}
                >
                    <option value="">Все типы</option>
                    {uniqueTypes.map(t => (
                        <option key={t} value={t}>{t}</option>
                    ))}
                </select>

                <select
                    className={styles.input}
                    value={sortByProductName}
                    onChange={e => setSortByProductName(e.target.value)}
                >
                    <option value="">Без сортировки</option>
                    <option value="asc">Товар: А-Я</option>
                    <option value="desc">Товар: Я-А</option>
                </select>

                <select
                    className={styles.input}
                    value={sortByDate}
                    onChange={e => setSortByDate(e.target.value)}
                >
                    <option value="desc">Дата: новые сверху</option>
                    <option value="asc">Дата: старые сверху</option>
                    <option value="">Дата: без сортировки</option>
                </select>
            </div>

            {loading ? (
                <p>Загрузка движений...</p>
            ) : error ? (
                <p className={styles.error}>{error}</p>
            ) : (
                <div className={styles.tableWrap}>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>ID</th>
                            <th>Дата</th>
                            <th>Тип</th>
                            <th>Со склада</th>
                            <th>На склад</th>
                            <th>Товар</th>
                            <th>ID товара</th>
                            <th>Количество</th>
                            <th>Цена</th>
                            <th>Сумма</th>
                            <th>Статус</th>
                            <th>Изменить дату</th>
                        </tr>
                        </thead>
                        <tbody>
                        {filteredMovements.length > 0 ? filteredMovements.map(m => (
                            <tr key={`${m.id}-${m.productId}`}>
                                <td>{m.id}</td>
                                <td>{formatDate(m.docDate)}</td>
                                <td>{m.docType ?? "—"}</td>
                                <td>{warehouseMap[m.fromWarehouseId] ?? m.fromWarehouseId ?? "—"}</td>
                                <td>{warehouseMap[m.toWarehouseId] ?? m.toWarehouseId ?? "—"}</td>
                                <td>{productMap[m.productId] ?? `Товар #${m.productId}`}</td>
                                <td>{m.productId}</td>
                                <td>{formatNumber(m.quantity)}</td>
                                <td>{m.unitPrice != null ? `${formatNumber(m.unitPrice)} ₽` : "—"}</td>
                                <td>{m.lineTotal != null ? `${formatNumber(m.lineTotal)} ₽` : "—"}</td>
                                <td>{m.status ?? "—"}</td>
                                <td>
                                    <div className={styles.dateEditCell}>
                                        <input
                                            type="datetime-local"
                                            className={styles.input}
                                            value={editDates[m.id] ?? ""}
                                            onChange={e => setEditDates(prev => ({ ...prev, [m.id]: e.target.value }))}
                                        />
                                        <button
                                            type="button"
                                            className={styles.refreshBtn}
                                            onClick={() => saveMovementDate(m.id)}
                                            disabled={savingDateId === m.id}
                                        >
                                            {savingDateId === m.id ? "..." : "Сохранить"}
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        )) : (
                            <tr>
                                <td colSpan="12" className={styles.empty}>Движений не найдено</td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
