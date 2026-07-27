import SupplierProductCard from "./SupplierProductCard";
import styles from "../SuppliersProductPage.module.css";

export default function SupplierAssortmentCatalog({
    rows,
    total,
    search,
    sortBy,
    favoritesOnly,
    loading,
    error,
    onSearchChange,
    onSortChange,
    onFavoritesChange,
    onRetry,
    onEdit,
    onClearFilters
}) {
    const filtersActive = Boolean(search || favoritesOnly || sortBy !== "name_asc");

    return (
        <section className={styles.catalogCard} aria-labelledby="supplier-assortment-title">
            <div className={styles.catalogHeading}>
                <div>
                    <p className={styles.sectionKicker}>Прайс-лист</p>
                    <h2 id="supplier-assortment-title">Ассортимент поставщика</h2>
                    <p className={styles.catalogSummary}>
                        Показано {rows.length} из {total}. Цена и пересчёт видны
                        без открытия карточки.
                    </p>
                </div>
                <button type="button" className={styles.refreshButton} onClick={onRetry}>
                    Обновить данные
                </button>
            </div>

            <div className={styles.filters} aria-label="Фильтры ассортимента">
                <label className={styles.field} htmlFor="supplier-product-search">
                    <span>Поиск</span>
                    <input
                        id="supplier-product-search"
                        name="supplierProductSearch"
                        type="search"
                        autoComplete="off"
                        value={search}
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Например, сыр…"
                        className={styles.searchInput}
                    />
                </label>
                <label className={styles.field} htmlFor="supplier-product-sort">
                    <span>Сортировка</span>
                    <select
                        id="supplier-product-sort"
                        name="supplierProductSort"
                        autoComplete="off"
                        value={sortBy}
                        onChange={(event) => onSortChange(event.target.value)}
                        className={styles.select}
                    >
                        <option value="name_asc">Название: А–Я</option>
                        <option value="name_desc">Название: Я–А</option>
                        <option value="price_asc">Цена: сначала ниже</option>
                        <option value="price_desc">Цена: сначала выше</option>
                    </select>
                </label>
                <label className={styles.favoriteFilter}>
                    <input
                        type="checkbox"
                        name="supplierFavoritesOnly"
                        checked={favoritesOnly}
                        onChange={(event) => onFavoritesChange(event.target.checked)}
                    />
                    <span>Только избранные</span>
                </label>
            </div>

            {error ? (
                <div className={styles.pageError} role="alert">
                    {error}
                    <button type="button" className={styles.clearFiltersButton} onClick={onRetry}>
                        Повторить
                    </button>
                </div>
            ) : null}

            <div className={styles.productList}>
                {loading ? (
                    <div className={styles.loadingState} role="status">
                        <span className={styles.loadingMark} aria-hidden="true">···</span>
                        <p>Загружаем ассортимент…</p>
                    </div>
                ) : rows.length > 0 ? (
                    rows.map((row) => (
                        <SupplierProductCard key={row.id} row={row} onEdit={onEdit} />
                    ))
                ) : (
                    <div className={styles.emptyState}>
                        <span className={styles.emptyMark} aria-hidden="true">
                            {filtersActive ? "0" : "+"}
                        </span>
                        <h3>{filtersActive ? "Подходящих позиций нет" : "Ассортимент пока пуст"}</h3>
                        <p>
                            {filtersActive
                                ? "Измените запрос или сбросьте фильтры, чтобы снова увидеть товары."
                                : "Добавьте первый продукт в редакторе. Он сразу появится в прайс-листе поставщика."}
                        </p>
                        {filtersActive ? (
                            <button
                                type="button"
                                className={styles.clearFiltersButton}
                                onClick={onClearFilters}
                            >
                                Сбросить фильтры
                            </button>
                        ) : null}
                    </div>
                )}
            </div>
        </section>
    );
}
