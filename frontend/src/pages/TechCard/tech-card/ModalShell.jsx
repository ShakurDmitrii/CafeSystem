import { useEffect, useRef } from "react";
import styles from "../TechCardPage.module.css";

export default function ModalShell({ titleId, eyebrow, title, subtitle, onClose, busy = false, children }) {
    const modalRef = useRef(null);
    const closeButtonRef = useRef(null);
    const onCloseRef = useRef(onClose);
    onCloseRef.current = onClose;

    useEffect(() => {
        const previousOverflow = document.body.style.overflow;
        const previouslyFocusedElement = document.activeElement;
        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape") {
                onCloseRef.current();
                return;
            }

            if (event.key !== "Tab") return;
            const focusable = modalRef.current?.querySelectorAll(
                'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
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
            previouslyFocusedElement?.focus();
        };
    }, []);

    return (
        <div className={styles.modalOverlay} onMouseDown={() => onCloseRef.current()}>
            <section
                ref={modalRef}
                className={styles.modal}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <div className={styles.modalHeader}>
                    <div>
                        <p className={styles.modalEyebrow}>{eyebrow}</p>
                        <h2 id={titleId} className={styles.modalTitle}>{title}</h2>
                        <p className={styles.modalSubtitle}>{subtitle}</p>
                    </div>
                    <button
                        ref={closeButtonRef}
                        type="button"
                        className={styles.closeButton}
                        onClick={() => onCloseRef.current()}
                        disabled={busy}
                    >
                        Закрыть
                    </button>
                </div>
                {children}
            </section>
        </div>
    );
}
