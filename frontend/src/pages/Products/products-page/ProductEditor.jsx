import { useEffect, useRef } from "react";
import styles from "../ProductsPage.module.css";

export default function ProductEditor({
    form,
    suppliers,
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
            id="product-editor"
            className={styles.editorCard}
            aria-labelledby="product-editor-title"
        >
            <div className={styles.editorHeading}>
                <div>
                    <p className={styles.sectionKicker}>
                        {editingProductId ? `Продукт #${editingProductId}` : "Новая позиция"}
                    </p>
                    <h2 id="product-editor-title">
                        {editingProductId ? "Изменить продукт" : "Добавить продукт"}
                    </h2>
                </div>
                <span className={styles.editorStamp}>
                    {editingProductId ? "Правка" : "Карточка"}
                </span>
            </div>

            <p className={styles.editorIntro}>
                Цена указывается за закупочную единицу. Коэффициент переводит её
                в единицу складского учёта.
            </p>

            <form className={styles.editorForm} onSubmit={onSubmit}>
                <label className={styles.field} htmlFor="product-supplier">
                    <span>Поставщик</span>
                    <select
                        id="product-supplier"
                        name="supplierId"
                        autoComplete="off"
                        value={form.supplierId}
                        onChange={(event) => onChange("supplierId", event.target.value)}
                        className={styles.select}
                        required
                    >
                        <option value="">Выберите поставщика</option>
                        {suppliers.map((supplier) => (
                            <option key={supplier.id} value={supplier.id}>
                                {supplier.name}
                            </option>
                        ))}
                    </select>
                </label>

                <label className={styles.field} htmlFor="product-name">
                    <span>Название продукта</span>
                    <input
                        id="product-name"
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
                    <label className={styles.field} htmlFor="product-price">
                        <span>Цена закупки, ₽</span>
                        <input
                            id="product-price"
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

                    <label className={styles.field} htmlFor="product-waste">
                        <span>Отход по умолчанию, %</span>
                        <input
                            id="product-waste"
                            name="waste"
                            type="number"
                            inputMode="decimal"
                            autoComplete="off"
                            min="0"
                            max="100"
                            step="0.01"
                            value={form.waste}
                            onChange={(event) => onChange("waste", event.target.value)}
                            placeholder="Например, 5…"
                            className={styles.input}
                            required
                        />
                    </label>
                </div>

                <fieldset className={styles.unitFieldset}>
                    <legend>Единицы и пересчёт</legend>
                    <div className={styles.unitFields}>
                        <label className={styles.field} htmlFor="product-unit">
                            <span>Закупочная</span>
                            <select
                                id="product-unit"
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

                        <label className={styles.field} htmlFor="product-base-unit">
                            <span>Базовая</span>
                            <select
                                id="product-base-unit"
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

                    <label className={styles.field} htmlFor="product-unit-factor">
                        <span>Базовых единиц в 1 закупочной</span>
                        <input
                            id="product-unit-factor"
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
                                {(form.productName || "П").slice(0, 1).toUpperCase()}
                            </span>
                        )}
                    </div>
                    <div className={styles.imageControls}>
                        <label className={styles.fileLabel} htmlFor="product-image">
                            <span>Фото продукта</span>
                            <input
                                id="product-image"
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
                        <small>Продукт будет проще найти при работе с поставщиком.</small>
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
                            : editingProductId ? "Сохранить изменения" : "Добавить в каталог"}
                    </button>
                    {editingProductId ? (
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={onCancel}
                            disabled={saving}
                        >
                            Отменить редактирование
                        </button>
                    ) : null}
                </div>
            </form>
        </section>
    );
}
