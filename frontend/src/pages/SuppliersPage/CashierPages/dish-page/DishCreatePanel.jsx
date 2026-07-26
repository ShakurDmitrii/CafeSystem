import DishFormFields from "./DishFormFields";
import DishImageField from "./DishImageField";
import styles from "../DishPage.module.css";

export default function DishCreatePanel({
    dishCount,
    form,
    categories,
    error,
    isCreating,
    isImageUploading,
    onFormChange,
    onImageChange,
    onCreate
}) {
    const isBusy = isCreating || isImageUploading;

    const handleSubmit = (event) => {
        event.preventDefault();
        onCreate();
    };

    return (
        <section className={styles.createCard} id="dish-create">
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>Новая позиция</p>
                    <h2>Добавить блюдо</h2>
                    <p>Заполните основное, а после создания мы сразу откроем техкарту.</p>
                </div>
                <div className={styles.counterChip}>{dishCount} в меню</div>
            </div>

            <form onSubmit={handleSubmit} aria-describedby={error ? "create-dish-error" : undefined}>
                <DishFormFields
                    form={form}
                    categories={categories}
                    idPrefix="create"
                    onChange={onFormChange}
                />
                <DishImageField
                    id="create-dish-image"
                    imageUrl={form.imageUrl}
                    isUploading={isImageUploading}
                    onFileChange={onImageChange}
                />

                {error ? (
                    <div id="create-dish-error" className={styles.errorBox} role="alert">
                        {error}
                    </div>
                ) : null}

                <div className={styles.formActions}>
                    <button type="submit" className={styles.primaryButton} disabled={isBusy}>
                        {isCreating ? "Создаём блюдо…" : isImageUploading ? "Загружаем фото…" : "Создать и открыть техкарту"}
                    </button>
                </div>
            </form>
        </section>
    );
}
