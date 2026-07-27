import styles from "../HomePage.module.css";

export default function TopDishesPanel({ dishes }) {
    return (
        <article className={styles.panel}>
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>Спрос сегодня</p>
                    <h2>Популярные блюда</h2>
                </div>
                <span className={styles.panelCount}>{dishes.length}</span>
            </div>

            {dishes.length === 0 ? (
                <div className={styles.emptyState}>
                    <strong>Продаж пока нет</strong>
                    <span>Здесь появятся блюда после первых заказов.</span>
                </div>
            ) : (
                <ol className={styles.rankedList}>
                    {dishes.map((dish, index) => (
                        <li key={dish.name} className={styles.rankedItem}>
                            <span className={styles.rank}>{String(index + 1).padStart(2, "0")}</span>
                            <span className={styles.itemName}>{dish.name}</span>
                            <strong className={styles.itemValue}>{dish.qty}&nbsp;шт.</strong>
                        </li>
                    ))}
                </ol>
            )}
        </article>
    );
}
