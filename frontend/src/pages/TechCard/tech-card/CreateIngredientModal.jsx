import ModalShell from "./ModalShell";
import styles from "../TechCardPage.module.css";
import { unitLabel } from "../../../utils/units";
import UnitChips from "../../../components/forms/UnitChips";
import { findProductByName } from "../../../components/forms/units";

export default function CreateIngredientModal({
    form,
    suppliers,
    products = [],
    error,
    loading,
    onChange,
    onSubmit,
    onPickExisting,
    onClose
}) {
    const duplicate = findProductByName(products, form.productName);
    const basePrice = Number(form.productPrice) / Number(form.unitFactor || 1);

    return (
        <ModalShell
            titleId="create-ingredient-title"
            eyebrow="Новый продукт"
            title="Добавить в справочник"
            subtitle="Достаточно названия, единицы и цены. После создания продукт сразу выберется для строки техкарты."
            onClose={onClose}
            busy={loading}
        >
            <form onSubmit={onSubmit} className={styles.modalForm}>
                <div className={styles.modalFormGrid}>
                    <label className={`${styles.field} ${styles.fieldWide}`} htmlFor="new-product-name">
                        <span>Название</span>
                        <input
                            id="new-product-name"
                            name="newProductName"
                            type="text"
                            value={form.productName}
                            onChange={(event) => onChange("productName", event.target.value)}
                            placeholder="Например, сливки 20%…"
                            autoComplete="off"
                            className={styles.input}
                            autoFocus
                            required
                        />
                    </label>
                    {duplicate ? (
                        <div className={`${styles.fieldWide} ${styles.duplicateNotice}`} role="status">
                            <span>«{duplicate.productName}» уже есть в справочнике.</span>
                            <button type="button" onClick={() => onPickExisting(duplicate)}>
                                Выбрать его
                            </button>
                        </div>
                    ) : null}

                    <div className={styles.fieldWide}>
                        <UnitChips value={form.unit} onChange={(value) => onChange("unit", value)} name="newProductUnit" />
                    </div>

                    <label className={styles.field} htmlFor="new-product-price">
                        <span>Цена за 1 {unitLabel(form.unit)}, ₽</span>
                        <input
                            id="new-product-price"
                            name="newProductPrice"
                            type="number"
                            inputMode="decimal"
                            value={form.productPrice}
                            onChange={(event) => onChange("productPrice", event.target.value)}
                            placeholder="Например, 450"
                            min="0"
                            step="0.01"
                            autoComplete="off"
                            className={styles.input}
                            required
                        />
                        {form.unit !== form.baseUnit && Number.isFinite(basePrice) && form.productPrice !== "" ? (
                            <small className={styles.fieldHint}>
                                = {basePrice.toLocaleString("ru-RU", { maximumFractionDigits: 4 })} ₽ за 1 {unitLabel(form.baseUnit)}
                            </small>
                        ) : null}
                    </label>

                    <label className={styles.field} htmlFor="new-product-supplier">
                        <span>Поставщик <small className={styles.fieldHint}>необязательно</small></span>
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

                    <details className={`${styles.fieldWide} ${styles.moreOptions}`}>
                        <summary>Дополнительно: отход при обработке</summary>
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
                    </details>
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
