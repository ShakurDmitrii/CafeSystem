import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./DishPage.module.css";

const API_DISHES = "http://localhost:8080/api/dishes";

export default function DishPage() {
    const [dishes, setDishes] = useState([]);
    const [name, setName] = useState("");
    const [price, setPrice] = useState("");
    const [weight, setWeight] = useState("");
    const [firstCost, setFirstCost] = useState("");
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();

    // Загрузка всех блюд
    useEffect(() => {
        loadDishes();
    }, []);

    const loadDishes = () => {
        fetch(API_DISHES)
            .then(res => res.json())
            .then(data => setDishes(Array.isArray(data) ? data : []))
            .catch(err => console.error("Ошибка загрузки блюд:", err));
    };

    const createDish = () => {
        if (!name || !price) return;

        const newDish = {
            dishName: name,
            weight: weight ? parseFloat(weight) : 0.0,
            firstCost: firstCost ? parseFloat(firstCost) : 0.0,
            price: parseFloat(price)
        };

        setLoading(true);
        fetch(API_DISHES, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(newDish)
        })
            .then(res => res.json())
            .then(created => {
                setDishes(prev => [created, ...prev]);
                setName("");
                setPrice("");
                setWeight("");
                setFirstCost("");
            })
            .catch(err => console.error("Ошибка создания блюда:", err))
            .finally(() => setLoading(false));
    };

    // Переход на страницу техкарты блюда
    const openTechCard = (dishId) => {
        navigate(`/tech-card/${dishId}`);
    };

    return (
        <div className={styles.page}>
            <h1>Блюда</h1>

            {/* Форма создания блюда */}
            <section className={styles.createDish}>
                <input
                    type="text"
                    placeholder="Название блюда"
                    value={name}
                    onChange={e => setName(e.target.value)}
                />

                <input
                    type="number"
                    placeholder="Цена"
                    min={0}
                    value={price}
                    onChange={e => setPrice(e.target.value)}
                />

                <input
                    type="number"
                    placeholder="Вес (г)"
                    min={0}
                    value={weight}
                    onChange={e => setWeight(e.target.value)}
                />

                <input
                    type="number"
                    placeholder="Себестоимость"
                    min={0}
                    value={firstCost}
                    onChange={e => setFirstCost(e.target.value)}
                />

                <button
                    className={styles.btn}
                    onClick={createDish}
                    disabled={loading || !name || !price}
                >
                    {loading ? "Сохранение..." : "Добавить блюдо"}
                </button>
            </section>

            {/* Список блюд */}
            <section className={styles.dishList}>
                {dishes.length > 0 ? (
                    dishes.map(d => (
                        <div key={d.dishId} className={styles.dishItem}>
                            <div className={styles.dishInfo}>
                                <span className={styles.dishName}>{d.dishName}</span>
                                <span>{d.price} ₽</span>
                                <span>Вес: {d.weight} г</span>
                                <span>Себестоимость: {d.firstCost} ₽</span>
                            </div>

                            <button
                                className={styles.techBtn}
                                onClick={() => openTechCard(d.dishId)}
                            >
                                Техкарта
                            </button>
                        </div>
                    ))
                ) : (
                    <p>Блюд пока нет</p>
                )}
            </section>
        </div>
    );
}
