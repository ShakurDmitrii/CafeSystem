import PageHeader from "../../../components/layout/PageHeader";

export default function ConsignmentHero({ totalCount, draftCount, postedCount, monthTotal }) {
    return (
        <PageHeader
            title="Накладные"
            description="Создайте черновик, добавьте позиции с ценами и проведите накладную на склад."
            stats={[
                { label: "Документов", value: totalCount },
                { label: "Черновиков", value: draftCount, tone: draftCount > 0 ? "warning" : undefined },
                { label: "Проведено", value: postedCount },
                { label: "Сумма проведённых", value: monthTotal }
            ]}
        />
    );
}
