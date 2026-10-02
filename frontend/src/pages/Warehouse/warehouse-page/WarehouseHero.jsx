import PageHeader from "../../../components/layout/PageHeader";

export default function WarehouseHero({ warehouses, positions, lowStock }) {
    return (
        <PageHeader
            title="Склады"
            description="Остатки, приход, списание и перемещение между складами."
            stats={[
                { label: "Складов", value: warehouses.length },
                { label: "Позиций", value: positions },
                { label: "Без остатка", value: lowStock, tone: lowStock > 0 ? "warning" : undefined }
            ]}
        />
    );
}
