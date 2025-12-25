import { useEffect, useState } from "react";
import styles from './WarehousePage.module.css';

const API_WAREHOUSES = "http://localhost:8080/warehouses";
const API_MOVEMENTS = "http://localhost:8080/movements";

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
    const [loadingWarehouses, setLoadingWarehouses] = useState(true);

    // Состояния для добавления продукта
    const [newProductName, setNewProductName] = useState("");
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
    }, [movementFrom]);

    const loadWarehouses = async () => {
        setLoadingWarehouses(true);
        try {
            const res = await fetch(API_WAREHOUSES);
            const data = await res.json();
            const warehousesArray = Array.isArray(data) ? data : [];
            setWarehouses(warehousesArray);

            // Загружаем продукты для всех складов
            await loadAllWarehouseProducts(warehousesArray);
        } catch (err) {
            console.error(err);
        } finally {
            setLoadingWarehouses(false);
        }
    };

    const loadAllWarehouseProducts = async (warehousesList) => {
        const productsMap = {};

        for (const wh of warehousesList) {
            try {
                const res = await fetch(`${API_WAREHOUSES}/${wh.warehouseId}/products`);
                const warehouseProductsData = await res.json();

                console.log(`Данные продуктов для склада ${wh.warehouseId}:`, warehouseProductsData);

                if (Array.isArray(warehouseProductsData) && warehouseProductsData.length > 0) {
                    const productsFullData = await Promise.all(
                        warehouseProductsData.map(async (warehouseProduct) => {
                            const { productId } = warehouseProduct;

                            if (!productId) return null;

                            try {
                                // Шаг 1: Получаем основной продукт
                                const resProd = await fetch(`http://localhost:8080/api/product/${productId}`);
                                if (!resProd.ok) {
                                    return {
                                        productId: productId,
                                        productName: "Неизвестный продукт",
                                        productPrice: "-",
                                        waste: "-",
                                        quantity: 0
                                    };
                                }

                                const productData = await resProd.json();
                                const product = Array.isArray(productData) ? productData[0] : productData;

                                // Шаг 2: Получаем ConsProductDTO для quantity
                                let quantity = 0;
                                try {
                                    const consRes = await fetch(`http://localhost:8080/api/consProduct/product/${productId}`);
                                    if (consRes.ok) {
                                        const consProduct = await consRes.json();
                                        quantity = consProduct.quantity || 0;
                                        console.log(`ConsProduct для ${productId}:`, consProduct);
                                    }
                                } catch (consErr) {
                                    console.warn(`Не удалось получить ConsProduct для ${productId}:`, consErr);
                                }

                                return {
                                    ...product,
                                    quantity: quantity
                                };

                            } catch (err) {
                                console.error("Ошибка продукта", productId, err);
                                return {
                                    productId: productId,
                                    productName: "Неизвестный продукт",
                                    productPrice: "-",
                                    waste: "-",
                                    quantity: 0
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
    const loadProductsFromWarehouse = (warehouseId) => {
        fetch(`${API_WAREHOUSES}/${warehouseId}/products`)
            .then(res => res.json())
            .then(data => setProductsFrom(Array.isArray(data) ? data : []))
            .catch(err => console.error(err));
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

        const payload = {
            fromWarehouseId: movementFrom,
            toWarehouseId: movementTo,
            productId: movementProduct,
            quantity: parseFloat(movementQuantity)
        };

        fetch(API_MOVEMENTS, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(res => res.json())
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
        if (!newProductName.trim() || !newProductPrice.trim() || !newProductWaste.trim() || !newProductQuantity.trim()) {
            alert("Заполните все поля для добавления продукта");
            return;
        }

        const productPayload = [
            {
                productName: newProductName,
                productPrice: parseFloat(newProductPrice),
                waste: newProductWaste,
                quantity: parseFloat(newProductQuantity) // Добавляем quantity
            }
        ];

        try {
            const response = await fetch(`${API_WAREHOUSES}/${warehouseId}/products`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(productPayload)
            });

            if (response.ok) {
                // Сброс полей ввода
                setNewProductName("");
                setNewProductPrice("");
                setNewProductWaste("");
                setNewProductQuantity("");
                setAddingToWarehouseId(null);

                // Перезагружаем данные
                loadWarehouses();
                alert("Продукт успешно добавлен на склад!");
            } else {
                alert("Ошибка при добавлении продукта");
            }
        } catch (error) {
            console.error("Ошибка:", error);
            alert("Ошибка при добавлении продукта");
        }
    };

    // Функция для начала добавления продукта на склад
    const startAddingProduct = (warehouseId) => {
        setAddingToWarehouseId(warehouseId);
        setNewProductName("");
        setNewProductPrice("");
        setNewProductWaste("");
        setNewProductQuantity("");
    };

    // Функция для отмены добавления продукта
    const cancelAddingProduct = () => {
        setAddingToWarehouseId(null);
        setNewProductName("");
        setNewProductPrice("");
        setNewProductWaste("");
        setNewProductQuantity("");
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
                                            <th>Цена</th>
                                            <th>Waste</th>
                                            <th>Количество</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {warehouseProducts[wh.warehouseId].map(p => (
                                            <tr key={p.productId}>
                                                <td>{p.productName}</td>
                                                <td>{p.productId}</td>
                                                <td>{p.productPrice}</td>
                                                <td>{p.waste}</td>
                                                <td>{p.quantity || 0}</td>
                                            </tr>
                                        ))}
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