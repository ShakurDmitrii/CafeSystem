export const formatMoney = (value) => new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
}).format(Number(value) || 0);

export const formatDateTime = (value) => {
    if (!value) return "Ещё не было";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Дата неизвестна";

    return new Intl.DateTimeFormat("ru-RU", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    }).format(date);
};

export const getInitials = (name) => {
    const parts = String(name || "")
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2);
    return parts.length ? parts.map((part) => part[0]?.toUpperCase()).join("") : "С";
};
