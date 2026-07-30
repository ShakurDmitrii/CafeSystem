import { useEffect, useMemo, useRef, useState } from "react";
import html2canvas from "html2canvas";
import { jsPDF } from "jspdf";
import { API_BASE_URL } from "../../auth";
import styles from "./InventoryShiftReport.module.css";

const API_SHIFTS = `${API_BASE_URL}/api/shifts`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;

const formatQty = (value) => {
    const n = Number(value ?? 0);
    if (!Number.isFinite(n)) return "0";
    return n.toLocaleString("ru-RU", { maximumFractionDigits: 3 });
};

const formatShiftLabel = (shift) => {
    if (!shift) return "";
    const date = shift?.data ?? "Без даты";
    const start = shift?.startTime ? String(shift.startTime).slice(0, 5) : "—";
    const end = shift?.endTime ? String(shift.endTime).slice(0, 5) : "открыта";
    return `Смена #${shift.shiftId} • ${date} • ${start}-${end}`;
};

const buildPdfFileName = (report) => {
    if (!report) return "warehouse-shift-report.pdf";
    const shiftId = report?.shiftId ?? "latest";
    const shiftDate = String(report?.shiftDate ?? "no-date").replace(/[^\d-]/g, "");
    return `warehouse-shift-report-shift-${shiftId}-${shiftDate}.pdf`;
};

