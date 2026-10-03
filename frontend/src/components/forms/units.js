/** Как продукт покупается: подпись для кнопки, базовая единица учёта и сколько её в одной закупочной. */
export const PURCHASE_UNITS = [
    { value: "kg", label: "кг", baseUnit: "g", unitFactor: 1000 },
    { value: "g", label: "г", baseUnit: "g", unitFactor: 1 },
    { value: "l", label: "л", baseUnit: "ml", unitFactor: 1000 },
    { value: "ml", label: "мл", baseUnit: "ml", unitFactor: 1 },
    { value: "pcs", label: "шт", baseUnit: "pcs", unitFactor: 1 }
];

export const presetForUnit = (unit) => PURCHASE_UNITS.find((option) => option.value === unit) ?? null;

/** Стандартная единица (кг, л…) с обычным коэффициентом, а не своя упаковка. */
export const isStandardUnit = (unit, baseUnit, unitFactor) => {
    const preset = presetForUnit(unit);
    return Boolean(preset)
        && preset.baseUnit === baseUnit
        && Number(unitFactor) === preset.unitFactor;
};

const normalizeName = (value) => String(value ?? "").trim().toLowerCase().replace(/\s+/g, " ");

/** Продукт с тем же названием (без учёта регистра и лишних пробелов), кроме исключённого id. */
export const findProductByName = (products, name, excludeId = null) => {
    const key = normalizeName(name);
    if (!key) return null;
    return (products ?? []).find((product) =>
        normalizeName(product?.productName) === key
        && Number(product?.productId) !== Number(excludeId)
    ) ?? null;
};
