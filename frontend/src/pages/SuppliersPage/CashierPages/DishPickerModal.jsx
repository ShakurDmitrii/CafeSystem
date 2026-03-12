import { useEffect, useMemo, useState } from "react";
import styles from "./DishPickerModal.module.css";

const normalize = (v) => String(v || "").trim();

const toCategoryName = (dish) =>
    dish?.categoryName || dish?.category || "Без категории";

const toDishId = (dish) => dish?.dishId ?? dish?.id;

const mapInitialItems = (items = []) =>
    items
        .filter(Boolean)
        .map((d) => ({
            dishId: toDishId(d),
            dishName: d.dishName || d.name || "Без названия",
            price: d.price ?? d.dishPrice ?? 0,
            qty: Number(d.qty || 0) || 0,
            imageUrl: d.imageUrl || null,
            categoryName: toCategoryName(d)
        }))
        .filter((d) => d.dishId != null && d.qty > 0);

export default function DishPickerModal({
    isOpen,
    onClose,
    dishes = [],
    categories = [],
    initialItems = [],
    onConfirm,
    disabled
}) {
    const [activeCategory, setActiveCategory] = useState("Все");
    const [cartItems, setCartItems] = useState([]);
    const [search, setSearch] = useState("");

    useEffect(() => {
        if (!isOpen) return;
        setCartItems(mapInitialItems(initialItems));
        setActiveCategory("Все");
        setSearch("");
    }, [isOpen, initialItems]);

    const categoryOptions = useMemo(() => {
        const fromApi = categories
            .map((c) => normalize(c?.name))
            .filter((x) => x);
        const fromDishes = dishes
            .map((d) => normalize(toCategoryName(d)))
            .filter((x) => x);
        const all = Array.from(new Set([...fromApi, ...fromDishes]));
        all.sort((a, b) => a.localeCompare(b, "ru"));
        return ["Все", ...all];
    }, [categories, dishes]);

    const dishesByCategory = useMemo(() => {
        const searchTerm = normalize(search).toLowerCase();
        const list = dishes.map((d) => ({
            ...d,
            dishId: toDishId(d),
            dishName: d.dishName || d.name || "Без названия",
            price: d.price ?? d.dishPrice ?? 0,
            categoryName: toCategoryName(d)
        }));
        const filteredByCategory = activeCategory === "Все"
            ? list
            : list.filter((d) => normalize(d.categoryName) === activeCategory);
        if (!searchTerm) return filteredByCategory;
        return filteredByCategory.filter((d) =>
            normalize(d.dishName).toLowerCase().includes(searchTerm)
        );
    }, [dishes, activeCategory, search]);

    const cartMap = useMemo(() => {
        const m = new Map();
        cartItems.forEach((i) => {
            m.set(i.dishId, i);
        });
        return m;
    }, [cartItems]);

    const addToCart = (dish) => {
        const dishId = toDishId(dish);
        if (dishId == null) return;
        setCartItems((prev) => {
            const existing = prev.find((p) => p.dishId === dishId);
            if (existing) {
                return prev.map((p) =>
                    p.dishId === dishId ? { ...p, qty: p.qty + 1 } : p
                );
            }
            return [
                ...prev,
                {
                    dishId,
                    dishName: dish.dishName || dish.name || "Без названия",
                    price: dish.price ?? dish.dishPrice ?? 0,
                    qty: 1,
                    imageUrl: dish.imageUrl || null,
                    categoryName: toCategoryName(dish)
                }
            ];
        });
    };

    const updateQty = (dishId, qty) => {
        const n = Number(qty);
        if (!Number.isFinite(n) || n <= 0) {
            setCartItems((prev) => prev.filter((p) => p.dishId !== dishId));
            return;
        }
        setCartItems((prev) =>
            prev.map((p) => (p.dishId === dishId ? { ...p, qty: n } : p))
        );
    };

    const removeItem = (dishId) => {
        setCartItems((prev) => prev.filter((p) => p.dishId !== dishId));
    };

    const total = cartItems.reduce(
        (sum, i) => sum + Number(i.price || 0) * Number(i.qty || 0),
        0
    );

    if (!isOpen) return null;

    return (
        <div className={styles.overlay}>
            <div className={styles.modal}>
                <header className={styles.header}>
                    <h2>Выбор блюд</h2>
                    <button className={styles.closeBtn} onClick={onClose}>
                        Закрыть
                    </button>
                </header>

                <div className={styles.body}>
                    <aside className={styles.categories}>
                        <div className={styles.sectionTitle}>Категории</div>
                        <div className={styles.categoryList}>
                            {categoryOptions.map((c) => (
                                <button
                                    key={c}
                                    type="button"
                                    className={`${styles.categoryItem} ${
                                        c === activeCategory ? styles.activeCategory : ""
                                    }`}
                                    onClick={() => setActiveCategory(c)}
                                >
                                    {c}
                                </button>
                            ))}
                        </div>
                    </aside>

                    <section className={styles.dishes}>
                        <div className={styles.sectionTitle}>
                            {activeCategory === "Все" ? "Все блюда" : activeCategory}
                        </div>
                        <input
                            type="text"
                            className={styles.searchInput}
                            placeholder="Поиск по названию..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                        />
                        <div className={styles.dishGrid}>
                            {dishesByCategory.map((d) => {
                                const inCart = cartMap.get(d.dishId);
                                return (
                                    <div key={d.dishId} className={styles.dishCard}>
                                        {d.imageUrl ? (
                                            <img
                                                src={d.imageUrl}
                                                alt={d.dishName}
                                                className={styles.dishImg}
                                            />
                                        ) : (
                                            <div className={styles.dishPlaceholder}>Нет фото</div>
                                        )}
                                        <div className={styles.dishName}>{d.dishName}</div>
                                        <div className={styles.dishMeta}>
                                            <span>{Number(d.price || 0).toFixed(2)} ₽</span>
                                            {d.categoryName && (
                                                <span className={styles.dishCategory}>{d.categoryName}</span>
                                            )}
                                        </div>
                                        <button
                                            className={styles.addBtn}
                                            type="button"
                                            onClick={() => addToCart(d)}
                                            disabled={disabled}
                                        >
                                            + Добавить {inCart ? `(${inCart.qty})` : ""}
                                        </button>
                                    </div>
                                );
                            })}
                        </div>
                    </section>

                    <aside className={styles.cart}>
                        <div className={styles.sectionTitle}>Корзина</div>
                        {cartItems.length === 0 ? (
                            <div className={styles.empty}>Нет выбранных блюд</div>
                        ) : (
                            <div className={styles.cartList}>
                                {cartItems.map((i) => (
                                    <div key={i.dishId} className={styles.cartItem}>
                                        <div className={styles.cartInfo}>
                                            <div className={styles.cartName}>{i.dishName}</div>
                                            <div className={styles.cartPrice}>
                                                {Number(i.price || 0).toFixed(2)} ₽
                                            </div>
                                        </div>
                                        <div className={styles.cartControls}>
                                            <button
                                                type="button"
                                                className={styles.qtyBtn}
                                                onClick={() => updateQty(i.dishId, i.qty - 1)}
                                                disabled={disabled}
                                            >
                                                −
                                            </button>
                                            <input
                                                type="number"
                                                min="1"
                                                className={styles.qtyInput}
                                                value={i.qty}
                                                onChange={(e) =>
                                                    updateQty(i.dishId, e.target.value)
                                                }
                                                disabled={disabled}
                                            />
                                            <button
                                                type="button"
                                                className={styles.qtyBtn}
                                                onClick={() => updateQty(i.dishId, i.qty + 1)}
                                                disabled={disabled}
                                            >
                                                +
                                            </button>
                                            <button
                                                type="button"
                                                className={styles.removeBtn}
                                                onClick={() => removeItem(i.dishId)}
                                                disabled={disabled}
                                            >
                                                Убрать
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <div className={styles.cartFooter}>
                            <div className={styles.total}>
                                Итого: {Number(total || 0).toFixed(2)} ₽
                            </div>
                            <button
                                className={styles.okBtn}
                                type="button"
                                onClick={() => onConfirm(cartItems)}
                                disabled={disabled}
                            >
                                ОК
                            </button>
                        </div>
                    </aside>
                </div>
            </div>
        </div>
    );
}
