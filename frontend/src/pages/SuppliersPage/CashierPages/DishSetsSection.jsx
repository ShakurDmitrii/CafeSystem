import { useCallback, useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "../../../auth";
import DishPickerModal from "./DishPickerModal";
import styles from "./DishPage.module.css";

const API_DISH_SETS = `${API_BASE_URL}/api/dish-sets`;
const API_UPLOAD = `${API_BASE_URL}/api/files/upload-image`;

const createSetForm = () => ({
    setName: "",
    price: "",
    imageUrl: "",
    items: []
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

const normalizeSetItems = (items = []) =>
    items
        .filter(Boolean)
        .map((item) => ({
            dishId: Number(item.dishId ?? item.dishID ?? item.id ?? 0),
            dishName: item.dishName || item.name || "Без названия",
            qty: Number(item.qty ?? 0) || 0,
            price: Number(item.price ?? item.dishPrice ?? 0) || 0,
            dishFirstCost: Number(item.dishFirstCost ?? item.firstCost ?? 0) || 0,
            imageUrl: item.imageUrl || null,
            categoryName: item.categoryName || item.category || ""
        }))
        .filter((item) => item.dishId > 0 && item.qty > 0);

const normalizeSet = (set) => ({
    ...set,
    setId: Number(set?.setId ?? 0),
    setName: set?.setName || "Без названия",
    price: Number(set?.price ?? 0) || 0,
    firstCost: Number(set?.firstCost ?? 0) || 0,
    imageUrl: set?.imageUrl || "",
    items: normalizeSetItems(set?.items)
});

const summarizeSetItems = (items = []) =>
    normalizeSetItems(items)
        .slice(0, 4)
        .map((item) => `${item.dishName} x${item.qty}`)
        .join(", ");

const countSetPortions = (items = []) =>
    normalizeSetItems(items).reduce((sum, item) => sum + Number(item.qty || 0), 0);

const buildSetPayload = (form) => ({
    setName: String(form.setName || "").trim(),
    price: Number(form.price),
    imageUrl: form.imageUrl || null,
    items: normalizeSetItems(form.items).map((item) => ({
        dishId: item.dishId,
        qty: item.qty
    }))
});

const normalizeSetName = (value) => String(value || "").trim().toLocaleLowerCase("ru-RU");

export default function DishSetsSection({ dishes = [], categories = [] }) {
    const [dishSets, setDishSets] = useState([]);
    const [loading, setLoading] = useState(true);
    const [createForm, setCreateForm] = useState(createSetForm);
    const [createLoading, setCreateLoading] = useState(false);
    const [createError, setCreateError] = useState("");
    const [createImageUploading, setCreateImageUploading] = useState(false);
    const [editingSet, setEditingSet] = useState(null);
    const [editForm, setEditForm] = useState(createSetForm);
    const [editModalOpen, setEditModalOpen] = useState(false);
    const [editLoading, setEditLoading] = useState(false);
    const [editError, setEditError] = useState("");
    const [editImageUploading, setEditImageUploading] = useState(false);
    const [deletingSetId, setDeletingSetId] = useState(null);
    const [composerTarget, setComposerTarget] = useState(null);

    const dishCostMap = useMemo(
        () => new Map(dishes.map((dish) => [dish.dishId, Number(dish.firstCost ?? 0) || 0])),
        [dishes]
    );

    const calculateSetCost = useCallback((items = []) => (
        normalizeSetItems(items).reduce((sum, item) => {
            const dishCost = dishCostMap.get(item.dishId) ?? Number(item.dishFirstCost ?? 0) ?? 0;
            return sum + dishCost * Number(item.qty || 0);
        }, 0)
    ), [dishCostMap]);

    const hasDuplicateSetName = useCallback((setName, excludedSetId = null) => {
        const normalized = normalizeSetName(setName);
        if (!normalized) return false;

        return dishSets.some((setItem) => {
            if (excludedSetId != null && Number(setItem.setId) === Number(excludedSetId)) {
                return false;
            }
            return normalizeSetName(setItem.setName) === normalized;
        });
    }, [dishSets]);

    const loadSets = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch(API_DISH_SETS);
            const text = await res.text();
            const data = parseJsonSafe(text);
            if (!res.ok) {
                if (res.status === 409) {
                    throw new Error(data?.message || "Набор с таким названием уже существует");
                }
                throw new Error(data?.message || `Не удалось загрузить наборы (${res.status})`);
            }
            setDishSets(Array.isArray(data) ? data.map(normalizeSet) : []);
        } catch (err) {
            console.error("Ошибка загрузки наборов:", err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadSets();
    }, [loadSets]);

    useEffect(() => {
        if (!dishSets.length) return;
        loadSets();
    }, [dishes, dishSets.length, loadSets]);

    const uploadImage = async (file) => {
        if (!file) return "";
        const body = new FormData();
        body.append("file", file);
        body.append("folder", "dish-sets");

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

    const updateSet = async (setId, payload) => {
        const res = await fetch(`${API_DISH_SETS}/${setId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const text = await res.text();
        const data = parseJsonSafe(text);
        if (!res.ok) {
            throw new Error(data?.message || `Ошибка обновления набора (${res.status})`);
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
            console.error("Ошибка загрузки изображения набора:", err);
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
            console.error("Ошибка загрузки изображения набора:", err);
            setEditError(err.message || "Не удалось загрузить изображение");
        } finally {
            setEditImageUploading(false);
        }
    };

    const handleCreateSet = async () => {
        const payload = buildSetPayload(createForm);

        if (!payload.setName) {
            setCreateError("Введите название набора");
            return;
        }

        if (!Number.isFinite(payload.price) || payload.price <= 0) {
            setCreateError("Укажите корректную цену набора");
            return;
        }

        if (!payload.items.length) {
            setCreateError("Добавьте в набор хотя бы одно блюдо");
            return;
        }

        if (hasDuplicateSetName(payload.setName)) {
            setCreateError("Набор с таким названием уже существует");
            return;
        }

        setCreateLoading(true);
        setCreateError("");
        try {
            const res = await fetch(API_DISH_SETS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const text = await res.text();
            const data = parseJsonSafe(text);
            if (!res.ok) {
                throw new Error(data?.message || `Ошибка создания набора (${res.status})`);
            }
            setCreateForm(createSetForm());
            await loadSets();
        } catch (err) {
            console.error("Ошибка создания набора:", err);
            setCreateError(err.message || "Не удалось создать набор");
        } finally {
            setCreateLoading(false);
        }
    };

    const openEditModal = (setItem) => {
        setEditingSet(setItem);
        setEditForm({
            setName: setItem.setName || "",
            price: setItem.price ?? "",
            imageUrl: setItem.imageUrl || "",
            items: normalizeSetItems(setItem.items)
        });
        setEditError("");
        setEditModalOpen(true);
    };

    const closeEditModal = () => {
        if (editLoading || editImageUploading) return;
        setEditModalOpen(false);
        setEditingSet(null);
        setEditForm(createSetForm());
        setEditError("");
    };

    const handleSaveSet = async () => {
        if (!editingSet) return;
        const payload = buildSetPayload(editForm);

        if (!payload.setName) {
            setEditError("Введите название набора");
            return;
        }

        if (!Number.isFinite(payload.price) || payload.price <= 0) {
            setEditError("Укажите корректную цену набора");
            return;
        }

        if (!payload.items.length) {
            setEditError("Добавьте в набор хотя бы одно блюдо");
            return;
        }

        if (hasDuplicateSetName(payload.setName, editingSet.setId)) {
            setEditError("Набор с таким названием уже существует");
            return;
        }

        setEditLoading(true);
        setEditError("");
        try {
            await updateSet(editingSet.setId, payload);
            await loadSets();
            closeEditModal();
        } catch (err) {
            console.error("Ошибка обновления набора:", err);
            setEditError(err.message || "Не удалось сохранить изменения");
        } finally {
            setEditLoading(false);
        }
    };

    const deleteSet = async (setItem) => {
        if (!setItem?.setId) return;
        if (!window.confirm(`Удалить набор "${setItem.setName}"?`)) return;

        setDeletingSetId(setItem.setId);
        try {
            const res = await fetch(`${API_DISH_SETS}/${setItem.setId}`, {
                method: "DELETE"
            });
            if (!res.ok) {
                const raw = await res.text().catch(() => "");
                const data = parseJsonSafe(raw);
                throw new Error(data?.message || data?.error || raw || `Ошибка удаления набора (${res.status})`);
            }
            setDishSets((prev) => prev.filter((item) => item.setId !== setItem.setId));
        } catch (err) {
            console.error("Ошибка удаления набора:", err);
            window.alert(err.message || "Не удалось удалить набор");
        } finally {
            setDeletingSetId(null);
        }
    };

    const composerInitialItems = useMemo(() => (
        composerTarget === "edit"
            ? normalizeSetItems(editForm.items)
            : normalizeSetItems(createForm.items)
    ), [composerTarget, createForm.items, editForm.items]);

    const handleCompositionConfirm = async (items) => {
        const normalized = normalizeSetItems(items);
        if (composerTarget === "edit") {
            setEditForm((prev) => ({ ...prev, items: normalized }));
            setEditError("");
            return;
        }
        setCreateForm((prev) => ({ ...prev, items: normalized }));
        setCreateError("");
    };

    const createSetCost = calculateSetCost(createForm.items);
    const editSetCost = calculateSetCost(editForm.items);

    return (
        <>
            <section className={styles.createCard}>
                <div className={styles.sectionHeading}>
                    <div>
                        <h2>Новый набор</h2>
                        <p>Соберите сет из существующих блюд, добавьте фото и цену, а себестоимость подтянется автоматически по составу.</p>
                    </div>
                    <div className={styles.counterChip}>{dishSets.length} шт.</div>
                </div>

                <div className={styles.formGrid}>
                    <label className={styles.field}>
                        <span>Название набора</span>
                        <input
                            type="text"
                            name="createSetName"
                            className={styles.input}
                            placeholder="Например, сет Филадельфия…"
                            autoComplete="off"
                            value={createForm.setName}
                            onChange={(e) => setCreateForm((prev) => ({ ...prev, setName: e.target.value }))}
                        />
                    </label>

                    <label className={styles.field}>
                        <span>Цена, ₽</span>
                        <input
                            type="number"
                            name="createSetPrice"
                            inputMode="decimal"
                            className={styles.input}
                            autoComplete="off"
                            min="0"
                            step="0.01"
                            value={createForm.price}
                            onChange={(e) => setCreateForm((prev) => ({ ...prev, price: e.target.value }))}
                        />
                    </label>

                    <label className={styles.field}>
                        <span>Себестоимость, ₽</span>
                        <input
                            type="text"
                            name="createSetCost"
                            className={styles.input}
                            value={`${formatMoney(createSetCost)} ₽`}
                            readOnly
                        />
                    </label>
                </div>

                <div className={styles.compositionPanel}>
                    <div className={styles.compositionHeader}>
                        <div>
                            <div className={styles.imageHeading}>Состав набора</div>
                            <div className={styles.imageHint}>
                                Выберите блюда, которые войдут в набор, и укажите количество каждой позиции.
                            </div>
                        </div>
                        <button type="button" className={styles.secondaryButton} onClick={() => setComposerTarget("create")}>
                            Выбрать состав
                        </button>
                    </div>

                    {createForm.items.length > 0 ? (
                        <>
                            <div className={styles.compositionSummary}>
                                {summarizeSetItems(createForm.items)}
                                {createForm.items.length > 4 ? "…" : ""}
                            </div>
                            <div className={styles.compositionChips}>
                                {normalizeSetItems(createForm.items).map((item) => (
                                    <span key={`create-set-item-${item.dishId}`} className={styles.compositionChip}>
                                        {item.dishName} x{item.qty}
                                    </span>
                                ))}
                            </div>
                        </>
                    ) : (
                        <div className={styles.compositionEmpty}>
                            Состав пока не выбран. Нажмите "Выбрать состав", чтобы собрать набор из блюд.
                        </div>
                    )}
                </div>

                <div className={styles.imagePanel}>
                    <div className={styles.imageInfo}>
                        <div className={styles.imageHeading}>Фото набора</div>
                        <div className={styles.imageHint}>
                            {createImageUploading
                                ? "Загружаем изображение…"
                                : createForm.imageUrl
                                    ? "Изображение набора загружено и будет сохранено вместе с карточкой."
                                    : "Добавьте фото, чтобы сет красиво смотрелся в меню."}
                        </div>
                    </div>

                    <div className={styles.imageControls}>
                        <input
                            type="file"
                            name="createSetImage"
                            accept="image/*"
                            className={styles.fileInput}
                            aria-label="Фото нового набора"
                            onChange={(e) => handleCreateImageUpload(e.target.files?.[0])}
                        />
                        {createForm.imageUrl ? (
                            <img
                                src={createForm.imageUrl}
                                alt="Превью набора"
                                className={styles.previewImage}
                                width="96"
                                height="96"
                            />
                        ) : (
                            <div className={styles.previewPlaceholder}>Нет фото</div>
                        )}
                    </div>
                </div>

                <div className={styles.formActions}>
                    <button
                        type="button"
                        className={styles.primaryButton}
                        onClick={handleCreateSet}
                        disabled={createLoading || createImageUploading}
                    >
                        {createLoading ? "Создаём набор…" : "Создать набор"}
                    </button>
                </div>

                {createError && <div className={styles.errorBox} role="alert">{createError}</div>}
            </section>

            <section className={styles.listSection}>
                <div className={styles.sectionHeading}>
                    <div>
                        <h2>Все наборы</h2>
                        <p>Смотрите состав, себестоимость и фото каждого сетта, а при необходимости быстро правьте его карточку.</p>
                    </div>
                </div>

                {loading ? (
                    <div className={styles.emptyState} role="status">Загружаем наборы…</div>
                ) : dishSets.length === 0 ? (
                    <div className={styles.emptyState}>
                        Пока нет ни одного набора. Создайте первый сверху и соберите его состав из блюд.
                    </div>
                ) : (
                    <div className={styles.cardsGrid}>
                        {dishSets.map((setItem) => (
                            <article key={setItem.setId} className={styles.card}>
                                <div className={styles.cardMedia}>
                                    {setItem.imageUrl ? (
                                        <img
                                            src={setItem.imageUrl}
                                            alt={setItem.setName}
                                            className={styles.cardImage}
                                            width="480"
                                            height="320"
                                            loading="lazy"
                                        />
                                    ) : (
                                        <div className={styles.previewPlaceholder}>Нет фото</div>
                                    )}
                                </div>

                                <div className={styles.cardHeader}>
                                    <div>
                                        <div className={styles.cardId}>#{setItem.setId}</div>
                                        <h3 className={styles.cardTitle}>{setItem.setName}</h3>
                                    </div>
                                    <div className={styles.priceChip}>{formatMoney(setItem.price)} ₽</div>
                                </div>

                                <div className={styles.metricsRow}>
                                    <div className={styles.metric}>
                                        <span className={styles.metricLabel}>Блюд в составе</span>
                                        <strong>{setItem.items.length}</strong>
                                    </div>
                                    <div className={styles.metric}>
                                        <span className={styles.metricLabel}>Себестоимость</span>
                                        <strong>{formatMoney(setItem.firstCost)} ₽</strong>
                                    </div>
                                </div>

                                <div className={styles.metaList}>
                                    <div className={styles.metaRow}>
                                        <span className={styles.metaLabel}>Порций в наборе</span>
                                        <span>{countSetPortions(setItem.items)}</span>
                                    </div>
                                    <div className={styles.metaRow}>
                                        <span className={styles.metaLabel}>Фото</span>
                                        <span>{setItem.imageUrl ? "Загружено" : "Не добавлено"}</span>
                                    </div>
                                </div>

                                <div className={styles.compositionPanel}>
                                    <div className={styles.compositionHeader}>
                                        <div className={styles.imageHeading}>Состав</div>
                                    </div>
                                    <div className={styles.compositionChips}>
                                        {normalizeSetItems(setItem.items).map((item) => (
                                            <span key={`set-card-item-${setItem.setId}-${item.dishId}`} className={styles.compositionChip}>
                                                {item.dishName} x{item.qty}
                                            </span>
                                        ))}
                                    </div>
                                </div>

                                <div className={styles.cardActions}>
                                    <button type="button" className={styles.secondaryButton} onClick={() => openEditModal(setItem)}>
                                        Редактировать
                                    </button>
                                    <button
                                        type="button"
                                        className={styles.dangerButton}
                                        onClick={() => deleteSet(setItem)}
                                        disabled={deletingSetId === setItem.setId}
                                    >
                                        {deletingSetId === setItem.setId ? "Удаляем…" : "Удалить"}
                                    </button>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>

            {editModalOpen && editingSet && (
                <div className={styles.modalOverlay} onClick={closeEditModal}>
                    <div
                        className={styles.modal}
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="edit-set-title"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className={styles.modalHeader}>
                            <div>
                                <p className={styles.eyebrow}>Редактирование набора</p>
                                <h2 id="edit-set-title" className={styles.modalTitle}>{editingSet.setName}</h2>
                                <p className={styles.modalSubtitle}>
                                    Обновите название, цену, фото и состав набора. Себестоимость пересчитается автоматически по блюдам внутри.
                                </p>
                            </div>
                            <button type="button" className={styles.closeButton} onClick={closeEditModal}>
                                Закрыть
                            </button>
                        </div>

                        <div className={styles.modalBody}>
                            <div className={styles.formGrid}>
                                <label className={styles.field}>
                                    <span>Название набора</span>
                                    <input
                                        type="text"
                                        name="editSetName"
                                        className={styles.input}
                                        autoComplete="off"
                                        value={editForm.setName}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, setName: e.target.value }))}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Цена, ₽</span>
                                    <input
                                        type="number"
                                        name="editSetPrice"
                                        inputMode="decimal"
                                        className={styles.input}
                                        autoComplete="off"
                                        min="0"
                                        step="0.01"
                                        value={editForm.price}
                                        onChange={(e) => setEditForm((prev) => ({ ...prev, price: e.target.value }))}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Себестоимость, ₽</span>
                                    <input
                                        type="text"
                                        name="editSetCost"
                                        className={styles.input}
                                        value={`${formatMoney(editSetCost)} ₽`}
                                        readOnly
                                    />
                                </label>
                            </div>

                            <div className={styles.compositionPanel}>
                                <div className={styles.compositionHeader}>
                                    <div>
                                        <div className={styles.imageHeading}>Состав набора</div>
                                        <div className={styles.imageHint}>
                                            Можно заменить блюда, добавить новые позиции или изменить количество каждой из них.
                                        </div>
                                    </div>
                                    <button type="button" className={styles.secondaryButton} onClick={() => setComposerTarget("edit")}>
                                        Изменить состав
                                    </button>
                                </div>

                                {editForm.items.length > 0 ? (
                                    <div className={styles.compositionChips}>
                                        {normalizeSetItems(editForm.items).map((item) => (
                                            <span key={`edit-set-item-${item.dishId}`} className={styles.compositionChip}>
                                                {item.dishName} x{item.qty}
                                            </span>
                                        ))}
                                    </div>
                                ) : (
                                    <div className={styles.compositionEmpty}>
                                        Состав пустой. Добавьте блюда в набор через кнопку выше.
                                    </div>
                                )}
                            </div>

                            <div className={styles.imagePanel}>
                                <div className={styles.imageInfo}>
                                    <div className={styles.imageHeading}>Фото набора</div>
                                    <div className={styles.imageHint}>
                                        {editImageUploading
                                            ? "Загружаем новое изображение…"
                                            : editForm.imageUrl
                                                ? "Фото набора обновится после сохранения."
                                                : "Можно добавить или заменить фото прямо отсюда."}
                                    </div>
                                </div>

                                <div className={styles.imageControls}>
                                    <input
                                        type="file"
                                        name="editSetImage"
                                        accept="image/*"
                                        className={styles.fileInput}
                                        aria-label="Новое фото набора"
                                        onChange={(e) => handleEditImageUpload(e.target.files?.[0])}
                                    />
                                    {editForm.imageUrl ? (
                                        <img
                                            src={editForm.imageUrl}
                                            alt="Превью набора"
                                            className={styles.previewImage}
                                            width="96"
                                            height="96"
                                        />
                                    ) : (
                                        <div className={styles.previewPlaceholder}>Нет фото</div>
                                    )}
                                </div>
                            </div>

                            {editError && <div className={styles.errorBox} role="alert">{editError}</div>}
                        </div>

                        <div className={styles.modalActions}>
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
                                onClick={handleSaveSet}
                                disabled={editLoading || editImageUploading}
                            >
                                {editLoading ? "Сохраняем…" : "Сохранить изменения"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <DishPickerModal
                isOpen={Boolean(composerTarget)}
                onClose={() => setComposerTarget(null)}
                dishes={dishes}
                categories={categories}
                initialItems={composerInitialItems}
                onConfirm={handleCompositionConfirm}
                disabled={createLoading || editLoading}
            />
        </>
    );
}
