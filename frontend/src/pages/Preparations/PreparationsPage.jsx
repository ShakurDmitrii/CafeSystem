import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import DeletePreparationModal from "./preparations-page/DeletePreparationModal";
import PreparationEditor from "./preparations-page/PreparationEditor";
import PreparationsHero from "./preparations-page/PreparationsHero";
import PreparationsList from "./preparations-page/PreparationsList";
import ProductionModal from "./preparations-page/ProductionModal";
import styles from "./PreparationsPage.module.css";

const API_PREPARATIONS = `${API_BASE_URL}/api/preparations`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_TECH = `${API_BASE_URL}/api/tech-products`;

const formatQuantity = (value) => {
    const num = Number(value ?? 0);
    if (!Number.isFinite(num)) return "0";
    return num.toLocaleString("ru-RU", { maximumFractionDigits: 2 });
};

const formatMoney = (value) => {
    const num = Number(value ?? 0);
    if (!Number.isFinite(num)) return "0";
    return num.toLocaleString("ru-RU", {
        minimumFractionDigits: 0,
        maximumFractionDigits: 2
    });
};

const createPreparationForm = () => ({
    preparationName: "",
    outputWeight: ""
});

const createProductionForm = () => ({
    warehouseId: "",
    batchCount: "1"
});

const parseJsonSafe = (raw) => {
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch {
        return null;
    }
};

const getResponseMessage = (raw, fallback) => {
    const data = parseJsonSafe(raw);
    return data?.message || data?.detail || raw || fallback;
};

const parseDecimal = (value) => {
    if (value == null) return NaN;
    const normalized = String(value).trim().replace(",", ".");
    if (!normalized) return NaN;
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? parsed : NaN;
};

