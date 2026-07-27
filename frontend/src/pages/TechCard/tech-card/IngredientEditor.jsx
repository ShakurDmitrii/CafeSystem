import styles from "../TechCardPage.module.css";

export default function IngredientEditor({
    selectedLabel,
    selectedIngredientType,
    selectedProductGroup,
    selectedPreparation,
    ingredientMeasureUnit,
    selectedIngredientId,
    weight,
    waste,
    editing,
    saving,
    error,
    onOpenPicker,
    onOpenCreateIngredient,
    onWeightChange,
    onWasteChange,
    onSubmit,
    onCancel
}) {
    const handleSubmit = (event) => {
        event.preventDefault();
        onSubmit();
    };

    return (
        <section className={styles.editorCard} id="ingredient-editor" aria-labelledby="ingredient-editor-title">
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>{editing ? "Редактирование строки" : "Новая строка"}</p>
                    <h2 id="ingredient-editor-title">{editing ? "Изменить ингредиент" : "Добавить в состав"}</h2>
                    <p>Выберите продукт или готовую заготовку и укажите количество на 1 выход.</p>
                </div>
                <span className={styles.stepBadge}>Шаг 1</span>
            </div>

            <form className={styles.editorForm} onSubmit={handleSubmit}>
                <div className={styles.ingredientChooser}>
                    <span className={styles.fieldLabel}>Ингредиент</span>
                    <button
                        type="button"
                        onClick={onOpenPicker}
                        className={styles.pickerButton}
                        aria-describedby={selectedIngredientId ? "selected-ingredient-meta" : undefined}
                    >
                        <span className={styles.pickerButtonValue}>
                            {selectedLabel || "Выбрать продукт или заготовку"}
                        </span>
                        <span className={styles.pickerButtonAction}>Открыть список</span>
                    </button>

                    {selectedIngredientId ? (
                        <div id="selected-ingredient-meta" className={styles.selectedMeta}>
                            {selectedIngredientType === "product" && selectedProductGroup
                                ? `Продукт · средняя цена ${Number(selectedProductGroup.averagePrice || 0).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽`
                                : `Заготовка · выход ${Number(selectedPreparation?.outputWeight || 0).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} г`}
                        </div>
                    ) : null}
                </div>

                <div className={styles.editorFields}>
                    <label className={styles.field} htmlFor="tech-card-weight">
                        <span>Количество, {ingredientMeasureUnit}</span>
                        <input
                            id="tech-card-weight"
                            name="ingredientQuantity"
                            type="number"
                            inputMode="decimal"
                            min="0"
                            step="0.01"
                            autoComplete="off"
                            value={weight}
                            onChange={(event) => onWeightChange(event.target.value)}
                            placeholder={`Например, 150 ${ingredientMeasureUnit}…`}
                            className={styles.input}
                            required
                        />
                    </label>

                    <label className={styles.field} htmlFor="tech-card-waste">
                        <span>Отход, %</span>
                        <input
                            id="tech-card-waste"
                            name="ingredientWaste"
                            type="number"
                            inputMode="decimal"
                            min="0"
                            max="100"
                            step="0.01"
                            autoComplete="off"
                            value={waste}
                            onChange={(event) => onWasteChange(event.target.value)}
                            placeholder="0"
                            className={styles.input}
                        />
                    </label>
                </div>

                {error ? <div className={styles.errorText} role="alert">{error}</div> : null}

                <div className={styles.editorActions}>
                    <button type="submit" className={styles.primaryButton} disabled={saving}>
                        {saving ? "Сохраняем…" : editing ? "Сохранить строку" : "Добавить в состав"}
                    </button>
                    {editing ? (
                        <button type="button" className={styles.secondaryButton} onClick={onCancel} disabled={saving}>
                            Отменить редактирование
                        </button>
                    ) : null}
                </div>
            </form>

            <div className={styles.createIngredientCallout}>
                <div>
                    <strong>Нужного продукта нет?</strong>
                    <span>Создайте его здесь, не покидая технологическую карту.</span>
                </div>
                <button type="button" className={styles.textButton} onClick={onOpenCreateIngredient}>
                    Создать продукт
                </button>
            </div>
        </section>
    );
}
