import { useEffect, useRef } from "react";
import styles from "../SuppliersProductPage.module.css";
import { unitLabel } from "../../../utils/units";
import UnitChips from "../../../components/forms/UnitChips";
import { findProductByName, isStandardUnit } from "../../../components/forms/units";

export default function SupplierProductEditor({
    supplierName,
    form,
    editingProductId,
    saving,
    uploadingImage,
    error,
    basePricePreview,
    onChange,
    onSubmit,
    onCancel,
    onUploadImage,
    supplierProducts = [],
    catalog = [],
    onEditExisting,
    focusKey
}) {
    const errorRef = useRef(null);
    const nameRef = useRef(null);
    const ownDuplicate = findProductByName(supplierProducts, form.productName, editingProductId);
    const catalogDuplicate = ownDuplicate
        ? null
        : findProductByName(catalog, form.productName, editingProductId);
    const standardUnit = isStandardUnit(form.unit, form.baseUnit, form.unitFactor);

    useEffect(() => {
        if (focusKey) nameRef.current?.focus();
    }, [focusKey]);

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    const handleFileChange = async (event) => {
        const file = event.target.files?.[0];
        if (file) await onUploadImage(file);
        event.target.value = "";
    };

    return (
        <section
            id="supplier-product-editor"
            className={styles.editorCard}
            aria-labelledby="supplier-product-editor-title"
        >
            <div className={styles.editorHeading}>
                <div>
                    <p className={styles.sectionKicker}>
                        {editingProductId ? `Продукт #${editingProductId}` : "Новая позиция"}
                    </p>
                    <h2 id="supplier-product-editor-title">
                        {editingProductId ? "Изменить продукт" : "Добавить продукт"}
                    </h2>
                </div>
                <span className={styles.editorStamp}>
                    {editingProductId ? "Правка" : "Прайс"}
                </span>
            </div>

            <p className={styles.editorIntro}>
                Поставщик: <strong>{supplierName}</strong>. Достаточно названия, единицы
                и цены. Цена подставляется в его поставки и не меняет стоимость уже принятого остатка.
            </p>

            <form className={styles.editorForm} onSubmit={onSubmit}>
                <label className={styles.field} htmlFor="supplier-product-name">
                    <span>Название продукта</span>
                    <input
                        id="supplier-product-name"
                        ref={nameRef}
                        name="productName"
                        type="text"
                        autoComplete="off"
                        value={form.productName}
                        onChange={(event) => onChange("productName", event.target.value)}
                        placeholder="Например, лосось охлаждённый…"
                        className={styles.input}
                        required
                    />
                </label>
                {ownDuplicate ? (
                    <div className={styles.duplicateNotice} role="status">
                        <span>«{ownDuplicate.productName}» уже есть у этого поставщика.</span>
                        <button type="button" onClick={() => onEditExisting(ownDuplicate)}>
                            Изменить её
                        </button>
                    </div>
                ) : null}
                {catalogDuplicate && editingProductId ? (
                    <div className={styles.duplicateNotice} role="status">
                        <span>Название «{catalogDuplicate.productName}» уже занято другой карточкой каталога.</span>
                    </div>
                ) : null}
                {catalogDuplicate && !editingProductId ? (
                    <p className={styles.catalogNotice} role="status">
                        «{catalogDuplicate.productName}» уже есть в каталоге (учёт в {unitLabel(catalogDuplicate.baseUnit)}).
                        Новая карточка не появится — этот поставщик добавится к существующей с вашей ценой.
                    </p>
                ) : null}

                {standardUnit ? (
                    <UnitChips
                        name="supplierPurchaseUnit"
                        value={form.unit}
                        onChange={(value) => onChange("unit", value)}
                    />
                ) : (
                    <p className={styles.customUnitNote}>
                        Своя единица закупки: 1 {unitLabel(form.unit)} = {form.unitFactor} {unitLabel(form.baseUnit)}.
                        Изменить можно в разделе «Дополнительно».
                    </p>
                )}

                <label className={styles.field} htmlFor="supplier-product-price">
                    <span>Цена у этого поставщика за 1 {unitLabel(form.unit)}, ₽</span>
                    <input
                        id="supplier-product-price"
                        name="productPrice"
                        type="number"
                        inputMode="decimal"
                        autoComplete="off"
                        min="0"
                        step="0.01"
                        value={form.productPrice}
                        onChange={(event) => onChange("productPrice", event.target.value)}
                        placeholder="Например, 720"
                        className={styles.input}
                        required
                    />
                    {form.productPrice !== "" && form.unit !== form.baseUnit ? (
                        <small className={styles.fieldHint}>{basePricePreview}</small>
                    ) : null}
                </label>

                <details className={styles.moreOptions} open={!standardUnit || undefined}>
                    <summary>Дополнительно: отход, упаковка, фото</summary>

                    <label className={styles.field} htmlFor="supplier-product-waste">
                        <span>Отход при обработке, %</span>
                        <input
                            id="supplier-product-waste"
                            name="waste"
                            type="number"
                            inputMode="decimal"
                            autoComplete="off"
                            min="0"
                            max="100"
                            step="0.01"
                            value={form.waste}
                            onChange={(event) => onChange("waste", event.target.value)}
                            placeholder="0"
                            className={styles.input}
                        />
                    </label>

                    <fieldset className={styles.unitFieldset}>
                        <legend>Своя единица закупки (упаковка, коробка)</legend>
                        <div className={styles.unitFields}>
                            <label className={styles.field} htmlFor="supplier-product-unit-custom">
                                <span>Название единицы</span>
                                <input
                                    id="supplier-product-unit-custom"
                                    type="text"
                                    autoComplete="off"
                                    value={form.unit}
                                    onChange={(event) => onChange("customUnit", event.target.value)}
                                    className={styles.input}
                                />
                            </label>
                            <label className={styles.field} htmlFor="supplier-product-base-unit">
                                <span>Учитывать в</span>
                                <select
                                    id="supplier-product-base-unit"
                                    name="baseUnit"
                                    autoComplete="off"
                                    value={form.baseUnit}
                                    onChange={(event) => onChange("baseUnit", event.target.value)}
                                    className={styles.select}
                                    disabled={Boolean(editingProductId)}
                                >
                                    <option value="g">граммах</option>
                                    <option value="ml">миллилитрах</option>
                                    <option value="pcs">штуках</option>
                                </select>
                            </label>
                        </div>
                        <label className={styles.field} htmlFor="supplier-product-factor">
                            <span>Сколько {unitLabel(form.baseUnit)} в 1 {unitLabel(form.unit)}</span>
                            <input
                                id="supplier-product-factor"
                                name="unitFactor"
                                type="number"
                                inputMode="decimal"
                                autoComplete="off"
                                min="0.0001"
                                step="0.0001"
                                value={form.unitFactor}
                                onChange={(event) => onChange("unitFactor", event.target.value)}
                                className={styles.input}
                                required
                            />
                        </label>
                    </fieldset>

                <div className={styles.imageField}>
                    <div className={styles.imagePreview}>
                        {form.imageUrl ? (
                            <img
                                src={form.imageUrl}
                                alt="Предпросмотр продукта"
                                width="88"
                                height="88"
                            />
                        ) : (
                            <span aria-hidden="true">
                                {(form.productName || "П").slice(0, 1).toLocaleUpperCase("ru")}
                            </span>
                        )}
                    </div>
                    <div className={styles.imageControls}>
                        <label className={styles.fileLabel} htmlFor="supplier-product-image">
                            <span>Фото продукта</span>
                            <input
                                id="supplier-product-image"
                                name="productImage"
                                type="file"
                                accept="image/jpeg,image/png"
                                onChange={handleFileChange}
                                disabled={uploadingImage}
                            />
                        </label>
                        <small aria-live="polite">
                            {uploadingImage
                                ? "Загружаем изображение…"
                                : form.imageUrl ? "Изображение добавлено" : "Можно добавить JPG или PNG до 5 МБ"}
                        </small>
                        {form.imageUrl ? (
                            <button
                                type="button"
                                className={styles.removeImageButton}
                                onClick={() => onChange("imageUrl", "")}
                                disabled={uploadingImage}
                            >
                                Убрать изображение
                            </button>
                        ) : null}
                    </div>
                </div>

                <label className={styles.favoriteField}>
                    <input
                        name="isFavorite"
                        type="checkbox"
                        checked={form.isFavorite}
                        onChange={(event) => onChange("isFavorite", event.target.checked)}
                    />
                    <span>
                        <strong>Добавить в избранное</strong>
                        <small>Позиция будет заметнее при оформлении поставки.</small>
                    </span>
                </label>
                </details>

                {error ? (
                    <div
                        ref={errorRef}
                        className={styles.errorBox}
                        role="alert"
                        tabIndex="-1"
                    >
                        {error}
                    </div>
                ) : null}

                <div className={styles.editorActions}>
                    <button
                        type="submit"
                        className={styles.primaryButton}
                        disabled={saving || uploadingImage}
                    >
                        {saving
                            ? "Сохраняем…"
                            : editingProductId ? "Сохранить изменения" : "Добавить в ассортимент"}
                    </button>
                    {editingProductId ? (
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={onCancel}
                            disabled={saving}
                        >
                            Отменить правку
                        </button>
                    ) : null}
                </div>
            </form>
        </section>
    );
}
