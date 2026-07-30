import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { API_BASE_URL } from "../../../auth";
import styles from "./KitchenDisplayPage.module.css";

const API_ORDERS = `${API_BASE_URL}/api/orders`;
const REFRESH_INTERVAL_MS = 5000;
const MIN_CARD_WIDTH = 150;
const CARD_HEIGHT = 118;
const GRID_GAP = 12;

function getPageSize(container) {
    if (!container) return 1;
    const columns = Math.max(1, Math.floor((container.clientWidth + GRID_GAP) / (MIN_CARD_WIDTH + GRID_GAP)));
    const rows = Math.max(1, Math.floor((container.clientHeight + GRID_GAP) / (CARD_HEIGHT + GRID_GAP)));
    return Math.max(1, columns * rows);
}

function getVisibleOrders(orders, pageSize, pageIndex) {
    if (!orders.length || pageSize <= 0) return orders;
    const pages = Math.max(1, Math.ceil(orders.length / pageSize));
    const start = (pageIndex % pages) * pageSize;
    return orders.slice(start, start + pageSize);
}

function KitchenLane({
    title,
    description,
    tone,
    orders,
    pageIndex,
    pageCount,
    pageSize,
    isLoading,
    freshReadyIds,
    bodyRef
}) {
    const visibleOrders = getVisibleOrders(orders, pageSize, pageIndex);
    const emptyText = tone === "ready"
        ? "Готовые заказы появятся здесь"
        : "Сейчас ничего не готовится";

    return (
        <section className={`${styles.lane} ${styles[`${tone}Lane`]}`}>
            <header className={styles.laneHeader}>
                <div>
                    <span className={styles.laneSignal} aria-hidden="true" />
                    <div>
                        <h2>{title}</h2>
                        <p>{description}</p>
                    </div>
                </div>
                <div className={styles.laneCount}>
                    <strong>{orders.length}</strong>
                    {pageCount > 1 && <span>{pageIndex + 1} / {pageCount}</span>}
                </div>
            </header>

            <div ref={bodyRef} className={styles.laneBody} aria-live="polite">
                {isLoading && orders.length === 0 ? (
                    <div className={styles.emptyState}>Загружаем очередь…</div>
                ) : orders.length === 0 ? (
                    <div className={styles.emptyState}>{emptyText}</div>
                ) : visibleOrders.map((order) => (
                    <article
                        key={order.orderId}
                        className={[
                            styles.ticket,
                            styles[`${tone}Ticket`],
                            freshReadyIds.includes(order.orderId) ? styles.freshTicket : ""
                        ].filter(Boolean).join(" ")}
                    >
                        <span>{tone === "ready" ? "Забрать" : "Готовится"}</span>
                        <strong>#{order.orderId}</strong>
                    </article>
                ))}
            </div>
        </section>
    );
}

