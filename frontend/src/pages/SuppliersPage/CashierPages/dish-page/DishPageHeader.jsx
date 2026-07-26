import styles from "../DishPage.module.css";

export default function DishPageHeader({ activeView, dishCount, categoryCount, onViewChange }) {
    return (
        <>
            <header className={styles.hero}>
                <div className={styles.heroCopy}>
                    <p className={styles.eyebrow}>Редактор меню</p>
                    <h1 className={styles.title}>Меню, готовое к смене</h1>
                    <p className={styles.subtitle}>
                        Собирайте блюда и наборы в одном месте. Цена, состав и техкарта
                        остаются рядом, чтобы карточку можно было быстро проверить перед продажей.
                    </p>
                </div>

                <div className={styles.menuBrief} aria-label="Сводка меню">
                    <div className={styles.briefMarker} aria-hidden="true">МЕНЮ</div>
                    <dl className={styles.briefStats}>
                        <div>
                            <dt>Блюд</dt>
                            <dd>{dishCount}</dd>
                        </div>
                        <div>
                            <dt>Категорий</dt>
                            <dd>{categoryCount}</dd>
                        </div>
                    </dl>
                    <p>Сначала создайте карточку, затем заполните её техкарту.</p>
                </div>
            </header>

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
