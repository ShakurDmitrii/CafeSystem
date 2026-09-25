import { useCallback, useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import styles from "./DesktopStartupGate.module.css";

export default function DesktopStartupGate({ children }) {
    const desktop = isTauri();
    const childWindow = window.location.pathname.startsWith("/desktop-print/")
        || window.location.pathname.startsWith("/kitchen-display/");
    const managesServices = desktop && !childWindow;
    const [state, setState] = useState(() => managesServices
        ? { phase: "starting", error: "" }
        : { phase: "ready", error: "" });

    const start = useCallback(async () => {
        if (!managesServices) return;
        setState({ phase: "starting", error: "" });
        try {
            await invoke("start_local_services");
            setState({ phase: "ready", error: "" });
        } catch (error) {
            setState({ phase: "error", error: String(error) });
        }
    }, [managesServices]);

    useEffect(() => {
        start();
    }, [start]);

    if (state.phase === "ready") return children;

    return (
        <main className={styles.page}>
            <section className={styles.card} aria-live="polite">
                <span className={styles.brandMark} aria-hidden="true">C</span>
                {state.phase === "starting" ? (
                    <>
                        <div className={styles.spinner} aria-hidden="true" />
                        <p className={styles.kicker}>Локальная система</p>
                        <h1>Запускаем CafeHelp</h1>
                        <p className={styles.description}>
                            Проверяем Docker и подготавливаем базу, сервер и рабочие сервисы. Первый запуск может занять несколько минут.
                        </p>
                    </>
                ) : (
                    <>
                        <span className={styles.errorIcon} aria-hidden="true">!</span>
                        <p className={styles.kicker}>Требуется внимание</p>
                        <h1>Не удалось запустить CafeHelp</h1>
                        <p className={styles.description}>{state.error}</p>
                        <button type="button" className={styles.retryButton} onClick={start}>
                            Повторить запуск
                        </button>
                        <p className={styles.hint}>Убедитесь, что Docker Desktop запущен и WSL 2 доступен.</p>
                    </>
                )}
            </section>
        </main>
    );
}
