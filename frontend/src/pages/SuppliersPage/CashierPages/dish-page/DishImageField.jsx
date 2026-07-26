import styles from "../DishPage.module.css";

export default function DishImageField({
    id,
    imageUrl,
    isUploading,
    onFileChange,
    mode = "create"
}) {
    const hint = isUploading
        ? "Загружаем изображение…"
        : imageUrl
            ? mode === "edit"
                ? "Новое фото появится после сохранения изменений."
                : "Фото загружено и сохранится вместе с блюдом."
            : "Добавьте фото, чтобы позицию было легко узнать в кассе.";

    return (
        <div className={styles.imagePanel}>
            <div className={styles.imageInfo}>
                <div className={styles.imageHeading}>Фото блюда</div>
                <div id={`${id}-hint`} className={styles.imageHint} aria-live="polite">
                    {hint}
                </div>
            </div>

            <div className={styles.imageControls}>
                <label className={styles.fileLabel} htmlFor={id}>Выбрать фото</label>
                <input
                    id={id}
                    name={id}
                    type="file"
                    accept="image/*"
                    className={styles.fileInput}
                    aria-describedby={`${id}-hint`}
                    onChange={(event) => onFileChange(event.target.files?.[0])}
                    disabled={isUploading}
                />
                {imageUrl ? (
                    <img
                        src={imageUrl}
                        alt="Предпросмотр блюда"
                        className={styles.previewImage}
                        width="96"
                        height="96"
                    />
                ) : (
                    <div className={styles.previewPlaceholder} aria-hidden="true">Нет фото</div>
                )}
            </div>
        </div>
    );
}
