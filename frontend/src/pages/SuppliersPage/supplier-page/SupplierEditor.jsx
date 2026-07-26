import { useEffect, useRef } from "react";
import styles from "../SuppliersPage.module.css";

export default function SupplierEditor({
    form,
    editingSupplierId,
    saving,
    error,
    onChange,
    onSubmit,
    onCancel
}) {
    const errorRef = useRef(null);

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    const initial = (form.name || "П").slice(0, 1).toLocaleUpperCase("ru");

    return (
        <section
            id="supplier-editor"
            className={styles.editorCard}
            aria-labelledby="supplier-editor-title"
        >
            <div className={styles.editorHeading}>
                <div>
                    <p className={styles.sectionKicker}>
                        {editingSupplierId ? `Поставщик #${editingSupplierId}` : "Новый партнёр"}
                    </p>
                    <h2 id="supplier-editor-title">
                        {editingSupplierId ? "Изменить данные" : "Добавить поставщика"}
                    </h2>
                </div>
                <span className={styles.editorStamp}>
                    {editingSupplierId ? "Правка" : "Контакт"}
                </span>
            </div>

            <p className={styles.editorIntro}>
                Название поможет найти партнёра, а контакт останется под рукой
                при согласовании поставки.
            </p>

            <form className={styles.editorForm} onSubmit={onSubmit}>
                <label className={styles.field} htmlFor="supplier-name">
                    <span>Название компании</span>
                    <input
                        id="supplier-name"
                        name="supplierName"
                        type="text"
                        autoComplete="organization"
                        value={form.name}
                        onChange={(event) => onChange("name", event.target.value)}
                        placeholder="Например, Океан Фуд…"
                        className={styles.input}
                        required
                    />
                </label>

                <label className={styles.field} htmlFor="supplier-contact">
                    <span>Телефон, почта или другой контакт</span>
                    <input
                        id="supplier-contact"
                        name="communication"
                        type="text"
                        autoComplete="off"
                        value={form.communication}
                        onChange={(event) => onChange("communication", event.target.value)}
                        placeholder="Например, +7 900 000-00-00…"
                        className={styles.input}
                    />
                </label>
                <p className={styles.contactHint}>
                    Поле можно оставить пустым и заполнить позже.
                </p>

                <div className={styles.editorPreview} aria-label="Предпросмотр карточки">
                    <span className={styles.previewMark} aria-hidden="true">{initial}</span>
                    <div>
                        <strong>{form.name.trim() || "Название компании"}</strong>
                        <span>{form.communication.trim() || "Контакт пока не указан"}</span>
                    </div>
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

                <div className={styles.editorActions}>
                    <button
                        type="submit"
                        className={styles.primaryButton}
                        disabled={saving}
                    >
                        {saving
                            ? "Сохраняем…"
                            : editingSupplierId ? "Сохранить изменения" : "Добавить поставщика"}
                    </button>
                    {editingSupplierId ? (
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={onCancel}
                            disabled={saving}
                        >
                            Отменить правку
                        </button>
                    ) : null}
                </div>
            </form>
        </section>
    );
}
