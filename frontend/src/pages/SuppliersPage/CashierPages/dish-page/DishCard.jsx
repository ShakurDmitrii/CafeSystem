import { Link } from "react-router-dom";
import styles from "../DishPage.module.css";

export default function DishCard({
    dish,
    isDeleting,
    formatMoney,
    formatWeight,
    onEdit,
    onDelete
}) {
    return (
        <article className={styles.card}>
            <div className={styles.cardMedia}>
                {dish.imageUrl ? (
                    <img
                        src={dish.imageUrl}
                        alt={dish.dishName}
                        className={styles.cardImage}
                        width="480"
                        height="320"
                        loading="lazy"
                    />
                ) : (
                    <div className={styles.cardPlaceholder} aria-hidden="true">
                        <span>Без фото</span>
                    </div>
                )}
                <span className={styles.categoryBadge}>
                    {dish.categoryName || dish.category || "Без категории"}
                </span>
            </div>

            <div className={styles.cardHeader}>
                <div className={styles.cardName}>
                    <div className={styles.cardId}>Позиция #{dish.dishId}</div>
                    <h3 className={styles.cardTitle}>{dish.dishName}</h3>
                </div>
                <div className={styles.priceChip}>{formatMoney(dish.price)} ₽</div>
            </div>

            <dl className={styles.metricsRow}>
                <div className={styles.metric}>
                    <dt className={styles.metricLabel}>Вес</dt>
                    <dd>{formatWeight(dish.weight)} г</dd>
                </div>
                <div className={styles.metric}>
                    <dt className={styles.metricLabel}>Себестоимость</dt>
                    <dd>{formatMoney(dish.firstCost)} ₽</dd>
                </div>
            </dl>

            <div className={styles.cardActions}>
                <Link className={styles.techButton} to={`/tech-card/${dish.dishId}`}>
                    Открыть техкарту
                </Link>
                <button type="button" className={styles.secondaryButton} onClick={() => onEdit(dish)}>
                    Редактировать
                </button>
                <button
                    type="button"
                    className={styles.dangerButton}
                    onClick={() => onDelete(dish)}
                    disabled={isDeleting}
                >
                    {isDeleting ? "Удаляем…" : "Удалить"}
                </button>
            </div>
        </article>
    );
}
