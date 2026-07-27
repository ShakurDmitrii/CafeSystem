import {
    useCallback,
    useDeferredValue,
    useEffect,
    useMemo,
    useState
} from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import ProductEditor from "./products-page/ProductEditor";
import ProductsCatalog from "./products-page/ProductsCatalog";
import ProductsHero from "./products-page/ProductsHero";
import styles from "./ProductsPage.module.css";

const API_PRODUCTS = `${API_BASE_URL}/api/product`;
const API_SUPPLIERS = `${API_BASE_URL}/api/supplier`;
const API_UPLOAD = `${API_BASE_URL}/api/files/upload-image`;

const UNIT_PRESETS = {
    g: { baseUnit: "g", unitFactor: "1" },
    kg: { baseUnit: "g", unitFactor: "1000" },
    ml: { baseUnit: "ml", unitFactor: "1" },
    l: { baseUnit: "ml", unitFactor: "1000" },
    pcs: { baseUnit: "pcs", unitFactor: "1" }
};

const UNIT_OPTIONS = [
    { value: "g", label: "Граммы (g)" },
    { value: "kg", label: "Килограммы (kg)" },
    { value: "ml", label: "Миллилитры (ml)" },
    { value: "l", label: "Литры (l)" },
    { value: "pcs", label: "Штуки (pcs)" }
];

const SORT_OPTIONS = new Set(["name_asc", "name_desc", "price_asc", "price_desc"]);

const createEmptyForm = () => ({
    supplierId: "",
    productName: "",
    productPrice: "",
    waste: "0",
    isFavorite: false,
    unit: "g",
    baseUnit: "g",
    unitFactor: "1",
    imageUrl: ""
});

const normalizeProduct = (product) => ({
    ...product,
    productId: Number(product?.productId ?? product?.id ?? 0),
    supplierId: Number(product?.supplierId ?? product?.supplierID ?? 0),
    productName: product?.productName ?? "",
    productPrice: Number(product?.productPrice ?? 0),
    waste: Number(product?.waste ?? 0),
    isFavorite: Boolean(product?.isFavorite),
    unit: product?.unit || product?.baseUnit || "g",
    baseUnit: product?.baseUnit || product?.unit || "g",
    unitFactor: Number(product?.unitFactor ?? 1),
    averageStockPrice: product?.averageStockPrice,
    imageUrl: product?.imageUrl ?? ""
});

const normalizeSupplier = (supplier) => {
    const id = Number(supplier?.supplierId ?? supplier?.supplierID ?? supplier?.id ?? 0);
    return {
        id,
        name: supplier?.supplierName ?? supplier?.name ?? `Поставщик #${id}`
    };
};

const parseJsonSafe = (raw) => {
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch {
        return null;
    }
};

const getResponseMessage = (raw, fallback) => {
    const data = parseJsonSafe(raw);
    return data?.message || data?.detail || raw || fallback;
};

const getSafeFactor = (value) => {
    const factor = Number(value);
    return Number.isFinite(factor) && factor > 0 ? factor : 1;
};

const formatNumber = (value, maximumFractionDigits = 4) => {
    const number = Number(value);
    if (!Number.isFinite(number)) return "0";
    return number.toLocaleString("ru-RU", {
        minimumFractionDigits: 0,
        maximumFractionDigits
    });
};

const formatMoney = (value) => {
    const number = Number(value);
    if (!Number.isFinite(number)) return "—";
    const digits = Math.abs(number) > 0 && Math.abs(number) < 1 ? 4 : 2;
    return number.toLocaleString("ru-RU", {
        minimumFractionDigits: 0,
        maximumFractionDigits: digits
    });
};

