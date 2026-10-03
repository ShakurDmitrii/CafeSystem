import PageHeader from "../../../components/layout/PageHeader";

const TYPE_LABELS = {
    receipt: "Приходов",
    movement: "Перемещений",
    writeoff: "Списаний"
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
        <PageHeader
            title="Движения"
            description="Журнал приходов, перемещений и списаний по всем складам."
            stats={Object.entries(TYPE_LABELS).map(([type, label]) => ({ label, value: counts[type] ?? 0 }))}
            actions={(
                <>
                    <button type="button" onClick={onRefresh} disabled={loading}>
                        {loading ? "Обновляем…" : "Обновить"}
                    </button>
                    <button type="button" onClick={onToggleReport} aria-pressed={showReport}>
                        Динамика закупок
                    </button>
                    <button type="button" onClick={onToggleTurnover} aria-pressed={showTurnoverReport}>
                        Оборот
                    </button>
                </>
            )}
        />
    );
}
