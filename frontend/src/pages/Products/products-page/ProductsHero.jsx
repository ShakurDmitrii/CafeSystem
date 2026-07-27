import styles from "../ProductsPage.module.css";

export default function ProductsHero({
    productCount,
    favoriteCount,
    supplierCount
}) {
    return (
        <section className={styles.hero} aria-labelledby="products-title">
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Картотека кухни</p>
                <h1 id="products-title" className={styles.title}>
                    Продукты и единицы без путаницы
                </h1>
                <p className={styles.subtitle}>
                    Храните закупочную цену, отход и пересчёт в базовую единицу,
                    которую используют склад и технологические карты.
                </p>
                <dl className={styles.heroStats}>
                    <div>
                        <dt>Продуктов</dt>
                        <dd>{productCount}</dd>
                    </div>
                    <div>
                        <dt>Избранных</dt>
                        <dd>{favoriteCount}</dd>
                    </div>
                    <div>
                        <dt>Поставщиков</dt>
                        <dd>{supplierCount}</dd>
                    </div>
                </dl>
            </div>

            <div className={styles.unitBoard} aria-label="Пример пересчёта единиц">
                <span className={styles.unitBoardLabel}>Правило учёта</span>
                <div className={styles.unitEquation}>
                    <span>
                        <small>Закупка</small>
                        <strong>1 kg</strong>
                    </span>
                    <span className={styles.unitArrow} aria-hidden="true">→</span>
                    <span>
                        <small>Склад</small>
                        <strong>1 000 g</strong>
                    </span>
                </div>
                <p>
                    Цена закупки делится на коэффициент. Так себестоимость блюда
                    рассчитывается в граммах, миллилитрах или штуках.
                </p>
            </div>
        </section>
    );
}
