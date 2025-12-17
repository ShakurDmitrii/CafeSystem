import { useEffect, useState } from "react";
import styles from './ConsignmentNotePage.module.css';
import { useNavigate } from "react-router-dom"; // Добавляем

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

    const navigate = useNavigate(); // Добавляем useNavigate

    // --------------------Склады-------------------------------
    useEffect(() => {
        async function fetchWarehouses() {
            try {
                const res = await fetch("http://localhost:8080/warehouses");
                const data = await res.json();
                setWarehouses(data);
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
                setNotes(notesData);

                const resSup = await fetch("http://localhost:8080/api/supplier");
                const suppliersData = await resSup.json();
                setSuppliers(suppliersData);
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
            // Получаем товары накладной
            const resCons = await fetch(`http://localhost:8080/api/consProduct/${noteId}`);
            const consProductsData = await resCons.json();

            // Получаем товары поставщика
            const resProd = await fetch(`http://localhost:8080/api/product/${note.supplierId}`);
            const productsData = await resProd.json();
            setProducts(productsData);

            // Добавляем имя продукта к каждому товару накладной
            const consProductsWithNames = consProductsData.map(cp => {
                const product = productsData.find(p => p.productId === cp.productId);
                return {
                    ...cp,
                    productName: product ? product.productName : "Неизвестный продукт"
                };
            });

            setConsProducts(consProductsWithNames);
            setCurrentTotal(totalsByNoteId[noteId] ?? 0);

            // Инициализация формы добавления
            setNewProduct({ consignmentId: noteId, productId: "", quantity: "" });
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    }

    function closeModal() {
        setSelectedNoteId(null);
        setCurrentTotal(0);
    }

    // -------------------- ДОБАВЛЕНИЕ ПРОДУКТА --------------------
    async function addProduct() {
        if (!newProduct.productId || !newProduct.quantity) {
            alert("Выберите продукт и укажите количество!");
            return;
        }

        try {
            const selectedProduct = products.find(p => p.productId === parseInt(newProduct.productId));
            if (!selectedProduct) throw new Error("Продукт не найден");

            const productToAdd = {
                consignmentId: newProduct.consignmentId,
                productId: selectedProduct.productId,
                quantity: parseFloat(newProduct.quantity),
                GROSS: selectedProduct.waste ?? 0,
            };

            const res = await fetch("http://localhost:8080/api/consProduct", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(productToAdd)
            });

            if (!res.ok) throw new Error("Ошибка добавления товара");

            const created = await res.json();
            setConsProducts(prev => [
                ...prev,
                {
                    ...created,
                    productName: selectedProduct.productName
                }
            ]);

            setNewProduct({ consignmentId: newProduct.consignmentId, productId: "", quantity: "" });
            // Добавляем товар на склад
            await fetch(`http://localhost:8080/warehouses/${selectedWarehouseId}/products`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify([{ productId: selectedProduct.productId }])
            });

            setConsProducts(prev => [
                ...prev,
                { ...created, productName: selectedProduct.productName }
            ]);

            setNewProduct({ consignmentId: newProduct.consignmentId, productId: "", quantity: "" });
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    }

    // -------------------- УДАЛЕНИЕ ПРОДУКТА --------------------
    async function deleteProduct(consProductId) {
        if (!consProductId) return;

        try {
            const res = await fetch(`http://localhost:8080/api/consProduct/${consProductId}`, {
                method: "DELETE"
            });

            if (!res.ok) throw new Error("Ошибка удаления товара");

            setConsProducts(prev => prev.filter(p => p.consProductId !== consProductId));
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    }

    // -------------------- РАСЧЕТ ИТОГО --------------------
    async function calculateTotal() {
        console.log("Начинаем расчёт итого. consProducts:", consProducts);

        const total = consProducts.reduce((sum, cp) => {
            const product = products.find(p => p.productId === cp.productId);
            const price = product?.productPrice ?? 0;
            const quantity = cp?.quantity ?? 0;
            console.log(`Товар: ${product?.productName}, цена: ${price}, количество: ${quantity}`);
            return sum + price * quantity;
        }, 0);

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
                            {suppliers.map(s => (
                                <option key={s.supplierID} value={s.supplierID}>{s.supplierName}</option>
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
                    {notes.map(note => (
                        <tr key={note.consignmentId}>
                            <td>{note.consignmentId}</td>
                            <td>{suppliers.find(s => s.supplierID === note.supplierId)?.supplierName || ''}</td>
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
                                    const product = products.find(prod => prod.productId === p.productId);
                                    const price = product?.productPrice ?? 0;
                                    const sum = p.quantity * price;

                                    return (
                                        <tr key={p.consProductId || `${p.productId}-${Math.random()}`}>
                                            <td>{p.productName}</td>
                                            <td>{p.quantity}</td>
                                            <td>{price.toFixed(2)}</td>
                                            <td>{sum.toFixed(2)}</td>
                                            <td>
                                                <button
                                                    className={styles.deleteSmallBtn}
                                                    onClick={() => deleteProduct(p.consProductId)}
                                                >
                                                    ✖
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>

                            <div className={styles.addProductSection}>
                                <h3>Добавить товар</h3>
                                <div className={styles.addProductForm}>
                                    <select
                                        value={selectedWarehouseId}
                                        onChange={e => setSelectedWarehouseId(e.target.value)}
                                        className={styles.inputField}
                                    >
                                        <option value="">Выберите склад</option>
                                        {warehouses.map(w => (
                                            <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>
                                        ))}
                                    </select>

                                    <select
                                        value={newProduct.productId}
                                        onChange={e => setNewProduct({ ...newProduct, productId: e.target.value })}
                                        className={styles.inputField}
                                    >
                                        <option value="">Выберите товар</option>
                                        {products.map(p => (
                                            <option key={p.productId} value={p.productId}>
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