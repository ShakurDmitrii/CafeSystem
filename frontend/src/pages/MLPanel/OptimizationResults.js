import {
    ExperimentOutlined,
    PlusOutlined,
    RocketOutlined,
    SafetyCertificateOutlined
} from '@ant-design/icons';
import React, { useEffect, useMemo, useState } from 'react';
import { ApiClient } from './api';
import { formatCurrency, formatNumber, formatPercent, formatTime } from './formatters';
import styles from './mlStyles.module.css';

const OPT_CACHE_KEY = 'ml.optimization.cache.v1';
const DEFAULT_CONSTRAINTS = {
    minIngredients: 3,
    maxIngredients: 6,
    maxCost: 350,
    mustInclude: ['рис', 'лосось'],
    exclude: [],
    minProfitMargin: 0.3,
    numResults: 3
};

function readCachedOptimization() {
    try {
        const cached = JSON.parse(localStorage.getItem(OPT_CACHE_KEY));
        return cached?.results ? cached : null;
    } catch {
        return null;
    }
}

function getScore(roll) {
    const value = Number(
        roll.score
        ?? roll.fitnessScore
        ?? roll.confidenceScore
        ?? roll.noveltyScore
    );
    if (!Number.isFinite(value)) return null;
    return value > 1 ? Math.min(value / 100, 1) : Math.min(value, 1);
}

