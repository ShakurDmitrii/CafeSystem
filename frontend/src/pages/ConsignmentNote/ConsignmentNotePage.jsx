import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import ConfirmConsignmentDelete from "./consignment-page/ConfirmConsignmentDelete";
import ConsignmentCatalog from "./consignment-page/ConsignmentCatalog";
import ConsignmentCreator from "./consignment-page/ConsignmentCreator";
import ConsignmentHero from "./consignment-page/ConsignmentHero";
import ConsignmentItemsModal from "./consignment-page/ConsignmentItemsModal";
import {
    buildMovementIndex,
    formatMoney,
    getProductId,
    getSupplierId,
    getTodayValue,
    normalizeCollection,
    parseDecimal,
    parseResponseMessage
} from "./consignment-page/consignmentUtils";
import styles from "./ConsignmentNotePage.module.css";

const API_MOVEMENTS = `${API_BASE_URL}/movements`;
const API_CONSIGNMENT = `${API_BASE_URL}/api/consignmentNote`;
const API_CONS_PRODUCT = `${API_BASE_URL}/api/consProduct`;
const API_SUPPLIER = `${API_BASE_URL}/api/supplier`;
const API_PRODUCT = `${API_BASE_URL}/api/product`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const CONSIGNMENT_MOVEMENT_PREFIX = "consignment-note:";

const createDocumentForm = () => ({
    supplierId: "",
    date: getTodayValue()
});

const createProductForm = () => ({
    productId: "",
    quantity: "",
    unitPrice: ""
});

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

