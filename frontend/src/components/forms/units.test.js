import { findProductByName, isStandardUnit, presetForUnit } from "./units";

test("presets give base unit and factor", () => {
    expect(presetForUnit("kg")).toMatchObject({ baseUnit: "g", unitFactor: 1000 });
    expect(presetForUnit("pcs")).toMatchObject({ baseUnit: "pcs", unitFactor: 1 });
    expect(presetForUnit("box")).toBeNull();
});

test("custom packaging is not a standard unit", () => {
    expect(isStandardUnit("kg", "g", "1000")).toBe(true);
    expect(isStandardUnit("kg", "g", 5000)).toBe(false);
    expect(isStandardUnit("коробка", "g", 5000)).toBe(false);
});

test("finds duplicates ignoring case and spaces, but not the product itself", () => {
    const products = [{ productId: 1, productName: "Лосось охлаждённый" }];
    expect(findProductByName(products, "  лосось   ОХЛАЖДЁННЫЙ ")?.productId).toBe(1);
    expect(findProductByName(products, "Лосось охлаждённый", 1)).toBeNull();
    expect(findProductByName(products, "Тунец")).toBeNull();
    expect(findProductByName(products, "   ")).toBeNull();
});
