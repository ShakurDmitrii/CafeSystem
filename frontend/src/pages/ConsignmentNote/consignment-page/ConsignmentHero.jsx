import styles from "../ConsignmentNotePage.module.css";

export default function ConsignmentHero({
    totalCount,
    draftCount,
    postedCount,
    monthTotal
}) {
    return (
        <section className={styles.hero} aria-labelledby="consignment-title">
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Приёмка поставок</p>
                <h1 id="consignment-title" className={styles.title}>
                    Накладная проходит один ясный маршрут
                </h1>
                <p className={styles.subtitle}>
                    Соберите позиции и закупочные цены в черновике, затем проведите
                    документ на нужный склад и отправьте печатную форму.
                </p>
                <dl className={styles.heroStats}>
                    <div>
                        <dt>Документов</dt>
                        <dd>{totalCount}</dd>
                    </div>
                    <div>
                        <dt>Черновиков</dt>
                        <dd>{draftCount}</dd>
                    </div>
                    <div>
                        <dt>Проведено</dt>
                        <dd>{postedCount}</dd>
                    </div>
                </dl>
            </div>

            <div className={styles.routeBoard} aria-label="Маршрут приходной накладной">
                <span className={styles.routeLabel}>Маршрут документа</span>
                <ol className={styles.routeSteps}>
                    <li>
                        <span>1</span>
                        <div>
                            <strong>Черновик</strong>
                            <small>Поставщик и дата</small>
                        </div>
                    </li>
                    <li>
                        <span>2</span>
                        <div>
                            <strong>Позиции</strong>
                            <small>Количество и цена</small>
                        </div>
                    </li>
                    <li>
                        <span>3</span>
                        <div>
                            <strong>Склад</strong>
                            <small>Проведение прихода</small>
                        </div>
                    </li>
                </ol>
                <p>
                    Сумма проведённых документов: <strong>{monthTotal}</strong>
                </p>
            </div>
        </section>
    );
}
