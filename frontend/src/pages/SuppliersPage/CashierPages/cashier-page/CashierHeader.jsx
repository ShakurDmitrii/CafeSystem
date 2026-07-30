import styles from "../CashierPage.module.css";

export default function CashierHeader({
    shiftOpen,
    currentShift,
    workersLabel,
    debtsCount,
    isLoading,
    onShowDebts,
    onPrintReport,
    onOpenKitchen
}) {
    return (
        <header className={styles.shiftConsole}>
            <div className={styles.consoleTitle}>
                <p className={styles.kicker}>Сервисная линия</p>
                <h1>{shiftOpen ? `Смена #${currentShift?.shiftId || "—"}` : "Касса закрыта"}</h1>
                <p>
                    {shiftOpen
                        ? `На линии: ${workersLabel}`
                        : "Выберите команду и откройте рабочую смену."}
                </p>
            </div>

            <div className={styles.serviceRail} aria-label="Путь заказа">
                <span>Принят</span>
                <i aria-hidden="true" />
                <span>Готовится</span>
                <i aria-hidden="true" />
                <span>К выдаче</span>
            </div>

            {shiftOpen && (
                <div className={styles.consoleActions}>
                    {debtsCount > 0 && (
                        <button className={styles.debtAlertButton} type="button" onClick={onShowDebts}>
                            Долги <span>{debtsCount}</span>
                        </button>
                    )}
                    <button
                        className={styles.utilityButton}
                        type="button"
                        onClick={onPrintReport}
                        disabled={!currentShift || isLoading}
                    >
                        Z-отчёт
                    </button>
                    <button
                        className={styles.utilityButton}
                        type="button"
                        onClick={onOpenKitchen}
                        disabled={!currentShift || isLoading}
                    >
                        Экран кухни <span aria-hidden="true">↗</span>
                    </button>
                </div>
            )}
        </header>
    );
}
