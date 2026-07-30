export const normalizeCollection = (value) => Array.isArray(value) ? value : [];

export const getProductId = (product) => Number(product?.productId ?? product?.id ?? 0);

export const parseDecimal = (value) => {
    const parsed = Number(String(value ?? "").trim().replace(",", "."));
    return Number.isFinite(parsed) ? parsed : Number.NaN;
};

export const getSafeUnitFactor = (value) => {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : 1;
};

export const getUnitLabel = (product) => {
    const unit = product?.unit ?? product?.baseUnit ?? "ед.";
    const baseUnit = product?.baseUnit ?? unit;
    const factor = getSafeUnitFactor(product?.unitFactor);
    return factor > 1 ? `${unit} · 1 = ${factor} ${baseUnit}` : unit;
};

export const formatQuantity = (value) => new Intl.NumberFormat("ru-RU", {
    maximumFractionDigits: 3
}).format(Number(value ?? 0));

export const formatMoney = (value) => {
    const number = Number(value);
    if (!Number.isFinite(number)) return "—";
    return new Intl.NumberFormat("ru-RU", {
        style: "currency",
        currency: "RUB",
        maximumFractionDigits: Math.abs(number) > 0 && Math.abs(number) < 1 ? 4 : 2
    }).format(number);
};

export const normalizeMovementPayload = (product, quantityValue, unitPriceValue) => {
    const quantity = Number(quantityValue);
    const unitPrice = Number(unitPriceValue);
    const factor = getSafeUnitFactor(product?.unitFactor);
    const unit = String(product?.unit ?? "").trim().toLowerCase();
    const baseUnit = String(product?.baseUnit ?? "").trim().toLowerCase();
    if (factor <= 1 || unit !== baseUnit) return { quantity, unitPrice };
    return { quantity: quantity / factor, unitPrice: unitPrice * factor };
};

export const buildIncomingPriceMaps = (movements) => {
    const totals = {};
    const latest = {};
    normalizeCollection(movements).forEach((movement) => {
        const productId = Number(movement?.productId);
        const quantity = Number(movement?.quantity);
        const price = Number(movement?.unitPrice);
        const warehouseId = movement?.docType === "receipt" || movement?.docType === "movement"
            ? Number(movement?.toWarehouseId)
            : 0;
        if (!productId || !warehouseId || !Number.isFinite(quantity) || quantity <= 0
            || !Number.isFinite(price) || price < 0) return;
        const key = `${warehouseId}-${productId}`;
        const amount = Number(movement?.lineTotal);
        totals[key] ??= { quantity: 0, amount: 0 };
        totals[key].quantity += quantity;
        totals[key].amount += Number.isFinite(amount) ? amount : quantity * price;
        const timestamp = movement?.docDate ? new Date(movement.docDate).getTime() : 0;
        if (!latest[key] || timestamp >= latest[key].timestamp) latest[key] = { price, timestamp };
    });
    return {
        averageMap: Object.fromEntries(Object.entries(totals).map(([key, value]) => [
            key,
            value.quantity > 0 ? value.amount / value.quantity : 0
        ])),
        latestMap: Object.fromEntries(Object.entries(latest).map(([key, value]) => [key, value.price]))
    };
};
