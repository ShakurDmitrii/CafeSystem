import React, { useState, useEffect } from 'react';
import Select from 'react-select'; // Эта строка должна быть наверху
import { ApiClient } from './api';
import styles from './mlStyles.module.css';

export default function IngredientSelector({ selected, onChange }) {
    const [availableIngredients, setAvailableIngredients] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        loadIngredients();
    }, []);

    const loadIngredients = async () => {
        try {
            setLoading(true);
            const ingredients = await ApiClient.getIngredients();

            // Преобразуем в формат для react-select
            const options = ingredients.map(ing => ({
                value: ing.name,
                label: `${ing.name} ${ing.costPerUnit ? `(${ing.costPerUnit}₽)` : ''}`,
                data: ing
            }));

            setAvailableIngredients(options);
            setError(null);
        } catch (err) {
            setError(`Ошибка загрузки: ${err.message}`);
            console.error('Failed to load ingredients:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleSelectChange = (selectedOptions) => {
        const values = (selectedOptions || []).map(opt => opt.value);
        onChange(values);
    };

    const selectedOptions = selected.map(value =>
        availableIngredients.find(opt => opt.value === value)
    ).filter(Boolean);

    if (loading) {
        return <div className="loading">Загрузка ингредиентов...</div>;
    }

    if (error) {
        return (
            <div className="error">
                <p>{error}</p>
                <button onClick={loadIngredients}>Повторить</button>
            </div>
        );
    }

    return (
        <div className={styles.ingredientSelector}>
            <h3 className={styles.selectLabel}>Выберите ингредиенты:</h3>

            {/* Используем react-select */}
            <Select
                isMulti
                options={availableIngredients}
                value={selectedOptions}
                onChange={handleSelectChange}
                className={styles.select}
                classNamePrefix="select"
                placeholder="Выберите ингредиенты..."
                noOptionsMessage={() => "Нет доступных ингредиентов"}
            />

            {/* Показываем выбранные ингредиенты списком */}
            {selected.length > 0 && (
                <div className={styles.selectedList}>
                    <h4>Выбранные ингредиенты:</h4>
                    <ul className={styles.ingredientList}>
                        {selected.map((ingredient, index) => (
                            <li key={index} className={styles.ingredientItem}>
                                <span className={styles.ingredientName}>{ingredient}</span>
                                <button
                                    type="button"
                                    className={styles.removeButton}
                                    onClick={() => {
                                        const newIngredients = selected.filter((_, i) => i !== index);
                                        onChange(newIngredients);
                                    }}
                                >
                                    ×
                                </button>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}