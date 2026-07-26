import { Link } from "react-router-dom";
import styles from "../SuppliersPage.module.css";

const getContactLink = (communication) => {
    if (!communication) return null;
    if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(communication)) {
        return `mailto:${communication}`;
    }

    const digits = communication.replace(/\D/g, "");
    if (digits.length >= 6) return `tel:${communication}`;
    return null;
};

export default function SupplierCard({ supplier, onEdit, onDelete }) {
    const contactLink = getContactLink(supplier.communication);
    const initial = (supplier.name || "П").slice(0, 1).toLocaleUpperCase("ru");

    return (
        <article className={styles.supplierCard}>
            <header className={styles.supplierIdentity}>
                <span className={styles.supplierMark} aria-hidden="true">{initial}</span>
                <div className={styles.supplierMeta}>
                    <span className={styles.supplierNumber}>#{supplier.id}</span>
                    <h3>{supplier.name || `Поставщик #${supplier.id}`}</h3>
                </div>
            </header>

            <div className={styles.contactBlock}>
                <span>Контакт для заказа</span>
                {supplier.communication ? (
                    contactLink ? (
                        <a href={contactLink}>{supplier.communication}</a>
                    ) : (
                        <strong>{supplier.communication}</strong>
                    )
                ) : (
                    <strong className={styles.contactMissing}>Не указан</strong>
                )}
            </div>

            <div className={styles.cardActions}>
                <Link
                    className={styles.assortmentLink}
                    to={`/suppliers/${supplier.id}`}
                >
                    Открыть ассортимент
                </Link>
                <button
                    type="button"
                    className={styles.editButton}
                    onClick={() => onEdit(supplier)}
                >
                    Изменить
                </button>
                <button
                    type="button"
                    className={styles.deleteButton}
                    onClick={() => onDelete(supplier)}
                >
                    Удалить
                </button>
            </div>
        </article>
    );
}
