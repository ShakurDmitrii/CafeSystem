// src/pages/MlPanel/api.js
import { API_BASE_URL } from '../../auth';

const API_BASE = `${API_BASE_URL}/api/ml`;
const ANALYTICS_BASE = `${API_BASE_URL}/api/analytics`;

export const ApiClient = {
    // 1. Проверка соединения с бэкендом
    checkConnection: async () => {
        try {
            const response = await fetch(`${API_BASE}/data/health`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                }
            });

            if (response.ok) {
                return await response.json();
            }
            return { status: 'error', message: `HTTP ${response.status}` };
        } catch (error) {
            return { status: 'unavailable', message: error.message };
        }
    },

    // 2. Получить все ингредиенты из БД
    getIngredients: async () => {
        try {
            const response = await fetch(`${API_BASE}/data/ingredients`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            // Проверяем content-type
            const contentType = response.headers.get('content-type');
            const isJson = contentType && contentType.includes('application/json');

            if (!isJson) {
                const text = await response.text();
                console.error('Server returned non-JSON:', text.substring(0, 500));
                throw new Error(`Server returned HTML (status: ${response.status})`);
            }

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            return data;

        } catch (error) {
            console.error('Failed to fetch ingredients:', error.message);
            throw error;
        }
    },

    // 3. Предсказать продажи для ролла
    predictSales: async (ingredients) => {
        try {
            const requestData = {
                ingredients: ingredients,

            };

            const response = await fetch(`${API_BASE}/predict/single`, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                let msg = `HTTP error! status: ${response.status}`;
                try {
                    const errData = await response.json();
                    msg = errData?.errorMessage || errData?.error || errData?.message || msg;
                } catch (_) {
                    // ignore parse errors
                }
                throw new Error(msg);
            }

            const data = await response.json();

            // Если бэкенд возвращает ошибку в поле errorMessage
            if (data.errorMessage) {
                throw new Error(data.errorMessage);
            }

            return data;

        } catch (error) {
            console.error('Prediction failed:', error.message);
            throw error;
        }
    },

    // 4. Оптимизировать состав ролла
    optimizeRoll: async (constraints) => {
        try {
            // Java endpoint `/api/ml/predict/optimize` expects OptimizationRequestDTO fields at top-level,
            // not wrapped into `constraints`.
            const requestData = {
                minIngredients: constraints.minIngredients,
                maxIngredients: constraints.maxIngredients,
                maxCost: constraints.maxCost,
                minProfitMargin: constraints.minProfitMargin,
                mustInclude: constraints.mustInclude,
                excludedIngredients: constraints.excludedIngredients ?? constraints.exclude ?? [],
                populationSize: constraints.populationSize,
                generations: constraints.generations,
                numResults: constraints.numResults
            };

            const response = await fetch(`${API_BASE}/predict/optimize`, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                let msg = `HTTP error! status: ${response.status}`;
                try {
                    const errData = await response.json();
                    msg = errData?.errorMessage || errData?.error || errData?.message || msg;
                } catch (_) {
                    // ignore parse errors
                }
                throw new Error(msg);
            }

            const data = await response.json();

            if (data.errorMessage) {
                throw new Error(data.errorMessage);
            }

            return data;

        } catch (error) {
            console.error('Optimization failed:', error.message);
            throw error;
        }
    },

    // 5. Сгенерировать новое блюдо через генетический алгоритм
    generateDish: async (params = {}) => {
        try {
            const response = await fetch(`${API_BASE}/predict/generate-dish`, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    days: params.days ?? 90,
                    minIngredients: params.minIngredients ?? 3,
                    maxIngredients: params.maxIngredients ?? 6,
                    populationSize: params.populationSize ?? 80,
                    generations: params.generations ?? 40,
                    markup: params.markup ?? 2.35,
                    mustInclude: params.mustInclude ?? [],
                    excludedIngredients: params.excludedIngredients ?? []
                })
            });

            const data = await response.json();
            if (!response.ok || data.status === 'failed') {
                throw new Error(data.errorMessage || `HTTP ${response.status}`);
            }
            return data;
        } catch (error) {
            console.error('Dish generation failed:', error.message);
            return {
                status: 'failed',
                errorMessage: error.message
            };
        }
    },

    saveGeneratedDish: async (dish) => {
        try {
            const response = await fetch(`${API_BASE}/predict/generate-dish/save`, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(dish)
            });

            const data = await response.json();
            if (!response.ok || data.status === 'failed') {
                throw new Error(data.errorMessage || `HTTP ${response.status}`);
            }
            return data;
        } catch (error) {
            console.error('Save generated dish failed:', error.message);
            return {
                status: 'failed',
                errorMessage: error.message
            };
        }
    },

    // 6. Получить популярные ингредиенты
    getPopularIngredients: async (days = 30) => {
        try {
            const response = await fetch(`${API_BASE}/data/ingredients/popular?days=${days}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            // Если приходит массив строк, преобразуем в объекты
            if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'string') {
                return data.map((name) => ({ name }));
            }

            return data;

        } catch (error) {
            console.error('Failed to fetch popular ingredients:', error.message);
            throw error;
        }
    },

    // 7. Получить данные для аналитики
    getAnalytics: async (timeRange = 'week', refresh = false) => {
        try {
            const query = new URLSearchParams({
                timeRange,
                refresh: String(refresh)
            });
            const response = await fetch(`${ANALYTICS_BASE}/dashboard?${query.toString()}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                }
            });

            if (response.ok) {
                return await response.json();
            }
            let message = `Analytics request failed: HTTP ${response.status}`;
            try {
                const payload = await response.json();
                message = payload?.errorMessage || payload?.message || message;
            } catch (_) {
                // Response body is optional for upstream failures.
            }
            throw new Error(message);
        } catch (error) {
            console.error('Analytics endpoint not available:', error.message);
            throw error;
        }
    }
};

// Экспортируем также как отдельные функции для удобства
export const {
    checkConnection,
    getIngredients,
    predictSales,
    optimizeRoll,
    generateDish,
    saveGeneratedDish,
    getPopularIngredients,
    getAnalytics
} = ApiClient;

export default ApiClient;
