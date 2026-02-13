import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import styles from "./TechCardPage.module.css";

const API_TECH = "http://localhost:8080/api/tech-products";
const API_PRODUCTS = "http://localhost:8080/api/product";
const API_DISHES = "http://localhost:8080/api/dishes";

export default function TechCardPage() {
    const { dishId } = useParams();

    const [dishName, setDishName] = useState(""); // название блюда
    const [items, setItems] = useState([]);
    const [products, setProducts] = useState([]);
    const [productId, setProductId] = useState("");
    const [weight, setWeight] = useState("");
    const [waste, setWaste] = useState("");

    // Текущий редактируемый ингредиент (null - режим добавления)
    const [editingTechProductId, setEditingTechProductId] = useState(null);

    useEffect(() => {
        loadDishName();
        loadTechCard();
        loadProducts();
    }, []);

    const loadDishName = () => {
        fetch(`${API_DISHES}/${dishId}`)
            .then(res => res.json())
            .then(data => setDishName(data.dishName))
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

        const payload = {
            dishId: parseInt(dishId),
            productId: parseInt(productId),
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
        setProductId(String(item.productId));
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

    // Расчёт себестоимости блюда
    const totalCost = items.reduce((sum, i) => {
        const product = products.find(p => p.productId === i.productId);
        const price = product?.productPrice || 0; // цена за кг/единицу
        const weightGr = i.weight || 0;
        const wastePercent = i.waste || 0;
        const netWeightKg = (weightGr * (1 - wastePercent / 100)) / 1000; // чистый вес в кг
        const cost = netWeightKg * price;
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
                    {products.map(p => (
                        <option key={p.productId} value={p.productId}>
                            {p.productName}
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
                        const product = products.find(p => p.productId === i.productId);
                        const price = product?.productPrice || 0;
                        const weightGr = i.weight || 0;
                        const wastePercent = i.waste || 0;
                        const netWeightKg = (weightGr * (1 - wastePercent / 100)) / 1000;
                        const cost = netWeightKg * price;

                        return (
                            <li key={i.techProductId} className={styles.ingredientItem}>
                                <div className={styles.ingredientMain}>
                                    <strong>{product?.productName || "Неизвестный"}</strong>
                                    {" — "}
                                    цена: {price} ₽
                                    {" — "}
                                    {i.weight} г (отход {i.waste ?? 0}%)
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

            <h3 className={styles.totalCost}>Себестоимость блюда: {totalCost.toFixed(2)} ₽</h3>
        </div>
    );
}
