import { useParams } from "react-router-dom";
import { useCallback, useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "../../auth";
import styles from "./TechCardPage.module.css";

const API_TECH = `${API_BASE_URL}/api/tech-products`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;
const API_DISHES = `${API_BASE_URL}/api/dishes`;
const API_SUPPLIERS = `${API_BASE_URL}/api/supplier`;
const API_PREPARATIONS = `${API_BASE_URL}/api/preparations`;

const UNIT_PRESETS = {
    g: { baseUnit: "g", unitFactor: "1" },
    kg: { baseUnit: "g", unitFactor: "1000" },
    ml: { baseUnit: "ml", unitFactor: "1" },
    l: { baseUnit: "ml", unitFactor: "1000" },
    pcs: { baseUnit: "pcs", unitFactor: "1" }
};

const createEmptyIngredientForm = () => ({
    supplierId: "",
    productName: "",
    productPrice: "",
    waste: "0",
    unit: "g",
    baseUnit: "g",
    unitFactor: "1"
});

const toSafeNumber = (value, fallback = 0) => {
    if (value === null || value === undefined || value === "") {
        return fallback;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
};

const getWasteMultiplier = (waste) => {
    const normalizedWaste = Math.min(100, Math.max(0, toSafeNumber(waste, 0)));
    return 1 + (normalizedWaste / 100);
};

const getSafeUnitFactor = (product) => {
    const factor = toSafeNumber(product?.unitFactor, 1);
    return factor > 0 ? factor : 1;
};

const getEffectiveBasePrice = (product) => {
    const averageStockPrice = toSafeNumber(product?.averageStockPrice, NaN);
    if (Number.isFinite(averageStockPrice)) {
        return averageStockPrice;
    }

    const directPrice = toSafeNumber(product?.productPrice, 0);
    return directPrice / getSafeUnitFactor(product);
};

const formatQuantity = (value) => {
    const num = toSafeNumber(value, 0);
    return num.toLocaleString("ru-RU", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
};

export default function TechCardPage() {
    const { dishId, preparationId } = useParams();
    const ownerType = preparationId ? "preparation" : "dish";
    const ownerId = Number(preparationId ?? dishId);

    const [ownerName, setOwnerName] = useState("");
    const [dishPrice, setDishPrice] = useState(null);
    const [outputWeight, setOutputWeight] = useState(null);
    const [items, setItems] = useState([]);
    const [products, setProducts] = useState([]);
    const [preparations, setPreparations] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [selectedIngredientType, setSelectedIngredientType] = useState("product");
    const [selectedIngredientId, setSelectedIngredientId] = useState("");
    const [weight, setWeight] = useState("");
    const [waste, setWaste] = useState("");
    const [editingTechProductId, setEditingTechProductId] = useState(null);
    const [ingredientPickerOpen, setIngredientPickerOpen] = useState(false);
    const [ingredientSearch, setIngredientSearch] = useState("");
    const [ingredientTab, setIngredientTab] = useState("products");
    const [createIngredientOpen, setCreateIngredientOpen] = useState(false);
    const [createIngredientForm, setCreateIngredientForm] = useState(createEmptyIngredientForm);
    const [createIngredientError, setCreateIngredientError] = useState("");
    const [createIngredientLoading, setCreateIngredientLoading] = useState(false);

    const normalizeName = (name) => String(name || "").trim().toLowerCase();

    const productsById = useMemo(() => {
        const map = new Map();
        products.forEach((product) => map.set(product.productId, product));
        return map;
    }, [products]);

    const preparationsById = useMemo(() => {
        const map = new Map();
        preparations.forEach((preparation) => map.set(preparation.preparationId, preparation));
        return map;
    }, [preparations]);

    const groupedProducts = useMemo(() => {
        const groups = new Map();

        products.forEach((product) => {
            const key = normalizeName(product.productName);
            if (!key) return;

            if (!groups.has(key)) {
                groups.set(key, {
                    key,
                    name: product.productName,
                    representativeId: product.productId,
                    prices: []
                });
            }

            const group = groups.get(key);
            const price = getEffectiveBasePrice(product);
            if (Number.isFinite(price) && price >= 0) {
                group.prices.push(price);
            }
        });

        return Array.from(groups.values())
            .map((group) => ({
                ...group,
                averagePrice: group.prices.length
                    ? group.prices.reduce((sum, value) => sum + value, 0) / group.prices.length
                    : 0
            }))
            .sort((a, b) => a.name.localeCompare(b.name, "ru"));
    }, [products]);

    const groupedPreparations = useMemo(() => (
        [...preparations]
            .filter((preparation) => Number(preparation.preparationId) !== ownerId || ownerType !== "preparation")
            .sort((a, b) => String(a.preparationName || "").localeCompare(String(b.preparationName || ""), "ru"))
    ), [ownerId, ownerType, preparations]);

    const representativeIdByName = useMemo(() => {
        const map = new Map();
        groupedProducts.forEach((group) => map.set(group.key, group.representativeId));
        return map;
    }, [groupedProducts]);

    const selectedProductGroup = useMemo(() => {
        if (selectedIngredientType !== "product" || !selectedIngredientId) return null;
        return groupedProducts.find((group) => String(group.representativeId) === String(selectedIngredientId)) || null;
    }, [groupedProducts, selectedIngredientId, selectedIngredientType]);

    const selectedPreparation = useMemo(() => {
        if (selectedIngredientType !== "preparation" || !selectedIngredientId) return null;
        return groupedPreparations.find((preparation) => String(preparation.preparationId) === String(selectedIngredientId)) || null;
    }, [groupedPreparations, selectedIngredientId, selectedIngredientType]);

    const selectedProduct = useMemo(() => {
        if (selectedIngredientType !== "product" || !selectedIngredientId) return null;
        return productsById.get(Number(selectedIngredientId)) || null;
    }, [productsById, selectedIngredientId, selectedIngredientType]);

    const filteredGroupedProducts = useMemo(() => {
        const searchTerm = ingredientSearch.trim().toLowerCase();
        if (!searchTerm) return groupedProducts;
        return groupedProducts.filter((group) => String(group.name || "").toLowerCase().includes(searchTerm));
    }, [groupedProducts, ingredientSearch]);

    const filteredPreparations = useMemo(() => {
        const searchTerm = ingredientSearch.trim().toLowerCase();
        if (!searchTerm) return groupedPreparations;
        return groupedPreparations.filter((preparation) => String(preparation.preparationName || "").toLowerCase().includes(searchTerm));
    }, [groupedPreparations, ingredientSearch]);

    const ingredientMeasureUnit = useMemo(() => {
        if (selectedIngredientType === "product") {
            return selectedProduct?.baseUnit || selectedProduct?.unit || "g";
        }
        if (selectedIngredientType === "preparation") {
            return "g";
        }
        return "g";
    }, [selectedIngredientType, selectedProduct]);

    const weightInputPlaceholder = useMemo(() => {
        if (!selectedIngredientId) {
            return "Количество";
        }
        return `Количество (${ingredientMeasureUnit})`;
    }, [ingredientMeasureUnit, selectedIngredientId]);

    const getAveragePriceForProductId = (id) => {
        const product = productsById.get(id);
        if (!product) return 0;
        return Number(getEffectiveBasePrice(product));
    };

    const getItemMeasureUnit = (item) => {
        if (item?.productId != null) {
            const product = productsById.get(item.productId);
            return product?.baseUnit || product?.unit || "g";
        }
        if (item?.ingredientPreparationId != null) {
            return "g";
        }
        return "g";
    };

    const loadOwner = useCallback(async () => {
        try {
            if (ownerType === "dish") {
                const res = await fetch(`${API_DISHES}/${ownerId}`);
                const data = await res.json();
                setOwnerName(data.dishName ?? "");
                setDishPrice(data.price ?? data.dishPrice ?? null);
                setOutputWeight(null);
                return;
            }

            const res = await fetch(`${API_PREPARATIONS}/${ownerId}`);
            const data = await res.json();
            setOwnerName(data.preparationName ?? "");
            setOutputWeight(data.outputWeight ?? null);
            setDishPrice(null);
        } catch (err) {
            console.error("Ошибка загрузки сущности для техкарты:", err);
        }
    }, [ownerId, ownerType]);

    const loadTechCard = useCallback(async () => {
        try {
            const endpoint = ownerType === "dish"
                ? `${API_TECH}/dish/${ownerId}`
                : `${API_TECH}/preparation/${ownerId}`;
            const res = await fetch(endpoint);
            const data = await res.json();
            if (Array.isArray(data)) setItems(data);
            else if (data) setItems([data]);
            else setItems([]);
        } catch (err) {
            console.error("Ошибка загрузки техкарты:", err);
        }
    }, [ownerId, ownerType]);

    const loadProducts = useCallback(async () => {
        try {
            const res = await fetch(API_PRODUCTS);
            const data = await res.json();
            const nextProducts = Array.isArray(data) ? data : [];
            setProducts(nextProducts);
            return nextProducts;
        } catch (err) {
            console.error("Ошибка загрузки продуктов:", err);
            setProducts([]);
            return [];
        }
    }, []);

    const loadPreparations = useCallback(async () => {
        try {
            const res = await fetch(API_PREPARATIONS);
            const data = await res.json();
            const nextPreparations = Array.isArray(data) ? data : [];
            setPreparations(nextPreparations);
            return nextPreparations;
        } catch (err) {
            console.error("Ошибка загрузки заготовок:", err);
            setPreparations([]);
            return [];
        }
    }, []);

    const loadSuppliers = useCallback(async () => {
        try {
            const res = await fetch(API_SUPPLIERS);
            if (!res.ok) {
                if (res.status === 401 || res.status === 403) {
                    setSuppliers([]);
                    return;
                }
                throw new Error(`Ошибка загрузки поставщиков (${res.status})`);
            }
            const data = await res.json();
            setSuppliers(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error("Ошибка загрузки поставщиков:", err);
            setSuppliers([]);
        }
    }, []);

    useEffect(() => {
        loadOwner();
        loadTechCard();
        loadProducts();
        loadPreparations();
        loadSuppliers();
    }, [loadOwner, loadPreparations, loadProducts, loadSuppliers, loadTechCard]);

    const openIngredientPicker = () => {
        setIngredientSearch("");
        setIngredientTab(selectedIngredientType === "preparation" ? "preparations" : "products");
        setIngredientPickerOpen(true);
    };

    const closeIngredientPicker = () => {
        setIngredientPickerOpen(false);
        setIngredientSearch("");
    };

    const selectProductIngredient = (group) => {
        setSelectedIngredientType("product");
        setSelectedIngredientId(String(group.representativeId));
        closeIngredientPicker();
    };

    const selectPreparationIngredient = (preparation) => {
        setSelectedIngredientType("preparation");
        setSelectedIngredientId(String(preparation.preparationId));
        closeIngredientPicker();
    };

    const openCreateIngredientModal = () => {
        setCreateIngredientForm(createEmptyIngredientForm());
        setCreateIngredientError("");
        setIngredientPickerOpen(false);
        setCreateIngredientOpen(true);
    };

    const closeCreateIngredientModal = () => {
        if (createIngredientLoading) return;
        setCreateIngredientOpen(false);
        setCreateIngredientError("");
    };

    const handleCreateIngredientChange = (field, value) => {
        setCreateIngredientForm((prev) => {
            if (field === "unit") {
                const preset = UNIT_PRESETS[value] ?? UNIT_PRESETS.g;
                return { ...prev, unit: value, baseUnit: preset.baseUnit, unitFactor: preset.unitFactor };
            }
            return { ...prev, [field]: value };
        });
    };

    const addOrUpdateItem = () => {
        if (!selectedIngredientId || !weight) return;

        const payload = {
            weight: parseFloat(weight),
            waste: waste ? parseFloat(waste) : 0,
            [ownerType === "dish" ? "dishId" : "preparationId"]: ownerId
        };

        if (selectedIngredientType === "product") {
            const selectedProduct = productsById.get(parseInt(selectedIngredientId, 10));
            const selectedNameKey = normalizeName(selectedProduct?.productName);
            const representativeId = representativeIdByName.get(selectedNameKey) ?? parseInt(selectedIngredientId, 10);
            payload.productId = representativeId;
            payload.ingredientPreparationId = null;
        } else {
            payload.productId = null;
            payload.ingredientPreparationId = parseInt(selectedIngredientId, 10);
        }

        const url = editingTechProductId ? `${API_TECH}/${editingTechProductId}` : API_TECH;
        const method = editingTechProductId ? "PUT" : "POST";

        fetch(url, {
            method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(
                editingTechProductId
                    ? { ...payload, techProductId: editingTechProductId }
                    : payload
            )
        })
            .then((res) => {
                if (!res.ok) {
                    throw new Error(
                        editingTechProductId
                            ? "Ошибка обновления ингредиента"
                            : "Ошибка добавления ингредиента"
                    );
                }
                return res.json();
            })
            .then(() => {
                setSelectedIngredientId("");
                setSelectedIngredientType("product");
                setWeight("");
                setWaste("");
                setEditingTechProductId(null);
                loadTechCard();
            })
            .catch((err) => console.error(err));
    };

    const startEditItem = (item) => {
        setEditingTechProductId(item.techProductId);
        if (item.ingredientPreparationId != null) {
            setSelectedIngredientType("preparation");
            setSelectedIngredientId(String(item.ingredientPreparationId));
            setIngredientTab("preparations");
        } else {
            const currentProduct = productsById.get(item.productId);
            const nameKey = normalizeName(currentProduct?.productName);
            const representativeId = representativeIdByName.get(nameKey) ?? item.productId;
            setSelectedIngredientType("product");
            setSelectedIngredientId(String(representativeId));
            setIngredientTab("products");
        }
        setWeight(String(item.weight ?? ""));
        setWaste(item.waste !== undefined && item.waste !== null ? String(item.waste) : "");
        setIngredientSearch("");
    };

    const cancelEdit = () => {
        setEditingTechProductId(null);
        setSelectedIngredientId("");
        setSelectedIngredientType("product");
        setWeight("");
        setWaste("");
    };

    const deleteItem = (item) => {
        if (!item) return;

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
            .then((res) => {
                if (!res.ok) throw new Error("Ошибка удаления ингредиента");
            })
            .then(() => {
                setItems((prev) =>
                    prev.filter((entry) => {
                        const currentId =
                            entry.techProductId ??
                            entry.techProductID ??
                            entry.id ??
                            entry.techId;
                        return currentId !== idToDelete;
                    })
                );
            })
            .catch((err) => console.error(err));
    };

    const submitCreateIngredient = async (e) => {
        e.preventDefault();

        const trimmedName = createIngredientForm.productName.trim();
        if (!trimmedName) {
            setCreateIngredientError("Введите название ингредиента");
            return;
        }

        if (createIngredientForm.productPrice === "" || Number(createIngredientForm.productPrice) < 0) {
            setCreateIngredientError("Укажите корректную цену");
            return;
        }

        setCreateIngredientLoading(true);
        setCreateIngredientError("");

        try {
            const payload = {
                supplierId: createIngredientForm.supplierId
                    ? Number(createIngredientForm.supplierId)
                    : null,
                productName: trimmedName,
                productPrice: Number(createIngredientForm.productPrice),
                waste: createIngredientForm.waste === "" ? 0 : Number(createIngredientForm.waste),
                isFavorite: false,
                unit: createIngredientForm.unit,
                baseUnit: createIngredientForm.baseUnit,
                unitFactor: Number(createIngredientForm.unitFactor)
            };

            const res = await fetch(API_PRODUCTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const rawError = await res.text().catch(() => "");
                let errorData = null;
                try {
                    errorData = rawError ? JSON.parse(rawError) : null;
                } catch {
                    errorData = null;
                }
                throw new Error(
                    errorData?.message ||
                    rawError ||
                    `Ошибка создания ингредиента (${res.status})`
                );
            }

            const createdIngredient = await res.json().catch(() => null);
            const refreshedProducts = await loadProducts();
            const createdNameKey = normalizeName(createdIngredient?.productName ?? trimmedName);
            const matchedProduct = refreshedProducts.find(
                (product) => normalizeName(product.productName) === createdNameKey
            );

            if (matchedProduct?.productId != null) {
                setSelectedIngredientType("product");
                setSelectedIngredientId(String(matchedProduct.productId));
            } else if (createdIngredient?.productId != null) {
                setSelectedIngredientType("product");
                setSelectedIngredientId(String(createdIngredient.productId));
            }

            setCreateIngredientOpen(false);
            setIngredientSearch("");
            setCreateIngredientForm(createEmptyIngredientForm());
        } catch (err) {
            console.error(err);
            setCreateIngredientError(err.message || "Не удалось создать ингредиент");
        } finally {
            setCreateIngredientLoading(false);
        }
    };

    const containsPreparationIngredients = items.some((item) => item.ingredientPreparationId != null);

    const getPreparationUnitCost = (preparation) => {
        if (!preparation) return 0;
        const output = toSafeNumber(preparation.outputWeight, 0);
        if (output <= 0) return 0;
        return toSafeNumber(preparation.cost, 0) / output;
    };

    const getItemCost = (item) => {
        const adjustedQuantity = toSafeNumber(item?.weight, 0) * getWasteMultiplier(item?.waste);
        if (adjustedQuantity <= 0) return 0;

        if (item?.productId != null) {
            const price = getAveragePriceForProductId(item.productId);
            return adjustedQuantity * price;
        }

        if (item?.ingredientPreparationId != null) {
            const preparation = preparationsById.get(item.ingredientPreparationId);
            return adjustedQuantity * getPreparationUnitCost(preparation);
        }

        return 0;
    };

    const totalCost = items.reduce((sum, item) => sum + getItemCost(item), 0);

    const title = ownerType === "dish"
        ? `Техкарта блюда: ${ownerName || `#${ownerId}`}`
        : `Техкарта заготовки: ${ownerName || `#${ownerId}`}`;

    const selectedLabel = selectedIngredientType === "preparation"
        ? selectedPreparation?.preparationName
        : selectedProductGroup?.name;

    return (
        <div className={styles.container}>
            <h2 className={styles.title}>{title}</h2>

            <div className={styles.formRow}>
                <div className={styles.ingredientChooser}>
                    <button
                        type="button"
                        onClick={openIngredientPicker}
                        className={styles.pickerButton}
                    >
                        <span className={styles.pickerButtonLabel}>Ингредиент</span>
                        <span className={styles.pickerButtonValue}>
                            {selectedLabel || "Выберите продукт или заготовку"}
                        </span>
                    </button>

                    {selectedIngredientType === "product" && selectedProductGroup && (
                        <div className={styles.selectedMeta}>
                            Продукт • средняя цена: {Number(selectedProductGroup.averagePrice || 0).toFixed(2)} ₽
                        </div>
                    )}

                    {selectedIngredientType === "preparation" && selectedPreparation && (
                        <div className={styles.selectedMeta}>
                            Заготовка • выход партии: {Number(selectedPreparation.outputWeight || 0).toFixed(2)} г
                        </div>
                    )}
                </div>

                <button
                    type="button"
                    onClick={openCreateIngredientModal}
                    className={styles.secondaryButton}
                >
                    Создать ингредиент
                </button>

                {selectedIngredientId && (
                    <div className={styles.selectedMeta}>
                        Количество для этого ингредиента указывается в {ingredientMeasureUnit}
                    </div>
                )}

                <input
                    type="number"
                    placeholder={weightInputPlaceholder}
                    title={`Количество будет указано в ${ingredientMeasureUnit}`}
                    value={weight}
                    onChange={(e) => setWeight(e.target.value)}
                    min={0}
                    className={styles.input}
                />

                <input
                    type="number"
                    placeholder="Отход (%)"
                    value={waste}
                    onChange={(e) => setWaste(e.target.value)}
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
                    items.map((item) => {
                        const product = item.productId != null ? productsById.get(item.productId) : null;
                        const preparation = item.ingredientPreparationId != null
                            ? preparationsById.get(item.ingredientPreparationId)
                            : null;
                        const itemMeasureUnit = getItemMeasureUnit(item);
                        const price = item.productId != null ? getAveragePriceForProductId(item.productId) : null;
                        const preparationUnitCost = item.ingredientPreparationId != null
                            ? getPreparationUnitCost(preparation)
                            : null;
                        const cost = getItemCost(item);
                        const costLabel = item.productId != null
                            ? `${Number(price || 0).toFixed(4)} ₽/${product?.baseUnit ?? product?.unit ?? "ед."}`
                            : `${Number(preparationUnitCost || 0).toFixed(4)} ₽/г`;

                        return (
                            <li key={item.techProductId} className={styles.ingredientItem}>
                                <div className={styles.ingredientMain}>
                                    <strong>{product?.productName || preparation?.preparationName || "Неизвестный"}</strong>
                                    {" — "}
                                    <span className={styles.ingredientBadge}>
                                        {item.productId != null ? "Продукт" : "Заготовка"}
                                    </span>
                                    {" — "}
                                    {formatQuantity(item.weight)} {itemMeasureUnit}
                                    {" — "}
                                    отход: {Number(item.waste || 0).toFixed(2)}%
                                    {(price != null || preparationUnitCost != null) && (
                                        <>
                                            {" — "}
                                            цена: {costLabel}
                                            {" — "}
                                            себестоимость: {cost.toFixed(2)} ₽
                                        </>
                                    )}
                                    {item.ingredientPreparationId != null && preparation?.outputWeight != null && (
                                        <>
                                            {" — "}
                                            выход партии: {Number(preparation.outputWeight).toFixed(2)} г
                                        </>
                                    )}
                                </div>
                                <div className={styles.ingredientActions}>
                                    <button
                                        onClick={() => startEditItem(item)}
                                        className={styles.secondaryButton}
                                    >
                                        Редактировать
                                    </button>
                                    <button
                                        onClick={() => deleteItem(item)}
                                        className={styles.dangerButton}
                                    >
                                        Удалить
                                    </button>
                                </div>
                            </li>
                        );
                    })
                ) : (
                    <li className={styles.emptyText}>Ингредиентов пока нет</li>
                )}
            </ul>

            <div className={styles.totalCostBlock}>
                <span className={styles.totalCost}>Полная себестоимость: {totalCost.toFixed(2)} ₽</span>
                {dishPrice != null && ownerType === "dish" && (
                    <span className={styles.totalCost}>Цена блюда: {Number(dishPrice).toFixed(2)} ₽</span>
                )}
                {outputWeight != null && ownerType === "preparation" && (
                    <span className={styles.totalCost}>Выход заготовки: {Number(outputWeight).toFixed(2)} г</span>
                )}
            </div>

            {containsPreparationIngredients && (
                <div className={styles.hintText}>
                    Вложенные заготовки теперь тоже входят в расчёт себестоимости по их собственной техкарте.
                </div>
            )}

            {ingredientPickerOpen && (
                <div className={styles.modalOverlay} onClick={closeIngredientPicker}>
                    <div
                        className={styles.modal}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className={styles.modalHeader}>
                            <div>
                                <h3 className={styles.modalTitle}>Выберите ингредиент</h3>
                                <p className={styles.modalSubtitle}>
                                    Можно добавить как обычный продукт, так и уже готовую заготовку.
                                </p>
                            </div>
                            <button
                                type="button"
                                className={styles.closeButton}
                                onClick={closeIngredientPicker}
                            >
                                Закрыть
                            </button>
                        </div>

                        <div className={styles.modalToolbar}>
                            <input
                                type="text"
                                value={ingredientSearch}
                                onChange={(e) => setIngredientSearch(e.target.value)}
                                placeholder="Поиск ингредиента..."
                                className={styles.searchInput}
                                autoFocus
                            />
                            <button
                                type="button"
                                className={styles.primaryButton}
                                onClick={openCreateIngredientModal}
                            >
                                Создать ингредиент
                            </button>
                        </div>

                        <div className={styles.pickerTabs}>
                            <button
                                type="button"
                                className={`${styles.pickerTab} ${ingredientTab === "products" ? styles.activePickerTab : ""}`}
                                onClick={() => setIngredientTab("products")}
                            >
                                Продукты
                            </button>
                            <button
                                type="button"
                                className={`${styles.pickerTab} ${ingredientTab === "preparations" ? styles.activePickerTab : ""}`}
                                onClick={() => setIngredientTab("preparations")}
                            >
                                Заготовки
                            </button>
                        </div>

                        <div className={styles.modalList}>
                            {ingredientTab === "products" ? (
                                filteredGroupedProducts.length > 0 ? (
                                    filteredGroupedProducts.map((group) => (
                                        <button
                                            key={group.key}
                                            type="button"
                                            className={styles.ingredientOption}
                                            onClick={() => selectProductIngredient(group)}
                                        >
                                            <div className={styles.ingredientOptionContent}>
                                                <span className={styles.ingredientOptionName}>{group.name}</span>
                                                <span className={styles.ingredientOptionMeta}>Продукт</span>
                                            </div>
                                            <span className={styles.ingredientOptionMeta}>
                                                Средняя цена: {Number(group.averagePrice || 0).toFixed(2)} ₽/{productsById.get(group.representativeId)?.baseUnit || productsById.get(group.representativeId)?.unit || "ед."}
                                            </span>
                                        </button>
                                    ))
                                ) : (
                                    <div className={styles.emptyModalState}>
                                        По вашему запросу продукты не найдены.
                                    </div>
                                )
                            ) : (
                                filteredPreparations.length > 0 ? (
                                    filteredPreparations.map((preparation) => (
                                        <button
                                            key={preparation.preparationId}
                                            type="button"
                                            className={styles.ingredientOption}
                                            onClick={() => selectPreparationIngredient(preparation)}
                                        >
                                            <div className={styles.ingredientOptionContent}>
                                                <span className={styles.ingredientOptionName}>{preparation.preparationName}</span>
                                                <span className={styles.ingredientOptionMeta}>Заготовка</span>
                                            </div>
                                            <span className={styles.ingredientOptionMeta}>
                                                Выход: {Number(preparation.outputWeight || 0).toFixed(2)} г
                                            </span>
                                        </button>
                                    ))
                                ) : (
                                    <div className={styles.emptyModalState}>
                                        По вашему запросу заготовки не найдены.
                                    </div>
                                )
                            )}
                        </div>
                    </div>
                </div>
            )}

            {createIngredientOpen && (
                <div className={styles.modalOverlay} onClick={closeCreateIngredientModal}>
                    <div
                        className={styles.modal}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className={styles.modalHeader}>
                            <div>
                                <h3 className={styles.modalTitle}>Создать ингредиент</h3>
                                <p className={styles.modalSubtitle}>
                                    Новый продукт сразу появится в списке и подставится в форму.
                                </p>
                            </div>
                            <button
                                type="button"
                                className={styles.closeButton}
                                onClick={closeCreateIngredientModal}
                            >
                                Закрыть
                            </button>
                        </div>

                        <form onSubmit={submitCreateIngredient} className={styles.modalForm}>
                            <div className={styles.modalFormGrid}>
                                <label className={styles.field}>
                                    <span>Название</span>
                                    <input
                                        type="text"
                                        value={createIngredientForm.productName}
                                        onChange={(e) => handleCreateIngredientChange("productName", e.target.value)}
                                        placeholder="Например, сливки 20%"
                                        className={styles.input}
                                        autoFocus
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Цена</span>
                                    <input
                                        type="number"
                                        value={createIngredientForm.productPrice}
                                        onChange={(e) => handleCreateIngredientChange("productPrice", e.target.value)}
                                        placeholder="Цена за единицу"
                                        min="0"
                                        step="0.01"
                                        className={styles.input}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Отход (%)</span>
                                    <input
                                        type="number"
                                        value={createIngredientForm.waste}
                                        onChange={(e) => handleCreateIngredientChange("waste", e.target.value)}
                                        placeholder="0"
                                        min="0"
                                        max="100"
                                        step="0.01"
                                        className={styles.input}
                                    />
                                </label>

                                <label className={styles.field}>
                                    <span>Единица</span>
                                    <select
                                        value={createIngredientForm.unit}
                                        onChange={(e) => handleCreateIngredientChange("unit", e.target.value)}
                                        className={styles.select}
                                    >
                                        <option value="g">g</option>
                                        <option value="kg">kg</option>
                                        <option value="ml">ml</option>
                                        <option value="l">l</option>
                                        <option value="pcs">pcs</option>
                                    </select>
                                </label>

                                <label className={styles.field}>
                                    <span>Поставщик</span>
                                    <select
                                        value={createIngredientForm.supplierId}
                                        onChange={(e) => handleCreateIngredientChange("supplierId", e.target.value)}
                                        className={styles.select}
                                    >
                                        <option value="">Без поставщика</option>
                                        {suppliers.map((supplier) => {
                                            const supplierId = supplier.supplierId ?? supplier.supplierID ?? supplier.id;
                                            const supplierName = supplier.supplierName ?? supplier.name ?? `Поставщик #${supplierId}`;
                                            return (
                                                <option key={supplierId} value={supplierId}>
                                                    {supplierName}
                                                </option>
                                            );
                                        })}
                                    </select>
                                </label>

                                <div className={styles.field}>
                                    <span>Нормализация единицы</span>
                                    <div className={styles.unitHint}>
                                        Будет сохранено как `{createIngredientForm.unit}` в базовой единице `{createIngredientForm.baseUnit}`
                                        с коэффициентом {createIngredientForm.unitFactor}.
                                    </div>
                                </div>
                            </div>

                            {createIngredientError && (
                                <div className={styles.errorText}>{createIngredientError}</div>
                            )}

                            <div className={styles.modalActions}>
                                <button
                                    type="button"
                                    onClick={closeCreateIngredientModal}
                                    className={styles.secondaryButton}
                                    disabled={createIngredientLoading}
                                >
                                    Отмена
                                </button>
                                <button
                                    type="submit"
                                    className={styles.primaryButton}
                                    disabled={createIngredientLoading}
                                >
                                    {createIngredientLoading ? "Создание..." : "Создать ингредиент"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
