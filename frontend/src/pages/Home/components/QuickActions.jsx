import { Link } from "react-router-dom";
import { hasRole } from "../../../auth";
import styles from "../HomePage.module.css";

const actions = [
    {
        to: "/cashier",
        label: "Открыть кассу",
        description: "Смена, заказы и оплата",
        roles: ["WORKER", "OWNER"]
    },
    {
        to: "/dish",
        label: "Открыть меню",
        description: "Блюда, наборы и техкарты",
        roles: ["WORKER", "OWNER"]
    },
    {
        to: "/clients",
        label: "Найти клиента",
        description: "История заказов и долги",
        roles: ["WORKER", "OWNER"]
    },
    {
        to: "/warehouse",
        label: "Проверить склад",
        description: "Остатки и перемещения",
        roles: ["OWNER"]
    }
];

export default function QuickActions({ auth }) {
    const visibleActions = actions.filter((action) => hasRole(auth, action.roles));

    return (
        <section className={styles.quickActions} aria-labelledby="quick-actions-title">
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.sectionKicker}>Быстрый доступ</p>
                    <h2 id="quick-actions-title">Что нужно сделать?</h2>
                </div>
            </div>
            <div className={styles.actionGrid}>
                {visibleActions.map((action) => (
                    <Link key={action.to} to={action.to} className={styles.actionLink}>
                        <span>
                            <strong>{action.label}</strong>
                            <small>{action.description}</small>
                        </span>
                        <span className={styles.actionArrow} aria-hidden="true">→</span>
                    </Link>
                ))}
            </div>
        </section>
    );
}