export default function OptimizationResults() {
    const [optimizationResults, setOptimizationResults] = useState(null);
    const [generatedAt, setGeneratedAt] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [popularIngredients, setPopularIngredients] = useState([]);
    const [constraints, setConstraints] = useState(DEFAULT_CONSTRAINTS);

    useEffect(() => {
        let active = true;
        const cached = readCachedOptimization();
        if (cached?.constraints) {
            setConstraints((previous) => ({ ...previous, ...cached.constraints }));
        }

        const resultsPromise = cached
            ? Promise.resolve(cached.results)
            : ApiClient.optimizeRoll(DEFAULT_CONSTRAINTS);

        Promise.allSettled([
            ApiClient.getPopularIngredients(30),
            resultsPromise
        ]).then(([ingredientsResult, optimizationResult]) => {
            if (!active) return;

            if (ingredientsResult.status === 'fulfilled') {
                setPopularIngredients(ingredientsResult.value);
            }

            if (optimizationResult.status === 'fulfilled') {
                setOptimizationResults(optimizationResult.value);
                setGeneratedAt(cached?.savedAt || optimizationResult.value?.timestamp || new Date().toISOString());
            } else {
                setError('Не удалось подготовить стартовые варианты.');
            }
            setLoading(false);
        });

        return () => {
            active = false;
        };
    }, []);

    const rolls = useMemo(() => (
        optimizationResults?.optimizedRolls
        || optimizationResults?.results
        || []
    ), [optimizationResults]);

    const updateConstraint = (field, value) => {
        setConstraints((previous) => ({ ...previous, [field]: value }));
    };

    const addMustInclude = (ingredient) => {
        setConstraints((previous) => (
            previous.mustInclude.includes(ingredient)
                ? previous
                : { ...previous, mustInclude: [...previous.mustInclude, ingredient] }
        ));
    };

    const removeMustInclude = (ingredient) => {
        setConstraints((previous) => ({
            ...previous,
            mustInclude: previous.mustInclude.filter((item) => item !== ingredient)
        }));
    };

    const handleOptimize = async () => {
        setLoading(true);
        setError(null);
        try {
            const results = await ApiClient.optimizeRoll(constraints);
            const timestamp = results?.timestamp || new Date().toISOString();
            setOptimizationResults(results);
            setGeneratedAt(timestamp);
            localStorage.setItem(OPT_CACHE_KEY, JSON.stringify({
                constraints,
                results,
                savedAt: timestamp
            }));
        } catch (err) {
            setError(err.message || 'Не удалось выполнить оптимизацию.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className={styles.optimizationContainer} aria-labelledby="optimization-heading">
            <div className={styles.sectionIntro}>
                <div>
                    <p className={styles.eyebrow}>Оптимизация меню</p>
                    <h2 id="optimization-heading">Найдите состав с лучшей экономикой</h2>
                    <p>Задайте ограничения — алгоритм сравнит рецепты по спросу, марже и себестоимости.</p>
                </div>
                <span className={styles.stepBadge}><ExperimentOutlined /> Генетический алгоритм</span>
            </div>

            <div className={styles.optimizationContent}>
                <aside className={styles.constraintsPanel}>
                    <div className={styles.cardHeading}>
                        <span className={styles.cardIndex}>01</span>
                        <div>
                            <h3>Ограничения</h3>
                            <p>Условия для генерации вариантов.</p>
                        </div>
                    </div>

                    <div className={styles.constraintsGrid}>
                        <label className={styles.constraintItem} htmlFor="min-ingredients">
                            <span>Минимум ингредиентов</span>
                            <input id="min-ingredients" name="minIngredients" type="number" min="2" max="10" autoComplete="off"
                                value={constraints.minIngredients}
                                onChange={(event) => updateConstraint('minIngredients', Number(event.target.value))} />
                        </label>
                        <label className={styles.constraintItem} htmlFor="max-ingredients">
                            <span>Максимум ингредиентов</span>
                            <input id="max-ingredients" name="maxIngredients" type="number" min="3" max="12" autoComplete="off"
                                value={constraints.maxIngredients}
                                onChange={(event) => updateConstraint('maxIngredients', Number(event.target.value))} />
                        </label>
                        <label className={styles.constraintItem} htmlFor="max-cost">
                            <span>Себестоимость до, ₽</span>
                            <input id="max-cost" name="maxCost" type="number" min="100" max="1000" step="50" autoComplete="off"
                                value={constraints.maxCost}
                                onChange={(event) => updateConstraint('maxCost', Number(event.target.value))} />
                        </label>
                        <label className={styles.constraintItem} htmlFor="min-margin">
                            <span>Минимальная маржа, %</span>
                            <input id="min-margin" name="minProfitMargin" type="number" min="10" max="80" step="5" autoComplete="off"
                                value={Math.round(constraints.minProfitMargin * 100)}
                                onChange={(event) => updateConstraint('minProfitMargin', Number(event.target.value) / 100)} />
                        </label>
                        <label className={styles.constraintItem} htmlFor="result-count">
                            <span>Количество вариантов</span>
                            <input id="result-count" name="numResults" type="number" min="1" max="10" autoComplete="off"
                                value={constraints.numResults}
                                onChange={(event) => updateConstraint('numResults', Number(event.target.value))} />
                        </label>
                    </div>

                    <div className={styles.mustIncludeSection}>
                        <div className={styles.labelRow}>
                            <h4>Обязательные ингредиенты</h4>
                            <span>{constraints.mustInclude.length}</span>
                        </div>
                        <div className={styles.ingredientsTags}>
                            {constraints.mustInclude.map((ingredient) => (
                                <span key={ingredient} className={styles.ingredientTag}>
                                    {ingredient}
                                    <button
                                        type="button"
                                        className={styles.removeTag}
                                        aria-label={`Убрать ${ingredient}`}
                                        onClick={() => removeMustInclude(ingredient)}
                                    >
                                        ×
                                    </button>
                                </span>
                            ))}
                        </div>

                        <h4 className={styles.subsectionTitle}>Добавить популярное</h4>
                        <div className={styles.popularIngredients}>
                            {popularIngredients.slice(0, 8).map((ingredient) => (
                                <button
                                    key={ingredient.name}
                                    type="button"
                                    className={`${styles.ingredientButton} ${
                                        constraints.mustInclude.includes(ingredient.name) ? styles.selected : ''
                                    }`}
                                    disabled={constraints.mustInclude.includes(ingredient.name)}
                                    onClick={() => addMustInclude(ingredient.name)}
                                >
                                    <PlusOutlined aria-hidden="true" />
                                    <span>{ingredient.name}</span>
                                </button>
                            ))}
                        </div>
                    </div>

                    <button
                        type="button"
                        className={`${styles.button} ${styles.buttonPrimary} ${styles.fullWidthButton}`}
                        onClick={handleOptimize}
                        disabled={loading}
                    >
                        <RocketOutlined aria-hidden="true" />
                        {loading ? 'Ищем варианты…' : 'Запустить оптимизацию'}
                    </button>
                </aside>

                <div className={styles.resultsPanel}>
                    <div className={styles.resultsHeader}>
                        <div>
                            <p className={styles.eyebrow}>Результат</p>
                            <h3>Рекомендованные рецепты</h3>
                        </div>
                        {generatedAt && <span>Обновлено в {formatTime(generatedAt)}</span>}
                    </div>

                    {error && <div className={styles.inlineError} role="alert">{error}</div>}
                    {loading && rolls.length === 0 && (
                        <div className={styles.centeredState} role="status">
                            <span className={styles.spinner} aria-hidden="true" />
                            <h3>Готовим стартовые варианты</h3>
                            <p>Это может занять несколько секунд.</p>
                        </div>
                    )}

                    {!loading && rolls.length === 0 && !error && (
                        <div className={styles.emptyResults}>
                            <ExperimentOutlined aria-hidden="true" />
                            <h3>Вариантов пока нет</h3>
                            <p>Измените ограничения и запустите оптимизацию.</p>
                        </div>
                    )}

                    <div className={styles.optimizationResults}>
                        {rolls.map((roll, index) => {
                            const score = getScore(roll);
                            const scoreClass = score >= 0.8
                                ? styles.scoreHigh
                                : score >= 0.6
                                    ? styles.scoreMedium
                                    : styles.scoreLow;
                            return (
                                <article key={`${roll.name || 'roll'}-${index}`} className={styles.optimizationCard}>
                                    <div className={styles.optimizationHeader}>
                                        <span className={styles.rankBadge}>0{index + 1}</span>
                                        <div>
                                            <h4>{roll.name || `Вариант ${index + 1}`}</h4>
                                            <span className={`${styles.scoreBadge} ${scoreClass}`}>
                                                <SafetyCertificateOutlined aria-hidden="true" />
                                                Оценка {formatPercent(score, { fraction: true })}
                                            </span>
                                        </div>
                                    </div>

                                    <div className={styles.ingredientsTags}>
                                        {(roll.ingredients || []).map((ingredient) => (
                                            <span key={ingredient} className={styles.ingredientTag}>{ingredient}</span>
                                        ))}
                                    </div>

                                    <div className={styles.financialGrid}>
                                        <div>
                                            <span>Себестоимость</span>
                                            <strong>{formatCurrency(roll.estimatedCost ?? roll.cost)}</strong>
                                        </div>
                                        <div>
                                            <span>Продажи в день</span>
                                            <strong>{formatNumber(roll.predictedSales)}</strong>
                                        </div>
                                        <div>
                                            <span>Прибыль</span>
                                            <strong>{formatCurrency(roll.estimatedProfit ?? roll.profit)}</strong>
                                        </div>
                                        <div>
                                            <span>Маржа</span>
                                            <strong>{formatPercent(roll.profitMargin, { fraction: true, digits: 1 })}</strong>
                                        </div>
                                    </div>

                                    {(roll.explanation || roll.reasons) && (
                                        <div className={styles.reasons}>
                                            <strong>Почему этот вариант</strong>
                                            <p>{roll.explanation || roll.reasons}</p>
                                        </div>
                                    )}
                                </article>
                            );
                        })}
                    </div>
                </div>
            </div>
        </section>
    );
}
