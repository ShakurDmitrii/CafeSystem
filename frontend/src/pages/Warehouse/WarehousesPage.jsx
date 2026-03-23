import { useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "../../auth";
import styles from "./WarehousePage.module.css";

const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_MOVEMENTS = `${API_BASE_URL}/movements`;
const API_SUPPLIERS = `${API_BASE_URL}/api/supplier`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;

const getSafeUnitFactor = (value) => {
    const n = Number(value);
    return Number.isFinite(n) && n > 0 ? n : 1;
};

const toDisplayQty = (qtyBase, unitFactor) => Number(qtyBase ?? 0) / getSafeUnitFactor(unitFactor);
const normalizeUnit = (value) => String(value ?? "").trim().toLowerCase();
const hasExpandedUnitDisplay = (product) => getSafeUnitFactor(product?.unitFactor) > 1;
const hasPackLikeDisplay = (product) =>
    hasExpandedUnitDisplay(product)
    && normalizeUnit(product?.unit) === normalizeUnit(product?.baseUnit);

const getWarehouseUnitLabel = (product) => {
    const unit = product?.unit ?? product?.baseUnit ?? "pcs";
    const baseUnit = product?.baseUnit ?? unit;
    const factor = getSafeUnitFactor(product?.unitFactor);
    if (!hasExpandedUnitDisplay(product)) {
        return unit;
    }
    return `${unit} (1 ед. = ${factor} ${baseUnit})`;
};

const getQuantityInputLabel = (product) => {
    if (hasPackLikeDisplay(product)) {
        return `${product?.baseUnit ?? product?.unit ?? "pcs"}, base`;
    }
    return product?.unit ?? product?.baseUnit ?? "pcs";
};

const normalizeMovementPayload = (product, qty, unitPrice) => {
    const quantity = Number(qty);
    const price = Number(unitPrice);

    if (!hasPackLikeDisplay(product)) {
        return {
            quantity,
            unitPrice: price
        };
    }

    const factor = getSafeUnitFactor(product?.unitFactor);
    return {
        quantity: quantity / factor,
        unitPrice: Number.isFinite(price) ? price * factor : price
    };
};

const formatQty = (value) => {
    const n = Number(value ?? 0);
    if (!Number.isFinite(n)) return "0";
    return n.toLocaleString("ru-RU", { maximumFractionDigits: 3 });
};

const formatPriceWithUnit = (value, unit) => {
    const n = Number(value);
    if (!Number.isFinite(n)) return "—";
    const digits = Math.abs(n) > 0 && Math.abs(n) < 1 ? 4 : 2;
    return `${n.toLocaleString("ru-RU", {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits
    })} ₽/${unit || "ед."}`;
};

export default function WarehousePage() {
    const [warehouses, setWarehouses] = useState([]);
    const [warehouseName, setWarehouseName] = useState("");
    const [editingId, setEditingId] = useState(null);
    const [showZeroStock, setShowZeroStock] = useState(false);

    const [movementFrom, setMovementFrom] = useState("");
    const [movementTo, setMovementTo] = useState("");
    const [movementProduct, setMovementProduct] = useState("");
    const [movementQuantity, setMovementQuantity] = useState("");

    const [productsFrom, setProductsFrom] = useState([]);
    const [warehouseProducts, setWarehouseProducts] = useState({});
    const [avgReceiptPriceByWarehouseProduct, setAvgReceiptPriceByWarehouseProduct] = useState({});
    const [loadingWarehouses, setLoadingWarehouses] = useState(true);
    const [suppliers, setSuppliers] = useState([]);
    const [catalogProducts, setCatalogProducts] = useState([]);
    const [adjustQtyInputs, setAdjustQtyInputs] = useState({}); // { "whId-productId": "0" }
    const [adjustPriceInputs, setAdjustPriceInputs] = useState({}); // { "whId-productId": "0" }
    const [productPickerWarehouseId, setProductPickerWarehouseId] = useState(null);
    const [productPickerSearch, setProductPickerSearch] = useState("");
    const [selectedCatalogProductId, setSelectedCatalogProductId] = useState("");
    const [selectedCatalogQuantity, setSelectedCatalogQuantity] = useState("");
    const [selectedCatalogPrice, setSelectedCatalogPrice] = useState("");
    const [productPickerError, setProductPickerError] = useState("");

    // Состояния для добавления продукта
    const [newProductName, setNewProductName] = useState("");
    const [newProductSupplierId, setNewProductSupplierId] = useState("");
    const [newProductPrice, setNewProductPrice] = useState("");
    const [newProductWaste, setNewProductWaste] = useState("");
    const [newProductQuantity, setNewProductQuantity] = useState(""); // Добавляем quantity
    const [addingToWarehouseId, setAddingToWarehouseId] = useState(null);

    useEffect(() => {
        loadWarehouses();
    }, []);

    useEffect(() => {
        if (movementFrom) loadProductsFromWarehouse(movementFrom);
        else setProductsFrom([]);
        setMovementProduct("");
    }, [movementFrom, avgReceiptPriceByWarehouseProduct]);

    const filteredCatalogProducts = useMemo(() => {
        const searchTerm = String(productPickerSearch || "").trim().toLowerCase();
        const list = [...catalogProducts].sort((a, b) =>
            String(a.productName ?? "").localeCompare(String(b.productName ?? ""), "ru")
        );
        if (!searchTerm) return list;

        return list.filter((product) => {
            const productName = String(product.productName ?? "").toLowerCase();
            const supplierId = product.supplierId ?? product.supplierID;
            const supplier = suppliers.find((s) => (s.supplierId ?? s.supplierID ?? s.id) === supplierId);
            const supplierName = String(supplier?.supplierName ?? supplier?.name ?? "").toLowerCase();
            return productName.includes(searchTerm) || supplierName.includes(searchTerm);
        });
    }, [catalogProducts, productPickerSearch, suppliers]);

    const buildWarehouseIncomingPriceMaps = (movementsList) => {
        const totalsByWarehouseProduct = {};
        const latestByWarehouseProduct = {};

        movementsList.forEach((movement) => {
            const pid = Number(movement?.productId);
            const qty = Number(movement?.quantity);
            const price = Number(movement?.unitPrice);
            const lineTotal = Number(movement?.lineTotal);
            const docDateTs = movement?.docDate ? new Date(movement.docDate).getTime() : 0;

            if (!Number.isFinite(pid) || !Number.isFinite(qty) || qty <= 0) return;
            if (!Number.isFinite(price) || price < 0) return;

            const incomingWarehouseId = movement?.docType === "receipt"
                ? Number(movement?.toWarehouseId)
                : movement?.docType === "movement"
                    ? Number(movement?.toWarehouseId)
                    : null;

            if (!Number.isFinite(incomingWarehouseId) || incomingWarehouseId <= 0) return;

            const key = `${incomingWarehouseId}-${pid}`;
            if (!totalsByWarehouseProduct[key]) {
                totalsByWarehouseProduct[key] = { qty: 0, amount: 0 };
            }

            totalsByWarehouseProduct[key].qty += qty;
            totalsByWarehouseProduct[key].amount += Number.isFinite(lineTotal) ? lineTotal : qty * price;

            const currentLatest = latestByWarehouseProduct[key];
            if (!currentLatest || docDateTs >= currentLatest.timestamp) {
                latestByWarehouseProduct[key] = { price, timestamp: docDateTs };
            }
        });

        const averageMap = Object.entries(totalsByWarehouseProduct).reduce((acc, [key, total]) => {
            if (total.qty > 0) {
                acc[key] = total.amount / total.qty;
            }
            return acc;
        }, {});

        const latestMap = Object.entries(latestByWarehouseProduct).reduce((acc, [key, value]) => {
            acc[key] = value.price;
            return acc;
        }, {});

        return { averageMap, latestMap };
    };

    const loadWarehouses = async () => {
        setLoadingWarehouses(true);
        try {
            const [whRes, supRes, movRes, productsRes] = await Promise.all([
                fetch(API_WAREHOUSES),
                fetch(API_SUPPLIERS),
                fetch(API_MOVEMENTS),
                fetch(API_PRODUCTS)
            ]);
            const whData = whRes.ok ? await whRes.json().catch(() => []) : [];
            const warehousesArray = Array.isArray(whData) ? whData : [];
            setWarehouses(warehousesArray);

            const supData = supRes.ok ? await supRes.json().catch(() => []) : [];
            const suppliersList = Array.isArray(supData) ? supData : [];
            setSuppliers(suppliersList);

            const productsData = productsRes.ok ? await productsRes.json().catch(() => []) : [];
            setCatalogProducts(Array.isArray(productsData) ? productsData : []);

            const movData = movRes.ok ? await movRes.json().catch(() => []) : [];
            const movementsList = Array.isArray(movData) ? movData : [];
            const { averageMap, latestMap } = buildWarehouseIncomingPriceMaps(movementsList);

            setAvgReceiptPriceByWarehouseProduct(averageMap);

            await loadAllWarehouseProducts(
                warehousesArray,
                suppliersList,
                averageMap,
                latestMap
            );
        } catch (err) {
            console.error(err);
        } finally {
            setLoadingWarehouses(false);
        }
    };

    const setMainWarehouse = async (warehouseId) => {
        try {
            const res = await fetch(`${API_WAREHOUSES}/${warehouseId}/main`, { method: "PUT" });
            if (!res.ok) throw new Error("Не удалось установить главный склад");
            await loadWarehouses();
        } catch (err) {
            console.error(err);
            alert("Не удалось установить главный склад");
        }
    };

    const loadAllWarehouseProducts = async (
        warehousesList,
        suppliersList = [],
        weightedReceiptPriceByWarehouseProduct = {},
        latestReceiptPriceByWarehouseProduct = {}
    ) => {
        const productsMap = {};

        for (const wh of warehousesList) {
            try {
                const res = await fetch(`${API_WAREHOUSES}/${wh.warehouseId}/products`);
                const warehouseProductsData = await res.json();

                console.log(`Данные продуктов для склада ${wh.warehouseId}:`, warehouseProductsData);

                if (Array.isArray(warehouseProductsData) && warehouseProductsData.length > 0) {
                    const productsFullData = await Promise.all(
                        warehouseProductsData.map(async (warehouseProduct) => {
                            const { productId, quantity: whQuantity } = warehouseProduct;

                            if (!productId) return null;

                            try {
                                const resProd = await fetch(`${API_BASE_URL}/api/product/${productId}`);
                                if (!resProd.ok) {
                                    return {
                                        ...warehouseProduct,
                                        productId,
                                        productName: "Неизвестный продукт",
                                        productPrice: "-",
                                        avgPrice: "-",
                                        lastPurchasePrice: "-",
                                        waste: "-",
                                        quantity: whQuantity ?? 0,
                                        supplierName: "—"
                                    };
                                }

                                const productData = await resProd.json();
                                const product = Array.isArray(productData) ? productData[0] : productData;
                                const warehouseProductKey = `${wh.warehouseId}-${productId}`;
                                const weightedReceiptPrice = weightedReceiptPriceByWarehouseProduct[warehouseProductKey];
                                const latestReceiptPrice = latestReceiptPriceByWarehouseProduct[warehouseProductKey];
                                const supplierId = product.supplierId ?? product.supplierID;
                                const supplier = suppliersList.find(s => (s.supplierId ?? s.supplierID ?? s.id) === supplierId);
                                const supplierName = supplier ? (supplier.supplierName ?? supplier.name) : "—";
                                const avgPrice = weightedReceiptPrice ?? product.productPrice;
                                const unitFactor = getSafeUnitFactor(product.unitFactor);
                                const quantityBase = Number(whQuantity ?? 0);
                                const quantityDisplay = toDisplayQty(quantityBase, unitFactor);

                                return {
                                    ...product,
                                    productPrice: avgPrice,
                                    avgPrice,
                                    lastPurchasePrice: latestReceiptPrice ?? null,
                                    productWarehouseId: warehouseProduct.productWarehouseId,
                                    quantityBase,
                                    quantity: quantityDisplay,
                                    unitFactor,
                                    unit: product.unit ?? product.baseUnit ?? "pcs",
                                    baseUnit: product.baseUnit ?? product.unit ?? "pcs",
                                    supplierName
                                };
                            } catch (err) {
                                console.error("Ошибка продукта", productId, err);
                                return {
                                    ...warehouseProduct,
                                    productId,
                                    productName: "Неизвестный продукт",
                                    productPrice: "-",
                                    avgPrice: "-",
                                    lastPurchasePrice: "-",
                                    waste: "-",
                                    quantityBase: Number(whQuantity ?? 0),
                                    quantity: Number(whQuantity ?? 0),
                                    unitFactor: 1,
                                    unit: "pcs",
                                    baseUnit: "pcs",
                                    supplierName: "—"
                                };
                            }
                        })
                    );

                    productsMap[wh.warehouseId] = productsFullData.filter(p => p !== null);
                } else {
                    productsMap[wh.warehouseId] = [];
                }
            } catch (err) {
                console.error(`Ошибка загрузки продуктов для склада ${wh.warehouseId}:`, err);
                productsMap[wh.warehouseId] = [];
            }
        }

        setWarehouseProducts(productsMap);
    };
    const loadProductsFromWarehouse = async (warehouseId) => {
        try {
            const res = await fetch(`${API_WAREHOUSES}/${warehouseId}/products`);
            const data = await res.json();
            const baseProducts = Array.isArray(data) ? data : [];

            const enriched = await Promise.all(
                baseProducts.map(async (item) => {
                    const productId = item.productId;
                    if (!productId) return null;

                    try {
                        const prodRes = await fetch(`${API_BASE_URL}/api/product/${productId}`);
                        if (!prodRes.ok) {
                            return {
                                ...item,
                                productName: `Товар #${productId}`
                            };
                        }

                        const prodData = await prodRes.json();
                        const product = Array.isArray(prodData) ? prodData[0] : prodData;
                        const unitFactor = getSafeUnitFactor(product?.unitFactor);
                        const quantityBase = Number(item.quantity ?? 0);
                        const warehouseProductKey = `${warehouseId}-${productId}`;
                        return {
                            ...item,
                            productName: product?.productName ?? `Товар #${productId}`,
                            productPrice: avgReceiptPriceByWarehouseProduct[warehouseProductKey] ?? product?.productPrice ?? 0,
                            unit: product?.unit ?? product?.baseUnit ?? "pcs",
                            baseUnit: product?.baseUnit ?? product?.unit ?? "pcs",
                            unitFactor,
                            quantityBase,
                            quantity: toDisplayQty(quantityBase, unitFactor)
                        };
                    } catch (e) {
                        console.error("Ошибка загрузки продукта для перемещения:", productId, e);
                        return {
                            ...item,
                            productName: `Товар #${productId}`,
                            productPrice: 0,
                            unit: "pcs",
                            baseUnit: "pcs",
                            unitFactor: 1,
                            quantityBase: Number(item.quantity ?? 0),
                            quantity: Number(item.quantity ?? 0)
                        };
                    }
                })
            );

            setProductsFrom(enriched.filter(p => p && Number(p.quantityBase ?? 0) > 0));
        } catch (err) {
            console.error(err);
            setProductsFrom([]);
        }
    };

    const handleSave = () => {
        if (!warehouseName) return;

        const payload = { warehouseName };
        const method = editingId ? "PUT" : "POST";
        const url = editingId ? `${API_WAREHOUSES}/${editingId}` : API_WAREHOUSES;

        fetch(url, {
            method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(res => res.json())
            .then(() => {
                setWarehouseName("");
                setEditingId(null);
                loadWarehouses();
            })
            .catch(err => console.error(err));
    };

    const handleEdit = (wh) => {
        setWarehouseName(wh.warehouseName);
        setEditingId(wh.warehouseId);
    };

    const handleDelete = (id) => {
        if (!window.confirm("Удалить склад?")) return;

        fetch(`${API_WAREHOUSES}/${id}`, { method: "DELETE" })
            .then(() => loadWarehouses())
            .catch(err => console.error(err));
    };

    const handleMovement = () => {
        if (!movementFrom || !movementTo || !movementProduct || !movementQuantity) return;
        const selectedProduct = productsFrom.find(p => String(p.productId) === String(movementProduct));
        const unitPrice = Number(selectedProduct?.productPrice ?? 0);
        const normalized = normalizeMovementPayload(selectedProduct, movementQuantity, unitPrice);

        const payload = {
            fromWarehouseId: movementFrom,
            toWarehouseId: movementTo,
            productId: movementProduct,
            quantity: normalized.quantity,
            unitPrice: Number.isFinite(normalized.unitPrice) ? normalized.unitPrice : 0
        };

        fetch(API_MOVEMENTS, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(res => {
                if (!res.ok) throw new Error("Ошибка создания перемещения");
                return res;
            })
            .then(() => {
                setMovementFrom("");
                setMovementTo("");
                setMovementProduct("");
                setMovementQuantity("");
                setProductsFrom([]);
                alert("Перемещение создано");
                loadWarehouses();
            })
            .catch(err => console.error(err));
    };
    const selectedMovementProduct = productsFrom.find(p => String(p.productId) === String(movementProduct));
    const movementUnit = selectedMovementProduct ? getQuantityInputLabel(selectedMovementProduct) : "";

    const openProductPicker = (warehouseId) => {
        setProductPickerWarehouseId(warehouseId);
        setProductPickerSearch("");
        setSelectedCatalogProductId("");
        setSelectedCatalogQuantity("");
        setSelectedCatalogPrice("");
        setProductPickerError("");
    };

    const closeProductPicker = () => {
        setProductPickerWarehouseId(null);
        setProductPickerSearch("");
        setSelectedCatalogProductId("");
        setSelectedCatalogQuantity("");
        setSelectedCatalogPrice("");
        setProductPickerError("");
    };

    const handleSelectCatalogProduct = (product) => {
        setSelectedCatalogProductId(String(product.productId));
        setSelectedCatalogPrice(String(product.productPrice ?? ""));
        setProductPickerError("");
    };

    const handleAddExistingProductToWarehouse = async () => {
        const warehouseId = Number(productPickerWarehouseId);
        const productId = Number(selectedCatalogProductId);
        const quantity = parseFloat(selectedCatalogQuantity);
        const unitPrice = parseFloat(selectedCatalogPrice);

        if (!warehouseId) {
            setProductPickerError("Не выбран склад");
            return;
        }

        if (!productId) {
            setProductPickerError("Выберите продукт из списка");
            return;
        }

        if (!Number.isFinite(quantity) || quantity <= 0) {
            setProductPickerError("Укажите корректное количество");
            return;
        }

        if (!Number.isFinite(unitPrice) || unitPrice <= 0) {
            setProductPickerError("Укажите корректную цену прихода");
            return;
        }

        try {
            const productMeta = catalogProducts.find((product) => Number(product.productId) === productId);
            const normalized = normalizeMovementPayload(productMeta, quantity, unitPrice);

            const res = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    docType: "receipt",
                    toWarehouseId: warehouseId,
                    supplierId: productMeta?.supplierId ?? productMeta?.supplierID ?? null,
                    productId,
                    quantity: normalized.quantity,
                    unitPrice: normalized.unitPrice,
                    comment: `warehouse-existing-add:${warehouseId}`,
                    createdBy: "warehouse-ui"
                })
            });

            if (!res.ok) {
                throw new Error("Ошибка добавления продукта на склад");
            }

            await loadWarehouses();
            closeProductPicker();
            alert("Продукт добавлен на склад");
        } catch (err) {
            console.error(err);
            setProductPickerError("Не удалось добавить выбранный продукт");
        }
    };

    // Функция для добавления продукта на склад
    const handleAddProductToWarehouse = async (warehouseId) => {
        if (!newProductName.trim() || !newProductSupplierId || !newProductPrice.trim() || !newProductWaste.trim() || !newProductQuantity.trim()) {
            alert("Заполните все поля для добавления продукта");
            return;
        }

        try {
            const createdProductRes = await fetch(API_PRODUCTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    supplierId: Number(newProductSupplierId),
                    productName: newProductName.trim(),
                    productPrice: parseFloat(newProductPrice),
                    waste: parseFloat(newProductWaste),
                    isFavorite: false
                })
            });

            if (!createdProductRes.ok) {
                const errorText = await createdProductRes.text();
                throw new Error(errorText || "Ошибка создания продукта");
            }

            const createdProduct = await createdProductRes.json();
            const createdProductId = Number(createdProduct?.productId);
            if (!createdProductId) {
                throw new Error("Не удалось получить ID созданного продукта");
            }

            const addToWarehouseRes = await fetch(`${API_WAREHOUSES}/${warehouseId}/products`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify([{
                    productId: createdProductId,
                    quantity: parseFloat(newProductQuantity)
                }])
            });

            if (!addToWarehouseRes.ok) {
                const errorText = await addToWarehouseRes.text();
                throw new Error(errorText || "Ошибка добавления продукта на склад");
            }

            try {
                await fetch(API_MOVEMENTS, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        docType: "receipt",
                        toWarehouseId: Number(warehouseId),
                        supplierId: Number(newProductSupplierId),
                        productId: createdProductId,
                        quantity: parseFloat(newProductQuantity),
                        unitPrice: parseFloat(newProductPrice),
                        comment: `warehouse-manual-add:${warehouseId}`,
                        createdBy: "warehouse-ui"
                    })
                });
            } catch (movementErr) {
                console.warn("Не удалось записать движение прихода:", movementErr);
            }

            setNewProductName("");
            setNewProductSupplierId("");
            setNewProductPrice("");
            setNewProductWaste("");
            setNewProductQuantity("");
            setAddingToWarehouseId(null);

            loadWarehouses();
            alert("Продукт успешно добавлен на склад!");
        } catch (error) {
            console.error("Ошибка:", error);
            alert("Ошибка при добавлении продукта");
        }
    };

    // Функция для начала добавления продукта на склад
    const startAddingProduct = (warehouseId) => {
        setAddingToWarehouseId(warehouseId);
        setNewProductName("");
        setNewProductSupplierId("");
        setNewProductPrice("");
        setNewProductWaste("");
        setNewProductQuantity("");
    };

    const cancelAddingProduct = () => {
        setAddingToWarehouseId(null);
        setNewProductName("");
        setNewProductSupplierId("");
        setNewProductPrice("");
        setNewProductWaste("");
        setNewProductQuantity("");
    };

    const getAdjustKey = (warehouseId, productId) => `${warehouseId}-${productId}`;

    const setAdjustInput = (key, value) => {
        setAdjustQtyInputs(prev => ({ ...prev, [key]: value }));
    };

    const setAdjustPriceInput = (key, value) => {
        setAdjustPriceInputs(prev => ({ ...prev, [key]: value }));
    };

    const handleWriteoffMovement = async (warehouseId, productId, qty, unitPrice, supplierId) => {
        try {
            const productMeta = (warehouseProducts[warehouseId] || []).find(
                (p) => Number(p.productId) === Number(productId)
            );
            const normalized = normalizeMovementPayload(productMeta, qty, unitPrice);
            const res = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    docType: "writeoff",
                    fromWarehouseId: Number(warehouseId),
                    productId: Number(productId),
                    quantity: normalized.quantity,
                    unitPrice: normalized.unitPrice,
                    supplierId: supplierId ? Number(supplierId) : null,
                    comment: `warehouse-writeoff:${warehouseId}`,
                    createdBy: "warehouse-ui"
                })
            });
            if (!res.ok) throw new Error("Ошибка списания через движение");
            setAdjustQtyInputs(prev => ({ ...prev, [getAdjustKey(warehouseId, productId)]: "" }));
            setAdjustPriceInputs(prev => ({ ...prev, [getAdjustKey(warehouseId, productId)]: "" }));
            await loadWarehouses();
        } catch (err) {
            console.error(err);
            alert("Не удалось списать товар через движение");
        }
    };

    const handleAddWithReceiptMovement = async (warehouseId, productId, qty, unitPrice, supplierId) => {
        try {
            const productMeta = (warehouseProducts[warehouseId] || []).find(
                (p) => Number(p.productId) === Number(productId)
            );
            const normalized = normalizeMovementPayload(productMeta, qty, unitPrice);
            const res = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    docType: "receipt",
                    toWarehouseId: Number(warehouseId),
                    productId: Number(productId),
                    quantity: normalized.quantity,
                    unitPrice: normalized.unitPrice,
                    supplierId: supplierId ? Number(supplierId) : null,
                    comment: `warehouse-adjust-add:${warehouseId}`,
                    createdBy: "warehouse-ui"
                })
            });
            if (!res.ok) throw new Error("Ошибка добавления через движение");
            setAdjustQtyInputs(prev => ({ ...prev, [getAdjustKey(warehouseId, productId)]: "" }));
            setAdjustPriceInputs(prev => ({ ...prev, [getAdjustKey(warehouseId, productId)]: "" }));
            await loadWarehouses();
        } catch (err) {
            console.error(err);
            alert("Не удалось добавить товар через движение");
        }
    };

    return (
        <div className={styles.container}>
            <h2 className={styles.header}>Управление складами</h2>

            {/* Форма склада */}
            <div className={styles.form}>
                <input
                    className={styles.input}
                    type="text"
                    placeholder="Название склада"
                    value={warehouseName}
                    onChange={e => setWarehouseName(e.target.value)}
                />
                <label className={styles.checkboxLabel} style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                        type="checkbox"
                        checked={showZeroStock}
                        onChange={(e) => setShowZeroStock(e.target.checked)}
                    />
                    Показывать нулевые
                </label>
                <button className={styles.button} onClick={handleSave}>
                    {editingId ? "Сохранить" : "Добавить"}
                </button>
                {editingId && (
                    <button
                        className={`${styles.button} ${styles.cancelButton}`}
                        onClick={() => { setEditingId(null); setWarehouseName(""); }}
                    >
                        Отмена
                    </button>
                )}
            </div>

            {/* Список складов */}
            {loadingWarehouses ? (
                <p>Загрузка складов...</p>
            ) : (
                <div className={styles.warehousesList}>
                    {warehouses.map(wh => (
                        <div key={wh.warehouseId} className={styles.warehouseCard}>
                            <div className={styles.warehouseHeader}>
                                <h3 className={styles.warehouseTitle}>
                                    {wh.warehouseName}
                                    {wh.isMain && (
                                        <span className={styles.mainBadge}>Главный</span>
                                    )}
                                    <span className={styles.productCount}>
                                        ({warehouseProducts[wh.warehouseId]?.length || 0} товаров)
                                    </span>
                                </h3>
                                <div className={styles.warehouseActions}>
                                    {!wh.isMain && (
                                        <button
                                            className={`${styles.actionButton} ${styles.mainButton}`}
                                            onClick={() => setMainWarehouse(wh.warehouseId)}
                                        >
                                            Сделать главным
                                        </button>
                                    )}
                                    <button
                                        className={`${styles.actionButton} ${styles.editButton}`}
                                        onClick={() => handleEdit(wh)}
                                    >
                                        Редактировать
                                    </button>
                                    <button
                                        className={`${styles.actionButton} ${styles.deleteButton}`}
                                        onClick={() => handleDelete(wh.warehouseId)}
                                    >
                                        Удалить
                                    </button>
                                    <button
                                        className={`${styles.actionButton} ${styles.addButton}`}
                                        onClick={() => openProductPicker(wh.warehouseId)}
                                    >
                                        + Добавить продукт
                                    </button>
                                </div>
                            </div>

                            {/* Форма добавления нового продукта */}
                            {addingToWarehouseId === wh.warehouseId && (
                                <div className={styles.addProductForm}>
                                    <h4>Добавить новый продукт на склад</h4>
                                    <div className={styles.productInputs}>
                                        <input
                                            className={styles.productInput}
                                            type="text"
                                            placeholder="Название продукта"
                                            value={newProductName}
                                            onChange={e => setNewProductName(e.target.value)}
                                        />
                                        <select
                                            className={styles.productInput}
                                            value={newProductSupplierId}
                                            onChange={e => setNewProductSupplierId(e.target.value)}
                                        >
                                            <option value="">Поставщик</option>
                                            {suppliers.map(s => (
                                                <option key={s.supplierId ?? s.supplierID ?? s.id}
                                                        value={s.supplierId ?? s.supplierID ?? s.id}>
                                                    {s.supplierName ?? s.name}
                                                </option>
                                            ))}
                                        </select>
                                        <input
                                            className={styles.productInput}
                                            type="number"
                                            placeholder="Цена продукта"
                                            value={newProductPrice}
                                            onChange={e => setNewProductPrice(e.target.value)}
                                            min="0"
                                            step="0.01"
                                        />
                                        <input
                                            className={styles.productInput}
                                            type="text"
                                            placeholder="Waste"
                                            value={newProductWaste}
                                            onChange={e => setNewProductWaste(e.target.value)}
                                        />
                                        <input
                                            className={styles.productInput}
                                            type="number"
                                            placeholder="Количество"
                                            value={newProductQuantity}
                                            onChange={e => setNewProductQuantity(e.target.value)}
                                            min="0"
                                            step="1"
                                        />
                                        <div className={styles.productFormButtons}>
                                            <button
                                                className={`${styles.button} ${styles.saveProductButton}`}
                                                onClick={() => handleAddProductToWarehouse(wh.warehouseId)}
                                            >
                                                Сохранить продукт
                                            </button>
                                            <button
                                                className={`${styles.button} ${styles.cancelProductButton}`}
                                                onClick={cancelAddingProduct}
                                            >
                                                Отмена
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Продукты склада */}
                            <div className={styles.productsSection}>
                                {(() => {
                                    const list = (warehouseProducts[wh.warehouseId] || []).filter(p => {
                                        if (showZeroStock) return true;
                                        return Number(p.quantityBase ?? 0) > 0;
                                    });
                                    if (list.length === 0) {
                                        return <p className={styles.noProducts}>На складе нет продуктов</p>;
                                    }
                                    return (
                                    <table className={styles.productsTable}>
                                        <thead>
                                        <tr>
                                            <th>Продукт</th>
                                            <th>ID</th>
                                            <th>Средняя цена / base</th>
                                            <th>Последняя цена / unit</th>
                                            <th>Waste</th>
                                            <th>Поставщик</th>
                                            <th>Ед.</th>
                                            <th>Количество</th>
                                            <th>Добавить / Списать</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {list.map(p => {
                                            const adjustKey = getAdjustKey(wh.warehouseId, p.productId);
                                            const inputVal = adjustQtyInputs[adjustKey] ?? "";
                                            const inputPriceVal = adjustPriceInputs[adjustKey] ?? "";
                                            return (
                                                <tr key={p.productId}>
                                                    <td>{p.productName}</td>
                                                    <td>{p.productId}</td>
                                                    <td>{formatPriceWithUnit(p.avgPrice ?? p.productPrice, p.baseUnit ?? p.unit ?? "pcs")}</td>
                                                    <td>{p.lastPurchasePrice != null ? formatPriceWithUnit(p.lastPurchasePrice, p.unit ?? p.baseUnit ?? "pcs") : "—"}</td>
                                                    <td>{p.waste}</td>
                                                    <td>{p.supplierName ?? "—"}</td>
                                                    <td>{getWarehouseUnitLabel(p)}</td>
                                                    <td>
                                                        {hasExpandedUnitDisplay(p)
                                                            ? `${formatQty(p.quantityBase)} ${p.baseUnit ?? p.unit ?? "pcs"}`
                                                            : `${formatQty(p.quantity)} ${p.unit ?? "pcs"}`}
                                                        {hasExpandedUnitDisplay(p) && (
                                                            <div className={styles.qtyHint}>
                                                                Закупочно: {formatQty(p.quantity)} {p.unit ?? "ед."} (1 ед. = {getSafeUnitFactor(p.unitFactor)} {p.baseUnit ?? p.unit ?? "pcs"})
                                                            </div>
                                                        )}
                                                    </td>
                                                    <td>
                                                        <div className={styles.adjustQuantityCell}>
                                                            <input
                                                                type="number"
                                                                className={styles.adjustInput}
                                                                placeholder={`Кол-во${getQuantityInputLabel(p) ? ` (${getQuantityInputLabel(p)})` : ""}`}
                                                                value={inputVal}
                                                                onChange={e => setAdjustInput(adjustKey, e.target.value)}
                                                                min="0"
                                                                step="0.01"
                                                            />
                                                            <input
                                                                type="number"
                                                                className={styles.adjustInput}
                                                                placeholder="Цена"
                                                                value={inputPriceVal}
                                                                onChange={e => setAdjustPriceInput(adjustKey, e.target.value)}
                                                                min="0"
                                                                step="0.01"
                                                            />
                                                            <button
                                                                type="button"
                                                                className={styles.adjustBtnAdd}
                                                                onClick={() => {
                                                                    const v = parseFloat(inputVal);
                                                                    const price = parseFloat(inputPriceVal);
                                                                    if (isNaN(v) || v <= 0) return;
                                                                    if (isNaN(price) || price <= 0) {
                                                                        alert("Укажите цену для добавления");
                                                                        return;
                                                                    }
                                                                    handleAddWithReceiptMovement(wh.warehouseId, p.productId, v, price, p.supplierId ?? p.supplierID);
                                                                }}
                                                            >
                                                                Добавить
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className={styles.adjustBtnSubtract}
                                                                onClick={() => {
                                                                    const v = parseFloat(inputVal);
                                                                    const price = parseFloat(inputPriceVal);
                                                                    const fallbackPrice = Number(p.productPrice ?? 0);
                                                                    const finalPrice = !isNaN(price) && price > 0 ? price : fallbackPrice;
                                                                    if (isNaN(v) || v <= 0) return;
                                                                    handleWriteoffMovement(
                                                                        wh.warehouseId,
                                                                        p.productId,
                                                                        v,
                                                                        finalPrice,
                                                                        p.supplierId ?? p.supplierID
                                                                    );
                                                                }}
                                                            >
                                                                Списать
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                        </tbody>
                                    </table>
                                    );
                                })()}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Форма перемещения */}
            <h3 className={styles.sectionTitle}>Создать перемещение</h3>
            <div className={styles.form}>
                <select className={styles.input} value={movementFrom} onChange={e => setMovementFrom(e.target.value)}>
                    <option value="">Склад отправитель</option>
                    {warehouses.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>)}
                </select>
                <select className={styles.input} value={movementTo} onChange={e => setMovementTo(e.target.value)}>
                    <option value="">Склад получатель</option>
                    {warehouses.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>)}
                </select>
                <select className={styles.input} value={movementProduct} onChange={e => setMovementProduct(e.target.value)} disabled={!movementFrom}>
                    <option value="">Выберите продукт со склада отправителя</option>
                    {productsFrom.map(p => (
                        <option key={p.productId} value={p.productId}>
                            {p.productName} (остаток: {hasExpandedUnitDisplay(p)
                                ? `${formatQty(p.quantityBase)} ${p.baseUnit ?? p.unit ?? "pcs"}`
                                : `${formatQty(p.quantity)} ${p.unit ?? "pcs"}`})
                        </option>
                    ))}
                </select>
                <input
                    className={styles.input}
                    type="number"
                    placeholder={`Количество${movementUnit ? ` (${movementUnit})` : ""}`}
                    value={movementQuantity}
                    onChange={e => setMovementQuantity(e.target.value)}
                    min={0}
                />
                <button className={styles.button} onClick={handleMovement}>Создать</button>
            </div>

            {productPickerWarehouseId && (
                <div className={styles.modalOverlay} onClick={closeProductPicker}>
                    <div className={styles.modalCard} onClick={(e) => e.stopPropagation()}>
                        <div className={styles.modalHeader}>
                            <div>
                                <h3 className={styles.modalTitle}>Добавить существующий продукт</h3>
                                <p className={styles.modalSubtitle}>
                                    Выберите товар из каталога, задайте количество и цену прихода для этого склада.
                                </p>
                            </div>
                            <button
                                type="button"
                                className={`${styles.button} ${styles.cancelButton}`}
                                onClick={closeProductPicker}
                            >
                                Закрыть
                            </button>
                        </div>

                        <div className={styles.modalControls}>
                            <input
                                className={styles.input}
                                type="text"
                                placeholder="Поиск по названию или поставщику"
                                value={productPickerSearch}
                                onChange={e => setProductPickerSearch(e.target.value)}
                            />
                            <input
                                className={styles.input}
                                type="number"
                                placeholder="Количество"
                                value={selectedCatalogQuantity}
                                onChange={e => setSelectedCatalogQuantity(e.target.value)}
                                min="0"
                                step="0.01"
                            />
                            <input
                                className={styles.input}
                                type="number"
                                placeholder="Цена прихода"
                                value={selectedCatalogPrice}
                                onChange={e => setSelectedCatalogPrice(e.target.value)}
                                min="0"
                                step="0.01"
                            />
                        </div>

                        <div className={styles.modalHint}>
                            Если нужного товара нет, его можно создать на странице `Продукты`, а затем вернуться сюда.
                        </div>

                        {productPickerError && (
                            <div className={styles.modalError}>{productPickerError}</div>
                        )}

                        <div className={styles.productPickerList}>
                            {filteredCatalogProducts.length > 0 ? (
                                filteredCatalogProducts.map((product) => {
                                    const supplierId = product.supplierId ?? product.supplierID;
                                    const supplier = suppliers.find((s) => (s.supplierId ?? s.supplierID ?? s.id) === supplierId);
                                    const isActive = String(product.productId) === String(selectedCatalogProductId);
                                    return (
                                        <button
                                            key={product.productId}
                                            type="button"
                                            className={`${styles.productPickerItem} ${isActive ? styles.productPickerItemActive : ""}`}
                                            onClick={() => handleSelectCatalogProduct(product)}
                                        >
                                            <div className={styles.productPickerMain}>
                                                <strong>{product.productName}</strong>
                                                <span className={styles.productPickerMeta}>
                                                    Поставщик: {supplier?.supplierName ?? supplier?.name ?? "—"}
                                                </span>
                                            </div>
                                            <div className={styles.productPickerSide}>
                                                <span>ID: {product.productId}</span>
                                                <span>Цена: {Number(product.productPrice ?? 0).toFixed(2)}</span>
                                                <span>Ед.: {product.unit ?? product.baseUnit ?? "pcs"}</span>
                                            </div>
                                        </button>
                                    );
                                })
                            ) : (
                                <div className={styles.noProducts}>Продукты не найдены</div>
                            )}
                        </div>

                        <div className={styles.modalActions}>
                            <button
                                type="button"
                                className={`${styles.button} ${styles.cancelButton}`}
                                onClick={closeProductPicker}
                            >
                                Отмена
                            </button>
                            <button
                                type="button"
                                className={styles.button}
                                onClick={handleAddExistingProductToWarehouse}
                            >
                                Добавить на склад
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
