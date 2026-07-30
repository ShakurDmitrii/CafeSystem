import { ArrowRightOutlined, ThunderboltOutlined } from '@ant-design/icons';
import React, { useState } from 'react';
import IngredientSelector from './IngredientSelector';
import PredictionResult from './PredictionResult';
import { ApiClient } from './api';
import styles from './mlStyles.module.css';

export default function PredictRollPage({ onOpenOptimizer }) {
    const [ingredients, setIngredients] = useState(['рис', 'лосось']);
    const [prediction, setPrediction] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const handlePredict = async () => {
        if (ingredients.length < 2) {
            setError('Выберите минимум два ингредиента.');
            return;
        }

        setLoading(true);
        setError(null);
        try {
            const result = await ApiClient.predictSales(ingredients);
            if (result?.errorMessage) throw new Error(result.errorMessage);
            setPrediction(result);
        } catch (err) {
            setError(err.message || 'Не удалось получить прогноз.');
            setPrediction(null);
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className={styles.predictSection} aria-labelledby="predict-heading">
            <div className={styles.sectionIntro}>
                <div>
                    <p className={styles.eyebrow}>Сценарий спроса</p>
                    <h2 id="predict-heading">Оцените потенциал нового состава</h2>
                    <p>Выберите ингредиенты — модель рассчитает ожидаемые продажи и экономику блюда.</p>
                </div>
                <span className={styles.stepBadge}>Минимум 2 ингредиента</span>
            </div>

            <div className={styles.predictGrid}>
                <div className={styles.workCard}>
                    <div className={styles.cardHeading}>
                        <span className={styles.cardIndex}>01</span>
                        <div>
                            <h3>Состав блюда</h3>
                            <p>Добавьте базу, начинку и акценты.</p>
                        </div>
                    </div>

                    <IngredientSelector selected={ingredients} onChange={setIngredients} />

                    <div className={styles.actionButtons}>
                        <button
                            type="button"
                            className={`${styles.button} ${styles.buttonPrimary}`}
                            onClick={handlePredict}
                            disabled={loading || ingredients.length < 2}
                        >
                            <ThunderboltOutlined aria-hidden="true" />
                            {loading ? 'Анализируем…' : 'Рассчитать прогноз'}
                        </button>
                        <button
                            type="button"
                            className={`${styles.button} ${styles.buttonSecondary}`}
                            onClick={onOpenOptimizer}
                        >
                            Открыть оптимизатор
                            <ArrowRightOutlined aria-hidden="true" />
                        </button>
                    </div>
                </div>

                <PredictionResult
                    prediction={prediction}
                    loading={loading}
                    error={error}
                />
            </div>
        </section>
    );
}
