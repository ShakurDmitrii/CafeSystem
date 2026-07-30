const numberFormatter = new Intl.NumberFormat('ru-RU', {
    maximumFractionDigits: 1
});

const integerFormatter = new Intl.NumberFormat('ru-RU', {
    maximumFractionDigits: 0
});

const currencyFormatter = new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0
});

const timeFormatter = new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit'
});

export const formatNumber = (value, fallback = '—') => (
    Number.isFinite(Number(value)) ? numberFormatter.format(Number(value)) : fallback
);

export const formatInteger = (value, fallback = '—') => (
    Number.isFinite(Number(value)) ? integerFormatter.format(Number(value)) : fallback
);

export const formatCurrency = (value, fallback = '—') => (
    Number.isFinite(Number(value)) ? currencyFormatter.format(Number(value)) : fallback
);

export const formatPercent = (value, { fraction = false, digits = 0 } = {}) => {
    const numericValue = Number(value);
    if (!Number.isFinite(numericValue)) return '—';
    const percentValue = fraction ? numericValue * 100 : numericValue;
    return `${new Intl.NumberFormat('ru-RU', {
        maximumFractionDigits: digits
    }).format(percentValue)}%`;
};

export const formatTime = (value) => {
    const date = value ? new Date(value) : new Date();
    return Number.isNaN(date.getTime()) ? '—' : timeFormatter.format(date);
};

