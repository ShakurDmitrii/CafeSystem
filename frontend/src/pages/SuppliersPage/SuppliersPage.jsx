import {
    useCallback,
    useDeferredValue,
    useEffect,
    useMemo,
    useState
} from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../auth";
import ConfirmSupplierDelete from "./supplier-page/ConfirmSupplierDelete";
import SupplierEditor from "./supplier-page/SupplierEditor";
import SuppliersCatalog from "./supplier-page/SuppliersCatalog";
import SuppliersHero from "./supplier-page/SuppliersHero";
import styles from "./SuppliersPage.module.css";

const API_SUPPLIERS = `${API_BASE_URL}/api/supplier`;
const SORT_OPTIONS = new Set(["name_asc", "name_desc", "id_desc"]);

const createEmptyForm = () => ({
    name: "",
    communication: ""
});

const normalizeSupplier = (supplier) => {
    const id = Number(
        supplier?.supplierID
        ?? supplier?.supplierId
        ?? supplier?.id
        ?? 0
    );

    return {
        ...supplier,
        id,
        name: String(supplier?.supplierName ?? supplier?.name ?? "").trim(),
        communication: String(supplier?.communication ?? "").trim()
    };
};

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

export default function SuppliersPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [pageError, setPageError] = useState("");
    const [statusMessage, setStatusMessage] = useState("");

    const [form, setForm] = useState(createEmptyForm);
    const [editingSupplierId, setEditingSupplierId] = useState(null);
    const [formError, setFormError] = useState("");
    const [saving, setSaving] = useState(false);
    const [supplierToDelete, setSupplierToDelete] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState("");

    const search = searchParams.get("q") ?? "";
    const requestedSort = searchParams.get("sort") ?? "name_asc";
    const sortBy = SORT_OPTIONS.has(requestedSort) ? requestedSort : "name_asc";
    const deferredSearch = useDeferredValue(search.trim().toLocaleLowerCase("ru"));

    const loadSuppliers = useCallback(async () => {
        setLoading(true);
        setPageError("");

        try {
            const response = await fetch(API_SUPPLIERS);
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(raw, `Не удалось загрузить поставщиков (${response.status}).`)
                );
            }

            const data = parseJsonSafe(raw);
            setSuppliers(
                Array.isArray(data)
                    ? data.map(normalizeSupplier).filter((supplier) => supplier.id > 0)
                    : []
            );
        } catch (error) {
            console.error("Ошибка загрузки поставщиков:", error);
            setPageError(
                error.message
                || "Не удалось загрузить поставщиков. Проверьте соединение и повторите попытку."
            );
            setSuppliers([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadSuppliers();
    }, [loadSuppliers]);

    const filteredSuppliers = useMemo(() => {
        const result = deferredSearch
            ? suppliers.filter((supplier) => (
                supplier.name.toLocaleLowerCase("ru").includes(deferredSearch)
                || supplier.communication.toLocaleLowerCase("ru").includes(deferredSearch)
                || String(supplier.id).includes(deferredSearch)
            ))
            : [...suppliers];

        result.sort((first, second) => {
            if (sortBy === "id_desc") return second.id - first.id;
            const comparison = first.name.localeCompare(second.name, "ru");
            return sortBy === "name_desc" ? -comparison : comparison;
        });

        return result;
    }, [deferredSearch, sortBy, suppliers]);

    const suppliersWithContacts = suppliers.filter(
        (supplier) => Boolean(supplier.communication)
    ).length;

    const updateSearchParam = (key, value, defaultValue = "") => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (!value || value === defaultValue) next.delete(key);
            else next.set(key, value);
            return next;
        }, { replace: true });
    };

    const resetEditor = () => {
        setForm(createEmptyForm());
        setEditingSupplierId(null);
        setFormError("");
    };

    const handleFormChange = (field, value) => {
        setFormError("");
        setForm((current) => ({ ...current, [field]: value }));
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        const name = form.name.trim();
        const communication = form.communication.trim();

        if (!name) {
            setFormError("Введите название компании.");
            return;
        }

        const editing = editingSupplierId != null;
        setSaving(true);
        setFormError("");
        setStatusMessage("");

        try {
            const response = await fetch(
                editing ? `${API_SUPPLIERS}/${editingSupplierId}` : API_SUPPLIERS,
                {
                    method: editing ? "PUT" : "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        supplierName: name,
                        communication
                    })
                }
            );
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(
                        raw,
                        editing
                            ? "Не удалось сохранить изменения."
                            : "Не удалось добавить поставщика."
                    )
                );
            }

            resetEditor();
            await loadSuppliers();
            setStatusMessage(
                editing
                    ? `Данные поставщика «${name}» обновлены.`
                    : `Поставщик «${name}» добавлен.`
            );
        } catch (error) {
            console.error("Ошибка сохранения поставщика:", error);
            setFormError(error.message || "Не удалось сохранить поставщика.");
        } finally {
            setSaving(false);
        }
    };

    const startEditing = (supplier) => {
        setEditingSupplierId(supplier.id);
        setForm({
            name: supplier.name,
            communication: supplier.communication
        });
        setFormError("");
        setStatusMessage("");
        document.getElementById("supplier-editor")?.scrollIntoView();
    };

    const requestDelete = (supplier) => {
        setSupplierToDelete(supplier);
        setDeleteError("");
    };

    const closeDeleteDialog = () => {
        if (deleting) return;
        setSupplierToDelete(null);
        setDeleteError("");
    };

    const confirmDelete = async () => {
        if (!supplierToDelete) return;
        setDeleting(true);
        setDeleteError("");
        setStatusMessage("");

        try {
            const response = await fetch(
                `${API_SUPPLIERS}/${supplierToDelete.id}`,
                { method: "DELETE" }
            );
            const raw = await response.text();
            if (!response.ok) {
                throw new Error(
                    getResponseMessage(
                        raw,
                        "Не удалось удалить поставщика. Проверьте связанные продукты и накладные."
                    )
                );
            }

            const deletedName = supplierToDelete.name;
            setSuppliers((current) => (
                current.filter((supplier) => supplier.id !== supplierToDelete.id)
            ));
            if (editingSupplierId === supplierToDelete.id) resetEditor();
            setSupplierToDelete(null);
            setStatusMessage(`Поставщик «${deletedName}» удалён.`);
        } catch (error) {
            console.error("Ошибка удаления поставщика:", error);
            setDeleteError(
                error.message
                || "Не удалось удалить поставщика. Уберите связанные данные и повторите попытку."
            );
        } finally {
            setDeleting(false);
        }
    };

    return (
        <div className={styles.page}>
            <SuppliersHero
                total={suppliers.length}
                withContacts={suppliersWithContacts}
                withoutContacts={suppliers.length - suppliersWithContacts}
            />

            {statusMessage ? (
                <div className={styles.statusMessage} role="status" aria-live="polite">
                    <span>{statusMessage}</span>
                    <button
                        type="button"
                        onClick={() => setStatusMessage("")}
                        aria-label="Скрыть сообщение"
                    >
                        Закрыть
                    </button>
                </div>
            ) : null}

            <div className={styles.workspaceGrid}>
                <SupplierEditor
                    form={form}
                    editingSupplierId={editingSupplierId}
                    saving={saving}
                    error={formError}
                    onChange={handleFormChange}
                    onSubmit={handleSubmit}
                    onCancel={resetEditor}
                />

                <SuppliersCatalog
                    suppliers={filteredSuppliers}
                    total={suppliers.length}
                    search={search}
                    sortBy={sortBy}
                    loading={loading}
                    error={pageError}
                    onSearchChange={(value) => updateSearchParam("q", value)}
                    onSortChange={(value) => updateSearchParam("sort", value, "name_asc")}
                    onRetry={loadSuppliers}
                    onEdit={startEditing}
                    onDelete={requestDelete}
                    onClearSearch={() => updateSearchParam("q", "")}
                />
            </div>

            {supplierToDelete ? (
                <ConfirmSupplierDelete
                    supplier={supplierToDelete}
                    deleting={deleting}
                    error={deleteError}
                    onConfirm={confirmDelete}
                    onClose={closeDeleteDialog}
                />
            ) : null}
        </div>
    );
}
