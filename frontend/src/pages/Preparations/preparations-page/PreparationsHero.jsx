import styles from "../PreparationsPage.module.css";

export default function PreparationsHero({
    preparationCount,
    warehouseCount,
    totalStock,
    formatQuantity
}) {
    return (
        <section className={styles.hero} aria-labelledby="preparations-title">
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Заготовочный цех</p>
                <h1 id="preparations-title" className={styles.title}>
                    Заготовки под контролем
                </h1>
                <p className={styles.subtitle}>
                    Создавайте основы, собирайте техкарты и выпускайте готовые партии
                    сразу на нужный склад.
                </p>
            </div>

            <div className={styles.shiftBoard} aria-label="Сводка по заготовкам">
                <span className={styles.boardLabel}>Сводка цеха</span>
                <dl className={styles.boardMetrics}>
                    <div>
                        <dt>Заготовок</dt>
                        <dd>{preparationCount}</dd>
                    </div>
                    <div>
                        <dt>Складов</dt>
                        <dd>{warehouseCount}</dd>
                    </div>
                    <div>
                        <dt>Общий остаток</dt>
                        <dd>{formatQuantity(totalStock)} г</dd>
                    </div>
                </dl>
                <p>
                    При выпуске ингредиенты списываются, а готовая партия приходуется
                    на один выбранный склад.
                </p>
            </div>
        </section>
    );
}