export default function ConsignmentNotePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [notes, setNotes] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [movementIndex, setMovementIndex] = useState({});
    const [totalsByNoteId, setTotalsByNoteId] = useState({});
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [statusMessage, setStatusMessage] = useState("");

    const [documentForm, setDocumentForm] = useState(createDocumentForm);
    const [creating, setCreating] = useState(false);
    const [createError, setCreateError] = useState("");

    const [selectedNoteId, setSelectedNoteId] = useState(null);
    const [availableProducts, setAvailableProducts] = useState([]);
    const [lines, setLines] = useState([]);
    const [productForm, setProductForm] = useState(createProductForm);
    const [selectedWarehouseId, setSelectedWarehouseId] = useState("");
    const [modalLoading, setModalLoading] = useState(false);
    const [modalError, setModalError] = useState("");
    const [addingProduct, setAddingProduct] = useState(false);
    const [posting, setPosting] = useState(false);
    const [pendingDeleteLineId, setPendingDeleteLineId] = useState(null);
    const [deletingLineId, setDeletingLineId] = useState(null);

    const [calculatingId, setCalculatingId] = useState(null);
    const [noteToDelete, setNoteToDelete] = useState(null);
    const [deletingNote, setDeletingNote] = useState(false);
    const [deleteError, setDeleteError] = useState("");

    const search = searchParams.get("q") || "";
    const status = searchParams.get("status") || "all";

    const loadPage = useCallback(async () => {
        setLoading(true);
        setPageError("");

        try {
            const responses = await Promise.all([
                fetch(API_CONSIGNMENT),
                fetch(API_SUPPLIER),
                fetch(API_WAREHOUSES),
                fetch(API_MOVEMENTS)
            ]);

            const [notesData, suppliersData, warehousesData, movementsData] = await Promise.all(
                responses.map((response, index) => readJsonResponse(
                    response,
                    [
                        "Не удалось загрузить накладные.",
                        "Не удалось загрузить поставщиков.",
                        "Не удалось загрузить склады.",
                        "Не удалось загрузить складские движения."
                    ][index]
                ))
            );

            const nextNotes = normalizeCollection(notesData)
                .filter((note) => Number(note?.consignmentId) > 0)
                .sort((a, b) => Number(b.consignmentId) - Number(a.consignmentId));
            const nextMovementIndex = buildMovementIndex(
                movementsData,
                CONSIGNMENT_MOVEMENT_PREFIX
            );

            setNotes(nextNotes);
            setSuppliers(normalizeCollection(suppliersData));
            setWarehouses(normalizeCollection(warehousesData));
            setMovementIndex(nextMovementIndex);
            setTotalsByNoteId(Object.fromEntries(
                nextNotes.map((note) => {
                    const noteId = Number(note.consignmentId);
                    return [
                        noteId,
                        nextMovementIndex[noteId]?.total ?? Number(note.amount ?? 0)
                    ];
                })
            ));
        } catch (error) {
            console.error("Не удалось загрузить страницу накладных:", error);
            setPageError(
                error.message
                || "Проверьте подключение к серверу и повторите загрузку."
            );
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadPage();
    }, [loadPage]);

    const supplierRows = useMemo(() => (
        suppliers
            .map((supplier) => ({
                id: getSupplierId(supplier),
                name: supplier?.supplierName || `Поставщик #${getSupplierId(supplier)}`
            }))
            .filter((supplier) => supplier.id > 0)
            .sort((a, b) => a.name.localeCompare(b.name, "ru"))
    ), [suppliers]);

    const supplierNamesById = useMemo(
        () => new Map(supplierRows.map((supplier) => [supplier.id, supplier.name])),
        [supplierRows]
    );

    const warehouseRows = useMemo(() => (
        warehouses
            .map((warehouse) => ({
                id: Number(warehouse?.warehouseId ?? warehouse?.id ?? 0),
                name: warehouse?.warehouseName || `Склад #${warehouse?.warehouseId ?? warehouse?.id}`
            }))
            .filter((warehouse) => warehouse.id > 0)
    ), [warehouses]);

    const warehouseNamesById = useMemo(
        () => new Map(warehouseRows.map((warehouse) => [warehouse.id, warehouse.name])),
        [warehouseRows]
    );

    const noteRows = useMemo(() => (
        notes.map((note) => {
            const id = Number(note.consignmentId);
            const movement = movementIndex[id];
            return {
                id,
                supplierId: Number(note.supplierId),
                supplierName: supplierNamesById.get(Number(note.supplierId))
                    || `Поставщик #${note.supplierId}`,
                date: note.date,
                posted: Boolean(movement?.posted),
                total: totalsByNoteId[id] ?? Number(note.amount ?? 0),
                warehouseName: movement?.warehouseId
                    ? warehouseNamesById.get(Number(movement.warehouseId))
                        || `Склад #${movement.warehouseId}`
                    : "Ещё не выбран",
                source: note
            };
        })
    ), [
        movementIndex,
        notes,
        supplierNamesById,
        totalsByNoteId,
        warehouseNamesById
    ]);

    const filteredRows = useMemo(() => {
        const query = search.trim().toLocaleLowerCase("ru");
        return noteRows.filter((row) => {
            if (status === "draft" && row.posted) return false;
            if (status === "posted" && !row.posted) return false;
            if (!query) return true;
            return row.supplierName.toLocaleLowerCase("ru").includes(query)
                || String(row.id).includes(query);
        });
    }, [noteRows, search, status]);

    const selectedNote = useMemo(
        () => notes.find((note) => Number(note.consignmentId) === selectedNoteId) || null,
        [notes, selectedNoteId]
    );

    const selectedMovement = selectedNoteId ? movementIndex[selectedNoteId] : null;
    const selectedTotal = selectedNoteId
        ? totalsByNoteId[selectedNoteId] ?? 0
        : 0;
    const postedCount = noteRows.filter((row) => row.posted).length;
    const postedTotal = noteRows.reduce(
        (sum, row) => sum + (row.posted ? Number(row.total ?? 0) : 0),
        0
    );

    const updateFilter = (key, value, defaultValue = "") => {
        const next = new URLSearchParams(searchParams);
        if (!value || value === defaultValue) next.delete(key);
        else next.set(key, value);
        setSearchParams(next, { replace: true });
    };

    const handleDocumentChange = (field, value) => {
        setDocumentForm((previous) => ({ ...previous, [field]: value }));
        setCreateError("");
    };

    const createDocument = async (event) => {
        event.preventDefault();
        const supplierId = Number(documentForm.supplierId);
        if (!supplierId) {
            setCreateError("Выберите поставщика для новой накладной.");
            return;
        }
        if (!documentForm.date) {
            setCreateError("Укажите дату поставки.");
            return;
        }

        setCreating(true);
        setCreateError("");
        setStatusMessage("");

        try {
            const response = await fetch(API_CONSIGNMENT, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    supplierId,
                    date: documentForm.date,
                    amount: 0
                })
            });
            const created = await readJsonResponse(
                response,
                "Не удалось создать накладную. Проверьте данные и повторите."
            );

            setNotes((previous) => [created, ...previous]);
            setTotalsByNoteId((previous) => ({
                ...previous,
                [Number(created.consignmentId)]: 0
            }));
            setDocumentForm(createDocumentForm());
            setStatusMessage(`Черновик накладной № ${created.consignmentId} создан.`);
        } catch (error) {
            console.error(error);
            setCreateError(error.message || "Не удалось создать накладную.");
        } finally {
            setCreating(false);
        }
    };

    const loadProductDetails = async (productIds, knownProducts) => {
        const knownIds = new Set(knownProducts.map(getProductId));
        const missingIds = productIds.filter((id) => id > 0 && !knownIds.has(id));
        if (missingIds.length === 0) return knownProducts;

        const missingProducts = await Promise.all(
            missingIds.map(async (productId) => {
                try {
                    const response = await fetch(`${API_PRODUCT}/${productId}`);
                    return await readJsonResponse(response, "");
                } catch {
                    return {
                        productId,
                        productName: `Продукт #${productId}`,
                        productPrice: 0
                    };
                }
            })
        );

        return [...knownProducts, ...missingProducts.filter(Boolean)];
    };

    const openProducts = async (noteId) => {
        const note = notes.find((item) => Number(item.consignmentId) === Number(noteId));
        if (!note) return;

        setSelectedNoteId(Number(noteId));
        setModalLoading(true);
        setModalError("");
        setLines([]);
        setAvailableProducts([]);
        setProductForm(createProductForm());
        setPendingDeleteLineId(null);
        setSelectedWarehouseId(
            movementIndex[noteId]?.warehouseId
                ? String(movementIndex[noteId].warehouseId)
                : ""
        );

        try {
            const [linesResponse, productsResponse] = await Promise.all([
                fetch(`${API_CONS_PRODUCT}/${noteId}`),
                fetch(`${API_PRODUCT}/supplier/${note.supplierId}`)
            ]);
            const [lineData, supplierProductData] = await Promise.all([
                readJsonResponse(linesResponse, "Не удалось загрузить позиции накладной."),
                readJsonResponse(productsResponse, "Не удалось загрузить ассортимент поставщика.")
            ]);

            const rawLines = normalizeCollection(lineData);
            const supplierProducts = normalizeCollection(supplierProductData);
            const productDetails = await loadProductDetails(
                rawLines.map(getProductId),
                supplierProducts
            );
            const productsById = new Map(
                productDetails.map((product) => [getProductId(product), product])
            );
            const movementPrices = movementIndex[noteId]?.priceByProductId || {};

            const normalizedProducts = productDetails
                .map((product) => ({
                    id: getProductId(product),
                    name: product?.productName || `Продукт #${getProductId(product)}`,
                    defaultPrice: Number(product?.productPrice ?? product?.price ?? 0)
                }))
                .filter((product) => product.id > 0)
                .sort((a, b) => a.name.localeCompare(b.name, "ru"));

            const normalizedLines = rawLines.map((line) => {
                const productId = getProductId(line);
                const product = productsById.get(productId);
                const unitPrice = Number(
                    movementPrices[productId]
                    ?? line.GROSS
                    ?? line.gross
                    ?? product?.productPrice
                    ?? product?.price
                    ?? 0
                );
                const quantity = Number(line.quantity ?? 0);
                return {
                    consProductId: Number(line.consProductId ?? 0),
                    productId,
                    productName: line.productName
                        || product?.productName
                        || `Продукт #${productId}`,
                    quantity,
                    unitPrice,
                    lineTotal: quantity * unitPrice
                };
            });

            const total = movementIndex[noteId]?.posted
                ? movementIndex[noteId].total
                : normalizedLines.reduce((sum, line) => sum + line.lineTotal, 0);

            setAvailableProducts(normalizedProducts);
            setLines(normalizedLines);
            setTotalsByNoteId((previous) => ({ ...previous, [noteId]: total }));
        } catch (error) {
            console.error(error);
            setModalError(
                error.message
                || "Не удалось открыть накладную. Повторите попытку."
            );
        } finally {
            setModalLoading(false);
        }
    };

    const closeProducts = () => {
        if (modalLoading || addingProduct || posting || deletingLineId != null) return;
        setSelectedNoteId(null);
        setLines([]);
        setAvailableProducts([]);
        setModalError("");
        setProductForm(createProductForm());
        setPendingDeleteLineId(null);
        setSelectedWarehouseId("");
    };

    const handleProductChange = (field, value) => {
        if (field === "productId") {
            const product = availableProducts.find(
                (item) => item.id === Number(value)
            );
            setProductForm((previous) => ({
                ...previous,
                productId: value,
                unitPrice: product && Number.isFinite(product.defaultPrice)
                    ? String(product.defaultPrice)
                    : previous.unitPrice
            }));
        } else {
            setProductForm((previous) => ({ ...previous, [field]: value }));
        }
        setModalError("");
    };

    const persistTotal = async (noteId, total) => {
        const response = await fetch(`${API_CONSIGNMENT}/${noteId}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ amount: total })
        });
        await readJsonResponse(response, "Не удалось сохранить итог накладной.");
    };

    const addProduct = async (event) => {
        event.preventDefault();
        if (!selectedNoteId || selectedMovement?.posted) return;

        const productId = Number(productForm.productId);
        const quantity = parseDecimal(productForm.quantity);
        const unitPrice = parseDecimal(productForm.unitPrice);
        if (!productId) {
            setModalError("Выберите продукт из ассортимента поставщика.");
            return;
        }
        if (!Number.isFinite(quantity) || quantity <= 0) {
            setModalError("Укажите количество больше 0.");
            return;
        }
        if (!Number.isFinite(unitPrice) || unitPrice < 0) {
            setModalError("Укажите закупочную цену 0 или больше.");
            return;
        }

        const product = availableProducts.find((item) => item.id === productId);
        setAddingProduct(true);
        setModalError("");

        try {
            const response = await fetch(API_CONS_PRODUCT, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    consignmentId: selectedNoteId,
                    productId,
                    quantity,
                    GROSS: unitPrice
                })
            });
            const created = await readJsonResponse(
                response,
                "Не удалось добавить позицию."
            );
            const line = {
                consProductId: Number(created.consProductId),
                productId,
                productName: product?.name || `Продукт #${productId}`,
                quantity,
                unitPrice,
                lineTotal: quantity * unitPrice
            };
            const nextTotal = selectedTotal + line.lineTotal;

            setLines((previous) => [...previous, line]);
            setTotalsByNoteId((previous) => ({
                ...previous,
                [selectedNoteId]: nextTotal
            }));
            setProductForm(createProductForm());
            await persistTotal(selectedNoteId, nextTotal);
        } catch (error) {
            console.error(error);
            setModalError(error.message || "Не удалось добавить позицию.");
        } finally {
            setAddingProduct(false);
        }
    };

    const deleteProduct = async (consProductId) => {
        if (!consProductId || selectedMovement?.posted) return;
        setDeletingLineId(consProductId);
        setModalError("");

        try {
            const response = await fetch(`${API_CONS_PRODUCT}/${consProductId}`, {
                method: "DELETE"
            });
            await readJsonResponse(response, "Не удалось удалить позицию.");

            const nextLines = lines.filter(
                (line) => line.consProductId !== Number(consProductId)
            );
            const nextTotal = nextLines.reduce((sum, line) => sum + line.lineTotal, 0);
            setLines(nextLines);
            setTotalsByNoteId((previous) => ({
                ...previous,
                [selectedNoteId]: nextTotal
            }));
            setPendingDeleteLineId(null);
            await persistTotal(selectedNoteId, nextTotal);
        } catch (error) {
            console.error(error);
            setModalError(error.message || "Не удалось удалить позицию.");
        } finally {
            setDeletingLineId(null);
        }
    };

    const calculateTotalForNote = async (noteId) => {
        setCalculatingId(noteId);
        setPageError("");
        try {
            const response = await fetch(`${API_CONS_PRODUCT}/${noteId}`);
            const data = await readJsonResponse(
                response,
                "Не удалось получить позиции для пересчёта."
            );
            const total = movementIndex[noteId]?.posted
                ? movementIndex[noteId].total
                : normalizeCollection(data).reduce((sum, line) => (
                    sum
                    + Number(line.quantity ?? 0)
                    * Number(line.GROSS ?? line.gross ?? 0)
                ), 0);

            await persistTotal(noteId, total);
            setTotalsByNoteId((previous) => ({ ...previous, [noteId]: total }));
            setStatusMessage(`Итог накладной № ${noteId} обновлён.`);
        } catch (error) {
            console.error(error);
            setPageError(error.message || "Не удалось пересчитать накладную.");
        } finally {
            setCalculatingId(null);
        }
    };

    const postDocument = async () => {
        if (!selectedNoteId || selectedMovement?.posted) return;
        const warehouseId = Number(selectedWarehouseId);
        if (!warehouseId) {
            setModalError("Выберите склад, на который поступит товар.");
            return;
        }
        if (lines.length === 0) {
            setModalError("Добавьте хотя бы одну позицию перед проведением.");
            return;
        }

        setPosting(true);
        setModalError("");
        try {
            await persistTotal(selectedNoteId, selectedTotal);
            const response = await fetch(
                `${API_CONSIGNMENT}/${selectedNoteId}/post`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ warehouseId })
                }
            );
            await readJsonResponse(response, "Не удалось провести накладную.");

            const postedId = selectedNoteId;
            setPosting(false);
            closeProducts();
            await loadPage();
            setStatusMessage(`Накладная № ${postedId} проведена на склад.`);
        } catch (error) {
            console.error(error);
            setModalError(error.message || "Не удалось провести накладную.");
        } finally {
            setPosting(false);
        }
    };

    const requestDeleteNote = (row) => {
        setNoteToDelete(row);
        setDeleteError("");
        setStatusMessage("");
    };

    const closeDeleteNote = () => {
        if (deletingNote) return;
        setNoteToDelete(null);
        setDeleteError("");
    };

    const deleteNote = async () => {
        if (!noteToDelete) return;
        setDeletingNote(true);
        setDeleteError("");

        try {
            const response = await fetch(
                `${API_CONSIGNMENT}/${noteToDelete.id}`,
                { method: "DELETE" }
            );
            await readJsonResponse(response, "Не удалось удалить накладную.");

            const deletedId = noteToDelete.id;
            setNotes((previous) => previous.filter(
                (note) => Number(note.consignmentId) !== deletedId
            ));
            setTotalsByNoteId((previous) => {
                const next = { ...previous };
                delete next[deletedId];
                return next;
            });
            setNoteToDelete(null);
            setStatusMessage(`Черновик накладной № ${deletedId} удалён.`);
        } catch (error) {
            console.error(error);
            setDeleteError(error.message || "Не удалось удалить накладную.");
        } finally {
            setDeletingNote(false);
        }
    };

    return (
        <div className={styles.page}>
            <ConsignmentHero
                totalCount={noteRows.length}
                draftCount={noteRows.length - postedCount}
                postedCount={postedCount}
                monthTotal={formatMoney(postedTotal)}
            />

            {statusMessage ? (
                <div className={styles.statusBanner} role="status" aria-live="polite">
                    <div>
                        <strong>Операция выполнена</strong>
                        <span>{statusMessage}</span>
                    </div>
                    <button
                        type="button"
                        className={styles.statusDismiss}
                        onClick={() => setStatusMessage("")}
                    >
                        Скрыть
                    </button>
                </div>
            ) : null}

            <div className={styles.workspace}>
                <ConsignmentCreator
                    form={documentForm}
                    suppliers={supplierRows}
                    saving={creating}
                    error={createError}
                    onChange={handleDocumentChange}
                    onSubmit={createDocument}
                />

                <ConsignmentCatalog
                    rows={filteredRows}
                    totalCount={noteRows.length}
                    search={search}
                    status={status}
                    loading={loading}
                    error={pageError}
                    calculatingId={calculatingId}
                    onSearchChange={(value) => updateFilter("q", value)}
                    onStatusChange={(value) => updateFilter("status", value, "all")}
                    onRetry={loadPage}
                    onOpen={openProducts}
                    onCalculate={calculateTotalForNote}
                    onDelete={requestDeleteNote}
                />
            </div>

            {selectedNote ? (
                <ConsignmentItemsModal
                    note={selectedNote}
                    supplierName={
                        supplierNamesById.get(Number(selectedNote.supplierId))
                        || `Поставщик #${selectedNote.supplierId}`
                    }
                    posted={Boolean(selectedMovement?.posted)}
                    warehouseName={
                        selectedMovement?.warehouseId
                            ? warehouseNamesById.get(Number(selectedMovement.warehouseId))
                                || `Склад #${selectedMovement.warehouseId}`
                            : ""
                    }
                    selectedWarehouseId={selectedWarehouseId}
                    warehouses={warehouseRows}
                    availableProducts={availableProducts}
                    lines={lines}
                    productForm={productForm}
                    total={selectedTotal}
                    loading={modalLoading}
                    adding={addingProduct}
                    posting={posting}
                    deletingLineId={deletingLineId}
                    pendingDeleteLineId={pendingDeleteLineId}
                    error={modalError}
                    onProductChange={handleProductChange}
                    onAddProduct={addProduct}
                    onRequestDeleteLine={setPendingDeleteLineId}
                    onCancelDeleteLine={() => setPendingDeleteLineId(null)}
                    onConfirmDeleteLine={deleteProduct}
                    onWarehouseChange={(value) => {
                        setSelectedWarehouseId(value);
                        setModalError("");
                    }}
                    onPost={postDocument}
                    onClose={closeProducts}
                />
            ) : null}

            {noteToDelete ? (
                <ConfirmConsignmentDelete
                    note={noteToDelete}
                    deleting={deletingNote}
                    error={deleteError}
                    onConfirm={deleteNote}
                    onClose={closeDeleteNote}
                />
            ) : null}
        </div>
    );
}
