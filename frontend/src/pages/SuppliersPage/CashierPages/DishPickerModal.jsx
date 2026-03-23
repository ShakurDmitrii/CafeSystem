import { useEffect, useMemo, useState } from "react";
import styles from "./DishPickerModal.module.css";

const normalize = (v) => String(v || "").trim();

const toItemType = (item) =>
    item?.itemType === "set" || item?.setId != null ? "set" : "dish";

const toCategoryName = (dish) =>
    toItemType(dish) === "set"
        ? "Наборы"
        : dish?.categoryName || dish?.category || "Без категории";

const toDishId = (dish) => dish?.dishId ?? dish?.id;

const toSetId = (item) => item?.setId;

const toDisplayName = (item) =>
    toItemType(item) === "set"
        ? item?.setName || item?.dishName || item?.name || "Без названия"
        : item?.dishName || item?.name || "Без названия";

const toPrice = (item) => item?.price ?? item?.dishPrice ?? 0;

const toEntityKey = (item) => {
    const itemType = toItemType(item);
    const rawId = itemType === "set" ? toSetId(item) : toDishId(item);
    if (rawId == null) return null;
    return `${itemType}-${rawId}`;
};

const toSetItems = (item) =>
    Array.isArray(item?.setItems)
        ? item.setItems
        : Array.isArray(item?.items)
            ? item.items
            : [];

const mapInitialItems = (items = []) =>
    items
        .filter(Boolean)
        .map((item) => {
            const itemType = toItemType(item);
            const entityKey = toEntityKey(item);
            return {
                itemType,
                entityKey,
                dishId: itemType === "dish" ? toDishId(item) : null,
                setId: itemType === "set" ? toSetId(item) : null,
                dishName: toDisplayName(item),
                price: toPrice(item),
                qty: Number(item.qty || 0) || 0,
                imageUrl: item.imageUrl || null,
                categoryName: toCategoryName(item),
                setItems: toSetItems(item)
            };
        })
        .filter((item) => item.entityKey && item.qty > 0);

