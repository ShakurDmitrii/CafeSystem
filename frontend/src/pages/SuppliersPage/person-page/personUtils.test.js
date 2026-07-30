import { applySalaryPayment, calculateSalaryBalance } from "./personUtils";

describe("salary payments", () => {
    test("partial payment reduces the balance without closing worked days", () => {
        const balance = calculateSalaryBalance({
            workedDays: 3,
            salaryPerDay: 2500,
            payment: {}
        });

        const payment = applySalaryPayment({
            payment: {},
            amount: 3000,
            workedDays: 3,
            amountToPay: balance.amountToPay
        });

        expect(payment).toMatchObject({
            paidDays: 0,
            partialPaid: 3000,
            totalPaid: 3000
        });
        expect(calculateSalaryBalance({
            workedDays: 3,
            salaryPerDay: 2500,
            payment
        }).amountToPay).toBe(4500);
    });

    test("paying the remaining balance closes every currently worked day", () => {
        const previous = {
            paidDays: 0,
            partialPaid: 3000,
            totalPaid: 3000
        };

        const payment = applySalaryPayment({
            payment: previous,
            amount: 4500,
            workedDays: 3,
            amountToPay: 4500
        });

        expect(payment).toMatchObject({
            paidDays: 3,
            partialPaid: 0,
            totalPaid: 7500
        });
    });

    test("a new shift becomes payable after the previous balance was closed", () => {
        const balance = calculateSalaryBalance({
            workedDays: 4,
            salaryPerDay: 2500,
            payment: {
                paidDays: 3,
                partialPaid: 0,
                totalPaid: 7500
            }
        });

        expect(balance.unpaidDays).toBe(1);
        expect(balance.amountToPay).toBe(2500);
    });

    test("legacy payment records without partialPaid remain supported", () => {
        const balance = calculateSalaryBalance({
            workedDays: 5,
            salaryPerDay: 2000,
            payment: {
                paidDays: 2,
                totalPaid: 4000
            }
        });

        expect(balance.unpaidDays).toBe(3);
        expect(balance.partialPaid).toBe(0);
        expect(balance.amountToPay).toBe(6000);
    });
});
