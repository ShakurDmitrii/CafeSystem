import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { API_BASE_URL } from "../../auth";
import styles from "./TaxPage.module.css";

const API_OVERVIEW = `${API_BASE_URL}/api/tax/overview`;
const API_BACKFILL = `${API_BASE_URL}/api/tax/backfill-existing`;
const API_RELAY = `${API_BASE_URL}/api/tax/relay-pending`;
const API_SEND_EXISTING = `${API_BASE_URL}/api/tax/send-existing`;
const API_SEND_ONE = `${API_BASE_URL}/api/tax/send-one`;
const API_SEND_JOBS = `${API_BASE_URL}/api/tax/send-jobs`;
const API_RETRY_FAILED = `${API_BASE_URL}/api/tax/retry-failed`;
const API_RECEIPT_DETAILS = `${API_BASE_URL}/api/tax/receipt-details`;

const STATUS_LABELS = {
    pending: "Ожидает",
    processing: "В обработке",
    processed: "Обработано",
    sent: "Отправлено",
    failed: "Ошибка",
    dead_letter: "Dead letter",
    manual_required: "Нужна проверка",
    cancelled: "Отменено"
};

const emptyOverview = {
    paidOrdersCount: 0,
    dispatchIntegration: {
        mode: "SAFE",
        provider: "SAFE",
        configured: false,
        ready: false,
        message: "Интеграция не настроена"
    },
    outboxStatusCounts: {},
    jobStatusCounts: {},
    recentOutbox: [],
    recentJobs: []
};

const moneyFormatter = new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 2
});
const dateFormatter = new Intl.DateTimeFormat("ru-RU");
const dateTimeFormatter = new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "short",
    timeStyle: "short"
});
const timeFormatter = new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit"
});

function parseJsonSafe(text) {
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

function statusKey(status) {
    return String(status || "").toLowerCase();
}

function formatStatus(status) {
    const key = statusKey(status);
    return STATUS_LABELS[key] || key || "—";
}

function formatDateTime(value) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : dateTimeFormatter.format(date);
}

function dateTone(value) {
    if (!value) return styles.dateOld;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return styles.dateOld;
    const today = new Date();
    const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const currentDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const difference = Math.round((currentDay - day) / 86400000);
    if (difference <= 0) return styles.dateToday;
    if (difference === 1) return styles.dateYesterday;
    if (difference <= 3) return styles.dateRecent;
    return styles.dateOld;
}

function dateBadge(value) {
    if (!value) return { date: "—", time: "" };
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return { date: String(value), time: "" };
    return { date: dateFormatter.format(date), time: timeFormatter.format(date) };
}

function money(value) {
    const number = Number(value || 0);
    return moneyFormatter.format(Number.isFinite(number) ? number : 0);
}

function StatusPill({ status }) {
    return (
        <span className={styles.statusPill} data-status={statusKey(status)}>
            {formatStatus(status)}
        </span>
    );
}

function DateStamp({ value, fallback = "дата смены" }) {
    const badge = dateBadge(value);
    return (
        <>
            <span className={`${styles.dateBadge} ${dateTone(value)}`}>
                <span>{badge.date}</span>
                <small>{badge.time || fallback}</small>
            </span>
        </>
    );
}

function StatusSummary({ values }) {
    const entries = Object.entries(values || {});
    if (!entries.length) return <span className={styles.summaryEmpty}>Статусов пока нет</span>;
    return (
        <span className={styles.summaryList}>
            {entries.map(([status, count]) => (
                <span key={status}>
                    {formatStatus(status)} <strong>{Number(count || 0)}</strong>
                </span>
            ))}
        </span>
    );
}

