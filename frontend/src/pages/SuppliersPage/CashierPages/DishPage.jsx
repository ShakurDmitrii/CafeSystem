import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../../auth";
import DishSetsSection from "./DishSetsSection";
import DishCreatePanel from "./dish-page/DishCreatePanel";
import DishEditModal from "./dish-page/DishEditModal";
import DishList from "./dish-page/DishList";
import DishPageHeader from "./dish-page/DishPageHeader";
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
    const [loadError, setLoadError] = useState("");
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
        setLoadError("");
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
            setLoadError(err.message || "Проверьте соединение и попробуйте снова.");
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
            <DishPageHeader
                activeView={activeView}
                dishCount={dishes.length}
                categoryCount={sortedCategories.length}
                onViewChange={setActiveView}
            />

            {activeView === "dishes" ? (
                <div
                    id="dishes-panel"
                    role="tabpanel"
                    aria-labelledby="dishes-tab"
                    className={styles.viewPanel}
                >
                    <DishCreatePanel
                        dishCount={dishes.length}
                        form={createForm}
                        categories={sortedCategories}
                        error={createError}
                        isCreating={createLoading}
                        isImageUploading={createImageUploading}
                        onFormChange={setCreateForm}
                        onImageChange={handleCreateImageUpload}
                        onCreate={handleCreateDish}
                    />
                    <DishList
                        dishes={dishes}
                        loading={loading}
                        error={loadError}
                        deletingDishId={deletingDishId}
                        formatMoney={formatMoney}
                        formatWeight={formatWeight}
                        onEdit={openEditModal}
                        onDelete={deleteDish}
                        onRetry={loadPage}
                    />
                </div>
            ) : (
                <div
                    id="sets-panel"
                    role="tabpanel"
                    aria-labelledby="sets-tab"
                    className={styles.viewPanel}
                >
                    <DishSetsSection dishes={dishes} categories={sortedCategories} />
                </div>
            )}

            {editModalOpen && editingDish && (
                <DishEditModal
                    dish={editingDish}
                    form={editForm}
                    categories={sortedCategories}
                    error={editError}
                    isSaving={editLoading}
                    isImageUploading={editImageUploading}
                    onFormChange={setEditForm}
                    onImageChange={handleEditImageUpload}
                    onClose={closeEditModal}
                    onSave={handleSaveDish}
                />
            )}
        </div>
    );
}
