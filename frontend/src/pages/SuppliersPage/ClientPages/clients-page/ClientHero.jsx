import styles from "../ClientsPage.module.css";
import { formatMoney } from "./clientUtils";

export default function ClientHero({
    clientsCount,
    contactsCount,
    debtClientsCount,
    totalDebt
}) {
    return (
        <section className={styles.hero}>
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Гостевая книга · CRM</p>
                <h1>Помните гостя, а не только чек</h1>
                <p>
                    Контакты, любимые блюда, история визитов и расчёты собраны в одном
                    рабочем профиле.
                </p>
                <dl className={styles.heroStats}>
                    <div>
                        <dt>Гостей</dt>
                        <dd>{clientsCount}</dd>
                    </div>
                    <div>
                        <dt>С контактом</dt>
                        <dd>{contactsCount}</dd>
                    </div>
                    <div>
                        <dt>Есть долг</dt>
                        <dd>{debtClientsCount}</dd>
                    </div>
                </dl>
            </div>

            <div className={styles.guestTicket} aria-label={`Открытые долги: ${formatMoney(totalDebt)}`}>
                <span className={styles.ticketLabel}>Открытый баланс</span>
                <strong>{formatMoney(totalDebt)}</strong>
                <span className={styles.ticketCaption}>
                    {debtClientsCount
                        ? `${debtClientsCount} ${debtClientsCount === 1 ? "гость ждёт" : "гостей ждут"} расчёта`
                        : "Все гостевые счета закрыты"}
                </span>
                <div className={styles.ticketRoute} aria-hidden="true">
                    <span>контакт</span>
                    <i />
                    <span>визит</span>
                    <i />
                    <span>возврат</span>
                </div>
            </div>
        </section>
    );
}
