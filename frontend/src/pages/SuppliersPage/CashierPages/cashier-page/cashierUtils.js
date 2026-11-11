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

const pad2 = (value) => String(value).padStart(2, "0");

// Локальная дата/время кассы в формате полей <input type="date">/<input type="time">.
export const toDateInputValue = (date = new Date()) =>
    `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

export const toTimeInputValue = (date = new Date()) =>
    `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;

// Значение для поля readyAt на сервере: "YYYY-MM-DDTHH:mm:00" без смещения (время бизнеса).
export const buildReadyAtValue = (dateValue, timeValue) => {
    if (!dateValue || !timeValue) return null;
    return `${dateValue}T${timeValue}:00`;
};

// Разбор readyAt с сервера ("2026-11-10T19:30:00") на дату и время для полей ввода.
export const splitReadyAt = (readyAt) => {
    const raw = String(readyAt || "");
    const match = raw.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/);
    return match ? { date: match[1], time: match[2] } : null;
};

// «19:30» для сегодняшнего дня, «11.11 19:30» для другого.
export const formatReadyAt = (readyAt, now = new Date()) => {
    const parts = splitReadyAt(readyAt);
    if (!parts) return "";
    if (parts.date === toDateInputValue(now)) return parts.time;
    const [, month, day] = parts.date.split("-");
    return `${day}.${month} ${parts.time}`;
};

// Время бизнеса пишется без смещения (Europe/Moscow, UTC+3).
export const parseBusinessDateTime = (value) => {
    if (!value) return null;
    const raw = String(value).trim();
    const hasTimezone = /[zZ]|[+-]\d{2}:\d{2}$/.test(raw);
    const normalized = raw.replace(/\.(\d{3})\d+/, ".$1").replace(" ", "T");
    const timestamp = new Date(hasTimezone ? normalized : `${normalized}+03:00`).getTime();
    return Number.isFinite(timestamp) ? timestamp : null;
};

// Почему заказ нельзя редактировать (те же правила, что на сервере), или null.
export const getOrderEditBlockReason = (order) => {
    if (!order) return "Заказ не найден";
    if (order.cancelledAt) return "Заказ отменён";
    if (order.date_issue || order.dateIssue) return "Заказ уже выдан, его нельзя изменить";
    if (order.duty) return "Заказ записан в долг: продукты уже списаны со склада, изменить его нельзя";
    if (order.paid === true) {
        return "Заказ уже оплачен: продукты списаны и чек отправлен в налоговую, изменить его нельзя";
    }
    return null;
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
