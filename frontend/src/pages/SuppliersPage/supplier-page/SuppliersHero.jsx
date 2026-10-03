import PageHeader from "../../../components/layout/PageHeader";

export default function SuppliersHero({ total, withContacts, withoutContacts }) {
    return (
        <PageHeader
            title="Поставщики"
            description="Откройте поставщика, чтобы работать с его ассортиментом и ценами."
            stats={[
                { label: "Поставщиков", value: total },
                { label: "С контактом", value: withContacts },
                { label: "Без контакта", value: withoutContacts, tone: withoutContacts > 0 ? "warning" : undefined }
            ]}
        />
    );
}