function ReceiptDialog({ loading, receipt, dispatchReady, sending, onClose, onSend }) {
    const dialogRef = useRef(null);

    useEffect(() => {
        const previousFocus = document.activeElement;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        const dialog = dialogRef.current;
        const getFocusableItems = () => [...(dialog?.querySelectorAll(
            'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
        ) || [])].filter((item) => item.getClientRects().length > 0);
        (dialog?.querySelector("[data-dialog-close]") || getFocusableItems()[0])?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape") {
                event.preventDefault();
                onClose();
                return;
            }
            if (event.key !== "Tab" || !dialog) return;
            const items = getFocusableItems();
            if (!items.length) return;
            const first = items[0];
            const last = items[items.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        };

        document.addEventListener("keydown", handleKeyDown);
        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = previousOverflow;
            previousFocus?.focus?.();
        };
    }, [onClose]);

    const orderId = receipt?.orderId;

    return (
        <div className={styles.modalLayer}>
            <button
                type="button"
                className={styles.modalBackdrop}
                aria-label="Закрыть карточку чека"
                onClick={onClose}
            />
            <section
                ref={dialogRef}
                className={styles.modalCard}
                role="dialog"
                aria-modal="true"
                aria-labelledby="tax-receipt-title"
                aria-describedby="tax-receipt-description"
            >
                <header className={styles.modalHeader}>
                    <div>
                        <p className={styles.modalEyebrow}>ТРАССИРОВКА ЧЕКА</p>
                        <h2 id="tax-receipt-title">Заказ #{orderId || "—"}</h2>
                        <p id="tax-receipt-description">Заказ и последние этапы его обработки.</p>
                    </div>
                    <div className={styles.modalHeaderActions}>
                        <button
                            type="button"
                            className={styles.primaryBtn}
                            disabled={sending || !dispatchReady || !orderId}
                            onClick={() => onSend(orderId)}
                        >
                            {sending ? "Отправка…" : "Отправить снова"}
                        </button>
                        <button type="button" className={styles.iconBtn} onClick={onClose} aria-label="Закрыть" data-dialog-close>
                            ×
                        </button>
                    </div>
                </header>

                {loading ? (
                    <div className={styles.modalLoading} role="status">Загружаем данные чека…</div>
                ) : (
                    <div className={styles.modalBody}>
                        <section className={styles.modalSection}>
                            <div className={styles.sectionHeading}>
                                <h3>Заказ</h3>
                                <span>Источник</span>
                            </div>
                            {!receipt?.order ? (
                                <p className={styles.emptyText}>Заказ не найден.</p>
                            ) : (
                                <dl className={styles.orderInfoGrid}>
                                    <div><dt>ID</dt><dd>{receipt.order.orderId}</dd></div>
                                    <div><dt>Дата смены</dt><dd>{receipt.order.businessDate || "—"}</dd></div>
                                    <div><dt>Создан</dt><dd>{formatDateTime(receipt.order.createdAt)}</dd></div>
                                    <div><dt>Сумма</dt><dd>{money(receipt.order.amount)}</dd></div>
                                    <div><dt>Оплачен</dt><dd>{receipt.order.isPaid ? "Да" : "Нет"}</dd></div>
                                    <div><dt>Оплата</dt><dd>{receipt.order.paymentType || "—"}</dd></div>
                                </dl>
                            )}
                        </section>
                        <section className={styles.traceGrid}>
                            <article className={styles.traceCard}>
                                <span className={styles.traceIndex}>01</span>
                                <div>
                                    <p>Очередь Outbox</p>
                                    <strong>{receipt?.outbox?.length || 0} записей</strong>
                                    <small>{receipt?.outbox?.[0]?.lastError || "Ошибок нет"}</small>
                                </div>
                            </article>
                            <article className={styles.traceCard}>
                                <span className={styles.traceIndex}>02</span>
                                <div>
                                    <p>Налоговая БД</p>
                                    <strong>{receipt?.jobs?.length || 0} задач</strong>
                                    <small>Статус: {formatStatus(receipt?.jobs?.[0]?.status)}</small>
                                </div>
                            </article>
                        </section>
                    </div>
                )}
            </section>
        </div>
    );
}

