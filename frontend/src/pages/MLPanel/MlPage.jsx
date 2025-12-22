import React, { useState } from 'react';
import PredictRollPage from './PredictRollPage';
import OptimizationResults from './OptimizationResults';
import AnalyticsDashboard from './AnalyticsDashboard';
import styles from './MlPage.module.css';

export default function MlPage() {
    const [activeTab, setActiveTab] = useState('predict');
    const [isTraining, setIsTraining] = useState(false);
    const [trainingStatus, setTrainingStatus] = useState(null);

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
            </nav>

            <div className={styles.content}>
                {activeTab === 'predict' && <PredictRollPage />}
                {activeTab === 'optimize' && <OptimizationResults />}
                {activeTab === 'analytics' && <AnalyticsDashboard />}
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