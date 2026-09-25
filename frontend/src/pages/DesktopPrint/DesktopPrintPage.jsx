import { useMemo, useRef } from "react";
import { useParams } from "react-router-dom";
import { takePrintJob } from "../../utils/desktopWindows";
import styles from "./DesktopPrintPage.module.css";

export default function DesktopPrintPage() {
    const { jobId } = useParams();
    const frameRef = useRef(null);
    const html = useMemo(() => takePrintJob(jobId), [jobId]);

    if (!html) {
        return <main className={styles.error}>Документ печати не найден. Закройте это окно и повторите печать.</main>;
    }

    return (
        <iframe
            ref={frameRef}
            className={styles.frame}
            title="Документ печати"
            srcDoc={html}
            onLoad={() => window.setTimeout(() => frameRef.current?.contentWindow?.print(), 180)}
        />
    );
}
