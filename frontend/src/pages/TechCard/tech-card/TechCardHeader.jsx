import PageHeader from "../../../components/layout/PageHeader";
import styles from "../TechCardPage.module.css";

export default function TechCardHeader({
    ownerType,
    ownerId,
    ownerName,
    itemCount,
    totalCost,
    dishPrice,
    outputWeight,
    formatMoney,
    formatQuantity
}) {
    const isDish = ownerType === "dish";
    const ownerLabel = isDish ? "Блюдо" : "Заготовка";
    const cost = Number(totalCost || 0);
    const price = dishPrice == null ? null : Number(dishPrice);
    const hasPrice = isDish && price != null && price > 0;
    const margin = hasPrice ? price - cost : null;
    const marginPercent = hasPrice ? Math.round((margin / price) * 100) : null;
    const unprofitable = hasPrice && cost >= price;
    const lowMargin = hasPrice && !unprofitable && marginPercent < 30;

    return (
        <PageHeader
            backLink={{
                to: isDish ? "/dish" : "/preparations",
                label: isDish ? "Вернуться в меню" : "Вернуться к заготовкам"
            }}
            title={ownerName || `${ownerLabel} #${ownerId}`}
            description={`Техкарта · ${ownerLabel.toLowerCase()}. Укажите состав на одну порцию — себестоимость пересчитается сама.`}
            stats={[
                { label: "Себестоимость", value: `${formatMoney(cost)} ₽`, tone: unprofitable ? "danger" : undefined },
                isDish
                    ? { label: "Цена продажи", value: price == null ? "Не указана" : `${formatMoney(price)} ₽` }
                    : { label: "Выход партии", value: outputWeight == null ? "Не указан" : `${formatQuantity(outputWeight)} г` },
                hasPrice
                    ? {
                        label: "Наценка",
                        value: `${formatMoney(margin)} ₽ · ${marginPercent}%`,
                        tone: unprofitable ? "danger" : lowMargin ? "warning" : undefined
                    }
                    : null,
                { label: "Позиций", value: itemCount }
            ]}
        >
            {unprofitable && (
                <p className={styles.costWarning} role="alert">
                    Себестоимость не ниже цены продажи: блюдо продаётся в убыток.
                    Проверьте количество ингредиентов и единицы продуктов или поднимите цену.
                </p>
            )}
        </PageHeader>
    );
}
