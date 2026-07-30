import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { formatDate, formatMoney, formatQuantity } from "./consignmentUtils";
import styles from "../ConsignmentNotePage.module.css";

export default function ConsignmentItemsModal({
    note,
    supplierName,
    posted,
    warehouseName,
    selectedWarehouseId,
    warehouses,
    availableProducts,
    lines,
    productForm,
    total,
    loading,
    adding,
    posting,
    deletingLineId,
    pendingDeleteLineId,
    error,
    onProductChange,
    onAddProduct,
    onRequestDeleteLine,
    onCancelDeleteLine,
    onConfirmDeleteLine,
    onWarehouseChange,
    onPost,
    onClose
}) {
    const modalRef = useRef(null);
    const closeButtonRef = useRef(null);
    const errorRef = useRef(null);
    const onCloseRef = useRef(onClose);
    const busyRef = useRef(loading || adding || posting || deletingLineId != null);
    onCloseRef.current = onClose;
    busyRef.current = loading || adding || posting || deletingLineId != null;

    useEffect(() => {
        const previousOverflow = document.body.style.overflow;
        const previousFocus = document.activeElement;
        document.body.style.overflow = "hidden";
        closeButtonRef.current?.focus();

        const handleKeyDown = (event) => {
            if (event.key === "Escape" && !busyRef.current) {
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
            previousFocus?.focus();
        };
    }, []);

    useEffect(() => {
        if (error) errorRef.current?.focus();
    }, [error]);

    return (
        <div
            className={styles.modalOverlay}
            onMouseDown={() => {
                if (!busyRef.current) onCloseRef.current();
            }}
        >
            <section
                ref={modalRef}
                className={styles.itemsModal}
                role="dialog"
                aria-modal="true"
                aria-labelledby="consignment-items-title"
                aria-describedby="consignment-items-description"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className={styles.modalHeader}>
                    <div>
                        <p className={styles.modalEyebrow}>Приходный документ</p>
                        <h2 id="consignment-items-title">Накладная № {note.consignmentId}</h2>
                        <p id="consignment-items-description">
                            {supplierName} · {formatDate(note.date)}
                        </p>
                    </div>
                    <button
                        ref={closeButtonRef}
                        type="button"
                        className={styles.closeButton}
                        onClick={onClose}
                        disabled={busyRef.current}
                    >
                        Закрыть
                    </button>
                </header>

                <div className={styles.modalBody}>
                    <div className={posted ? styles.postedNotice : styles.draftNotice}>
                        <strong>{posted ? "Документ проведён" : "Документ в черновике"}</strong>
                        <span>
                            {posted
                                ? `Приход создан${warehouseName ? ` на склад «${warehouseName}»` : ""}. Позиции доступны только для просмотра.`
                                : "Добавьте все позиции и проверьте сумму перед проведением на склад."}
                        </span>
                    </div>

                    {error ? (
                        <div
                            ref={errorRef}
                            className={styles.formError}
                            role="alert"
                            tabIndex="-1"
                        >
                            {error}
                        </div>
                    ) : null}

                    {loading ? (
                        <div className={styles.loadingState} aria-live="polite">
                            <span className={styles.loadingMark} aria-hidden="true" />
                            <div>
                                <strong>Загружаем позиции…</strong>
                                <span>Сверяем ассортимент и закупочные цены.</span>
                            </div>
                        </div>
                    ) : lines.length === 0 ? (
                        <div className={styles.linesEmpty}>
                            <span>0 позиций</span>
                            <h3>Накладная пока пустая</h3>
                            <p>
                                {posted
                                    ? "В проведённом документе не найдено позиций."
                                    : "Выберите продукт ниже, укажите количество и закупочную цену."}
                            </p>
                        </div>
                    ) : (
                        <div className={styles.linesTableWrap}>
                            <table className={styles.linesTable}>
                                <thead>
                                    <tr>
                                        <th>Продукт</th>
                                        <th>Количество</th>
                                        <th>Цена</th>
                                        <th>Сумма</th>
                                        {!posted ? <th><span className={styles.visuallyHidden}>Действия</span></th> : null}
                                    </tr>
                                </thead>
                                <tbody>
                                    {lines.map((line) => (
                                        <tr key={line.consProductId || `${line.productId}-${line.productName}`}>
                                            <td data-label="Продукт">{line.productName}</td>
                                            <td data-label="Количество">{formatQuantity(line.quantity)}</td>
                                            <td data-label="Цена">{formatMoney(line.unitPrice)}</td>
                                            <td data-label="Сумма">{formatMoney(line.lineTotal)}</td>
                                            {!posted ? (
                                                <td className={styles.lineAction} data-label="Действие">
                                                    {pendingDeleteLineId === line.consProductId ? (
                                                        <div className={styles.inlineConfirm}>
                                                            <span>Удалить?</span>
                                                            <button
                                                                type="button"
                                                                onClick={onCancelDeleteLine}
                                                                disabled={deletingLineId != null}
                                                            >
                                                                Нет
                                                            </button>
                                                            <button
                                                                type="button"
                                                                onClick={() => onConfirmDeleteLine(line.consProductId)}
                                                                disabled={deletingLineId != null}
                                                            >
                                                                {deletingLineId === line.consProductId ? "Удаляем…" : "Да"}
                                                            </button>
                                                        </div>
                                                    ) : (
                                                        <button
                                                            type="button"
                                                            className={styles.deleteLineButton}
                                                            onClick={() => onRequestDeleteLine(line.consProductId)}
                                                            aria-label={`Удалить позицию «${line.productName}»`}
                                                        >
                                                            Удалить
                                                        </button>
                                                    )}
                                                </td>
                                            ) : null}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {!posted && !loading ? (
                        <form className={styles.addLineForm} onSubmit={onAddProduct} noValidate>
                            <div className={styles.addLineHeading}>
                                <div>
                                    <p className={styles.sectionKicker}>Новая позиция</p>
                                    <h3>Добавить продукт</h3>
                                </div>
                                <span>{availableProducts.length} в ассортименте</span>
                            </div>

                            <div className={styles.addLineFields}>
                                <label className={styles.field} htmlFor="line-product">
                                    <span>Продукт</span>
                                    <select
                                        id="line-product"
                                        name="lineProductId"
                                        autoComplete="off"
                                        value={productForm.productId}
                                        onChange={(event) => onProductChange("productId", event.target.value)}
                                    >
                                        <option value="">Выберите продукт</option>
                                        {availableProducts.map((product) => (
                                            <option key={product.id} value={product.id}>
                                                {product.name}
                                            </option>
                                        ))}
                                    </select>
                                </label>

                                <label className={styles.field} htmlFor="line-quantity">
                                    <span>Количество</span>
                                    <input
                                        id="line-quantity"
                                        name="lineQuantity"
                                        type="text"
                                        inputMode="decimal"
                                        autoComplete="off"
                                        value={productForm.quantity}
                                        onChange={(event) => onProductChange("quantity", event.target.value)}
                                        placeholder="Например, 2,5…"
                                    />
                                </label>

                                <label className={styles.field} htmlFor="line-price">
                                    <span>Цена за единицу</span>
                                    <input
                                        id="line-price"
                                        name="lineUnitPrice"
                                        type="text"
                                        inputMode="decimal"
                                        autoComplete="off"
                                        value={productForm.unitPrice}
                                        onChange={(event) => onProductChange("unitPrice", event.target.value)}
                                        placeholder="Например, 450…"
                                    />
                                </label>

                                <button type="submit" className={styles.primaryButton} disabled={adding}>
                                    {adding ? "Добавляем…" : "Добавить позицию"}
                                </button>
                            </div>
                        </form>
                    ) : null}
                </div>

                <footer className={styles.modalFooter}>
                    <div className={styles.documentTotal}>
                        <span>Итого по документу</span>
                        <strong>{formatMoney(total)}</strong>
                    </div>

                    <div className={styles.footerActions}>
                        {!posted ? (
                            <label className={styles.warehouseField} htmlFor="post-warehouse">
                                <span>Склад прихода</span>
                                <select
                                    id="post-warehouse"
                                    name="warehouseId"
                                    autoComplete="off"
                                    value={selectedWarehouseId}
                                    onChange={(event) => onWarehouseChange(event.target.value)}
                                >
                                    <option value="">Выберите склад</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.id} value={warehouse.id}>
                                            {warehouse.name}
                                        </option>
                                    ))}
                                </select>
                            </label>
                        ) : null}

                        <Link
                            className={styles.secondaryLink}
                            to={`/consignment-notes/print/${note.consignmentId}`}
                        >
                            Печатная форма
                        </Link>

                        {!posted ? (
                            <button
                                type="button"
                                className={styles.postButton}
                                onClick={onPost}
                                disabled={posting || lines.length === 0}
                            >
                                {posting ? "Проводим…" : "Провести на склад"}
                            </button>
                        ) : null}
                    </div>
                </footer>
            </section>
        </div>
    );
}
