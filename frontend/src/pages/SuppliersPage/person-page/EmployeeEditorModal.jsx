import { useEffect, useRef, useState } from "react";
import styles from "../PersonPage.module.css";

const toInputValue = (value) => {
    const number = Number(value);
    return Number.isFinite(number) ? String(number) : "0";
};

export default function EmployeeEditorModal({
    person,
    saving,
    error,
    onSave,
    onClose
}) {
    const [form, setForm] = useState(() => ({
        name: person.name || "",
        salaryPerDay: toInputValue(person.salaryPerDay)
    }));
    const closeButtonRef = useRef(null);
    const onCloseRef = useRef(onClose);
    const savingRef = useRef(saving);
    onCloseRef.current = onClose;
    savingRef.current = saving;

    useEffect(() => {
        const previousOverflow = document.body.style.overflow;
        const previouslyFocusedElement = document.activeElement;
        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape" && !savingRef.current) onCloseRef.current();
        };
        window.addEventListener("keydown", handleKeyDown);

        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", handleKeyDown);
            previouslyFocusedElement?.focus();
        };
    }, []);

    const updateField = (field, value) => {
        setForm((current) => ({ ...current, [field]: value }));
    };

    const handleSubmit = (event) => {
        event.preventDefault();
        if (saving) return;
        onSave({
            name: form.name.trim(),
            salaryPerDay: Number(form.salaryPerDay || 0)
        });
    };

    return (
        <div
            className={styles.modalOverlay}
            onMouseDown={() => {
                if (!saving) onClose();
            }}
        >
            <section
                className={styles.employeeEditor}
                role="dialog"
                aria-modal="true"
                aria-labelledby="employee-editor-title"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className={styles.editorHeader}>
                    <div>
                        <p className={styles.kicker}>Сотрудник #{person.personID}</p>
                        <h2 id="employee-editor-title">Редактировать сотрудника</h2>
                        <p>Начисления рассчитываются по ставке за день. Изменение применится к ещё не закрытым дням.</p>
                    </div>
                    <button
                        ref={closeButtonRef}
                        className={styles.editorClose}
                        type="button"
                        onClick={onClose}
                        disabled={saving}
                    >
                        Закрыть
                    </button>
                </header>

                <form className={styles.employeeEditorForm} onSubmit={handleSubmit}>
                    <label className={styles.editorWideField} htmlFor="employee-edit-name">
                        Имя и фамилия
                        <input
                            id="employee-edit-name"
                            name="name"
                            type="text"
                            autoComplete="name"
                            value={form.name}
                            onChange={(event) => updateField("name", event.target.value)}
                            required
                        />
                    </label>

                    <label className={styles.editorWideField} htmlFor="employee-edit-daily-rate">
                        Ставка за день
                        <span className={styles.editorInputWithUnit}>
                            <input
                                id="employee-edit-daily-rate"
                                name="salaryPerDay"
                                type="number"
                                min="0"
                                step="0.01"
                                inputMode="decimal"
                                value={form.salaryPerDay}
                                onChange={(event) => updateField("salaryPerDay", event.target.value)}
                                required
                            />
                            <span>₽</span>
                        </span>
                    </label>

                    {error ? <div className={styles.editorError} role="alert">{error}</div> : null}

                    <div className={styles.editorActions}>
                        <button
                            type="button"
                            className={styles.editorSecondaryButton}
                            onClick={onClose}
                            disabled={saving}
                        >
                            Отмена
                        </button>
                        <button
                            type="submit"
                            className={styles.editorPrimaryButton}
                            disabled={saving}
                        >
                            {saving ? "Сохраняем…" : "Сохранить изменения"}
                        </button>
                    </div>
                </form>
            </section>
        </div>
    );
}
