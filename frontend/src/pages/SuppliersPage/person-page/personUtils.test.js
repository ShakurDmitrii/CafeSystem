import { formatDateTime, formatMoney, getInitials } from "./personUtils";

describe("person presentation helpers", () => {
    test("formats a salary amount as Russian rubles", () => {
        expect(formatMoney(2500)).toContain("2 500");
    });

    test("returns two initials for an employee", () => {
        expect(getInitials("Мария Орлова")).toBe("МО");
    });

    test("handles a missing payment date", () => {
        expect(formatDateTime(null)).toBe("Ещё не было");
    });
});
