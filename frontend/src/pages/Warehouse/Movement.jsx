import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import MovementHero from "./movement-page/MovementHero";
import MovementFilters from "./movement-page/MovementFilters";
import MovementLedger from "./movement-page/MovementLedger";
import styles from "./Movement.module.css";

const API_MOVEMENTS = `${API_BASE_URL}/movements`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;

const formatNumber = (value) => {
    const num = Number(value ?? 0);
    return Number.isFinite(num) ? num.toLocaleString("ru-RU") : "0";
};

const formatCurrency = (value) => `${formatNumber(value)} ₽`;

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

const toDateInputValue = (date) => {
    const pad = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

const applyQuickRange = (range, setFrom, setTo) => {
    const now = new Date();
    const to = toDateInputValue(now);
    const fromDate = new Date(now);

    if (range === "day") {
        const day = toDateInputValue(fromDate);
        setFrom(day);
        setTo(day);
        return;
    }

    if (range === "week") {
        fromDate.setDate(fromDate.getDate() - 6);
        setFrom(toDateInputValue(fromDate));
        setTo(to);
        return;
    }

    fromDate.setDate(1);
    setFrom(toDateInputValue(fromDate));
    setTo(to);
};

const getProductLabel = (product) => {
    if (!product) return "Все товары";
    const parts = [product.productName || `Товар #${product.productId}`];
    if (product.supplierName) parts.push(product.supplierName);
    return parts.join(" • ");
};

const getAverageValue = (amount, qty) => {
    const amountNum = Number(amount ?? 0);
    const qtyNum = Number(qty ?? 0);
    if (!Number.isFinite(amountNum) || !Number.isFinite(qtyNum) || Math.abs(qtyNum) < 1e-9) return 0;
    return amountNum / qtyNum;
};

export default function MovementPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [movements, setMovements] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const [filterWarehouse, setFilterWarehouse] = useState(() => searchParams.get("warehouse") ?? "");
    const [filterProduct, setFilterProduct] = useState(() => searchParams.get("productId") ?? "");
    const [filterProductName, setFilterProductName] = useState(() => searchParams.get("q") ?? "");
    const [filterType, setFilterType] = useState(() => searchParams.get("type") ?? "");
    const [sortByProductName, setSortByProductName] = useState(() => searchParams.get("productSort") ?? "");
    const [sortByDate, setSortByDate] = useState(() => searchParams.get("dateSort") ?? "desc");
    const [editDates, setEditDates] = useState({});
    const [savingDateId, setSavingDateId] = useState(null);
    const [dateError, setDateError] = useState("");
    const [showReport, setShowReport] = useState(false);
    const [reportProductId, setReportProductId] = useState("");
    const [reportDateFrom, setReportDateFrom] = useState("");
    const [reportDateTo, setReportDateTo] = useState("");
    const [reportRows, setReportRows] = useState([]);
    const [reportLoading, setReportLoading] = useState(false);
    const [reportError, setReportError] = useState("");
    const [showTurnoverReport, setShowTurnoverReport] = useState(false);
    const [turnoverProductId, setTurnoverProductId] = useState("");
    const [turnoverDateFrom, setTurnoverDateFrom] = useState("");
    const [turnoverDateTo, setTurnoverDateTo] = useState("");
    const [turnoverRows, setTurnoverRows] = useState([]);
    const [turnoverLoading, setTurnoverLoading] = useState(false);
    const [turnoverError, setTurnoverError] = useState("");
    const [productPickerTarget, setProductPickerTarget] = useState("");
    const [productSearch, setProductSearch] = useState("");
    const [printPayload, setPrintPayload] = useState(null);

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

    const productMetaMap = useMemo(() => {
        const m = {};
        products.forEach((product) => {
            m[product.productId] = product;
        });
        return m;
    }, [products]);

    const allProducts = useMemo(() => {
        return [...products].sort((a, b) =>
            String(a.productName || "").localeCompare(String(b.productName || ""), "ru")
        );
    }, [products]);

    const selectedReportProduct = useMemo(
        () => allProducts.find((product) => String(product.productId) === String(reportProductId)) || null,
        [allProducts, reportProductId]
    );

    const selectedTurnoverProduct = useMemo(
        () => allProducts.find((product) => String(product.productId) === String(turnoverProductId)) || null,
        [allProducts, turnoverProductId]
    );

    const filteredPickerProducts = useMemo(() => {
        const term = String(productSearch || "").trim().toLowerCase();
        if (!term) return allProducts;
        return allProducts.filter((product) => {
            const name = String(product.productName || "").toLowerCase();
            const supplier = String(product.supplierName || "").toLowerCase();
            const id = String(product.productId || "");
            return name.includes(term) || supplier.includes(term) || id.includes(term);
        });
    }, [allProducts, productSearch]);

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

    useEffect(() => {
        const next = new URLSearchParams(searchParams);
        const values = {
            warehouse: filterWarehouse,
            productId: filterProduct,
            q: filterProductName,
            type: filterType,
            productSort: sortByProductName,
            dateSort: sortByDate === "desc" ? "" : sortByDate
        };
        Object.entries(values).forEach(([key, value]) => {
            if (value) next.set(key, value);
            else next.delete(key);
        });
        if (next.toString() !== searchParams.toString()) {
            setSearchParams(next, { replace: true });
        }
    }, [
        filterProduct,
        filterProductName,
        filterType,
        filterWarehouse,
        searchParams,
        setSearchParams,
        sortByDate,
        sortByProductName
    ]);

    useEffect(() => {
        if (!printPayload) return undefined;

        const handleAfterPrint = () => {
            setPrintPayload(null);
        };

        window.addEventListener("afterprint", handleAfterPrint);
        const timerId = window.setTimeout(() => {
            window.print();
        }, 180);

        return () => {
            window.removeEventListener("afterprint", handleAfterPrint);
            window.clearTimeout(timerId);
        };
    }, [printPayload]);

    const saveMovementDate = async (movementId) => {
        const dateVal = editDates[movementId];
        if (!dateVal) {
            setDateError("Выберите дату движения перед сохранением.");
            return;
        }

        try {
            setSavingDateId(movementId);
            setDateError("");
            const res = await fetch(`${API_MOVEMENTS}/${movementId}/date`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ docDate: dateVal })
            });
            if (!res.ok) throw new Error("Не удалось обновить дату");
            await loadData();
        } catch (e) {
            console.error(e);
            setDateError(e.message || "Ошибка обновления даты");
        } finally {
            setSavingDateId(null);
        }
    };

    const loadReport = async () => {
        if (!reportDateFrom || !reportDateTo) {
            setReportError("Выберите даты начала и окончания периода.");
            return;
        }

        try {
            setReportLoading(true);
            setReportError("");
            const params = new URLSearchParams({
                dateFrom: reportDateFrom,
                dateTo: reportDateTo
            });
            if (reportProductId) {
                params.set("productId", String(reportProductId));
            }
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
            setTurnoverError("Выберите даты начала и окончания периода.");
            return;
        }

        try {
            setTurnoverLoading(true);
            setTurnoverError("");

            const params = new URLSearchParams({
                dateFrom: turnoverDateFrom,
                dateTo: turnoverDateTo
            });
            if (turnoverProductId) {
                params.set("productId", String(turnoverProductId));
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

    const receiptTotals = useMemo(() => {
        return reportRows.reduce((acc, row) => ({
            quantity: acc.quantity + Number(row.quantity ?? 0),
            amount: acc.amount + Number(row.lineTotal ?? 0)
        }), {
            quantity: 0,
            amount: 0
        });
    }, [reportRows]);

    const closeProductPicker = () => {
        setProductPickerTarget("");
        setProductSearch("");
    };

    const handlePickProduct = (product) => {
        if (productPickerTarget === "report") {
            setReportProductId(product ? String(product.productId) : "");
        }
        if (productPickerTarget === "turnover") {
            setTurnoverProductId(product ? String(product.productId) : "");
        }
        closeProductPicker();
    };

    const exportReceiptReportToPdf = () => {
        const subtitle = `Период: ${reportDateFrom || "не задан"} - ${reportDateTo || "не задан"}; товар: ${selectedReportProduct ? getProductLabel(selectedReportProduct) : "все товары"}`;
        setPrintPayload({
            title: "Отчет по динамике закупок",
            subtitle,
            headers: ["Товар", "Дата", "Кол-во", "Цена", "Сумма", "Изм. цены", "Изм. кол-ва"],
            rows: reportRows.map((row) => [
                row.productName ?? productMap[row.productId] ?? `Товар #${row.productId}`,
                formatDate(row.docDate),
                formatNumber(row.quantity),
                row.unitPrice != null ? formatCurrency(row.unitPrice) : "—",
                row.lineTotal != null ? formatCurrency(row.lineTotal) : "—",
                row.priceDelta != null ? `${Number(row.priceDelta) > 0 ? "+" : ""}${formatCurrency(row.priceDelta)}` : "—",
                row.quantityDelta != null ? `${Number(row.quantityDelta) > 0 ? "+" : ""}${formatNumber(row.quantityDelta)}` : "—"
            ]),
            totalsRow: ["ИТОГО", "", formatNumber(receiptTotals.quantity), "", formatCurrency(receiptTotals.amount), "", ""]
        });
    };

    const exportTurnoverReportToPdf = () => {
        const subtitle = `Период: ${turnoverDateFrom || "не задан"} - ${turnoverDateTo || "не задан"}; товар: ${selectedTurnoverProduct ? getProductLabel(selectedTurnoverProduct) : "все товары"}`;
        setPrintPayload({
            title: "Отчет по приходу / уходу / списанию",
            subtitle,
            headers: ["Товар", "Ед.", "Приход", "Уход", "Списание", "Итого уход", "Приход сумма", "Уход сумма", "Списание сумма", "Итого уход сумма", "Ср. цена прихода", "Ср. цена ухода", "Чистое кол-во", "Чистая сумма"],
            rows: turnoverRows.map((row) => {
                const unit = productMetaMap[row.productId]?.baseUnit || productMetaMap[row.productId]?.unit || "ед.";
                const netQty = Number(row.qtyIn ?? 0) - Number(row.qtyOutTotal ?? 0);
                const netAmount = Number(row.amountIn ?? 0) - Number(row.amountOutTotal ?? 0);
                return [
                    row.productName ?? productMap[row.productId] ?? `Товар #${row.productId}`,
                    unit,
                    formatNumber(row.qtyIn),
                    formatNumber(row.qtyOutMovement),
                    formatNumber(row.qtyWriteoff),
                    formatNumber(row.qtyOutTotal),
                    formatCurrency(row.amountIn),
                    formatCurrency(row.amountOutMovement),
                    formatCurrency(row.amountWriteoff),
                    formatCurrency(row.amountOutTotal),
                    formatCurrency(getAverageValue(row.amountIn, row.qtyIn)),
                    formatCurrency(getAverageValue(row.amountOutTotal, row.qtyOutTotal)),
                    formatNumber(netQty),
                    formatCurrency(netAmount)
                ];
            }),
            totalsRow: [
                "ИТОГО",
                "",
                formatNumber(turnoverTotals.qtyIn),
                formatNumber(turnoverTotals.qtyOutMovement),
                formatNumber(turnoverTotals.qtyWriteoff),
                formatNumber(turnoverTotals.qtyOutTotal),
                formatCurrency(turnoverTotals.amountIn),
                formatCurrency(turnoverTotals.amountOutMovement),
                formatCurrency(turnoverTotals.amountWriteoff),
                formatCurrency(turnoverTotals.amountOutTotal),
                "",
                "",
                formatNumber(turnoverTotals.qtyIn - turnoverTotals.qtyOutTotal),
                formatCurrency(turnoverTotals.amountIn - turnoverTotals.amountOutTotal)
            ]
        });
    };

    return (
        <div className={styles.page}>
            <div className={styles.screenContent}>
            <MovementHero
                movements={movements}
                loading={loading}
                showReport={showReport}
                showTurnoverReport={showTurnoverReport}
                onRefresh={loadData}
                onToggleReport={() => setShowReport((previous) => !previous)}
                onToggleTurnover={() => setShowTurnoverReport((previous) => !previous)}
            />

            {showReport && (
                <div className={styles.reportCard}>
                    <h3>Отчет по динамике закупок</h3>
                    <div className={styles.reportSummary}>
                        <span className={styles.summaryChip}>Товар: {selectedReportProduct ? getProductLabel(selectedReportProduct) : "Все товары"}</span>
                        <span className={styles.summaryChip}>Период: {reportDateFrom || "—"} - {reportDateTo || "—"}</span>
                    </div>
                    <div className={styles.reportControls}>
                        <button className={styles.pickerButton} type="button" onClick={() => setProductPickerTarget("report")}>
                            {selectedReportProduct ? getProductLabel(selectedReportProduct) : "Все товары"}
                        </button>
                        <button className={styles.clearButton} type="button" onClick={() => setReportProductId("")}>
                            Сбросить товар
                        </button>
                        <input
                            type="date"
                            name="receiptDateFrom"
                            aria-label="Начало периода закупок"
                            className={styles.input}
                            value={reportDateFrom}
                            onChange={e => setReportDateFrom(e.target.value)}
                        />
                        <input
                            type="date"
                            name="receiptDateTo"
                            aria-label="Конец периода закупок"
                            className={styles.input}
                            value={reportDateTo}
                            onChange={e => setReportDateTo(e.target.value)}
                        />
                        <button className={styles.refreshBtn} onClick={loadReport} disabled={reportLoading}>
                            {reportLoading ? "Загрузка…" : "Показать"}
                        </button>
                        <button className={styles.clearButton} type="button" onClick={exportReceiptReportToPdf} disabled={reportRows.length === 0}>
                            Сохранить PDF
                        </button>
                    </div>
                    <div className={styles.quickRangeRow}>
                        <button type="button" className={styles.quickRangeBtn} onClick={() => applyQuickRange("day", setReportDateFrom, setReportDateTo)}>
                            День
                        </button>
                        <button type="button" className={styles.quickRangeBtn} onClick={() => applyQuickRange("week", setReportDateFrom, setReportDateTo)}>
                            Неделя
                        </button>
                        <button type="button" className={styles.quickRangeBtn} onClick={() => applyQuickRange("month", setReportDateFrom, setReportDateTo)}>
                            Месяц
                        </button>
                    </div>
                    {reportError && <p className={styles.error}>{reportError}</p>}
                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Товар</th>
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
                                    <td>{r.productName ?? productMap[r.productId] ?? `Товар #${r.productId}`}</td>
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
                                    <td colSpan="7" className={styles.empty}>Нет данных за выбранный период</td>
                                </tr>
                            )}
                            {reportRows.length > 0 && (
                                <tr className={styles.totalRow}>
                                    <td>ИТОГО</td>
                                    <td>—</td>
                                    <td>{formatNumber(receiptTotals.quantity)}</td>
                                    <td>—</td>
                                    <td>{formatCurrency(receiptTotals.amount)}</td>
                                    <td>—</td>
                                    <td>—</td>
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
                    <div className={styles.reportSummary}>
                        <span className={styles.summaryChip}>Товар: {selectedTurnoverProduct ? getProductLabel(selectedTurnoverProduct) : "Все товары"}</span>
                        <span className={styles.summaryChip}>Период: {turnoverDateFrom || "—"} - {turnoverDateTo || "—"}</span>
                    </div>
                    <div className={styles.reportControls}>
                        <button className={styles.pickerButton} type="button" onClick={() => setProductPickerTarget("turnover")}>
                            {selectedTurnoverProduct ? getProductLabel(selectedTurnoverProduct) : "Все товары"}
                        </button>
                        <button className={styles.clearButton} type="button" onClick={() => setTurnoverProductId("")}>
                            Сбросить товар
                        </button>
                        <input
                            type="date"
                            name="turnoverDateFrom"
                            aria-label="Начало периода оборота"
                            className={styles.input}
                            value={turnoverDateFrom}
                            onChange={e => setTurnoverDateFrom(e.target.value)}
                        />
                        <input
                            type="date"
                            name="turnoverDateTo"
                            aria-label="Конец периода оборота"
                            className={styles.input}
                            value={turnoverDateTo}
                            onChange={e => setTurnoverDateTo(e.target.value)}
                        />
                        <button className={styles.refreshBtn} onClick={loadTurnoverReport} disabled={turnoverLoading}>
                            {turnoverLoading ? "Загрузка…" : "Показать"}
                        </button>
                        <button className={styles.clearButton} type="button" onClick={exportTurnoverReportToPdf} disabled={turnoverRows.length === 0}>
                            Сохранить PDF
                        </button>
                    </div>
                    <div className={styles.quickRangeRow}>
                        <button type="button" className={styles.quickRangeBtn} onClick={() => applyQuickRange("day", setTurnoverDateFrom, setTurnoverDateTo)}>
                            День
                        </button>
                        <button type="button" className={styles.quickRangeBtn} onClick={() => applyQuickRange("week", setTurnoverDateFrom, setTurnoverDateTo)}>
                            Неделя
                        </button>
                        <button type="button" className={styles.quickRangeBtn} onClick={() => applyQuickRange("month", setTurnoverDateFrom, setTurnoverDateTo)}>
                            Месяц
                        </button>
                    </div>

                    {turnoverError && <p className={styles.error}>{turnoverError}</p>}

                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Товар</th>
                                <th>Ед.</th>
                                <th>Приход, кол-во</th>
                                <th>Уход, кол-во</th>
                                <th>Списание, кол-во</th>
                                <th>Итого уход, кол-во</th>
                                <th>Приход, сумма</th>
                                <th>Уход, сумма</th>
                                <th>Списание, сумма</th>
                                <th>Итого уход, сумма</th>
                                <th>Ср. цена прихода</th>
                                <th>Ср. цена ухода</th>
                                <th>Чистое кол-во</th>
                                <th>Чистая сумма</th>
                            </tr>
                            </thead>
                            <tbody>
                            {turnoverRows.length > 0 ? (
                                <>
                                    {turnoverRows.map(r => (
                                        <tr key={`${r.productName ?? "unknown"}-${r.productId ?? "none"}`}>
                                            <td>{r.productName ?? productMap[r.productId] ?? `Товар #${r.productId}`}</td>
                                            <td>{productMetaMap[r.productId]?.baseUnit || productMetaMap[r.productId]?.unit || "ед."}</td>
                                            <td className={styles.turnoverIn}>{formatNumber(r.qtyIn)}</td>
                                            <td className={styles.turnoverOut}>{formatNumber(r.qtyOutMovement)}</td>
                                            <td className={styles.turnoverWriteoff}>{formatNumber(r.qtyWriteoff)}</td>
                                            <td className={styles.turnoverTotal}>{formatNumber(r.qtyOutTotal)}</td>
                                            <td className={styles.turnoverIn}>{formatCurrency(r.amountIn)}</td>
                                            <td className={styles.turnoverOut}>{formatCurrency(r.amountOutMovement)}</td>
                                            <td className={styles.turnoverWriteoff}>{formatCurrency(r.amountWriteoff)}</td>
                                            <td className={styles.turnoverTotal}>{formatCurrency(r.amountOutTotal)}</td>
                                            <td>{formatCurrency(getAverageValue(r.amountIn, r.qtyIn))}</td>
                                            <td>{formatCurrency(getAverageValue(r.amountOutTotal, r.qtyOutTotal))}</td>
                                            <td>{formatNumber(Number(r.qtyIn ?? 0) - Number(r.qtyOutTotal ?? 0))}</td>
                                            <td>{formatCurrency(Number(r.amountIn ?? 0) - Number(r.amountOutTotal ?? 0))}</td>
                                        </tr>
                                    ))}
                                    <tr className={styles.totalRow}>
                                        <td>ИТОГО</td>
                                        <td>—</td>
                                        <td>{formatNumber(turnoverTotals.qtyIn)}</td>
                                        <td>{formatNumber(turnoverTotals.qtyOutMovement)}</td>
                                        <td>{formatNumber(turnoverTotals.qtyWriteoff)}</td>
                                        <td>{formatNumber(turnoverTotals.qtyOutTotal)}</td>
                                        <td>{formatCurrency(turnoverTotals.amountIn)}</td>
                                        <td>{formatCurrency(turnoverTotals.amountOutMovement)}</td>
                                        <td>{formatCurrency(turnoverTotals.amountWriteoff)}</td>
                                        <td>{formatCurrency(turnoverTotals.amountOutTotal)}</td>
                                        <td>—</td>
                                        <td>—</td>
                                        <td>{formatNumber(turnoverTotals.qtyIn - turnoverTotals.qtyOutTotal)}</td>
                                        <td>{formatCurrency(turnoverTotals.amountIn - turnoverTotals.amountOutTotal)}</td>
                                    </tr>
                                </>
                            ) : (
                                <tr>
                                    <td colSpan="14" className={styles.empty}>Нет данных за выбранный период</td>
                                </tr>
                            )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            <section className={styles.ledgerSection} aria-labelledby="movement-ledger-heading">
                <div className={styles.sectionHeading}>
                    <div>
                        <p>Складской след</p>
                        <h2 id="movement-ledger-heading">Журнал операций</h2>
                        <span>Фильтры сохраняются в адресе страницы.</span>
                    </div>
                    <strong>{filteredMovements.length} из {movements.length}</strong>
                </div>
                <MovementFilters
                    warehouses={warehouses}
                    uniqueTypes={uniqueTypes}
                    values={{ filterWarehouse, filterProduct, filterProductName, filterType, sortByProductName, sortByDate }}
                    onChange={{
                        filterWarehouse: setFilterWarehouse,
                        filterProduct: setFilterProduct,
                        filterProductName: setFilterProductName,
                        filterType: setFilterType,
                        sortByProductName: setSortByProductName,
                        sortByDate: setSortByDate
                    }}
                    onReset={() => {
                        setFilterWarehouse("");
                        setFilterProduct("");
                        setFilterProductName("");
                        setFilterType("");
                        setSortByProductName("");
                        setSortByDate("desc");
                    }}
                />
                {dateError && <div className={`${styles.stateCard} ${styles.error}`} role="alert">{dateError}</div>}
                <MovementLedger
                    movements={filteredMovements}
                    loading={loading}
                    error={error}
                    warehouseMap={warehouseMap}
                    productMap={productMap}
                    editDates={editDates}
                    savingDateId={savingDateId}
                    onDateChange={(id, value) => setEditDates((previous) => ({ ...previous, [id]: value }))}
                    onSaveDate={saveMovementDate}
                    formatDate={formatDate}
                    formatNumber={formatNumber}
                />
            </section>

            {productPickerTarget && (
                <div className={styles.modalOverlay}>
                    <div
                        className={styles.modal}
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="movement-product-picker-title"
                    >
                        <div className={styles.modalHeader}>
                            <div>
                                <h3 className={styles.modalTitle} id="movement-product-picker-title">Выбор товара</h3>
                                <p className={styles.modalSubtitle}>
                                    Можно выбрать конкретный товар или оставить пусто, чтобы строить отчет по всем товарам.
                                </p>
                            </div>
                            <button type="button" className={styles.clearButton} onClick={closeProductPicker}>
                                Закрыть
                            </button>
                        </div>

                        <input
                            type="text"
                            name="movementProductPickerSearch"
                            autoComplete="off"
                            aria-label="Поиск товара"
                            className={styles.input}
                            placeholder="Поиск по названию, поставщику или ID…"
                            value={productSearch}
                            onChange={(e) => setProductSearch(e.target.value)}
                        />

                        <div className={styles.modalActions}>
                            <button type="button" className={styles.clearButton} onClick={() => handlePickProduct(null)}>
                                Все товары
                            </button>
                        </div>

                        <div className={styles.productPickerList}>
                            {filteredPickerProducts.length > 0 ? filteredPickerProducts.map((product) => (
                                <button
                                    key={product.productId}
                                    type="button"
                                    className={styles.productPickerItem}
                                    onClick={() => handlePickProduct(product)}
                                >
                                    <span className={styles.productPickerName}>{product.productName || `Товар #${product.productId}`}</span>
                                    <span className={styles.productPickerMeta}>
                                        ID: {product.productId}
                                        {product.supplierName ? ` • ${product.supplierName}` : ""}
                                    </span>
                                </button>
                            )) : (
                                <div className={styles.empty}>Ничего не найдено</div>
                            )}
                        </div>
                    </div>
                </div>
            )}
            </div>

            {printPayload && (
                <div className={styles.printSheet}>
                    <h1 className={styles.printTitle}>{printPayload.title}</h1>
                    <p className={styles.printSubtitle}>{printPayload.subtitle}</p>
                    <table className={styles.printTable}>
                        <thead>
                        <tr>
                            {printPayload.headers.map((header) => (
                                <th key={header}>{header}</th>
                            ))}
                        </tr>
                        </thead>
                        <tbody>
                        {printPayload.rows.map((row, rowIndex) => (
                            <tr key={`print-row-${rowIndex}`}>
                                {row.map((cell, cellIndex) => (
                                    <td key={`print-cell-${rowIndex}-${cellIndex}`}>{cell}</td>
                                ))}
                            </tr>
                        ))}
                        {printPayload.totalsRow && (
                            <tr className={styles.printTotalRow}>
                                {printPayload.totalsRow.map((cell, index) => (
                                    <td key={`print-total-${index}`}>{cell}</td>
                                ))}
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
