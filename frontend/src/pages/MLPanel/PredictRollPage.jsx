import React, { useState } from 'react';
import IngredientSelector from './IngredientSelector';
import PredictionResult from './PredictionResult';
import { ApiClient } from './api';
import styles from './mlStyles.module.css';

export default function PredictRollPage() {
    const [ingredients, setIngredients] = useState(["рис", "лосось"]);
    const [prediction, setPrediction] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const handlePredict = async () => {
        if (ingredients.length < 2) {
            setError('Выберите минимум 2 ингредиента');
            return;
        }

        setLoading(true);
        setError(null);

        try {
            const result = await ApiClient.predictSales(ingredients);
            setPrediction(result);
        } catch (err) {
            setError(err.message || 'Ошибка предсказания');
            setPrediction(null);
        } finally {
            setLoading(false);
        }
    };

    const handleOptimize = async () => {
        setLoading(true);
        try {
            const constraints = {
                minIngredients: 3,
                maxIngredients: 6,
                maxCost: 350,
                mustInclude: ingredients,
                numResults: 3
            };
            const result = await ApiClient.optimizeRoll(constraints);
            console.log('Optimization result:', result);
            alert(`Сгенерировано ${result.results?.length || 0} вариантов`);
        } catch (err) {
            setError(`Ошибка оптимизации: ${err.message}`);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.container}>
            <header className={styles.pageHeader}>
                <h1 className={styles.pageTitle}>🔮 AI Шеф-консультант</h1>
                <p className={styles.pageSubtitle}>Предсказание продаж и оптимизация состава роллов</p>
            </header>

            <main className={styles.predictPage}>
                <div className={styles.predictMain}>
                    <div className={styles.leftPanel}>
                        <IngredientSelector
                            selected={ingredients}
                            onChange={setIngredients}
                        />

                        <div className={styles.actionButtons}>
                            <button
                                className={`${styles.button} ${styles.buttonPrimary}`}
                                onClick={handlePredict}
                                disabled={loading || ingredients.length < 2}
                            >
                                {loading ? 'Анализ...' : '🔮 Предсказать продажи'}
                            </button>

                            <button
                                className={`${styles.button} ${styles.buttonSecondary}`}
                                onClick={handleOptimize}
                                disabled={loading || ingredients.length === 0}
                            >
                                ⚙️ Оптимизировать состав
                            </button>
                        </div>
                    </div>

                    <div className={styles.rightPanel}>
                        <PredictionResult
                            prediction={prediction}
                            loading={loading}
                            error={error}
                        />
                    </div>
                </div>
            </main>
        </div>
    );
}