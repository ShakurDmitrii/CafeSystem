import { useMemo, useState } from "react";
import DishCard from "./DishCard";
import styles from "../DishPage.module.css";

const categoryOf = (dish) => dish.categoryName || dish.category || "Без категории";

export default function DishList({
    dishes,
    loading,
    error,
    deletingDishId,
    formatMoney,
    formatWeight,
    onEdit,
    onDelete,
    onRetry,
    onCreate
}) {
    const [query, setQuery] = useState("");
    const [category, setCategory] = useState("");

    const categories = useMemo(
        () => [...new Set(dishes.map(categoryOf))].sort((a, b) => a.localeCompare(b, "ru")),
        [dishes]
    );
    const visibleDishes = useMemo(() => {
        const normalized = query.trim().toLowerCase();
        return dishes.filter((dish) =>
            (!category || categoryOf(dish) === category)
            && (!normalized || String(dish.dishName || "").toLowerCase().includes(normalized))
        );
    }, [dishes, query, category]);

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
                <span>
                    {onCreate
                        ? "Создайте первое блюдо кнопкой «Новое блюдо», затем заполните его техкарту."
                        : "Блюда появятся, когда владелец заполнит меню."}
                </span>
            </div>
        );
    } else if (visibleDishes.length === 0) {
        content = <div className={styles.emptyState}>По этому запросу блюд нет.</div>;
    } else {
        content = (
            <div className={styles.cardsGrid}>
                {visibleDishes.map((dish) => (
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
            <div className={styles.listToolbar}>
                <h2 id="dish-list-title">Блюда</h2>
                <input
                    type="search"
                    className={styles.input}
                    placeholder="Поиск по названию…"
                    aria-label="Поиск блюда по названию"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                />
                <select
                    className={styles.select}
                    aria-label="Фильтр по категории"
                    value={category}
                    onChange={(event) => setCategory(event.target.value)}
                >
                    <option value="">Все категории</option>
                    {categories.map((name) => (
                        <option key={name} value={name}>{name}</option>
                    ))}
                </select>
                {onCreate && (
                    <button type="button" className={styles.primaryButton} onClick={onCreate}>
                        + Новое блюдо
                    </button>
                )}
            </div>
            {content}
        </section>
    );
}
