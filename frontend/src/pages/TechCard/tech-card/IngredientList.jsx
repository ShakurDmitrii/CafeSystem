import styles from "../TechCardPage.module.css";

export default function IngredientList({ rows, containsPreparations, onEdit, onDelete }) {
    return (
        <section className={styles.compositionCard} aria-labelledby="composition-title">
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>Состав и расчёт</p>
                    <h2 id="composition-title">Ингредиенты</h2>
                    <p>Количество, отход и стоимость каждой строки рассчитываются отдельно.</p>
                </div>
                <span className={styles.stepBadge}>Шаг 2 · {rows.length}</span>
            </div>

            {rows.length > 0 ? (
                <ol className={styles.ingredientsList}>
                    {rows.map((row, index) => (
                        <li key={row.id} className={styles.ingredientItem}>
                            <article>
                                <div className={styles.ingredientHeader}>
                                    <span className={styles.rowNumber}>{String(index + 1).padStart(2, "0")}</span>
                                    <div className={styles.ingredientName}>
                                        <span className={styles.ingredientBadge}>{row.typeLabel}</span>
                                        <h3>{row.name}</h3>
                                    </div>
                                    <strong className={styles.itemCost}>{row.costLabel}</strong>
                                </div>

                                <dl className={styles.ingredientMetrics}>
                                    <div>
                                        <dt>Количество</dt>
                                        <dd>{row.quantityLabel}</dd>
                                    </div>
                                    <div>
                                        <dt>Отход</dt>
                                        <dd>{row.wasteLabel}</dd>
                                    </div>
                                    <div>
                                        <dt>Цена единицы</dt>
                                        <dd>{row.unitCostLabel}</dd>
                                    </div>
                                    {row.outputLabel ? (
                                        <div>
                                            <dt>Выход партии</dt>
                                            <dd>{row.outputLabel}</dd>
                                        </div>
                                    ) : null}
                                </dl>

                                <div className={styles.ingredientActions}>
                                    <button type="button" className={styles.secondaryButton} onClick={() => onEdit(row.source)}>
                                        Редактировать
                                    </button>
                                    <button type="button" className={styles.dangerButton} onClick={() => onDelete(row.source)}>
                                        Удалить
                                    </button>
                                </div>
                            </article>
                        </li>
                    ))}
                </ol>
            ) : (
                <div className={styles.emptyState}>
                    <strong>Состав пока пуст</strong>
                    <span>Выберите первый ингредиент в форме рядом.</span>
                    <a href="#ingredient-editor">Добавить ингредиент</a>
                </div>
            )}

            {containsPreparations ? (
                <div className={styles.hintText}>
                    Вложенные заготовки включены в расчёт по себестоимости их собственной техкарты.
                </div>
            ) : null}
        </section>
    );
}
