import styles from "../PersonPage.module.css";
import { formatMoney } from "./personUtils";

export default function TeamHero({ peopleCount, closedShifts, amountDue }) {
    return (
        <section className={styles.hero}>
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Команда · сменная доска</p>
                <h1>Люди, на которых держится смена</h1>
                <p>
                    Аккаунты сотрудников, отработанные дни и ближайшие выплаты —
                    в одном спокойном рабочем ритме.
                </p>
            </div>

            <div className={styles.shiftRail} aria-label="Сводка команды">
                <div className={styles.railLine} aria-hidden="true">
                    <i />
                    <i />
                    <i />
                </div>
                <dl>
                    <div>
                        <dt>В команде</dt>
                        <dd>{peopleCount}</dd>
                    </div>
                    <div>
                        <dt>Закрыто смен</dt>
                        <dd>{closedShifts}</dd>
                    </div>
                    <div>
                        <dt>К выплате</dt>
                        <dd>{formatMoney(amountDue)}</dd>
                    </div>
                </dl>
            </div>
        </section>
    );
}
