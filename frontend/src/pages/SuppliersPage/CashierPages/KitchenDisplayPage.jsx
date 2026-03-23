import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { API_BASE_URL } from "../../../auth";
import styles from "./KitchenDisplayPage.module.css";

const API_ORDERS = `${API_BASE_URL}/api/orders`;
const ROTATION_INTERVAL_MS = 5000;
const PAGE_EXIT_MS = 760;
const PAGE_SETTLE_MS = 1720;
const MIN_CARD_WIDTH = 128;
const CARD_HEIGHT = 104;
const GRID_GAP = 12;

function getPageSize(container) {
    if (!container) {
        return 1;
    }

    const width = container.clientWidth || 0;
    const height = container.clientHeight || 0;
    const columns = Math.max(1, Math.floor((width + GRID_GAP) / (MIN_CARD_WIDTH + GRID_GAP)));
    const rows = Math.max(1, Math.floor((height + GRID_GAP) / (CARD_HEIGHT + GRID_GAP)));

    return Math.max(1, columns * rows);
}

function getVisibleOrders(orders, pageSize, pageIndex) {
    if (!orders.length || pageSize <= 0) {
        return orders;
    }

    const pageCount = Math.max(1, Math.ceil(orders.length / pageSize));
    const safeIndex = pageIndex % pageCount;
    const start = safeIndex * pageSize;
    return orders.slice(start, start + pageSize);
}

