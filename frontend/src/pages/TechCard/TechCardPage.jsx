import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";

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
            .then(data => setItems(data || []))
            .catch(err => console.error("Ошибка загрузки техкарты:", err));
    };

    const loadProducts = () => {
        fetch(API_PRODUCTS)
            .then(res => res.json())
            .then(data => setProducts(Array.isArray(data) ? data : []))
            .catch(err => console.error("Ошибка загрузки продуктов:", err));
    };

    const addItem = () => {
        if (!productId || !weight) return;

        fetch(API_TECH, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                dishId: parseInt(dishId),
                productId: parseInt(productId),
                weight: parseFloat(weight),
                waste: waste ? parseFloat(waste) : 0
            })
        })
            .then(res => {
                if (!res.ok) throw new Error("Ошибка добавления ингредиента");
                return res.json();
            })
            .then(() => {
                setProductId("");
                setWeight("");
                setWaste("");
                loadTechCard();
            })
            .catch(err => console.error(err));
    };

    return (
        <div style={{ padding: "20px" }}>
            <h2>Техкарта блюда: {dishName || `#${dishId}`}</h2>

            <div style={{ marginBottom: "20px", display: "flex", gap: "10px", flexWrap: "wrap" }}>
                <select
                    value={productId}
                    onChange={e => setProductId(e.target.value)}
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
                />

                <input
                    type="number"
                    placeholder="Отход (%)"
                    value={waste}
                    onChange={e => setWaste(e.target.value)}
                    min={0}
                    max={100}
                />

                <button onClick={addItem}>Добавить ингредиент</button>
            </div>

            <h3>Список ингредиентов</h3>
            <ul>
                {items.length > 0 ? (
                    items.map(i => {
                        const product = products.find(p => p.productId === i.productId);
                        return (
                            <li key={i.techProductId}>
                                {product?.productName || "Неизвестный"} — {i.weight} г (отход {i.waste}%)
                            </li>
                        );
                    })
                ) : (
                    <p>Ингредиентов пока нет</p>
                )}
            </ul>
        </div>
    );
}