export default function InventoryShiftReport({ warehouses, onApplied, initialShiftId, lockShiftSelection = false }) {
    const [shifts, setShifts] = useState([]);
    const [selectedShiftId, setSelectedShiftId] = useState(initialShiftId ? String(initialShiftId) : "");
    const [report, setReport] = useState(null);
    const [actualInputs, setActualInputs] = useState({});
    const [collapsed, setCollapsed] = useState(false);
    const [loadingShifts, setLoadingShifts] = useState(true);
    const [loadingReport, setLoadingReport] = useState(false);
    const [savingReport, setSavingReport] = useState(false);
    const [downloadingPdf, setDownloadingPdf] = useState(false);
    const [error, setError] = useState("");
    const reportRef = useRef(null);

    const reportWarehouse = useMemo(() => {
        if (!Array.isArray(warehouses) || warehouses.length === 0) return null;
        return warehouses.find((warehouse) => warehouse?.isMain) ?? warehouses[0];
    }, [warehouses]);

    const sortedShifts = useMemo(() => {
        const list = Array.isArray(shifts) ? [...shifts] : [];
        return list.sort((a, b) => {
            const aDate = `${a?.data ?? ""}T${a?.startTime ?? "00:00:00"}`;
            const bDate = `${b?.data ?? ""}T${b?.startTime ?? "00:00:00"}`;
            return bDate.localeCompare(aDate) || Number(b?.shiftId ?? 0) - Number(a?.shiftId ?? 0);
        });
    }, [shifts]);

    useEffect(() => {
        if (initialShiftId) {
            setSelectedShiftId(String(initialShiftId));
        }
    }, [initialShiftId]);

    useEffect(() => {
        let ignore = false;

        const loadShifts = async () => {
            setLoadingShifts(true);
            setError("");
            try {
                const res = await fetch(API_SHIFTS);
                const data = res.ok ? await res.json().catch(() => []) : [];
                if (ignore) return;
                const shiftsList = Array.isArray(data) ? data : [];
                setShifts(shiftsList);

                const sorted = [...shiftsList].sort((a, b) => {
                    const aDate = `${a?.data ?? ""}T${a?.startTime ?? "00:00:00"}`;
                    const bDate = `${b?.data ?? ""}T${b?.startTime ?? "00:00:00"}`;
                    return bDate.localeCompare(aDate) || Number(b?.shiftId ?? 0) - Number(a?.shiftId ?? 0);
                });

                setSelectedShiftId((prev) => {
                    if (initialShiftId) {
                        return String(initialShiftId);
                    }
                    if (prev && shiftsList.some((shift) => String(shift?.shiftId) === String(prev))) {
                        return prev;
                    }
                    return sorted[0]?.shiftId ? String(sorted[0].shiftId) : "";
                });
            } catch (err) {
                console.error(err);
                if (!ignore) {
                    setError("Не удалось загрузить список смен");
                    setShifts([]);
                    setSelectedShiftId("");
                }
            } finally {
                if (!ignore) {
                    setLoadingShifts(false);
                }
            }
        };

        loadShifts();
        return () => {
            ignore = true;
        };
    }, [initialShiftId]);

    useEffect(() => {
        if (!reportWarehouse?.warehouseId || !selectedShiftId) {
            setReport(null);
            return;
        }

        let ignore = false;

        const loadReport = async () => {
            setLoadingReport(true);
            setError("");
            try {
                const res = await fetch(
                    `${API_WAREHOUSES}/${reportWarehouse.warehouseId}/inventory-shift-report?shiftId=${selectedShiftId}`
                );
                const data = await res.json().catch(() => null);
                if (!res.ok) {
                    throw new Error(data?.message || "Не удалось загрузить отчёт");
                }
                if (ignore) return;

                const rows = Array.isArray(data?.rows) ? data.rows : [];
                setReport(data);
                setActualInputs(
                    rows.reduce((acc, row) => {
                        const productId = row?.productId;
                        if (productId == null) return acc;
                        const value = row?.actualQty ?? row?.expectedQty ?? row?.systemQty ?? 0;
                        acc[productId] = String(value);
                        return acc;
                    }, {})
                );
            } catch (err) {
                console.error(err);
                if (!ignore) {
                    setError(err.message || "Не удалось загрузить отчёт");
                    setReport(null);
                    setActualInputs({});
                }
            } finally {
                if (!ignore) {
                    setLoadingReport(false);
                }
            }
        };

        loadReport();
        return () => {
            ignore = true;
        };
    }, [reportWarehouse, selectedShiftId]);

    const handleActualChange = (productId, value) => {
        setActualInputs((prev) => ({
            ...prev,
            [productId]: value
        }));
    };

    const handleApply = async () => {
        if (!reportWarehouse?.warehouseId || !selectedShiftId || !report?.rows?.length) return;

        const payload = {
            shiftId: Number(selectedShiftId),
            rows: report.rows.map((row) => ({
                productId: row.productId,
                actualQty: Number(actualInputs[row.productId] ?? row.actualQty ?? row.expectedQty ?? row.systemQty ?? 0)
            }))
        };

        setSavingReport(true);
        setError("");
        try {
            const res = await fetch(
                `${API_WAREHOUSES}/${reportWarehouse.warehouseId}/inventory-shift-report/apply`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                }
            );
            const data = await res.json().catch(() => null);
            if (!res.ok) {
                throw new Error(data?.message || "Не удалось сохранить отчёт");
            }

            setReport(data);
            setActualInputs(
                (Array.isArray(data?.rows) ? data.rows : []).reduce((acc, row) => {
                    acc[row.productId] = String(row?.actualQty ?? row?.expectedQty ?? row?.systemQty ?? 0);
                    return acc;
                }, {})
            );

            if (typeof onApplied === "function") {
                await onApplied();
            }
        } catch (err) {
            console.error(err);
            setError(err.message || "Не удалось сохранить отчёт");
        } finally {
            setSavingReport(false);
        }
    };

    const handleDownloadPdf = async () => {
        if (!reportRef.current || !report?.rows?.length) return;

        setDownloadingPdf(true);
        setError("");
        try {
            const target = reportRef.current;
            const targetWidth = Math.max(target.scrollWidth, target.clientWidth, 1200);
            const targetHeight = Math.max(target.scrollHeight, target.clientHeight);

            const canvas = await html2canvas(target, {
                backgroundColor: "#ffffff",
                scale: 2,
                useCORS: true,
                width: targetWidth,
                height: targetHeight,
                windowWidth: targetWidth,
                windowHeight: targetHeight,
                scrollX: 0,
                scrollY: 0,
                onclone: (clonedDoc) => {
                    clonedDoc.querySelectorAll('[data-pdf-ignore="true"]').forEach((node) => {
                        node.remove();
                    });
                    clonedDoc.querySelectorAll('[data-pdf-expand="true"]').forEach((node) => {
                        node.style.overflow = "visible";
                    });
                    const clonedRoot = clonedDoc.querySelector('[data-pdf-report-root="true"]');
                    if (clonedRoot) {
                        clonedRoot.style.width = `${targetWidth}px`;
                        clonedRoot.style.maxWidth = "none";
                    }
                }
            });

            const imageData = canvas.toDataURL("image/png");
            const pdf = new jsPDF({
                orientation: "landscape",
                unit: "mm",
                format: "a4",
                compress: true
            });

            const pageWidth = pdf.internal.pageSize.getWidth();
            const pageHeight = pdf.internal.pageSize.getHeight();
            const margin = 8;
            const printableWidth = pageWidth - margin * 2;
            const printableHeight = pageHeight - margin * 2;
            const imageHeight = (canvas.height * printableWidth) / canvas.width;

            let heightLeft = imageHeight;
            let positionY = margin;

            pdf.addImage(imageData, "PNG", margin, positionY, printableWidth, imageHeight, undefined, "FAST");
            heightLeft -= printableHeight;

            while (heightLeft > 0) {
                positionY = margin - (imageHeight - heightLeft);
                pdf.addPage("a4", "landscape");
                pdf.addImage(imageData, "PNG", margin, positionY, printableWidth, imageHeight, undefined, "FAST");
                heightLeft -= printableHeight;
            }

            pdf.save(buildPdfFileName(report));
        } catch (err) {
            console.error(err);
            setError("Не удалось сформировать PDF");
        } finally {
            setDownloadingPdf(false);
        }
    };

    return (
        <section className={styles.card} ref={reportRef} data-pdf-report-root="true">
            <div className={styles.header}>
                <div>
                    <h3 className={styles.title}>Отчёт по смене</h3>
                    <p className={styles.subtitle}>
                        Продажи считаются по техкартам. Остатки берутся по главному складу,
                        затем можно ввести факт и заменить системные остатки.
                    </p>
                </div>
                <div className={styles.headerActions} data-pdf-ignore="true">
                    {reportWarehouse && (
                        <div className={styles.warehouseBadge}>
                            Склад: <strong>{reportWarehouse.warehouseName}</strong>
                        </div>
                    )}
                    <button
                        type="button"
                        className={styles.collapseButton}
                        onClick={() => setCollapsed((prev) => !prev)}
                    >
                        {collapsed ? "Развернуть отчёт" : "Скрыть отчёт"}
                    </button>
                </div>
            </div>

            {!collapsed && (
                <>
            <div className={styles.toolbar} data-pdf-ignore="true">
                <select
                    className={styles.select}
                    name="inventoryShiftId"
                    aria-label="Смена для складского отчёта"
                    value={selectedShiftId}
                    onChange={(e) => setSelectedShiftId(e.target.value)}
                    disabled={lockShiftSelection || loadingShifts || sortedShifts.length === 0}
                >
                    {sortedShifts.length === 0 ? (
                        <option value="">Нет смен</option>
                    ) : (
                        sortedShifts.map((shift) => (
                            <option key={shift.shiftId} value={shift.shiftId}>
                                {formatShiftLabel(shift)}
                            </option>
                        ))
                    )}
                </select>

                <div className={styles.actionsGroup}>
                    <button
                        type="button"
                        className={styles.pdfButton}
                        onClick={handleDownloadPdf}
                        disabled={downloadingPdf || savingReport || loadingReport || !report?.rows?.length}
                    >
                        {downloadingPdf ? "Формирование PDF…" : "Скачать PDF"}
                    </button>

                    <button
                        type="button"
                        className={styles.applyButton}
                        onClick={handleApply}
                        disabled={savingReport || loadingReport || !report?.rows?.length}
                    >
                        {savingReport ? "Сохранение…" : "Заменить остатки на фактические"}
                    </button>
                </div>
            </div>

            {error && <div className={styles.error}>{error}</div>}

            {loadingShifts || loadingReport ? (
                <div className={styles.emptyState}>Загрузка отчёта…</div>
            ) : !reportWarehouse ? (
                <div className={styles.emptyState}>Сначала создайте склад</div>
            ) : !selectedShiftId ? (
                <div className={styles.emptyState}>Нет доступных смен</div>
            ) : !report ? (
                <div className={styles.emptyState}>Отчёт пока недоступен</div>
            ) : (
                <>
                    <div className={styles.metaGrid}>
                        <div className={styles.metaCard}>
                            <span className={styles.metaLabel}>Смена</span>
                            <strong>#{report.shiftId}</strong>
                            <span>{report.shiftDate ?? "—"}</span>
                        </div>
                        <div className={styles.metaCard}>
                            <span className={styles.metaLabel}>Время</span>
                            <strong>{report.shiftStartTime ? String(report.shiftStartTime).slice(0, 5) : "—"}</strong>
                            <span>{report.shiftEndTime ? `до ${String(report.shiftEndTime).slice(0, 5)}` : "открыта"}</span>
                        </div>
                        <div className={styles.metaCard}>
                            <span className={styles.metaLabel}>Заказов</span>
                            <strong>{report.ordersCount ?? 0}</strong>
                            <span>позиций: {report.soldPositionsCount ?? 0}</span>
                        </div>
                        <div className={styles.metaCard}>
                            <span className={styles.metaLabel}>Статус</span>
                            <strong>{report.saved ? "Сохранён" : "Черновой"}</strong>
                            <span>{report.appliedAt ? `применён ${String(report.appliedAt).replace("T", " ").slice(0, 16)}` : "ещё не применён"}</span>
                        </div>
                    </div>

                    {!report.snapshotAvailable && (
                        <div className={styles.warningPanel}>
                            У этой смены нет снимка склада на момент открытия. Колонка «Было» рассчитана приблизительно
                            из текущего остатка, движений и продаж. Для следующих смен стартовые остатки уже будут
                            сохраняться автоматически.
                        </div>
                    )}

                    <div className={styles.salesBlock}>
                        <div className={styles.salesTitle}>Продажи за смену</div>
                        {Array.isArray(report.sales) && report.sales.length > 0 ? (
                            <div className={styles.salesList}>
                                {report.sales.map((item) => (
                                    <div key={`${item.itemType}-${item.itemId}`} className={styles.saleChip}>
                                        <span>{item.itemName}</span>
                                        <strong>x{item.qty}</strong>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className={styles.emptyState}>В этой смене нет продаж</div>
                        )}
                    </div>

                    <div className={styles.tableWrap} data-pdf-expand="true">
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>Продукт</th>
                                <th>Было</th>
                                <th>Движения</th>
                                <th>Продажи</th>
                                <th>Должно быть</th>
                                <th>В системе</th>
                                <th>Фактический остаток</th>
                                <th>Расхождение</th>
                                <th>Контроль</th>
                            </tr>
                            </thead>
                            <tbody>
                            {(report.rows ?? []).map((row) => {
                                const actualValue = actualInputs[row.productId] ?? String(row.actualQty ?? row.expectedQty ?? row.systemQty ?? 0);
                                const actualQty = Number(actualValue);
                                const expectedQty = Number(row.expectedQty ?? 0);
                                const discrepancy = Number.isFinite(actualQty)
                                    ? actualQty - expectedQty
                                    : Number(row.discrepancyQty ?? 0);
                                const movementNet = Number(row.movementNetQty ?? 0);

                                const discrepancyClass = discrepancy < -0.000001
                                    ? styles.discrepancyNegative
                                    : discrepancy > 0.000001
                                        ? styles.discrepancyPositive
                                        : styles.discrepancyNeutral;

                                return (
                                    <tr key={row.productId}>
                                        <td>
                                            <div className={styles.productName}>{row.productName}</div>
                                            <div className={styles.productMeta}>ID: {row.productId} • {row.unit || "ед."}</div>
                                        </td>
                                        <td>{formatQty(row.openingQty)} {row.unit}</td>
                                        <td>
                                            <div className={movementNet < -0.000001 ? styles.netOut : movementNet > 0.000001 ? styles.netIn : styles.netNeutral}>
                                                {movementNet > 0.000001 ? "+" : ""}
                                                {formatQty(row.movementNetQty)} {row.unit}
                                            </div>
                                            <div className={styles.productMeta}>
                                                +{formatQty(row.movementInQty)} / -{formatQty(row.movementOutQty)}
                                            </div>
                                        </td>
                                        <td>{formatQty(row.soldQty)} {row.unit}</td>
                                        <td>{formatQty(row.expectedQty)} {row.unit}</td>
                                        <td>{formatQty(row.systemQty)} {row.unit}</td>
                                        <td>
                                            <input
                                                className={styles.input}
                                                type="number"
                                                name={`actualQuantity-${row.productId}`}
                                                aria-label={`Фактический остаток: ${row.productName}`}
                                                autoComplete="off"
                                                min="0"
                                                step="0.001"
                                                value={actualValue}
                                                onChange={(e) => handleActualChange(row.productId, e.target.value)}
                                            />
                                        </td>
                                        <td>
                                            <span className={`${styles.discrepancy} ${discrepancyClass}`}>
                                                {discrepancy < -0.000001 ? "−" : discrepancy > 0.000001 ? "+" : ""}
                                                {formatQty(Math.abs(discrepancy))} {row.unit}
                                            </span>
                                            <div className={styles.productMeta}>
                                                {discrepancy < -0.000001
                                                    ? "перерасход"
                                                    : discrepancy > 0.000001
                                                        ? "лишний остаток"
                                                        : "без расхождения"}
                                            </div>
                                        </td>
                                        <td>
                                            {row.shortageFlag ? (
                                                <div className={styles.warningBadge}>
                                                    Возможна нехватка: {formatQty(row.shortageQty)} {row.unit}
                                                </div>
                                            ) : (
                                                <span className={styles.okBadge}>Ок</span>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
                </>
            )}
        </section>
    );
}
