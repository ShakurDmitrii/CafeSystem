export const normalizeCollection = (value) => {
    if (Array.isArray(value)) return value;
    if (!value || typeof value !== "object") return [];

    const nested = Object.values(value).find(Array.isArray);
    return nested || [value];
};

export const getSupplierId = (supplier) => Number(
    supplier?.supplierId ?? supplier?.supplierID ?? supplier?.id ?? 0
);

export const getProductId = (product) => Number(
    product?.productId ?? product?.productID ?? product?.id ?? 0
);

export const parseDecimal = (value) => {
    const normalized = String(value ?? "").trim().replace(",", ".");
    if (!normalized) return Number.NaN;
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? parsed : Number.NaN;
};

export const parseResponseMessage = (raw, fallback) => {
    if (!raw) return fallback;
    try {
        const data = JSON.parse(raw);
        return data?.message || data?.detail || fallback;
    } catch {
        return raw || fallback;
    }
};

export const formatMoney = (value) => {
    const amount = Number(value ?? 0);
    return new Intl.NumberFormat("ru-RU", {
        style: "currency",
        currency: "RUB",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(Number.isFinite(amount) ? amount : 0);
};

export const formatQuantity = (value) => {
    const quantity = Number(value ?? 0);
    return new Intl.NumberFormat("ru-RU", {
        maximumFractionDigits: 3
    }).format(Number.isFinite(quantity) ? quantity : 0);
};

export const formatDate = (value) => {
    if (!value) return "Дата не указана";
    const [year, month, day] = String(value).split("-").map(Number);
    if (!year || !month || !day) return String(value);

    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "long",
        year: "numeric"
    }).format(new Date(year, month - 1, day));
};

export const getTodayValue = () => {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, "0");
    const day = String(today.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
};

export const buildMovementIndex = (movements, prefix) => {
    const index = {};

    normalizeCollection(movements).forEach((movement) => {
        if (movement?.docType !== "receipt") return;
        const comment = String(movement.comment ?? "").trim();
        if (!comment.startsWith(prefix)) return;

        const noteId = Number(comment.slice(prefix.length));
        if (!Number.isFinite(noteId) || noteId <= 0) return;

        if (!index[noteId]) {
            index[noteId] = {
                posted: true,
                total: 0,
                warehouseId: null,
                priceByProductId: {}
            };
        }

        const entry = index[noteId];
        const productId = Number(movement.productId);
        const unitPrice = Number(movement.unitPrice);
        const quantity = Number(movement.quantity);
        const lineTotal = Number(movement.lineTotal);

        if (Number.isFinite(productId) && Number.isFinite(unitPrice)) {
            entry.priceByProductId[productId] = unitPrice;
        }
        if (Number.isFinite(lineTotal)) {
            entry.total += lineTotal;
        } else if (Number.isFinite(unitPrice) && Number.isFinite(quantity)) {
            entry.total += unitPrice * quantity;
        }
        if (movement.toWarehouseId != null) {
            entry.warehouseId = Number(movement.toWarehouseId);
        }
    });

    return index;
};
