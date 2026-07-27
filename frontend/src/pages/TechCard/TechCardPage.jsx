import { useParams } from "react-router-dom";
import { useCallback, useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "../../auth";
import CreateIngredientModal from "./tech-card/CreateIngredientModal";
import IngredientEditor from "./tech-card/IngredientEditor";
import IngredientList from "./tech-card/IngredientList";
import IngredientPickerModal from "./tech-card/IngredientPickerModal";
import TechCardHeader from "./tech-card/TechCardHeader";
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

const formatMoney = (value) => {
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
    const [itemSaving, setItemSaving] = useState(false);
    const [itemError, setItemError] = useState("");
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
        setItemError("");
        setSelectedIngredientType("product");
        setSelectedIngredientId(String(group.representativeId));
        closeIngredientPicker();
    };

    const selectPreparationIngredient = (preparation) => {
        setItemError("");
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

    const addOrUpdateItem = async () => {
        const parsedWeight = Number(weight);
        const parsedWaste = waste === "" ? 0 : Number(waste);

        if (!selectedIngredientId) {
            setItemError("Выберите продукт или заготовку.");
            return;
        }
        if (!Number.isFinite(parsedWeight) || parsedWeight <= 0) {
            setItemError("Укажите количество больше 0.");
            return;
        }
        if (!Number.isFinite(parsedWaste) || parsedWaste < 0 || parsedWaste > 100) {
            setItemError("Отход должен быть от 0 до 100%.");
            return;
        }

        const payload = {
            weight: parsedWeight,
            waste: parsedWaste,
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

        setItemSaving(true);
        setItemError("");
        try {
            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(
                    editingTechProductId
                        ? { ...payload, techProductId: editingTechProductId }
                        : payload
                )
            });
            if (!res.ok) {
                throw new Error(
                    editingTechProductId
                        ? "Не удалось обновить ингредиент. Попробуйте снова."
                        : "Не удалось добавить ингредиент. Попробуйте снова."
                );
            }
            await res.json().catch(() => null);
            setSelectedIngredientId("");
            setSelectedIngredientType("product");
            setWeight("");
            setWaste("");
            setEditingTechProductId(null);
            await loadTechCard();
        } catch (err) {
            console.error(err);
            setItemError(err.message || "Не удалось сохранить строку техкарты.");
        } finally {
            setItemSaving(false);
        }
    };

    const startEditItem = (item) => {
        setItemError("");
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
        setItemError("");
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

    const selectedLabel = selectedIngredientType === "preparation"
        ? selectedPreparation?.preparationName
        : selectedProductGroup?.name;

    const ingredientRows = items.map((item, index) => {
        const product = item.productId != null ? productsById.get(item.productId) : null;
        const preparation = item.ingredientPreparationId != null
            ? preparationsById.get(item.ingredientPreparationId)
            : null;
        const itemMeasureUnit = getItemMeasureUnit(item);
        const price = item.productId != null ? getAveragePriceForProductId(item.productId) : null;
        const preparationUnitCost = item.ingredientPreparationId != null
            ? getPreparationUnitCost(preparation)
            : null;
        const unitCost = item.productId != null ? price : preparationUnitCost;
        const unitCostUnit = item.productId != null
            ? product?.baseUnit ?? product?.unit ?? "ед."
            : "г";
        const id = item.techProductId ?? item.techProductID ?? item.id ?? item.techId
            ?? `${item.productId ?? item.ingredientPreparationId}-${index}`;

        return {
            id,
            source: item,
            name: product?.productName || preparation?.preparationName || "Неизвестный ингредиент",
            typeLabel: item.productId != null ? "Продукт" : "Заготовка",
            quantityLabel: `${formatQuantity(item.weight)} ${itemMeasureUnit}`,
            wasteLabel: `${formatQuantity(item.waste)}%`,
            unitCostLabel: `${formatMoney(unitCost)} ₽/${unitCostUnit}`,
            costLabel: `${formatMoney(getItemCost(item))} ₽`,
            outputLabel: item.ingredientPreparationId != null && preparation?.outputWeight != null
                ? `${formatQuantity(preparation.outputWeight)} г`
                : ""
        };
    });

    return (
        <div className={styles.container}>
            <TechCardHeader
                ownerType={ownerType}
                ownerId={ownerId}
                ownerName={ownerName}
                itemCount={items.length}
                totalCost={totalCost}
                dishPrice={dishPrice}
                outputWeight={outputWeight}
                formatMoney={formatMoney}
                formatQuantity={formatQuantity}
            />

            <div className={styles.workspace}>
                <IngredientEditor
                    selectedLabel={selectedLabel}
                    selectedIngredientType={selectedIngredientType}
                    selectedProductGroup={selectedProductGroup}
                    selectedPreparation={selectedPreparation}
                    ingredientMeasureUnit={ingredientMeasureUnit}
                    selectedIngredientId={selectedIngredientId}
                    weight={weight}
                    waste={waste}
                    editing={Boolean(editingTechProductId)}
                    saving={itemSaving}
                    error={itemError}
                    onOpenPicker={openIngredientPicker}
                    onOpenCreateIngredient={openCreateIngredientModal}
                    onWeightChange={setWeight}
                    onWasteChange={setWaste}
                    onSubmit={addOrUpdateItem}
                    onCancel={cancelEdit}
                />
                <IngredientList
                    rows={ingredientRows}
                    containsPreparations={containsPreparationIngredients}
                    onEdit={startEditItem}
                    onDelete={deleteItem}
                />
            </div>

            {ingredientPickerOpen && (
                <IngredientPickerModal
                    search={ingredientSearch}
                    tab={ingredientTab}
                    products={filteredGroupedProducts}
                    preparations={filteredPreparations}
                    productsById={productsById}
                    onSearchChange={setIngredientSearch}
                    onTabChange={setIngredientTab}
                    onSelectProduct={selectProductIngredient}
                    onSelectPreparation={selectPreparationIngredient}
                    onCreateIngredient={openCreateIngredientModal}
                    onClose={closeIngredientPicker}
                />
            )}

            {createIngredientOpen && (
                <CreateIngredientModal
                    form={createIngredientForm}
                    suppliers={suppliers}
                    error={createIngredientError}
                    loading={createIngredientLoading}
                    onChange={handleCreateIngredientChange}
                    onSubmit={submitCreateIngredient}
                    onClose={closeCreateIngredientModal}
                />
            )}
        </div>
    );
}
