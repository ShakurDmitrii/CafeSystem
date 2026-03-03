import { useEffect, useMemo, useState } from "react";
import styles from "./ProductsPage.module.css";

const API_PRODUCTS = "http://localhost:8080/api/product";
const API_SUPPLIERS = "http://localhost:8080/api/supplier";
const UNIT_PRESETS = {
    g: { baseUnit: "g", unitFactor: "1" },
    kg: { baseUnit: "g", unitFactor: "1000" },
    ml: { baseUnit: "ml", unitFactor: "1" },
    l: { baseUnit: "ml", unitFactor: "1000" },
    pcs: { baseUnit: "pcs", unitFactor: "1" }
};

export default function ProductsPage() {
    const [products, setProducts] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const [search, setSearch] = useState("");
    const [sortBy, setSortBy] = useState("name_asc");

    const [form, setForm] = useState({
        supplierId: "",
        productName: "",
        productPrice: "",
        waste: "",
        isFavorite: false,
        unit: "g",
        baseUnit: "g",
        unitFactor: "1"
    });

    const loadData = async () => {
        setLoading(true);
        setError("");
        try {
            const [productsRes, suppliersRes] = await Promise.all([
                fetch(API_PRODUCTS),
                fetch(API_SUPPLIERS)
            ]);

            if (!productsRes.ok) throw new Error(`Ошибка загрузки продуктов (${productsRes.status})`);
            if (!suppliersRes.ok) throw new Error(`Ошибка загрузки поставщиков (${suppliersRes.status})`);

            const productsData = await productsRes.json().catch(() => []);
            const suppliersData = await suppliersRes.json().catch(() => []);

            setProducts(Array.isArray(productsData) ? productsData : []);
            setSuppliers(Array.isArray(suppliersData) ? suppliersData : []);
        } catch (e) {
            setError(e.message || "Ошибка загрузки данных");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const supplierMap = useMemo(() => {
        const map = new Map();
        suppliers.forEach((s) => {
            const id = s.supplierId ?? s.supplierID ?? s.id;
            const name = s.supplierName ?? s.name ?? `Поставщик #${id}`;
            map.set(Number(id), name);
        });
        return map;
    }, [suppliers]);

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
                    return { ...prev, unit: value, baseUnit: preset.baseUnit, unitFactor: preset.unitFactor };
                }
            }
            return { ...prev, [field]: value };
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");

        if (!form.supplierId || !form.productName.trim() || !form.productPrice.trim() || !form.waste.trim() || !form.unitFactor.trim()) {
            setError("Заполните все поля формы");
            return;
        }

        const payload = {
            supplierId: Number(form.supplierId),
            productName: form.productName.trim(),
            productPrice: Number(form.productPrice),
            waste: Number(form.waste),
            isFavorite: !!form.isFavorite,
            unit: form.unit,
            baseUnit: form.baseUnit,
            unitFactor: Number(form.unitFactor)
        };

        try {
            const res = await fetch(API_PRODUCTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data?.message || `Ошибка создания (${res.status})`);
            }
            await res.json().catch(() => null);
            setForm({
                supplierId: "",
                productName: "",
                productPrice: "",
                waste: "",
                isFavorite: false,
                unit: "g",
                baseUnit: "g",
                unitFactor: "1"
            });
            await loadData();
        } catch (e2) {
            setError(e2.message || "Ошибка при создании продукта");
        }
    };

    return (
        <div className={styles.page}>
            <div className={styles.headerRow}>
                <h2>Продукты</h2>
                <button className={styles.reloadBtn} onClick={loadData} type="button">Обновить</button>
            </div>

            <form className={styles.form} onSubmit={handleSubmit}>
                <h3>Добавить продукт</h3>
                <div className={styles.formGrid}>
                    <select
                        value={form.supplierId}
                        onChange={(e) => handleChange("supplierId", e.target.value)}
                        required
                    >
                        <option value="">Поставщик</option>
                        {suppliers.map((s) => {
                            const id = s.supplierId ?? s.supplierID ?? s.id;
                            const name = s.supplierName ?? s.name ?? `Поставщик #${id}`;
                            return (
                                <option key={id} value={id}>{name}</option>
                            );
                        })}
                    </select>
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
                    <div className={styles.hint}>
                        Подсказка: для `kg` и `l` коэффициент обычно `1000`, для `g`, `ml`, `pcs` — `1`.
                    </div>
                    <label className={styles.checkboxLabel}>
                        <input
                            type="checkbox"
                            checked={form.isFavorite}
                            onChange={(e) => handleChange("isFavorite", e.target.checked)}
                        />
                        Избранный
                    </label>
                    <button type="submit" className={styles.submitBtn}>Создать</button>
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
                <div className={styles.info}>Продукты не найдены</div>
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
                            <th>Избранный</th>
                        </tr>
                        </thead>
                        <tbody>
                        {filteredProducts.map((p) => (
                            <tr key={p.productId}>
                                <td>{p.productId}</td>
                                <td>{p.productName}</td>
                                <td>{supplierMap.get(Number(p.supplierId)) ?? `#${p.supplierId}`}</td>
                                <td>{Number(p.productPrice ?? 0).toFixed(2)}</td>
                                <td>{Number(p.waste ?? 0).toFixed(2)}</td>
                                <td>{p.unit ?? "-"}</td>
                                <td>{p.baseUnit ?? "-"}</td>
                                <td>{Number(p.unitFactor ?? 1).toFixed(4)}</td>
                                <td>{p.isFavorite ? "Да" : "Нет"}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
