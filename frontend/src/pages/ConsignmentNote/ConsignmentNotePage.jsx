import { useEffect, useState } from "react";
import styles from './ConsignmentNotePage.module.css';
import { useNavigate } from "react-router-dom";

const API_MOVEMENTS = "http://localhost:8080/movements";
const CONSIGNMENT_MOVEMENT_PREFIX = "consignment-note:";

export default function ConsignmentNotePage() {
    const [notes, setNotes] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [selectedNoteId, setSelectedNoteId] = useState(null);
    const [selectedSupplierId, setSelectedSupplierId] = useState(null);

    const [consProducts, setConsProducts] = useState([]);
    const [newProduct, setNewProduct] = useState({ consignmentId: "", productId: "", quantity: "", unitPrice: "" });
    const [formData, setFormData] = useState({ supplierId: '', date: '' });

    const [currentTotal, setCurrentTotal] = useState(0);
    const [totalsByNoteId, setTotalsByNoteId] = useState({});
    const [isCalculatingAll, setIsCalculatingAll] = useState(false);

    const [selectedWarehouseId, setSelectedWarehouseId] = useState("");
    const [warehouses, setWarehouses] = useState([]);

    const navigate = useNavigate();

    async function getNoteReceiptMovements(noteId) {
        const marker = `${CONSIGNMENT_MOVEMENT_PREFIX}${noteId}`;
        const res = await fetch(API_MOVEMENTS);
        if (!res.ok) throw new Error("Не удалось получить движения для накладной");

        const data = await res.json();
        const rows = Array.isArray(data) ? data : [];
        const noteRows = rows.filter(m => {
            const comment = String(m.comment ?? "").trim();
            return m.docType === "receipt" && comment === marker;
        });

        const priceByProductId = {};
        let total = 0;
        noteRows.forEach(m => {
            if (m.productId != null && m.unitPrice != null && priceByProductId[m.productId] == null) {
                priceByProductId[m.productId] = Number(m.unitPrice);
            }
            if (m.lineTotal != null) {
                total += Number(m.lineTotal) || 0;
            } else if (m.unitPrice != null && m.quantity != null) {
                total += (Number(m.unitPrice) || 0) * (Number(m.quantity) || 0);
            }
        });

        return { noteRows, priceByProductId, total };
    }

    // --------------------Склады-------------------------------
    useEffect(() => {
        async function fetchWarehouses() {
            try {
                const res = await fetch("http://localhost:8080/warehouses");
                const data = await res.json();
                setWarehouses(Array.isArray(data) ? data : []);
            } catch (err) {
                console.error(err);
            }
        }
        fetchWarehouses();
    }, []);

    // -------------------- ЗАГРУЗКА НАКЛАДНЫХ И ПОСТАВЩИКОВ --------------------
    useEffect(() => {
        async function fetchAll() {
            try {
                setLoading(true);

                const resNotes = await fetch("http://localhost:8080/api/consignmentNote");
                const notesData = await resNotes.json();
                setNotes(Array.isArray(notesData) ? notesData : []);

                const resSup = await fetch("http://localhost:8080/api/supplier");
                const suppliersData = await resSup.json();
                setSuppliers(Array.isArray(suppliersData) ? suppliersData : []);

            } catch (err) {
                console.error(err);
                setError(err.message);
            } finally {
                setLoading(false);
            }
        }
        fetchAll();
    }, []);

    // -------------------- СОЗДАНИЕ НАКЛАДНОЙ --------------------
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!formData.supplierId) {
            alert("Выберите поставщика!");
            return;
        }
        try {
            const res = await fetch("http://localhost:8080/api/consignmentNote", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(formData)
            });

            if (!res.ok) throw new Error("Ошибка создания накладной");

            const newNote = await res.json();
            setNotes(prev => [...prev, newNote]);
            setFormData({ supplierId: "", date: "" });
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    };

    // -------------------- ОТКРЫТИЕ МОДАЛКИ ТОВАРОВ --------------------
    async function openProducts(noteId) {
        const note = notes.find(n => n.consignmentId === noteId);
        if (!note) return;

        setSelectedNoteId(noteId);
        setSelectedSupplierId(note.supplierId);

        try {
            // 1. Получаем товары накладной
            const resCons = await fetch(`http://localhost:8080/api/consProduct/${noteId}`);
            const consProductsData = await resCons.json();
            console.log("Товары накладной:", consProductsData);

            // 2. Получаем товары поставщика для выпадающего списка
            try {
                const resSupProducts = await fetch(`http://localhost:8080/api/product/supplier/${note.supplierId}`);
                const supplierProductsData = await resSupProducts.json();
                console.log("Товары поставщика:", supplierProductsData);

                // Преобразуем данные в массив, если это необходимо
                let productsArray = [];
                if (Array.isArray(supplierProductsData)) {
                    productsArray = supplierProductsData;
                } else if (supplierProductsData && typeof supplierProductsData === 'object') {
                    // Если это один товар
                    if (supplierProductsData.productId || supplierProductsData.productID) {
                        productsArray = [supplierProductsData];
                    } else {
                        // Или если это объект с массивом внутри
                        for (const key in supplierProductsData) {
                            if (Array.isArray(supplierProductsData[key])) {
                                productsArray = supplierProductsData[key];
                                break;
                            }
                        }
                    }
                }

                setProducts(productsArray);
            } catch (err) {
                console.warn("Не удалось получить товары поставщика:", err);
                setProducts([]);
            }
            // 3. Для каждого товара накладной получаем его полную информацию
            const consProductsWithNames = await Promise.all(
                consProductsData.map(async (cp) => {
                    try {
                        const productId = cp.productId || cp.productID;
                        if (!productId) {
                            return {
                                ...cp,
                                productName: "Неизвестный продукт",
                                productPrice: 0,
                                waste: 0
                            };
                        }

                        // Получаем информацию о товаре
                        const resProd = await fetch(`http://localhost:8080/api/product/${productId}`);

                        if (!resProd.ok) {
                            throw new Error(`Ошибка получения товара ${productId}`);
                        }

                        const productData = await resProd.json();
                        console.log(`Получен товар ${productId}:`, productData);

                        return {
                            ...cp,
                            productName: productData.productName ||
                                productData.name ||
                                "Неизвестный продукт",
                            productPrice: productData.productPrice ||
                                productData.price ||
                                0,
                            waste: productData.waste || 0
                        };
                    } catch (err) {
                        console.error(`Ошибка получения товара:`, err);
                        return {
                            ...cp,
                            productName: "Неизвестный продукт",
                            productPrice: 0,
                            waste: 0
                        };
                    }
                })
            );

            console.log("Обработанные товары накладной:", consProductsWithNames);

            let priceMap = {};
            let movementTotal = null;
            try {
                const movementData = await getNoteReceiptMovements(noteId);
                priceMap = movementData.priceByProductId;
                movementTotal = movementData.noteRows.length > 0 ? movementData.total : null;
            } catch (e) {
                console.warn("Не удалось получить цены из движений:", e);
            }

            const mergedProducts = consProductsWithNames.map(cp => {
                const pId = cp.productId || cp.productID;
                const movementPrice = pId != null ? priceMap[pId] : null;
                return {
                    ...cp,
                    productPrice: movementPrice ?? cp.productPrice
                };
            });

            setConsProducts(mergedProducts);
            setCurrentTotal(movementTotal ?? totalsByNoteId[noteId] ?? 0);
            setNewProduct({ consignmentId: noteId, productId: "", quantity: "", unitPrice: "" });

        } catch (err) {
            console.error("Ошибка в openProducts:", err);
            setError(err.message);
        }
    }

    function closeModal() {
        setSelectedNoteId(null);
        setCurrentTotal(0);
        setConsProducts([]);
    }

    // -------------------- ДОБАВЛЕНИЕ ПРОДУКТА --------------------
    async function addProduct() {
        if (!newProduct.productId || !newProduct.quantity || !newProduct.unitPrice) {
            alert("Выберите продукт, укажите количество и цену закупки!");
            return;
        }

        if (!selectedWarehouseId) {
            alert("Выберите склад!");
            return;
        }

        try {
            // Создаем Map из products для быстрого поиска
            const productsMap = new Map();
            if (Array.isArray(products)) {
                products.forEach(product => {
                    const id = product.productId || product.productID || product.id;
                    if (id !== undefined) {
                        productsMap.set(String(id), product);
                    }
                });
            }

            const selectedProduct = productsMap.get(String(newProduct.productId));

            if (!selectedProduct) {
                console.log("Выбранный productId:", newProduct.productId);
                console.log("Все доступные продукты:", products);
                throw new Error(`Продукт с ID ${newProduct.productId} не найден`);
            }

            const productToAdd = {
                consignmentId: newProduct.consignmentId,
                productId: selectedProduct.productId || selectedProduct.productID,
                quantity: parseFloat(newProduct.quantity),
                GROSS: selectedProduct.waste ?? 0,
            };

            console.log("Отправляем на сервер:", productToAdd);

            const res = await fetch("http://localhost:8080/api/consProduct", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(productToAdd)
            });

            if (!res.ok) {
                const errorText = await res.text();
                throw new Error(`Ошибка добавления товара: ${errorText}`);
            }

            const created = await res.json();
            console.log("Создан consProduct:", created);

            const currentNote = notes.find(n => String(n.consignmentId) === String(newProduct.consignmentId));
            const noteDate = currentNote?.date ? String(currentNote.date).slice(0, 10) : null;

            const movementPayload = {
                docType: "receipt",
                docDate: noteDate ? `${noteDate}T00:00:00` : undefined,
                fromWarehouseId: null,
                toWarehouseId: Number(selectedWarehouseId),
                productId: selectedProduct.productId || selectedProduct.productID,
                quantity: parseFloat(newProduct.quantity),
                supplierId: selectedSupplierId ? Number(selectedSupplierId) : null,
                unitPrice: parseFloat(newProduct.unitPrice),
                comment: `${CONSIGNMENT_MOVEMENT_PREFIX}${newProduct.consignmentId}`,
                createdBy: "consignment-ui"
            };

            const movementRes = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(movementPayload)
            });

            if (!movementRes.ok) {
                await fetch(`http://localhost:8080/api/consProduct/${created.consProductId}`, {
                    method: "DELETE"
                }).catch(() => {});
                const errText = await movementRes.text();
                throw new Error(`Ошибка создания движения: ${errText || movementRes.status}`);
            }

            // Добавляем товар в список
            const enteredUnitPrice = parseFloat(newProduct.unitPrice);
            const enteredQuantity = parseFloat(newProduct.quantity);
            const newConsProduct = {
                ...created,
                productName: selectedProduct.productName,
                productPrice: Number.isFinite(enteredUnitPrice) ? enteredUnitPrice : 0
            };

            setConsProducts(prev => [...prev, newConsProduct]);
            setNewProduct({ consignmentId: newProduct.consignmentId, productId: "", quantity: "", unitPrice: "" });

            if (Number.isFinite(enteredUnitPrice) && Number.isFinite(enteredQuantity)) {
                const lineSum = enteredUnitPrice * enteredQuantity;
                setCurrentTotal(prev => prev + lineSum);
                setTotalsByNoteId(prev => ({
                    ...prev,
                    [Number(newProduct.consignmentId)]: (prev[Number(newProduct.consignmentId)] ?? 0) + lineSum
                }));
            }

        } catch (err) {
            console.error(err);
            setError(err.message);
            alert(err.message);
        }
    }

    // -------------------- УДАЛЕНИЕ ПРОДУКТА --------------------
    // Используем productId как идентификатор для DELETE /api/consProduct/{productId}
    async function deleteProduct(productId) {
        if (!productId) return;

        try {
            const idToDelete = Number(productId);
            if (!idToDelete) {
                console.error("Некорректный productId для удаления:", productId);
                return;
            }

            const res = await fetch(`http://localhost:8080/api/consProduct/${idToDelete}`, {
                method: "DELETE"
            });

            if (!res.ok) throw new Error("Ошибка удаления товара");

            // Удаляем из списка по productId
            setConsProducts(prev => prev.filter(p => p.productId !== idToDelete));
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    }

    // -------------------- РАСЧЕТ ИТОГО --------------------
    async function calculateTotalForNote(noteId) {
        const note = notes.find(n => n.consignmentId === noteId);
        if (!note) return;

        try {
            const resCons = await fetch(`http://localhost:8080/api/consProduct/${noteId}`);
            const consProductsData = await resCons.json();
            const consProductsArray = Array.isArray(consProductsData) ? consProductsData : [];

            let total = 0;
            try {
                const movementData = await getNoteReceiptMovements(noteId);
                total = movementData.total;
            } catch (e) {
                console.warn("Не удалось посчитать итого по движениям, fallback на справочник цен:", e);
            }

            if (!total && consProductsArray.length > 0) {
                for (const cp of consProductsArray) {
                    const productId = cp.productId || cp.productID;
                    const quantity = Number(cp.quantity) || 0;
                    if (!productId) continue;

                    try {
                        const resProd = await fetch(`http://localhost:8080/api/product/${productId}`);
                        if (!resProd.ok) continue;
                        const productData = await resProd.json();
                        const price = Number(productData.productPrice || productData.price) || 0;
                        total += price * quantity;
                    } catch (err) {
                        console.warn(`Не удалось получить цену для товара ${productId}:`, err);
                    }
                }
            }

            setTotalsByNoteId(prev => ({ ...prev, [noteId]: total }));
            if (selectedNoteId === noteId) setCurrentTotal(total);

            await fetch(`http://localhost:8080/api/consignmentNote/${noteId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: total })
            });
        } catch (err) {
            console.error("Ошибка при расчете итого:", err);
            setError(err.message);
        }
    }

    async function calculateAllTotals() {
        if (!Array.isArray(notes) || notes.length === 0) return;
        try {
            setIsCalculatingAll(true);
            await Promise.all(notes.map(note => calculateTotalForNote(note.consignmentId)));
        } finally {
            setIsCalculatingAll(false);
        }
    }

    async function handleDeleteNote(noteId) {
        if (!window.confirm(`Удалить накладную #${noteId}?`)) return;
        try {
            const res = await fetch(`http://localhost:8080/api/consignmentNote/${noteId}`, {
                method: "DELETE"
            });
            if (!res.ok) throw new Error("Ошибка удаления накладной");

            setNotes(prev => prev.filter(n => n.consignmentId !== noteId));
            setTotalsByNoteId(prev => {
                const next = { ...prev };
                delete next[noteId];
                return next;
            });

            if (selectedNoteId === noteId) closeModal();
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    }

    // -------------------- ПЕЧАТНАЯ ФОРМА --------------------
    const handlePrintForm = (noteId) => {
        navigate(`/consignment-notes/print/${noteId}`);
    };

    if (loading) return <div className={styles.emptyState}>Загрузка...</div>;
    if (error) return <div className={styles.emptyState}>{error}</div>;

    return (
        <div className={styles.container}>
            <h1 className={styles.title}>Накладные</h1>

            <section className={styles.addConsignmentForm}>
                <h2>Добавить новую накладную</h2>
                <form onSubmit={handleSubmit}>
                    <label>
                        Поставщик:
                        <select
                            value={formData.supplierId}
                            onChange={e => setFormData({ ...formData, supplierId: e.target.value })}
                            required
                            className={styles.inputField}
                        >
                            <option value="">Выберите поставщика</option>
                            {Array.isArray(suppliers) && suppliers.map(s => (
                                <option key={s.supplierID || s.supplierId || s.id}
                                        value={s.supplierID || s.supplierId || s.id}>
                                    {s.supplierName}
                                </option>
                            ))}
                        </select>
                    </label>

                    <input
                        type="date"
                        className={styles.inputField}
                        value={formData.date}
                        onChange={e => setFormData({ ...formData, date: e.target.value })}
                        required
                    />

                    <button type="submit" className={styles.submitBtn}>Создать</button>
                </form>
            </section>

            {/* Таблица накладных */}
            <div className={styles.tableContainer}>
                <table className={styles.consignmentTable}>
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>Поставщик</th>
                        <th>Номер</th>
                        <th>Дата</th>
                        <th>Итого</th>
                        <th>
                            Действия
                            <button
                                type="button"
                                className={styles.calculateBtn}
                                onClick={calculateAllTotals}
                                disabled={isCalculatingAll}
                                style={{ marginLeft: "8px" }}
                            >
                                {isCalculatingAll ? "Считаю..." : "Рассчитать все"}
                            </button>
                        </th>
                    </tr>
                    </thead>
                    <tbody>
                    {Array.isArray(notes) && notes.map(note => (
                        <tr key={note.consignmentId}>
                            <td>{note.consignmentId}</td>
                            <td>
                                {Array.isArray(suppliers)
                                    ? suppliers.find(s => (s.supplierID || s.supplierId || s.id) == note.supplierId)?.supplierName || ''
                                    : ''
                                }
                            </td>
                            <td>{note.consignmentId}</td>
                            <td>{note.date}</td>
                            <td>{totalsByNoteId[note.consignmentId] ?? "–"}</td>
                            <td>
                                <div className={styles.actionButtons}>
                                    <button className={styles.openBtn} onClick={() => openProducts(note.consignmentId)}>
                                        Товары
                                    </button>
                                    <button
                                        className={styles.calculateBtn}
                                        onClick={() => calculateTotalForNote(note.consignmentId)}
                                    >
                                        Рассчитать Итого
                                    </button>
                                    <button
                                        className={styles.printBtn}
                                        onClick={() => handlePrintForm(note.consignmentId)}
                                    >
                                        Печатная форма
                                    </button>
                                    <button
                                        className={styles.deleteBtn}
                                        onClick={() => handleDeleteNote(note.consignmentId)}
                                    >
                                        Удалить
                                    </button>
                                </div>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {/* Модалка товаров */}
            {selectedNoteId && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <div className={styles.modalHeader}>
                            <h2>Товары накладной #{selectedNoteId}</h2>
                            <button className={styles.closeModalBtn} onClick={closeModal}>×</button>
                        </div>

                        <div className={styles.modalContent}>
                            {consProducts.length === 0 ? (
                                <div className={styles.emptyState}>
                                    <p>Товаров в накладной нет</p>
                                </div>
                            ) : (
                                <table className={styles.consignmentTable}>
                                    <thead>
                                    <tr>
                                        <th>Продукт</th>
                                        <th>Кол-во</th>
                                        <th>Цена</th>
                                        <th>Сумма</th>
                                        <th></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {consProducts.map(p => {
                                        const price = p.productPrice || 0;
                                        const quantity = p.quantity || 0;
                                        const sum = price * quantity;

                                        return (
                                            <tr key={p.consProductId || `${p.productId}-${Math.random()}`}>
                                                <td>{p.productName || "Неизвестный продукт"}</td>
                                                <td>{quantity}</td>
                                                <td>{price.toFixed(2)}</td>
                                                <td>{sum.toFixed(2)}</td>
                                                <td>
                                                    <button
                                                        className={styles.deleteSmallBtn}
                                                        onClick={() => deleteProduct(p.productId)}
                                                    >
                                                        ✖
                                                    </button>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    </tbody>
                                </table>
                            )}

                            <div className={styles.addProductSection}>
                                <h3>Добавить товар</h3>
                                <div className={styles.addProductForm}>
                                    <select
                                        value={selectedWarehouseId}
                                        onChange={e => setSelectedWarehouseId(e.target.value)}
                                        className={styles.inputField}
                                        required
                                    >
                                        <option value="">Выберите склад</option>
                                        {Array.isArray(warehouses) && warehouses.map(w => (
                                            <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>
                                        ))}
                                    </select>

                                    <select
                                        value={newProduct.productId}
                                        onChange={e => {
                                            const productId = e.target.value;
                                            const selected = products.find(p =>
                                                String(p.productId || p.productID) === String(productId)
                                            );
                                            setNewProduct({
                                                ...newProduct,
                                                productId,
                                                unitPrice: selected?.productPrice != null ? String(selected.productPrice) : newProduct.unitPrice
                                            });
                                        }}
                                        className={styles.inputField}
                                    >
                                        <option value="">Выберите товар</option>
                                        {Array.isArray(products) && products.map(p => (
                                            <option key={p.productId || p.productID}
                                                    value={p.productId || p.productID}>
                                                {p.productName} — Цена: {p.productPrice} — Остаток: {p.waste}
                                            </option>
                                        ))}
                                    </select>

                                    <input
                                        type="number"
                                        placeholder="Количество"
                                        className={styles.inputField}
                                        value={newProduct.quantity}
                                        onChange={e => setNewProduct({ ...newProduct, quantity: e.target.value })}
                                        step="0.01"
                                        min="0"
                                    />

                                    <input
                                        type="number"
                                        placeholder="Цена закупки"
                                        className={styles.inputField}
                                        value={newProduct.unitPrice}
                                        onChange={e => setNewProduct({ ...newProduct, unitPrice: e.target.value })}
                                        step="0.01"
                                        min="0"
                                    />

                                    <button className={styles.addBtn} onClick={addProduct}>Добавить</button>
                                </div>
                            </div>

                            <div className={styles.totalSection}>
                                <div className={styles.totalInfo}>
                                    <strong>Итого: {currentTotal.toFixed(2)}</strong>
                                </div>

                                <div className={styles.modalActions}>
                                    <button
                                        className={styles.printBtn}
                                        onClick={() => handlePrintForm(selectedNoteId)}
                                    >
                                        📄 Печатная форма
                                    </button>
                                    <button className={styles.closeBtn} onClick={closeModal}>
                                        Закрыть
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
