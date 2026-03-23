import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import styles from "./PreparationsPage.module.css";

const API_PREPARATIONS = `${API_BASE_URL}/api/preparations`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_TECH = `${API_BASE_URL}/api/tech-products`;

const formatQty = (value) => {
    const num = Number(value ?? 0);
    if (!Number.isFinite(num)) return "0";
    return num.toLocaleString("ru-RU", { maximumFractionDigits: 2 });
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

export default function PreparationsPage() {
    const navigate = useNavigate();
    const [preparations, setPreparations] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stocksByPreparation, setStocksByPreparation] = useState({});
    const [techCardCounts, setTechCardCounts] = useState({});
    const [loading, setLoading] = useState(true);
    const [createForm, setCreateForm] = useState(createPreparationForm);
    const [createLoading, setCreateLoading] = useState(false);
    const [createError, setCreateError] = useState("");
    const [productionModalOpen, setProductionModalOpen] = useState(false);
    const [selectedPreparation, setSelectedPreparation] = useState(null);
    const [productionForm, setProductionForm] = useState(createProductionForm);
    const [productionLoading, setProductionLoading] = useState(false);
    const [productionError, setProductionError] = useState("");

    const loadStocks = async (warehouseList) => {
        const stockEntries = await Promise.all(
            warehouseList.map(async (warehouse) => {
                try {
                    const res = await fetch(`${API_WAREHOUSES}/${warehouse.warehouseId}/preparations`);
                    if (!res.ok) return [warehouse.warehouseId, []];
                    const data = await res.json().catch(() => []);
                    return [warehouse.warehouseId, Array.isArray(data) ? data : []];
                } catch (err) {
                    console.error(`Ошибка загрузки остатков заготовок для склада ${warehouse.warehouseId}:`, err);
                    return [warehouse.warehouseId, []];
                }
            })
        );

        const grouped = {};
        stockEntries.forEach(([warehouseId, items]) => {
            items.forEach((item) => {
                const preparationId = item?.preparationId;
                if (!preparationId) return;
                if (!grouped[preparationId]) grouped[preparationId] = [];
                grouped[preparationId].push({
                    warehouseId,
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
                    const res = await fetch(`${API_TECH}/preparation/${preparation.preparationId}`);
                    if (!res.ok) return [preparation.preparationId, 0];
                    const data = await res.json().catch(() => []);
                    const items = Array.isArray(data) ? data : (data ? [data] : []);
                    return [preparation.preparationId, items.length];
                } catch (err) {
                    console.error(`Ошибка загрузки техкарты заготовки ${preparation.preparationId}:`, err);
                    return [preparation.preparationId, 0];
                }
            })
        );

        setTechCardCounts(Object.fromEntries(entries));
    };

    const loadPage = useCallback(async () => {
        setLoading(true);
        try {
            const [prepRes, warehouseRes] = await Promise.all([
                fetch(API_PREPARATIONS),
                fetch(API_WAREHOUSES)
            ]);

            const prepData = prepRes.ok ? await prepRes.json().catch(() => []) : [];
            const warehouseData = warehouseRes.ok ? await warehouseRes.json().catch(() => []) : [];
            const nextPreparations = Array.isArray(prepData) ? prepData : [];
            const nextWarehouses = Array.isArray(warehouseData) ? warehouseData : [];

            setPreparations(nextPreparations);
            setWarehouses(nextWarehouses);

            await Promise.all([
                loadStocks(nextWarehouses),
                loadTechCardCounts(nextPreparations)
            ]);
        } catch (err) {
            console.error("Ошибка загрузки страницы заготовок:", err);
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

    const handleCreatePreparation = async () => {
        const trimmedName = createForm.preparationName.trim();
        const outputWeight = Number(createForm.outputWeight);

        if (!trimmedName) {
            setCreateError("Введите название заготовки");
            return;
        }

        if (!Number.isFinite(outputWeight) || outputWeight <= 0) {
            setCreateError("Укажите корректный выход заготовки");
            return;
        }

        setCreateLoading(true);
        setCreateError("");

        try {
            const res = await fetch(API_PREPARATIONS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    preparationName: trimmedName,
                    outputWeight
                })
            });

            const text = await res.text();
            const data = parseJsonSafe(text);
            if (!res.ok) {
                throw new Error(data?.message || text || `Ошибка создания заготовки (${res.status})`);
            }

            setCreateForm(createPreparationForm());
            await loadPage();
            if (data?.preparationId) {
                navigate(`/preparation-tech-card/${data.preparationId}`);
            }
        } catch (err) {
            console.error(err);
            setCreateError(err.message || "Не удалось создать заготовку");
        } finally {
            setCreateLoading(false);
        }
    };

    const handleProduce = async () => {
        if (!selectedPreparation) return;

        const warehouseId = Number(productionForm.warehouseId);
        const batchCount = Number(productionForm.batchCount);

        if (!Number.isFinite(warehouseId) || warehouseId <= 0) {
            setProductionError("Выберите склад");
            return;
        }

        if (!Number.isFinite(batchCount) || batchCount <= 0) {
            setProductionError("Укажите количество партий");
            return;
        }

        setProductionLoading(true);
        setProductionError("");

        try {
            const res = await fetch(`${API_PREPARATIONS}/${selectedPreparation.preparationId}/produce`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    warehouseId,
                    batchCount
                })
            });

            const raw = await res.text();
            const data = parseJsonSafe(raw);
            if (!res.ok) {
                throw new Error(data?.message || raw || `Ошибка выпуска (${res.status})`);
            }

            await loadPage();
            closeProductionModal();
            window.alert(
                `Заготовка "${selectedPreparation.preparationName}" выпущена: ${formatQty(data?.producedQuantity)} г`
            );
        } catch (err) {
            console.error(err);
            setProductionError(err.message || "Не удалось выпустить заготовку");
        } finally {
            setProductionLoading(false);
        }
    };

    return (
        <div className={styles.page}>
            <section className={styles.hero}>
                <div>
                    <p className={styles.eyebrow}>Заготовки</p>
                    <h1 className={styles.title}>Центр заготовок и выпуска</h1>
                    <p className={styles.subtitle}>
                        Создавайте соусы, основы и полуфабрикаты, ведите для них техкарты и выпускайте партии сразу на нужный склад.
                    </p>
                </div>
                <div className={styles.heroNote}>
                    При выпуске ингредиенты списываются с выбранного склада, а готовая заготовка сразу приходуется туда же.
                </div>
            </section>

            <section className={styles.createCard}>
                <div className={styles.sectionHeading}>
                    <div>
                        <h2>Новая заготовка</h2>
                        <p>После создания сразу откроем техкарту, чтобы можно было задать состав.</p>
                    </div>
                </div>

                <div className={styles.createGrid}>
                    <label className={styles.field}>
                        <span>Название</span>
                        <input
                            type="text"
                            className={styles.input}
                            placeholder="Например, соус бургер"
                            value={createForm.preparationName}
                            onChange={(e) => setCreateForm((prev) => ({ ...prev, preparationName: e.target.value }))}
                        />
                    </label>

                    <label className={styles.field}>
                        <span>Выход, г</span>
                        <input
                            type="number"
                            className={styles.input}
                            placeholder="Например, 1000"
                            min="0"
                            step="0.01"
                            value={createForm.outputWeight}
                            onChange={(e) => setCreateForm((prev) => ({ ...prev, outputWeight: e.target.value }))}
                        />
                    </label>

                    <button
                        type="button"
                        className={styles.primaryButton}
                        onClick={handleCreatePreparation}
                        disabled={createLoading}
                    >
                        {createLoading ? "Создание..." : "Создать заготовку"}
                    </button>
                </div>

                {createError && <div className={styles.errorBox}>{createError}</div>}
            </section>

            <section className={styles.listSection}>
                <div className={styles.sectionHeading}>
                    <div>
                        <h2>Все заготовки</h2>
                        <p>Для каждой заготовки можно открыть техкарту или сразу выпустить новую партию.</p>
                    </div>
                    <div className={styles.counterChip}>
                        {preparations.length} шт.
                    </div>
                </div>

                {loading ? (
                    <div className={styles.emptyState}>Загрузка заготовок...</div>
                ) : preparations.length === 0 ? (
                    <div className={styles.emptyState}>
                        Пока нет ни одной заготовки. Создайте первую сверху и задайте ей состав.
                    </div>
                ) : (
                    <div className={styles.cardsGrid}>
                        {preparations.map((preparation) => {
                            const stocks = (stocksByPreparation[preparation.preparationId] || [])
                                .filter((item) => Number(item.quantity ?? 0) > 0);

                            return (
                                <article key={preparation.preparationId} className={styles.card}>
                                    <div className={styles.cardHeader}>
                                        <div>
                                            <div className={styles.cardId}>#{preparation.preparationId}</div>
                                            <h3 className={styles.cardTitle}>{preparation.preparationName}</h3>
                                        </div>
                                        <div className={styles.outputChip}>
                                            Выход: {formatQty(preparation.outputWeight)} г
                                        </div>
                                    </div>

                                    <div className={styles.metricsRow}>
                                        <div className={styles.metric}>
                                            <span className={styles.metricLabel}>Ингредиентов</span>
                                            <strong>{techCardCounts[preparation.preparationId] ?? 0}</strong>
                                        </div>
                                        <div className={styles.metric}>
                                            <span className={styles.metricLabel}>Остаток</span>
                                            <strong>{formatQty(totalStockByPreparation[preparation.preparationId])} г</strong>
                                        </div>
                                    </div>

                                    <div className={styles.stockSection}>
                                        <div className={styles.stockTitle}>Остатки по складам</div>
                                        {stocks.length > 0 ? (
                                            <div className={styles.stockChips}>
                                                {stocks.map((stock) => {
                                                    const warehouse = warehouses.find((item) => item.warehouseId === stock.warehouseId);
                                                    return (
                                                        <span key={`${preparation.preparationId}-${stock.warehouseId}`} className={styles.stockChip}>
                                                            {warehouse?.warehouseName || `Склад #${stock.warehouseId}`}: {formatQty(stock.quantity)} г
                                                        </span>
                                                    );
                                                })}
                                            </div>
                                        ) : (
                                            <div className={styles.mutedText}>Пока нет остатков на складах.</div>
                                        )}
                                    </div>

                                    <div className={styles.cardActions}>
                                        <button
                                            type="button"
                                            className={styles.secondaryButton}
                                            onClick={() => navigate(`/preparation-tech-card/${preparation.preparationId}`)}
                                        >
                                            Открыть техкарту
                                        </button>
                                        <button
                                            type="button"
                                            className={styles.primaryButton}
                                            onClick={() => openProductionModal(preparation)}
                                        >
                                            Произвести
                                        </button>
                                    </div>
                                </article>
                            );
                        })}
                    </div>
                )}
            </section>

            {productionModalOpen && selectedPreparation && (
                <div className={styles.modalOverlay} onClick={closeProductionModal}>
                    <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
                        <div className={styles.modalHeader}>
                            <div>
                                <p className={styles.eyebrow}>Выпуск заготовки</p>
                                <h3 className={styles.modalTitle}>{selectedPreparation.preparationName}</h3>
                                <p className={styles.modalSubtitle}>
                                    Ингредиенты будут списаны с выбранного склада, а готовая партия оприходована на него же.
                                </p>
                            </div>
                            <button type="button" className={styles.closeButton} onClick={closeProductionModal}>
                                Закрыть
                            </button>
                        </div>

                        <div className={styles.modalBody}>
                            <label className={styles.field}>
                                <span>Склад списания и прихода</span>
                                <select
                                    className={styles.select}
                                    value={productionForm.warehouseId}
                                    onChange={(e) => setProductionForm((prev) => ({ ...prev, warehouseId: e.target.value }))}
                                >
                                    <option value="">Выберите склад</option>
                                    {warehouses.map((warehouse) => (
                                        <option key={warehouse.warehouseId} value={warehouse.warehouseId}>
                                            {warehouse.warehouseName}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label className={styles.field}>
                                <span>Количество партий</span>
                                <input
                                    type="number"
                                    className={styles.input}
                                    min="0"
                                    step="0.01"
                                    value={productionForm.batchCount}
                                    onChange={(e) => setProductionForm((prev) => ({ ...prev, batchCount: e.target.value }))}
                                />
                            </label>

                            <div className={styles.previewBox}>
                                Будет произведено: {formatQty(Number(selectedPreparation.outputWeight ?? 0) * Number(productionForm.batchCount || 0))} г
                            </div>

                            {productionError && <div className={styles.errorBox}>{productionError}</div>}
                        </div>

                        <div className={styles.modalActions}>
                            <button
                                type="button"
                                className={styles.secondaryButton}
                                onClick={closeProductionModal}
                                disabled={productionLoading}
                            >
                                Отмена
                            </button>
                            <button
                                type="button"
                                className={styles.primaryButton}
                                onClick={handleProduce}
                                disabled={productionLoading}
                            >
                                {productionLoading ? "Выпуск..." : "Списать и оприходовать"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
