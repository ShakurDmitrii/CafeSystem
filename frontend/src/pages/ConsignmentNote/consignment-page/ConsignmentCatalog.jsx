import ConsignmentCard from "./ConsignmentCard";
import styles from "../ConsignmentNotePage.module.css";

export default function ConsignmentCatalog({
    rows,
    totalCount,
    search,
    status,
    loading,
    error,
    calculatingId,
    onSearchChange,
    onStatusChange,
    onRetry,
    onOpen,
    onCalculate,
    onDelete
}) {
    return (
        <section className={styles.catalog} aria-labelledby="consignment-catalog-title">
            <div className={styles.catalogHeading}>
                <div>
                    <p className={styles.sectionKicker}>Журнал поставок</p>
                    <h2 id="consignment-catalog-title">Приходные накладные</h2>
                    <p>Показано {rows.length} из {totalCount} документов.</p>
                </div>
                <button type="button" className={styles.refreshButton} onClick={onRetry}>
                    Обновить данные
                </button>
            </div>

            <div className={styles.filters} aria-label="Фильтры накладных">
                <label className={styles.searchField} htmlFor="consignment-search">
                    <span>Поиск</span>
                    <input
                        id="consignment-search"
                        name="consignmentSearch"
                        type="search"
                        autoComplete="off"
                        value={search}
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Поставщик или номер…"
                    />
                </label>

                <label className={styles.statusField} htmlFor="consignment-status">
                    <span>Статус</span>
                    <select
                        id="consignment-status"
                        name="consignmentStatus"
                        autoComplete="off"
                        value={status}
                        onChange={(event) => onStatusChange(event.target.value)}
                    >
                        <option value="all">Все документы</option>
                        <option value="draft">Только черновики</option>
                        <option value="posted">Только проведённые</option>
                    </select>
                </label>
            </div>

            {error ? (
                <div className={styles.loadError} role="alert">
                    <div>
                        <strong>Не удалось загрузить накладные</strong>
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
                        <strong>Загружаем журнал…</strong>
                        <span>Сверяем поставщиков, документы и складские приходы.</span>
                    </div>
                </div>
            ) : rows.length === 0 && !error ? (
                <div className={styles.emptyState}>
                    <span>Журнал пуст</span>
                    <h3>{totalCount === 0 ? "Создайте первую накладную" : "Документы не найдены"}</h3>
                    <p>
                        {totalCount === 0
                            ? "Выберите поставщика и дату в форме рядом."
                            : "Измените поиск или покажите документы с другим статусом."}
                    </p>
                    {totalCount === 0 ? <a href="#consignment-editor">Перейти к форме</a> : null}
                </div>
            ) : (
                <div className={styles.noteGrid}>
                    {rows.map((row) => (
                        <ConsignmentCard
                            key={row.id}
                            row={row}
                            calculating={calculatingId === row.id}
                            onOpen={onOpen}
                            onCalculate={onCalculate}
                            onDelete={onDelete}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}