export default function KitchenDisplayPage() {
    const { shiftId } = useParams();
    const [orders, setOrders] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState("");
    const [clock, setClock] = useState(new Date());
    const [freshReadyIds, setFreshReadyIds] = useState([]);
    const [pageSize, setPageSize] = useState({ cooking: 1, ready: 1 });
    const [pageIndex, setPageIndex] = useState({ cooking: 0, ready: 0 });
    const previousOrdersRef = useRef([]);
    const cookingBodyRef = useRef(null);
    const readyBodyRef = useRef(null);

    const loadData = useCallback(async (silent = false) => {
        if (!shiftId) return;
        if (!silent) setIsLoading(true);

        try {
            const response = await fetch(API_ORDERS);
            if (!response.ok) throw new Error(`Не удалось загрузить заказы (${response.status})`);
            const payload = await response.json();
            const visibleOrders = (Array.isArray(payload) ? payload : [])
                .filter((order) => Number(order.shiftId) === Number(shiftId))
                .filter((order) => !order.date_issue && !order.dateIssue);

            const previousReadyIds = new Set(
                previousOrdersRef.current.filter((order) => order.status).map((order) => order.orderId)
            );
            const justReady = visibleOrders
                .filter((order) => order.status && !previousReadyIds.has(order.orderId))
                .map((order) => order.orderId);

            previousOrdersRef.current = visibleOrders;
            setOrders(visibleOrders);
            setError("");

            if (justReady.length > 0) {
                setFreshReadyIds((current) => [...new Set([...current, ...justReady])]);
                window.setTimeout(() => {
                    setFreshReadyIds((current) => current.filter((id) => !justReady.includes(id)));
                }, 1800);
            }
        } catch (loadError) {
            console.error(loadError);
            setError(loadError.message || "Не удалось загрузить экран кухни");
        } finally {
            if (!silent) setIsLoading(false);
        }
    }, [shiftId]);

    useEffect(() => {
        loadData();
        const refreshInterval = window.setInterval(() => loadData(true), REFRESH_INTERVAL_MS);
        const clockInterval = window.setInterval(() => setClock(new Date()), 1000);
        return () => {
            window.clearInterval(refreshInterval);
            window.clearInterval(clockInterval);
        };
    }, [loadData]);

    useEffect(() => {
        const measure = () => setPageSize({
            cooking: getPageSize(cookingBodyRef.current),
            ready: getPageSize(readyBodyRef.current)
        });
        measure();

        const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(measure) : null;
        if (observer) {
            if (cookingBodyRef.current) observer.observe(cookingBodyRef.current);
            if (readyBodyRef.current) observer.observe(readyBodyRef.current);
        } else {
            window.addEventListener("resize", measure);
        }
        return () => {
            observer?.disconnect();
            if (!observer) window.removeEventListener("resize", measure);
        };
    }, []);

    const cookingOrders = useMemo(
        () => orders
            .filter((order) => !order.status)
            .sort((a, b) => Number(b.orderId || 0) - Number(a.orderId || 0)),
        [orders]
    );
    const readyOrders = useMemo(
        () => orders
            .filter((order) => order.status)
            .sort((a, b) => Number(b.orderId || 0) - Number(a.orderId || 0)),
        [orders]
    );
    const pageCount = {
        cooking: Math.max(1, Math.ceil(cookingOrders.length / pageSize.cooking)),
        ready: Math.max(1, Math.ceil(readyOrders.length / pageSize.ready))
    };

    useEffect(() => {
        const interval = window.setInterval(() => {
            setPageIndex((current) => ({
                cooking: pageCount.cooking > 1 ? (current.cooking + 1) % pageCount.cooking : 0,
                ready: pageCount.ready > 1 ? (current.ready + 1) % pageCount.ready : 0
            }));
        }, REFRESH_INTERVAL_MS);
        return () => window.clearInterval(interval);
    }, [pageCount.cooking, pageCount.ready]);

    return (
        <main className={styles.screen}>
            <header className={styles.topbar}>
                <div className={styles.identity}>
                    <span>Кухонный экран</span>
                    <h1>Смена #{shiftId}</h1>
                </div>
                <div className={styles.flow} aria-label="Путь заказа">
                    <span>Касса</span><i aria-hidden="true" />
                    <strong>Кухня</strong><i aria-hidden="true" />
                    <span>Выдача</span>
                </div>
                <time dateTime={clock.toISOString()}>
                    {new Intl.DateTimeFormat("ru-RU", {
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit"
                    }).format(clock)}
                </time>
            </header>

            {error && <div className={styles.error} role="alert">{error}</div>}

            <div className={styles.board}>
                <KitchenLane
                    title="В работе"
                    description="Заказы, которые готовит кухня"
                    tone="cooking"
                    orders={cookingOrders}
                    pageIndex={pageIndex.cooking}
                    pageCount={pageCount.cooking}
                    pageSize={pageSize.cooking}
                    isLoading={isLoading}
                    freshReadyIds={[]}
                    bodyRef={cookingBodyRef}
                />
                <KitchenLane
                    title="К выдаче"
                    description="Можно забирать на стойке"
                    tone="ready"
                    orders={readyOrders}
                    pageIndex={pageIndex.ready}
                    pageCount={pageCount.ready}
                    pageSize={pageSize.ready}
                    isLoading={isLoading}
                    freshReadyIds={freshReadyIds}
                    bodyRef={readyBodyRef}
                />
            </div>
        </main>
    );
}
