import PreparationCard from "./PreparationCard";
import styles from "../PreparationsPage.module.css";

export default function PreparationsList({
    rows,
    loading,
    error,
    onRetry,
    onProduce,
    onEdit,
    onDelete
}) {
    return (
        <section className={styles.listSection} aria-labelledby="preparations-list-title">
            <div className={styles.listHeading}>
                <div>
                    <p className={styles.sectionKicker}>Каталог производства</p>
                    <h2 id="preparations-list-title">Все заготовки</h2>
                    <p>Остатки, состав и выпуск партии находятся в одной карточке.</p>
                </div>
                <span className={styles.counterChip}>{rows.length} поз.</span>
            </div>

            {error ? (
                <div className={styles.loadError} role="alert">
                    <div>
                        <strong>Не удалось загрузить заготовки</strong>
                        <span>{error}</span>
                    </div>
                    <button type="button" className={styles.secondaryButton} onClick={onRetry}>
                        Повторить загрузку
                    </button>
                </div>
            ) : null}

            {loading ? (
                <div className={styles.loadingState} aria-live="polite">
                    <span className={styles.loadingMark} aria-hidden="true" />
                    <div>
                        <strong>Собираем данные цеха…</strong>
                        <span>Проверяем техкарты и остатки по складам.</span>
                    </div>
                </div>
            ) : rows.length === 0 && !error ? (
                <div className={styles.emptyState}>
                    <span className={styles.emptyStateLabel}>Каталог пуст</span>
                    <h3>Создайте первую заготовку</h3>
                    <p>После сохранения откроется техкарта для заполнения состава.</p>
                    <a href="#preparation-editor">Перейти к форме</a>
                </div>
            ) : (
                <div className={styles.cardsList}>
                    {rows.map((row) => (
                        <PreparationCard
                            key={row.id}
                            row={row}
                            onProduce={onProduce}
                            onEdit={onEdit}
                            onDelete={onDelete}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}
