import {
    BulbOutlined,
    ReloadOutlined,
    RiseOutlined,
    TrophyOutlined
} from '@ant-design/icons';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiClient } from './api';
import { formatCurrency, formatInteger, formatPercent } from './formatters';
import styles from './mlStyles.module.css';

const RANGE_OPTIONS = [
    { value: 'day', label: 'День' },
    { value: 'week', label: 'Неделя' },
    { value: 'month', label: 'Месяц' },
    { value: 'quarter', label: 'Квартал' }
];

const EMPTY_ANALYTICS = {
    kpi: {
        totalProfit: 0,
        totalSales: 0,
        profitChange: 0,
        salesChange: 0,
        modelAccuracy: 0
    },
    topRolls: [],
    salesTrend: [],
    insights: []
};

function normalizeAnalytics(data = {}) {
    const kpi = data.kpi || data;
    return {
        kpi: {
            totalProfit: kpi.totalProfit ?? kpi.total_profit ?? 0,
            totalSales: kpi.totalSales ?? kpi.total_sales ?? 0,
            profitChange: kpi.profitChange ?? kpi.profit_change ?? 0,
            salesChange: kpi.salesChange ?? kpi.sales_change ?? 0,
            modelAccuracy: kpi.modelAccuracy ?? kpi.model_accuracy ?? null
        },
        topRolls: data.topRolls || data.top_rolls || [],
        salesTrend: data.salesTrend || data.sales_trend || [],
        insights: data.insights || [],
        isFallback: Boolean(data.isFallback || data.is_fallback)
    };
}

