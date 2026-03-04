import { useParams } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import styles from "./TechCardPage.module.css";

const API_TECH = "http://localhost:8080/api/tech-products";
const API_PRODUCTS = "http://localhost:8080/api/product";
const API_DISHES = "http://localhost:8080/api/dishes";

export default function TechCardPage() {
    const { dishId } = useParams();

    const [dishName, setDishName] = useState(""); // название блюда
    const [dishPrice, setDishPrice] = useState(null); // цена блюда из БД
    const [items, setItems] = useState([]);
    const [products, setProducts] = useState([]);
    const [productId, setProductId] = useState("");
    const [weight, setWeight] = useState("");
    const [waste, setWaste] = useState("");

    // Текущий редактируемый ингредиент (null - режим добавления)
    const [editingTechProductId, setEditingTechProductId] = useState(null);

    const normalizeName = (name) => String(name || "").trim().toLowerCase();

    const productsById = useMemo(() => {
        const map = new Map();
        products.forEach(p => map.set(p.productId, p));
        return map;
    }, [products]);

    // Объединяем продукты по названию: в UI показываем 1 ингредиент = 1 имя.
    const groupedProducts = useMemo(() => {
        const groups = new Map();

        products.forEach((p) => {
            const key = normalizeName(p.productName);
            if (!key) return;

            if (!groups.has(key)) {
                groups.set(key, {
                    key,
                    name: p.productName,
                    representativeId: p.productId,
                    prices: []
                });
            }

            const g = groups.get(key);
            const price = Number(p?.averageStockPrice ?? p?.productPrice ?? 0);
            if (Number.isFinite(price) && price >= 0) {
                g.prices.push(price);
            }
        });

        return Array.from(groups.values())
            .map((g) => ({
                ...g,
                averagePrice: g.prices.length
                    ? g.prices.reduce((sum, v) => sum + v, 0) / g.prices.length
                    : 0
            }))
            .sort((a, b) => a.name.localeCompare(b.name, "ru"));
    }, [products]);

    const averagePriceByName = useMemo(() => {
        const map = new Map();
        groupedProducts.forEach(g => map.set(g.key, g.averagePrice));
        return map;
    }, [groupedProducts]);

    const representativeIdByName = useMemo(() => {
        const map = new Map();
        groupedProducts.forEach(g => map.set(g.key, g.representativeId));
        return map;
    }, [groupedProducts]);

    const getAveragePriceForProductId = (id) => {
        const p = productsById.get(id);
        if (!p) return 0;
        const key = normalizeName(p.productName);
        return Number(averagePriceByName.get(key) ?? p?.averageStockPrice ?? p?.productPrice ?? 0);
    };

    useEffect(() => {
        loadDish();
        loadTechCard();
        loadProducts();
    }, []);

    const loadDish = () => {
        fetch(`${API_DISHES}/${dishId}`)
            .then(res => res.json())
            .then(data => {
                setDishName(data.dishName ?? "");
                setDishPrice(data.price ?? data.dishPrice ?? null);
            })
            .catch(err => console.error("Ошибка загрузки блюда:", err));
    };

    const loadTechCard = () => {
        fetch(`${API_TECH}/dish/${dishId}`)
            .then(res => res.json())
            .then(data => {
                // Бэкенд может вернуть один объект или массив
                if (Array.isArray(data)) {
                    setItems(data);
                } else if (data) {
                    setItems([data]);
                } else {
                    setItems([]);
                }
            })
            .catch(err => console.error("Ошибка загрузки техкарты:", err));
    };

    const loadProducts = () => {
        fetch(API_PRODUCTS)
            .then(res => res.json())
            .then(data => setProducts(Array.isArray(data) ? data : []))
            .catch(err => console.error("Ошибка загрузки продуктов:", err));
    };

    const addOrUpdateItem = () => {
        if (!productId || !weight) return;

        const selectedProduct = productsById.get(parseInt(productId, 10));
        const selectedNameKey = normalizeName(selectedProduct?.productName);
        const representativeId = representativeIdByName.get(selectedNameKey) ?? parseInt(productId, 10);

        const payload = {
            dishId: parseInt(dishId),
            productId: representativeId,
            weight: parseFloat(weight),
            waste: waste ? parseFloat(waste) : 0
        };

        // Если есть редактируемый ингредиент — обновляем, иначе добавляем новый
        const url = editingTechProductId ? `${API_TECH}/${editingTechProductId}` : API_TECH;
        const method = editingTechProductId ? "PUT" : "POST";

        fetch(url, {
            method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(editingTechProductId ? { ...payload, techProductId: editingTechProductId } : payload)
        })
            .then(res => {
                if (!res.ok) throw new Error(editingTechProductId ? "Ошибка обновления ингредиента" : "Ошибка добавления ингредиента");
                return res.json();
            })
            .then(() => {
                setProductId("");
                setWeight("");
                setWaste("");
                setEditingTechProductId(null);
                loadTechCard();
            })
            .catch(err => console.error(err));
    };

    const startEditItem = (item) => {
        setEditingTechProductId(item.techProductId);
        const currentProduct = productsById.get(item.productId);
        const nameKey = normalizeName(currentProduct?.productName);
        const representativeId = representativeIdByName.get(nameKey) ?? item.productId;
        setProductId(String(representativeId));
        setWeight(String(item.weight));
        setWaste(item.waste !== undefined && item.waste !== null ? String(item.waste) : "");
    };

    const cancelEdit = () => {
        setEditingTechProductId(null);
        setProductId("");
        setWeight("");
        setWaste("");
    };

    const deleteItem = (item) => {
        if (!item) return;

        // Берём ID как есть, не отбрасывая 0
        const idToDelete =
            item.techProductId ??
            item.techProductID ??
            item.id ??
            item.techId;

        if (idToDelete === undefined || idToDelete === null) {
            console.error("Нет корректного ID для удаления ингредиента:", item);
            window.alert("Не удалось определить ID ингредиента для удаления");
            return;
        }

        if (!window.confirm("Удалить этот ингредиент из техкарты?")) return;

        fetch(`${API_TECH}/${idToDelete}`, {
            method: "DELETE"
        })
            .then(res => {
                if (!res.ok) throw new Error("Ошибка удаления ингредиента");
            })
            .then(() => {
                setItems(prev =>
                    prev.filter(i => {
                        const currentId =
                            i.techProductId ??
                            i.techProductID ??
                            i.id ??
                            i.techId;
                        return currentId !== idToDelete;
                    }));
            })
            .catch(err => console.error(err));
    };

    // Расчёт себестоимости блюда (без учета отходов)
    const totalCost = items.reduce((sum, i) => {
        const price = getAveragePriceForProductId(i.productId); // средняя цена по названию, ₽/кг
        const weightGr = i.weight || 0;
        const weightKg = weightGr / 1000; // вес в кг
        const cost = weightKg * price;
        return sum + cost;
    }, 0);

    return (
        <div className={styles.container}>
            <h2 className={styles.title}>Техкарта блюда: {dishName || `#${dishId}`}</h2>

            <div className={styles.formRow}>
                <select
                    value={productId}
                    onChange={e => setProductId(e.target.value)}
                    className={styles.select}
                >
                    <option value="">Выберите ингредиент</option>
                    {groupedProducts.map(p => (
                        <option key={p.key} value={p.representativeId}>
                            {p.name}
                        </option>
                    ))}
                </select>

                <input
                    type="number"
                    placeholder="Вес (г)"
                    value={weight}
                    onChange={e => setWeight(e.target.value)}
                    min={0}
                    className={styles.input}
                />

                <input
                    type="number"
                    placeholder="Отход (%)"
                    value={waste}
                    onChange={e => setWaste(e.target.value)}
                    min={0}
                    max={100}
                    className={styles.input}
                />

                <button onClick={addOrUpdateItem} className={styles.primaryButton}>
                    {editingTechProductId ? "Сохранить изменения" : "Добавить ингредиент"}
                </button>

                {editingTechProductId && (
                    <button onClick={cancelEdit} className={styles.secondaryButton}>
                        Отмена
                    </button>
                )}
            </div>

            <h3 className={styles.ingredientsTitle}>Список ингредиентов</h3>
            <ul className={styles.ingredientsList}>
                {items.length > 0 ? (
                    items.map(i => {
                        const product = productsById.get(i.productId);
                        const price = getAveragePriceForProductId(i.productId);
                        const weightGr = i.weight || 0;
                        const weightKg = weightGr / 1000; // вес в кг
                        const cost = weightKg * price;

                        return (
                            <li key={i.techProductId} className={styles.ingredientItem}>
                                <div className={styles.ingredientMain}>
                                    <strong>{product?.productName || "Неизвестный"}</strong>
                                    {" — "}
                                    цена: {price} ₽/кг
                                    {" — "}
                                    {i.weight} г
                                    {" — "}
                                    себестоимость: {cost.toFixed(2)} ₽
                                </div>
                                <div className={styles.ingredientActions}>
                                    <button
                                        onClick={() => startEditItem(i)}
                                        className={styles.secondaryButton}
                                    >
                                        Редактировать
                                    </button>
                                    <button
                                        onClick={() => deleteItem(i)}
                                        className={styles.dangerButton}
                                    >
                                        Удалить
                                    </button>
                                </div>
                            </li>
                        );
                    })
                ) : (
                    <p className={styles.emptyText}>Ингредиентов пока нет</p>
                )}
            </ul>

            <div className={styles.totalCostBlock}>
                <span className={styles.totalCost}>Себестоимость: {totalCost.toFixed(2)} ₽</span>
                {dishPrice != null && (
                    <span className={styles.totalCost}>Цена блюда: {Number(dishPrice).toFixed(2)} ₽</span>
                )}
            </div>
        </div>
    );
}
