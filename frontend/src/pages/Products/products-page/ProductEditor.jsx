import { useEffect, useRef } from "react";
import styles from "../ProductsPage.module.css";
import { unitLabel } from "../../../utils/units";
import UnitChips from "../../../components/forms/UnitChips";
import { findProductByName, isStandardUnit } from "../../../components/forms/units";

export default function ProductEditor({
    form,
    suppliers,
    dishCategories,
    unitOptions,
    editingProductId,
    saving,
    uploadingImage,
    error,
    basePricePreview,
    onChange,
    onSubmit,
    onCancel,
    onUploadImage,
    products = [],
    onEditExisting,
    focusKey
}) {
    const errorRef = useRef(null);
    const nameRef = useRef(null);
    const duplicate = findProductByName(products, form.productName, editingProductId);
    const standardUnit = isStandardUnit(form.unit, form.baseUnit, form.unitFactor);
    const detailsOpen = !standardUnit || form.itemType !== "ingredient";

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
                Достаточно названия, единицы и цены. Цена подставляется в новые
                поставки и не меняет стоимость уже принятого остатка.
            </p>

            <form className={styles.editorForm} onSubmit={onSubmit}>
                <label className={styles.field} htmlFor="product-name">
                    <span>Название</span>
                    <input
                        id="product-name"
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
                {duplicate ? (
                    <div className={styles.duplicateNotice} role="status">
                        <span>«{duplicate.productName}» уже есть в каталоге.</span>
                        <button type="button" onClick={() => onEditExisting(duplicate)}>
                            Открыть карточку
                        </button>
                    </div>
                ) : null}

                {standardUnit ? (
                    <UnitChips value={form.unit} onChange={(value) => onChange("unit", value)} />
                ) : (
                    <p className={styles.customUnitNote}>
                        Своя единица закупки: 1 {unitLabel(form.unit)} = {form.unitFactor} {unitLabel(form.baseUnit)}.
                        Изменить можно в разделе «Дополнительно».
                    </p>
                )}

                <label className={styles.field} htmlFor="product-price">
                    <span>Цена за 1 {unitLabel(form.unit)}, ₽</span>
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
                        placeholder="Например, 720"
                        className={styles.input}
                        required
                    />
                    {basePricePreview && form.productPrice !== "" && form.unit !== form.baseUnit ? (
                        <small className={styles.fieldHint}>{basePricePreview} — так считается себестоимость в техкартах.</small>
                    ) : null}
                </label>

                <label className={styles.field} htmlFor="product-supplier">
                    <span>Поставщик <small className={styles.optionalMark}>необязательно</small></span>
                    <select
                        id="product-supplier"
                        name="supplierId"
                        autoComplete="off"
                        value={form.supplierId}
                        onChange={(event) => onChange("supplierId", event.target.value)}
                        className={styles.select}
                    >
                        <option value="">Без поставщика</option>
                        {suppliers.map((supplier) => (
                            <option key={supplier.id} value={supplier.id}>
                                {supplier.name}
                            </option>
                        ))}
                    </select>
                </label>

                <details className={styles.moreOptions} open={detailsOpen || undefined}>
                    <summary>Дополнительно: отход, упаковка, тип, фото</summary>

                    <label className={styles.field} htmlFor="product-waste">
                        <span>Отход при обработке, %</span>
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
                            placeholder="0"
                            className={styles.input}
                        />
                    </label>

                    <fieldset className={styles.unitFieldset}>
                        <legend>Своя единица закупки (упаковка, коробка)</legend>
                        <div className={styles.unitFields}>
                            <label className={styles.field} htmlFor="product-unit-custom">
                                <span>Название единицы</span>
                                <input
                                    id="product-unit-custom"
                                    type="text"
                                    autoComplete="off"
                                    value={form.unit}
                                    onChange={(event) => onChange("customUnit", event.target.value)}
                                    className={styles.input}
                                />
                            </label>
                            <label className={styles.field} htmlFor="product-base-unit">
                                <span>Учитывать в</span>
                                <select
                                    id="product-base-unit"
                                    name="baseUnit"
                                    autoComplete="off"
                                    value={form.baseUnit}
                                    onChange={(event) => onChange("baseUnit", event.target.value)}
                                    className={styles.select}
                                >
                                    <option value="g">граммах</option>
                                    <option value="ml">миллилитрах</option>
                                    <option value="pcs">штуках</option>
                                </select>
                            </label>
                        </div>
                        <label className={styles.field} htmlFor="product-unit-factor">
                            <span>Сколько {unitLabel(form.baseUnit)} в 1 {unitLabel(form.unit)}</span>
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
                    </fieldset>

                    <fieldset className={styles.unitFieldset}>
                        <legend>Назначение</legend>
                        <div className={styles.unitFields}>
                        <label className={styles.field} htmlFor="product-item-type">
                                <span>Тип</span>
                                <select
                                    id="product-item-type"
                                    value={form.itemType}
                                    onChange={(event) => onChange("itemType", event.target.value)}
                                    className={styles.select}
                                >
                                    <option value="ingredient">Ингредиент рецепта</option>
                                    <option value="consumable">Расходник</option>
                                    <option value="packaging">Упаковка</option>
                                </select>
                            </label>
                            {form.itemType !== "ingredient" ? (
                                <label className={styles.field} htmlFor="consumable-basis">
                                    <span>Добавлять</span>
                                    <select
                                        id="consumable-basis"
                                        value={form.consumableBasis}
                                        onChange={(event) => onChange("consumableBasis", event.target.value)}
                                        className={styles.select}
                                    >
                                        <option value="per_person">На количество персон</option>
                                        <option value="per_order">Один раз на заказ</option>
                                        <option value="per_menu_item">На позиции меню</option>
                                    </select>
                                </label>
                            ) : null}
                        </div>
                    {form.itemType !== "ingredient" ? (
                        <>
                            <div className={styles.unitFields}>
                                <label className={styles.field} htmlFor="consumable-default-quantity">
                                    <span>Количество, {unitLabel(form.baseUnit)}</span>
                                    <input
                                        id="consumable-default-quantity"
                                        type="number"
                                        min="0"
                                        step="0.001"
                                        value={form.consumableDefaultQuantity}
                                        onChange={(event) => onChange("consumableDefaultQuantity", event.target.value)}
                                        className={styles.input}
                                    />
                                </label>
                                {form.consumableBasis !== "per_order" ? (
                                    <label className={styles.field} htmlFor="consumable-trigger-quantity">
                                        <span>{form.consumableBasis === "per_person" ? "На каждые N персон" : "На каждые N позиций"}</span>
                                        <input
                                            id="consumable-trigger-quantity"
                                            type="number"
                                            min="0.001"
                                            step="0.001"
                                            value={form.consumableTriggerQuantity}
                                            onChange={(event) => onChange("consumableTriggerQuantity", event.target.value)}
                                            className={styles.input}
                                        />
                                    </label>
                                ) : null}
                            </div>
                            {form.consumableBasis === "per_menu_item" ? (
                                <label className={styles.field} htmlFor="consumable-category">
                                    <span>Категория блюд</span>
                                    <select
                                        id="consumable-category"
                                        value={form.consumableDishCategoryId}
                                        onChange={(event) => onChange("consumableDishCategoryId", event.target.value)}
                                        className={styles.select}
                                    >
                                        <option value="">Все позиции меню</option>
                                        {dishCategories.map((category) => (
                                            <option key={category.categoryId} value={category.categoryId}>{category.name}</option>
                                        ))}
                                    </select>
                                </label>
                            ) : null}
                            <label className={styles.favoriteField}>
                                <input
                                    type="checkbox"
                                    checked={Boolean(form.consumableActive)}
                                    onChange={(event) => onChange("consumableActive", event.target.checked)}
                                />
                                <span>
                                    <strong>Добавлять в заказ автоматически</strong>
                                    <small>Сотрудник сможет изменить количество и вручную указать доплату.</small>
                                </span>
                            </label>
                        </>
                    ) : null}
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
                        <small>Продукт будет проще найти при работе с поставщиком.</small>
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
