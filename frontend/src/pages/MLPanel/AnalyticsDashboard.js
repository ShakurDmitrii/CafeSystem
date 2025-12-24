import React, { useState, useEffect } from 'react';
import styles from './mlStyles.module.css';

export default function AnalyticsDashboard() {
    const [analyticsData, setAnalyticsData] = useState({
        kpi: { totalProfit: 0, totalSales: 0, profitChange: 0, salesChange: 0, modelAccuracy: 0 },
        topRolls: [],
        salesTrend: [],
        insights: []
    });
    const [timeRange, setTimeRange] = useState('week');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => { fetchAnalyticsData(timeRange); }, [timeRange]);

    const fetchAnalyticsData = async (range) => {
        setLoading(true); setError(null);
        try {
            const response = await fetch(`http://localhost:8000/api/analytics/dashboard?timeRange=${range}`);
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            console.log("Fetched analytics:", data);
            setAnalyticsData({
                kpi: {
                    totalProfit: data.kpi?.total_profit || 0,
                    totalSales: data.kpi?.total_sales || 0,
                    profitChange: data.kpi?.profit_change || 0,
                    salesChange: data.kpi?.sales_change || 0,
                    modelAccuracy: data.kpi?.model_accuracy || 0
                },
                topRolls: data.top_rolls || [],
                salesTrend: data.sales_trend || [],
                insights: data.insights || []
            });
        } catch (err) {
            console.error('Error fetching analytics:', err);
            setError('Не удалось загрузить данные аналитики');
            setAnalyticsData(getMockData(range));
        } finally { setLoading(false); }
    };

    const getMockData = (range) => ({
        kpi: { totalProfit: range==='day'?22000:range==='month'?520000:154320, totalSales: range==='day'?127:range==='month'?3200:892, profitChange:12.5, salesChange:8.2, modelAccuracy:0.87 },
        topRolls: [
            { name:'Калифорния', sales:245, profit:51200, margin:42.3 },
            { name:'Филадельфия', sales:198, profit:42300, margin:38.7 },
            { name:'Дракон', sales:156, profit:37800, margin:45.1 },
            { name:'Аляска', sales:132, profit:28400, margin:36.8 },
            { name:'Унаги', sales:98, profit:21000, margin:40.2 }
        ],
        salesTrend: [
            { date:'Пн', sales:120, predicted:115 },
            { date:'Вт', sales:145, predicted:140 },
            { date:'Ср', sales:132, predicted:130 },
            { date:'Чт', sales:168, predicted:160 },
            { date:'Пт', sales:210, predicted:200 },
            { date:'Сб', sales:198, predicted:190 },
            { date:'Вс', sales:156, predicted:150 }
        ],
        insights: [
            { type:'opportunity', title:'Высокий спрос на авокадо', description:'Блюда с авокадо продаются на 25% лучше среднего' },
            { type:'warning', title:'Низкая маржа на угорь', description:'Стоимость угря выросла на 15%, рассмотрите замену' },
            { type:'insight', title:'Лучшее время для роллов с лососем', description:'Продажи увеличиваются на 30% в обеденное время' }
        ]
    });

    const getMaxValue = (data,key) => (!data||data.length===0)?100:Math.max(...data.map(item=>item[key]));
    const getMarginColor = (margin)=>margin>40?'#4CAF50':margin>30?'#FF9800':'#F44336';
    const formatNumber = (num)=>num.toLocaleString('ru-RU');
    const calculatePopularity = (rollSales,totalSales)=>totalSales===0?0:(rollSales/totalSales)*100;

    if (loading) return <div className={styles.loadingContainer}><div className={styles.spinner}></div><p>Загрузка аналитики...</p></div>;
    if (error) return <div className={styles.errorContainer}><div className={styles.errorIcon}>⚠️</div><h3>Ошибка загрузки</h3><p>{error}</p><button className={styles.retryButton} onClick={()=>fetchAnalyticsData(timeRange)}>Повторить попытку</button></div>;

    return (
        <div className={styles.analyticsContainer}>
            {/* Header и тайм-рейндж селектор */}
            <header className={styles.pageHeader}>
                <h1 className={styles.pageTitle}>📊 AI Аналитическая панель</h1>
                <p className={styles.pageSubtitle}>Анализ продаж, прибыли и эффективности меню</p>
                <div className={styles.timeRangeSelector}>
                    {['day','week','month','quarter'].map(range=>(
                        <button key={range} className={`${styles.timeButton} ${timeRange===range?styles.active:''}`} onClick={()=>setTimeRange(range)} disabled={loading}>
                            {range==='day'?'День':range==='week'?'Неделя':range==='month'?'Месяц':'Квартал'}
                        </button>
                    ))}
                </div>
            </header>

            {/* KPI */}
            <div className={styles.kpiGrid}>
                {[
                    {icon:'💰', title:'Общая прибыль', value:analyticsData.kpi.totalProfit, change:analyticsData.kpi.profitChange, suffix:'₽'},
                    {icon:'📈', title:'Продажи', value:analyticsData.kpi.totalSales, change:analyticsData.kpi.salesChange},
                    {icon:'🍣', title:'Популярные роллы', value:analyticsData.topRolls[0]?.name || '—', subtitle:`${analyticsData.topRolls[0]?.sales||0} продаж`},
                    {icon:'🎯', title:'Точность AI', value:(analyticsData.kpi.modelAccuracy*100).toFixed(1)+'%', subtitle:'На основе исторических данных'}
                ].map((kpi,index)=>(
                    <div key={index} className={styles.kpiCard}>
                        <div className={styles.kpiHeader}><span className={styles.kpiIcon}>{kpi.icon}</span><h3 className={styles.kpiTitle}>{kpi.title}</h3></div>
                        <div className={styles.kpiValue}>{kpi.value}{kpi.suffix||''}</div>
                        {kpi.change!==undefined&&<div className={`${styles.kpiChange} ${kpi.change>=0?styles.positive:styles.negative}`}>{kpi.change>=0?'↑':'↓'}{Math.abs(kpi.change)}%</div>}
                        {kpi.subtitle&&<div className={styles.kpiSubtitle}>{kpi.subtitle}</div>}
                    </div>
                ))}
            </div>

            {/* График продаж */}
            <div className={styles.chartCard}>
                <div className={styles.chartHeader}>
                    <h3 className={styles.chartTitle}>📈 Динамика продаж</h3>
                    <span className={styles.timeRangeBadge}>{timeRange==='day'?'За день':timeRange==='week'?'За неделю':timeRange==='month'?'За месяц':'За квартал'}</span>
                </div>
                <div className={styles.simpleChart}>
                    <div className={styles.chartBars}>
                        {analyticsData.salesTrend.map((day,index)=>{
                            const maxSales=getMaxValue(analyticsData.salesTrend,'sales');
                            const salesHeight=(day.sales/maxSales)*150;
                            const predictedHeight=((day.predicted||0)/maxSales)*150;
                            return (
                                <div key={index} className={styles.chartBarContainer}>
                                    <div className={styles.barGroup}>
                                        <div className={styles.actualBar} style={{height:`${salesHeight}px`}} title={`Факт: ${day.sales}`}><span className={styles.barLabel}>{day.sales}</span></div>
                                        {day.predicted!==undefined&&<div className={styles.predictedBar} style={{height:`${predictedHeight}px`}} title={`Прогноз: ${day.predicted}`}></div>}
                                    </div>
                                    <div className={styles.barLabel}>{day.date||day.period}</div>
                                </div>
                            )
                        })}
                    </div>
                    <div className={styles.chartLegend}>
                        <div className={styles.legendItem}><div className={styles.legendColor} style={{backgroundColor:'#8884d8'}}></div><span>Фактические продажи</span></div>
                        <div className={styles.legendItem}><div className={styles.legendColor} style={{backgroundColor:'#82ca9d'}}></div><span>Прогноз AI</span></div>
                    </div>
                </div>
            </div>

            {/* Таблица топ роллов */}
            <div className={styles.tableSection}>
                <h3 className={styles.sectionTitle}>🏆 Топ роллов по продажам</h3>
                <div className={styles.tableContainer}>
                    <table className={styles.dataTable}>
                        <thead><tr><th>Ролл</th><th>Продажи</th><th>Прибыль (₽)</th><th>Маржа (%)</th><th>Популярность</th><th>Рекомендация</th></tr></thead>
                        <tbody>
                        {analyticsData.topRolls.map((roll,index)=>{
                            const popularityPercent=calculatePopularity(roll.sales,analyticsData.kpi.totalSales);
                            return (
                                <tr key={index}>
                                    <td className={styles.rollName}>{roll.name}</td>
                                    <td>{roll.sales}</td>
                                    <td className={styles.profitCell}>{formatNumber(roll.profit)}₽</td>
                                    <td className={styles.marginCell}><span className={styles.marginBadge} style={{backgroundColor:getMarginColor(roll.margin)+'20',color:getMarginColor(roll.margin)}}>{roll.margin.toFixed(1)}%</span></td>
                                    <td className={styles.popularityCell}><div className={styles.popularityBar}><div className={styles.popularityFill} style={{width:`${Math.min(popularityPercent,100)}%`}}></div></div><span className={styles.popularityText}>{popularityPercent.toFixed(1)}%</span></td>
                                    <td className={styles.recommendationCell}><span className={`${styles.recommendationBadge} ${roll.margin>40?styles.recommendIncrease:roll.margin<30?styles.recommendDecrease:styles.recommendMaintain}`}>{roll.margin>40?'Увеличить':roll.margin<30?'Уменьшить':'Оставить'}</span></td>
                                </tr>
                            )
                        })}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* AI Инсайты */}
            <div className={styles.insightsSection}>
                <div className={styles.sectionHeader}>
                    <h3 className={styles.sectionTitle}>💡 AI Инсайты</h3>
                    <button className={styles.refreshButton} onClick={()=>fetchAnalyticsData(timeRange)} title="Обновить инсайты">🔄</button>
                </div>
                <div className={styles.insightsGrid}>
                    {analyticsData.insights.map((insight,index)=>(
                        <div key={index} className={styles.insightCard}>
                            <span className={styles.insightIcon}>{insight.type==='opportunity'?'🚀':insight.type==='warning'?'⚠️':'💡'}</span>
                            <h4 className={styles.insightTitle}>{insight.title}</h4>
                            <p className={styles.insightText}>{insight.description}</p>
                            {insight.confidence&&<div className={styles.confidenceBadge}>Уверенность: {(insight.confidence*100).toFixed(0)}%</div>}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    )
}
