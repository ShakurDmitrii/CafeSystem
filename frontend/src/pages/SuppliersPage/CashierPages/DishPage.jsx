import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../../auth";
import DishSetsSection from "./DishSetsSection";
import styles from "./DishPage.module.css";

const API_DISHES = `${API_BASE_URL}/api/dishes`;
const API_UPLOAD = `${API_BASE_URL}/api/files/upload-image`;
const API_DISH_CATEGORIES = `${API_BASE_URL}/api/dish-categories`;

const createDishForm = () => ({
    dishName: "",
    price: "",
    weight: "",
    firstCost: "",
    selectedCategoryId: "",
    customCategory: "",
    imageUrl: ""
});

const parseJsonSafe = (raw) => {
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch {
        return null;
    }
};

const formatMoney = (value) => {
    const num = Number(value ?? 0);
    if (!Number.isFinite(num)) return "0";
    return num.toLocaleString("ru-RU", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
};

const formatWeight = (value) => {
    const num = Number(value ?? 0);
    if (!Number.isFinite(num)) return "0";
    return num.toLocaleString("ru-RU", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
};

const buildCategoryPayload = (form, categories) => {
    const customCategory = String(form.customCategory || "").trim();
    if (customCategory) {
        return {
            categoryId: null,
            categoryName: customCategory,
            category: customCategory
        };
    }

    const selectedCategoryId = Number(form.selectedCategoryId);
    if (Number.isFinite(selectedCategoryId) && selectedCategoryId > 0) {
        const selectedCategory = categories.find((item) => item.categoryId === selectedCategoryId);
        const categoryName = selectedCategory?.name || "";
        return {
            categoryId: selectedCategoryId,
            categoryName,
            category: categoryName
        };
    }

    return {
        categoryId: null,
        categoryName: null,
        category: null
    };
};

export default function DishPage() {
    const navigate = useNavigate();
    const [activeView, setActiveView] = useState("dishes");
    const [dishes, setDishes] = useState([]);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [createForm, setCreateForm] = useState(createDishForm);
    const [createLoading, setCreateLoading] = useState(false);
    const [createError, setCreateError] = useState("");
    const [createImageUploading, setCreateImageUploading] = useState(false);
    const [editingDish, setEditingDish] = useState(null);
    const [editForm, setEditForm] = useState(createDishForm);
    const [editModalOpen, setEditModalOpen] = useState(false);
    const [editLoading, setEditLoading] = useState(false);
    const [editError, setEditError] = useState("");
    const [editImageUploading, setEditImageUploading] = useState(false);
    const [deletingDishId, setDeletingDishId] = useState(null);

    const sortedCategories = useMemo(
        () => [...categories].sort((a, b) => String(a.name || "").localeCompare(String(b.name || ""), "ru")),
        [categories]
    );

    const loadPage = useCallback(async () => {
        setLoading(true);
        try {
            const [dishesRes, categoriesRes] = await Promise.all([
                fetch(API_DISHES),
                fetch(API_DISH_CATEGORIES)
            ]);

            const dishesText = await dishesRes.text();
            const categoriesText = await categoriesRes.text();
            const dishesData = parseJsonSafe(dishesText);
            const categoriesData = parseJsonSafe(categoriesText);

            if (!dishesRes.ok) {
                throw new Error(dishesData?.message || `Не удалось загрузить блюда (${dishesRes.status})`);
            }

            if (!categoriesRes.ok) {
                throw new Error(categoriesData?.message || `Не удалось загрузить категории (${categoriesRes.status})`);
            }

            setDishes(Array.isArray(dishesData) ? dishesData : []);
            setCategories(Array.isArray(categoriesData) ? categoriesData : []);
        } catch (err) {
            console.error("Ошибка загрузки блюд:", err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadPage();
    }, [loadPage]);

    const uploadImage = async (file) => {
        if (!file) return "";

        const body = new FormData();
        body.append("file", file);
        body.append("folder", "dishes");

        const res = await fetch(API_UPLOAD, {
            method: "POST",
            body
        });
        const text = await res.text();
        const data = parseJsonSafe(text);
        if (!res.ok) {
            throw new Error(data?.message || "Ошибка загрузки изображения");
        }
        return data?.url || "";
    };

    const updateDish = async (dishId, payload) => {
        const res = await fetch(`${API_DISHES}/${dishId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const text = await res.text();
        const data = parseJsonSafe(text);
        if (!res.ok) {
            throw new Error(data?.message || `Ошибка обновления блюда (${res.status})`);
        }
        return data || payload;
    };

    const handleCreateImageUpload = async (file) => {
        if (!file) return;
        setCreateImageUploading(true);
        try {
            const imageUrl = await uploadImage(file);
            setCreateForm((prev) => ({ ...prev, imageUrl }));
        } catch (err) {
            console.error("Ошибка загрузки изображения:", err);
            setCreateError(err.message || "Не удалось загрузить изображение");
        } finally {
            setCreateImageUploading(false);
        }
    };

    const handleEditImageUpload = async (file) => {
        if (!file) return;
        setEditImageUploading(true);
        try {
            const imageUrl = await uploadImage(file);
            setEditForm((prev) => ({ ...prev, imageUrl }));
        } catch (err) {
            console.error("Ошибка загрузки изображения:", err);
            setEditError(err.message || "Не удалось загрузить изображение");
        } finally {
            setEditImageUploading(false);
        }
    };

    const handleCreateDish = async () => {
        const dishName = String(createForm.dishName || "").trim();
        const price = Number(createForm.price);
        const weight = Number(createForm.weight);
        const firstCost = Number(createForm.firstCost);

        if (!dishName) {
            setCreateError("Введите название блюда");
            return;
        }

        if (!Number.isFinite(price) || price <= 0) {
            setCreateError("Укажите корректную цену");
            return;
        }

        setCreateLoading(true);
        setCreateError("");

        try {
            const res = await fetch(API_DISHES, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    dishName,
                    price,
                    weight: Number.isFinite(weight) ? weight : 0,
                    firstCost: Number.isFinite(firstCost) ? firstCost : 0,
                    imageUrl: createForm.imageUrl || null,
                    ...buildCategoryPayload(createForm, sortedCategories)
                })
            });

            const text = await res.text();
            const data = parseJsonSafe(text);
            if (!res.ok) {
                throw new Error(data?.message || `Ошибка создания блюда (${res.status})`);
            }

            setCreateForm(createDishForm());
            await loadPage();
            if (data?.dishId) {
                navigate(`/tech-card/${data.dishId}`);
            }
        } catch (err) {
            console.error("Ошибка создания блюда:", err);
            setCreateError(err.message || "Не удалось создать блюдо");
        } finally {
            setCreateLoading(false);
        }
    };

    const openEditModal = (dish) => {
        setEditingDish(dish);
        setEditForm({
            dishName: dish.dishName || "",
            price: dish.price ?? "",
            weight: dish.weight ?? "",
            firstCost: dish.firstCost ?? "",
            selectedCategoryId: dish.categoryId ? String(dish.categoryId) : "",
            customCategory: dish.categoryId ? "" : (dish.categoryName || dish.category || ""),
            imageUrl: dish.imageUrl || ""
        });
        setEditError("");
        setEditModalOpen(true);
    };

    const closeEditModal = () => {
        if (editLoading || editImageUploading) return;
        setEditModalOpen(false);
        setEditingDish(null);
        setEditForm(createDishForm());
        setEditError("");
    };

    const handleSaveDish = async () => {
        if (!editingDish) return;

        const dishName = String(editForm.dishName || "").trim();
        const price = Number(editForm.price);
        const weight = Number(editForm.weight);
        const firstCost = Number(editForm.firstCost);

        if (!dishName) {
            setEditError("Введите название блюда");
            return;
        }

        if (!Number.isFinite(price) || price <= 0) {
            setEditError("Укажите корректную цену");
            return;
        }

        setEditLoading(true);
        setEditError("");

        try {
            await updateDish(editingDish.dishId, {
                dishId: editingDish.dishId,
                dishName,
                price,
                weight: Number.isFinite(weight) ? weight : 0,
                firstCost: Number.isFinite(firstCost) ? firstCost : 0,
                techProduct: editingDish.techProduct,
                imageUrl: editForm.imageUrl || null,
                ...buildCategoryPayload(editForm, sortedCategories)
            });

            await loadPage();
            closeEditModal();
        } catch (err) {
            console.error("Ошибка обновления блюда:", err);
            setEditError(err.message || "Не удалось сохранить изменения");
        } finally {
            setEditLoading(false);
        }
    };

    const deleteDish = async (dish) => {
        if (!dish?.dishId) return;
        if (!window.confirm(`Удалить блюдо "${dish.dishName}"?`)) return;

        setDeletingDishId(dish.dishId);
        try {
            const res = await fetch(`${API_DISHES}/${dish.dishId}`, {
                method: "DELETE"
            });
            if (!res.ok) {
                const raw = await res.text().catch(() => "");
                const data = parseJsonSafe(raw);
                throw new Error(data?.message || data?.error || raw || `Ошибка удаления блюда (${res.status})`);
            }
            setDishes((prev) => prev.filter((item) => item.dishId !== dish.dishId));
        } catch (err) {
            console.error("Ошибка удаления блюда:", err);
            window.alert(err.message || "Не удалось удалить блюдо");
        } finally {
            setDeletingDishId(null);
        }
    };

    return (
        <div className={styles.page}>
            <section className={styles.hero}>
                <div>
                    <p className={styles.eyebrow}>Меню</p>
                    <h1 className={styles.title}>Блюда, карточки и наборы</h1>
                    <p className={styles.subtitle}>
                        Создавайте блюда, держите карточки меню в порядке и редактируйте цену, вес, категорию и фото в одном аккуратном окне, а ниже собирайте наборы из уже готовых блюд.
                    </p>
                </div>
                <div className={styles.heroNote}>
                    Переключайтесь между блюдами и наборами через свитч ниже: в каждом режиме останется только свой сценарий работы без визуальной каши.
                </div>
            </section>

            <section className={styles.switchCard}>
                <div className={styles.switchGroup}>
                    <button
                        type="button"
                        className={`${styles.switchButton} ${activeView === "dishes" ? styles.switchButtonActive : ""}`}
                        onClick={() => setActiveView("dishes")}
                    >
                        Блюда
                    </button>
                    <button
                        type="button"
                        className={`${styles.switchButton} ${activeView === "sets" ? styles.switchButtonActive : ""}`}
                        onClick={() => setActiveView("sets")}
                    >
                        Наборы
                    </button>
                </div>
                <div className={styles.switchHint}>
                    {activeView === "dishes"
                        ? `Сейчас открыт режим блюд: ${dishes.length} поз.`
                        : "Сейчас открыт режим наборов: создание и управление сеттами."}
                </div>
            </section>

            {activeView === "dishes" ? (
                <>
                    <section className={styles.createCard}>
                        <div className={styles.sectionHeading}>
                            <div>
                                <h2>Новое блюдо</h2>
                                <p>Соберите карточку блюда сразу с ценой, категорией и фото, а затем перейдите к техкарте.</p>
                            </div>
                            <div className={styles.counterChip}>{dishes.length} шт.</div>
                        </div>

                        <div className={styles.formGrid}>
                            <label className={styles.field}>
                                <span>Название блюда</span>
                                <input
                                    type="text"
                                    className={styles.input}
                                    placeholder="Например, бургер классик"
                                    value={createForm.dishName}
                                    onChange={(e) => setCreateForm((prev) => ({ ...prev, dishName: e.target.value }))}
                                />
                            </label>

                            <label className={styles.field}>
                                <span>Цена, ₽</span>
                                <input
                                    type="number"
                                    className={styles.input}
                                    min="0"
                                    step="0.01"
                                    value={createForm.price}
                                    onChange={(e) => setCreateForm((prev) => ({ ...prev, price: e.target.value }))}
                                />
                            </label>

                            <label className={styles.field}>
                                <span>Вес, г</span>
                                <input
                                    type="number"
                                    className={styles.input}
                                    min="0"
                                    step="0.01"
                                    value={createForm.weight}
                                    onChange={(e) => setCreateForm((prev) => ({ ...prev, weight: e.target.value }))}
                                />
                            </label>

                            <label className={styles.field}>
                                <span>Себестоимость, ₽</span>
                                <input
                                    type="number"
                                    className={styles.input}
                                    min="0"
                                    step="0.01"
                                    value={createForm.firstCost}
                                    onChange={(e) => setCreateForm((prev) => ({ ...prev, firstCost: e.target.value }))}
                                />
                            </label>

                            <label className={styles.field}>
                                <span>Категория из списка</span>
                                <select
                                    className={styles.select}
                                    value={createForm.selectedCategoryId}
                                    onChange={(e) => setCreateForm((prev) => ({ ...prev, selectedCategoryId: e.target.value }))}
                                >
                                    <option value="">Без категории</option>
                                    {sortedCategories.map((category) => (
                                        <option key={category.categoryId} value={category.categoryId}>
                                            {category.name}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label className={styles.field}>
                                <span>Новая категория</span>
                                <input
                                    type="text"
                                    className={styles.input}
                                    placeholder="Например, сезонное меню"
                                    value={createForm.customCategory}
                                    onChange={(e) => setCreateForm((prev) => ({ ...prev, customCategory: e.target.value }))}
                                />
                            </label>
                        </div>

                        <div className={styles.imagePanel}>
                            <div className={styles.imageInfo}>
                                <div className={styles.imageHeading}>Фото блюда</div>
                                <div className={styles.imageHint}>
                                    {createImageUploading
                                        ? "Загружаем изображение..."
                                        : createForm.imageUrl
                                            ? "Изображение загружено и будет сохранено вместе с блюдом."
                                            : "Загрузите фото, чтобы карточка меню выглядела аккуратно."}
                                </div>
                            </div>

                            <div className={styles.imageControls}>
                                <input
                                    type="file"
                                    accept="image/*"
                                    className={styles.fileInput}
                                    onChange={(e) => handleCreateImageUpload(e.target.files?.[0])}
                                />
                                {createForm.imageUrl ? (
                                    <img src={createForm.imageUrl} alt="Превью блюда" className={styles.previewImage} />
                                ) : (
                                    <div className={styles.previewPlaceholder}>Нет фото</div>
                                )}
                            </div>
                        </div>

                        <div className={styles.formActions}>
                            <button
                                type="button"
                                className={styles.primaryButton}
                                onClick={handleCreateDish}
                                disabled={createLoading || createImageUploading}
                            >
                                {createLoading ? "Создание..." : "Создать блюдо"}
                            </button>
                        </div>

                        {createError && <div className={styles.errorBox}>{createError}</div>}
                    </section>

                    <section className={styles.listSection}>
                        <div className={styles.sectionHeading}>
                            <div>
                                <h2>Все блюда</h2>
                                <p>Открывайте техкарты, редактируйте карточки меню и удаляйте блюда, которые больше не используются.</p>
                            </div>
                        </div>

                        {loading ? (
                            <div className={styles.emptyState}>Загрузка блюд...</div>
                        ) : dishes.length === 0 ? (
                            <div className={styles.emptyState}>
                                Пока нет ни одного блюда. Создайте первое сверху, и после сохранения сразу откроется техкарта.
                            </div>
                        ) : (
                            <div className={styles.cardsGrid}>
                                {dishes.map((dish) => (
                                    <article key={dish.dishId} className={styles.card}>
                                        <div className={styles.cardMedia}>
                                            {dish.imageUrl ? (
                                                <img src={dish.imageUrl} alt={dish.dishName} className={styles.cardImage} />
                                            ) : (
                                                <div className={styles.previewPlaceholder}>Нет фото</div>
                                            )}
                                        </div>

                                        <div className={styles.cardHeader}>
                                            <div>
                                                <div className={styles.cardId}>#{dish.dishId}</div>
                                                <h3 className={styles.cardTitle}>{dish.dishName}</h3>
                                            </div>
                                            <div className={styles.priceChip}>{formatMoney(dish.price)} ₽</div>
                                        </div>

                                        <div className={styles.metricsRow}>
                                            <div className={styles.metric}>
                                                <span className={styles.metricLabel}>Вес</span>
                                                <strong>{formatWeight(dish.weight)} г</strong>
                                            </div>
                                            <div className={styles.metric}>
                                                <span className={styles.metricLabel}>Себестоимость</span>
                                                <strong>{formatMoney(dish.firstCost)} ₽</strong>
                                            </div>
                                        </div>

                                        <div className={styles.metaList}>
                                            <div className={styles.metaRow}>
                                                <span className={styles.metaLabel}>Категория</span>
                                                <span>{dish.categoryName || dish.category || "Не указана"}</span>
                                            </div>
                                            <div className={styles.metaRow}>
                                                <span className={styles.metaLabel}>Фото</span>
                                                <span>{dish.imageUrl ? "Загружено" : "Не добавлено"}</span>
                                            </div>
                                        </div>

                                        <div className={styles.cardActions}>
                                            <button
                                                type="button"
                                                className={styles.secondaryButton}
                                                onClick={() => openEditModal(dish)}
                                            >
                                                Редактировать
                                            </button>
                                            <button
                                                type="button"
                                                className={styles.techButton}
                                                onClick={() => navigate(`/tech-card/${dish.dishId}`)}
                                            >
                                                Техкарта
                                            </button>
                                            <button
                                                type="button"
                                                className={styles.dangerButton}
                                                onClick={() => deleteDish(dish)}
                                                disabled={deletingDishId === dish.dishId}
                                            >
                                                {deletingDishId === dish.dishId ? "Удаление..." : "Удалить"}
                                            </button>
                                        </div>
                                    </article>
                                ))}
                            </div>
                        )}
                    </section>
                </>
            ) : (
                <DishSetsSection dishes={dishes} categories={sortedCategories} />
            )}

            {editModalOpen && editingDish && (
                <div className={styles.modalOverlay} onClick={closeEditModal}>
                    <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
                        <div className={styles.modalHeader}>
                            <div>
                                <p className={styles.eyebrow}>Редактирование блюда</p>
                                <h3 className={styles.modalTitle}>{editingDish.dishName}</h3>
                                <p className={styles.modalSubtitle}>
                                    Здесь можно поправить название, цену, вес, себестоимость, категорию и изображение блюда.
                                </p>
                            </div>
                            <button type="button" className={styles.closeButton} onClick={closeEditModal}>
                                Закрыть
                            </button>
                        </div>

                        <div className={styles.modalBody}>
                            <div className={styles.formGrid}>
                                <label className={styles.field}>
                                    <span>Название блюда</span>
                                    <input
                                        type="text"
                                        className={styles.input}
                                        value={editForm.dishName}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, dishName: e.target.value }))}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Цена, ₽</span>
                                    <input
                                        type="number"
                                        className={styles.input}
                                        min="0"
                                        step="0.01"
                                        value={editForm.price}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, price: e.target.value }))}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Вес, г</span>
                                    <input
                                        type="number"
                                        className={styles.input}
                                        min="0"
                                        step="0.01"
                                        value={editForm.weight}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, weight: e.target.value }))}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Себестоимость, ₽</span>
                                    <input
                                        type="number"
                                        className={styles.input}
                                        min="0"
                                        step="0.01"
                                        value={editForm.firstCost}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, firstCost: e.target.value }))}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Категория из списка</span>
                                    <select
                                        className={styles.select}
                                        value={editForm.selectedCategoryId}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, selectedCategoryId: e.target.value }))}
                                    >
                                        <option value="">Без категории</option>
                                        {sortedCategories.map((category) => (
                                            <option key={category.categoryId} value={category.categoryId}>
                                                {category.name}
                                            </option>
                                        ))}
                                    </select>
                                </label>

                                <label className={styles.field}>
                                    <span>Новая категория</span>
                                    <input
                                        type="text"
                                        className={styles.input}
                                        placeholder="Оставьте пустым, чтобы использовать категорию из списка"
                                        value={editForm.customCategory}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, customCategory: e.target.value }))}
                                    />
                                </label>
                            </div>

                            <div className={styles.imagePanel}>
                                <div className={styles.imageInfo}>
                                    <div className={styles.imageHeading}>Фото блюда</div>
                                    <div className={styles.imageHint}>
                                        {editImageUploading
                                            ? "Загружаем новое изображение..."
                                            : editForm.imageUrl
                                                ? "Фото обновится после сохранения блюда."
                                                : "Можно добавить или заменить фото прямо отсюда."}
                                    </div>
                                </div>

                                <div className={styles.imageControls}>
                                    <input
                                        type="file"
                                        accept="image/*"
                                        className={styles.fileInput}
                                        onChange={(e) => handleEditImageUpload(e.target.files?.[0])}
                                    />
                                    {editForm.imageUrl ? (
                                        <img src={editForm.imageUrl} alt="Превью блюда" className={styles.previewImage} />
                                    ) : (
                                        <div className={styles.previewPlaceholder}>Нет фото</div>
                                    )}
                                </div>
                            </div>

                            {editError && <div className={styles.errorBox}>{editError}</div>}
                        </div>

                        <div className={styles.modalActions}>
                            <button
                                type="button"
                                className={styles.secondaryButton}
                                onClick={() => navigate(`/tech-card/${editingDish.dishId}`)}
                            >
                                Открыть техкарту
                            </button>
                            <button
                                type="button"
                                className={styles.closeButton}
                                onClick={closeEditModal}
                                disabled={editLoading || editImageUploading}
                            >
                                Отмена
                            </button>
                            <button
                                type="button"
                                className={styles.primaryButton}
                                onClick={handleSaveDish}
                                disabled={editLoading || editImageUploading}
                            >
                                {editLoading ? "Сохранение..." : "Сохранить изменения"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