export default function DishPickerModal({
    isOpen,
    onClose,
    dishes = [],
    dishSets = [],
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

    const catalogItems = useMemo(() => {
        const dishItems = dishes.map((dish) => ({
            ...dish,
            itemType: "dish",
            entityKey: toEntityKey(dish),
            dishId: toDishId(dish),
            dishName: toDisplayName(dish),
            price: toPrice(dish),
            categoryName: toCategoryName(dish)
        }));
        const setItems = dishSets.map((dishSet) => ({
            ...dishSet,
            itemType: "set",
            entityKey: toEntityKey(dishSet),
            setId: toSetId(dishSet),
            dishName: toDisplayName(dishSet),
            price: toPrice(dishSet),
            categoryName: toCategoryName(dishSet),
            setItems: toSetItems(dishSet)
        }));
        return [...dishItems, ...setItems].filter((item) => item.entityKey);
    }, [dishes, dishSets]);

    const categoryOptions = useMemo(() => {
        const fromApi = categories
            .map((c) => normalize(c?.name))
            .filter((x) => x);
        const fromCatalog = catalogItems
            .map((d) => normalize(toCategoryName(d)))
            .filter((x) => x);
        const all = Array.from(new Set([...fromApi, ...fromCatalog]));
        all.sort((a, b) => a.localeCompare(b, "ru"));
        return ["Все", ...all];
    }, [categories, catalogItems]);

    const dishesByCategory = useMemo(() => {
        const searchTerm = normalize(search).toLowerCase();
        const filteredByCategory = activeCategory === "Все"
            ? catalogItems
            : catalogItems.filter((d) => normalize(d.categoryName) === activeCategory);
        if (!searchTerm) return filteredByCategory;
        return filteredByCategory.filter((d) =>
            normalize(d.dishName).toLowerCase().includes(searchTerm)
        );
    }, [catalogItems, activeCategory, search]);

    const cartMap = useMemo(() => {
        const m = new Map();
        cartItems.forEach((i) => {
            m.set(i.entityKey, i);
        });
        return m;
    }, [cartItems]);

    const addToCart = (dish) => {
        const entityKey = toEntityKey(dish);
        if (!entityKey) return;
        const itemType = toItemType(dish);
        setCartItems((prev) => {
            const existing = prev.find((p) => p.entityKey === entityKey);
            if (existing) {
                return prev.map((p) =>
                    p.entityKey === entityKey ? { ...p, qty: p.qty + 1 } : p
                );
            }
            return [
                ...prev,
                {
                    itemType,
                    entityKey,
                    dishId: itemType === "dish" ? toDishId(dish) : null,
                    setId: itemType === "set" ? toSetId(dish) : null,
                    dishName: toDisplayName(dish),
                    price: toPrice(dish),
                    qty: 1,
                    imageUrl: dish.imageUrl || null,
                    categoryName: toCategoryName(dish),
                    setItems: toSetItems(dish)
                }
            ];
        });
    };

    const updateQty = (entityKey, qty) => {
        const n = Number(qty);
        if (!Number.isFinite(n) || n <= 0) {
            setCartItems((prev) => prev.filter((p) => p.entityKey !== entityKey));
            return;
        }
        setCartItems((prev) =>
            prev.map((p) => (p.entityKey === entityKey ? { ...p, qty: n } : p))
        );
    };

    const removeItem = (entityKey) => {
        setCartItems((prev) => prev.filter((p) => p.entityKey !== entityKey));
    };

    const total = cartItems.reduce(
        (sum, i) => sum + Number(i.price || 0) * Number(i.qty || 0),
        0
    );

    const handleConfirm = async () => {
        await Promise.resolve(onConfirm?.(cartItems));
        onClose?.();
    };

    if (!isOpen) return null;

    return (
        <div className={styles.overlay}>
            <div className={styles.modal}>
                <header className={styles.header}>
                    <h2>Выбор позиций</h2>
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
                            {activeCategory === "Все" ? "Все позиции" : activeCategory}
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
                                const inCart = cartMap.get(d.entityKey);
                                const isSet = d.itemType === "set";
                                const setSummary = isSet
                                    ? toSetItems(d)
                                        .map((item) => `${item.dishName} x${item.qty || 1}`)
                                        .join(", ")
                                    : "";
                                return (
                                    <div key={d.entityKey} className={styles.dishCard}>
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
                                            <span className={styles.dishMetaRight}>
                                                <span className={styles.typeBadge}>
                                                    {isSet ? "Набор" : "Блюдо"}
                                                </span>
                                                {d.categoryName && (
                                                    <span className={styles.dishCategory}>{d.categoryName}</span>
                                                )}
                                            </span>
                                        </div>
                                        {isSet && setSummary && (
                                            <div className={styles.setSummary}>{setSummary}</div>
                                        )}
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
                            <div className={styles.empty}>Нет выбранных позиций</div>
                        ) : (
                            <div className={styles.cartList}>
                                {cartItems.map((i) => (
                                    <div key={i.entityKey} className={styles.cartItem}>
                                        <div className={styles.cartInfo}>
                                            <div className={styles.cartNameWrap}>
                                                <div className={styles.cartName}>{i.dishName}</div>
                                                <span className={styles.typeBadge}>
                                                    {i.itemType === "set" ? "Набор" : "Блюдо"}
                                                </span>
                                            </div>
                                            <div className={styles.cartPrice}>
                                                {Number(i.price || 0).toFixed(2)} ₽
                                            </div>
                                        </div>
                                        <div className={styles.cartControls}>
                                            <button
                                                type="button"
                                                className={styles.qtyBtn}
                                                onClick={() => updateQty(i.entityKey, i.qty - 1)}
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
                                                    updateQty(i.entityKey, e.target.value)
                                                }
                                                disabled={disabled}
                                            />
                                            <button
                                                type="button"
                                                className={styles.qtyBtn}
                                                onClick={() => updateQty(i.entityKey, i.qty + 1)}
                                                disabled={disabled}
                                            >
                                                +
                                            </button>
                                            <button
                                                type="button"
                                                className={styles.removeBtn}
                                                onClick={() => removeItem(i.entityKey)}
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
                                onClick={handleConfirm}
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
