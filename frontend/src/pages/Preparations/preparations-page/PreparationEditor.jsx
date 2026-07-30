import { useEffect, useRef } from "react";
import styles from "../PreparationsPage.module.css";

export default function PreparationEditor({
    form,
    editing,
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

    const handleSubmit = (event) => {
        event.preventDefault();
        onSubmit();
    };

    return (
        <section
            id="preparation-editor"
            className={styles.editorCard}
            aria-labelledby="preparation-editor-title"
        >
            <div className={styles.editorHeading}>
                <div>
                    <p className={styles.sectionKicker}>
                        {editing ? "Редактирование" : "Новая позиция"}
                    </p>
                    <h2 id="preparation-editor-title">
                        {editing ? "Изменить заготовку" : "Создать заготовку"}
                    </h2>
                </div>
                <span className={styles.editorStamp}>{editing ? "Правка" : "Черновик"}</span>
            </div>

            <p className={styles.editorIntro}>
                Укажите название и выход одной партии. Состав настраивается в отдельной
                технологической карте.
            </p>

            <form className={styles.editorForm} onSubmit={handleSubmit}>
                <label className={styles.field} htmlFor="preparation-name">
                    <span>Название</span>
                    <input
                        id="preparation-name"
                        name="preparationName"
                        type="text"
                        autoComplete="off"
                        value={form.preparationName}
                        onChange={(event) => onChange("preparationName", event.target.value)}
                        placeholder="Например, соус спайси…"
                        className={styles.input}
                        required
                    />
                </label>

                <label className={styles.field} htmlFor="preparation-output">
                    <span>Выход одной партии, г</span>
                    <input
                        id="preparation-output"
                        name="outputWeight"
                        type="number"
                        inputMode="decimal"
                        autoComplete="off"
                        min="0.01"
                        step="0.01"
                        value={form.outputWeight}
                        onWheel={(event) => event.currentTarget.blur()}
                        onChange={(event) => onChange("outputWeight", event.target.value)}
                        placeholder="Например, 500…"
                        className={styles.input}
                        required
                    />
                </label>

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
                    <button type="submit" className={styles.primaryButton} disabled={saving}>
                        {saving
                            ? "Сохраняем…"
                            : editing ? "Сохранить изменения" : "Создать и заполнить состав"}
                    </button>
                    {editing ? (
                        <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={onCancel}
                            disabled={saving}
                        >
                            Отменить редактирование
                        </button>
                    ) : null}
                </div>
            </form>

            <div className={styles.editorNote}>
                <strong>После создания</strong>
                <span>Сразу откроется техкарта, где можно добавить продукты и другие заготовки.</span>
            </div>
        </section>
    );
}
