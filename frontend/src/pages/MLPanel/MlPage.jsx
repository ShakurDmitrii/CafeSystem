import {
    BarChartOutlined,
    ExperimentOutlined,
    RobotOutlined,
    SyncOutlined,
    ThunderboltOutlined
} from '@ant-design/icons';
import React, { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { API_BASE_URL } from '../../auth';
import AnalyticsDashboard from './AnalyticsDashboard';
import { ApiClient } from './api';
import { formatCurrency, formatInteger, formatNumber, formatPercent } from './formatters';
import styles from './MlPage.module.css';
import OptimizationResults from './OptimizationResults';
import PredictRollPage from './PredictRollPage';

const TABS = [
    { id: 'predict', label: 'Прогноз', icon: ThunderboltOutlined },
    { id: 'optimize', label: 'Оптимизация', icon: ExperimentOutlined },
    { id: 'analytics', label: 'Аналитика', icon: BarChartOutlined },
    { id: 'generate', label: 'Новое блюдо', icon: RobotOutlined }
];

const DEFAULT_DISH_PARAMS = {
    days: 90,
    minIngredients: 3,
    maxIngredients: 6,
    populationSize: 80,
    generations: 40,
    markup: 2.35,
    mustIncludeText: 'рис, нори',
    excludedIngredientsText: ''
};

const parseCsv = (value) => value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

export default function MlPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedView = searchParams.get('view');
    const activeTab = TABS.some((tab) => tab.id === requestedView) ? requestedView : 'predict';
    const tabRefs = useRef({});
    const [isTraining, setIsTraining] = useState(false);
    const [trainingStatus, setTrainingStatus] = useState(null);
    const [isGeneratingDish, setIsGeneratingDish] = useState(false);
    const [generatedDish, setGeneratedDish] = useState(null);
    const [generationError, setGenerationError] = useState(null);
    const [isSavingDish, setIsSavingDish] = useState(false);
    const [saveStatus, setSaveStatus] = useState(null);
    const [dishParams, setDishParams] = useState(DEFAULT_DISH_PARAMS);

    useEffect(() => {
        tabRefs.current[activeTab]?.scrollIntoView({
            block: 'nearest',
            inline: 'center'
        });
    }, [activeTab]);

    const setActiveTab = (view) => {
        const nextParams = new URLSearchParams(searchParams);
        nextParams.set('view', view);
        setSearchParams(nextParams, { replace: true });
    };

    const sendTrainingData = async () => {
        setIsTraining(true);
        setTrainingStatus({ type: 'progress', text: 'Собираем продажи и обновляем модель…' });
        try {
            const response = await fetch(`${API_BASE_URL}/api/ml/train-with-recent-data`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    days: 90,
                    includeMenu: true,
                    includeSales: true,
                    includeIngredients: true
                })
            });
            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.error || result.message || 'Не удалось обучить модель.');
            }

            const recordsCount = result.recordsCount
                ?? result.records
                ?? result.trainingResult?.recordsCount;
            const ingredientsCount = result.newIngredientsCount
                ?? result.ingredientsCount
                ?? result.trainingResult?.ingredientsCount;
            setTrainingStatus({
                type: 'success',
                text: `Модель обновлена: ${formatInteger(recordsCount, '—')} записей, ${formatInteger(ingredientsCount, '—')} ингредиентов.`
            });
        } catch (error) {
            setTrainingStatus({
                type: 'error',
                text: error.message || 'Не удалось связаться с сервисом обучения.'
            });
        } finally {
            setIsTraining(false);
        }
    };

    const updateDishParam = (field, value) => {
        setDishParams((previous) => ({ ...previous, [field]: value }));
    };

    const handleGenerateDish = async (event) => {
        event.preventDefault();
        setIsGeneratingDish(true);
        setGenerationError(null);
        setGeneratedDish(null);
        setSaveStatus(null);
        try {
            const result = await ApiClient.generateDish({
                days: Number(dishParams.days) || 90,
                minIngredients: Number(dishParams.minIngredients) || 3,
                maxIngredients: Number(dishParams.maxIngredients) || 6,
                populationSize: Number(dishParams.populationSize) || 80,
                generations: Number(dishParams.generations) || 40,
                markup: Number(dishParams.markup) || 2.35,
                mustInclude: parseCsv(dishParams.mustIncludeText),
                excludedIngredients: parseCsv(dishParams.excludedIngredientsText)
            });

            if (result.status === 'failed') {
                throw new Error(result.errorMessage || 'Не удалось сгенерировать блюдо.');
            }
            setGeneratedDish(result);
        } catch (error) {
            setGenerationError(error.message || 'Ошибка генерации.');
        } finally {
            setIsGeneratingDish(false);
        }
    };

    const handleSaveDish = async () => {
        if (!generatedDish?.dish) return;
        setIsSavingDish(true);
        setSaveStatus(null);
        try {
            const dish = generatedDish.dish;
            const result = await ApiClient.saveGeneratedDish({
                dishName: dish.name,
                category: 'AI',
                recommendedPrice: dish.recommendedPrice,
                estimatedCost: dish.estimatedCost,
                weightGrams: dish.techCard?.reduce(
                    (sum, row) => sum + (Number(row.quantityGrams) || 0),
                    0
                ) || 140,
                ingredients: dish.ingredients || [],
                techCard: (dish.techCard || []).map((row) => ({
                    ingredientName: row.ingredientName,
                    quantityGrams: Number(row.quantityGrams) || 0,
                    unitCost: Number(row.unitCost) || 0,
                    totalCost: Number(row.totalCost) || 0
                }))
            });

            if (result.status === 'failed') {
                throw new Error(result.errorMessage || 'Не удалось сохранить блюдо.');
            }
            const missing = result.missingIngredients?.length
                ? ` Не найдены: ${result.missingIngredients.join(', ')}.`
                : '';
            setSaveStatus({
                type: 'success',
                text: `Блюдо сохранено с ID ${result.dishId}.${missing}`
            });
        } catch (error) {
            setSaveStatus({ type: 'error', text: error.message || 'Ошибка сохранения блюда.' });
        } finally {
            setIsSavingDish(false);
        }
    };

    return (
        <div className={styles.mlPage}>
            <header className={styles.hero}>
                <div className={styles.heroCopy}>
                    <p className={styles.eyebrow}>CafeHelp Intelligence</p>
                    <h1>Решения для меню на основе данных</h1>
                    <p className={styles.subtitle}>
                        Прогнозируйте спрос, проверяйте экономику рецептов и находите точки роста.
                    </p>
                </div>
                <div className={styles.heroActions}>
                    <span className={styles.modelStatus}>
                        <i aria-hidden="true" />
                        Модель готова
                    </span>
                    <button
                        type="button"
                        onClick={sendTrainingData}
                        disabled={isTraining}
                        className={styles.trainButton}
                    >
                        <SyncOutlined spin={isTraining} aria-hidden="true" />
                        {isTraining ? 'Обновляем модель…' : 'Обучить на новых данных'}
                    </button>
                </div>
            </header>

            {trainingStatus && (
                <div
                    className={`${styles.trainingStatus} ${styles[trainingStatus.type]}`}
                    role={trainingStatus.type === 'error' ? 'alert' : 'status'}
                    aria-live="polite"
                >
                    {trainingStatus.text}
                </div>
            )}

            <nav className={styles.tabs} aria-label="Инструменты AI">
                {TABS.map((tab) => {
                    const Icon = tab.icon;
                    const selected = activeTab === tab.id;
                    return (
                        <button
                            key={tab.id}
                            ref={(element) => {
                                tabRefs.current[tab.id] = element;
                            }}
                            type="button"
                            className={`${styles.tab} ${selected ? styles.active : ''}`}
                            aria-current={selected ? 'page' : undefined}
                            onClick={() => setActiveTab(tab.id)}
                        >
                            <Icon aria-hidden="true" />
                            <span>{tab.label}</span>
                        </button>
                    );
                })}
            </nav>

            <div className={styles.content}>
                {activeTab === 'predict' && (
                    <PredictRollPage onOpenOptimizer={() => setActiveTab('optimize')} />
                )}
                {activeTab === 'optimize' && <OptimizationResults />}
                {activeTab === 'analytics' && <AnalyticsDashboard />}
                {activeTab === 'generate' && (
                    <GenerateDishPanel
                        dishParams={dishParams}
                        updateDishParam={updateDishParam}
                        handleGenerateDish={handleGenerateDish}
                        isGeneratingDish={isGeneratingDish}
                        generationError={generationError}
                        generatedDish={generatedDish}
                        handleSaveDish={handleSaveDish}
                        isSavingDish={isSavingDish}
                        saveStatus={saveStatus}
                    />
                )}
            </div>

            <p className={styles.disclaimer}>
                Прогнозы помогают принять решение, но не заменяют проверку фактических продаж и себестоимости.
            </p>
        </div>
    );
}

