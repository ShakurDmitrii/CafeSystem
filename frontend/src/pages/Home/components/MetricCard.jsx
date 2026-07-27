import styles from "../HomePage.module.css";

export default function MetricCard({ label, value, note, tone = "neutral", marker }) {
    return (
        <article className={`${styles.metricCard} ${styles[`metricCard_${tone}`]}`}>
            <div className={styles.metricTopline}>
                <span className={styles.metricMarker} aria-hidden="true">{marker}</span>
                <p className={styles.metricLabel}>{label}</p>
            </div>
            <p className={styles.metricValue}>{value}</p>
            <p className={styles.metricNote}>{note}</p>
        </article>
    );
}
