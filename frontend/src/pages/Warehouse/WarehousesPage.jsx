import { useCallback, useEffect, useMemo, useState } from "react";
import { API_BASE_URL } from "../../auth";
import InventoryShiftReport from "./InventoryShiftReport";
import WarehouseHero from "./warehouse-page/WarehouseHero";
import WarehouseSetup from "./warehouse-page/WarehouseSetup";
import TransferPanel from "./warehouse-page/TransferPanel";
import WarehouseCard from "./warehouse-page/WarehouseCard";
import {
    buildIncomingPriceMaps,
    getProductId,
    getSafeUnitFactor,
    normalizeMovementPayload,
    normalizeCollection,
    parseDecimal
} from "./warehouse-page/warehouseUtils";
import styles from "./WarehousePage.module.css";

const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_MOVEMENTS = `${API_BASE_URL}/movements`;
const API_SUPPLIERS = `${API_BASE_URL}/api/supplier`;
const API_PRODUCTS = `${API_BASE_URL}/api/product`;

const EMPTY_NEW_PRODUCT = {
    productName: "",
    supplierId: "",
    productPrice: "",
    waste: "",
    quantity: ""
};

export default function WarehousePage() {
    const [warehouses, setWarehouses] = useState([]);
    const [catalogProducts, setCatalogProducts] = useState([]);
    const [suppliers, setSuppliers] = useState([]);
    const [movements, setMovements] = useState([]);
    const [warehouseProducts, setWarehouseProducts] = useState({});
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [notice, setNotice] = useState("");
    const [warehouseName, setWarehouseName] = useState("");
    const [editingId, setEditingId] = useState(null);
    const [savingWarehouse, setSavingWarehouse] = useState(false);
    const [showZeroStock, setShowZeroStock] = useState(false);
    const [openStockPanel, setOpenStockPanel] = useState(null);
    const [catalogSearch, setCatalogSearch] = useState("");
    const [catalogForm, setCatalogForm] = useState({ productId: "", quantity: "", unitPrice: "" });
    const [newProductForm, setNewProductForm] = useState(EMPTY_NEW_PRODUCT);
    const [stockInputs, setStockInputs] = useState({});
    const [busyKey, setBusyKey] = useState("");
    const [transferForm, setTransferForm] = useState({
        fromWarehouseId: "",
        toWarehouseId: "",
        productId: "",
        quantity: ""
    });

    const supplierNamesById = useMemo(() => new Map(
        suppliers.map((supplier) => [
            Number(supplier?.supplierId ?? supplier?.supplierID ?? supplier?.id),
            supplier?.supplierName ?? supplier?.name ?? "—"
        ])
    ), [suppliers]);

    const productMetaById = useMemo(() => new Map(
        catalogProducts.map((product) => [getProductId(product), product])
    ), [catalogProducts]);

    const { averageMap, latestMap } = useMemo(
        () => buildIncomingPriceMaps(movements),
        [movements]
    );

    const hydrateStocks = useCallback(async (warehouseList, products, supplierMap, averages, latest) => {
        const productMap = new Map(products.map((product) => [getProductId(product), product]));
        const entries = await Promise.all(warehouseList.map(async (warehouse) => {
            const warehouseId = Number(warehouse.warehouseId);
            try {
                const response = await fetch(`${API_WAREHOUSES}/${warehouseId}/products`);
                if (!response.ok) throw new Error("Не удалось загрузить остатки");
                const rows = normalizeCollection(await response.json().catch(() => []));
                return [warehouseId, rows.map((row) => {
                    const productId = Number(row.productId);
                    const product = productMap.get(productId) ?? {};
                    const priceKey = `${warehouseId}-${productId}`;
                    const unitFactor = getSafeUnitFactor(product.unitFactor);
                    const averageBasePrice = averages[priceKey];
                    return {
                        ...product,
                        ...row,
                        productId,
                        productName: product.productName ?? `Товар #${productId}`,
                        quantityBase: Number(row.quantity ?? 0),
                        quantityDisplay: Number(row.quantity ?? 0) / unitFactor,
                        supplierName: supplierMap.get(Number(product.supplierId ?? product.supplierID)) ?? "—",
                        averagePrice: averageBasePrice == null
                            ? Number(product.productPrice ?? 0)
                            : averageBasePrice * unitFactor,
                        latestPrice: latest[priceKey] ?? null
                    };
                })];
            } catch (error) {
                console.error(error);
                return [warehouseId, []];
            }
        }));
        setWarehouseProducts(Object.fromEntries(entries));
    }, []);

    const loadData = useCallback(async () => {
        setLoading(true);
        setPageError("");
        try {
            const [warehouseResponse, supplierResponse, productResponse, movementResponse] = await Promise.all([
                fetch(API_WAREHOUSES),
                fetch(API_SUPPLIERS),
                fetch(API_PRODUCTS),
                fetch(API_MOVEMENTS)
            ]);
            if (!warehouseResponse.ok) throw new Error("Не удалось загрузить склады.");
            if (!productResponse.ok) throw new Error("Не удалось загрузить каталог товаров.");

            const [warehouseData, supplierData, productData, movementData] = await Promise.all([
                warehouseResponse.json().catch(() => []),
                supplierResponse.ok ? supplierResponse.json().catch(() => []) : [],
                productResponse.json().catch(() => []),
                movementResponse.ok ? movementResponse.json().catch(() => []) : []
            ]);
            const nextWarehouses = normalizeCollection(warehouseData);
            const nextSuppliers = normalizeCollection(supplierData);
            const nextProducts = normalizeCollection(productData);
            const nextMovements = normalizeCollection(movementData);
            const nextSupplierMap = new Map(nextSuppliers.map((supplier) => [
                Number(supplier?.supplierId ?? supplier?.supplierID ?? supplier?.id),
                supplier?.supplierName ?? supplier?.name ?? "—"
            ]));
            const prices = buildIncomingPriceMaps(nextMovements);

            setWarehouses(nextWarehouses);
            setSuppliers(nextSuppliers);
            setCatalogProducts(nextProducts);
            setMovements(nextMovements);
            await hydrateStocks(
                nextWarehouses,
                nextProducts,
                nextSupplierMap,
                prices.averageMap,
                prices.latestMap
            );
        } catch (error) {
            console.error(error);
            setPageError(error.message || "Не удалось загрузить складские данные.");
        } finally {
            setLoading(false);
        }
    }, [hydrateStocks]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const warehouseStats = useMemo(() => {
        let positions = 0;
        let lowStock = 0;
        Object.values(warehouseProducts).forEach((rows) => {
            rows.forEach((row) => {
                positions += 1;
                if (Number(row.quantityBase ?? 0) <= 0) lowStock += 1;
            });
        });
        return { positions, lowStock };
    }, [warehouseProducts]);

    const transferProducts = useMemo(() => {
        const sourceId = Number(transferForm.fromWarehouseId);
        return (warehouseProducts[sourceId] ?? []).filter((product) => Number(product.quantityBase) > 0);
    }, [transferForm.fromWarehouseId, warehouseProducts]);

    const filteredCatalog = useMemo(() => {
        const term = catalogSearch.trim().toLocaleLowerCase("ru-RU");
        return [...catalogProducts]
            .sort((a, b) => String(a.productName ?? "").localeCompare(String(b.productName ?? ""), "ru"))
            .filter((product) => {
                if (!term) return true;
                const supplierName = supplierNamesById.get(Number(product.supplierId ?? product.supplierID)) ?? "";
                return `${product.productName ?? ""} ${supplierName} ${getProductId(product)}`
                    .toLocaleLowerCase("ru-RU")
                    .includes(term);
            });
    }, [catalogProducts, catalogSearch, supplierNamesById]);

    const showNotice = (message) => {
        setNotice(message);
        window.setTimeout(() => setNotice(""), 4000);
    };

    const saveWarehouse = async (event) => {
        event.preventDefault();
        const name = warehouseName.trim();
        if (!name) {
            setPageError("Введите название склада.");
            return;
        }
        setSavingWarehouse(true);
        setPageError("");
        try {
            const response = await fetch(
                editingId ? `${API_WAREHOUSES}/${editingId}` : API_WAREHOUSES,
                {
                    method: editingId ? "PUT" : "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ warehouseName: name })
                }
            );
            if (!response.ok) throw new Error("Не удалось сохранить склад.");
            showNotice(editingId ? "Название склада обновлено." : "Склад создан.");
            setWarehouseName("");
            setEditingId(null);
            await loadData();
        } catch (error) {
            setPageError(error.message);
        } finally {
            setSavingWarehouse(false);
        }
    };

    const deleteWarehouse = async (warehouse) => {
        if (!window.confirm(`Удалить склад «${warehouse.warehouseName}»?`)) return;
        setBusyKey(`warehouse-${warehouse.warehouseId}`);
        try {
            const response = await fetch(`${API_WAREHOUSES}/${warehouse.warehouseId}`, { method: "DELETE" });
            if (!response.ok) throw new Error("Не удалось удалить склад. Проверьте связанные остатки.");
            showNotice("Склад удалён.");
            await loadData();
        } catch (error) {
            setPageError(error.message);
        } finally {
            setBusyKey("");
        }
    };

    const setMainWarehouse = async (warehouseId) => {
        setBusyKey(`warehouse-${warehouseId}`);
        try {
            const response = await fetch(`${API_WAREHOUSES}/${warehouseId}/main`, { method: "PUT" });
            if (!response.ok) throw new Error("Не удалось назначить главный склад.");
            showNotice("Главный склад изменён.");
            await loadData();
        } catch (error) {
            setPageError(error.message);
        } finally {
            setBusyKey("");
        }
    };

    const createMovement = async (payload, successMessage, operationKey) => {
        setBusyKey(operationKey);
        setPageError("");
        try {
            const response = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error("Операция не проведена. Проверьте остаток, цену и выбранные склады.");
            showNotice(successMessage);
            await loadData();
            return true;
        } catch (error) {
            setPageError(error.message);
            return false;
        } finally {
            setBusyKey("");
        }
    };

    const submitTransfer = async (event) => {
        event.preventDefault();
        const product = transferProducts.find(
            (row) => String(row.productId) === String(transferForm.productId)
        );
        const quantity = parseDecimal(transferForm.quantity);
        if (!product || !Number.isFinite(quantity) || quantity <= 0) {
            setPageError("Выберите товар и укажите положительное количество.");
            return;
        }
        const normalized = normalizeMovementPayload(product, quantity, Number(product.averagePrice ?? 0));
        const ok = await createMovement({
            docType: "movement",
            fromWarehouseId: Number(transferForm.fromWarehouseId),
            toWarehouseId: Number(transferForm.toWarehouseId),
            productId: Number(product.productId),
            quantity: normalized.quantity,
            unitPrice: normalized.unitPrice,
            comment: "warehouse-transfer",
            createdBy: "warehouse-ui"
        }, "Перемещение проведено и записано в журнал.", "transfer");
        if (ok) setTransferForm({ fromWarehouseId: "", toWarehouseId: "", productId: "", quantity: "" });
    };

    const submitCatalogReceipt = async (warehouseId) => {
        const product = productMetaById.get(Number(catalogForm.productId));
        const quantity = parseDecimal(catalogForm.quantity);
        const unitPrice = parseDecimal(catalogForm.unitPrice);
        if (!product || !Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(unitPrice) || unitPrice <= 0) {
            setPageError("Выберите товар и укажите положительные количество и цену прихода.");
            return;
        }
        const normalized = normalizeMovementPayload(product, quantity, unitPrice);
        const ok = await createMovement({
            docType: "receipt",
            toWarehouseId: Number(warehouseId),
            supplierId: Number(product.supplierId ?? product.supplierID),
            productId: getProductId(product),
            quantity: normalized.quantity,
            unitPrice: normalized.unitPrice,
            comment: `warehouse-existing-add:${warehouseId}`,
            createdBy: "warehouse-ui"
        }, "Приход проведён и добавлен в остаток.", `catalog-${warehouseId}`);
        if (ok) {
            setOpenStockPanel(null);
            setCatalogSearch("");
            setCatalogForm({ productId: "", quantity: "", unitPrice: "" });
        }
    };

    const submitNewProduct = async (warehouseId) => {
        const quantity = parseDecimal(newProductForm.quantity);
        const price = parseDecimal(newProductForm.productPrice);
        const waste = parseDecimal(newProductForm.waste);
        if (!newProductForm.productName.trim() || !newProductForm.supplierId
            || !Number.isFinite(quantity) || quantity <= 0
            || !Number.isFinite(price) || price <= 0 || !Number.isFinite(waste)) {
            setPageError("Заполните название, поставщика, цену, отход и количество.");
            return;
        }
        setBusyKey(`new-${warehouseId}`);
        try {
            const response = await fetch(API_PRODUCTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    supplierId: Number(newProductForm.supplierId),
                    productName: newProductForm.productName.trim(),
                    productPrice: price,
                    waste,
                    isFavorite: false
                })
            });
            if (!response.ok) throw new Error("Не удалось создать продукт.");
            const created = await response.json();
            const movementResponse = await fetch(API_MOVEMENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    docType: "receipt",
                    toWarehouseId: Number(warehouseId),
                    supplierId: Number(newProductForm.supplierId),
                    productId: getProductId(created),
                    quantity,
                    unitPrice: price,
                    comment: `warehouse-manual-add:${warehouseId}`,
                    createdBy: "warehouse-ui"
                })
            });
            if (!movementResponse.ok) throw new Error("Продукт создан, но приход не проведён.");
            setNewProductForm(EMPTY_NEW_PRODUCT);
            setOpenStockPanel(null);
            showNotice("Новый продукт создан и принят на склад.");
            await loadData();
        } catch (error) {
            setPageError(error.message);
        } finally {
            setBusyKey("");
        }
    };

    const adjustStock = async (warehouseId, product, direction) => {
        const key = `${warehouseId}-${product.productId}`;
        const values = stockInputs[key] ?? {};
        const quantity = parseDecimal(values.quantity);
        const enteredPrice = parseDecimal(values.unitPrice);
        const fallbackPrice = Number(product.averagePrice ?? product.productPrice ?? 0);
        const unitPrice = Number.isFinite(enteredPrice) && enteredPrice > 0 ? enteredPrice : fallbackPrice;
        if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(unitPrice) || unitPrice < 0) {
            setPageError("Укажите положительное количество. Для прихода также проверьте цену.");
            return;
        }
        const normalized = normalizeMovementPayload(product, quantity, unitPrice);
        const ok = await createMovement({
            docType: direction === "in" ? "receipt" : "writeoff",
            fromWarehouseId: direction === "out" ? Number(warehouseId) : null,
            toWarehouseId: direction === "in" ? Number(warehouseId) : null,
            supplierId: Number(product.supplierId ?? product.supplierID) || null,
            productId: Number(product.productId),
            quantity: normalized.quantity,
            unitPrice: normalized.unitPrice,
            comment: direction === "in" ? "warehouse-adjust-receipt" : "warehouse-adjust-writeoff",
            createdBy: "warehouse-ui"
        }, direction === "in" ? "Приход проведён." : "Списание проведено.", `stock-${key}`);
        if (ok) setStockInputs((previous) => ({ ...previous, [key]: { quantity: "", unitPrice: "" } }));
    };

    return (
        <div className={styles.page}>
            <WarehouseHero
                warehouses={warehouses}
                warehouseProducts={warehouseProducts}
                positions={warehouseStats.positions}
                lowStock={warehouseStats.lowStock}
            />

            {notice ? <div className={styles.notice} role="status" aria-live="polite">{notice}</div> : null}
            {pageError ? (
                <div className={styles.errorBanner} role="alert">
                    <span>{pageError}</span>
                    <button type="button" onClick={() => setPageError("")}>Закрыть</button>
                </div>
            ) : null}

            <section className={styles.operationsGrid} aria-label="Операции со складами">
                <WarehouseSetup
                    warehouseName={warehouseName}
                    editingId={editingId}
                    saving={savingWarehouse}
                    onNameChange={setWarehouseName}
                    onSubmit={saveWarehouse}
                    onCancel={() => {
                        setWarehouseName("");
                        setEditingId(null);
                    }}
                />
                <TransferPanel
                    warehouses={warehouses}
                    products={transferProducts}
                    form={transferForm}
                    busy={busyKey === "transfer"}
                    onChange={(field, value) => setTransferForm((previous) => ({
                        ...previous,
                        [field]: value,
                        ...(field === "fromWarehouseId" ? { productId: "" } : {})
                    }))}
                    onSubmit={submitTransfer}
                />
            </section>

            <section className={styles.stockSection} aria-labelledby="stock-heading">
                <div className={styles.sectionHeader}>
                    <div>
                        <p className={styles.kicker}>Карта хранения</p>
                        <h2 id="stock-heading">Остатки по складам</h2>
                        <p>Каждый приход и списание проходит через журнал движений.</p>
                    </div>
                    <label className={styles.switchLabel}>
                        <input
                            type="checkbox"
                            checked={showZeroStock}
                            onChange={(event) => setShowZeroStock(event.target.checked)}
                        />
                        Показывать нулевые остатки
                    </label>
                </div>

                {loading ? (
                    <div className={styles.loadingState} role="status">Загружаем остатки…</div>
                ) : warehouses.length === 0 ? (
                    <div className={styles.emptyState}>Создайте первый склад — после этого здесь появятся остатки.</div>
                ) : (
                    <div className={styles.warehouseGrid}>
                        {warehouses.map((warehouse) => (
                            <WarehouseCard
                                key={warehouse.warehouseId}
                                warehouse={warehouse}
                                products={warehouseProducts[warehouse.warehouseId] ?? []}
                                showZeroStock={showZeroStock}
                                busyKey={busyKey}
                                openPanel={openStockPanel?.warehouseId === warehouse.warehouseId ? openStockPanel.mode : ""}
                                catalogProducts={filteredCatalog}
                                suppliers={suppliers}
                                catalogSearch={catalogSearch}
                                catalogForm={catalogForm}
                                newProductForm={newProductForm}
                                stockInputs={stockInputs}
                                averageMap={averageMap}
                                latestMap={latestMap}
                                onEdit={() => {
                                    setWarehouseName(warehouse.warehouseName);
                                    setEditingId(warehouse.warehouseId);
                                    window.scrollTo({ top: 0, behavior: "smooth" });
                                }}
                                onDelete={() => deleteWarehouse(warehouse)}
                                onSetMain={() => setMainWarehouse(warehouse.warehouseId)}
                                onOpenPanel={(mode) => {
                                    setOpenStockPanel({ warehouseId: warehouse.warehouseId, mode });
                                    setPageError("");
                                }}
                                onClosePanel={() => setOpenStockPanel(null)}
                                onCatalogSearch={setCatalogSearch}
                                onCatalogForm={setCatalogForm}
                                onNewProductForm={setNewProductForm}
                                onCatalogSubmit={() => submitCatalogReceipt(warehouse.warehouseId)}
                                onNewProductSubmit={() => submitNewProduct(warehouse.warehouseId)}
                                onStockInput={(key, value) => setStockInputs((previous) => ({
                                    ...previous,
                                    [key]: { ...(previous[key] ?? {}), ...value }
                                }))}
                                onAdjustStock={(product, direction) => adjustStock(warehouse.warehouseId, product, direction)}
                            />
                        ))}
                    </div>
                )}
            </section>

            <InventoryShiftReport warehouses={warehouses} onApplied={loadData} />
        </div>
    );
}
