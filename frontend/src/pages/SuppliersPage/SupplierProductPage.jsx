import {
    useCallback,
    useDeferredValue,
    useEffect,
    useMemo,
    useState
} from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import SupplierAssortmentCatalog from "./supplier-products/SupplierAssortmentCatalog";
import SupplierAssortmentHero from "./supplier-products/SupplierAssortmentHero";
import SupplierProductEditor from "./supplier-products/SupplierProductEditor";
import styles from "./SuppliersProductPage.module.css";

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
    productName: String(product?.productName ?? "").trim(),
    productPrice: Number(product?.productPrice ?? 0),
    waste: Number(product?.waste ?? 0),
    isFavorite: Boolean(product?.isFavorite),
    unit: product?.unit || product?.baseUnit || "g",
    baseUnit: product?.baseUnit || product?.unit || "g",
    unitFactor: Number(product?.unitFactor ?? 1),
    averageStockPrice: product?.averageStockPrice,
    imageUrl: product?.imageUrl ?? ""
});

const normalizeSupplier = (supplier, fallbackId) => ({
    id: Number(
        supplier?.supplierID
        ?? supplier?.supplierId
        ?? supplier?.id
        ?? fallbackId
    ),
    name: String(
        supplier?.supplierName
        ?? supplier?.name
        ?? `Поставщик #${fallbackId}`
    ).trim(),
    communication: String(supplier?.communication ?? "").trim()
});

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