export default function AnalyticsDashboard() {
    const [searchParams, setSearchParams] = useSearchParams();
    const rangeParam = searchParams.get('period');
    const timeRange = RANGE_OPTIONS.some((item) => item.value === rangeParam) ? rangeParam : 'week';
    const [analyticsData, setAnalyticsData] = useState(EMPTY_ANALYTICS);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchAnalyticsData = useCallback(async (range, refresh = false) => {
        setLoading(true);
        setError(null);
        try {
            const data = await ApiClient.getAnalytics(range, refresh);
            setAnalyticsData(normalizeAnalytics(data));
        } catch (err) {
            setError(err.message || 'Не удалось загрузить аналитику.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchAnalyticsData(timeRange);
    }, [fetchAnalyticsData, timeRange]);

    const setTimeRange = (range) => {
        const nextParams = new URLSearchParams(searchParams);
        nextParams.set('period', range);
        setSearchParams(nextParams, { replace: true });
    };

    const maxTrendValue = useMemo(() => Math.max(
        1,
        ...analyticsData.salesTrend.flatMap((point) => [
            Number(point.sales) || 0,
            point.predicted == null ? 0 : Number(point.predicted) || 0
        ])
    ), [analyticsData.salesTrend]);
    const hasPredictions = analyticsData.salesTrend.some((point) => point.predicted != null);

    const kpiCards = [
        {
            label: 'Прибыль',
            value: formatCurrency(analyticsData.kpi.totalProfit),
            change: analyticsData.kpi.profitChange,
            note: 'к прошлому периоду'
        },
        {
            label: 'Продажи',
            value: formatInteger(analyticsData.kpi.totalSales),
            change: analyticsData.kpi.salesChange,
            note: 'позиций за период'
        },
        {
            label: 'Лидер меню',
            value: analyticsData.topRolls[0]?.name || '—',
            note: `${formatInteger(analyticsData.topRolls[0]?.sales, '0')} продаж`
        },
        {
            label: 'Точность модели',
            value: analyticsData.kpi.modelAccuracy == null
                ? '—'
                : formatPercent(analyticsData.kpi.modelAccuracy, { fraction: true, digits: 1 }),
            note: analyticsData.kpi.modelAccuracy == null
                ? 'модель ещё не обучена'
                : 'по отложенной выборке'
        }
    ];

    return (
        <section className={styles.analyticsContainer} aria-labelledby="analytics-heading">
            <div className={styles.sectionIntro}>
                <div>
                    <p className={styles.eyebrow}>Контроль качества</p>
                    <h2 id="analytics-heading">Продажи и точность прогноза</h2>
                    <p>Сравнивайте факт с прогнозом и находите точки роста в меню.</p>
                </div>
                <button
                    type="button"
                    className={styles.iconTextButton}
                    onClick={() => fetchAnalyticsData(timeRange, true)}
                    disabled={loading}
                >
                    <ReloadOutlined aria-hidden="true" />
                    Обновить
                </button>
            </div>

            <div className={styles.timeRangeSelector} role="group" aria-label="Период аналитики">
                {RANGE_OPTIONS.map((range) => (
                    <button
                        key={range.value}
                        type="button"
                        className={`${styles.timeButton} ${timeRange === range.value ? styles.active : ''}`}
                        aria-pressed={timeRange === range.value}
                        onClick={() => setTimeRange(range.value)}
                        disabled={loading}
                    >
                        {range.label}
                    </button>
                ))}
            </div>

            {error && <div className={styles.inlineError} role="alert">{error}</div>}
            {analyticsData.isFallback && (
                <div className={styles.demoNotice} role="status">
                    Сервис аналитики временно недоступен — данные не показаны.
                </div>
            )}

            <div className={styles.kpiGrid} aria-busy={loading}>
                {kpiCards.map((card) => (
                    <article key={card.label} className={styles.kpiCard}>
                        <span>{card.label}</span>
                        <strong title={String(card.value)}>{card.value}</strong>
                        <div>
                            {card.change !== undefined && (
                                <b className={Number(card.change) >= 0 ? styles.positive : styles.negative}>
                                    {Number(card.change) >= 0 ? '↑' : '↓'} {formatPercent(Math.abs(Number(card.change)))}
                                </b>
                            )}
                            <small>{card.note}</small>
                        </div>
                    </article>
                ))}
            </div>

            <div className={styles.analyticsGrid}>
                <article className={styles.chartCard}>
                    <div className={styles.chartHeader}>
                        <div>
                            <p className={styles.eyebrow}>Спрос</p>
                            <h3>Факт и прогноз</h3>
                        </div>
                        <span className={styles.legend}>
                            <i className={styles.factDot} /> Факт
                            {hasPredictions && (
                                <>
                                    <i className={styles.forecastDot} /> AI
                                </>
                            )}
                        </span>
                    </div>

                    {loading && analyticsData.salesTrend.length === 0 ? (
                        <div className={styles.chartLoading} role="status">Загружаем график…</div>
                    ) : (
                        <div className={styles.barChart} aria-label="График фактических и прогнозных продаж">
                            {analyticsData.salesTrend.map((point, index) => {
                                const sales = Number(point.sales) || 0;
                                const hasPrediction = point.predicted != null;
                                const predicted = hasPrediction ? Number(point.predicted) || 0 : null;
                                return (
                                    <div className={styles.barColumn} key={`${point.date || point.period}-${index}`}>
                                        <div className={styles.barValues}>
                                            <span
                                                className={styles.factBar}
                                                style={{ height: `${Math.max((sales / maxTrendValue) * 100, 3)}%` }}
                                                title={`Факт: ${formatInteger(sales)}`}
                                            />
                                            {hasPrediction && (
                                                <span
                                                    className={styles.forecastBar}
                                                    style={{ height: `${Math.max((predicted / maxTrendValue) * 100, 3)}%` }}
                                                    title={`Прогноз: ${formatInteger(predicted)}`}
                                                />
                                            )}
                                        </div>
                                        <small>{point.date || point.period}</small>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </article>

                <article className={styles.topRollsCard}>
                    <div className={styles.chartHeader}>
                        <div>
                            <p className={styles.eyebrow}>Рейтинг</p>
                            <h3>Лидеры продаж</h3>
                        </div>
                        <TrophyOutlined aria-hidden="true" />
                    </div>
                    <ol className={styles.topRollsList}>
                        {analyticsData.topRolls.slice(0, 5).map((roll, index) => (
                            <li key={`${roll.name}-${index}`}>
                                <span className={styles.rollRank}>{index + 1}</span>
                                <span className={styles.rollSummary}>
                                    <strong>{roll.name}</strong>
                                    <small>{formatInteger(roll.sales)} продаж</small>
                                </span>
                                <span className={styles.rollFinance}>
                                    <strong>{formatCurrency(roll.profit)}</strong>
                                    <small>{formatPercent(roll.margin, { digits: 1 })} маржа</small>
                                </span>
                            </li>
                        ))}
                    </ol>
                </article>
            </div>

            <section className={styles.insightsSection} aria-labelledby="insights-heading">
                <div className={styles.insightsHeading}>
                    <span aria-hidden="true"><BulbOutlined /></span>
                    <div>
                        <p className={styles.eyebrow}>Подсказки модели</p>
                        <h3 id="insights-heading">На что обратить внимание</h3>
                    </div>
                </div>
                <div className={styles.insightsGrid}>
                    {analyticsData.insights.map((insight, index) => (
                        <article key={`${insight.title}-${index}`} className={styles.insightCard}>
                            <span className={`${styles.insightType} ${styles[insight.type] || ''}`}>
                                {insight.type === 'warning' ? 'Риск' : insight.type === 'opportunity' ? 'Возможность' : 'Наблюдение'}
                            </span>
                            <h4>{insight.title}</h4>
                            <p>{insight.description}</p>
                            {insight.confidence != null && (
                                <small><RiseOutlined /> Уверенность {formatPercent(insight.confidence, { fraction: true })}</small>
                            )}
                        </article>
                    ))}
                </div>
            </section>
        </section>
    );
}
