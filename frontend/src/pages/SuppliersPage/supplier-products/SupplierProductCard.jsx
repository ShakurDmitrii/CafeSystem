import styles from "../SuppliersProductPage.module.css";

export default function SupplierProductCard({ row, onEdit }) {
    return (
        <article className={styles.productCard}>
            <header className={styles.cardHeader}>
                <div className={styles.productImage}>
                    {row.imageUrl ? (
                        <img
                            src={row.imageUrl}
                            alt={row.name}
                            width="68"
                            height="68"
                            loading="lazy"
                        />
                    ) : (
                        <span aria-hidden="true">{row.initial}</span>
                    )}
                </div>
                <div className={styles.cardIdentity}>
                    <div className={styles.cardMeta}>
                        <span className={styles.cardNumber}>#{row.id}</span>
                        {row.favorite ? (
                            <span className={styles.favoriteBadge}>Избранное</span>
                        ) : null}
                    </div>
                    <h3>{row.name}</h3>
                </div>
            </header>

            <dl className={styles.priceLedger}>
                <div>
                    <dt>Закупочная цена</dt>
                    <dd>{row.purchasePriceLabel}</dd>
                </div>
                <div>
                    <dt>{row.hasStockPrice ? "Средняя по складу" : "Цена базовой единицы"}</dt>
                    <dd>{row.basePriceLabel}</dd>
                </div>
            </dl>

            <div className={styles.cardDetails}>
                <div>
                    <span>Пересчёт</span>
                    <strong>{row.conversionLabel}</strong>
                </div>
                <div>
                    <span>Отход</span>
                    <strong>{row.wasteLabel}</strong>
                </div>
            </div>

            <button
                type="button"
                className={styles.editButton}
                onClick={() => onEdit(row.source)}
            >
                Изменить карточку
            </button>
        </article>
    );
}
