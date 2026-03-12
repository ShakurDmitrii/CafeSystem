import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../../auth";
import styles from "./DishPage.module.css";

const API_DISHES = `${API_BASE_URL}/api/dishes`;
const API_UPLOAD = `${API_BASE_URL}/api/files/upload-image`;
const API_DISH_CATEGORIES = `${API_BASE_URL}/api/dish-categories`;

export default function DishPage() {
    const [dishes, setDishes] = useState([]);
    const [name, setName] = useState("");
    const [price, setPrice] = useState("");
    const [weight, setWeight] = useState("");
    const [firstCost, setFirstCost] = useState("");
    const [loading, setLoading] = useState(false);
    const [uploadingImage, setUploadingImage] = useState(false);
    const [imageUrl, setImageUrl] = useState("");
    const [editingImageId, setEditingImageId] = useState(null);
    const [uploadingImageId, setUploadingImageId] = useState(null);
    const [categoryDrafts, setCategoryDrafts] = useState({});
    const [editingCategoryId, setEditingCategoryId] = useState(null);
    const [savingCategoryId, setSavingCategoryId] = useState(null);
    const [categories, setCategories] = useState([]);

    const navigate = useNavigate();

    // Загрузка всех блюд
    useEffect(() => {
        loadDishes();
        loadCategories();
    }, []);

    const loadDishes = () => {
        fetch(API_DISHES)
            .then(async (res) => {
                if (!res.ok) {
                    throw new Error(`Не удалось загрузить блюда (${res.status})`);
                }
                const text = await res.text();
                return text ? JSON.parse(text) : [];
            })
            .then(data => setDishes(Array.isArray(data) ? data : []))
            .catch(err => console.error("Ошибка загрузки блюд:", err));
    };

    const loadCategories = () => {
        fetch(API_DISH_CATEGORIES)
            .then(async (res) => {
                if (!res.ok) {
                    throw new Error(`Не удалось загрузить категории (${res.status})`);
                }
                const text = await res.text();
                return text ? JSON.parse(text) : [];
            })
            .then(data => setCategories(Array.isArray(data) ? data : []))
            .catch(err => console.error("Ошибка загрузки категорий:", err));
    };

    const createDish = () => {
        if (!name || !price) return;

        const newDish = {
            dishName: name,
            weight: weight ? parseFloat(weight) : 0.0,
            firstCost: firstCost ? parseFloat(firstCost) : 0.0,
            price: parseFloat(price),
            imageUrl: imageUrl || null
        };

        setLoading(true);
        fetch(API_DISHES, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(newDish)
        })
            .then(async (res) => {
                const text = await res.text();
                const payload = text ? JSON.parse(text) : null;
                if (!res.ok) {
                    throw new Error(payload?.message || `Ошибка создания блюда (${res.status})`);
                }
                return payload;
            })
            .then(created => {
                if (created) setDishes(prev => [created, ...prev]);
                setName("");
                setPrice("");
                setWeight("");
                setFirstCost("");
                setImageUrl("");
            })
            .catch(err => console.error("Ошибка создания блюда:", err))
            .finally(() => setLoading(false));
    };

    const handleUploadImage = async (file) => {
        if (!file) return;
        setUploadingImage(true);
        try {
            const body = new FormData();
            body.append("file", file);
            body.append("folder", "dishes");

            const res = await fetch(API_UPLOAD, {
                method: "POST",
                body
            });
            const text = await res.text();
            const data = text ? JSON.parse(text) : null;
            if (!res.ok) throw new Error("Ошибка загрузки изображения");
            setImageUrl(data.url || "");
        } catch (err) {
            console.error("Ошибка загрузки изображения:", err);
            alert("Не удалось загрузить изображение");
        } finally {
            setUploadingImage(false);
        }
    };

    const updateDish = async (dish, patch) => {
        const payload = {
            dishId: dish.dishId,
            dishName: dish.dishName,
            price: dish.price,
            weight: dish.weight,
            firstCost: dish.firstCost,
            techProduct: dish.techProduct,
            imageUrl: dish.imageUrl,
            category: dish.category,
            categoryId: dish.categoryId,
            categoryName: dish.categoryName,
            ...patch
        };

        const res = await fetch(`${API_DISHES}/${dish.dishId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) {
            throw new Error(data?.message || `Ошибка обновления блюда (${res.status})`);
        }
        return data || payload;
    };

    const handleDishImageUpload = async (dish, file) => {
        if (!file) return;
        setUploadingImageId(dish.dishId);
        try {
            const body = new FormData();
            body.append("file", file);
            body.append("folder", "dishes");

            const res = await fetch(API_UPLOAD, {
                method: "POST",
                body
            });
            const text = await res.text();
            const data = text ? JSON.parse(text) : null;
            if (!res.ok) throw new Error("Ошибка загрузки изображения");

            const updated = await updateDish(dish, { imageUrl: data.url || "" });
            setDishes(prev => prev.map(d => d.dishId === dish.dishId ? { ...d, ...updated } : d));
            setEditingImageId(null);
        } catch (err) {
            console.error("Ошибка загрузки изображения:", err);
            alert("Не удалось загрузить изображение");
        } finally {
            setUploadingImageId(null);
        }
    };

    const startCategoryEdit = (dish) => {
        setEditingCategoryId(dish.dishId);
        setCategoryDrafts(prev => ({
            ...prev,
            [dish.dishId]: dish.categoryName || dish.category || ""
        }));
    };

    const saveCategory = async (dish) => {
        const draft = String(categoryDrafts[dish.dishId] || "").trim();
        if (!draft) return;
        setSavingCategoryId(dish.dishId);
        try {
            const updated = await updateDish(dish, { categoryName: draft, category: draft });
            setDishes(prev => prev.map(d => d.dishId === dish.dishId ? { ...d, ...updated } : d));
            setEditingCategoryId(null);
        } catch (err) {
            console.error("Ошибка обновления категории:", err);
            alert(err.message || "Не удалось обновить категорию");
        } finally {
            setSavingCategoryId(null);
        }
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

                <input
                    type="file"
                    accept="image/*"
                    className={styles.fileInput}
                    onChange={e => handleUploadImage(e.target.files?.[0])}
                />

                <span className={styles.uploadInfo}>
                    {uploadingImage ? "Загрузка изображения..." : (imageUrl ? "Изображение загружено" : "Изображение не выбрано")}
                </span>

                {imageUrl && <img src={imageUrl} alt="Превью блюда" className={styles.previewImage} />}

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
                                {d.imageUrl ? <img src={d.imageUrl} alt={d.dishName} className={styles.dishThumb} /> : (
                                    <div className={styles.missingThumb}>Нет фото</div>
                                )}
                                <span className={styles.dishName}>{d.dishName}</span>
                                <span>{d.price} ₽</span>
                                <span>Вес: {d.weight} г</span>
                                <span>Себестоимость: {d.firstCost} ₽</span>
                                <span className={styles.dishCategory}>
                                    Категория: {d.categoryName || d.category || "—"}
                                </span>
                                {!d.imageUrl && (
                                    <div className={styles.inlineAction}>
                                        {editingImageId === d.dishId ? (
                                            <input
                                                type="file"
                                                accept="image/*"
                                                className={styles.fileInputInline}
                                                onChange={e => handleDishImageUpload(d, e.target.files?.[0])}
                                                disabled={uploadingImageId === d.dishId}
                                            />
                                        ) : (
                                            <button
                                                className={styles.addBtn}
                                                type="button"
                                                onClick={() => setEditingImageId(d.dishId)}
                                            >
                                                Добавить фото
                                            </button>
                                        )}
                                        {uploadingImageId === d.dishId && (
                                            <span className={styles.muted}>Загрузка...</span>
                                        )}
                                    </div>
                                )}
                                {(!d.categoryName && !d.category) && (
                                    <div className={styles.inlineAction}>
                                        {editingCategoryId === d.dishId ? (
                                            <>
                                                {categories.length > 0 && (
                                                    <select
                                                        className={styles.categorySelect}
                                                        value={categoryDrafts[d.dishId] || ""}
                                                        onChange={e => setCategoryDrafts(prev => ({ ...prev, [d.dishId]: e.target.value }))}
                                                        disabled={savingCategoryId === d.dishId}
                                                    >
                                                        <option value="">Выберите категорию</option>
                                                        {categories.map(c => (
                                                            <option key={c.categoryId} value={c.name}>
                                                                {c.name}
                                                            </option>
                                                        ))}
                                                    </select>
                                                )}
                                                <input
                                                    type="text"
                                                    placeholder="Или новая категория"
                                                    className={styles.categoryInput}
                                                    value={categoryDrafts[d.dishId] || ""}
                                                    onChange={e => setCategoryDrafts(prev => ({ ...prev, [d.dishId]: e.target.value }))}
                                                    disabled={savingCategoryId === d.dishId}
                                                />
                                                <button
                                                    className={styles.addBtn}
                                                    type="button"
                                                    onClick={() => saveCategory(d)}
                                                    disabled={savingCategoryId === d.dishId}
                                                >
                                                    {savingCategoryId === d.dishId ? "Сохраняю..." : "Сохранить"}
                                                </button>
                                            </>
                                        ) : (
                                            <button
                                                className={styles.addBtn}
                                                type="button"
                                                onClick={() => startCategoryEdit(d)}
                                            >
                                                Добавить категорию
                                            </button>
                                        )}
                                    </div>
                                )}
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
