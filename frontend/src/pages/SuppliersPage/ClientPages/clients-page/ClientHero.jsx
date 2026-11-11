import PageHeader from "../../../../components/layout/PageHeader";
import { formatMoney } from "../../CashierPages/cashier-page/cashierUtils";

export default function ClientHero({ clientsCount, contactsCount, debtClientsCount, totalDebt }) {
    return (
        <PageHeader
            title="Клиенты"
            description="Контакты гостей, история заказов и долги."
            stats={[
                { label: "Гостей", value: clientsCount },
                { label: "С контактом", value: contactsCount },
                { label: "С долгом", value: debtClientsCount, tone: debtClientsCount > 0 ? "warning" : undefined },
                { label: "Сумма долгов", value: formatMoney(totalDebt), tone: totalDebt > 0 ? "warning" : undefined }
            ]}
        />
    );
}
