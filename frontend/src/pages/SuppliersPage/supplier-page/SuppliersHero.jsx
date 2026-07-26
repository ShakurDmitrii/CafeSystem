import { Link } from "react-router-dom";
import styles from "../SuppliersPage.module.css";

export default function SuppliersHero({ total, withContacts, withoutContacts }) {
    return (
        <section className={styles.hero} aria-labelledby="suppliers-title">
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Книга закупок</p>
                <h1 id="suppliers-title">Поставки начинаются с контакта</h1>
                <p className={styles.heroDescription}>
                    Храните партнёров и способы связи рядом с их ассортиментом,
                    чтобы от заявки до накладной было меньше лишних шагов.
                </p>
                <dl className={styles.heroStats}>
                    <div>
                        <dt>Поставщиков</dt>
                        <dd>{total}</dd>
                    </div>
                    <div>
                        <dt>С контактом</dt>
                        <dd>{withContacts}</dd>
                    </div>
                    <div>
                        <dt>Нужно заполнить</dt>
                        <dd>{withoutContacts}</dd>
                    </div>
                </dl>
            </div>

            <div className={styles.flowBoard} aria-label="Путь закупки">
                <p className={styles.flowLabel}>Путь закупки</p>
                <div className={styles.flow}>
                    <div className={styles.flowStep}>
                        <span>Шаг 1</span>
                        <strong>Поставщик</strong>
                    </div>
                    <span className={styles.flowArrow} aria-hidden="true">→</span>
                    <Link className={styles.flowStep} to="/products">
                        <span>Шаг 2</span>
                        <strong>Ассортимент</strong>
                    </Link>
                    <span className={styles.flowArrow} aria-hidden="true">→</span>
                    <Link className={styles.flowStep} to="/consigment">
                        <span>Шаг 3</span>
                        <strong>Накладная</strong>
                    </Link>
                </div>
                <p className={styles.flowHint}>
                    Откройте карточку партнёра, чтобы работать только с его товарами.
                </p>
            </div>
        </section>
    );
}
