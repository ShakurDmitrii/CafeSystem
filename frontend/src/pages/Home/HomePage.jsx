import { hasRole } from "../../auth";
import MetricCard from "./components/MetricCard";
import QuickActions from "./components/QuickActions";
import StockAlertsPanel from "./components/StockAlertsPanel";
import TopDishesPanel from "./components/TopDishesPanel";
import useHomeDashboard from "./hooks/useHomeDashboard";
import styles from "./HomePage.module.css";

const moneyFormatter = new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
});

const dateFormatter = new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric"
});

const decimalFormatter = new Intl.NumberFormat("ru-RU", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
});

function formatMoney(value) {
    return moneyFormatter.format(Number(value) || 0);
}

function formatOrderCount(count) {
    const normalizedCount = Math.abs(Number(count) || 0);
    const lastTwoDigits = normalizedCount % 100;
    const lastDigit = normalizedCount % 10;
    let word = "заказов";

    if (lastTwoDigits < 11 || lastTwoDigits > 14) {
        if (lastDigit === 1) word = "заказ";
        if (lastDigit >= 2 && lastDigit <= 4) word = "заказа";
    }

    return `${normalizedCount} ${word} за день`;
}

function formatDate(value) {
    if (!value) return "Сегодня";
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}

function DashboardState({ type, message, onRetry }) {
    return (
        <div className={`${styles.dashboardState} ${styles[`dashboardState_${type}`]}`} role={type === "error" ? "alert" : "status"}>
            <span className={styles.statePulse} aria-hidden="true" />
            <div>
                <strong>{type === "error" ? "Сводка не загрузилась" : "Собираем данные"}</strong>
                <p>{message}</p>
                {type === "error" && (
                    <button type="button" className={styles.retryButton} onClick={onRetry}>
                        Повторить
                    </button>
                )}
            </div>
        </div>
    );
}

export default function HomePage({ auth }) {
    const { data, loading, error, reload } = useHomeDashboard(auth);
    const isOwner = hasRole(auth, ["OWNER"]);
    const name = auth?.personName || auth?.username || "команда";
    const workersText = data.workers.length > 0
        ? data.workers.join(", ")
        : "Сотрудники не указаны";

    if (loading) {
        return (
            <DashboardState
                type="loading"
                message="Проверяем смены, заказы и остатки…"
            />
        );
    }

    if (error) {
        return <DashboardState type="error" message={error} onRetry={reload} />;
    }

    const metrics = [
        {
            label: "Выручка",
            value: formatMoney(data.revenue),
            note: formatOrderCount(data.ordersCount),
            tone: "positive",
            marker: "₽"
        },
        {
            label: "Средний чек",
            value: formatMoney(data.avgCheck),
            note: "На один оформленный заказ",
            marker: "Ø"
        },
        {
            label: "Доставка",
            value: data.deliveryCount,
            note: `На сумму ${formatMoney(data.deliverySum)}`,
            marker: "Д"
        },
        {
            label: "Ожидают оплаты",
            value: data.unpaidCount,
            note: data.unpaidCount > 0 ? "Нужно проверить заказы" : "Все заказы оплачены",
            tone: data.unpaidCount > 0 ? "warning" : "positive",
            marker: "!"
        },
        {
            label: "Среднее время",
            value: `${decimalFormatter.format(data.avgPrepMinutes)} мин`,
            note: "Приготовление заказа",
            marker: "T"
        },
        {
            label: "С задержкой",
            value: data.delayedOrdersCount,
            note: data.delayedOrdersCount > 0 ? "Есть отклонения по времени" : "Смена идёт по плану",
            tone: data.delayedOrdersCount > 0 ? "warning" : "positive",
            marker: "Δ"
        }
    ];

    return (
        <div className={styles.page}>
            <section className={styles.hero} aria-labelledby="dashboard-title">
                <div className={styles.heroCopy}>
                    <p className={styles.heroKicker}>Рабочая сводка</p>
                    <h1 id="dashboard-title">Добрый день, {name}</h1>
                    <p>
                        Самое важное о текущей смене — без лишних отчётов и переходов.
                    </p>
                </div>

                <div className={styles.shiftPulse}>
                    <div className={styles.pulseHeader}>
                        <span className={styles.liveDot} aria-hidden="true" />
                        <span>Смена в фокусе</span>
                    </div>
                    <time dateTime={data.today}>{formatDate(data.today)}</time>
                    <p title={workersText}>{workersText}</p>
                    <div className={styles.pulseTrack} aria-hidden="true">
                        <span />
                        <span />
                        <span />
                        <span />
                        <span />
                    </div>
                </div>
            </section>

            <QuickActions auth={auth} />

            <section className={styles.metricsSection} aria-labelledby="metrics-title">
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.sectionKicker}>Показатели дня</p>
                        <h2 id="metrics-title">Состояние смены</h2>
                    </div>
                    <span className={styles.updatedLabel}>Данные за {formatDate(data.today)}</span>
                </div>
                <div className={styles.metricsGrid}>
                    {metrics.map((metric) => (
                        <MetricCard key={metric.label} {...metric} />
                    ))}
                </div>
            </section>

            <section className={styles.detailsGrid} aria-label="Продажи и складские остатки">
                <TopDishesPanel dishes={data.topDishes} />
                <StockAlertsPanel stocks={data.criticalStocks} visible={isOwner} />
            </section>
        </div>
    );
}
