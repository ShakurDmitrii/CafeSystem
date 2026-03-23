import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
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
const createEmptyForm = () => ({
    productName: "",
    productPrice: "",
    waste: "",
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
    unit: product?.unit ?? "g",
    baseUnit: product?.baseUnit ?? "g",
    unitFactor: Number(product?.unitFactor ?? 1),
    imageUrl: product?.imageUrl ?? ""
});

export default function SupplierProductPage() {
    const { id } = useParams();
    const supplierId = Number(id);

    const [products, setProducts] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [uploadingImage, setUploadingImage] = useState(false);
    const [editingProductId, setEditingProductId] = useState(null);
    const [search, setSearch] = useState("");
    const [sortBy, setSortBy] = useState("name_asc");
    const [form, setForm] = useState(createEmptyForm);

    const loadData = async () => {
        if (!Number.isFinite(supplierId) || supplierId <= 0) {
            setError("Некорректный ID поставщика");
            setLoading(false);
            return;
        }

        setLoading(true);
        setError("");

        try {
            const [productsRes, suppliersRes] = await Promise.all([
                fetch(`${API_PRODUCTS}/supplier/${supplierId}`),
                fetch(API_SUPPLIERS)
            ]);

            if (!productsRes.ok) {
                throw new Error(`Ошибка загрузки товаров (${productsRes.status})`);
            }
            if (!suppliersRes.ok) {
                throw new Error(`Ошибка загрузки поставщиков (${suppliersRes.status})`);
            }

            const productsData = await productsRes.json().catch(() => []);
            const suppliersData = await suppliersRes.json().catch(() => []);

            setProducts(Array.isArray(productsData) ? productsData.map(normalizeProduct) : []);
            setSuppliers(Array.isArray(suppliersData) ? suppliersData : []);
        } catch (e) {
            console.error("Ошибка загрузки данных поставщика:", e);
            setError(e.message || "Ошибка загрузки данных");
            setProducts([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, [supplierId]);

    const supplierMap = useMemo(() => {
        const map = new Map();
        suppliers.forEach((s) => {
            const idValue = s.supplierId ?? s.supplierID ?? s.id;
            const name = s.supplierName ?? s.name ?? `Поставщик #${idValue}`;
            map.set(Number(idValue), name);
        });
        return map;
    }, [suppliers]);

    const currentSupplierName = supplierMap.get(supplierId) ?? `Поставщик #${supplierId}`;

    const filteredProducts = useMemo(() => {
        const normalizedSearch = search.trim().toLowerCase();
        let list = [...products];

        if (normalizedSearch) {
            list = list.filter((p) =>
                String(p.productName ?? "").toLowerCase().includes(normalizedSearch)
            );
        }

        list.sort((a, b) => {
            const nameA = String(a.productName ?? "").toLowerCase();
            const nameB = String(b.productName ?? "").toLowerCase();
            const priceA = Number(a.productPrice ?? 0);
            const priceB = Number(b.productPrice ?? 0);

            if (sortBy === "name_asc") return nameA.localeCompare(nameB);
            if (sortBy === "name_desc") return nameB.localeCompare(nameA);
            if (sortBy === "price_asc") return priceA - priceB;
            return priceB - priceA;
        });

        return list;
    }, [products, search, sortBy]);

    const handleChange = (field, value) => {
        setForm((prev) => {
            if (field === "unit") {
                const preset = UNIT_PRESETS[value];
                if (preset) {
                    return {
                        ...prev,
                        unit: value,
                        baseUnit: preset.baseUnit,
                        unitFactor: preset.unitFactor
                    };
                }
            }
            return { ...prev, [field]: value };
        });
    };

    const handleUploadImage = async (file) => {
        if (!file) return;

        setError("");
        setUploadingImage(true);
        try {
            const body = new FormData();
            body.append("file", file);
            body.append("folder", "products");

            const res = await fetch(API_UPLOAD, {
                method: "POST",
                body
            });

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data?.message || `Ошибка загрузки изображения (${res.status})`);
            }

            const data = await res.json().catch(() => ({}));
            handleChange("imageUrl", data.url || "");
        } catch (e) {
            console.error("Ошибка загрузки изображения:", e);
            setError(e.message || "Ошибка загрузки изображения");
        } finally {
            setUploadingImage(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");

        if (
            !form.productName.trim() ||
            !form.productPrice.trim() ||
            !form.waste.trim() ||
            !form.unitFactor.trim()
        ) {
            setError("Заполните все поля формы");
            return;
        }

        const payload = {
            supplierId,
            productName: form.productName.trim(),
            productPrice: Number(form.productPrice),
            waste: Number(form.waste),
            isFavorite: !!form.isFavorite,
            unit: form.unit,
            baseUnit: form.baseUnit,
            unitFactor: Number(form.unitFactor),
            imageUrl: form.imageUrl || null
        };

        try {
            const res = await fetch(editingProductId ? `${API_PRODUCTS}/${editingProductId}` : API_PRODUCTS, {
                method: editingProductId ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                const text = await res.text().catch(() => "");
                throw new Error(data?.message || text || `Ошибка сохранения (${res.status})`);
            }

            setForm(createEmptyForm());
            setEditingProductId(null);

            await loadData();
        } catch (e2) {
            console.error("Ошибка сохранения товара:", e2);
            setError(e2.message || "Ошибка при сохранении товара");
        }
    };

    const startEditing = (product) => {
        setEditingProductId(product.productId);
        setError("");
        setForm({
            productName: product.productName ?? "",
            productPrice: String(product.productPrice ?? ""),
            waste: String(product.waste ?? ""),
            isFavorite: !!product.isFavorite,
            unit: product.unit ?? "g",
            baseUnit: product.baseUnit ?? "g",
            unitFactor: String(product.unitFactor ?? "1"),
            imageUrl: product.imageUrl ?? ""
        });
        window.scrollTo({ top: 0, behavior: "smooth" });
    };

    const cancelEditing = () => {
        setEditingProductId(null);
        setError("");
        setForm(createEmptyForm());
    };

    return (
        <div className={styles.page}>
            <div className={styles.headerRow}>
                <h2>Товары поставщика: {currentSupplierName}</h2>
                <button className={styles.reloadBtn} onClick={loadData} type="button">Обновить</button>
            </div>

            <form className={styles.form} onSubmit={handleSubmit}>
                <h3>{editingProductId ? `Редактирование товара #${editingProductId}` : "Добавить товар"}</h3>
                <div className={styles.formGrid}>
                    <input
                        type="text"
                        placeholder="Название"
                        value={form.productName}
                        onChange={(e) => handleChange("productName", e.target.value)}
                        required
                    />
                    <input
                        type="number"
                        placeholder="Цена"
                        step="0.01"
                        min="0"
                        value={form.productPrice}
                        onChange={(e) => handleChange("productPrice", e.target.value)}
                        required
                    />
                    <input
                        type="number"
                        placeholder="Waste"
                        step="0.01"
                        min="0"
                        value={form.waste}
                        onChange={(e) => handleChange("waste", e.target.value)}
                        required
                    />
                    <select
                        value={form.unit}
                        onChange={(e) => handleChange("unit", e.target.value)}
                    >
                        <option value="g">g</option>
                        <option value="kg">kg</option>
                        <option value="ml">ml</option>
                        <option value="l">l</option>
                        <option value="pcs">pcs</option>
                    </select>
                    <select
                        value={form.baseUnit}
                        onChange={(e) => handleChange("baseUnit", e.target.value)}
                    >
                        <option value="g">g</option>
                        <option value="ml">ml</option>
                        <option value="pcs">pcs</option>
                    </select>
                    <input
                        type="number"
                        step="0.0001"
                        min="0.0001"
                        placeholder="Коэффициент в base"
                        value={form.unitFactor}
                        onChange={(e) => handleChange("unitFactor", e.target.value)}
                        required
                    />
                    <input
                        type="file"
                        accept="image/*"
                        className={styles.fileInput}
                        onChange={(e) => handleUploadImage(e.target.files?.[0])}
                    />
                    <div className={styles.uploadStatus}>
                        {uploadingImage ? "Загрузка изображения..." : (form.imageUrl ? "Изображение загружено" : "Изображение не выбрано")}
                    </div>
                    <div className={styles.hint}>
                        Подсказка: для `kg` и `l` коэффициент обычно `1000`, для `g`, `ml`, `pcs` — `1`.
                    </div>
                    {form.imageUrl && (
                        <img src={form.imageUrl} alt="Превью продукта" className={styles.previewImage} />
                    )}
                    <label className={styles.checkboxLabel}>
                        <input
                            type="checkbox"
                            checked={form.isFavorite}
                            onChange={(e) => handleChange("isFavorite", e.target.checked)}
                        />
                        Избранный
                    </label>
                    <div className={styles.formActions}>
                        <button type="submit" className={styles.submitBtn}>
                            {editingProductId ? "Сохранить" : "Создать"}
                        </button>
                        {editingProductId && (
                            <button type="button" className={styles.cancelBtn} onClick={cancelEditing}>
                                Отмена
                            </button>
                        )}
                    </div>
                </div>
            </form>

            <div className={styles.controls}>
                <input
                    type="text"
                    placeholder="Поиск по имени..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                    <option value="name_asc">Имя: А-Я</option>
                    <option value="name_desc">Имя: Я-А</option>
                    <option value="price_asc">Цена: по возрастанию</option>
                    <option value="price_desc">Цена: по убыванию</option>
                </select>
            </div>

            {error && <div className={styles.error}>{error}</div>}

            {loading ? (
                <div className={styles.info}>Загрузка...</div>
            ) : filteredProducts.length === 0 ? (
                <div className={styles.info}>Товары не найдены</div>
            ) : (
                <div className={styles.tableWrap}>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>ID</th>
                            <th>Название</th>
                            <th>Поставщик</th>
                            <th>Цена</th>
                            <th>Waste</th>
                            <th>Ед.</th>
                            <th>Base</th>
                            <th>Коэф.</th>
                            <th>Фото</th>
                            <th>Избранный</th>
                            <th>Действия</th>
                        </tr>
                        </thead>
                        <tbody>
                        {filteredProducts.map((product) => (
                            <tr key={product.productId}>
                                <td>{product.productId}</td>
                                <td>{product.productName}</td>
                                <td>{supplierMap.get(Number(product.supplierId)) ?? `#${product.supplierId}`}</td>
                                <td>{Number(product.productPrice ?? 0).toFixed(2)}</td>
                                <td>{Number(product.waste ?? 0).toFixed(2)}</td>
                                <td>{product.unit ?? "-"}</td>
                                <td>{product.baseUnit ?? "-"}</td>
                                <td>{Number(product.unitFactor ?? 1).toFixed(4)}</td>
                                <td>
                                    {product.imageUrl ? (
                                        <img src={product.imageUrl} alt={product.productName} className={styles.tableThumb} />
                                    ) : "—"}
                                </td>
                                <td>{product.isFavorite ? "Да" : "Нет"}</td>
                                <td>
                                    <div className={styles.rowActions}>
                                        <button
                                            type="button"
                                            className={styles.editBtn}
                                            onClick={() => startEditing(product)}
                                        >
                                            Редактировать
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
