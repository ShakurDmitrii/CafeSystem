import { Link } from "react-router-dom";
import PageHeader from "../../../components/layout/PageHeader";

export default function SupplierAssortmentHero({ supplier, productCount, favoriteCount }) {
    return (
        <PageHeader
            backLink={{ to: "/suppliers", label: "Все поставщики" }}
            title={supplier.name}
            description={supplier.communication || "Контакт поставщика не указан"}
            stats={[
                { label: "Позиций", value: productCount },
                { label: "Избранных", value: favoriteCount }
            ]}
            actions={<Link to="/products">Общий каталог продуктов →</Link>}
        />
    );
}
