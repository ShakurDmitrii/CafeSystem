import ProductCard from "./ProductCard";
import styles from "../ProductsPage.module.css";

export default function ProductsCatalog({
    rows,
    totalCount,
    search,
    sortBy,
    favoritesOnly,
    loading,
    error,
    onSearchChange,
    onSortChange,
    onFavoritesChange,
    onRetry,
    onEdit
}) {
    return (
        <section className={styles.catalog} aria-labelledby="products-catalog-title">
            <div className={styles.catalogHeading}>
                <div>
                    <p className={styles.sectionKicker}>Номенклатура</p>
                    <h2 id="products-catalog-title">Каталог продуктов</h2>
                    <p>
                        Показано {rows.length} из {totalCount}. Поиск проверяет название продукта.
                    </p>
                </div>
                <button type="button" className={styles.refreshButton} onClick={onRetry}>
                    Обновить данные
                </button>
            </div>

            <div className={styles.filters} aria-label="Фильтры каталога">
                <label className={styles.searchField} htmlFor="products-search">
                    <span>Поиск</span>
                    <input
                        id="products-search"
                        name="productSearch"
                        type="search"
                        autoComplete="off"
                        value={search}
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Например, сыр…"
                    />
                </label>

                <label className={styles.sortField} htmlFor="products-sort">
                    <span>Сортировка</span>
                    <select
                        id="products-sort"
                        name="productSort"
                        autoComplete="off"
                        value={sortBy}
                        onChange={(event) => onSortChange(event.target.value)}
                    >
                        <option value="name_asc">Название: А–Я</option>
                        <option value="name_desc">Название: Я–А</option>
                        <option value="price_asc">Цена: сначала ниже</option>
                        <option value="price_desc">Цена: сначала выше</option>
                    </select>
                </label>

                <label className={styles.filterCheckbox}>
                    <input
                        type="checkbox"
                        checked={favoritesOnly}
                        onChange={(event) => onFavoritesChange(event.target.checked)}
                    />
                    <span>Только избранные</span>
                </label>
            </div>

            {error ? (
                <div className={styles.loadError} role="alert">
                    <div>
                        <strong>Не удалось загрузить каталог</strong>
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
                        <strong>Загружаем продукты…</strong>
                        <span>Собираем цены, единицы и поставщиков.</span>
                    </div>
                </div>
            ) : rows.length === 0 && !error ? (
                <div className={styles.emptyState}>
                    <span>Ничего не найдено</span>
                    <h3>{totalCount === 0 ? "Добавьте первый продукт" : "Измените условия поиска"}</h3>
                    <p>
                        {totalCount === 0
                            ? "Заполните карточку продукта и сохраните её в каталоге."
                            : "Очистите поиск или отключите фильтр избранного."}
                    </p>
                    {totalCount === 0 ? <a href="#product-editor">Перейти к форме</a> : null}
                </div>
            ) : (
                <div className={styles.productGrid}>
                    {rows.map((row) => (
                        <ProductCard key={row.id} row={row} onEdit={onEdit} />
                    ))}
                </div>
            )}
        </section>
    );
}
