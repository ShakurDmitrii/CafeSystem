import { Link } from "react-router-dom";
import styles from "../SuppliersProductPage.module.css";

export default function SupplierAssortmentHero({
    supplier,
    productCount,
    favoriteCount
}) {
    return (
        <section className={styles.hero} aria-labelledby="supplier-products-title">
            <div className={styles.heroCopy}>
                <Link className={styles.backLink} to="/suppliers">
                    ← Все поставщики
                </Link>
                <p className={styles.eyebrow}>Ассортимент партнёра</p>
                <h1 id="supplier-products-title">{supplier.name}</h1>
                <p className={styles.heroDescription}>
                    Закупочные цены и единицы этого поставщика собраны в одном
                    прайс-листе. Здесь можно добавить позицию или уточнить её карточку.
                </p>
            </div>

            <aside className={styles.supplierPassport} aria-label="Карточка поставщика">
                <p className={styles.passportLabel}>Карточка партнёра</p>
                <strong className={styles.passportNumber}>#{supplier.id || "—"}</strong>
                <span className={styles.passportContact}>
                    {supplier.communication || "Контакт пока не указан"}
                </span>
                <dl className={styles.passportStats}>
                    <div>
                        <dt>Позиций</dt>
                        <dd>{productCount}</dd>
                    </div>
                    <div>
                        <dt>Избранных</dt>
                        <dd>{favoriteCount}</dd>
                    </div>
                </dl>
                <Link className={styles.allProductsLink} to="/products">
                    Открыть общий каталог →
                </Link>
            </aside>
        </section>
    );
}
