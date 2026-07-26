import { useEffect, useRef } from "react";
import styles from "../SuppliersPage.module.css";

export default function ConfirmSupplierDelete({
    supplier,
    deleting,
    error,
    onConfirm,
    onClose
}) {
    const dialogRef = useRef(null);
    const closeButtonRef = useRef(null);
    const onCloseRef = useRef(onClose);
    const deletingRef = useRef(deleting);
    onCloseRef.current = onClose;
    deletingRef.current = deleting;

    useEffect(() => {
        const previousOverflow = document.body.style.overflow;
        const previousFocus = document.activeElement;
        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape" && !deletingRef.current) {
                onCloseRef.current();
                return;
            }
            if (event.key !== "Tab") return;

            const focusable = dialogRef.current?.querySelectorAll(
                'button:not([disabled]), a[href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
            );
            if (!focusable?.length) return;

            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", handleKeyDown);
            previousFocus?.focus();
        };
    }, []);

    return (
        <div
            className={styles.modalOverlay}
            onMouseDown={() => {
                if (!deleting) onCloseRef.current();
            }}
        >
            <section
                ref={dialogRef}
                className={styles.dialog}
                role="dialog"
                aria-modal="true"
                aria-labelledby="delete-supplier-title"
                aria-describedby="delete-supplier-description"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className={styles.dialogHeader}>
                    <div>
                        <p className={styles.dialogEyebrow}>Удаление карточки</p>
                        <h2 id="delete-supplier-title">Удалить поставщика?</h2>
                    </div>
                    <button
                        ref={closeButtonRef}
                        type="button"
                        className={styles.closeDialog}
                        onClick={onClose}
                        disabled={deleting}
                    >
                        Закрыть
                    </button>
                </header>

                <p id="delete-supplier-description" className={styles.dialogCopy}>
                    Карточка <strong>«{supplier.name}»</strong> исчезнет из книги.
                    Если к ней привязаны продукты или накладные, сервер может
                    отклонить удаление.
                </p>

                {error ? (
                    <div className={styles.dialogError} role="alert">{error}</div>
                ) : null}

                <div className={styles.dialogActions}>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={onClose}
                        disabled={deleting}
                    >
                        Оставить карточку
                    </button>
                    <button
                        type="button"
                        className={styles.dangerButton}
                        onClick={onConfirm}
                        disabled={deleting}
                    >
                        {deleting ? "Удаляем…": "Удалить поставщика"}
                    </button>
                </div>
            </section>
        </div>
    );
}
