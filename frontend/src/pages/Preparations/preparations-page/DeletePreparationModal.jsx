import { useEffect, useRef } from "react";
import ModalShell from "./ModalShell";
import styles from "../PreparationsPage.module.css";

export default function DeletePreparationModal({
    preparation,
    loading,
    error,
    onConfirm,
    onClose
}) {
    const errorRef = useRef(null);

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    return (
        <ModalShell
            titleId="delete-preparation-title"
            eyebrow="Удаление"
            title={`Удалить «${preparation.preparationName}»?`}
            subtitle="Заготовка исчезнет из каталога. Если она используется в техкартах или есть на складе, сервер может запретить удаление."
            busy={loading}
            onClose={onClose}
        >
            <div className={styles.deleteNotice}>
                <span>Без возможности отмены</span>
                <p>Проверьте название перед подтверждением действия.</p>
            </div>

            {error ? (
                <div
                    ref={errorRef}
                    className={styles.errorBox}
                    role="alert"
                    tabIndex="-1"
                >
                    {error}
                </div>
            ) : null}

            <div className={styles.modalActions}>
                <button
                    type="button"
                    className={styles.secondaryButton}
                    onClick={onClose}
                    disabled={loading}
                >
                    Оставить заготовку
                </button>
                <button
                    type="button"
                    className={styles.destructiveButton}
                    onClick={onConfirm}
                    disabled={loading}
                >
                    {loading ? "Удаляем…" : "Удалить заготовку"}
                </button>
            </div>
        </ModalShell>
    );
}
