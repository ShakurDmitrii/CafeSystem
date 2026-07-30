import CashierModal from "./CashierModal";
import styles from "../CashierPage.module.css";
import { formatDate, formatMoney } from "./cashierUtils";

export default function DebtNotification({ todayDebts, overdueDebts, onClose }) {
    const allDebts = [...overdueDebts, ...todayDebts];
    const total = allDebts.reduce((sum, debt) => sum + Number(debt.amount || 0), 0);

    return (
        <CashierModal
            title="Счета ждут оплаты"
            description="Проверьте обещанные даты и свяжитесь с гостями после смены."
            onClose={onClose}
            actions={(
                <>
                    <span className={styles.modalSummary}>
                        {allDebts.length} · {formatMoney(total)}
                    </span>
                    <button className={styles.primaryButton} type="button" onClick={onClose}>
                        Понятно
                    </button>
                </>
            )}
        >
            <div className={styles.debtNoticeList}>
                {overdueDebts.length > 0 && (
                    <section>
                        <h3>Просрочены · {overdueDebts.length}</h3>
                        {overdueDebts.map((debt) => (
                            <article key={debt.orderId} className={styles.overdueDebt}>
                                <div><span>Заказ</span><strong>#{debt.orderId}</strong></div>
                                <strong>{formatMoney(debt.amount)}</strong>
                                <span>Срок: {formatDate(debt.debt_payment_date)}</span>
                            </article>
                        ))}
                    </section>
                )}
                {todayDebts.length > 0 && (
                    <section>
                        <h3>На сегодня · {todayDebts.length}</h3>
                        {todayDebts.map((debt) => (
                            <article key={debt.orderId} className={styles.todayDebt}>
                                <div><span>Заказ</span><strong>#{debt.orderId}</strong></div>
                                <strong>{formatMoney(debt.amount)}</strong>
                                <span>Срок: {formatDate(debt.debt_payment_date)}</span>
                            </article>
                        ))}
                    </section>
                )}
            </div>
        </CashierModal>
    );
}
