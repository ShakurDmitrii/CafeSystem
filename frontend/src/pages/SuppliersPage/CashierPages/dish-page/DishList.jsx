import DishCard from "./DishCard";
import styles from "../DishPage.module.css";

export default function DishList({
    dishes,
    loading,
    error,
    deletingDishId,
    formatMoney,
    formatWeight,
    onEdit,
    onDelete,
    onRetry
}) {
    let content;

    if (loading) {
        content = <div className={styles.emptyState} role="status">Загружаем блюда…</div>;
    } else if (error) {
        content = (
            <div className={styles.errorState} role="alert">
                <strong>Не удалось загрузить меню</strong>
                <span>{error}</span>
                <button type="button" className={styles.secondaryButton} onClick={onRetry}>
                    Попробовать снова
                </button>
            </div>
        );
    } else if (dishes.length === 0) {
        content = (
            <div className={styles.emptyState}>
                <strong>Меню пока пустое</strong>
                <span>Создайте первое блюдо в форме выше, затем заполните его техкарту.</span>
                <a className={styles.inlineLink} href="#dish-create">Добавить блюдо</a>
            </div>
        );
    } else {
        content = (
            <div className={styles.cardsGrid}>
                {dishes.map((dish) => (
                    <DishCard
                        key={dish.dishId}
                        dish={dish}
                        isDeleting={deletingDishId === dish.dishId}
                        formatMoney={formatMoney}
                        formatWeight={formatWeight}
                        onEdit={onEdit}
                        onDelete={onDelete}
                    />
                ))}
            </div>
        );
    }

    return (
        <section className={styles.listSection} aria-labelledby="dish-list-title">
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>Текущее меню</p>
                    <h2 id="dish-list-title">Все блюда</h2>
                    <p>Проверьте цену и себестоимость или откройте техкарту для состава.</p>
                </div>
            </div>
            {content}
        </section>
    );
}
