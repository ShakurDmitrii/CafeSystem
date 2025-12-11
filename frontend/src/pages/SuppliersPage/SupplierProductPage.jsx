import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import styles from './SuppliersProductPage.module.css';

export default function SupplierProductPage() {
    const { id } = useParams();

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
            if (!id) return;

            try {
                const productsRes = await fetch(`http://localhost:8080/api/product/${id}`);
                const favRes = await fetch(`http://localhost:8080/api/product/favorite/${id}`);

                if (!productsRes.ok) throw new Error(`Ошибка загрузки продуктов: ${productsRes.status}`);
                if (!favRes.ok) throw new Error(`Ошибка загрузки избранных: ${favRes.status}`);

                const productsData = await productsRes.json().catch(() => []);
                const favoritesData = await favRes.json().catch(() => []);

                setProducts(Array.isArray(productsData) ? productsData : []);
                setFavorites(Array.isArray(favoritesData) ? favoritesData : []);
            } catch (err) {
                console.error(err);
                setError(err.message);
            } finally {
                setLoading(false);
            }
        }

        fetchData();
    }, [id]);

    const handleSubmit = async (e) => {
        e.preventDefault();

        const productToAdd = {
            ...newProduct,
            supplierId: Number(id)
        };

        try {
            const res = await fetch(`http://localhost:8080/api/product`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(productToAdd)
            });

            if (!res.ok) throw new Error('Ошибка добавления продукта');

            const addedProduct = await res.json();
            setProducts(prev => [...prev, addedProduct]);
            setNewProduct({ productName: '', productPrice: '', waste: '', isFavorite: false });
        } catch (err) {
            console.error(err);
            setError(err.message);
        }
    };

    if (loading) return <div className="loading-container"><div className="spinner"></div>Загрузка...</div>;
    if (error) return <div className="error-alert">{error}</div>;


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
                    <div className={styles.emptyState}>Нет товаров</div>
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