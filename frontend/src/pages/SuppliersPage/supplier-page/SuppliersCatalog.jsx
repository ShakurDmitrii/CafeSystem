import SupplierCard from "./SupplierCard";
import styles from "../SuppliersPage.module.css";

export default function SuppliersCatalog({
    suppliers,
    total,
    search,
    sortBy,
    loading,
    error,
    onSearchChange,
    onSortChange,
    onRetry,
    onEdit,
    onDelete,
    onClearSearch
}) {
    return (
        <section className={styles.catalogCard} aria-labelledby="suppliers-catalog-title">
            <div className={styles.catalogHeading}>
                <div>
                    <p className={styles.sectionKicker}>Партнёры</p>
                    <h2 id="suppliers-catalog-title">Каталог поставщиков</h2>
                    <p className={styles.catalogSummary}>
                        Показано {suppliers.length} из {total}. Поиск проверяет
                        название, контакт и номер карточки.
                    </p>
                </div>
                <button type="button" className={styles.refreshButton} onClick={onRetry}>
                    Обновить данные
                </button>
            </div>

            <div className={styles.filters} aria-label="Фильтры поставщиков">
                <label className={styles.field} htmlFor="supplier-search">
                    <span>Поиск</span>
                    <input
                        id="supplier-search"
                        name="supplierSearch"
                        type="search"
                        autoComplete="off"
                        value={search}
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Например, Океан или телефон…"
                        className={styles.searchInput}
                    />
                </label>
                <label className={styles.field} htmlFor="supplier-sort">
                    <span>Сортировка</span>
                    <select
                        id="supplier-sort"
                        name="supplierSort"
                        autoComplete="off"
                        value={sortBy}
                        onChange={(event) => onSortChange(event.target.value)}
                        className={styles.select}
                    >
                        <option value="name_asc">Название: А–Я</option>
                        <option value="name_desc">Название: Я–А</option>
                        <option value="id_desc">Сначала новые</option>
                    </select>
                </label>
            </div>

            {error ? (
                <div className={styles.pageError} role="alert">
                    <span>{error}</span>
                    <button type="button" className={styles.dismissError} onClick={onRetry}>
                        Повторить
                    </button>
                </div>
            ) : null}

            <div className={styles.supplierList}>
                {loading ? (
                    <div className={styles.loadingState} role="status">
                        <span className={styles.loadingMark} aria-hidden="true">···</span>
                        <p>Загружаем книгу поставщиков…</p>
                    </div>
                ) : suppliers.length > 0 ? (
                    suppliers.map((supplier) => (
                        <SupplierCard
                            key={supplier.id}
                            supplier={supplier}
                            onEdit={onEdit}
                            onDelete={onDelete}
                        />
                    ))
                ) : (
                    <div className={styles.emptyState}>
                        <span className={styles.emptyMark} aria-hidden="true">
                            {search ? "0" : "+"}
                        </span>
                        <h3>{search ? "Совпадений нет" : "Поставщиков пока нет"}</h3>
                        <p>
                            {search
                                ? `По запросу «${search}» ничего не найдено. Измените запрос или очистите поиск.`
                                : "Добавьте первого партнёра в редакторе, чтобы собрать ассортимент и оформлять поставки."}
                        </p>
                        {search ? (
                            <button
                                type="button"
                                className={styles.clearSearchButton}
                                onClick={onClearSearch}
                            >
                                Очистить поиск
                            </button>
                        ) : null}
                    </div>
                )}
            </div>
        </section>
    );
}