export default function SupplierProductPage() {
    const { id } = useParams();
    const supplierId = Number(id);
    const [searchParams, setSearchParams] = useSearchParams();

    const [supplier, setSupplier] = useState(() => (
        normalizeSupplier(null, supplierId)
    ));
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [statusMessage, setStatusMessage] = useState("");

    const [form, setForm] = useState(createEmptyForm);
    const [editingProductId, setEditingProductId] = useState(null);
    const [formError, setFormError] = useState("");
    const [saving, setSaving] = useState(false);
    const [uploadingImage, setUploadingImage] = useState(false);

    const search = searchParams.get("q") ?? "";
    const requestedSort = searchParams.get("sort") ?? "name_asc";
    const sortBy = SORT_OPTIONS.has(requestedSort) ? requestedSort : "name_asc";
    const favoritesOnly = searchParams.get("favorites") === "1";
    const deferredSearch = useDeferredValue(search.trim().toLocaleLowerCase("ru"));

    const loadData = useCallback(async () => {
        if (!Number.isFinite(supplierId) || supplierId <= 0) {
            setPageError("Некорректный номер поставщика. Вернитесь к списку и выберите карточку.");
            setLoading(false);
            return;
        }

        setLoading(true);
        setPageError("");

        try {
            const [productsResponse, supplierResponse] = await Promise.all([
                fetch(`${API_PRODUCTS}/supplier/${supplierId}`),
                fetch(`${API_SUPPLIERS}/${supplierId}`)
            ]);
            const [productsRaw, supplierRaw] = await Promise.all([
                productsResponse.text(),
                supplierResponse.text()
            ]);

            if (!productsResponse.ok) {
                throw new Error(
                    getResponseMessage(
                        productsRaw,
                        `Не удалось загрузить ассортимент (${productsResponse.status}).`
                    )
                );
            }
            if (!supplierResponse.ok) {
                throw new Error(
                    getResponseMessage(
                        supplierRaw,
                        `Не удалось загрузить поставщика (${supplierResponse.status}).`
                    )
                );
            }

            const productsData = parseJsonSafe(productsRaw);
            const supplierData = parseJsonSafe(supplierRaw);
            setProducts(
                Array.isArray(productsData)
                    ? productsData.map(normalizeProduct).filter((product) => product.productId > 0)
                    : []
            );
            setSupplier(normalizeSupplier(supplierData, supplierId));
        } catch (error) {
            console.error("Ошибка загрузки ассортимента поставщика:", error);
            setPageError(
                error.message
                || "Не удалось загрузить ассортимент. Проверьте соединение и повторите попытку."
            );
            setProducts([]);
        } finally {
            setLoading(false);
        }
    }, [supplierId]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const filteredProducts = useMemo(() => {
        let result = products.filter((product) => {
            if (favoritesOnly && !product.isFavorite) return false;
            if (!deferredSearch) return true;
            return product.productName.toLocaleLowerCase("ru").includes(deferredSearch);
        });

        result = [...result].sort((first, second) => {
            const firstName = first.productName.toLocaleLowerCase("ru");
            const secondName = second.productName.toLocaleLowerCase("ru");
            if (sortBy === "name_asc") return firstName.localeCompare(secondName, "ru");
            if (sortBy === "name_desc") return secondName.localeCompare(firstName, "ru");
            if (sortBy === "price_asc") return first.productPrice - second.productPrice;
            return second.productPrice - first.productPrice;
        });

        return result;
    }, [deferredSearch, favoritesOnly, products, sortBy]);

    const rows = useMemo(() => filteredProducts.map((product) => {
        const factor = Number.isFinite(product.unitFactor) && product.unitFactor > 0
            ? product.unitFactor
            : 1;
        const calculatedBasePrice = product.productPrice / factor;
        const stockPrice = Number(product.averageStockPrice);
        const hasStockPrice = Number.isFinite(stockPrice) && stockPrice > 0;

        return {
            id: product.productId,
            name: product.productName || `Продукт #${product.productId}`,
            initial: (product.productName || "П").slice(0, 1).toLocaleUpperCase("ru"),
            imageUrl: product.imageUrl,
            favorite: product.isFavorite,
            purchasePriceLabel: `${formatMoney(product.productPrice)} ₽/${product.unit}`,
            basePriceLabel: `${formatMoney(hasStockPrice ? stockPrice : calculatedBasePrice)} ₽/${product.baseUnit}`,
            hasStockPrice,
            conversionLabel: factor === 1 && product.unit === product.baseUnit
                ? `Учёт в ${product.baseUnit}`
                : `1 ${product.unit} = ${formatNumber(factor)} ${product.baseUnit}`,
            wasteLabel: `${formatNumber(product.waste, 2)}%`,
            source: product
        };
    }), [filteredProducts]);

    const basePricePreview = useMemo(() => {
        const price = Number(form.productPrice);
        const factor = Number(form.unitFactor);
        if (!Number.isFinite(price) || price < 0 || !Number.isFinite(factor) || factor <= 0) {
            return `0 ₽ за 1 ${form.baseUnit}`;
        }
        return `${formatMoney(price / factor)} ₽ за 1 ${form.baseUnit}`;
    }, [form.baseUnit, form.productPrice, form.unitFactor]);

    const updateSearchParam = (key, value, defaultValue = "") => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (!value || value === defaultValue) next.delete(key);
            else next.set(key, String(value));
            return next;
        }, { replace: true });
    };

    const handleChange = (field, value) => {
        setFormError("");
        setForm((current) => {
            if (field === "unit" && UNIT_PRESETS[value]) {
                return {
                    ...current,
                    unit: value,
                    ...UNIT_PRESETS[value]
                };
            }
            return { ...current, [field]: value };
        });
    };

    const resetEditor = () => {
        setForm(createEmptyForm());
        setEditingProductId(null);
        setFormError("");
    };

    const handleUploadImage = async (file) => {
        if (!file) return;
        setUploadingImage(true);
        setFormError("");

        try {
            const body = new FormData();
            body.append("file", file);
            body.append("folder", "products");

            const response = await fetch(API_UPLOAD, { method: "POST", body });
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(raw, "Не удалось загрузить изображение.")
                );
            }

            const data = parseJsonSafe(raw);
            handleChange("imageUrl", data?.url || "");
        } catch (error) {
            console.error("Ошибка загрузки изображения:", error);
            setFormError(error.message || "Не удалось загрузить изображение.");
        } finally {
            setUploadingImage(false);
        }
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        const productName = form.productName.trim();
        const productPrice = Number(form.productPrice);
        const waste = Number(form.waste);
        const unitFactor = Number(form.unitFactor);

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
        setSaving(true);
        setFormError("");
        setStatusMessage("");

        try {
            const response = await fetch(
                editing ? `${API_PRODUCTS}/${editingProductId}` : API_PRODUCTS,
                {
                    method: editing ? "PUT" : "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        supplierId,
                        productName,
                        productPrice,
                        waste,
                        isFavorite: Boolean(form.isFavorite),
                        unit: form.unit,
                        baseUnit: form.baseUnit,
                        unitFactor,
                        imageUrl: form.imageUrl || null
                    })
                }
            );
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
                    : `Продукт «${productName}» добавлен в ассортимент.`
            );
        } catch (error) {
            console.error("Ошибка сохранения продукта:", error);
            setFormError(error.message || "Не удалось сохранить продукт.");
        } finally {
            setSaving(false);
        }
    };

    const startEditing = (product) => {
        setEditingProductId(product.productId);
        setForm({
            productName: product.productName,
            productPrice: String(product.productPrice),
            waste: String(product.waste),
            isFavorite: product.isFavorite,
            unit: product.unit,
            baseUnit: product.baseUnit,
            unitFactor: String(product.unitFactor),
            imageUrl: product.imageUrl
        });
        setFormError("");
        setStatusMessage("");
        document.getElementById("supplier-product-editor")?.scrollIntoView();
    };

    return (
        <div className={styles.page}>
            <SupplierAssortmentHero
                supplier={supplier}
                productCount={products.length}
                favoriteCount={products.filter((product) => product.isFavorite).length}
            />

            {statusMessage ? (
                <div className={styles.statusMessage} role="status" aria-live="polite">
                    <span>{statusMessage}</span>
                    <button
                        type="button"
                        onClick={() => setStatusMessage("")}
                        aria-label="Скрыть сообщение"
                    >
                        Закрыть
                    </button>
                </div>
            ) : null}

            <div className={styles.workspaceGrid}>
                <SupplierProductEditor
                    supplierName={supplier.name}
                    form={form}
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

                <SupplierAssortmentCatalog
                    rows={rows}
                    total={products.length}
                    search={search}
                    sortBy={sortBy}
                    favoritesOnly={favoritesOnly}
                    loading={loading}
                    error={pageError}
                    onSearchChange={(value) => updateSearchParam("q", value)}
                    onSortChange={(value) => updateSearchParam("sort", value, "name_asc")}
                    onFavoritesChange={(checked) => (
                        updateSearchParam("favorites", checked ? "1" : "")
                    )}
                    onRetry={loadData}
                    onEdit={startEditing}
                    onClearFilters={() => setSearchParams({}, { replace: true })}
                />
            </div>
        </div>
    );
}
