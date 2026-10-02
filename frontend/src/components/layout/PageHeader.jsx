import { Link } from "react-router-dom";
import styles from "./PageHeader.module.css";

/**
 * Компактная шапка раздела: название, короткое пояснение, ключевые счётчики
 * и действия. Заменяет большие баннеры, чтобы рабочая часть была на первом экране.
 */
export default function PageHeader({
    title,
    description,
    backLink,
    stats = [],
    actions,
    children
}) {
    const visibleStats = stats.filter(Boolean);
    return (
        <header className={styles.pageHeader}>
            <div className={styles.headingRow}>
                <div className={styles.copy}>
                    {backLink && (
                        <Link className={styles.backLink} to={backLink.to}>
                            ← {backLink.label}
                        </Link>
                    )}
                    <h1 className={styles.title}>{title}</h1>
                    {description && <p className={styles.description}>{description}</p>}
                </div>
                {actions && <div className={styles.actions}>{actions}</div>}
            </div>

            {visibleStats.length > 0 && (
                <dl className={styles.stats}>
                    {visibleStats.map((stat) => (
                        <div
                            key={stat.label}
                            className={stat.tone ? styles[`stat_${stat.tone}`] : undefined}
                        >
                            <dt>{stat.label}</dt>
                            <dd>{stat.value}</dd>
                        </div>
                    ))}
                </dl>
            )}
            {children}
        </header>
    );
}
