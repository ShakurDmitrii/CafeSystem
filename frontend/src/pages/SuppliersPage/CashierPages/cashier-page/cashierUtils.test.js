import { describeDebt } from "./cashierUtils";

const plain = (text) => String(text).replace(/\s/g, " ");

test("regular order has no debt mark", () => {
    expect(describeDebt({ isDebt: false, orderAmount: 500 })).toBeNull();
});

test("open debt shows the outstanding amount", () => {
    expect(plain(describeDebt({
        isDebt: true, debtStatus: "open", debtOriginalAmount: 800, debtRepaidAmount: 0, debtRemainingAmount: 800
    }))).toBe("Долг не погашен: 800 ₽");
});

test("partial debt shows paid and remaining", () => {
    expect(plain(describeDebt({
        isDebt: true, debtStatus: "partial", debtOriginalAmount: 800, debtRepaidAmount: 300, debtRemainingAmount: 500
    }))).toBe("Долг: погашено 300 ₽ из 800 ₽, остаток 500 ₽");
});

test("repaid debt shows the date of the last payment", () => {
    const mark = describeDebt({
        isDebt: true,
        debtStatus: "repaid",
        debtOriginalAmount: 800,
        debtRepaidAmount: 800,
        debtRemainingAmount: 0,
        debtPayments: [
            { amount: 300, paidAt: "2026-09-20T12:00" },
            { amount: 500, paidAt: "2026-10-01T18:30" }
        ]
    });
    expect(mark).toMatch(/^Долг погашен /);
    expect(mark).toContain("2026");
});
