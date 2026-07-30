import styles from "../Movement.module.css";

export default function MovementFilters({ warehouses, uniqueTypes, values, onChange, onReset }) {
    return (
        <div className={styles.filters}>
            <label>
                <span>Склад</span>
                <select name="movementWarehouse" value={values.filterWarehouse}
                    onChange={(event) => onChange.filterWarehouse(event.target.value)}>
                    <option value="">Все склады</option>
                    {warehouses.map((warehouse) => (
                        <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.warehouseName}</option>
                    ))}
                </select>
            </label>
            <label>
                <span>Поиск по товару</span>
                <input name="movementSearch" type="search" autoComplete="off" placeholder="Название товара…"
                    value={values.filterProductName} onChange={(event) => onChange.filterProductName(event.target.value)} />
            </label>
            <label>
                <span>ID товара</span>
                <input name="movementProductId" inputMode="numeric" autoComplete="off" placeholder="Например, 42…"
                    value={values.filterProduct} onChange={(event) => onChange.filterProduct(event.target.value)} />
            </label>
            <label>
                <span>Тип операции</span>
                <select name="movementType" value={values.filterType}
                    onChange={(event) => onChange.filterType(event.target.value)}>
                    <option value="">Все типы</option>
                    {uniqueTypes.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
            </label>
            <label>
                <span>Товар</span>
                <select name="movementProductSort" value={values.sortByProductName}
                    onChange={(event) => onChange.sortByProductName(event.target.value)}>
                    <option value="">Без сортировки</option>
                    <option value="asc">От А до Я</option>
                    <option value="desc">От Я до А</option>
                </select>
            </label>
            <label>
                <span>Дата</span>
                <select name="movementDateSort" value={values.sortByDate}
                    onChange={(event) => onChange.sortByDate(event.target.value)}>
                    <option value="desc">Новые сверху</option>
                    <option value="asc">Старые сверху</option>
                    <option value="">Без сортировки</option>
                </select>
            </label>
            <button className={styles.resetButton} type="button" onClick={onReset}>Сбросить фильтры</button>
        </div>
    );
}