function GenerateDishPanel({
    dishParams,
    updateDishParam,
    handleGenerateDish,
    isGeneratingDish,
    generationError,
    generatedDish,
    handleSaveDish,
    isSavingDish,
    saveStatus
}) {
    const dish = generatedDish?.dish;
    const fields = [
        { name: 'days', label: 'Период продаж, дней', min: 7 },
        { name: 'minIngredients', label: 'Минимум ингредиентов', min: 2 },
        { name: 'maxIngredients', label: 'Максимум ингредиентов', min: 3 },
        { name: 'populationSize', label: 'Размер популяции', min: 20 },
        { name: 'generations', label: 'Поколения', min: 5 },
        { name: 'markup', label: 'Коэффициент наценки', min: 1.3, step: 0.05 }
    ];

    return (
        <section className={styles.generateWrap} aria-labelledby="generate-heading">
            <div className={styles.sectionIntro}>
                <div>
                    <p className={styles.eyebrow}>Лаборатория меню</p>
                    <h2 id="generate-heading">Создайте блюдо из истории продаж</h2>
                    <p>Алгоритм соберёт рецепт, рассчитает техкарту и предложит цену.</p>
                </div>
                <span className={styles.labBadge}><RobotOutlined /> Экспериментальный режим</span>
            </div>

            <div className={styles.generateGrid}>
                <form className={styles.generatorForm} onSubmit={handleGenerateDish}>
                    <div className={styles.formHeading}>
                        <span>01</span>
                        <div>
                            <h3>Параметры поиска</h3>
                            <p>Укажите рамки для нового рецепта.</p>
                        </div>
                    </div>
                    <div className={styles.formGrid}>
                        {fields.map((field) => (
                            <label key={field.name} className={styles.field} htmlFor={`dish-${field.name}`}>
                                <span>{field.label}</span>
                                <input
                                    id={`dish-${field.name}`}
                                    name={field.name}
                                    type="number"
                                    autoComplete="off"
                                    min={field.min}
                                    step={field.step}
                                    value={dishParams[field.name]}
                                    onChange={(event) => updateDishParam(field.name, event.target.value)}
                                />
                            </label>
                        ))}
                        <label className={`${styles.field} ${styles.fieldWide}`} htmlFor="dish-required">
                            <span>Обязательные ингредиенты</span>
                            <input
                                id="dish-required"
                                name="mustInclude"
                                type="text"
                                autoComplete="off"
                                value={dishParams.mustIncludeText}
                                onChange={(event) => updateDishParam('mustIncludeText', event.target.value)}
                                placeholder="Например: рис, нори"
                            />
                            <small>Перечислите через запятую</small>
                        </label>
                        <label className={`${styles.field} ${styles.fieldWide}`} htmlFor="dish-excluded">
                            <span>Исключить из рецепта</span>
                            <input
                                id="dish-excluded"
                                name="excludedIngredients"
                                type="text"
                                autoComplete="off"
                                value={dishParams.excludedIngredientsText}
                                onChange={(event) => updateDishParam('excludedIngredientsText', event.target.value)}
                                placeholder="Например: угорь, кунжут"
                            />
                        </label>
                    </div>

                    <button type="submit" className={styles.generateButton} disabled={isGeneratingDish}>
                        <RobotOutlined aria-hidden="true" />
                        {isGeneratingDish ? 'Создаём рецепт…' : 'Сгенерировать блюдо'}
                    </button>
                    {generationError && <div className={styles.errorBox} role="alert">{generationError}</div>}
                </form>

                {!dish ? (
                    <div className={styles.generateEmpty}>
                        <RobotOutlined aria-hidden="true" />
                        <p className={styles.eyebrow}>Новый рецепт</p>
                        <h3>{isGeneratingDish ? 'Алгоритм перебирает варианты' : 'Результат появится здесь'}</h3>
                        <p>Вы получите состав, цену, техкарту и объяснение выбора модели.</p>
                    </div>
                ) : (
                    <article className={styles.resultCard}>
                        <div className={styles.resultHeading}>
                            <div>
                                <p className={styles.eyebrow}>Рецепт готов</p>
                                <h3>{dish.name}</h3>
                            </div>
                            <span>{formatPercent(dish.noveltyScore, { fraction: true })} новизна</span>
                        </div>

                        <div className={styles.metrics}>
                            <div><span>Себестоимость</span><strong>{formatCurrency(dish.estimatedCost)}</strong></div>
                            <div><span>Цена</span><strong>{formatCurrency(dish.recommendedPrice)}</strong></div>
                            <div><span>Продажи</span><strong>{formatNumber(dish.predictedSales)}</strong></div>
                            <div><span>Прибыль</span><strong>{formatCurrency(dish.estimatedProfit)}</strong></div>
                        </div>

                        <h4>Состав</h4>
                        <div className={styles.tags}>
                            {(dish.ingredients || []).map((ingredient) => (
                                <span key={ingredient} className={styles.tag}>{ingredient}</span>
                            ))}
                        </div>

                        <h4>Техкарта</h4>
                        <div className={styles.techTableWrap}>
                            <table className={styles.techTable}>
                                <thead>
                                    <tr>
                                        <th>Ингредиент</th>
                                        <th>Граммы</th>
                                        <th>Цена ед.</th>
                                        <th>Сумма</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {(dish.techCard || []).map((row) => (
                                        <tr key={row.ingredientName}>
                                            <td>{row.ingredientName}</td>
                                            <td>{formatNumber(row.quantityGrams)}</td>
                                            <td>{formatCurrency(row.unitCost)}</td>
                                            <td>{formatCurrency(row.totalCost)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {dish.reasoning?.length > 0 && (
                            <>
                                <h4>Почему этот состав</h4>
                                <ul className={styles.reasonList}>
                                    {dish.reasoning.map((text) => <li key={text}>{text}</li>)}
                                </ul>
                            </>
                        )}

                        <button
                            type="button"
                            className={styles.saveButton}
                            onClick={handleSaveDish}
                            disabled={isSavingDish}
                        >
                            {isSavingDish ? 'Сохраняем…' : 'Сохранить в меню'}
                        </button>
                        {saveStatus && (
                            <div
                                className={saveStatus.type === 'success' ? styles.saveOk : styles.saveError}
                                role={saveStatus.type === 'error' ? 'alert' : 'status'}
                            >
                                {saveStatus.text}
                            </div>
                        )}
                    </article>
                )}
            </div>
        </section>
    );
}
