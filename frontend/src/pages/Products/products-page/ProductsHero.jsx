import PageHeader from "../../../components/layout/PageHeader";

export default function ProductsHero({ productCount, favoriteCount, supplierCount }) {
    return (
        <PageHeader
            title="Продукты"
            description="Цена закупки, отход и пересчёт в базовую единицу (например, 1 кг = 1000 г) для склада и техкарт."
            stats={[
                { label: "Продуктов", value: productCount },
                { label: "Избранных", value: favoriteCount },
                { label: "Поставщиков", value: supplierCount }
            ]}
        />
    );
}
