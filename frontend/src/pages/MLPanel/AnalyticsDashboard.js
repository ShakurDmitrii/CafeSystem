import React, { useState, useEffect } from 'react';
import styles from './mlStyles.module.css';

export default function AnalyticsDashboard() {
    const [analyticsData, setAnalyticsData] = useState({
        // Мок данные, пока нет API
        totalProfit: 154320,
        totalSales: 892,
        profitChange: 12.5,
        salesChange: 8.2,
        modelAccuracy: 0.87,
        topRolls: [
            { name: 'Калифорния', sales: 245, profit: 51200, margin: 42.3 },
            { name: 'Филадельфия', sales: 198, profit: 42300, margin: 38.7 },
            { name: 'Дракон', sales: 156, profit: 37800, margin: 45.1 },
            { name: 'Аляска', sales: 132, profit: 28400, margin: 36.8 },
            { name: 'Унаги', sales: 98, profit: 21000, margin: 40.2 }
        ],
        salesTrend: [
            { date: 'Пн', sales: 120, predicted: 115 },
            { date: 'Вт', sales: 145, predicted: 140 },
            { date: 'Вт', sales: 132, predicted: 130 },
            { date: 'Чт', sales: 168, predicted: 160 },
            { date: 'Пт', sales: 210, predicted: 200 },
            { date: 'Сб', sales: 198, predicted: 190 },
            { date: 'Вс', sales: 156, predicted: 150 }
        ],
        insights: [
            {
                type: 'opportunity',
                title: 'Высокий спрос на авокадо',
                description: 'Блюда с авокадо продаются на 25% лучше среднего'
            },
            {
                type: 'warning',
                title: 'Низкая маржа на угорь',
                description: 'Стоимость угря выросла на 15%, рассмотрите замену'
            },
            {
                type: 'insight',
                title: 'Лучшее время для роллов с лососем',
                description: 'Продажи увеличиваются на 30% в обеденное время'
            }
        ]
    });

    const [timeRange, setTimeRange] = useState('week');

    // Функция для расчета максимального значения в массиве
    const getMaxValue = (data, key) => {
        return Math.max(...data.map(item => item[key]));
    };

    // Получение цвета для маржи
    const getMarginColor = (margin) => {
        if (margin > 40) return '#4CAF50';
        if (margin > 30) return '#FF9800';
        return '#F44336';
    };

    // Форматирование чисел
    const formatNumber = (num) => {
        return num.toLocaleString('ru-RU');
    };

    return (
        <div className={styles.analyticsContainer}>
            <header className={styles.pageHeader}>
                <h1 className={styles.pageTitle}>📊 AI Аналитическая панель</h1>
                <p className={styles.pageSubtitle}>Анализ продаж, прибыли и эффективности меню</p>

                <div className={styles.timeRangeSelector}>
                    <button
                        className={`${styles.timeButton} ${timeRange === 'day' ? styles.active : ''}`}
                        onClick={() => setTimeRange('day')}
                    >
                        День
                    </button>
                    <button
                        className={`${styles.timeButton} ${timeRange === 'week' ? styles.active : ''}`}
                        onClick={() => setTimeRange('week')}
                    >
                        Неделя
                    </button>
                    <button
                        className={`${styles.timeButton} ${timeRange === 'month' ? styles.active : ''}`}
                        onClick={() => setTimeRange('month')}
                    >
                        Месяц
                    </button>
                    <button
                        className={`${styles.timeButton} ${timeRange === 'quarter' ? styles.active : ''}`}
                        onClick={() => setTimeRange('quarter')}
                    >
                        Квартал
                    </button>
                </div>
            </header>

            {/* KPI Карточки */}
            <div className={styles.kpiGrid}>
                <div className={styles.kpiCard}>
                    <div className={styles.kpiHeader}>
                        <span className={styles.kpiIcon}>💰</span>
                        <h3 className={styles.kpiTitle}>Общая прибыль</h3>
                    </div>
                    <div className={styles.kpiValue}>
                        {formatNumber(analyticsData.totalProfit)}₽
                    </div>
                    <div className={`${styles.kpiChange} ${analyticsData.profitChange >= 0 ? styles.positive : styles.negative}`}>
                        {analyticsData.profitChange >= 0 ? '↑' : '↓'}
                        {Math.abs(analyticsData.profitChange)}%
                    </div>
                </div>

                <div className={styles.kpiCard}>
                    <div className={styles.kpiHeader}>
                        <span className={styles.kpiIcon}>📈</span>
                        <h3 className={styles.kpiTitle}>Продажи</h3>
                    </div>
                    <div className={styles.kpiValue}>
                        {formatNumber(analyticsData.totalSales)}
                    </div>
                    <div className={`${styles.kpiChange} ${analyticsData.salesChange >= 0 ? styles.positive : styles.negative}`}>
                        {analyticsData.salesChange >= 0 ? '↑' : '↓'}
                        {Math.abs(analyticsData.salesChange)}%
                    </div>
                </div>

                <div className={styles.kpiCard}>
                    <div className={styles.kpiHeader}>
                        <span className={styles.kpiIcon}>🍣</span>
                        <h3 className={styles.kpiTitle}>Популярные роллы</h3>
                    </div>
                    <div className={styles.kpiValue}>
                        {analyticsData.topRolls[0]?.name || '—'}
                    </div>
                    <div className={styles.kpiSubtitle}>
                        {analyticsData.topRolls[0]?.sales || '0'} продаж
                    </div>
                </div>

                <div className={styles.kpiCard}>
                    <div className={styles.kpiHeader}>
                        <span className={styles.kpiIcon}>🎯</span>
                        <h3 className={styles.kpiTitle}>Точность AI</h3>
                    </div>
                    <div className={styles.kpiValue}>
                        {(analyticsData.modelAccuracy * 100).toFixed(1)}%
                    </div>
                    <div className={styles.kpiSubtitle}>
                        На основе исторических данных
                    </div>
                </div>
            </div>

            {/* График продаж (упрощенный) */}
            <div className={styles.chartCard}>
                <h3 className={styles.chartTitle}>📈 Динамика продаж</h3>
                <div className={styles.simpleChart}>
                    <div className={styles.chartBars}>
                        {analyticsData.salesTrend.map((day, index) => {
                            const maxSales = getMaxValue(analyticsData.salesTrend, 'sales');
                            const salesHeight = (day.sales / maxSales) * 150;
                            const predictedHeight = (day.predicted / maxSales) * 150;

                            return (
                                <div key={index} className={styles.chartBarContainer}>
                                    <div className={styles.barGroup}>
                                        <div
                                            className={styles.actualBar}
                                            style={{ height: `${salesHeight}px` }}
                                            title={`Факт: ${day.sales}`}
                                        >
                                            <span className={styles.barLabel}>{day.sales}</span>
                                        </div>
                                        <div
                                            className={styles.predictedBar}
                                            style={{ height: `${predictedHeight}px` }}
                                            title={`Прогноз: ${day.predicted}`}
                                        ></div>
                                    </div>
                                    <div className={styles.barLabel}>{day.date}</div>
                                </div>
                            );
                        })}
                    </div>
                    <div className={styles.chartLegend}>
                        <div className={styles.legendItem}>
                            <div className={styles.legendColor} style={{ backgroundColor: '#8884d8' }}></div>
                            <span>Фактические продажи</span>
                        </div>
                        <div className={styles.legendItem}>
                            <div className={styles.legendColor} style={{ backgroundColor: '#82ca9d' }}></div>
                            <span>Прогноз AI</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Таблица топ роллов */}
            <div className={styles.tableSection}>
                <h3 className={styles.sectionTitle}>🏆 Топ роллов по продажам</h3>
                <div className={styles.tableContainer}>
                    <table className={styles.dataTable}>
                        <thead>
                        <tr>
                            <th>Ролл</th>
                            <th>Продажи</th>
                            <th>Прибыль (₽)</th>
                            <th>Маржа (%)</th>
                            <th>Популярность</th>
                            <th>Рекомендация</th>
                        </tr>
                        </thead>
                        <tbody>
                        {analyticsData.topRolls.map((roll, index) => {
                            const popularityPercent = (roll.sales / analyticsData.totalSales) * 100;
                            return (
                                <tr key={index}>
                                    <td className={styles.rollName}>{roll.name}</td>
                                    <td>{roll.sales}</td>
                                    <td className={styles.profitCell}>
                                        {formatNumber(roll.profit)}₽
                                    </td>
                                    <td className={styles.marginCell}>
                                            <span
                                                className={styles.marginBadge}
                                                style={{
                                                    backgroundColor: getMarginColor(roll.margin) + '20',
                                                    color: getMarginColor(roll.margin)
                                                }}
                                            >
                                                {roll.margin.toFixed(1)}%
                                            </span>
                                    </td>
                                    <td className={styles.popularityCell}>
                                        <div className={styles.popularityBar}>
                                            <div
                                                className={styles.popularityFill}
                                                style={{ width: `${popularityPercent}%` }}
                                            ></div>
                                        </div>
                                        <span className={styles.popularityText}>
                                                {popularityPercent.toFixed(1)}%
                                            </span>
                                    </td>
                                    <td className={styles.recommendationCell}>
                                            <span className={`${styles.recommendationBadge} ${
                                                roll.margin > 40 ? styles.recommendIncrease :
                                                    roll.margin < 30 ? styles.recommendDecrease :
                                                        styles.recommendMaintain
                                            }`}>
                                                {roll.margin > 40 ? 'Увеличить' :
                                                    roll.margin < 30 ? 'Уменьшить' : 'Оставить'}
                                            </span>
                                    </td>
                                </tr>
                            );
                        })}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* AI Инсайты */}
            <div className={styles.insightsSection}>
                <h3 className={styles.sectionTitle}>💡 AI Инсайты</h3>
                <div className={styles.insightsGrid}>
                    {analyticsData.insights.map((insight, index) => (
                        <div key={index} className={styles.insightCard}>
                            <span className={styles.insightIcon}>
                                {insight.type === 'opportunity' ? '🚀' :
                                    insight.type === 'warning' ? '⚠️' : '💡'}
                            </span>
                            <h4 className={styles.insightTitle}>{insight.title}</h4>
                            <p className={styles.insightText}>{insight.description}</p>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}