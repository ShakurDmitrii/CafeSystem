import PageHeader from "../../../components/layout/PageHeader";

export default function PreparationsHero({ preparationCount, warehouseCount, totalStock, formatQuantity }) {
    return (
        <PageHeader
            title="Заготовки"
            description="При выпуске партии ингредиенты списываются, а готовая заготовка приходуется на выбранный склад."
            stats={[
                { label: "Заготовок", value: preparationCount },
                { label: "Складов", value: warehouseCount },
                { label: "Общий остаток", value: `${formatQuantity(totalStock)} г` }
            ]}
        />
    );
}
