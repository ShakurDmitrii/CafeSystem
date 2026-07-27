import { Link } from "react-router-dom";
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
    const backTo = isDish ? "/dish" : "/preparations";
    const ownerLabel = isDish ? "Блюдо" : "Заготовка";

    return (
        <header className={styles.hero}>
            <div className={styles.heroCopy}>
                <Link className={styles.backLink} to={backTo}>
                    ← {isDish ? "Вернуться в меню" : "Вернуться к заготовкам"}
                </Link>
                <p className={styles.eyebrow}>Технологическая карта · {ownerLabel}</p>
                <h1 className={styles.title}>{ownerName || `${ownerLabel} #${ownerId}`}</h1>
                <p className={styles.subtitle}>
                    Соберите точный состав, укажите отход и сразу проверьте итоговую себестоимость.
                </p>
            </div>

            <div className={styles.recipeTicket} aria-label="Сводка технологической карты">
                <div className={styles.ticketLabel} aria-hidden="true">ТЕХКАРТА</div>
                <dl className={styles.ticketStats}>
                    <div>
                        <dt>Себестоимость</dt>
                        <dd>{formatMoney(totalCost)} ₽</dd>
                    </div>
                    <div>
                        <dt>{isDish ? "Цена продажи" : "Выход партии"}</dt>
                        <dd>
                            {isDish
                                ? dishPrice == null ? "Не указана" : `${formatMoney(dishPrice)} ₽`
                                : outputWeight == null ? "Не указан" : `${formatQuantity(outputWeight)} г`}
                        </dd>
                    </div>
                    <div>
                        <dt>Позиций</dt>
                        <dd>{itemCount}</dd>
                    </div>
                </dl>
            </div>
        </header>
    );
}
