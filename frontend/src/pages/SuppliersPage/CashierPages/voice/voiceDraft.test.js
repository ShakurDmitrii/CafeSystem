import { applyVoiceDraft, candidateToCartItem, mergeIntoCart } from "./voiceDraft";

const catalog = new Map([
    ["dish-11", { entityKey: "dish-11", dishName: "Филадельфия", price: 450, imageUrl: "/f.jpg", categoryName: "Холодные", setItems: [] }],
    ["set-2", { entityKey: "set-2", dishName: "Сет Американо", price: 1200, imageUrl: null, categoryName: "Наборы", setItems: [{ dishName: "Филадельфия", qty: 1 }] }]
]);

test("matched lines are added to the cart in the picker's item shape", () => {
    const draft = {
        transcript: "филадельфия два",
        personCount: 3,
        unrecognized: [],
        lines: [
            { text: "филадельфия", status: "MATCHED", qty: 2, candidates: [{ itemType: "dish", id: 11, name: "Филадельфия", price: 450, score: 1 }] }
        ]
    };

    const result = applyVoiceDraft([], draft, catalog);

    expect(result.cartItems).toEqual([
        {
            itemType: "dish", entityKey: "dish-11", dishId: 11, setId: null, dishName: "Филадельфия",
            price: 450, qty: 2, imageUrl: "/f.jpg", categoryName: "Холодные", setItems: []
        }
    ]);
    expect(result.added).toEqual([{ name: "Филадельфия", qty: 2 }]);
    expect(result.personCount).toBe(3);
    expect(result.ambiguous).toEqual([]);
});

test("ambiguous and unrecognized lines are returned for the cashier, not added", () => {
    const candidates = [
        { itemType: "dish", id: 127, name: "Кофе для дедушки", price: 100, score: 0.5 },
        { itemType: "dish", id: 125, name: "Кофе Американо большой", price: 120, score: 0.33 }
    ];
    const draft = {
        transcript: "кофе пицца",
        personCount: null,
        unrecognized: ["пицца"],
        lines: [{ text: "кофе", status: "AMBIGUOUS", qty: 1, candidates }]
    };

    const result = applyVoiceDraft([], draft, catalog);

    expect(result.cartItems).toEqual([]);
    expect(result.ambiguous).toEqual([{ text: "кофе", qty: 1, candidates }]);
    expect(result.unrecognized).toEqual(["пицца"]);
    expect(result.personCount).toBeNull();
});

test("repeated item increases quantity instead of duplicating the line", () => {
    const existing = [candidateToCartItem({ itemType: "dish", id: 11, name: "Филадельфия", price: 450 }, 1, catalog)];

    const merged = mergeIntoCart(existing, candidateToCartItem({ itemType: "dish", id: 11, name: "Филадельфия", price: 450 }, 2, catalog));

    expect(merged).toHaveLength(1);
    expect(merged[0].qty).toBe(3);
});

test("sets keep their composition from the catalog", () => {
    const item = candidateToCartItem({ itemType: "set", id: 2, name: "Сет Американо", price: 1200 }, 1, catalog);

    expect(item.setId).toBe(2);
    expect(item.dishId).toBeNull();
    expect(item.setItems).toEqual([{ dishName: "Филадельфия", qty: 1 }]);
});

test("unknown catalog entries fall back to the candidate data", () => {
    const item = candidateToCartItem({ itemType: "dish", id: 999, name: "Новое блюдо", price: 10 }, 0, catalog);

    expect(item).toMatchObject({ entityKey: "dish-999", dishName: "Новое блюдо", price: 10, qty: 1, categoryName: "Без категории" });
});
