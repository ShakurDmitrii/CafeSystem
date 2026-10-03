import { unitLabel } from "./units";

test("maps unit codes to Russian labels", () => {
    expect(unitLabel("kg")).toBe("кг");
    expect(unitLabel("G")).toBe("г");
    expect(unitLabel("pcs")).toBe("шт");
    expect(unitLabel("ml")).toBe("мл");
    expect(unitLabel("l")).toBe("л");
});

test("keeps unknown units and labels empty ones", () => {
    expect(unitLabel("пачка")).toBe("пачка");
    expect(unitLabel("")).toBe("ед.");
    expect(unitLabel(null)).toBe("ед.");
});
