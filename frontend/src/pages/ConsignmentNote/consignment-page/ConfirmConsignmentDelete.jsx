import { useEffect, useRef } from "react";
import styles from "../ConsignmentNotePage.module.css";

export default function ConfirmConsignmentDelete({
    note,
    deleting,
    error,
    onConfirm,
    onClose
}) {
    const dialogRef = useRef(null);
    const closeButtonRef = useRef(null);
    const errorRef = useRef(null);
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

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    return (
        <div
            className={styles.modalOverlay}
            onMouseDown={() => {
                if (!deleting) onCloseRef.current();
            }}
        >
            <section
                ref={dialogRef}
                className={styles.confirmDialog}
                role="dialog"
                aria-modal="true"
                aria-labelledby="delete-consignment-title"
                aria-describedby="delete-consignment-description"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className={styles.dialogHeader}>
                    <div>
                        <p className={styles.modalEyebrow}>Удаление черновика</p>
                        <h2 id="delete-consignment-title">Удалить накладную № {note.id}?</h2>
                    </div>
                    <button
                        ref={closeButtonRef}
                        type="button"
                        className={styles.closeButton}
                        onClick={onClose}
                        disabled={deleting}
                    >
                        Закрыть
                    </button>
                </header>

                <p id="delete-consignment-description" className={styles.dialogCopy}>
                    Документ поставщика <strong>«{note.supplierName}»</strong> и все
                    его позиции будут удалены. Проведённые накладные удалить нельзя.
                </p>

                {error ? (
                    <div
                        ref={errorRef}
                        className={styles.formError}
                        role="alert"
                        tabIndex="-1"
                    >
                        {error}
                    </div>
                ) : null}

                <div className={styles.dialogActions}>
                    <button
                        type="button"
                        className={styles.secondaryButton}
                        onClick={onClose}
                        disabled={deleting}
                    >
                        Оставить черновик
                    </button>
                    <button
                        type="button"
                        className={styles.dangerButton}
                        onClick={onConfirm}
                        disabled={deleting}
                    >
                        {deleting ? "Удаляем…" : "Удалить накладную"}
                    </button>
                </div>
            </section>
        </div>
    );
}
