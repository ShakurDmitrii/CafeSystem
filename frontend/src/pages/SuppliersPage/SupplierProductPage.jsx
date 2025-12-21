import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import styles from './SuppliersProductPage.module.css';

export default function SupplierProductPage() {
    const { id } = useParams();
    console.log("Получен ID поставщика из URL:", id);

    const [products, setProducts] = useState([]);
    const [favorites, setFavorites] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [newProduct, setNewProduct] = useState({
        productName: '',
        productPrice: '',
        waste: '',
        isFavorite: false
    });

    useEffect(() => {
        async function fetchData() {
            console.log("Начало загрузки данных для supplierId:", id);
            if (!id) {
                console.warn("ID поставщика не определен!");
                return;
            }

            try {
                // ЗАМЕНИТЕ ЭТУ СТРОКУ для правильного пути
                const productsUrl = `http://localhost:8080/api/product/supplier/${id}`;
                const favoritesUrl = `http://localhost:8080/api/product/favorite/${id}`;

                console.log("Запрос продуктов по URL:", productsUrl);
                console.log("Запрос избранных по URL:", favoritesUrl);

                const productsRes = await fetch(productsUrl);
                const favRes = await fetch(favoritesUrl);

                console.log("Статус ответа продуктов:", productsRes.status, productsRes.statusText);
                console.log("Статус ответа избранных:", favRes.status, favRes.statusText);

                if (!productsRes.ok) {
                    console.error("Ошибка загрузки продуктов. Статус:", productsRes.status);
                    throw new Error(`Ошибка загрузки продуктов: ${productsRes.status}`);
                }
                if (!favRes.ok) {
                    console.error("Ошибка загрузки избранных. Статус:", favRes.status);
                    throw new Error(`Ошибка загрузки избранных: ${favRes.status}`);
                }

                const productsData = await productsRes.json().catch((err) => {
                    console.error("Ошибка парсинга JSON продуктов:", err);
                    return [];
                });

                const favoritesData = await favRes.json().catch((err) => {
                    console.error("Ошибка парсинга JSON избранных:", err);
                    return [];
                });

                console.log("Полученные продукты:", productsData);
                console.log("Полученные избранные:", favoritesData);
                console.log("Тип productsData:", typeof productsData);
                console.log("productsData is array?", Array.isArray(productsData));

                setProducts(Array.isArray(productsData) ? productsData : []);
                setFavorites(Array.isArray(favoritesData) ? favoritesData : []);

                console.log("Установлено продуктов:", Array.isArray(productsData) ? productsData.length : 0);
                console.log("Установлено избранных:", Array.isArray(favoritesData) ? favoritesData.length : 0);

            } catch (err) {
                console.error("Ошибка в fetchData:", err);
                setError(err.message);
            } finally {
                console.log("Завершение загрузки данных");
                setLoading(false);
            }
        }

        fetchData();
    }, [id]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        console.log("Отправка формы, supplierId:", id);

        const productToAdd = {
            ...newProduct,
            supplierId: Number(id)
        };

        console.log("Данные для отправки:", productToAdd);

        try {
            const url = `http://localhost:8080/api/product`;
            console.log("POST запрос по URL:", url);

            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(productToAdd)
            });

            console.log("Статус ответа POST:", res.status, res.statusText);

            if (!res.ok) {
                const errorText = await res.text();
                console.error("Ошибка сервера при добавлении:", errorText);
                throw new Error(`Ошибка добавления продукта: ${res.status} - ${errorText}`);
            }

            const addedProduct = await res.json();
            console.log("Успешно добавлен товар:", addedProduct);

            setProducts(prev => {
                const newProducts = [...prev, addedProduct];
                console.log("Обновленный список продуктов:", newProducts);
                return newProducts;
            });

            setNewProduct({ productName: '', productPrice: '', waste: '', isFavorite: false });
            console.log("Форма очищена");

        } catch (err) {
            console.error("Ошибка при добавлении товара:", err);
            setError(err.message);
        }
    };

    // Добавим логи при рендере
    console.log("Рендер компонента:");
    console.log("loading:", loading);
    console.log("error:", error);
    console.log("products количество:", products.length);
    console.log("favorites количество:", favorites.length);

    if (loading) {
        console.log("Показываем спиннер загрузки");
        return <div className="loading-container"><div className="spinner"></div>Загрузка...</div>;
    }

    if (error) {
        console.log("Показываем ошибку:", error);
        return <div className="error-alert">{error}</div>;
    }

    return (
        <div className={styles.container}>
            {/* Форма добавления товара */}
            <section className={styles.addProductForm}>
                <h2>Добавить новый товар</h2>
                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        placeholder="Название товара"
                        className={styles.inputField}
                        value={newProduct.productName}
                        onChange={e => setNewProduct({ ...newProduct, productName: e.target.value })}
                        required
                    />
                    <input
                        type="number"
                        step="0.01"
                        placeholder="Цена"
                        className={styles.inputField}
                        value={newProduct.productPrice}
                        onChange={e => setNewProduct({ ...newProduct, productPrice: e.target.value })}
                        required
                    />
                    <input
                        type="number"
                        step="0.01"
                        placeholder="Waste"
                        className={styles.inputField}
                        value={newProduct.waste}
                        onChange={e => setNewProduct({ ...newProduct, waste: e.target.value })}
                        required
                    />
                    <label className={styles.checkboxLabel}>
                        <input
                            type="checkbox"
                            className={styles.checkboxInput}
                            checked={newProduct.isFavorite}
                            onChange={e => setNewProduct({ ...newProduct, isFavorite: e.target.checked })}
                        />
                        Избранное
                    </label>
                    <button type="submit" className={styles.submitBtn}>Добавить</button>
                </form>
            </section>

            <h1 className={styles.title}>Товары поставщика #{id}</h1>

            {/* Все товары */}
            <section>
                <h2 className={styles.sectionTitle}>Все товары</h2>
                {products.length > 0 ? (
                    <div className={`${styles.productsGrid} ${styles.gridCols3}`}>
                        {products.map((p, idx) => (
                            <div key={p.id ?? idx} className={styles.productCard}>
                                <h3>{p.productName}</h3>
                                <p>Цена: {p.productPrice}</p>
                                <p>Waste: {p.waste}</p>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className={styles.emptyState}>
                        Нет товаров
                        <div style={{ fontSize: '12px', marginTop: '10px' }}>
                            SupplierId: {id}, URL: /api/product/supplier/{id}
                        </div>
                    </div>
                )}
            </section>

            {/* Избранные товары */}
            <section>
                <h2 className={styles.sectionTitle}>Избранные товары</h2>
                {favorites.length > 0 ? (
                    <div className={`${styles.productsGrid} ${styles.gridCols3}`}>
                        {favorites.map((f, idx) => (
                            <div key={f.id ?? idx} className={styles.favoriteCard}>
                                <h3>{f.productName}</h3>
                                <p>Цена: {f.productPrice}</p>
                                <p>Waste: {f.waste}</p>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className={styles.emptyState}>Нет избранных товаров</div>
                )}
            </section>
        </div>
    );
}