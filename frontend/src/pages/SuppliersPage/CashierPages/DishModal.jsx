import { useEffect, useState } from "react";
import styles from "../DishModal.module.css";

export default function DishModal({ isOpen, onClose, onAddDish, dishes }) {
    const [search, setSearch] = useState("");
    const [filteredDishes, setFilteredDishes] = useState([]);
    const [selectedDish, setSelectedDish] = useState(null);

    useEffect(() => {
        const searchTerm = search.toLowerCase();
        setFilteredDishes(dishes.filter(d => (d.dishName || "").toLowerCase().includes(searchTerm)));
    }, [search, dishes]);

    const handleAdd = () => {
        if (!selectedDish) return;
        onAddDish({ ...selectedDish }); // qty = 1 добавляется в CashierPage
        setSelectedDish(null);
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className={styles.modalOverlay}>
            <div className={styles.modal}>
                <h2>Выберите блюдо</h2>
                <input
                    type="text"
                    placeholder="Поиск..."
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    className={styles.searchInput}
                />
                <div className={styles.dishList}>
                    {filteredDishes.map(dish => (
                        <div
                            key={dish.id}
                            className={`${styles.dishItem} ${selectedDish?.id === dish.id ? styles.selected : ""}`}
                            onClick={() => setSelectedDish(dish)}
                        >
                            <span>{dish.dishName || "Без названия"}</span>
                            <span>{dish.price != null ? `${dish.price} ₽` : "—"}</span>
                        </div>
                    ))}
                    {filteredDishes.length === 0 && <p>Ничего не найдено</p>}
                </div>
                <div className={styles.actions}>
                    <button className={styles.btn} onClick={onClose}>Отмена</button>
                    <button className={styles.btn} onClick={handleAdd} disabled={!selectedDish}>
                        Добавить
                    </button>
                </div>
            </div>
        </div>
    );
}
