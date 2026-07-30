import { useEffect, useRef } from "react";
import styles from "../ConsignmentNotePage.module.css";

export default function ConsignmentCreator({
    form,
    suppliers,
    saving,
    error,
    onChange,
    onSubmit
}) {
    const errorRef = useRef(null);

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    return (
        <section id="consignment-editor" className={styles.creator} aria-labelledby="creator-title">
            <div className={styles.sectionHeading}>
                <p className={styles.sectionKicker}>Новый документ</p>
                <h2 id="creator-title">Создать черновик</h2>
                <p>
                    Сначала выберите поставщика и дату. Позиции добавляются после
                    создания накладной.
                </p>
            </div>

            <form className={styles.creatorForm} onSubmit={onSubmit} noValidate>
                <label className={styles.field} htmlFor="consignment-supplier">
                    <span>Поставщик</span>
                    <select
                        id="consignment-supplier"
                        name="supplierId"
                        autoComplete="off"
                        value={form.supplierId}
                        onChange={(event) => onChange("supplierId", event.target.value)}
                        aria-describedby={error ? "consignment-create-error" : undefined}
                    >
                        <option value="">Выберите поставщика</option>
                        {suppliers.map((supplier) => (
                            <option key={supplier.id} value={supplier.id}>
                                {supplier.name}
                            </option>
                        ))}
                    </select>
                </label>

                <label className={styles.field} htmlFor="consignment-date">
                    <span>Дата поставки</span>
                    <input
                        id="consignment-date"
                        name="consignmentDate"
                        type="date"
                        autoComplete="off"
                        value={form.date}
                        onChange={(event) => onChange("date", event.target.value)}
                        aria-describedby={error ? "consignment-create-error" : undefined}
                    />
                </label>

                {error ? (
                    <div
                        ref={errorRef}
                        id="consignment-create-error"
                        className={styles.formError}
                        role="alert"
                        tabIndex="-1"
                    >
                        {error}
                    </div>
                ) : null}

                <button type="submit" className={styles.primaryButton} disabled={saving}>
                    {saving ? "Создаём черновик…" : "Создать накладную"}
                </button>
            </form>

            <div className={styles.creatorNote}>
                <span aria-hidden="true">i</span>
                <p>
                    Черновик не меняет остатки. Приход появится на складе только
                    после проведения документа.
                </p>
            </div>
        </section>
    );
}
