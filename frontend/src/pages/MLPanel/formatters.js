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

const hasNumber = (value) => (
    (typeof value === 'number' || (typeof value === 'string' && value.trim() !== ''))
    && Number.isFinite(Number(value))
);

export const formatNumber = (value, fallback = '—') => (
    hasNumber(value) ? numberFormatter.format(Number(value)) : fallback
);

export const formatInteger = (value, fallback = '—') => (
    hasNumber(value) ? integerFormatter.format(Number(value)) : fallback
);

export const formatUnitCost = (value) => (
    hasNumber(value) ? new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 6 }).format(Number(value)) : '—'
);

export const formatCurrency = (value, fallback = '—') => (
    hasNumber(value) ? currencyFormatter.format(Number(value)) : fallback
);

export const formatPercent = (value, { fraction = false, digits = 0 } = {}) => {
    if (!hasNumber(value)) return '—';
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

const formatIsoDate = (value) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value ?? ''));
    return match ? `${match[3]}.${match[2]}.${match[1]}` : null;
};

/** Итог обучения для статусной строки: период, объём данных и предупреждения модели. */
export const describeTrainingResult = (result) => {
    const training = result?.trainingResult ?? {};
    const records = result?.recordsCount ?? training.records;
    const from = formatIsoDate(training.trainingStartDate);
    const to = formatIsoDate(training.trainingEndDate);
    const period = from && to ? ` на продажах с ${from} по ${to}` : '';
    const zeroDays = Number(training.zeroSaleDaysAdded) > 0
        ? `, из них дней без продаж: ${formatInteger(training.zeroSaleDaysAdded)}`
        : '';
    const parts = [
        `Модель обучена${period}: ${formatInteger(records, '—')} записей по блюдам и дням${zeroDays}, `
            + `${formatInteger(result?.newIngredientsCount, '—')} ингредиентов.`
    ];
    if (result?.trimmedToRecent) {
        parts.push('История слишком длинная для одного запуска: взяты самые свежие продажи.');
    }
    return [...parts, ...(training.warnings || [])].join(' ');
};
