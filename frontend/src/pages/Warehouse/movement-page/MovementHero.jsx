import styles from "../Movement.module.css";

const TYPE_LABELS = {
    receipt: "Приход",
    movement: "Перемещение",
    writeoff: "Списание"
};

export default function MovementHero({
    movements,
    loading,
    showReport,
    showTurnoverReport,
    onRefresh,
    onToggleReport,
    onToggleTurnover
}) {
    const counts = movements.reduce((accumulator, movement) => {
        accumulator[movement.docType] = (accumulator[movement.docType] ?? 0) + 1;
        return accumulator;
    }, {});
    return (
        <header className={styles.hero}>
            <div className={styles.heroCopy}>
                <p>Журнал товарного потока</p>
                <h1>Каждая упаковка оставляет след</h1>
                <span>
                    Приходы из накладных, внутренние передачи и списания собраны
                    в одной хронологии.
                </span>
                <div className={styles.heroActions}>
                    <button type="button" onClick={onRefresh} disabled={loading}>
                        {loading ? "Обновляем…" : "Обновить журнал"}
                    </button>
                    <button type="button" onClick={onToggleReport} aria-pressed={showReport}>
                        Динамика закупок
                    </button>
                    <button type="button" onClick={onToggleTurnover} aria-pressed={showTurnoverReport}>
                        Оборот
                    </button>
                </div>
            </div>
            <div className={styles.flowBoard} aria-label="Сводка по типам операций">
                <span>Каналы движения</span>
                {Object.entries(TYPE_LABELS).map(([type, label]) => (
                    <div key={type}>
                        <i className={styles[`flow_${type}`]} aria-hidden="true" />
                        <strong>{label}</strong>
                        <small>{counts[type] ?? 0} документов</small>
                    </div>
                ))}
            </div>
        </header>
    );
}
