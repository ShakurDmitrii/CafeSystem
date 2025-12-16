import { useState, useEffect } from "react";
import styles from "./DishPage.module.css";

const API_DISHES = "http://localhost:8080/api/dishes";

export default function DishPage() {
    const [dishes, setDishes] = useState([]);
    const [name, setName] = useState("");
    const [price, setPrice] = useState("");
    const [weight, setWeight] = useState("");
    const [firstCost, setFirstCost] = useState("");
    const [techProduct, setTechProduct] = useState(1);
    const [loading, setLoading] = useState(false);

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

        // Подготовка объекта под DishDTO
        const newDish = {
            dishName: name,
            weight: weight ? parseFloat(weight) : 0.0,
            firstCost: firstCost ? parseFloat(firstCost) : 0.0,
            price: parseFloat(price),
            techProduct: techProduct
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

    return (
        <div className={styles.page}>
            <h1>Блюда</h1>

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
                    Добавить блюдо
                </button>
            </section>

            <section className={styles.dishList}>
                {dishes.length > 0 ? (
                    dishes.map(d => (
                        <div key={d.dishId} className={styles.dishItem}>
                            <span>{d.dishName}</span>
                            <span>{d.price} ₽</span>
                            <span>Вес: {d.weight} г</span>
                            <span>Себестоимость: {d.firstCost} ₽</span>
                        </div>
                    ))
                ) : (
                    <p>Блюд пока нет</p>
                )}
            </section>
        </div>
    );
}
