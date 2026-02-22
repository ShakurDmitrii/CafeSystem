import { useEffect, useState } from "react";
import styles from './WarehousePage.module.css';

const API_WAREHOUSES = "http://localhost:8080/warehouses";
const API_MOVEMENTS = "http://localhost:8080/movements";
const API_SUPPLIERS = "http://localhost:8080/api/supplier";
const API_PRODUCTS = "http://localhost:8080/api/product";

export default function WarehousePage() {
    const [warehouses, setWarehouses] = useState([]);
    const [warehouseName, setWarehouseName] = useState("");
    const [editingId, setEditingId] = useState(null);

    const [movementFrom, setMovementFrom] = useState("");
    const [movementTo, setMovementTo] = useState("");
    const [movementProduct, setMovementProduct] = useState("");
    const [movementQuantity, setMovementQuantity] = useState("");

    const [productsFrom, setProductsFrom] = useState([]);
    const [warehouseProducts, setWarehouseProducts] = useState({});
    const [avgReceiptPriceByProductId, setAvgReceiptPriceByProductId] = useState({});
    const [loadingWarehouses, setLoadingWarehouses] = useState(true);
    const [suppliers, setSuppliers] = useState([]);
    const [adjustQtyInputs, setAdjustQtyInputs] = useState({}); // { "whId-productId": "0" }
    const [adjustPriceInputs, setAdjustPriceInputs] = useState({}); // { "whId-productId": "0" }

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
    }, [movementFrom, avgReceiptPriceByProductId]);

    const buildWeightedReceiptPriceMap = (movementsList) => {
        const totalsByProduct = {};

        movementsList
            .filter(m => m.docType === "receipt" && m.productId != null && m.unitPrice != null && m.quantity != null)
            .forEach(m => {
                const pid = Number(m.productId);
                const qty = Number(m.quantity);
                const price = Number(m.unitPrice);
                if (!Number.isFinite(pid) || !Number.isFinite(qty) || !Number.isFinite(price) || qty <= 0 || price < 0) return;

                if (!totalsByProduct[pid]) {
                    totalsByProduct[pid] = { qty: 0, amount: 0 };
                }
                totalsByProduct[pid].qty += qty;
                totalsByProduct[pid].amount += qty * price;
            });

        return Object.entries(totalsByProduct).reduce((acc, [pid, total]) => {
            if (total.qty > 0) {
                acc[Number(pid)] = total.amount / total.qty;
            }
            return acc;
        }, {});
    };

    const loadWarehouses = async () => {
        setLoadingWarehouses(true);
        try {
            const [whRes, supRes, movRes] = await Promise.all([
                fetch(API_WAREHOUSES),
                fetch(API_SUPPLIERS),
                fetch(API_MOVEMENTS)
            ]);
            const whData = await whRes.json();
            const warehousesArray = Array.isArray(whData) ? whData : [];
            setWarehouses(warehousesArray);

            const supData = await supRes.json().catch(() => []);
            const suppliersList = Array.isArray(supData) ? supData : [];
            setSuppliers(suppliersList);

            const movData = movRes.ok ? await movRes.json().catch(() => []) : [];
            const movementsList = Array.isArray(movData) ? movData : [];
            const weightedReceiptPriceByProductId = buildWeightedReceiptPriceMap(movementsList);
            const latestReceiptPriceByProductId = movementsList
                .filter(m => m.docType === "receipt" && m.productId != null && m.unitPrice != null)
                .sort((a, b) => new Date(b.docDate || 0) - new Date(a.docDate || 0))
                .reduce((acc, m) => {
                    const key = Number(m.productId);
                    if (!Number.isNaN(key) && acc[key] == null) {
                        acc[key] = Number(m.unitPrice);
                    }
                    return acc;
                }, {});

            setAvgReceiptPriceByProductId(weightedReceiptPriceByProductId);

            await loadAllWarehouseProducts(
                warehousesArray,
                suppliersList,
                weightedReceiptPriceByProductId,
                latestReceiptPriceByProductId
            );
        } catch (err) {
            console.error(err);
        } finally {
            setLoadingWarehouses(false);
        }
    };

    const loadAllWarehouseProducts = async (
        warehousesList,
        suppliersList = [],
        weightedReceiptPriceByProductId = {},
        latestReceiptPriceByProductId = {}
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
                                const resProd = await fetch(`http://localhost:8080/api/product/${productId}`);
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
                                const weightedReceiptPrice = weightedReceiptPriceByProductId[productId];
                                const latestReceiptPrice = latestReceiptPriceByProductId[productId];
                                const supplierId = product.supplierId ?? product.supplierID;
                                const supplier = suppliersList.find(s => (s.supplierId ?? s.supplierID ?? s.id) === supplierId);
                                const supplierName = supplier ? (supplier.supplierName ?? supplier.name) : "—";
                                const avgPrice = weightedReceiptPrice ?? product.productPrice;

                                return {
                                    ...product,
                                    productPrice: avgPrice,
                                    avgPrice,
                                    lastPurchasePrice: latestReceiptPrice ?? null,
                                    productWarehouseId: warehouseProduct.productWarehouseId,
                                    quantity: whQuantity ?? 0,
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
                                    quantity: whQuantity ?? 0,
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
                        const prodRes = await fetch(`http://localhost:8080/api/product/${productId}`);
                        if (!prodRes.ok) {
                            return {
                                ...item,
                                productName: `Товар #${productId}`
                            };
                        }

                        const prodData = await prodRes.json();
                        const product = Array.isArray(prodData) ? prodData[0] : prodData;
                        return {
                            ...item,
                            productName: product?.productName ?? `Товар #${productId}`,
                            productPrice: avgReceiptPriceByProductId[productId] ?? product?.productPrice ?? 0
                        };
                    } catch (e) {
                        console.error("Ошибка загрузки продукта для перемещения:", productId, e);
                        return {
                            ...item,
                            productName: `Товар #${productId}`,
                            productPrice: 0
                        };
                    }
                })
            );

            setProductsFrom(enriched.filter(Boolean));
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

        const payload = {
            fromWarehouseId: movementFrom,
            toWarehouseId: movementTo,
            productId: movementProduct,
            quantity: parseFloat(movementQuantity),
            unitPrice: Number.isFinite(unitPrice) ? unitPrice : 0
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
            const res = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    docType: "writeoff",
                    fromWarehouseId: Number(warehouseId),
                    productId: Number(productId),
                    quantity: Number(qty),
                    unitPrice: Number(unitPrice),
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
            const res = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    docType: "receipt",
                    toWarehouseId: Number(warehouseId),
                    productId: Number(productId),
                    quantity: Number(qty),
                    unitPrice: Number(unitPrice),
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
                                    <span className={styles.productCount}>
                                        ({warehouseProducts[wh.warehouseId]?.length || 0} товаров)
                                    </span>
                                </h3>
                                <div className={styles.warehouseActions}>
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
                                        onClick={() => startAddingProduct(wh.warehouseId)}
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
                                {warehouseProducts[wh.warehouseId]?.length > 0 ? (
                                    <table className={styles.productsTable}>
                                        <thead>
                                        <tr>
                                            <th>Продукт</th>
                                            <th>ID</th>
                                            <th>Средняя цена</th>
                                            <th>Последняя закупочная цена</th>
                                            <th>Waste</th>
                                            <th>Поставщик</th>
                                            <th>Количество</th>
                                            <th>Добавить / Списать</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {warehouseProducts[wh.warehouseId].map(p => {
                                            const adjustKey = getAdjustKey(wh.warehouseId, p.productId);
                                            const inputVal = adjustQtyInputs[adjustKey] ?? "";
                                            const inputPriceVal = adjustPriceInputs[adjustKey] ?? "";
                                            return (
                                                <tr key={p.productId}>
                                                    <td>{p.productName}</td>
                                                    <td>{p.productId}</td>
                                                    <td>{p.avgPrice ?? p.productPrice ?? "—"}</td>
                                                    <td>{p.lastPurchasePrice ?? "—"}</td>
                                                    <td>{p.waste}</td>
                                                    <td>{p.supplierName ?? "—"}</td>
                                                    <td>{p.quantity ?? 0}</td>
                                                    <td>
                                                        <div className={styles.adjustQuantityCell}>
                                                            <input
                                                                type="number"
                                                                className={styles.adjustInput}
                                                                placeholder="Кол-во"
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
                                ) : (
                                    <p className={styles.noProducts}>На складе нет продуктов</p>
                                )}
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
                    {productsFrom.map(p => <option key={p.productId} value={p.productId}>{p.productName}</option>)}
                </select>
                <input
                    className={styles.input}
                    type="number"
                    placeholder="Количество"
                    value={movementQuantity}
                    onChange={e => setMovementQuantity(e.target.value)}
                    min={0}
                />
                <button className={styles.button} onClick={handleMovement}>Создать</button>
            </div>
        </div>
    );
}
