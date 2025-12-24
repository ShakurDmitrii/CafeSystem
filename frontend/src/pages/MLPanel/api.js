// src/pages/MlPanel/api.js
const API_BASE = 'http://localhost:8080/api/ml';

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
            console.warn('Backend not available, using mock data');
            return { status: 'mock', message: 'Using mock data' };
        }
    },

    // 2. Получить все ингредиенты из БД
    getIngredients: async () => {
        try {
            console.log('Fetching ingredients from:', `${API_BASE}/data/ingredients`);

            const response = await fetch(`${API_BASE}/data/ingredients`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            console.log('Response status:', response.status);

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
            console.log('Successfully loaded ingredients:', data.length);
            return data;

        } catch (error) {
            console.error('Failed to fetch ingredients, using mock data:', error.message);

            // Возвращаем мок данные при ошибке
            return [
                { name: 'рис', costPerUnit: 50 },
                { name: 'лосось', costPerUnit: 200 },
                { name: 'авокадо', costPerUnit: 80 },
                { name: 'огурец', costPerUnit: 30 },
                { name: 'нори', costPerUnit: 15 },
                { name: 'сыр', costPerUnit: 90 },
                { name: 'икра', costPerUnit: 150 },
                { name: 'угорь', costPerUnit: 180 },
                { name: 'кунжут', costPerUnit: 10 },
                { name: 'майонез', costPerUnit: 20 },
                { name: 'лосось копченый', costPerUnit: 220 },
                { name: 'креветка', costPerUnit: 170 },
                { name: 'тунец', costPerUnit: 190 },
                { name: 'крабовые палочки', costPerUnit: 70 },
                { name: 'перец', costPerUnit: 25 },
                { name: 'морковь', costPerUnit: 20 },
                { name: 'салат', costPerUnit: 15 },
                { name: 'соус спайси', costPerUnit: 35 },
                { name: 'соус унаги', costPerUnit: 40 },
                { name: 'крем-чиз', costPerUnit: 85 }
            ];
        }
    },

    // 3. Предсказать продажи для ролла
    predictSales: async (ingredients) => {
        try {
            const requestData = {
                ingredients: ingredients,

            };

            console.log('Predicting sales for:', ingredients);

            const response = await fetch(`${API_BASE}/predict/single`, {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            // Если бэкенд возвращает ошибку в поле errorMessage
            if (data.errorMessage) {
                throw new Error(data.errorMessage);
            }

            return data;

        } catch (error) {
            console.error('Prediction failed, returning mock:', error.message);

            // Мок данные для предсказания
            const baseSales = ingredients.includes('лосось') ? 80 : 60;
            const ingredientCount = ingredients.length;
            const randomFactor = 0.8 + Math.random() * 0.4;

            return {
                predictedSales: Math.round((baseSales + ingredientCount * 10) * randomFactor),
                confidenceScore: 0.65 + Math.random() * 0.25,
                estimatedCost: ingredientCount * 45 + Math.random() * 50,
                estimatedProfit: Math.round(200 + Math.random() * 150),
                modelVersion: '1.0',
                timestamp: new Date().toISOString()
            };
        }
    },

    // 4. Оптимизировать состав ролла
    optimizeRoll: async (constraints) => {
        try {
            console.log('Optimizing with constraints:', constraints);

            const requestData = {
                constraints: constraints,
                optimizationType: "profit_maximization"
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
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.errorMessage) {
                throw new Error(data.errorMessage);
            }

            return data;

        } catch (error) {
            console.error('Optimization failed, returning mock:', error.message);

            // Генерируем мок оптимизированные роллы
            const generateOptimizedRoll = (index, baseIngredients) => {
                const additionalIngredients = [
                    'авокадо', 'сыр', 'огурец', 'икра', 'кунжут',
                    'майонез', 'соус спайси', 'крабовые палочки'
                ];

                const extraCount = Math.min(2, additionalIngredients.length);
                const shuffled = [...additionalIngredients].sort(() => Math.random() - 0.5);
                const extraIngredients = shuffled.slice(0, extraCount);

                const allIngredients = [...baseIngredients, ...extraIngredients];
                const cost = allIngredients.length * 40 + Math.random() * 60;
                const sales = 70 + Math.random() * 30;
                const profit = Math.round(sales * 2.5 + Math.random() * 100);

                return {
                    name: `Оптимизированный ролл ${index + 1}`,
                    ingredients: allIngredients,
                    cost: Math.round(cost),
                    predictedSales: Math.round(sales),
                    estimatedProfit: profit,
                    profitMargin: parseFloat((profit / (cost * sales)).toFixed(2)),
                    score: 0.7 + Math.random() * 0.25,
                    explanation: index === 0
                        ? 'Оптимальное сочетание популярности и себестоимости'
                        : 'Высокая маржа при сохранении вкусовых качеств'
                };
            };

            const baseIngredients = constraints.mustInclude || ['рис', 'лосось'];
            const numResults = constraints.numResults || 3;

            const optimizedRolls = Array.from({ length: numResults }, (_, i) =>
                generateOptimizedRoll(i, baseIngredients)
            );

            // Сортируем по оценке
            optimizedRolls.sort((a, b) => b.score - a.score);

            return {
                optimizedRolls: optimizedRolls,
                status: 'success',
                timestamp: new Date().toISOString(),
                constraints: constraints,
                message: 'Generated mock optimization results'
            };
        }
    },

    // 5. Получить популярные ингредиенты
    getPopularIngredients: async (days = 30) => {
        try {
            console.log('Fetching popular ingredients for', days, 'days');

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
                return data.map((name, index) => ({
                    name: name,
                    popularity: 100 - index * 5,
                    frequency: Math.round(0.8 - index * 0.05)
                }));
            }

            return data;

        } catch (error) {
            console.error('Failed to fetch popular ingredients, using mock:', error.message);

            // Мок популярные ингредиенты
            return [
                { name: 'лосось', popularity: 95, frequency: 0.92 },
                { name: 'авокадо', popularity: 88, frequency: 0.85 },
                { name: 'рис', popularity: 100, frequency: 1.00 },
                { name: 'сыр', popularity: 75, frequency: 0.78 },
                { name: 'угорь', popularity: 65, frequency: 0.70 },
                { name: 'огурец', popularity: 70, frequency: 0.72 },
                { name: 'нори', popularity: 100, frequency: 1.00 },
                { name: 'икра', popularity: 60, frequency: 0.65 },
                { name: 'майонез', popularity: 55, frequency: 0.60 },
                { name: 'кунжут', popularity: 50, frequency: 0.58 }
            ];
        }
    },

    // 6. Получить данные для аналитики
    getAnalytics: async (timeRange = 'week') => {
        try {
            const response = await fetch(`${API_BASE}/data/analytics?timeRange=${timeRange}`, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json'
                }
            });

            if (response.ok) {
                return await response.json();
            }
        } catch (error) {
            console.warn('Analytics endpoint not available:', error.message);
        }

        // Мок данные для аналитики
        const now = new Date();
        const salesTrend = [];
        const days = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];

        for (let i = 0; i < 7; i++) {
            const base = 120 + Math.random() * 50;
            salesTrend.push({
                date: days[i],
                sales: Math.round(base),
                predicted: Math.round(base * (0.9 + Math.random() * 0.2))
            });
        }

        const topRolls = [
            { name: 'Калифорния', sales: 245, profit: 51200, margin: 42.3 },
            { name: 'Филадельфия', sales: 198, profit: 42300, margin: 38.7 },
            { name: 'Дракон', sales: 156, profit: 37800, margin: 45.1 },
            { name: 'Аляска', sales: 132, profit: 28400, margin: 36.8 },
            { name: 'Унаги', sales: 98, profit: 21000, margin: 40.2 }
        ];

        return {
            totalProfit: 154320,
            totalSales: 892,
            profitChange: 12.5,
            salesChange: 8.2,
            modelAccuracy: 0.87,
            topRolls: topRolls,
            salesTrend: salesTrend,
            insights: [
                {
                    type: 'opportunity',
                    title: 'Высокий спрос на авокадо',
                    description: 'Блюда с авокадо продаются на 25% лучше среднего'
                },
                {
                    type: 'warning',
                    title: 'Низкая маржа на угорь',
                    description: 'Стоимость угря выросла на 15%, рассмотрите замену'
                },
                {
                    type: 'insight',
                    title: 'Лучшее время для роллов с лососем',
                    description: 'Продажи увеличиваются на 30% в обеденное время'
                }
            ],
            timestamp: now.toISOString(),
            timeRange: timeRange
        };
    }
};

// Экспортируем также как отдельные функции для удобства
export const {
    checkConnection,
    getIngredients,
    predictSales,
    optimizeRoll,
    getPopularIngredients,
    getAnalytics
} = ApiClient;

export default ApiClient;