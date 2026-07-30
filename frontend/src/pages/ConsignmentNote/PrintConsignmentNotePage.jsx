import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import {
    buildMovementIndex,
    formatDate,
    formatMoney,
    formatQuantity,
    getProductId,
    normalizeCollection,
    parseResponseMessage
} from "./consignment-page/consignmentUtils";
import styles from "./PrintConsignmentNotePage.module.css";

const API_CONSIGNMENT = `${API_BASE_URL}/api/consignmentNote`;
const API_CONS_PRODUCT = `${API_BASE_URL}/api/consProduct`;
const API_SUPPLIER = `${API_BASE_URL}/api/supplier`;
const API_PRODUCT = `${API_BASE_URL}/api/product`;
const API_MOVEMENTS = `${API_BASE_URL}/movements`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const CONSIGNMENT_MOVEMENT_PREFIX = "consignment-note:";

const readJsonResponse = async (response, fallback) => {
    const raw = await response.text();
    if (!response.ok) {
        throw new Error(parseResponseMessage(raw, fallback));
    }
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch {
        return null;
    }
};

export default function PrintConsignmentNotePage() {
    const { id } = useParams();
    const noteId = Number(id);
    const [note, setNote] = useState(null);
    const [supplier, setSupplier] = useState(null);
    const [lines, setLines] = useState([]);
    const [movement, setMovement] = useState(null);
    const [warehouseName, setWarehouseName] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [printError, setPrintError] = useState("");

    const loadDocument = useCallback(async () => {
        if (!noteId) {
            setError("Некорректный номер накладной.");
            setLoading(false);
            return;
        }

        setLoading(true);
        setError("");

        try {
            const noteResponse = await fetch(`${API_CONSIGNMENT}/${noteId}`);
            const noteData = await readJsonResponse(
                noteResponse,
                "Накладная не найдена."
            );

            const responses = await Promise.all([
                fetch(`${API_SUPPLIER}/${noteData.supplierId}`),
                fetch(`${API_CONS_PRODUCT}/${noteId}`),
                fetch(`${API_PRODUCT}/supplier/${noteData.supplierId}`),
                fetch(API_MOVEMENTS),
                fetch(API_WAREHOUSES)
            ]);
            const [
                supplierData,
                lineData,
                productData,
                movementData,
                warehouseData
            ] = await Promise.all(
                responses.map((response, index) => readJsonResponse(
                    response,
                    [
                        "Не удалось загрузить поставщика.",
                        "Не удалось загрузить позиции.",
                        "Не удалось загрузить ассортимент.",
                        "Не удалось загрузить движения.",
                        "Не удалось загрузить склады."
                    ][index]
                ))
            );

            const movementEntry = buildMovementIndex(
                movementData,
                CONSIGNMENT_MOVEMENT_PREFIX
            )[noteId] || null;
            const products = normalizeCollection(productData);
            const productsById = new Map(
                products.map((product) => [getProductId(product), product])
            );
            const normalizedLines = normalizeCollection(lineData).map((line, index) => {
                const productId = getProductId(line);
                const product = productsById.get(productId);
                const quantity = Number(line.quantity ?? 0);
                const unitPrice = Number(
                    movementEntry?.priceByProductId?.[productId]
                    ?? line.GROSS
                    ?? line.gross
                    ?? product?.productPrice
                    ?? product?.price
                    ?? 0
                );

                return {
                    id: Number(line.consProductId ?? 0) || `${productId}-${index}`,
                    productId,
                    name: line.productName
                        || product?.productName
                        || `Продукт #${productId}`,
                    quantity,
                    unitPrice,
                    total: quantity * unitPrice
                };
            });

            const warehouses = normalizeCollection(warehouseData);
            const targetWarehouse = warehouses.find(
                (warehouse) => Number(warehouse?.warehouseId ?? warehouse?.id)
                    === Number(movementEntry?.warehouseId)
            );

            setNote(noteData);
            setSupplier(supplierData);
            setLines(normalizedLines);
            setMovement(movementEntry);
            setWarehouseName(
                targetWarehouse?.warehouseName
                || (movementEntry?.warehouseId ? `Склад #${movementEntry.warehouseId}` : "")
            );
        } catch (loadError) {
            console.error(loadError);
            setError(
                loadError.message
                || "Не удалось собрать печатную форму. Повторите загрузку."
            );
        } finally {
            setLoading(false);
        }
    }, [noteId]);

    useEffect(() => {
        loadDocument();
    }, [loadDocument]);

    const total = useMemo(
        () => movement?.posted
            ? Number(movement.total ?? 0)
            : lines.reduce((sum, line) => sum + line.total, 0),
        [lines, movement]
    );

    const printDocument = () => {
        if (lines.length === 0) {
            setPrintError("Добавьте позиции в накладную перед печатью.");
            return;
        }

        setPrintError("");
        window.print();
    };

    if (loading) {
        return (
            <div className={styles.stateCard} aria-live="polite">
                <span className={styles.loadingMark} aria-hidden="true" />
                <div>
                    <strong>Собираем печатную форму…</strong>
                    <p>Загружаем реквизиты, позиции и закупочные цены.</p>
                </div>
            </div>
        );
    }

    if (error || !note) {
        return (
            <div className={styles.stateCard} role="alert">
                <div>
                    <strong>Печатная форма недоступна</strong>
                    <p>{error || "Накладная не найдена."}</p>
                </div>
                <div className={styles.stateActions}>
                    <button type="button" onClick={loadDocument}>Повторить загрузку</button>
                    <Link to="/consigment">Вернуться в журнал</Link>
                </div>
            </div>
        );
    }

    return (
        <div className={styles.page}>
            <header className={styles.pageHeader}>
                <div>
                    <p className={styles.eyebrow}>Просмотр документа</p>
                    <h1>Печатная форма накладной</h1>
                    <p>
                        Проверьте поставщика, дату, позиции и итог перед отправкой
                        в сервис печати.
                    </p>
                </div>
                <div className={styles.headerActions}>
                    <Link className={styles.secondaryLink} to="/consigment">
                        Вернуться в журнал
                    </Link>
                    <button
                        type="button"
                        className={styles.printButton}
                        onClick={printDocument}
                        disabled={lines.length === 0}
                    >
                        Печать
                    </button>
                </div>
            </header>

            {printError ? (
                <div className={styles.errorBanner} role="alert">
                    <strong>Печать не выполнена</strong>
                    <span>{printError}</span>
                </div>
            ) : null}

            <article className={styles.paper} aria-labelledby="document-title">
                <div className={styles.paperEdge} aria-hidden="true" />
                <header className={styles.documentHeader}>
                    <div>
                        <p>Приходная накладная</p>
                        <h2 id="document-title">№ {note.consignmentId}</h2>
                    </div>
                    <span className={movement?.posted ? styles.postedBadge : styles.draftBadge}>
                        {movement?.posted ? "Проведена" : "Черновик"}
                    </span>
                </header>

                <dl className={styles.documentFacts}>
                    <div>
                        <dt>Поставщик</dt>
                        <dd>{supplier?.supplierName || `Поставщик #${note.supplierId}`}</dd>
                    </div>
                    <div>
                        <dt>Контакт</dt>
                        <dd>{supplier?.communication || "Не указан"}</dd>
                    </div>
                    <div>
                        <dt>Дата поставки</dt>
                        <dd>{formatDate(note.date)}</dd>
                    </div>
                    <div>
                        <dt>Склад прихода</dt>
                        <dd>{warehouseName || "Не выбран"}</dd>
                    </div>
                </dl>

                {lines.length === 0 ? (
                    <div className={styles.emptyLines}>
                        <strong>В накладной нет позиций</strong>
                        <p>Вернитесь в журнал и добавьте продукты перед печатью.</p>
                    </div>
                ) : (
                    <div className={styles.tableWrap}>
                        <table className={styles.documentTable}>
                            <thead>
                                <tr>
                                    <th>№</th>
                                    <th>Наименование</th>
                                    <th>Количество</th>
                                    <th>Цена</th>
                                    <th>Сумма</th>
                                </tr>
                            </thead>
                            <tbody>
                                {lines.map((line, index) => (
                                    <tr key={line.id}>
                                        <td data-label="№">{index + 1}</td>
                                        <td data-label="Наименование">{line.name}</td>
                                        <td data-label="Количество">{formatQuantity(line.quantity)}</td>
                                        <td data-label="Цена">{formatMoney(line.unitPrice)}</td>
                                        <td data-label="Сумма">{formatMoney(line.total)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                <footer className={styles.documentFooter}>
                    <div className={styles.signatures}>
                        <span>Принял</span>
                        <span>Передал</span>
                    </div>
                    <div className={styles.total}>
                        <span>Итого</span>
                        <strong>{formatMoney(total)}</strong>
                    </div>
                </footer>
            </article>
        </div>
    );
}
