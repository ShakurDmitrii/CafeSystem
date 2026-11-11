// Чистые функции для переноса голосового черновика в корзину окна выбора блюд.
// Здесь нет React и сети, чтобы логику можно было проверить обычными тестами.

export const toEntityKey = (itemType, id) => `${itemType}-${id}`;

/**
 * Превращает кандидата из черновика в позицию корзины в том же формате,
 * что и ручное добавление в DishPickerModal. Данные каталога (фото, категория,
 * состав набора) берутся из catalogByKey, если позиция там есть.
 */
export function candidateToCartItem(candidate, qty, catalogByKey) {
    const itemType = candidate.itemType === "set" ? "set" : "dish";
    const entityKey = toEntityKey(itemType, candidate.id);
    const catalogItem = catalogByKey?.get(entityKey);
    return {
        itemType,
        entityKey,
        dishId: itemType === "dish" ? candidate.id : null,
        setId: itemType === "set" ? candidate.id : null,
        dishName: catalogItem?.dishName || candidate.name,
        price: catalogItem?.price ?? candidate.price ?? 0,
        qty: Math.max(1, Number(qty) || 1),
        imageUrl: catalogItem?.imageUrl || null,
        categoryName: catalogItem?.categoryName || (itemType === "set" ? "Наборы" : "Без категории"),
        setItems: catalogItem?.setItems || []
    };
}

/** Добавляет позицию в корзину: если она уже есть, увеличивает количество. */
export function mergeIntoCart(cartItems, item) {
    const existing = cartItems.find((cartItem) => cartItem.entityKey === item.entityKey);
    if (!existing) {
        return [...cartItems, item];
    }
    return cartItems.map((cartItem) => (
        cartItem.entityKey === item.entityKey
            ? { ...cartItem, qty: Number(cartItem.qty || 0) + Number(item.qty || 0) }
            : cartItem
    ));
}

/**
 * Применяет черновик к корзине: однозначные строки добавляются сразу,
 * неоднозначные и нераспознанные возвращаются для показа кассиру.
 */
export function applyVoiceDraft(cartItems, draft, catalogByKey) {
    let nextCart = cartItems;
    const added = [];
    const ambiguous = [];

    (draft?.lines || []).forEach((line) => {
        const qty = Math.max(1, Number(line.qty) || 1);
        const candidates = Array.isArray(line.candidates) ? line.candidates : [];
        if (line.status === "MATCHED" && candidates.length > 0) {
            const item = candidateToCartItem(candidates[0], qty, catalogByKey);
            nextCart = mergeIntoCart(nextCart, item);
            added.push({ name: item.dishName, qty });
            return;
        }
        if (candidates.length > 0) {
            ambiguous.push({ text: line.text, qty, candidates });
        }
    });

    const personCount = Number(draft?.personCount);

    return {
        cartItems: nextCart,
        added,
        ambiguous,
        unrecognized: Array.isArray(draft?.unrecognized) ? draft.unrecognized : [],
        personCount: Number.isInteger(personCount) && personCount > 0 ? personCount : null,
        transcript: String(draft?.transcript || "")
    };
}

export function describeAdded(added) {
    return added.map((entry) => `${entry.name} ×${entry.qty}`).join(", ");
}
