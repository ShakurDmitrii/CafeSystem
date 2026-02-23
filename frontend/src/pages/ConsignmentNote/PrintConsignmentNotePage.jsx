
import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import styles from './PrintConsignmentNotePage.module.css';

export default function PrintConsignmentNotePage() {
    const { id } = useParams(); // Получаем ID накладной из URL
    const navigate = useNavigate();

    const [note, setNote] = useState(null);
    const [supplier, setSupplier] = useState(null);
    const [products, setProducts] = useState([]);
    const [allProducts, setAllProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [newProduct, setNewProduct] = useState({ productId: '', quantity: '' });
    const [totalAmount, setTotalAmount] = useState(0);

    // Загрузка данных накладной
    useEffect(() => {
        async function fetchNoteData() {
            try {
                setLoading(true);

                // Загружаем данные накладной
                const resNote = await fetch(`http://localhost:8080/api/consignmentNote/${id}`);
                const noteData = await resNote.json();
                setNote(noteData);

                // Загружаем данные поставщика
                const resSupplier = await fetch(`http://localhost:8080/api/supplier/${noteData.supplierId}`);
                const supplierData = await resSupplier.json();
                setSupplier(supplierData);

                // Загружаем товары накладной
                const resConsProducts = await fetch(`http://localhost:8080/api/consProduct/${id}`);
                const consProductsData = await resConsProducts.json();
                const consProductsArray = Array.isArray(consProductsData) ? consProductsData : [];

                // Загружаем все товары поставщика для выпадающего списка
                const resAllProducts = await fetch(`http://localhost:8080/api/product/supplier/${noteData.supplierId}`);
                const allProductsData = await resAllProducts.json();

                // Нормализуем ответ к массиву
                const productsArray = Array.isArray(allProductsData)
                    ? allProductsData
                    : (allProductsData ? [allProductsData] : []);
                setAllProducts(productsArray);

                // Сопоставляем товары накладной с их названиями.
                // Для идентификации строки используем productId (он же пойдёт в DELETE /api/consProduct/{productId}).
                const productsWithNames = consProductsArray.map(cp => {
                    const cpProductId = Number(cp.productId ?? cp.productID);
                    const product = productsArray.find(
                        p => Number(p.productId ?? p.productID) === cpProductId
                    );

                    return {
                        ...cp,
                        productName: product ? product.productName : 'Неизвестный продукт',
                        productPrice: Number(product?.productPrice ?? product?.price ?? 0)
                    };
                });

                setProducts(productsWithNames);

                // Рассчитываем общую сумму
                const total = productsWithNames.reduce((sum, p) =>
                    sum + (p.quantity * p.productPrice), 0
                );
                setTotalAmount(total);

            } catch (err) {
                console.error(err);
                setError('Ошибка загрузки данных накладной');
            } finally {
                setLoading(false);
            }
        }

        if (id) {
            fetchNoteData();
        }
    }, [id]);

    // Добавление товара
    const handleAddProduct = async () => {
        if (!newProduct.productId || !newProduct.quantity) {
            alert('Выберите товар и укажите количество');
            return;
        }

        try {
            const productToAdd = {
                consignmentId: parseInt(id),
                productId: parseInt(newProduct.productId),
                quantity: parseFloat(newProduct.quantity)
            };

            const response = await fetch('http://localhost:8080/api/consProduct', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(productToAdd)
            });

            if (!response.ok) throw new Error('Ошибка добавления товара');

            const addedProduct = await response.json();

            // Находим название добавленного товара
            const productInfo = allProducts.find(
                p => Number(p.productId ?? p.productID) === parseInt(newProduct.productId)
            );

            // Обновляем список товаров
            const unitPrice = Number(productInfo?.productPrice ?? 0);
            setProducts(prev => [...prev, {
                ...addedProduct,
                productName: productInfo ? productInfo.productName : 'Неизвестный продукт',
                productPrice: unitPrice
            }]);

            // Обновляем общую сумму
            const newTotal = totalAmount + (unitPrice * parseFloat(newProduct.quantity));
            setTotalAmount(newTotal);

            // Обновляем amount в накладной
            await fetch(`http://localhost:8080/api/consignmentNote/${id}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: newTotal })
            });

            // Сбрасываем форму
            setNewProduct({ productId: '', quantity: '' });

        } catch (err) {
            console.error(err);
            alert('Ошибка при добавлении товара');
        }
    };

    // Сохранение
    const handlePrint = async () => {
        try {
            const response = await fetch(`http://localhost:8080/api/consignmentNote/print/${id}`, {
                method: 'POST'
            });

            if (!response.ok) throw new Error('Ошибка при отправке на печать');

            alert('Накладная отправлена на печать!');
        } catch (err) {
            console.error(err);
            alert('Ошибка при печати накладной');
        }
    };

    // Удаление товара
    // Здесь в качестве идентификатора используется productId, т.к. бэкенд ожидает /api/consProduct/{productId}
    const handleDeleteProduct = async (productId, productPrice, quantity) => {
        if (!window.confirm('Удалить этот товар из накладной?')) return;

        try {
            const idToDelete = Number(productId);
            if (!idToDelete) {
                console.error('Некорректный productId для удаления:', productId);
                alert('Не удалось определить ID товара для удаления');
                return;
            }

            const response = await fetch(`http://localhost:8080/api/consProduct/${idToDelete}`, {
                method: 'DELETE'
            });

            if (!response.ok) throw new Error('Ошибка удаления товара');

            // Удаляем из списка (по productId)
            setProducts(prev => prev.filter(p => p.productId !== idToDelete));

            // Обновляем общую сумму
            const newTotal = totalAmount - (productPrice * quantity);
            setTotalAmount(newTotal);

            // Обновляем amount в накладной
            await fetch(`http://localhost:8080/api/consignmentNote/${id}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: newTotal })
            });

        } catch (err) {
            console.error(err);
            alert('Ошибка при удалении товара');
        }
    };

    // Удаление всей накладной
    const handleDeleteNote = async () => {
        if (!window.confirm('Удалить всю накладную?')) return;

        try {
            const response = await fetch(`http://localhost:8080/api/consignmentNote/${id}`, {
                method: 'DELETE'
            });

            if (!response.ok) throw new Error('Ошибка удаления накладной');

            navigate('/consignment-notes'); // Возвращаемся к списку накладных

        } catch (err) {
            console.error(err);
            alert('Ошибка при удалении накладной');
        }
    };

    if (loading) return <div className={styles.loading}>Загрузка накладной...</div>;
    if (error) return <div className={styles.error}>{error}</div>;
    if (!note) return <div className={styles.error}>Накладная не найдена</div>;

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <h1 className={styles.title}>Накладная #{note.consignmentId}</h1>
                <div className={styles.headerActions}>
                    <button onClick={() => navigate('/consigment')} className={styles.backBtn}>
                        ← Назад к списку
                    </button>
                    <button onClick={handleDeleteNote} className={styles.deleteBtn}>
                        Удалить накладную
                    </button>
                    <button onClick={handlePrint} className={styles.printBtn}>
                        🖨️ Печать накладной
                    </button>
                </div>
            </div>

            <div className={styles.noteInfo}>
                <div className={styles.infoCard}>
                    <h3>📋 Информация о накладной</h3>
                    <p><strong>Номер:</strong> {note.consignmentId}</p>
                    <p><strong>Дата:</strong> {note.date}</p>
                    <p><strong>Поставщик:</strong> {supplier?.supplierName || 'Неизвестен'}</p>
                    <p><strong>Общая сумма:</strong> <span className={styles.totalAmount}>{totalAmount.toFixed(2)} ₽</span></p>
                </div>

                <div className={styles.supplierInfo}>
                    <h3>🏢 Информация о поставщике</h3>
                    <p><strong>Название:</strong> {supplier?.supplierName || 'Неизвестно'}</p>
                    <p><strong>Контакт:</strong> {supplier?.communication || 'Не указан'}</p>
                </div>
            </div>

            <div className={styles.productsSection}>
                <h2>📦 Товары в накладной</h2>

                <div className={styles.productsTableContainer}>
                    <table className={styles.productsTable}>
                        <thead>
                        <tr>
                            <th>№</th>
                            <th>Название товара</th>
                            <th>Количество</th>
                            <th>Цена за единицу</th>
                            <th>Сумма</th>
                            <th>Действия</th>
                        </tr>
                        </thead>
                        <tbody>
                        {products.map((product, index) => (
                            <tr key={product.consProductId || product.productId || index}>
                                <td>{index + 1}</td>
                                <td>{product.productName}</td>
                                <td>{product.quantity}</td>
                                <td>{product.productPrice.toFixed(2)} ₽</td>
                                <td>{(product.quantity * product.productPrice).toFixed(2)} ₽</td>
                                <td>
                                    <button
                                        onClick={() => handleDeleteProduct(
                                            product.productId,
                                            product.productPrice,
                                            product.quantity
                                        )}
                                        className={styles.deleteProductBtn}
                                    >
                                        Удалить
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>

                    {products.length === 0 && (
                        <div className={styles.emptyProducts}>
                            Нет товаров в накладной. Добавьте первый товар ниже.
                        </div>
                    )}
                </div>

                <div className={styles.addProductForm}>
                    <h3>➕ Добавить товар</h3>
                    <div className={styles.formRow}>
                        <select
                            value={newProduct.productId}
                            onChange={e => setNewProduct({...newProduct, productId: e.target.value})}
                            className={styles.selectField}
                        >
                            <option value="">Выберите товар</option>
                            {allProducts.map(product => (
                                <option key={product.productId} value={product.productId}>
                                    {product.productName} — {product.productPrice} ₽
                                </option>
                            ))}
                        </select>

                        <input
                            type="number"
                            placeholder="Количество"
                            value={newProduct.quantity}
                            onChange={e => setNewProduct({...newProduct, quantity: e.target.value})}
                            className={styles.numberField}
                            min="0.01"
                            step="0.01"
                        />

                        <button onClick={handleAddProduct} className={styles.addBtn}>
                            Добавить
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
