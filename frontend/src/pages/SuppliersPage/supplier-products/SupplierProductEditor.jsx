import { useEffect, useRef } from "react";
import styles from "../SuppliersProductPage.module.css";

export default function SupplierProductEditor({
    supplierName,
    form,
    unitOptions,
    editingProductId,
    saving,
    uploadingImage,
    error,
    basePricePreview,
    onChange,
    onSubmit,
    onCancel,
    onUploadImage
}) {
    const errorRef = useRef(null);

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
                Поставщик: <strong>{supplierName}</strong>. Цена указывается
                за закупочную единицу.
            </p>

            <form className={styles.editorForm} onSubmit={onSubmit}>
                <label className={styles.field} htmlFor="supplier-product-name">
                    <span>Название продукта</span>
                    <input
                        id="supplier-product-name"
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

                <div className={styles.pairedFields}>
                    <label className={styles.field} htmlFor="supplier-product-price">
                        <span>Цена закупки, ₽</span>
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
                            placeholder="Например, 720…"
                            className={styles.input}
                            required
                        />
                    </label>

                    <label className={styles.field} htmlFor="supplier-product-waste">
                        <span>Отход, %</span>
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
                            className={styles.input}
                            required
                        />
                    </label>
                </div>

                <fieldset className={styles.unitFieldset}>
                    <legend>Единицы и пересчёт</legend>
                    <div className={styles.unitFields}>
                        <label className={styles.field} htmlFor="supplier-product-unit">
                            <span>Закупочная</span>
                            <select
                                id="supplier-product-unit"
                                name="unit"
                                autoComplete="off"
                                value={form.unit}
                                onChange={(event) => onChange("unit", event.target.value)}
                                className={styles.select}
                            >
                                {unitOptions.map((option) => (
                                    <option key={option.value} value={option.value}>
                                        {option.label}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className={styles.field} htmlFor="supplier-product-base-unit">
                            <span>Базовая</span>
                            <select
                                id="supplier-product-base-unit"
                                name="baseUnit"
                                autoComplete="off"
                                value={form.baseUnit}
                                onChange={(event) => onChange("baseUnit", event.target.value)}
                                className={styles.select}
                            >
                                <option value="g">Граммы (g)</option>
                                <option value="ml">Миллилитры (ml)</option>
                                <option value="pcs">Штуки (pcs)</option>
                            </select>
                        </label>
                    </div>

                    <label className={styles.field} htmlFor="supplier-product-factor">
                        <span>Базовых единиц в 1 закупочной</span>
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

                    <div className={styles.unitPreview} aria-live="polite">
                        <span>
                            1 {form.unit} = {form.unitFactor || "0"} {form.baseUnit}
                        </span>
                        <strong>{basePricePreview}</strong>
                    </div>
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
                                accept="image/*"
                                onChange={handleFileChange}
                                disabled={uploadingImage}
                            />
                        </label>
                        <small aria-live="polite">
                            {uploadingImage
                                ? "Загружаем изображение…"
                                : form.imageUrl ? "Изображение добавлено" : "Можно добавить JPG, PNG или WebP"}
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
