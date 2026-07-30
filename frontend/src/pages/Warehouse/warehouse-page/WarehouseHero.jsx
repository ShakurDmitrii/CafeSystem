import styles from "../WarehousePage.module.css";

export default function WarehouseHero({ warehouses, warehouseProducts, positions, lowStock }) {
    const lanes = warehouses.slice(0, 4);
    return (
        <header className={styles.hero}>
            <div className={styles.heroCopy}>
                <p className={styles.heroEyebrow}>Складской контур</p>
                <h1>Запас виден до последней полки</h1>
                <p>
                    Принимайте товар, переводите между точками и фиксируйте списания
                    в одном рабочем маршруте.
                </p>
                <dl className={styles.heroStats}>
                    <div><dt>Складов</dt><dd>{warehouses.length}</dd></div>
                    <div><dt>Позиций</dt><dd>{positions}</dd></div>
                    <div><dt>Без остатка</dt><dd>{lowStock}</dd></div>
                </dl>
            </div>
            <div className={styles.stockLanes} aria-label="Сводка по складам">
                <span className={styles.lanesLabel}>Линии запаса</span>
                {lanes.length ? lanes.map((warehouse, index) => {
                    const count = (warehouseProducts[warehouse.warehouseId] ?? [])
                        .filter((product) => Number(product.quantityBase) > 0).length;
                    return (
                        <div className={styles.stockLane} key={warehouse.warehouseId}>
                            <span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
                            <div>
                                <strong>{warehouse.warehouseName}</strong>
                                <small>{count} активных позиций</small>
                            </div>
                            <i className={warehouse.isMain ? styles.mainDot : ""} aria-hidden="true" />
                        </div>
                    );
                }) : <p className={styles.lanesEmpty}>Создайте склад, чтобы открыть первую линию хранения.</p>}
            </div>
        </header>
    );
}
