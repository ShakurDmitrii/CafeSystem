import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import DishFormFields from "./DishFormFields";
import DishImageField from "./DishImageField";
import styles from "../DishPage.module.css";

export default function DishEditModal({
    dish,
    form,
    categories,
    error,
    isSaving,
    isImageUploading,
    onFormChange,
    onImageChange,
    onClose,
    onSave
}) {
    const closeButtonRef = useRef(null);
    const onCloseRef = useRef(onClose);
    const isBusy = isSaving || isImageUploading;
    onCloseRef.current = onClose;

    useEffect(() => {
        const previousOverflow = document.body.style.overflow;
        const previouslyFocusedElement = document.activeElement;
        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape") onCloseRef.current();
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", handleKeyDown);
            previouslyFocusedElement?.focus();
        };
    }, []);

    const handleSubmit = (event) => {
        event.preventDefault();
        onSave();
    };

    return (
        <div className={styles.modalOverlay} onMouseDown={onClose}>
            <section
                className={styles.modal}
                role="dialog"
                aria-modal="true"
                aria-labelledby="edit-dish-title"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <div className={styles.modalHeader}>
                    <div>
                        <p className={styles.eyebrow}>Редактирование блюда</p>
                        <h2 id="edit-dish-title" className={styles.modalTitle}>{dish.dishName}</h2>
                        <p className={styles.modalSubtitle}>
                            Измените карточку меню или перейдите к составу и технологии приготовления.
                        </p>
                    </div>
                    <button
                        ref={closeButtonRef}
                        type="button"
                        className={styles.closeButton}
                        onClick={onClose}
                        disabled={isBusy}
                    >
                        Закрыть
                    </button>
                </div>

                <form className={styles.modalBody} onSubmit={handleSubmit} aria-describedby={error ? "edit-dish-error" : undefined}>
                    <DishFormFields
                        form={form}
                        categories={categories}
                        idPrefix="edit"
                        onChange={onFormChange}
                    />
                    <DishImageField
                        id="edit-dish-image"
                        imageUrl={form.imageUrl}
                        isUploading={isImageUploading}
                        onFileChange={onImageChange}
                        mode="edit"
                    />

                    {error ? (
                        <div id="edit-dish-error" className={styles.errorBox} role="alert">
                            {error}
                        </div>
                    ) : null}

                    <div className={styles.modalActions}>
                        <Link className={styles.techButton} to={`/tech-card/${dish.dishId}`}>
                            Открыть техкарту
                        </Link>
                        <button type="button" className={styles.closeButton} onClick={onClose} disabled={isBusy}>
                            Отмена
                        </button>
                        <button type="submit" className={styles.primaryButton} disabled={isBusy}>
                            {isSaving ? "Сохраняем…" : isImageUploading ? "Загружаем фото…" : "Сохранить изменения"}
                        </button>
                    </div>
                </form>
            </section>
        </div>
    );
}
