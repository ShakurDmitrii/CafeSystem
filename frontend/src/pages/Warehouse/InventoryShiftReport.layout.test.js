import fs from "fs";
import path from "path";

describe("InventoryShiftReport narrow layout", () => {
    test("turns the wide inventory table into labelled rows on narrow screens", () => {
        const component = fs.readFileSync(
            path.join(__dirname, "InventoryShiftReport.jsx"),
            "utf8"
        );
        const css = fs.readFileSync(
            path.join(__dirname, "InventoryShiftReport.module.css"),
            "utf8"
        );

        expect(component).toContain('data-label="Продукт"');
        expect(component).toContain('data-label="Фактический остаток"');
        expect(css).toMatch(/@media\s*\(max-width:\s*760px\)/);
        expect(css).toContain("content: attr(data-label)");
    });
});
