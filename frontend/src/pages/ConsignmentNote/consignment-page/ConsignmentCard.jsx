import { Link } from "react-router-dom";
import { formatDate, formatMoney } from "./consignmentUtils";
import styles from "../ConsignmentNotePage.module.css";

export default function ConsignmentCard({
    row,
    calculating,
    onOpen,
    onCalculate,
    onDelete
}) {
    return (
        <article className={styles.noteCard}>
            <div className={styles.noteCardTop}>
                <div>
                    <p className={styles.noteNumber}>Накладная № {row.id}</p>
                    <h3>{row.supplierName}</h3>
                </div>
                <span className={row.posted ? styles.postedBadge : styles.draftBadge}>
                    {row.posted ? "Проведена" : "Черновик"}
                </span>
            </div>

            <dl className={styles.noteFacts}>
                <div>
                    <dt>Дата поставки</dt>
                    <dd>{formatDate(row.date)}</dd>
                </div>
                <div>
                    <dt>Сумма</dt>
                    <dd>{formatMoney(row.total)}</dd>
                </div>
                <div>
                    <dt>Склад</dt>
                    <dd>{row.warehouseName}</dd>
                </div>
            </dl>

            <div className={styles.noteActions}>
                <button
                    type="button"
                    className={styles.primaryButton}
                    onClick={() => onOpen(row.id)}
                >
                    {row.posted ? "Посмотреть позиции" : "Продолжить накладную"}
                </button>
                <Link
                    className={styles.secondaryLink}
                    to={`/consignment-notes/print/${row.id}`}
                >
                    Печатная форма
                </Link>
                {!row.posted ? (
                    <>
                        <button
                            type="button"
                            className={styles.ghostButton}
                            onClick={() => onCalculate(row.id)}
                            disabled={calculating}
                        >
                            {calculating ? "Считаем…" : "Пересчитать"}
                        </button>
                        <button
                            type="button"
                            className={styles.dangerTextButton}
                            onClick={() => onDelete(row)}
                        >
                            Удалить
                        </button>
                    </>
                ) : null}
            </div>
        </article>
    );
}