export default function KitchenDisplayPage() {
    const { shiftId } = useParams();
    const [orders, setOrders] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState("");
    const [freshReadyIds, setFreshReadyIds] = useState([]);
    const [pageSize, setPageSize] = useState({ cooking: 1, ready: 1 });
    const [pageIndex, setPageIndex] = useState({ cooking: 0, ready: 0 });
    const [pagePhase, setPagePhase] = useState({ cooking: "idle", ready: "idle" });
    const previousOrdersRef = useRef([]);
    const rotationTimeoutsRef = useRef([]);
    const cookingBodyRef = useRef(null);
    const readyBodyRef = useRef(null);

    const loadData = useCallback(async (silent = false) => {
        if (!shiftId) return;
        if (!silent) setIsLoading(true);

        try {
            setError("");
            const ordersResponse = await fetch(API_ORDERS);

            if (!ordersResponse.ok) {
                throw new Error(`Не удалось загрузить заказы (${ordersResponse.status})`);
            }
            const ordersText = await ordersResponse.text();

            const rawOrders = ordersText ? JSON.parse(ordersText) : [];
            const visibleOrders = (Array.isArray(rawOrders) ? rawOrders : [])
                .filter((order) => Number(order?.shiftId) === Number(shiftId))
                .filter((order) => !order?.date_issue && !order?.dateIssue);

            const detailedOrders = visibleOrders;

            const previousReadyIds = new Set(
                previousOrdersRef.current
                    .filter((order) => order?.status)
                    .map((order) => order.orderId)
            );
            const justReady = detailedOrders
                .filter((order) => order?.status && !previousReadyIds.has(order.orderId))
                .map((order) => order.orderId);

            previousOrdersRef.current = detailedOrders;
            setOrders(detailedOrders);

            if (justReady.length > 0) {
                setFreshReadyIds((prev) => Array.from(new Set([...prev, ...justReady])));
                window.setTimeout(() => {
                    setFreshReadyIds((prev) => prev.filter((id) => !justReady.includes(id)));
                }, 1400);
            }
        } catch (loadError) {
            console.error(loadError);
            setError(loadError.message || "Не удалось загрузить кухонный экран");
        } finally {
            if (!silent) setIsLoading(false);
        }
    }, [shiftId]);

    const measurePageSizes = useCallback(() => {
        setPageSize({
            cooking: getPageSize(cookingBodyRef.current),
            ready: getPageSize(readyBodyRef.current)
        });
    }, []);

    useEffect(() => {
        loadData();
        const interval = window.setInterval(() => loadData(true), ROTATION_INTERVAL_MS);
        return () => window.clearInterval(interval);
    }, [loadData]);

    useEffect(() => {
        measurePageSizes();

        const resizeObserver = typeof ResizeObserver !== "undefined"
            ? new ResizeObserver(() => measurePageSizes())
            : null;

        if (resizeObserver) {
            if (cookingBodyRef.current) {
                resizeObserver.observe(cookingBodyRef.current);
            }
            if (readyBodyRef.current) {
                resizeObserver.observe(readyBodyRef.current);
            }
        } else {
            window.addEventListener("resize", measurePageSizes);
        }

        return () => {
            if (resizeObserver) {
                resizeObserver.disconnect();
            } else {
                window.removeEventListener("resize", measurePageSizes);
            }
        };
    }, [measurePageSizes]);

    useEffect(() => {
        const previousBodyOverflow = document.body.style.overflow;
        const previousHtmlOverflow = document.documentElement.style.overflow;

        document.body.style.overflow = "hidden";
        document.documentElement.style.overflow = "hidden";

        return () => {
            document.body.style.overflow = previousBodyOverflow;
            document.documentElement.style.overflow = previousHtmlOverflow;
        };
    }, []);

    const cookingOrders = useMemo(
        () => orders
            .filter((order) => !order?.status)
            .sort((a, b) => Number(b.orderId || 0) - Number(a.orderId || 0)),
        [orders]
    );

    const readyOrders = useMemo(
        () => orders
            .filter((order) => order?.status)
            .sort((a, b) => Number(b.orderId || 0) - Number(a.orderId || 0)),
        [orders]
    );

    const cookingPageCount = Math.max(1, Math.ceil(cookingOrders.length / pageSize.cooking));
    const readyPageCount = Math.max(1, Math.ceil(readyOrders.length / pageSize.ready));

    useEffect(() => {
        setPageIndex((prev) => ({
            cooking: prev.cooking >= cookingPageCount ? 0 : prev.cooking,
            ready: prev.ready >= readyPageCount ? 0 : prev.ready
        }));
    }, [cookingPageCount, readyPageCount]);

    useEffect(() => {
        const interval = window.setInterval(() => {
            const shouldRotateCooking = cookingPageCount > 1;
            const shouldRotateReady = readyPageCount > 1;

            if (!shouldRotateCooking && !shouldRotateReady) {
                return;
            }

            setPagePhase({
                cooking: shouldRotateCooking ? "leaving" : "idle",
                ready: shouldRotateReady ? "leaving" : "idle"
            });

            rotationTimeoutsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
            rotationTimeoutsRef.current = [];

            const switchTimeout = window.setTimeout(() => {
                setPageIndex((prev) => ({
                    cooking: shouldRotateCooking ? (prev.cooking + 1) % cookingPageCount : 0,
                    ready: shouldRotateReady ? (prev.ready + 1) % readyPageCount : 0
                }));
                setPagePhase({
                    cooking: shouldRotateCooking ? "entering" : "idle",
                    ready: shouldRotateReady ? "entering" : "idle"
                });
            }, PAGE_EXIT_MS);

            const settleTimeout = window.setTimeout(() => {
                setPagePhase({ cooking: "idle", ready: "idle" });
            }, PAGE_SETTLE_MS);

            rotationTimeoutsRef.current = [switchTimeout, settleTimeout];
        }, ROTATION_INTERVAL_MS);

        return () => {
            window.clearInterval(interval);
            rotationTimeoutsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
            rotationTimeoutsRef.current = [];
        };
    }, [cookingPageCount, readyPageCount]);

    const visibleCookingOrders = useMemo(
        () => getVisibleOrders(cookingOrders, pageSize.cooking, pageIndex.cooking),
        [cookingOrders, pageIndex.cooking, pageSize.cooking]
    );

    const visibleReadyOrders = useMemo(
        () => getVisibleOrders(readyOrders, pageSize.ready, pageIndex.ready),
        [readyOrders, pageIndex.ready, pageSize.ready]
    );

    return (
        <div className={styles.screen}>
            <div className={styles.backgroundGlow}></div>
            {error && <div className={styles.error}>{error}</div>}

            <div className={styles.board}>
                <section className={styles.column}>
                    <div className={styles.columnHeader}>
                        <div className={styles.columnTitleBlock}>
                            <span className={styles.columnBadge}>В процессе</span>
                            {cookingPageCount > 1 && (
                                <span className={styles.pageBadge}>
                                    {pageIndex.cooking + 1}/{cookingPageCount}
                                </span>
                            )}
                        </div>
                    </div>

                    <div ref={cookingBodyRef} className={styles.columnBody}>
                        {isLoading && cookingOrders.length === 0 ? (
                            <div className={styles.emptyState}>Загрузка заказов...</div>
                        ) : cookingOrders.length === 0 ? (
                            <div className={styles.emptyState}>Сейчас ничего не готовится</div>
                        ) : (
                            visibleCookingOrders.map((order, index) => {
                                return (
                                    <article
                                        key={order.orderId}
                                        className={`${styles.orderCard} ${styles.cookingCard} ${styles.numberOnlyCard} ${pagePhase.cooking === "leaving" ? styles.cardPageLeaving : ""} ${pagePhase.cooking === "entering" ? styles.cardPageEntering : ""}`}
                                        style={{ "--card-index": index }}
                                    >
                                        <div className={styles.numberOnlyInner}>
                                            <div className={styles.orderNumber}>#{order.orderId}</div>
                                        </div>
                                    </article>
                                );
                            })
                        )}
                    </div>
                </section>

                <section className={styles.column}>
                    <div className={styles.columnHeader}>
                        <div className={styles.columnTitleBlock}>
                            <span className={styles.columnBadgeReady}>Готово</span>
                            {readyPageCount > 1 && (
                                <span className={styles.pageBadge}>
                                    {pageIndex.ready + 1}/{readyPageCount}
                                </span>
                            )}
                        </div>
                    </div>

                    <div ref={readyBodyRef} className={styles.columnBody}>
                        {isLoading && readyOrders.length === 0 ? (
                            <div className={styles.emptyState}>Загрузка заказов...</div>
                        ) : readyOrders.length === 0 ? (
                            <div className={styles.emptyState}>Готовые заказы появятся здесь</div>
                        ) : (
                            visibleReadyOrders.map((order, index) => {
                                const isFresh = freshReadyIds.includes(order.orderId) && pagePhase.ready === "idle";
                                return (
                                    <article
                                        key={order.orderId}
                                        className={`${styles.orderCard} ${styles.readyCard} ${styles.numberOnlyCard} ${isFresh ? styles.cardFreshReady : ""} ${pagePhase.ready === "leaving" ? styles.cardPageLeaving : ""} ${pagePhase.ready === "entering" ? styles.cardPageEntering : ""}`}
                                        style={{ "--card-index": index }}
                                    >
                                        <div className={styles.numberOnlyInner}>
                                            <div className={styles.orderNumber}>#{order.orderId}</div>
                                        </div>
                                    </article>
                                );
                            })
                        )}
                    </div>
                </section>
            </div>
        </div>
    );
}
