export const getOrderTimestamp = (order) => {
    const raw = order?.created_at || order?.createdAt || order?.date;
    const timestamp = raw ? new Date(raw).getTime() : 0;
    return Number.isFinite(timestamp) ? timestamp : 0;
};

export const formatDateTime = (value) => {
    if (!value) return "Дата не указана";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);

    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
};

export const formatMoney = (value) => new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
}).format(Number(value) || 0);

export const formatPaymentType = (paymentType, paid) => {
    const normalized = String(paymentType || "").trim().toLowerCase();
    if (normalized === "cash") return "Наличные";
    if (normalized === "transfer") return "Перевод";
    if (normalized === "card") return "Карта";
    if (normalized === "unpaid" || paid === false) return "Не оплачено";
    return paymentType || "Оплата не указана";
};

export const getInitials = (name) => {
    const parts = String(name || "")
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2);

    return parts.length ? parts.map((part) => part[0]?.toUpperCase()).join("") : "Г";
};

export const getClientDebt = (clientWithDuty) => {
    if (clientWithDuty?.totalDuty !== undefined) {
        return Number(clientWithDuty.totalDuty) || 0;
    }

    return (clientWithDuty?.dutyOrders || []).reduce(
        (sum, order) => sum + (Number(order.amount) || 0),
        0
    );
};
