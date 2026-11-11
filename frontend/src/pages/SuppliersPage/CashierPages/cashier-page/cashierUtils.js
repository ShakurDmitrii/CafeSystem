export const formatMoney = (value) => new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
}).format(Number(value) || 0);

export const formatDate = (value) => {
    if (!value) return "Дата не указана";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "short",
        year: "numeric"
    }).format(date);
};

export const getInitials = (name) => {
    const parts = String(name || "").trim().split(/\s+/).filter(Boolean).slice(0, 2);
    return parts.length ? parts.map((part) => part[0]?.toUpperCase()).join("") : "С";
};

/**
 * Пометка для заказа, оформленного в долг: долг остаётся в отчёте смены,
 * где был заказ, а погашения показываются здесь, даже если приняты позже.
 */
export const describeDebt = (order) => {
    if (!order?.isDebt) return null;
    const original = Number(order.debtOriginalAmount ?? order.orderAmount ?? 0);
    const repaid = Number(order.debtRepaidAmount ?? 0);
    const remaining = Number(order.debtRemainingAmount ?? Math.max(0, original - repaid));
    if (order.debtStatus === "repaid" || remaining <= 0) {
        const payments = Array.isArray(order.debtPayments) ? order.debtPayments : [];
        const lastPaidAt = payments.length ? payments[payments.length - 1].paidAt : null;
        return lastPaidAt
            ? `Долг погашен ${formatDate(lastPaidAt)}`
            : "Долг погашен";
    }
    if (repaid > 0) {
        return `Долг: погашено ${formatMoney(repaid)} из ${formatMoney(original)}, остаток ${formatMoney(remaining)}`;
    }
    return `Долг не погашен: ${formatMoney(remaining)}`;
};

/** Заказы, из-за которых нельзя закрыть смену: не оплачены и не оформлены в долг. */
export const findUnpaidOrders = (orders) => (orders ?? []).filter(
    (order) => order && order.paid !== true && !order.duty
);

export const describeUnpaidOrdersBlock = (orders) => {
    const unpaid = findUnpaidOrders(orders);
    if (unpaid.length === 0) return null;
    const numbers = unpaid.map((order) => `№ ${order.orderId}`).join(", ");
    return `Нельзя закрыть смену: не оплачены заказы ${numbers}. Примите по ним оплату или отмените их.`;
};