export default function ProductsPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [products, setProducts] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [statusMessage, setStatusMessage] = useState("");

    const [form, setForm] = useState(createEmptyForm);
    const [editingProductId, setEditingProductId] = useState(null);
    const [saving, setSaving] = useState(false);
    const [uploadingImage, setUploadingImage] = useState(false);
    const [formError, setFormError] = useState("");

    const search = searchParams.get("q") ?? "";
    const requestedSort = searchParams.get("sort") ?? "name_asc";
    const sortBy = SORT_OPTIONS.has(requestedSort) ? requestedSort : "name_asc";
    const favoritesOnly = searchParams.get("favorites") === "1";
    const deferredSearch = useDeferredValue(search);

    const loadData = useCallback(async () => {
        setLoading(true);
        setPageError("");

        try {
            const [productsResponse, suppliersResponse] = await Promise.all([
                fetch(API_PRODUCTS),
                fetch(API_SUPPLIERS)
            ]);

            if (!productsResponse.ok || !suppliersResponse.ok) {
                throw new Error("Проверьте подключение к серверу и повторите загрузку.");
            }

            const [productsData, suppliersData] = await Promise.all([
                productsResponse.json().catch(() => []),
                suppliersResponse.json().catch(() => [])
            ]);

            setProducts(
                Array.isArray(productsData)
                    ? productsData.map(normalizeProduct)
                    : []
            );
            setSuppliers(
                Array.isArray(suppliersData)
                    ? suppliersData.map(normalizeSupplier).filter((supplier) => supplier.id > 0)
                    : []
            );
        } catch (error) {
            console.error("Ошибка загрузки каталога продуктов:", error);
            setPageError(error.message || "Не удалось получить продукты и поставщиков.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const supplierMap = useMemo(
        () => new Map(suppliers.map((supplier) => [supplier.id, supplier.name])),
        [suppliers]
    );

    const filteredProducts = useMemo(() => {
        const normalizedSearch = deferredSearch.trim().toLowerCase();
        const list = products.filter((product) => {
            if (favoritesOnly && !product.isFavorite) return false;
            if (!normalizedSearch) return true;
            return product.productName.toLowerCase().includes(normalizedSearch);
        });

        list.sort((left, right) => {
            const leftName = left.productName.toLowerCase();
            const rightName = right.productName.toLowerCase();

            if (sortBy === "name_asc") return leftName.localeCompare(rightName, "ru");
            if (sortBy === "name_desc") return rightName.localeCompare(leftName, "ru");
            if (sortBy === "price_asc") return left.productPrice - right.productPrice;
            return right.productPrice - left.productPrice;
        });

        return list;
    }, [deferredSearch, favoritesOnly, products, sortBy]);

    const productRows = useMemo(() => (
        filteredProducts.map((product) => {
            const factor = getSafeFactor(product.unitFactor);
            const fallbackBasePrice = product.productPrice / factor;
            const averageStockPrice = Number(product.averageStockPrice);
            const hasStockPrice = product.averageStockPrice != null
                && Number.isFinite(averageStockPrice);
            const basePrice = hasStockPrice ? averageStockPrice : fallbackBasePrice;

            return {
                id: product.productId,
                name: product.productName || "Без названия",
                initial: (product.productName || "П").slice(0, 1).toUpperCase(),
                supplierName: supplierMap.get(product.supplierId)
                    ?? (product.supplierId ? `Поставщик #${product.supplierId}` : "Поставщик не указан"),
                favorite: product.isFavorite,
                imageUrl: product.imageUrl,
                purchasePriceLabel: `${formatMoney(product.productPrice)} ₽/${product.unit}`,
                basePriceLabel: `${formatMoney(basePrice)} ₽/${product.baseUnit}`,
                conversionLabel: factor === 1 && product.unit === product.baseUnit
                    ? `Учёт в ${product.baseUnit}`
                    : `1 ${product.unit} = ${formatNumber(factor)} ${product.baseUnit}`,
                wasteLabel: `${formatNumber(product.waste, 2)}%`,
                hasStockPrice,
                source: product
            };
        })
    ), [filteredProducts, supplierMap]);

    const favoriteCount = useMemo(
        () => products.filter((product) => product.isFavorite).length,
        [products]
    );

    const basePricePreview = useMemo(() => {
        const price = Number(form.productPrice);
        const factor = Number(form.unitFactor);
        if (!Number.isFinite(price) || !Number.isFinite(factor) || factor <= 0) {
            return "Укажите цену и коэффициент";
        }
        return `${formatMoney(price / factor)} ₽ за 1 ${form.baseUnit}`;
    }, [form.baseUnit, form.productPrice, form.unitFactor]);

    const updateSearchParam = (key, value, defaultValue = "") => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (value === defaultValue || value === "" || value === false) {
                next.delete(key);
            } else {
                next.set(key, String(value));
            }
            return next;
        }, { replace: true });
    };

    const handleChange = (field, value) => {
        setFormError("");
        setForm((previous) => {
            if (field === "unit") {
                const preset = UNIT_PRESETS[value];
                if (preset) {
                    return {
                        ...previous,
                        unit: value,
                        baseUnit: preset.baseUnit,
                        unitFactor: preset.unitFactor
                    };
                }
            }
            return { ...previous, [field]: value };
        });
    };

    const resetEditor = () => {
        setForm(createEmptyForm());
        setEditingProductId(null);
        setFormError("");
    };

    const handleSubmit = async (event) => {
        event.preventDefault();

        const supplierId = Number(form.supplierId);
        const productName = form.productName.trim();
        const productPrice = Number(form.productPrice);
        const waste = Number(form.waste);
        const unitFactor = Number(form.unitFactor);

        if (!Number.isFinite(supplierId) || supplierId <= 0) {
            setFormError("Выберите поставщика.");
            return;
        }
        if (!productName) {
            setFormError("Введите название продукта.");
            return;
        }
        if (!Number.isFinite(productPrice) || productPrice < 0) {
            setFormError("Укажите корректную закупочную цену.");
            return;
        }
        if (!Number.isFinite(waste) || waste < 0 || waste > 100) {
            setFormError("Отход должен быть от 0 до 100%.");
            return;
        }
        if (!Number.isFinite(unitFactor) || unitFactor <= 0) {
            setFormError("Коэффициент пересчёта должен быть больше 0.");
            return;
        }

        const editing = editingProductId != null;
        const endpoint = editing ? `${API_PRODUCTS}/${editingProductId}` : API_PRODUCTS;
        const payload = {
            supplierId,
            productName,
            productPrice,
            waste,
            isFavorite: Boolean(form.isFavorite),
            unit: form.unit,
            baseUnit: form.baseUnit,
            unitFactor,
            imageUrl: form.imageUrl || null
        };

        setSaving(true);
        setFormError("");
        setStatusMessage("");

        try {
            const response = await fetch(endpoint, {
                method: editing ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(raw, "Не удалось сохранить продукт.")
                );
            }

            resetEditor();
            await loadData();
            setStatusMessage(
                editing
                    ? `Карточка «${productName}» обновлена.`
                    : `Продукт «${productName}» добавлен в каталог.`
            );
        } catch (error) {
            console.error("Ошибка сохранения продукта:", error);
            setFormError(error.message || "Не удалось сохранить продукт.");
        } finally {
            setSaving(false);
        }
    };

    const handleUploadImage = async (file) => {
        if (!file) return;

        setUploadingImage(true);
        setFormError("");

        try {
            const body = new FormData();
            body.append("file", file);
            body.append("folder", "products");

            const response = await fetch(API_UPLOAD, {
                method: "POST",
                body
            });
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(raw, "Не удалось загрузить изображение.")
                );
            }

            const data = parseJsonSafe(raw);
            handleChange("imageUrl", data?.url || "");
        } catch (error) {
            console.error("Ошибка загрузки изображения продукта:", error);
            setFormError(error.message || "Не удалось загрузить изображение.");
        } finally {
            setUploadingImage(false);
        }
    };

    const startEditing = (product) => {
        setEditingProductId(product.productId);
        setFormError("");
        setStatusMessage("");
        setForm({
            supplierId: String(product.supplierId || ""),
            productName: product.productName || "",
            productPrice: String(product.productPrice ?? ""),
            waste: String(product.waste ?? "0"),
            isFavorite: Boolean(product.isFavorite),
            unit: product.unit || "g",
            baseUnit: product.baseUnit || product.unit || "g",
            unitFactor: String(product.unitFactor ?? "1"),
            imageUrl: product.imageUrl || ""
        });
        requestAnimationFrame(() => {
            document.getElementById("product-editor")?.scrollIntoView({ block: "start" });
        });
    };

    return (
        <div className={styles.page}>
            <ProductsHero
                productCount={products.length}
                favoriteCount={favoriteCount}
                supplierCount={suppliers.length}
            />

            {statusMessage ? (
                <div className={styles.statusBanner} role="status" aria-live="polite">
                    <div>
                        <strong>Операция выполнена</strong>
                        <span>{statusMessage}</span>
                    </div>
                    <button
                        type="button"
                        className={styles.statusDismiss}
                        onClick={() => setStatusMessage("")}
                    >
                        Скрыть
                    </button>
                </div>
            ) : null}

            <div className={styles.workspace}>
                <ProductEditor
                    form={form}
                    suppliers={suppliers}
                    unitOptions={UNIT_OPTIONS}
                    editingProductId={editingProductId}
                    saving={saving}
                    uploadingImage={uploadingImage}
                    error={formError}
                    basePricePreview={basePricePreview}
                    onChange={handleChange}
                    onSubmit={handleSubmit}
                    onCancel={resetEditor}
                    onUploadImage={handleUploadImage}
                />
                <ProductsCatalog
                    rows={productRows}
                    totalCount={products.length}
                    search={search}
                    sortBy={sortBy}
                    favoritesOnly={favoritesOnly}
                    loading={loading}
                    error={pageError}
                    onSearchChange={(value) => updateSearchParam("q", value)}
                    onSortChange={(value) => updateSearchParam("sort", value, "name_asc")}
                    onFavoritesChange={(checked) => updateSearchParam("favorites", checked ? "1" : "")}
                    onRetry={loadData}
                    onEdit={startEditing}
                />
            </div>
        </div>
    );
}
