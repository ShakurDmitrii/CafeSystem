import { useEffect, useMemo, useRef, useState } from "react";
import CashierModal from "./CashierModal";
import { formatMoney } from "./cashierUtils";
import styles from "../CashierPage.module.css";

export default function CashPaymentModal({ total, busy, error, onClose, onConfirm }) {
    const [received, setReceived] = useState("");
    const inputRef = useRef(null);
    const numericTotal = Number(total || 0);
    const numericReceived = received === "" ? Number.NaN : Number(received);
    const change = useMemo(
        () => Number.isFinite(numericReceived) ? Math.max(0, numericReceived - numericTotal) : 0,
        [numericReceived, numericTotal]
    );
    const canConfirm = Number.isFinite(numericReceived) && numericReceived >= numericTotal && !busy;

    useEffect(() => {
        inputRef.current?.focus();
    }, []);

    const submit = (event) => {
        event.preventDefault();
        if (canConfirm) onConfirm(numericReceived);
    };

    return (
        <CashierModal
            title="Оплата наличными"
            description="Введите сумму, которую передал гость."
            onClose={busy ? () => {} : onClose}
            actions={(
                <>
                    <button className={styles.secondaryButton} type="button" onClick={onClose} disabled={busy}>
                        Отмена
                    </button>
                    <button className={styles.primaryButton} type="submit" form="cash-payment-form" disabled={!canConfirm}>
                        {busy ? "Проводим оплату…" : "Оплачено"}
                    </button>
                </>
            )}
        >
            <form id="cash-payment-form" className={styles.cashPaymentForm} onSubmit={submit}>
                <div className={styles.cashPaymentTotal}>
                    <span>Итого</span>
                    <strong>{formatMoney(numericTotal)}</strong>
                </div>
                <label className={styles.cashReceivedField} htmlFor="cash-received">
                    Вам дали
                    <div>
                        <input
                            ref={inputRef}
                            id="cash-received"
                            name="cashReceived"
                            type="number"
                            inputMode="decimal"
                            min={numericTotal}
                            step="0.01"
                            autoComplete="off"
                            value={received}
                            onChange={(event) => setReceived(event.target.value)}
                            placeholder={numericTotal.toFixed(2)}
                            disabled={busy}
                        />
                        <span>₽</span>
                    </div>
                </label>
                <div className={styles.cashPaymentChange} aria-live="polite">
                    <span>Сдача</span>
                    <strong>{formatMoney(change)}</strong>
                </div>
                {Number.isFinite(numericReceived) && numericReceived < numericTotal && (
                    <p className={styles.cashPaymentHint}>Не хватает {formatMoney(numericTotal - numericReceived)}</p>
                )}
                {error && <p className={styles.cashPaymentError}>{error}</p>}
            </form>
        </CashierModal>
    );
}

