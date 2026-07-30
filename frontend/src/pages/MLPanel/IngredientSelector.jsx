import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Select from 'react-select';
import { ApiClient } from './api';
import { formatCurrency } from './formatters';
import styles from './mlStyles.module.css';

export default function IngredientSelector({ selected, onChange }) {
    const [availableIngredients, setAvailableIngredients] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadIngredients = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const ingredients = await ApiClient.getIngredients();
            setAvailableIngredients(ingredients.map((ingredient) => ({
                value: ingredient.name,
                label: ingredient.costPerUnit
                    ? `${ingredient.name} · ${formatCurrency(ingredient.costPerUnit)}`
                    : ingredient.name
            })));
        } catch (err) {
            setError(err.message || 'Не удалось загрузить ингредиенты.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadIngredients();
    }, [loadIngredients]);

    const selectedOptions = useMemo(() => selected.map((value) => (
        availableIngredients.find((option) => option.value === value) || { value, label: value }
    )), [availableIngredients, selected]);

    if (loading) {
        return <div className={styles.inlineState} role="status">Загружаем ингредиенты…</div>;
    }

    if (error) {
        return (
            <div className={styles.inlineError} role="alert">
                <span>{error}</span>
                <button type="button" onClick={loadIngredients}>Повторить</button>
            </div>
        );
    }

    return (
        <div className={styles.ingredientSelector}>
            <label className={styles.selectLabel} htmlFor="ml-ingredients">
                Ингредиенты
                <span>{selected.length} выбрано</span>
            </label>
            <Select
                inputId="ml-ingredients"
                instanceId="ml-ingredients"
                isMulti
                options={availableIngredients}
                value={selectedOptions}
                onChange={(options) => onChange((options || []).map((option) => option.value))}
                className={styles.select}
                classNamePrefix="ml-select"
                placeholder="Начните вводить название…"
                noOptionsMessage={() => 'Ингредиенты не найдены'}
                loadingMessage={() => 'Загрузка…'}
            />
            <div className={styles.selectionHint} aria-live="polite">
                {selected.length < 2
                    ? `Добавьте ещё ${2 - selected.length}`
                    : 'Состав готов к расчёту'}
            </div>
        </div>
    );
}
