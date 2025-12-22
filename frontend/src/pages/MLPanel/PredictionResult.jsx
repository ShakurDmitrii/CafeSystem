import React from 'react';
import styles from './mlStyles.module.css';


export default function PredictionResult({ prediction, loading, error }) {
    if (loading) {
        return (
            <div className={styles.loading}>
                <div className={styles.spinner}></div>
                <p>ИИ анализирует состав ролла...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div className={styles.error}>
                <h3>❌ Ошибка предсказания</h3>
                <p>{error}</p>
            </div>
        );
    }

    if (!prediction) {
        return (
            <div className={styles.empty}>
                <h3>🔮 Предсказание продаж</h3>
                <p>Выберите ингредиенты и нажмите "Предсказать"</p>
            </div>
        );
    }

    const confidenceClass = prediction.confidenceScore > 0.8
        ? styles.confidenceHigh
        : prediction.confidenceScore > 0.6
            ? styles.confidenceMedium
            : styles.confidenceLow;

    return (
        <div className={styles.predictionCard}>
            <div className={styles.predictionHeader}>
                <h3 className={styles.predictionTitle}>📊 Результат предсказания</h3>
                <span className={`${styles.confidenceBadge} ${confidenceClass}`}>
                    {(prediction.confidenceScore * 100).toFixed(1)}%
                </span>
            </div>

            <div className={styles.salesValue}>
                {prediction.predictedSales?.toFixed(1) || '0'} в день
            </div>

            {prediction.estimatedCost && (
                <div className={styles.financialGrid}>
                    <div className={styles.financialItem}>
                        <span className={styles.financialLabel}>Себестоимость:</span>
                        <span className={styles.financialValue}>{prediction.estimatedCost.toFixed(2)}₽</span>
                    </div>
                    <div className={styles.financialItem}>
                        <span className={styles.financialLabel}>Прибыль:</span>
                        <span className={styles.financialValue}>{prediction.estimatedProfit?.toFixed(2) || '—'}₽</span>
                    </div>
                </div>
            )}

            <div className={styles.metaInfo}>
                <small>Модель: {prediction.modelVersion || '1.0'}</small>
                <small>Время: {new Date().toLocaleTimeString()}</small>
            </div>
        </div>
    );
}