export default function PreparationsPage() {
    const navigate = useNavigate();
    const [preparations, setPreparations] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stocksByPreparation, setStocksByPreparation] = useState({});
    const [techCardCounts, setTechCardCounts] = useState({});
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [statusMessage, setStatusMessage] = useState("");

    const [preparationForm, setPreparationForm] = useState(createPreparationForm);
    const [editingPreparationId, setEditingPreparationId] = useState(null);
    const [preparationSaving, setPreparationSaving] = useState(false);
    const [preparationError, setPreparationError] = useState("");

    const [productionModalOpen, setProductionModalOpen] = useState(false);
    const [selectedPreparation, setSelectedPreparation] = useState(null);
    const [productionForm, setProductionForm] = useState(createProductionForm);
    const [productionLoading, setProductionLoading] = useState(false);
    const [productionError, setProductionError] = useState("");

    const [preparationToDelete, setPreparationToDelete] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);
    const [deleteError, setDeleteError] = useState("");

    const loadStocks = async (warehouseList) => {
        const stockEntries = await Promise.all(
            warehouseList.map(async (warehouse) => {
                try {
                    const res = await fetch(`${API_WAREHOUSES}/${warehouse.warehouseId}/preparations`);
                    if (!res.ok) return [warehouse.warehouseId, []];
                    const data = await res.json().catch(() => []);
                    return [warehouse.warehouseId, Array.isArray(data) ? data : []];
                } catch (err) {
                    console.error(
                        `Ошибка загрузки остатков заготовок для склада ${warehouse.warehouseId}:`,
                        err
                    );
                    return [warehouse.warehouseId, []];
                }
            })
        );

        const grouped = {};
        stockEntries.forEach(([warehouseId, items]) => {
            items.forEach((item) => {
                const preparationId = Number(item?.preparationId);
                if (!preparationId) return;
                if (!grouped[preparationId]) grouped[preparationId] = [];
                grouped[preparationId].push({
                    warehouseId: Number(warehouseId),
                    quantity: Number(item.quantity ?? 0)
                });
            });
        });

        setStocksByPreparation(grouped);
    };

    const loadTechCardCounts = async (preparationsList) => {
        const entries = await Promise.all(
            preparationsList.map(async (preparation) => {
                try {
                    const res = await fetch(
                        `${API_TECH}/preparation/${preparation.preparationId}`
                    );
                    if (!res.ok) return [preparation.preparationId, 0];
                    const data = await res.json().catch(() => []);
                    const items = Array.isArray(data) ? data : (data ? [data] : []);
                    return [preparation.preparationId, items.length];
                } catch (err) {
                    console.error(
                        `Ошибка загрузки техкарты заготовки ${preparation.preparationId}:`,
                        err
                    );
                    return [preparation.preparationId, 0];
                }
            })
        );

        setTechCardCounts(Object.fromEntries(entries));
    };

    const loadPage = useCallback(async () => {
        setLoading(true);
        setPageError("");

        try {
            const [preparationResponse, warehouseResponse] = await Promise.all([
                fetch(API_PREPARATIONS),
                fetch(API_WAREHOUSES)
            ]);

            if (!preparationResponse.ok || !warehouseResponse.ok) {
                throw new Error("Проверьте подключение к серверу и повторите загрузку.");
            }

            const [preparationData, warehouseData] = await Promise.all([
                preparationResponse.json().catch(() => []),
                warehouseResponse.json().catch(() => [])
            ]);
            const nextPreparations = Array.isArray(preparationData) ? preparationData : [];
            const nextWarehouses = Array.isArray(warehouseData) ? warehouseData : [];

            setPreparations(nextPreparations);
            setWarehouses(nextWarehouses);

            await Promise.all([
                loadStocks(nextWarehouses),
                loadTechCardCounts(nextPreparations)
            ]);
        } catch (err) {
            console.error("Ошибка загрузки страницы заготовок:", err);
            setPageError(err.message || "Не удалось получить данные заготовочного цеха.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadPage();
    }, [loadPage]);

    const totalStockByPreparation = useMemo(() => (
        Object.fromEntries(
            preparations.map((preparation) => {
                const total = (stocksByPreparation[preparation.preparationId] || [])
                    .reduce((sum, item) => sum + Number(item.quantity ?? 0), 0);
                return [preparation.preparationId, total];
            })
        )
    ), [preparations, stocksByPreparation]);

    const warehouseNamesById = useMemo(() => (
        new Map(
            warehouses.map((warehouse) => [
                Number(warehouse.warehouseId),
                warehouse.warehouseName
            ])
        )
    ), [warehouses]);

    const preparationRows = useMemo(() => (
        preparations.map((preparation) => {
            const id = preparation.preparationId;
            const totalStock = totalStockByPreparation[id] ?? 0;
            const stocks = (stocksByPreparation[id] || [])
                .filter((stock) => Number(stock.quantity ?? 0) > 0)
                .map((stock) => ({
                    ...stock,
                    warehouseName: warehouseNamesById.get(Number(stock.warehouseId))
                        || `Склад #${stock.warehouseId}`,
                    quantityLabel: `${formatQuantity(stock.quantity)} г`
                }));

            return {
                id,
                name: preparation.preparationName || "Без названия",
                outputLabel: `${formatQuantity(preparation.outputWeight)} г`,
                costLabel: `${formatMoney(preparation.cost)} ₽`,
                totalStock,
                totalStockLabel: totalStock > 0
                    ? `${formatQuantity(totalStock)} г в наличии`
                    : "Нет в наличии",
                techCardCount: techCardCounts[id] ?? 0,
                stocks,
                source: preparation
            };
        })
    ), [
        preparations,
        stocksByPreparation,
        techCardCounts,
        totalStockByPreparation,
        warehouseNamesById
    ]);

    const totalStock = useMemo(
        () => preparationRows.reduce((sum, row) => sum + row.totalStock, 0),
        [preparationRows]
    );

    const resetPreparationEditor = () => {
        setPreparationForm(createPreparationForm());
        setEditingPreparationId(null);
        setPreparationError("");
    };

    const handlePreparationChange = (field, value) => {
        setPreparationForm((previous) => ({ ...previous, [field]: value }));
        setPreparationError("");
    };

    const startEditing = (preparation) => {
        setPreparationForm({
            preparationName: preparation.preparationName ?? "",
            outputWeight: String(preparation.outputWeight ?? "")
        });
        setEditingPreparationId(preparation.preparationId);
        setPreparationError("");
        setStatusMessage("");
        requestAnimationFrame(() => {
            document.getElementById("preparation-editor")?.scrollIntoView({
                block: "start"
            });
        });
    };

    const savePreparation = async () => {
        const trimmedName = preparationForm.preparationName.trim();
        const outputWeight = parseDecimal(preparationForm.outputWeight);

        if (!trimmedName) {
            setPreparationError("Введите название заготовки.");
            return;
        }
        if (!Number.isFinite(outputWeight) || outputWeight <= 0) {
            setPreparationError("Укажите выход одной партии больше 0.");
            return;
        }

        const editing = editingPreparationId != null;
        const endpoint = editing
            ? `${API_PREPARATIONS}/${editingPreparationId}`
            : API_PREPARATIONS;

        setPreparationSaving(true);
        setPreparationError("");
        setStatusMessage("");

        try {
            const response = await fetch(endpoint, {
                method: editing ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    preparationName: trimmedName,
                    outputWeight
                })
            });
            const raw = await response.text();
            const data = parseJsonSafe(raw);

            if (!response.ok) {
                throw new Error(
                    getResponseMessage(
                        raw,
                        editing
                            ? "Не удалось сохранить изменения."
                            : "Не удалось создать заготовку."
                    )
                );
            }

            resetPreparationEditor();
            await loadPage();

            if (!editing && data?.preparationId) {
                navigate(`/preparation-tech-card/${data.preparationId}`);
                return;
            }

            setStatusMessage(`Заготовка «${trimmedName}» обновлена.`);
        } catch (err) {
            console.error(err);
            setPreparationError(err.message || "Не удалось сохранить заготовку.");
        } finally {
            setPreparationSaving(false);
        }
    };

    const openProductionModal = (preparation) => {
        setSelectedPreparation(preparation);
        setProductionForm({
            warehouseId: String(warehouses[0]?.warehouseId ?? ""),
            batchCount: "1"
        });
        setProductionError("");
        setProductionModalOpen(true);
    };

    const closeProductionModal = () => {
        if (productionLoading) return;
        setProductionModalOpen(false);
        setSelectedPreparation(null);
        setProductionError("");
        setProductionForm(createProductionForm());
    };

    const handleProductionChange = (field, value) => {
        setProductionForm((previous) => ({ ...previous, [field]: value }));
        setProductionError("");
    };

    const producePreparation = async () => {
        if (!selectedPreparation) return;

        const warehouseId = Number(productionForm.warehouseId);
        const batchCount = parseDecimal(productionForm.batchCount);

        if (!Number.isFinite(warehouseId) || warehouseId <= 0) {
            setProductionError("Выберите склад списания и прихода.");
            return;
        }
        if (!Number.isFinite(batchCount) || batchCount <= 0) {
            setProductionError("Укажите количество партий больше 0.");
            return;
        }

        setProductionLoading(true);
        setProductionError("");
        setStatusMessage("");

        try {
            const response = await fetch(
                `${API_PREPARATIONS}/${selectedPreparation.preparationId}/produce`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ warehouseId, batchCount })
                }
            );
            const raw = await response.text();
            const data = parseJsonSafe(raw);

            if (!response.ok) {
                throw new Error(
                    getResponseMessage(raw, "Не удалось выпустить заготовку.")
                );
            }

            const preparationName = selectedPreparation.preparationName;
            const producedQuantity = data?.producedQuantity
                ?? Number(selectedPreparation.outputWeight ?? 0) * batchCount;

            await loadPage();
            setProductionModalOpen(false);
            setSelectedPreparation(null);
            setProductionForm(createProductionForm());
            setStatusMessage(
                `Заготовка «${preparationName}» выпущена: ${formatQuantity(producedQuantity)} г.`
            );
        } catch (err) {
            console.error(err);
            setProductionError(err.message || "Не удалось выпустить заготовку.");
        } finally {
            setProductionLoading(false);
        }
    };

    const openDeleteModal = (preparation) => {
        setPreparationToDelete(preparation);
        setDeleteError("");
        setStatusMessage("");
    };

    const closeDeleteModal = () => {
        if (deleteLoading) return;
        setPreparationToDelete(null);
        setDeleteError("");
    };

    const deletePreparation = async () => {
        if (!preparationToDelete) return;

        setDeleteLoading(true);
        setDeleteError("");

        try {
            const response = await fetch(
                `${API_PREPARATIONS}/${preparationToDelete.preparationId}`,
                { method: "DELETE" }
            );
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(
                        raw,
                        "Не удалось удалить заготовку. Проверьте её техкарты и остатки."
                    )
                );
            }

            const deletedName = preparationToDelete.preparationName;
            if (editingPreparationId === preparationToDelete.preparationId) {
                resetPreparationEditor();
            }

            await loadPage();
            setPreparationToDelete(null);
            setStatusMessage(`Заготовка «${deletedName}» удалена.`);
        } catch (err) {
            console.error(err);
            setDeleteError(
                err.message || "Не удалось удалить заготовку. Проверьте связанные данные."
            );
        } finally {
            setDeleteLoading(false);
        }
    };

    return (
        <div className={styles.page}>
            <PreparationsHero
                preparationCount={preparations.length}
                warehouseCount={warehouses.length}
                totalStock={totalStock}
                formatQuantity={formatQuantity}
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
                <PreparationEditor
                    form={preparationForm}
                    editing={editingPreparationId != null}
                    saving={preparationSaving}
                    error={preparationError}
                    onChange={handlePreparationChange}
                    onSubmit={savePreparation}
                    onCancel={resetPreparationEditor}
                />
                <PreparationsList
                    rows={preparationRows}
                    loading={loading}
                    error={pageError}
                    onRetry={loadPage}
                    onProduce={openProductionModal}
                    onEdit={startEditing}
                    onDelete={openDeleteModal}
                />
            </div>

            {productionModalOpen && selectedPreparation ? (
                <ProductionModal
                    preparation={selectedPreparation}
                    warehouses={warehouses}
                    form={productionForm}
                    loading={productionLoading}
                    error={productionError}
                    formatQuantity={formatQuantity}
                    onChange={handleProductionChange}
                    onSubmit={producePreparation}
                    onClose={closeProductionModal}
                />
            ) : null}

            {preparationToDelete ? (
                <DeletePreparationModal
                    preparation={preparationToDelete}
                    loading={deleteLoading}
                    error={deleteError}
                    onConfirm={deletePreparation}
                    onClose={closeDeleteModal}
                />
            ) : null}
        </div>
    );
}
