import PageHeader from "../../../components/layout/PageHeader";
import { formatMoney } from "../CashierPages/cashier-page/cashierUtils";

export default function TeamHero({ peopleCount, closedShifts, amountDue }) {
    return (
        <PageHeader
            title="Персонал"
            description="Сотрудники, их аккаунты, отработанные смены и выплаты."
            stats={[
                { label: "В команде", value: peopleCount },
                { label: "Закрыто смен", value: closedShifts },
                { label: "К выплате", value: formatMoney(amountDue), tone: amountDue > 0 ? "warning" : undefined }
            ]}
        />
    );
}
