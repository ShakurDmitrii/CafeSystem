import { BarChartOutlined, CheckCircleOutlined } from '@ant-design/icons';
import React from 'react';
import { formatCurrency, formatNumber, formatPercent, formatTime } from './formatters';
import styles from './mlStyles.module.css';

export default function PredictionResult({ prediction, loading, error }) {
    if (loading) {
        return (
            <div className={`${styles.resultCard} ${styles.centeredState}`} role="status">
                <span className={styles.spinner} aria-hidden="true" />
                <h3>Модель считает сценарий</h3>
                <p>Сопоставляем состав с историей продаж и себестоимостью.</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className={`${styles.resultCard} ${styles.errorState}`} role="alert">
                <span className={styles.stateMark}>!</span>
                <h3>Прогноз не готов</h3>
                <p>{error}</p>
            </div>
        );
    }

    if (!prediction) {
        return (
            <div className={`${styles.resultCard} ${styles.emptyState}`}>
                <span className={styles.emptyIcon} aria-hidden="true"><BarChartOutlined /></span>
                <p className={styles.eyebrow}>Результат</p>
                <h3>Здесь появится прогноз</h3>
                <p>После расчёта вы увидите продажи в день, уверенность модели и финансовые показатели.</p>
            </div>
        );
    }

    const confidence = Number(prediction.confidenceScore) || 0;
    const confidenceTone = confidence >= 0.8
        ? styles.confidenceHigh
        : confidence >= 0.6
            ? styles.confidenceMedium
            : styles.confidenceLow;

    return (
        <article className={`${styles.resultCard} ${styles.resultCardReady}`} aria-live="polite">
            <div className={styles.resultTopline}>
                <span className={styles.successMark} aria-hidden="true"><CheckCircleOutlined /></span>
                <span className={`${styles.confidenceBadge} ${confidenceTone}`}>
                    Уверенность {formatPercent(confidence, { fraction: true, digits: 0 })}
                </span>
            </div>

            <p className={styles.eyebrow}>Прогноз спроса</p>
            <div className={styles.salesValue}>
                {formatNumber(prediction.predictedSales)}
                <span>продаж в день</span>
            </div>

            <div className={styles.metricGrid}>
                <div>
                    <span>Себестоимость</span>
                    <strong>{formatCurrency(prediction.estimatedCost)}</strong>
                </div>
                <div>
                    <span>Ожидаемая прибыль</span>
                    <strong>{formatCurrency(prediction.estimatedProfit)}</strong>
                </div>
            </div>

            <div className={styles.metaInfo}>
                <span>Модель {prediction.modelVersion || '1.0'}</span>
                <span>Расчёт в {formatTime(prediction.timestamp)}</span>
            </div>
        </article>
    );
}
