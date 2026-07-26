import { useEffect, useRef } from "react";
import ModalShell from "./ModalShell";
import styles from "../PreparationsPage.module.css";

export default function ProductionModal({
    preparation,
    warehouses,
    form,
    loading,
    error,
    formatQuantity,
    onChange,
    onSubmit,
    onClose
}) {
    const errorRef = useRef(null);

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    const producedQuantity = Number(preparation.outputWeight ?? 0)
        * Number(form.batchCount || 0);

    const handleSubmit = (event) => {
        event.preventDefault();
        onSubmit();
    };

    return (
        <ModalShell
            titleId="production-modal-title"
            eyebrow="Выпуск партии"
            title={preparation.preparationName}
            subtitle="Выберите склад и количество партий. Ингредиенты спишутся с этого же склада."
            busy={loading}
            onClose={onClose}
        >
            <form className={styles.modalForm} onSubmit={handleSubmit}>
                <div className={styles.modalFields}>
                    <label className={styles.field} htmlFor="production-warehouse">
                        <span>Склад списания и прихода</span>
                        <select
                            id="production-warehouse"
                            name="warehouseId"
                            autoComplete="off"
                            className={styles.select}
                            value={form.warehouseId}
                            onChange={(event) => onChange("warehouseId", event.target.value)}
                            required
                        >
                            <option value="">Выберите склад</option>
                            {warehouses.map((warehouse) => (
                                <option key={warehouse.warehouseId} value={warehouse.warehouseId}>
                                    {warehouse.warehouseName}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label className={styles.field} htmlFor="production-batches">
                        <span>Количество партий</span>
                        <input
                            id="production-batches"
                            name="batchCount"
                            type="number"
                            inputMode="decimal"
                            autoComplete="off"
                            min="0.01"
                            step="0.01"
                            className={styles.input}
                            value={form.batchCount}
                            onChange={(event) => onChange("batchCount", event.target.value)}
                            required
                        />
                    </label>
                </div>

                <div className={styles.productionPreview} aria-live="polite">
                    <span>Будет произведено</span>
                    <strong>{formatQuantity(producedQuantity)} г</strong>
                    <small>
                        {formatQuantity(form.batchCount || 0)} парт. ×{" "}
                        {formatQuantity(preparation.outputWeight)} г
                    </small>
                </div>

                {warehouses.length === 0 ? (
                    <div className={styles.warningBox} role="status">
                        Сначала создайте склад: без него выпустить заготовку не получится.
                    </div>
                ) : null}

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
                        Отмена
                    </button>
                    <button
                        type="submit"
                        className={styles.primaryButton}
                        disabled={loading || warehouses.length === 0}
                    >
                        {loading ? "Выпускаем…" : "Списать и выпустить"}
                    </button>
                </div>
            </form>
        </ModalShell>
    );
}
