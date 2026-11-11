import PageHeader from "../../../../components/layout/PageHeader";
import styles from "../DishPage.module.css";

export default function DishPageHeader({ activeView, dishCount, categoryCount, onViewChange }) {
    return (
        <>
            <PageHeader
                title="Меню"
                description="Блюда и наборы. Состав и себестоимость блюда задаются в его техкарте."
                stats={[
                    { label: "Блюд", value: dishCount },
                    { label: "Категорий", value: categoryCount }
                ]}
            />

            <section className={styles.switchCard} aria-label="Раздел меню">
                <div className={styles.switchGroup} role="tablist" aria-label="Тип позиций меню">
                    <button
                        id="dishes-tab"
                        type="button"
                        role="tab"
                        aria-selected={activeView === "dishes"}
                        aria-controls="dishes-panel"
                        className={`${styles.switchButton} ${activeView === "dishes" ? styles.switchButtonActive : ""}`}
                        onClick={() => onViewChange("dishes")}
                    >
                        Блюда
                        <span className={styles.switchCount}>{dishCount}</span>
                    </button>
                    <button
                        id="sets-tab"
                        type="button"
                        role="tab"
                        aria-selected={activeView === "sets"}
                        aria-controls="sets-panel"
                        className={`${styles.switchButton} ${activeView === "sets" ? styles.switchButtonActive : ""}`}
                        onClick={() => onViewChange("sets")}
                    >
                        Наборы
                    </button>
                </div>
                <p className={styles.switchHint}>
                    {activeView === "dishes"
                        ? "Карточки отдельных позиций и переход к техкартам."
                        : "Готовые комбинации блюд с общей ценой и фото."}
                </p>
            </section>
        </>
    );
}
