import styles from "../WarehousePage.module.css";

export default function WarehouseSetup({
    warehouseName,
    editingId,
    saving,
    onNameChange,
    onSubmit,
    onCancel
}) {
    return (
        <form className={styles.operationCard} onSubmit={onSubmit}>
            <div className={styles.operationHeading}>
                <span>01 · Точки хранения</span>
                <h2>{editingId ? "Переименовать склад" : "Новый склад"}</h2>
                <p>Главный склад используется для сменного отчёта и контрольного остатка.</p>
            </div>
            <label className={styles.field}>
                <span>Название</span>
                <input
                    name="warehouseName"
                    autoComplete="off"
                    placeholder="Например, Основной склад…"
                    value={warehouseName}
                    onChange={(event) => onNameChange(event.target.value)}
                />
            </label>
            <div className={styles.formActions}>
                <button className={styles.primaryButton} type="submit" disabled={saving}>
                    {saving ? "Сохраняем…" : editingId ? "Сохранить название" : "Создать склад"}
                </button>
                {editingId ? <button className={styles.secondaryButton} type="button" onClick={onCancel}>Отмена</button> : null}
            </div>
        </form>
    );
}