export default function TaxPage() {
    const [overview, setOverview] = useState(emptyOverview);
    const [loading, setLoading] = useState(true);
    const [runningAction, setRunningAction] = useState("");
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [manualOrderId, setManualOrderId] = useState("");
    const [manualReceiptDate, setManualReceiptDate] = useState("");
    const [selectedReceipt, setSelectedReceipt] = useState(null);
    const [modalLoading, setModalLoading] = useState(false);
    const [filters, setFilters] = useState({
        fromDate: "",
        toDate: "",
        backfillLimit: 1000,
        relayLimit: 500,
        sendLimit: 100,
        retryLimit: 500,
        refreshSeconds: 15
    });

    const totals = useMemo(() => ({
        outbox: Object.values(overview.outboxStatusCounts || {}).reduce((sum, value) => sum + Number(value || 0), 0),
        jobs: Object.values(overview.jobStatusCounts || {}).reduce((sum, value) => sum + Number(value || 0), 0)
    }), [overview]);

    const loadOverview = useCallback(async ({ silent = false } = {}) => {
        if (!silent) {
            setLoading(true);
            setError("");
        }
        try {
            const response = await fetch(`${API_OVERVIEW}?limit=50`);
            const body = parseJsonSafe(await response.text());
            if (!response.ok) throw new Error(body?.message || `Ошибка загрузки (${response.status})`);
            setOverview({
                paidOrdersCount: Number(body?.paidOrdersCount || 0),
                dispatchIntegration: body?.dispatchIntegration || body?.myTaxIntegration || emptyOverview.dispatchIntegration,
                outboxStatusCounts: body?.outboxStatusCounts || {},
                jobStatusCounts: body?.jobStatusCounts || {},
                recentOutbox: Array.isArray(body?.recentOutbox) ? body.recentOutbox : [],
                recentJobs: Array.isArray(body?.recentJobs) ? body.recentJobs : []
            });
        } catch (loadError) {
            if (!silent) setError(loadError?.message || "Ошибка загрузки");
        } finally {
            if (!silent) setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadOverview();
    }, [loadOverview]);

    useEffect(() => {
        const seconds = Number(filters.refreshSeconds || 0);
        if (seconds <= 0) return undefined;
        const timer = window.setInterval(() => {
            if (!runningAction && !modalLoading) loadOverview({ silent: true });
        }, seconds * 1000);
        return () => window.clearInterval(timer);
    }, [filters.refreshSeconds, loadOverview, modalLoading, runningAction]);

    const runAction = async (actionKey, url, payload) => {
        const requiresDispatch = actionKey === "send_one" || actionKey === "send_jobs";
        if (requiresDispatch && !overview?.dispatchIntegration?.ready) {
            setError(overview?.dispatchIntegration?.message || "Интеграция отправки чеков не настроена");
            return null;
        }
        setRunningAction(actionKey);
        setError("");
        setMessage("");
        try {
            const response = await fetch(url, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload || {})
            });
            const body = parseJsonSafe(await response.text()) || {};
            if (!response.ok) throw new Error(body?.message || `Ошибка (${response.status})`);
            setMessage("Операция выполнена");
            await loadOverview();
            return body;
        } catch (actionError) {
            setError(actionError?.message || "Ошибка операции");
            return null;
        } finally {
            setRunningAction("");
        }
    };

    const openReceipt = async (orderId) => {
        const id = Number(orderId || 0);
        if (!id) return;
        setModalLoading(true);
        setSelectedReceipt({ orderId: id, order: null, outbox: [], jobs: [] });
        try {
            const response = await fetch(`${API_RECEIPT_DETAILS}?orderId=${id}`);
            const body = parseJsonSafe(await response.text()) || {};
            if (!response.ok) throw new Error(body?.message || `Ошибка (${response.status})`);
            setSelectedReceipt({
                orderId: id,
                order: body?.order || null,
                outbox: Array.isArray(body?.outbox) ? body.outbox : [],
                jobs: Array.isArray(body?.jobs) ? body.jobs : []
            });
        } catch (receiptError) {
            setSelectedReceipt(null);
            setError(`Не удалось открыть чек #${id}: ${receiptError?.message || "ошибка"}`);
        } finally {
            setModalLoading(false);
        }
    };

    const closeReceipt = useCallback(() => {
        setSelectedReceipt(null);
        setModalLoading(false);
    }, []);

    const sendOne = async (orderIdValue) => {
        const id = Number(orderIdValue || 0);
        if (!Number.isInteger(id) || id <= 0) {
            setError("Введите корректный ID заказа");
            return;
        }
        setManualOrderId(String(id));
        const payload = { orderId: id, forcePending: true };
        if (manualReceiptDate) payload.receiptDate = manualReceiptDate;
        const result = await runAction("send_one", API_SEND_ONE, payload);
        if (result) await openReceipt(id);
    };

    const datePayload = {};
    if (filters.fromDate) datePayload.fromDate = filters.fromDate;
    if (filters.toDate) datePayload.toDate = filters.toDate;
    const dispatchReady = Boolean(overview?.dispatchIntegration?.ready);
    const dispatchMessage = overview?.dispatchIntegration?.message || "Интеграция не настроена";
    const dispatchProvider = overview?.dispatchIntegration?.provider || "партнёр";
    const dispatchMode = overview?.dispatchIntegration?.mode || "SAFE";
    const busy = Boolean(runningAction);

    return (
        <div className={styles.page}>
            <section className={styles.hero}>
                <div className={styles.heroCopy}>
                    <p className={styles.eyebrow}>ФИСКАЛИЗАЦИЯ · {dispatchMode}</p>
                    <h1>Поток чеков</h1>
                    <p className={styles.subtitle}>
                        Контроль очереди, налоговой базы и отправки чеков через {dispatchProvider}.
                    </p>
                </div>
                <div className={styles.integrationCard}>
                    <span className={dispatchReady ? styles.readyDot : styles.safeDot} aria-hidden="true" />
                    <div>
                        <small>Канал отправки</small>
                        <strong>{dispatchReady ? "Готов к работе" : "Безопасный режим"}</strong>
                        <span>{dispatchMessage}</span>
                    </div>
                    <button
                        type="button"
                        className={styles.heroButton}
                        onClick={() => loadOverview()}
                        disabled={loading || busy}
                    >
                        {loading ? "Обновление…" : "Обновить"}
                    </button>
                </div>
            </section>

            {(error || message) && (
                <div
                    className={`${styles.banner} ${error ? styles.bannerError : styles.bannerOk}`}
                    role={error ? "alert" : "status"}
                    aria-live={error ? "assertive" : "polite"}
                >
                    {error || message}
                </div>
            )}
            {!dispatchReady && (
                <div className={`${styles.banner} ${styles.bannerWarn}`} role="status">
                    Внешняя отправка отключена. Очередь и перенос в налоговую БД доступны.
                </div>
            )}

            <section className={styles.pipeline} aria-labelledby="tax-pipeline-title">
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.sectionEyebrow}>СОСТОЯНИЕ КОНТУРА</p>
                        <h2 id="tax-pipeline-title">Маршрут данных</h2>
                    </div>
                    <span className={styles.autoRefresh}>Автообновление: {filters.refreshSeconds ? `${filters.refreshSeconds} сек` : "выключено"}</span>
                </div>
                <div className={styles.pipelineGrid}>
                    <article className={styles.pipelineStep}>
                        <span>01</span><small>Оплаченные заказы</small><strong>{overview.paidOrdersCount}</strong>
                    </article>
                    <article className={styles.pipelineStep}>
                        <span>02</span><small>Очередь Outbox</small><strong>{totals.outbox}</strong>
                        <StatusSummary values={overview.outboxStatusCounts} />
                    </article>
                    <article className={styles.pipelineStep}>
                        <span>03</span><small>Налоговая БД</small><strong>{totals.jobs}</strong>
                        <StatusSummary values={overview.jobStatusCounts} />
                    </article>
                    <article className={`${styles.pipelineStep} ${dispatchReady ? styles.pipelineReady : styles.pipelineSafe}`}>
                        <span>04</span><small>Канал партнёра</small><strong>{dispatchReady ? "ON" : "SAFE"}</strong>
                        <span className={styles.summaryEmpty}>{dispatchProvider}</span>
                    </article>
                </div>
            </section>

            <section className={styles.commandCenter} aria-labelledby="tax-command-title">
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.sectionEyebrow}>КОМАНДНЫЙ ЦЕНТР</p>
                        <h2 id="tax-command-title">Запуск обработки</h2>
                    </div>
                    {busy && <span className={styles.busyState} role="status">Выполняется операция…</span>}
                </div>

                <div className={styles.singleReceiptBox}>
                    <div>
                        <span className={styles.commandNumber}>01</span>
                        <h3>Один чек</h3>
                        <p>Дата необязательна — без неё используется дата заказа.</p>
                    </div>
                    <div className={styles.singleReceiptActions}>
                        <label>
                            <span>ID заказа</span>
                            <input
                                name="taxOrderId"
                                inputMode="numeric"
                                type="number"
                                min="1"
                                value={manualOrderId}
                                onChange={(event) => setManualOrderId(event.target.value)}
                                placeholder="Например, 1842"
                                autoComplete="off"
                            />
                        </label>
                        <label>
                            <span>Дата чека</span>
                            <input
                                name="taxReceiptDate"
                                type="date"
                                value={manualReceiptDate}
                                onChange={(event) => setManualReceiptDate(event.target.value)}
                                autoComplete="off"
                            />
                        </label>
                        <button
                            type="button"
                            className={styles.primaryBtn}
                            disabled={busy || !dispatchReady}
                            onClick={() => sendOne(manualOrderId)}
                        >
                            {runningAction === "send_one" ? "Отправка…" : "Отправить чек"}
                        </button>
                    </div>
                </div>

                <div className={styles.filters}>
                    <label><span>С даты</span><input name="taxFromDate" type="date" value={filters.fromDate} onChange={(event) => setFilters((current) => ({ ...current, fromDate: event.target.value }))} autoComplete="off" /></label>
                    <label><span>По дату</span><input name="taxToDate" type="date" value={filters.toDate} onChange={(event) => setFilters((current) => ({ ...current, toDate: event.target.value }))} autoComplete="off" /></label>
                    <label><span>Backfill</span><input name="taxBackfillLimit" type="number" min="1" value={filters.backfillLimit} onChange={(event) => setFilters((current) => ({ ...current, backfillLimit: event.target.value }))} autoComplete="off" /></label>
                    <label><span>Relay</span><input name="taxRelayLimit" type="number" min="1" value={filters.relayLimit} onChange={(event) => setFilters((current) => ({ ...current, relayLimit: event.target.value }))} autoComplete="off" /></label>
                    <label><span>Отправка</span><input name="taxSendLimit" type="number" min="1" value={filters.sendLimit} onChange={(event) => setFilters((current) => ({ ...current, sendLimit: event.target.value }))} autoComplete="off" /></label>
                    <label><span>Повтор</span><input name="taxRetryLimit" type="number" min="1" value={filters.retryLimit} onChange={(event) => setFilters((current) => ({ ...current, retryLimit: event.target.value }))} autoComplete="off" /></label>
                    <label>
                        <span>Автообновление</span>
                        <select name="taxRefreshSeconds" value={String(filters.refreshSeconds)} onChange={(event) => setFilters((current) => ({ ...current, refreshSeconds: Number(event.target.value) }))}>
                            <option value="0">Выключено</option>
                            <option value="10">10 сек</option>
                            <option value="15">15 сек</option>
                            <option value="30">30 сек</option>
                            <option value="60">60 сек</option>
                        </select>
                    </label>
                </div>

                <div className={styles.actions}>
                    <button type="button" className={styles.primaryBtn} disabled={busy} onClick={() => runAction("send_existing", API_SEND_EXISTING, { ...datePayload, backfillLimit: Number(filters.backfillLimit || 1000), relayLimit: Number(filters.relayLimit || 500) })}>Полный запуск</button>
                    <button type="button" className={styles.secondaryBtn} disabled={busy} onClick={() => runAction("backfill", API_BACKFILL, { ...datePayload, limit: Number(filters.backfillLimit || 1000) })}>Заполнить очередь</button>
                    <button type="button" className={styles.secondaryBtn} disabled={busy} onClick={() => runAction("relay", API_RELAY, { limit: Number(filters.relayLimit || 500) })}>Перенести в налоговую БД</button>
                    <button type="button" className={styles.secondaryBtn} disabled={busy || !dispatchReady} onClick={() => runAction("send_jobs", API_SEND_JOBS, { limit: Number(filters.sendLimit || 100) })}>Отправить партнёру</button>
                    <button type="button" className={styles.secondaryBtn} disabled={busy} onClick={() => runAction("retry", API_RETRY_FAILED, { outboxLimit: Number(filters.retryLimit || 500), jobsLimit: Number(filters.retryLimit || 500) })}>Повторить ошибки</button>
                </div>

                <details className={styles.instructions}>
                    <summary>Как работает обработка</summary>
                    <ol>
                        <li>«Полный запуск» добавляет оплаченные заказы в Outbox и переносит их в налоговую БД.</li>
                        <li>«Отправить партнёру» передаёт ожидающие задачи во внешний сервис, когда канал настроен.</li>
                        <li>«Повторить ошибки» возвращает в работу записи со статусами «Ошибка» и Dead letter.</li>
                        <li>Кнопка с номером заказа открывает трассировку чека по всем этапам.</li>
                    </ol>
                </details>
            </section>

            <section className={styles.ledgers}>
                <article className={styles.panel}>
                    <div className={styles.panelHeading}>
                        <div><p>ЖУРНАЛ 01</p><h2>Очередь Outbox</h2></div>
                        <span>{overview.recentOutbox?.length || 0} последних</span>
                    </div>
                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <caption className={styles.srOnly}>Последние записи очереди Outbox</caption>
                            <thead><tr><th>ID</th><th>Заказ</th><th>Дата</th><th>Статус</th><th>Попытки</th><th>Ошибка</th><th>Действие</th></tr></thead>
                            <tbody>
                                {!overview.recentOutbox?.length && <tr><td colSpan={7} className={styles.emptyRow}>Записей пока нет</td></tr>}
                                {overview.recentOutbox?.map((row) => (
                                    <tr key={row.id}>
                                        <td className={styles.dataCell}>{row.id}</td>
                                        <td><button type="button" className={styles.receiptLink} onClick={() => openReceipt(row.orderId)}>#{row.orderId}</button></td>
                                        <td><DateStamp value={row.createdAt} /><small className={styles.dateMeta}>обновлено {formatDateTime(row.updatedAt)}</small></td>
                                        <td><StatusPill status={row.status} /></td>
                                        <td className={styles.dataCell}>{row.attemptCount ?? 0}</td>
                                        <td className={styles.errorCell}>{row.lastError || "—"}</td>
                                        <td><button type="button" className={styles.rowActionBtn} disabled={busy || !dispatchReady} onClick={() => sendOne(row.orderId)}>Отправить</button></td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <div className={styles.mobileLedger}>
                        {!overview.recentOutbox?.length && <p className={styles.emptyText}>Записей пока нет</p>}
                        {overview.recentOutbox?.map((row) => (
                            <article className={styles.mobileRecord} key={row.id}>
                                <header><button type="button" className={styles.receiptLink} onClick={() => openReceipt(row.orderId)}>Заказ #{row.orderId}</button><StatusPill status={row.status} /></header>
                                <div><DateStamp value={row.createdAt} /><span>Попыток: {row.attemptCount ?? 0}</span></div>
                                {row.lastError && <p>{row.lastError}</p>}
                                <button type="button" className={styles.rowActionBtn} disabled={busy || !dispatchReady} onClick={() => sendOne(row.orderId)}>Отправить чек</button>
                            </article>
                        ))}
                    </div>
                </article>

                <article className={styles.panel}>
                    <div className={styles.panelHeading}>
                        <div><p>ЖУРНАЛ 02</p><h2>Задачи налоговой БД</h2></div>
                        <span>{overview.recentJobs?.length || 0} последних</span>
                    </div>
                    <div className={styles.tableWrap}>
                        <table className={styles.table}>
                            <caption className={styles.srOnly}>Последние задачи налоговой базы данных</caption>
                            <thead><tr><th>ID</th><th>Заказ</th><th>Дата</th><th>Сумма</th><th>Оплата</th><th>Статус</th><th>Попытки</th><th>Ошибка</th></tr></thead>
                            <tbody>
                                {!overview.recentJobs?.length && <tr><td colSpan={8} className={styles.emptyRow}>Записей пока нет</td></tr>}
                                {overview.recentJobs?.map((row) => {
                                    const sourceDate = row.businessDate || row.createdAt;
                                    return (
                                        <tr key={row.id}>
                                            <td className={styles.dataCell}>{row.id}</td>
                                            <td><button type="button" className={styles.receiptLink} onClick={() => openReceipt(row.orderId)}>#{row.orderId}</button></td>
                                            <td><DateStamp value={sourceDate} /><small className={styles.dateMeta}>обновлено {formatDateTime(row.updatedAt)}</small></td>
                                            <td className={styles.dataCell}>{money(row.amount)}</td>
                                            <td>{row.paymentType || "—"}</td>
                                            <td><StatusPill status={row.status} /></td>
                                            <td className={styles.dataCell}>{row.attemptCount ?? 0}</td>
                                            <td className={styles.errorCell}>{row.lastErrorMessage || row.lastErrorCode || "—"}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                    <div className={styles.mobileLedger}>
                        {!overview.recentJobs?.length && <p className={styles.emptyText}>Записей пока нет</p>}
                        {overview.recentJobs?.map((row) => (
                            <article className={styles.mobileRecord} key={row.id}>
                                <header><button type="button" className={styles.receiptLink} onClick={() => openReceipt(row.orderId)}>Заказ #{row.orderId}</button><StatusPill status={row.status} /></header>
                                <div><DateStamp value={row.businessDate || row.createdAt} /><strong>{money(row.amount)}</strong></div>
                                <p>{row.paymentType || "Тип оплаты не указан"} · попыток {row.attemptCount ?? 0}</p>
                                {(row.lastErrorMessage || row.lastErrorCode) && <p className={styles.mobileError}>{row.lastErrorMessage || row.lastErrorCode}</p>}
                            </article>
                        ))}
                    </div>
                </article>
            </section>

            {(selectedReceipt || modalLoading) && (
                <ReceiptDialog
                    loading={modalLoading}
                    receipt={selectedReceipt}
                    dispatchReady={dispatchReady}
                    sending={runningAction === "send_one"}
                    onClose={closeReceipt}
                    onSend={sendOne}
                />
            )}
        </div>
    );
}
