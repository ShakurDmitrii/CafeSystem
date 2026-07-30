import styles from "../Movement.module.css";

const TYPE_LABELS = {
    receipt: "Приход",
    movement: "Передача",
    writeoff: "Списание"
};

const getRoute = (movement, warehouseMap) => {
    const from = warehouseMap[movement.fromWarehouseId] ?? movement.fromWarehouseId;
    const to = warehouseMap[movement.toWarehouseId] ?? movement.toWarehouseId;
    if (movement.docType === "receipt") return { from: "Поставщик", to: to ?? "—" };
    if (movement.docType === "writeoff") return { from: from ?? "—", to: "Списание" };
    return { from: from ?? "—", to: to ?? "—" };
};

export default function MovementLedger({
    movements,
    loading,
    error,
    warehouseMap,
    productMap,
    editDates,
    savingDateId,
    onDateChange,
    onSaveDate,
    formatDate,
    formatNumber
}) {
    if (loading) return <div className={styles.stateCard} role="status">Загружаем движения…</div>;
    if (error) return <div className={`${styles.stateCard} ${styles.error}`} role="alert">{error}</div>;
    if (!movements.length) return <div className={styles.stateCard}>По этим фильтрам движений нет.</div>;
    return (
        <div className={styles.ledgerList}>
            {movements.map((movement) => {
                const route = getRoute(movement, warehouseMap);
                return (
                    <article className={styles.ledgerRow} key={`${movement.id}-${movement.productId}`}>
                        <div className={styles.ledgerMarker}>
                            <span className={styles[`type_${movement.docType}`]} aria-hidden="true" />
                            <small>#{movement.id}</small>
                        </div>
                        <div className={styles.ledgerMain}>
                            <div className={styles.ledgerTitle}>
                                <strong>{productMap[movement.productId] ?? `Товар #${movement.productId}`}</strong>
                                <span className={styles[`badge_${movement.docType}`]}>
                                    {TYPE_LABELS[movement.docType] ?? movement.docType ?? "Операция"}
                                </span>
                            </div>
                            <div className={styles.routeLine}>
                                <span>{route.from}</span><i aria-hidden="true">→</i><span>{route.to}</span>
                            </div>
                            <p>{formatDate(movement.docDate)} · товар #{movement.productId} · {movement.status ?? "—"}</p>
                        </div>
                        <dl className={styles.ledgerNumbers}>
                            <div><dt>Количество</dt><dd>{formatNumber(movement.quantity)}</dd></div>
                            <div><dt>Цена</dt><dd>{movement.unitPrice == null ? "—" : `${formatNumber(movement.unitPrice)} ₽`}</dd></div>
                            <div><dt>Сумма</dt><dd>{movement.lineTotal == null ? "—" : `${formatNumber(movement.lineTotal)} ₽`}</dd></div>
                        </dl>
                        <details className={styles.dateDetails}>
                            <summary>Изменить дату</summary>
                            <div>
                                <label>
                                    <span className={styles.visuallyHidden}>Дата движения #{movement.id}</span>
                                    <input type="datetime-local" name={`movementDate-${movement.id}`}
                                        value={editDates[movement.id] ?? ""}
                                        onChange={(event) => onDateChange(movement.id, event.target.value)} />
                                </label>
                                <button type="button" onClick={() => onSaveDate(movement.id)}
                                    disabled={savingDateId === movement.id}>
                                    {savingDateId === movement.id ? "Сохраняем…" : "Сохранить"}
                                </button>
                            </div>
                        </details>
                    </article>
                );
            })}
        </div>
    );
}
