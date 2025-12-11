import { useEffect, useState } from "react";
import styles from './ConsignmentNotePage.module.css';

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
    const [formData, setFormData] = useState({ supplierId: '', noteNumber: '', date: '' });

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
        try {
            const res = await fetch("http://localhost:8080/api/consignmentNote", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(formData)
            });

            if (!res.ok) throw new Error("Ошибка создания накладной");

            const newNote = await res.json();
            setNotes(prev => [...prev, newNote]);
            setFormData({ supplierId: "", noteNumber: "", date: "" });
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

            // Инициализация формы добавления
            setNewProduct({ consignmentId: noteId, productId: "", quantity: "" });
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    }

    function closeModal() {
        setSelectedNoteId(null);
    }

    // -------------------- ДОБАВЛЕНИЕ ПРОДУКТА --------------------
    async function addProduct() {
        if (!newProduct.productId || !newProduct.quantity) return;

        try {
            const selectedProduct = products.find(p => p.productId === parseInt(newProduct.productId));
            if (!selectedProduct) throw new Error("Продукт не найден");

            const productToAdd = {
                consignmentId: newProduct.consignmentId,
                productId: selectedProduct.productId,
                quantity: parseFloat(newProduct.quantity),
                GROSS: selectedProduct.waste,
            };

            const res = await fetch("http://localhost:8080/api/consProduct", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(productToAdd)
            });

            if (!res.ok) throw new Error("Ошибка добавления товара");

            const created = await res.json();
            if (!created.consProductId) throw new Error("Сервер не вернул consProductId");

            setConsProducts(prev => [
                ...prev,
                {
                    ...created,
                    productName: selectedProduct.productName
                }
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

    if (loading) return <div className={styles.emptyState}>Загрузка...</div>;
    if (error) return <div className={styles.emptyState}>{error}</div>;

    return (
        <div className={styles.container}>
            <h1 className={styles.title}>Накладные</h1>

            {/* Форма создания накладной */}
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
                        type="text"
                        placeholder="Номер накладной"
                        className={styles.inputField}
                        value={formData.noteNumber}
                        onChange={e => setFormData({ ...formData, noteNumber: e.target.value })}
                        required
                    />

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
                        <th>Итог</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    {notes.map(note => {
                        // Рассчитываем итог только для открытой накладной
                        const total = consProducts
                            .filter(p => p.consignmentId === note.consignmentId)
                            .reduce((sum, cp) => {
                                const product = products.find(pr => pr.productId === cp.productId);
                                return sum + (product ? product.price * cp.quantity : 0);
                            }, 0);

                        return (
                            <tr key={note.consignmentId}>
                                <td>{note.consignmentId}</td>
                                <td>{suppliers.find(s => s.supplierID === note.supplierId)?.supplierName || ''}</td>
                                <td>{note.noteNumber ?? ''}</td>
                                <td>{note.date}</td>
                                <td>{total.toFixed(2)}</td>
                                <td>
                                    <button className={styles.openBtn} onClick={() => openProducts(note.consignmentId)}>
                                        Товары
                                    </button>
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            </div>

            {/* Модалка товаров */}
            {selectedNoteId && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <h2>Товары накладной #{selectedNoteId}</h2>

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
                                const product = products.find(pr => pr.productId === p.productId);
                                const price = product ? product.price : 0;
                                const sum = price * p.quantity;

                                return (
                                    <tr key={p.consProductId}>
                                        <td>{p.productName}</td>
                                        <td>{p.quantity}</td>
                                        <td>{price.toFixed(2)}</td>
                                        <td>{sum.toFixed(2)}</td>
                                        <td>
                                            <button onClick={() => deleteProduct(p.consProductId)}>✖</button>
                                        </td>
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>

                        {/* Итог по накладной */}
                        <h3>
                            Итого: {consProducts.reduce((sum, cp) => {
                            const product = products.find(pr => pr.productId === cp.productId);
                            return sum + (product ? product.price * cp.quantity : 0);
                        }, 0).toFixed(2)}
                        </h3>

                        {/* Форма добавления товара */}
                        <h3>Добавить товар</h3>
                        <select
                            value={newProduct.productId}
                            onChange={e => setNewProduct({ ...newProduct, productId: e.target.value })}
                            className={styles.inputField}
                        >
                            <option value="">Выберите товар</option>
                            {products.map(p => (
                                <option key={p.productId} value={p.productId}>{p.productName}</option>
                            ))}
                        </select>

                        <input
                            type="number"
                            placeholder="Количество"
                            className={styles.inputField}
                            value={newProduct.quantity}
                            onChange={e => setNewProduct({ ...newProduct, quantity: e.target.value })}
                        />

                        <button className={styles.submitBtn} onClick={addProduct}>Добавить</button>
                        <button className={styles.closeBtn} onClick={closeModal}>Закрыть</button>
                    </div>
                </div>
            )}
        </div>
    );
}
