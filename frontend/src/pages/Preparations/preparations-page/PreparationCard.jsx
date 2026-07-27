import { Link } from "react-router-dom";
import styles from "../PreparationsPage.module.css";

export default function PreparationCard({
    row,
    onProduce,
    onEdit,
    onDelete
}) {
    return (
        <article className={styles.preparationCard}>
            <header className={styles.cardHeader}>
                <div className={styles.cardIdentity}>
                    <span className={styles.cardNumber}>
                        #{String(row.id).padStart(2, "0")}
                    </span>
                    <div>
                        <span className={`${styles.recipeStatus} ${row.techCardCount > 0 ? styles.recipeReady : ""}`}>
                            {row.techCardCount > 0 ? "Состав заполнен" : "Нужен состав"}
                        </span>
                        <h3>{row.name}</h3>
                    </div>
                </div>
                <strong className={styles.stockTotal}>{row.totalStockLabel}</strong>
            </header>

            <dl className={styles.cardMetrics}>
                <div>
                    <dt>Выход партии</dt>
                    <dd>{row.outputLabel}</dd>
                </div>
                <div>
                    <dt>Себестоимость</dt>
                    <dd>{row.costLabel}</dd>
                </div>
                <div>
                    <dt>В составе</dt>
                    <dd>{row.techCardCount} поз.</dd>
                </div>
            </dl>

            <div className={styles.stockBlock}>
                <p>Остатки по складам</p>
                {row.stocks.length > 0 ? (
                    <ul className={styles.stockList}>
                        {row.stocks.map((stock) => (
                            <li key={`${row.id}-${stock.warehouseId}`}>
                                <span>{stock.warehouseName}</span>
                                <strong>{stock.quantityLabel}</strong>
                            </li>
                        ))}
                    </ul>
                ) : (
                    <p className={styles.emptyStock}>
                        Остатков пока нет. Выпустите первую партию на нужный склад.
                    </p>
                )}
            </div>

            <div className={styles.cardActions}>
                <Link
                    className={styles.secondaryButton}
                    to={`/preparation-tech-card/${row.id}`}
                >
                    Открыть техкарту
                </Link>
                <button
                    type="button"
                    className={styles.primaryButton}
                    onClick={() => onProduce(row.source)}
                >
                    Выпустить партию
                </button>
            </div>

            <div className={styles.cardManagement}>
                <button type="button" className={styles.textButton} onClick={() => onEdit(row.source)}>
                    Изменить
                </button>
                <button
                    type="button"
                    className={styles.dangerButton}
                    onClick={() => onDelete(row.source)}
                >
                    Удалить
                </button>
            </div>
        </article>
    );
}
