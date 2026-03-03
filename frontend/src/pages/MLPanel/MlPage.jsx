import React, { useState } from 'react';
import PredictRollPage from './PredictRollPage';
import OptimizationResults from './OptimizationResults';
import AnalyticsDashboard from './AnalyticsDashboard';
import { ApiClient } from './api';
import styles from './MlPage.module.css';

export default function MlPage() {
    const [activeTab, setActiveTab] = useState('predict');
    const [isTraining, setIsTraining] = useState(false);
    const [trainingStatus, setTrainingStatus] = useState(null);
    const [isGeneratingDish, setIsGeneratingDish] = useState(false);
    const [generatedDish, setGeneratedDish] = useState(null);
    const [generationError, setGenerationError] = useState(null);
    const [isSavingDish, setIsSavingDish] = useState(false);
    const [saveStatus, setSaveStatus] = useState(null);
    const [dishParams, setDishParams] = useState({
        days: 90,
        minIngredients: 3,
        maxIngredients: 6,
        populationSize: 80,
        generations: 40,
        markup: 2.35,
        mustIncludeText: 'рис, нори',
        excludedIngredientsText: ''
    });

    const sendTrainingData = async () => {
        setIsTraining(true);
        setTrainingStatus('Отправка данных для обучения ML...');

        try {
            // Здесь будет запрос к бэкенду Java, чтобы получить реальные данные
            // и отправить их в Python ML сервис
            const response = await fetch('http://localhost:8080/api/ml/train-with-recent-data', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    days: 90, // Данные за последние 90 дней
                    includeMenu: true,
                    includeSales: true,
                    includeIngredients: true
                })
            });

            const result = await response.json();

            if (response.ok) {
                setTrainingStatus(`✅ Модель обучена на ${result.recordsCount} записях. 
                ${result.newIngredientsCount} ингредиентов в словаре.`);
            } else {
                setTrainingStatus(`❌ Ошибка: ${result.message || 'Не удалось обучить модель'}`);
            }
        } catch (error) {
            setTrainingStatus(`❌ Ошибка соединения: ${error.message}`);
        } finally {
            setIsTraining(false);
        }
    };

    const syncRealTimeData = async () => {
        try {
            setTrainingStatus('Синхронизация последних данных...');
            const response = await fetch('/api/ml/sync-latest', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    syncType: 'incremental',
                    updateModel: true
                })
            });

            const result = await response.json();
            setTrainingStatus(`✅ Синхронизировано ${result.newRecords} новых записей`);
        } catch (error) {
            setTrainingStatus(`❌ Ошибка синхронизации: ${error.message}`);
        }
    };

    const updateDishParam = (field, value) => {
        setDishParams(prev => ({
            ...prev,
            [field]: value
        }));
    };

    const parseCsv = (value) => value
        .split(',')
        .map(v => v.trim())
        .filter(Boolean);

    const handleGenerateDish = async () => {
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
                setGenerationError(result.errorMessage || 'Не удалось сгенерировать блюдо');
                return;
            }

            setGeneratedDish(result);
        } catch (error) {
            setGenerationError(error.message || 'Ошибка генерации');
        } finally {
            setIsGeneratingDish(false);
        }
    };

    const handleSaveDish = async () => {
        if (!generatedDish?.dish) return;

        setIsSavingDish(true);
        setSaveStatus(null);
        try {
            const payload = {
                dishName: generatedDish.dish.name,
                category: 'AI',
                recommendedPrice: generatedDish.dish.recommendedPrice,
                estimatedCost: generatedDish.dish.estimatedCost,
                weightGrams: generatedDish.dish.techCard?.reduce((sum, r) => sum + (Number(r.quantityGrams) || 0), 0) || 140,
                ingredients: generatedDish.dish.ingredients || [],
                techCard: (generatedDish.dish.techCard || []).map(row => ({
                    ingredientName: row.ingredientName,
                    quantityGrams: Number(row.quantityGrams) || 0,
                    unitCost: Number(row.unitCost) || 0,
                    totalCost: Number(row.totalCost) || 0
                }))
            };

            const result = await ApiClient.saveGeneratedDish(payload);
            if (result.status === 'failed') {
                setSaveStatus({ type: 'error', text: result.errorMessage || 'Ошибка сохранения' });
                return;
            }
            const missing = result.missingIngredients?.length
                ? ` Не найдены ингредиенты: ${result.missingIngredients.join(', ')}.`
                : '';
            setSaveStatus({
                type: 'success',
                text: `Блюдо сохранено (ID ${result.dishId}), строк техкарты: ${result.techCardRowsCreated}.${missing}`
            });
        } catch (error) {
            setSaveStatus({ type: 'error', text: error.message || 'Ошибка сохранения блюда' });
        } finally {
            setIsSavingDish(false);
        }
    };

    return (
        <div className={styles.mlPage}>
            <header className={styles.header}>
                <div className={styles.headerRow}>
                    <div>
                        <h1>🤖 AI Аналитика ресторана</h1>
                        <p className={styles.subtitle}>Машинное обучение для увеличения прибыли</p>
                    </div>
                    <div className={styles.trainingButtons}>
                        <button
                            onClick={sendTrainingData}
                            disabled={isTraining}
                            className={styles.trainButton}
                            title="Отправить исторические данные для обучения модели"
                        >
                            {isTraining ? '⏳ Обучение...' : '🎓 Обучить модель'}
                        </button>
                        <button
                            onClick={syncRealTimeData}
                            className={styles.syncButton}
                            title="Синхронизировать последние данные"
                        >
                            🔄 Синхро
                        </button>
                    </div>
                </div>

                {trainingStatus && (
                    <div className={styles.trainingStatus}>
                        {trainingStatus}
                    </div>
                )}
            </header>

            <nav className={styles.tabs}>
                <button
                    className={`${styles.tab} ${activeTab === 'predict' ? styles.active : ''}`}
                    onClick={() => setActiveTab('predict')}
                >
                    🔮 Предсказание продаж
                </button>
                <button
                    className={`${styles.tab} ${activeTab === 'optimize' ? styles.active : ''}`}
                    onClick={() => setActiveTab('optimize')}
                >
                    ⚙️ Оптимизация роллов
                </button>
                <button
                    className={`${styles.tab} ${activeTab === 'analytics' ? styles.active : ''}`}
                    onClick={() => setActiveTab('analytics')}
                >
                    📊 Аналитика
                </button>
                <button
                    className={`${styles.tab} ${activeTab === 'generate' ? styles.active : ''}`}
                    onClick={() => setActiveTab('generate')}
                >
                    🧬 Генерация блюда
                </button>
            </nav>

            <div className={styles.content}>
                {activeTab === 'predict' && <PredictRollPage />}
                {activeTab === 'optimize' && <OptimizationResults />}
                {activeTab === 'analytics' && <AnalyticsDashboard />}
                {activeTab === 'generate' && (
                    <div className={styles.generateWrap}>
                        <h2 className={styles.generateTitle}>Генерация нового блюда по данным продаж</h2>
                        <p className={styles.generateSubtitle}>
                            Алгоритм использует продажи, техкарты и текущие ингредиенты для создания нового блюда.
                        </p>

                        <div className={styles.formGrid}>
                            <label className={styles.field}>
                                <span>Период продаж (дней)</span>
                                <input
                                    type="number"
                                    min="7"
                                    value={dishParams.days}
                                    onChange={(e) => updateDishParam('days', e.target.value)}
                                />
                            </label>
                            <label className={styles.field}>
                                <span>Мин. ингредиентов</span>
                                <input
                                    type="number"
                                    min="2"
                                    value={dishParams.minIngredients}
                                    onChange={(e) => updateDishParam('minIngredients', e.target.value)}
                                />
                            </label>
                            <label className={styles.field}>
                                <span>Макс. ингредиентов</span>
                                <input
                                    type="number"
                                    min="3"
                                    value={dishParams.maxIngredients}
                                    onChange={(e) => updateDishParam('maxIngredients', e.target.value)}
                                />
                            </label>
                            <label className={styles.field}>
                                <span>Размер популяции</span>
                                <input
                                    type="number"
                                    min="20"
                                    value={dishParams.populationSize}
                                    onChange={(e) => updateDishParam('populationSize', e.target.value)}
                                />
                            </label>
                            <label className={styles.field}>
                                <span>Поколения</span>
                                <input
                                    type="number"
                                    min="5"
                                    value={dishParams.generations}
                                    onChange={(e) => updateDishParam('generations', e.target.value)}
                                />
                            </label>
                            <label className={styles.field}>
                                <span>Наценка</span>
                                <input
                                    type="number"
                                    min="1.3"
                                    step="0.05"
                                    value={dishParams.markup}
                                    onChange={(e) => updateDishParam('markup', e.target.value)}
                                />
                            </label>
                            <label className={`${styles.field} ${styles.fieldWide}`}>
                                <span>Обязательные ингредиенты (через запятую)</span>
                                <input
                                    type="text"
                                    value={dishParams.mustIncludeText}
                                    onChange={(e) => updateDishParam('mustIncludeText', e.target.value)}
                                />
                            </label>
                            <label className={`${styles.field} ${styles.fieldWide}`}>
                                <span>Исключить ингредиенты (через запятую)</span>
                                <input
                                    type="text"
                                    value={dishParams.excludedIngredientsText}
                                    onChange={(e) => updateDishParam('excludedIngredientsText', e.target.value)}
                                />
                            </label>
                        </div>

                        <button
                            className={styles.generateButton}
                            onClick={handleGenerateDish}
                            disabled={isGeneratingDish}
                        >
                            {isGeneratingDish ? 'Генерация...' : 'Сгенерировать блюдо'}
                        </button>

                        {generationError && (
                            <div className={styles.errorBox}>
                                {generationError}
                            </div>
                        )}

                        {generatedDish?.dish && (
                            <div className={styles.resultCard}>
                                <h3>{generatedDish.dish.name}</h3>
                                <div className={styles.metrics}>
                                    <div><strong>Себестоимость:</strong> {generatedDish.dish.estimatedCost} ₽</div>
                                    <div><strong>Рекомендуемая цена:</strong> {generatedDish.dish.recommendedPrice} ₽</div>
                                    <div><strong>Прогноз продаж:</strong> {generatedDish.dish.predictedSales}</div>
                                    <div><strong>Ожидаемая прибыль:</strong> {generatedDish.dish.estimatedProfit} ₽</div>
                                    <div><strong>Новизна:</strong> {generatedDish.dish.noveltyScore}</div>
                                    <div><strong>Generation:</strong> {generatedDish.dish.generationFound}</div>
                                </div>

                                <h4>Ингредиенты</h4>
                                <div className={styles.tags}>
                                    {generatedDish.dish.ingredients?.map((ing) => (
                                        <span key={ing} className={styles.tag}>{ing}</span>
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
                                        {generatedDish.dish.techCard?.map((row) => (
                                            <tr key={row.ingredientName}>
                                                <td>{row.ingredientName}</td>
                                                <td>{row.quantityGrams}</td>
                                                <td>{row.unitCost}</td>
                                                <td>{row.totalCost}</td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>

                                <h4>Почему выбрано</h4>
                                <ul className={styles.reasonList}>
                                    {generatedDish.dish.reasoning?.map((text, idx) => (
                                        <li key={idx}>{text}</li>
                                    ))}
                                </ul>

                                <div className={styles.saveRow}>
                                    <button
                                        className={styles.saveButton}
                                        onClick={handleSaveDish}
                                        disabled={isSavingDish}
                                    >
                                        {isSavingDish ? 'Сохранение...' : 'Сохранить как блюдо'}
                                    </button>
                                </div>
                                {saveStatus && (
                                    <div className={saveStatus.type === 'success' ? styles.saveOk : styles.saveError}>
                                        {saveStatus.text}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                )}
            </div>

            <footer className={styles.footer}>
                <p className={styles.disclaimer}>
                    💡 AI аналитика основана на исторических данных и машинном обучении.
                    Результаты являются прогнозными и могут отличаться от фактических показателей.
                </p>
            </footer>
        </div>
    );
}
