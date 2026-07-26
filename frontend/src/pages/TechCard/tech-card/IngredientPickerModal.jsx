import ModalShell from "./ModalShell";
import styles from "../TechCardPage.module.css";

export default function IngredientPickerModal({
    search,
    tab,
    products,
    preparations,
    productsById,
    onSearchChange,
    onTabChange,
    onSelectProduct,
    onSelectPreparation,
    onCreateIngredient,
    onClose
}) {
    return (
        <ModalShell
            titleId="ingredient-picker-title"
            eyebrow="Справочник кухни"
            title="Выбрать ингредиент"
            subtitle="Используйте продукт со склада или готовую заготовку из другой техкарты."
            onClose={onClose}
        >
            <div className={styles.modalToolbar}>
                <label className={styles.searchField} htmlFor="ingredient-search">
                    <span>Поиск по названию</span>
                    <input
                        id="ingredient-search"
                        name="ingredientSearch"
                        type="search"
                        value={search}
                        onChange={(event) => onSearchChange(event.target.value)}
                        placeholder="Например, сливочный сыр…"
                        autoComplete="off"
                        className={styles.searchInput}
                    />
                </label>
                <button type="button" className={styles.secondaryButton} onClick={onCreateIngredient}>
                    Создать новый продукт
                </button>
            </div>

            <div className={styles.pickerTabs} role="tablist" aria-label="Тип ингредиента">
                <button
                    id="products-tab"
                    type="button"
                    role="tab"
                    aria-selected={tab === "products"}
                    aria-controls="ingredient-options"
                    className={`${styles.pickerTab} ${tab === "products" ? styles.activePickerTab : ""}`}
                    onClick={() => onTabChange("products")}
                >
                    Продукты <span>{products.length}</span>
                </button>
                <button
                    id="preparations-tab"
                    type="button"
                    role="tab"
                    aria-selected={tab === "preparations"}
                    aria-controls="ingredient-options"
                    className={`${styles.pickerTab} ${tab === "preparations" ? styles.activePickerTab : ""}`}
                    onClick={() => onTabChange("preparations")}
                >
                    Заготовки <span>{preparations.length}</span>
                </button>
            </div>

            <div
                id="ingredient-options"
                className={styles.modalList}
                role="tabpanel"
                aria-labelledby={tab === "products" ? "products-tab" : "preparations-tab"}
            >
                {tab === "products" ? (
                    products.length > 0 ? products.map((group) => (
                        <button
                            key={group.key}
                            type="button"
                            className={styles.ingredientOption}
                            onClick={() => onSelectProduct(group)}
                        >
                            <span className={styles.ingredientOptionContent}>
                                <strong>{group.name}</strong>
                                <span>Продукт</span>
                            </span>
                            <span className={styles.ingredientOptionPrice}>
                                {Number(group.averagePrice || 0).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽/
                                {productsById.get(group.representativeId)?.baseUnit || productsById.get(group.representativeId)?.unit || "ед."}
                            </span>
                        </button>
                    )) : <div className={styles.emptyModalState}>Продукты по этому запросу не найдены.</div>
                ) : (
                    preparations.length > 0 ? preparations.map((preparation) => (
                        <button
                            key={preparation.preparationId}
                            type="button"
                            className={styles.ingredientOption}
                            onClick={() => onSelectPreparation(preparation)}
                        >
                            <span className={styles.ingredientOptionContent}>
                                <strong>{preparation.preparationName}</strong>
                                <span>Заготовка</span>
                            </span>
                            <span className={styles.ingredientOptionPrice}>
                                Выход {Number(preparation.outputWeight || 0).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} г
                            </span>
                        </button>
                    )) : <div className={styles.emptyModalState}>Заготовки по этому запросу не найдены.</div>
                )}
            </div>
        </ModalShell>
    );
}
