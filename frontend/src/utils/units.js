const UNIT_LABELS = {
    g: "г",
    kg: "кг",
    ml: "мл",
    l: "л",
    pcs: "шт",
    pc: "шт"
};

/** Русская подпись для кода единицы из данных (kg → кг, pcs → шт). */
export const unitLabel = (code) => {
    const normalized = String(code ?? "").trim().toLowerCase();
    if (!normalized) return "ед.";
    return UNIT_LABELS[normalized] ?? String(code).trim();
};
