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

    const [modalOpen, setModalOpen] = useState(false);
    const [modalWarehouse, setModalWarehouse] = useState(null);
    const [warehouseProducts, setWarehouseProducts] = useState([]);
    const [loadingProducts, setLoadingProducts] = useState(false);

    useEffect(() => {
        loadWarehouses();
    }, []);

    useEffect(() => {
        if (movementFrom) loadProductsFromWarehouse(movementFrom);
        else setProductsFrom([]);
        setMovementProduct("");
    }, [movementFrom]);

    const loadWarehouses = () => {
        fetch(API_WAREHOUSES)
            .then(res => res.json())
            .then(data => setWarehouses(Array.isArray(data) ? data : []))
            .catch(err => console.error(err));
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
            })
            .catch(err => console.error(err));
    };

    // --- Модалка с корректным рендером продуктов ---
    const openWarehouseModal = async (wh) => {
        setModalWarehouse(wh);
        setLoadingProducts(true);

        try {
            const res = await fetch(`${API_WAREHOUSES}/${wh.warehouseId}/products`);
            const warehouseProductsData = await res.json();

            if (!Array.isArray(warehouseProductsData) || warehouseProductsData.length === 0) {
                setWarehouseProducts([]);
                setModalOpen(true);
                return;
            }

            const productsFullData = await Promise.all(
                warehouseProductsData.map(async (p) => {
                    if (!p.productId) return null;

                    try {
                        const resProd = await fetch(`http://localhost:8080/api/product/${p.productId}`);
                        if (!resProd.ok) {
                            return {
                                productId: p.productId,
                                productName: "Неизвестный продукт",
                                productPrice: "-",
                                waste: "-"
                            };
                        }

                        const productData = await resProd.json();
                        // Берём первый элемент массива
                        return Array.isArray(productData) ? productData[0] : productData;

                    } catch (err) {
                        console.error("Ошибка продукта", p.productId, err);
                        return {
                            productId: p.productId,
                            productName: "Неизвестный продукт",
                            productPrice: "-",
                            waste: "-"
                        };
                    }
                })
            );

            setWarehouseProducts(productsFullData.filter(p => p !== null));
            setModalOpen(true);

        } catch (err) {
            console.error(err);
            setWarehouseProducts([]);
            setModalOpen(true);
        } finally {
            setLoadingProducts(false);
        }
    };


    const closeModal = () => {
        setModalOpen(false);
        setModalWarehouse(null);
        setWarehouseProducts([]);
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
            <ul className={styles.list}>
                {warehouses.map(wh => (
                    <li key={wh.warehouseId} className={styles.listItem}>
                        <span>{wh.warehouseName}</span>
                        <div className={styles.listItemButtons}>
                            <button className={`${styles.listItemButton} ${styles.editButton}`} onClick={() => handleEdit(wh)}>Редактировать</button>
                            <button className={`${styles.listItemButton} ${styles.deleteButton}`} onClick={() => handleDelete(wh.warehouseId)}>Удалить</button>
                            <button className={`${styles.listItemButton}`} onClick={() => openWarehouseModal(wh)}>Просмотр продуктов</button>
                        </div>
                    </li>
                ))}
            </ul>

            {/* Форма перемещения */}
            <h3>Создать перемещение</h3>
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

            {/* Модалка */}
            {modalOpen && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <div className={styles.modalHeader}>
                            <h3>Продукты склада: {modalWarehouse?.warehouseName}</h3>
                            <button onClick={closeModal} className={styles.closeModalBtn}>×</button>
                        </div>
                        <div className={styles.modalContent}>
                            {loadingProducts ? (
                                <p>Загрузка продуктов...</p>
                            ) : warehouseProducts.length > 0 ? (
                                <table className={styles.table}>
                                    <thead>
                                    <tr>
                                        <th>Продукт</th>
                                        <th>ID</th>
                                        <th>Цена</th>
                                        <th>Waste</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {warehouseProducts.map(p => (
                                        <tr key={p.productId}>
                                            <td>{p.productName}</td>
                                            <td>{p.productId}</td>
                                            <td>{p.productPrice}</td>
                                            <td>{p.waste}</td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            ) : (
                                <p>Продукты не найдены</p>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
