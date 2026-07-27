import ModalShell from "./ModalShell";
import styles from "../TechCardPage.module.css";

const unitOptions = [
    { value: "g", label: "Граммы (g)" },
    { value: "kg", label: "Килограммы (kg)" },
    { value: "ml", label: "Миллилитры (ml)" },
    { value: "l", label: "Литры (l)" },
    { value: "pcs", label: "Штуки (pcs)" }
];

export default function CreateIngredientModal({
    form,
    suppliers,
    error,
    loading,
    onChange,
    onSubmit,
    onClose
}) {
    return (
        <ModalShell
            titleId="create-ingredient-title"
            eyebrow="Новый продукт"
            title="Добавить в справочник"
            subtitle="После создания продукт автоматически выберется для новой строки техкарты."
            onClose={onClose}
            busy={loading}
        >
            <form onSubmit={onSubmit} className={styles.modalForm}>
                <div className={styles.modalFormGrid}>
                    <label className={`${styles.field} ${styles.fieldWide}`} htmlFor="new-product-name">
                        <span>Название продукта</span>
                        <input
                            id="new-product-name"
                            name="newProductName"
                            type="text"
                            value={form.productName}
                            onChange={(event) => onChange("productName", event.target.value)}
                            placeholder="Например, сливки 20%…"
                            autoComplete="off"
                            className={styles.input}
                            required
                        />
                    </label>

                    <label className={styles.field} htmlFor="new-product-price">
                        <span>Цена за единицу, ₽</span>
                        <input
                            id="new-product-price"
                            name="newProductPrice"
                            type="number"
                            inputMode="decimal"
                            value={form.productPrice}
                            onChange={(event) => onChange("productPrice", event.target.value)}
                            placeholder="Например, 0,42…"
                            min="0"
                            step="0.01"
                            autoComplete="off"
                            className={styles.input}
                            required
                        />
                    </label>

                    <label className={styles.field} htmlFor="new-product-waste">
                        <span>Отход по умолчанию, %</span>
                        <input
                            id="new-product-waste"
                            name="newProductWaste"
                            type="number"
                            inputMode="decimal"
                            value={form.waste}
                            onChange={(event) => onChange("waste", event.target.value)}
                            min="0"
                            max="100"
                            step="0.01"
                            autoComplete="off"
                            className={styles.input}
                        />
                    </label>

                    <label className={styles.field} htmlFor="new-product-unit">
                        <span>Единица закупки</span>
                        <select
                            id="new-product-unit"
                            name="newProductUnit"
                            value={form.unit}
                            onChange={(event) => onChange("unit", event.target.value)}
                            autoComplete="off"
                            className={styles.select}
                        >
                            {unitOptions.map((unit) => (
                                <option key={unit.value} value={unit.value}>{unit.label}</option>
                            ))}
                        </select>
                    </label>

                    <label className={styles.field} htmlFor="new-product-supplier">
                        <span>Поставщик</span>
                        <select
                            id="new-product-supplier"
                            name="newProductSupplier"
                            value={form.supplierId}
                            onChange={(event) => onChange("supplierId", event.target.value)}
                            autoComplete="off"
                            className={styles.select}
                        >
                            <option value="">Без поставщика</option>
                            {suppliers.map((supplier) => {
                                const supplierId = supplier.supplierId ?? supplier.supplierID ?? supplier.id;
                                const supplierName = supplier.supplierName ?? supplier.name ?? `Поставщик #${supplierId}`;
                                return <option key={supplierId} value={supplierId}>{supplierName}</option>;
                            })}
                        </select>
                    </label>

                    <div className={`${styles.field} ${styles.unitSummary}`}>
                        <span>Как сохранится цена</span>
                        <div className={styles.unitHint}>
                            1 {form.unit} = {form.unitFactor} {form.baseUnit}. Расчёт техкарты использует базовую единицу.
                        </div>
                    </div>
                </div>

                {error ? <div className={styles.errorText} role="alert">{error}</div> : null}

                <div className={styles.modalActions}>
                    <button type="button" onClick={onClose} className={styles.secondaryButton} disabled={loading}>
                        Отмена
                    </button>
                    <button type="submit" className={styles.primaryButton} disabled={loading}>
                        {loading ? "Создаём продукт…" : "Создать продукт"}
                    </button>
                </div>
            </form>
        </ModalShell>
    );
}
