import React, { useState, useEffect } from 'react';
import { ApiClient } from './api';
import styles from './mlStyles.module.css';

export default function OptimizationResults() {
    const [optimizationResults, setOptimizationResults] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [popularIngredients, setPopularIngredients] = useState([]);
    const OPT_CACHE_KEY = 'ml.optimization.cache.v1';
    const [constraints, setConstraints] = useState({
        minIngredients: 3,
        maxIngredients: 6,
        maxCost: 350,
        mustInclude: ['рис', 'лосось'],
        exclude: [],
        minProfitMargin: 0.3,
        numResults: 3
    });

    useEffect(() => {
        loadPopularIngredients();
        loadDefaultOptimization();
    }, []);

    const loadPopularIngredients = async () => {
        try {
            const ingredients = await ApiClient.getPopularIngredients(30);
            setPopularIngredients(ingredients);
        } catch (err) {
            console.error('Failed to load popular ingredients:', err);
        }
    };

    const loadDefaultOptimization = async () => {
        // Try cache first to avoid regenerating on navigation.
        try {
            const raw = localStorage.getItem(OPT_CACHE_KEY);
            if (raw) {
                const cached = JSON.parse(raw);
                if (cached?.results) {
                    setOptimizationResults(cached.results);
                    if (cached?.constraints) setConstraints(prev => ({ ...prev, ...cached.constraints }));
                    setError(null);
                    return;
                }
            }
        } catch (e) {
            // ignore cache errors
        }

        setLoading(true);
        try {
            const results = await ApiClient.optimizeRoll(constraints);
            setOptimizationResults(results);
            setError(null);
            try {
                localStorage.setItem(OPT_CACHE_KEY, JSON.stringify({
                    constraints,
                    results,
                    savedAt: new Date().toISOString()
                }));
            } catch (e) {
                // ignore storage errors
            }
        } catch (err) {
            setError(`Ошибка загрузки: ${err.message}`);
        } finally {
            setLoading(false);
        }
    };

    const handleOptimize = async () => {
        setLoading(true);
        setError(null);
        try {
            const results = await ApiClient.optimizeRoll(constraints);
            setOptimizationResults(results);
            try {
                localStorage.setItem(OPT_CACHE_KEY, JSON.stringify({
                    constraints,
                    results,
                    savedAt: new Date().toISOString()
                }));
            } catch (e) {
                // ignore storage errors
            }
        } catch (err) {
            setError(`Ошибка оптимизации: ${err.message}`);
        } finally {
            setLoading(false);
        }
    };

    const updateConstraint = (field, value) => {
        setConstraints(prev => ({
            ...prev,
            [field]: value
        }));
    };

    const addMustInclude = (ingredient) => {
        if (!constraints.mustInclude.includes(ingredient)) {
            setConstraints(prev => ({
                ...prev,
                mustInclude: [...prev.mustInclude, ingredient]
            }));
        }
    };

    const removeMustInclude = (ingredient) => {
        setConstraints(prev => ({
            ...prev,
            mustInclude: prev.mustInclude.filter(item => item !== ingredient)
        }));
    };

    if (loading) {
        return (
            <div className={styles.optimizationContainer}>
                <div className={styles.loading}>
                    <div className={styles.spinner}></div>
                    <p>AI генерирует оптимальные рецепты...</p>
                </div>
            </div>
        );
    }

    return (
        <div className={styles.optimizationContainer}>
            <header className={styles.pageHeader}>
                <h1 className={styles.pageTitle}>⚙️ AI Оптимизатор роллов</h1>
                <p className={styles.pageSubtitle}>Нахождение оптимального состава для максимальной прибыли</p>
            </header>

            <div className={styles.optimizationContent}>
                {/* Панель настроек */}
                <div className={styles.constraintsPanel}>
                    <h3 className={styles.sectionTitle}>Параметры оптимизации</h3>

                    <div className={styles.constraintsGrid}>
                        <div className={styles.constraintItem}>
                            <label>Минимум ингредиентов:</label>
                            <input
                                type="number"
                                min="2"
                                max="10"
                                value={constraints.minIngredients}
                                onChange={(e) => updateConstraint('minIngredients', parseInt(e.target.value))}
                            />
                        </div>

                        <div className={styles.constraintItem}>
                            <label>Максимум ингредиентов:</label>
                            <input
                                type="number"
                                min="3"
                                max="12"
                                value={constraints.maxIngredients}
                                onChange={(e) => updateConstraint('maxIngredients', parseInt(e.target.value))}
                            />
                        </div>

                        <div className={styles.constraintItem}>
                            <label>Макс. себестоимость (₽):</label>
                            <input
                                type="number"
                                min="100"
                                max="1000"
                                step="50"
                                value={constraints.maxCost}
                                onChange={(e) => updateConstraint('maxCost', parseInt(e.target.value))}
                            />
                        </div>

                        <div className={styles.constraintItem}>
                            <label>Мин. маржа прибыли (%):</label>
                            <input
                                type="number"
                                min="10"
                                max="80"
                                step="5"
                                value={Math.round(constraints.minProfitMargin * 100)}
                                onChange={(e) => updateConstraint('minProfitMargin', parseInt(e.target.value) / 100)}
                            />
                        </div>

                        <div className={styles.constraintItem}>
                            <label>Количество вариантов:</label>
                            <input
                                type="number"
                                min="1"
                                max="10"
                                value={constraints.numResults}
                                onChange={(e) => updateConstraint('numResults', parseInt(e.target.value))}
                            />
                        </div>
                    </div>

                    <div className={styles.mustIncludeSection}>
                        <h4 className={styles.subsectionTitle}>Обязательные ингредиенты:</h4>
                        <div className={styles.ingredientsTags}>
                            {constraints.mustInclude.map((ingredient, index) => (
                                <span key={index} className={styles.ingredientTag}>
                                    {ingredient}
                                    <button
                                        type="button"
                                        className={styles.removeTag}
                                        onClick={() => removeMustInclude(ingredient)}
                                    >
                                        ×
                                    </button>
                                </span>
                            ))}
                        </div>

                        <h4 className={styles.subsectionTitle}>Популярные ингредиенты:</h4>
                        <div className={styles.popularIngredients}>
                            {popularIngredients.slice(0, 8).map((ingredient, index) => (
                                <button
                                    key={index}
                                    type="button"
                                    className={`${styles.ingredientButton} ${
                                        constraints.mustInclude.includes(ingredient.name) ? styles.selected : ''
                                    }`}
                                    onClick={() => addMustInclude(ingredient.name)}
                                >
                                    {ingredient.name}
                                    <span className={styles.ingredientStats}>
                                        {ingredient.popularity ? ` ${ingredient.popularity}%` : ''}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>

                    <button
                        className={`${styles.button} ${styles.buttonPrimary}`}
                        onClick={handleOptimize}
                        disabled={loading}
                    >
                        {loading ? 'Оптимизация...' : '🚀 Запустить оптимизацию'}
                    </button>
                </div>

                {/* Результаты */}
                <div className={styles.resultsPanel}>
                    <div className={styles.resultsHeader}>
                        <h3 className={styles.sectionTitle}>Оптимальные рецепты</h3>
                        {optimizationResults && (
                            <div className={styles.generationInfo}>
                                <small>Сгенерировано: {new Date().toLocaleTimeString()}</small>
                            </div>
                        )}
                    </div>

                    {error && (
                        <div className={styles.error}>
                            <p>{error}</p>
                            <button onClick={handleOptimize}>Повторить</button>
                        </div>
                    )}

                    {optimizationResults?.optimizedRolls ? (
                        <div className={styles.optimizationResults}>
                            {optimizationResults.optimizedRolls.map((roll, index) => (
                                <div key={index} className={styles.optimizationCard}>
                                    <div className={styles.optimizationHeader}>
                                        <span className={styles.rankBadge}>#{index + 1}</span>
                                        <h4 className={styles.optimizationTitle}>
                                            {roll.name || `Оптимизированный ролл ${index + 1}`}
                                        </h4>
                                        {(() => {
                                            const rawScore =
                                                roll.score ??
                                                roll.fitnessScore ??
                                                roll.confidenceScore ??
                                                roll.noveltyScore ??
                                                null;
                                            // Normalize fitnessScore to 0..1 for UI badge if needed.
                                            const normScore = rawScore == null
                                                ? null
                                                : (rawScore > 1 ? Math.min(1, rawScore / 100) : rawScore);
                                            const cls = normScore == null
                                                ? styles.scoreLow
                                                : (normScore > 0.8 ? styles.scoreHigh : normScore > 0.6 ? styles.scoreMedium : styles.scoreLow);
                                            const label = normScore == null ? '—' : `${(normScore * 100).toFixed(0)}%`;
                                            return (
                                                <span className={`${styles.scoreBadge} ${cls}`}>
                                                    Оценка: {label}
                                                </span>
                                            );
                                        })()}
                                    </div>

                                    <div className={styles.ingredientsList}>
                                        <strong>Состав:</strong>
                                        <div className={styles.ingredientsTags}>
                                            {roll.ingredients.map((ing, i) => (
                                                <span key={i} className={styles.ingredientTag}>
                                                    {ing}
                                                </span>
                                            ))}
                                        </div>
                                    </div>

                                    <div className={styles.financialGrid}>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Себестоимость:</span>
                                            <span className={styles.financialValue}>
                                                {(roll.estimatedCost ?? roll.cost) != null
                                                    ? `${Number(roll.estimatedCost ?? roll.cost).toFixed(2)}₽`
                                                    : '—'}
                                            </span>
                                        </div>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Прогноз продаж:</span>
                                            <span className={styles.financialValue}>
                                                {roll.predictedSales ? `${roll.predictedSales.toFixed(1)} в день` : '—'}
                                            </span>
                                        </div>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Прибыль:</span>
                                            <span className={styles.financialValue}>
                                                {roll.estimatedProfit != null
                                                    ? `${Number(roll.estimatedProfit).toFixed(2)}₽`
                                                    : (roll.profit != null ? `${Number(roll.profit).toFixed(2)}₽` : '—')}
                                            </span>
                                        </div>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Маржа:</span>
                                            <span className={styles.financialValue}>
                                                {roll.profitMargin ? `${(roll.profitMargin * 100).toFixed(1)}%` : '—'}
                                            </span>
                                        </div>
                                    </div>

                                    {roll.explanation && (
                                        <div className={styles.reasons}>
                                            <strong>Рекомендация AI:</strong>
                                            <p>{roll.explanation}</p>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    ) : optimizationResults?.results ? (
                        // Если API возвращает results вместо optimizedRolls
                        <div className={styles.optimizationResults}>
                            {optimizationResults.results.map((result, index) => (
                                <div key={index} className={styles.optimizationCard}>
                                    <div className={styles.optimizationHeader}>
                                        <span className={styles.rankBadge}>#{index + 1}</span>
                                        <h4 className={styles.optimizationTitle}>
                                            {result.name || `Вариант ${index + 1}`}
                                        </h4>
                                        {(() => {
                                            const rawScore =
                                                result.score ??
                                                result.fitnessScore ??
                                                result.confidenceScore ??
                                                result.noveltyScore ??
                                                null;
                                            const normScore = rawScore == null
                                                ? null
                                                : (rawScore > 1 ? Math.min(1, rawScore / 100) : rawScore);
                                            const cls = normScore == null
                                                ? styles.scoreLow
                                                : (normScore > 0.8 ? styles.scoreHigh : normScore > 0.6 ? styles.scoreMedium : styles.scoreLow);
                                            const label = normScore == null ? '—' : `${(normScore * 100).toFixed(0)}%`;
                                            return (
                                                <span className={`${styles.scoreBadge} ${cls}`}>
                                                    Оценка: {label}
                                                </span>
                                            );
                                        })()}
                                    </div>

                                    <div className={styles.ingredientsList}>
                                        <strong>Состав:</strong>
                                        <div className={styles.ingredientsTags}>
                                            {result.ingredients.map((ing, i) => (
                                                <span key={i} className={styles.ingredientTag}>
                                                    {ing}
                                                </span>
                                            ))}
                                        </div>
                                    </div>

                                    <div className={styles.financialGrid}>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Себестоимость:</span>
                                            <span className={styles.financialValue}>
                                                {(result.estimatedCost ?? result.cost) != null
                                                    ? `${Number(result.estimatedCost ?? result.cost).toFixed(2)}₽`
                                                    : '—'}
                                            </span>
                                        </div>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Прогноз продаж:</span>
                                            <span className={styles.financialValue}>
                                                {result.predictedSales ? `${result.predictedSales.toFixed(1)} в день` : '—'}
                                            </span>
                                        </div>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Прибыль:</span>
                                            <span className={styles.financialValue}>
                                                {result.estimatedProfit != null
                                                    ? `${Number(result.estimatedProfit).toFixed(2)}₽`
                                                    : (result.profit != null ? `${Number(result.profit).toFixed(2)}₽` : '—')}
                                            </span>
                                        </div>
                                        <div className={styles.financialItem}>
                                            <span className={styles.financialLabel}>Маржа:</span>
                                            <span className={styles.financialValue}>
                                                {result.profitMargin ? `${(result.profitMargin * 100).toFixed(1)}%` : '—'}
                                            </span>
                                        </div>
                                    </div>

                                    {result.reasons && (
                                        <div className={styles.reasons}>
                                            <strong>Рекомендация AI:</strong>
                                            <p>{result.reasons}</p>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className={styles.emptyResults}>
                            <p>Запустите оптимизацию для получения результатов</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}