export const formatMoney = (value) => new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
}).format(Number(value) || 0);

const toCents = (value) => Math.max(0, Math.round((Number(value) || 0) * 100));
const fromCents = (value) => value / 100;

export const calculateSalaryBalance = ({ workedDays, salaryPerDay, payment = {} }) => {
    const normalizedWorkedDays = Math.max(0, Number(workedDays) || 0);
    const paidDays = Math.min(
        Math.max(0, Number(payment.paidDays) || 0),
        normalizedWorkedDays
    );
    const unpaidDays = Math.max(0, normalizedWorkedDays - paidDays);
    const accruedCents = toCents(salaryPerDay) * unpaidDays;
    const partialPaidCents = Math.min(toCents(payment.partialPaid), accruedCents);

    return {
        paidDays,
        unpaidDays,
        partialPaid: fromCents(partialPaidCents),
        amountToPay: fromCents(Math.max(0, accruedCents - partialPaidCents)),
        totalPaid: fromCents(toCents(payment.totalPaid)),
        lastPaidAt: payment.lastPaidAt
    };
};

export const applySalaryPayment = ({
    payment = {},
    amount,
    workedDays,
    amountToPay,
    paidAt = new Date().toISOString()
}) => {
    const amountCents = toCents(amount);
    const dueCents = toCents(amountToPay);
    const isFullPayment = amountCents === dueCents;

    return {
        paidDays: isFullPayment
            ? Math.max(0, Number(workedDays) || 0)
            : Math.max(0, Number(payment.paidDays) || 0),
        partialPaid: isFullPayment
            ? 0
            : fromCents(toCents(payment.partialPaid) + amountCents),
        totalPaid: fromCents(toCents(payment.totalPaid) + amountCents),
        lastPaidAt: paidAt
    };
};

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
