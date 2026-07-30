import { useEffect, useId, useRef } from "react";
import styles from "../CashierPage.module.css";

const FOCUSABLE = [
    "button:not([disabled])",
    "a[href]",
    "input:not([disabled])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])"
].join(",");

export default function CashierModal({
    title,
    description,
    onClose,
    children,
    actions,
    wide = false,
    extraWide = false
}) {
    const titleId = useId();
    const modalRef = useRef(null);

    useEffect(() => {
        const previousFocus = document.activeElement;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        const modal = modalRef.current;
        const firstFocusable = modal?.querySelector(FOCUSABLE);
        firstFocusable?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape") {
                event.preventDefault();
                onClose();
                return;
            }
            if (event.key !== "Tab" || !modal) return;

            const focusable = [...modal.querySelectorAll(FOCUSABLE)];
            if (focusable.length === 0) return;
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

        document.addEventListener("keydown", handleKeyDown);
        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = previousOverflow;
            previousFocus?.focus?.();
        };
    }, [onClose]);

    return (
        <div className={styles.modalOverlay}>
            <section
                ref={modalRef}
                className={`${styles.modal} ${wide ? styles.modalWide : ""} ${extraWide ? styles.modalExtraWide : ""}`}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
            >
                <header className={styles.modalHeader}>
                    <div>
                        <h2 id={titleId}>{title}</h2>
                        {description && <p>{description}</p>}
                    </div>
                    <button
                        className={styles.modalClose}
                        type="button"
                        onClick={onClose}
                        aria-label="Закрыть окно"
                    >
                        ×
                    </button>
                </header>
                <div className={styles.modalBody}>{children}</div>
                {actions && <footer className={styles.modalActions}>{actions}</footer>}
            </section>
        </div>
    );
}
