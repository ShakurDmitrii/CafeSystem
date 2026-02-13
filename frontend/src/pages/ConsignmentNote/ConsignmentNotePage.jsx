import { useEffect, useState } from "react";
import styles from './ConsignmentNotePage.module.css';
import { useNavigate } from "react-router-dom";

export default function ConsignmentNotePage() {
    const [notes, setNotes] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [selectedNoteId, setSelectedNoteId] = useState(null);
    const [selectedSupplierId, setSelectedSupplierId] = useState(null);

    const [consProducts, setConsProducts] = useState([]);
    const [newProduct, setNewProduct] = useState({ consignmentId: "", productId: "", quantity: "" });
    const [formData, setFormData] = useState({ supplierId: '', date: '' });

    const [currentTotal, setCurrentTotal] = useState(0);
    const [totalsByNoteId, setTotalsByNoteId] = useState({});

    const [selectedWarehouseId, setSelectedWarehouseId] = useState("");
    const [warehouses, setWarehouses] = useState([]);

    const navigate = useNavigate();

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

            setConsProducts(consProductsWithNames);
            setCurrentTotal(totalsByNoteId[noteId] ?? 0);
            setNewProduct({ consignmentId: noteId, productId: "", quantity: "" });

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
        if (!newProduct.productId || !newProduct.quantity) {
            alert("Выберите продукт и укажите количество!");
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

            // Добавляем товар в список
            const newConsProduct = {
                ...created,
                productName: selectedProduct.productName,
                productPrice: selectedProduct.productPrice
            };

            setConsProducts(prev => [...prev, newConsProduct]);
            setNewProduct({ consignmentId: newProduct.consignmentId, productId: "", quantity: "" });

            // Добавляем товар на склад
            try {
                await fetch(`http://localhost:8080/warehouses/${selectedWarehouseId}/products`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify([{
                        productId: selectedProduct.productId || selectedProduct.productID
                    }])
                });
            } catch (warehouseErr) {
                console.warn("Ошибка при добавлении товара на склад:", warehouseErr);
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
    async function calculateTotal() {
        console.log("Начинаем расчёт итого.");
        console.log("consProducts для расчета:", consProducts);

        // Создаем Map из products для быстрого поиска цен
        const productsMap = new Map();
        if (Array.isArray(products)) {
            products.forEach(product => {
                const id = product.productId || product.productID || product.id;
                if (id !== undefined) {
                    productsMap.set(String(id), product);
                }
            });
        }

        let total = 0;

        for (const cp of consProducts) {
            console.log("Обрабатываем consProduct:", cp);

            const cpId = String(cp.productId || cp.productID || cp.id);
            const product = productsMap.get(cpId);
            const price = product ? product.productPrice : 0;
            const quantity = cp.quantity || 0;
            const sum = price * quantity;

            console.log(`Товар: ${cp.productName}, цена: ${price}, количество: ${quantity}, сумма: ${sum}`);

            total += sum;
        }

        console.log("Итого:", total);

        setCurrentTotal(total);
        setTotalsByNoteId(prev => ({ ...prev, [selectedNoteId]: total }));

        try {
            await fetch(`http://localhost:8080/api/consignmentNote/${selectedNoteId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: total })
            });
            console.log("Amount успешно обновлен на сервере");
        } catch (err) {
            console.error("Ошибка при обновлении amount:", err);
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
                        <th>Действия</th>
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
                                        className={styles.printBtn}
                                        onClick={() => handlePrintForm(note.consignmentId)}
                                    >
                                        Печатная форма
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
                                        onChange={e => setNewProduct({ ...newProduct, productId: e.target.value })}
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

                                    <button className={styles.addBtn} onClick={addProduct}>Добавить</button>
                                </div>
                            </div>

                            <div className={styles.totalSection}>
                                <div className={styles.totalInfo}>
                                    <strong>Итого: {currentTotal.toFixed(2)}</strong>
                                    <button className={styles.calculateBtn} onClick={calculateTotal}>
                                        Рассчитать Итого
                                    </button>
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