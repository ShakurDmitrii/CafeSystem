import styles from "../CashierPage.module.css";
import { formatDate, formatMoney, getInitials } from "./cashierUtils";

export default function ShiftLobby({
    selectedPeople,
    shifts,
    isLoading,
    getWorkersLabel,
    onChoosePeople,
    onRemovePerson,
    onCreateShift,
    onOpenShift,
    onOpenReport
}) {
    return (
        <div className={styles.shiftLobby}>
            <section className={styles.openShiftPanel}>
                <p className={styles.kicker}>Новая смена</p>
                <h2>Кто сегодня на линии?</h2>
                <p>Сотрудники попадут в сменный отчёт и останутся в истории заказов.</p>

                <button
                    className={styles.peoplePickerButton}
                    type="button"
                    onClick={onChoosePeople}
                    disabled={isLoading}
                >
                    {selectedPeople.length ? "Изменить состав" : "Выбрать сотрудников"}
                </button>

                {selectedPeople.length > 0 ? (
                    <div className={styles.selectedCrew}>
                        {selectedPeople.map((person) => (
                            <div key={person.personID} className={styles.crewChip}>
                                <span aria-hidden="true">{getInitials(person.name)}</span>
                                <strong>{person.name}</strong>
                                <button
                                    type="button"
                                    onClick={() => onRemovePerson(person)}
                                    disabled={isLoading}
                                    aria-label={`Убрать ${person.name} из смены`}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                    </div>
                ) : (
                    <div className={styles.compactEmpty}>Можно выбрать несколько сотрудников.</div>
                )}

                <button
                    className={styles.primaryButton}
                    type="button"
                    disabled={selectedPeople.length === 0 || isLoading}
                    onClick={onCreateShift}
                >
                    {isLoading ? "Открываем смену…" : "Открыть смену"}
                </button>
            </section>

            <section className={styles.shiftArchive}>
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.kicker}>Журнал</p>
                        <h2>Последние смены</h2>
                    </div>
                    <span>{shifts.length}</span>
                </div>

                {shifts.length === 0 ? (
                    <div className={styles.stateCard}>Смены появятся после первого открытия кассы.</div>
                ) : (
                    <div className={styles.shiftList}>
                        {shifts.map((shift) => (
                            <article key={shift.shiftId} className={styles.shiftCard}>
                                <div className={styles.shiftCardId}>
                                    <span>Смена</span>
                                    <strong>#{shift.shiftId}</strong>
                                </div>
                                <div className={styles.shiftCardCopy}>
                                    <h3>{formatDate(shift.data)}</h3>
                                    <p>{getWorkersLabel(shift)}</p>
                                    <div>
                                        <span>{shift.startTime || "Время не указано"}</span>
                                        <span>{formatMoney(shift.income)}</span>
                                    </div>
                                </div>
                                <span className={shift.endTime ? styles.closedStatus : styles.openStatus}>
                                    {shift.endTime ? "Закрыта" : "Открыта"}
                                </span>
                                <button
                                    className={styles.shiftCardAction}
                                    type="button"
                                    onClick={() => shift.endTime ? onOpenReport(shift.shiftId) : onOpenShift(shift)}
                                >
                                    {shift.endTime ? "Открыть отчёт" : "Войти в смену"}
                                </button>
                            </article>
                        ))}
                    </div>
                )}
            </section>
        </div>
    );
}
