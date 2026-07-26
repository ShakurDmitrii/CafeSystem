import styles from "../DishPage.module.css";

const numericFields = [
    { key: "price", label: "Цена", suffix: "₽", required: true },
    { key: "weight", label: "Вес", suffix: "г" },
    { key: "firstCost", label: "Себестоимость", suffix: "₽" }
];

export default function DishFormFields({ form, categories, idPrefix, onChange }) {
    const updateField = (field, value) => onChange((previous) => ({ ...previous, [field]: value }));

    return (
        <div className={styles.formGrid}>
            <label className={`${styles.field} ${styles.fieldWide}`} htmlFor={`${idPrefix}-dish-name`}>
                <span>Название блюда</span>
                <input
                    id={`${idPrefix}-dish-name`}
                    name={`${idPrefix}DishName`}
                    type="text"
                    className={styles.input}
                    placeholder="Например, бургер классик…"
                    autoComplete="off"
                    value={form.dishName}
                    onChange={(event) => updateField("dishName", event.target.value)}
                    required
                />
            </label>

            {numericFields.map((field) => (
                <label className={styles.field} htmlFor={`${idPrefix}-${field.key}`} key={field.key}>
                    <span>{field.label}, {field.suffix}</span>
                    <input
                        id={`${idPrefix}-${field.key}`}
                        name={`${idPrefix}${field.key}`}
                        type="number"
                        inputMode="decimal"
                        className={styles.input}
                        min="0"
                        step="0.01"
                        autoComplete="off"
                        value={form[field.key]}
                        onChange={(event) => updateField(field.key, event.target.value)}
                        required={field.required}
                    />
                </label>
            ))}

            <label className={styles.field} htmlFor={`${idPrefix}-category`}>
                <span>Категория из списка</span>
                <select
                    id={`${idPrefix}-category`}
                    name={`${idPrefix}Category`}
                    className={styles.select}
                    autoComplete="off"
                    value={form.selectedCategoryId}
                    onChange={(event) => updateField("selectedCategoryId", event.target.value)}
                >
                    <option value="">Без категории</option>
                    {categories.map((category) => (
                        <option key={category.categoryId} value={category.categoryId}>
                            {category.name}
                        </option>
                    ))}
                </select>
            </label>

            <label className={styles.field} htmlFor={`${idPrefix}-custom-category`}>
                <span>Или новая категория</span>
                <input
                    id={`${idPrefix}-custom-category`}
                    name={`${idPrefix}CustomCategory`}
                    type="text"
                    className={styles.input}
                    placeholder="Например, сезонное меню…"
                    autoComplete="off"
                    value={form.customCategory}
                    onChange={(event) => updateField("customCategory", event.target.value)}
                />
            </label>
        </div>
    );
}
