import { Link } from "react-router-dom";
import styles from "../HomePage.module.css";

const quantityFormatter = new Intl.NumberFormat("ru-RU", {
    maximumFractionDigits: 2
});

export default function StockAlertsPanel({ stocks, visible }) {
    if (!visible) {
        return (
            <article className={styles.panel}>
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.sectionKicker}>Доступ по роли</p>
                        <h2>Остатки на складе</h2>
                    </div>
                </div>
                <div className={styles.emptyState}>
                    <strong>Раздел доступен владельцу</strong>
                    <span>Сотрудник видит только данные, нужные для текущей смены.</span>
                </div>
            </article>
        );
    }

    return (
        <article className={styles.panel}>
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>Требуют внимания</p>
                    <h2>Низкие остатки</h2>
                </div>
                <Link to="/warehouse" className={styles.sectionLink}>Все склады</Link>
            </div>

            {stocks.length === 0 ? (
                <div className={`${styles.emptyState} ${styles.emptyStateSuccess}`}>
                    <strong>Запасов достаточно</strong>
                    <span>Критичных позиций сейчас нет.</span>
                </div>
            ) : (
                <ul className={styles.stockList}>
                    {stocks.map((stock) => (
                        <li
                            key={stock.key || `${stock.productId}-${stock.warehouseId || "warehouse"}`}
                            className={styles.stockItem}
                        >
                            <span
                                className={`${styles.stockStatus} ${
                                    stock.level === "critical"
                                        ? styles.stockStatusCritical
                                        : styles.stockStatusWarning
                                }`}
                                aria-hidden="true"
                            />
                            <span className={styles.stockMeta}>
                                <strong>{stock.productName}</strong>
                                <small>
                                    {stock.isMain ? "Главный склад" : stock.warehouseName}
                                </small>
                            </span>
                            <strong className={styles.itemValue}>
                                {quantityFormatter.format(stock.qty)}&nbsp;{stock.unit}
                            </strong>
                        </li>
                    ))}
                </ul>
            )}
        </article>
    );
}
