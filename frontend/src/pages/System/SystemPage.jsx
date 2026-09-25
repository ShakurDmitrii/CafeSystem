import { useCallback, useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import styles from "./SystemPage.module.css";

const SERVICE_LABELS = {
    "cafehelp-db": "Основная база",
    "cafehelp-tax-db": "Налоговая база",
    "cafehelp-backend": "Сервер CafeHelp",
    "cafehelp-pymodule": "AI-модуль",
    "cafehelp-minio": "Файлы и изображения",
    "cafehelp-vkbot": "VK-бот"
};

const CORE_SERVICE_NAMES = [
    "cafehelp-db",
    "cafehelp-tax-db",
    "cafehelp-backend",
    "cafehelp-pymodule",
    "cafehelp-minio"
];

function CheckCard({ title, check }) {
    const available = Boolean(check?.available);
    return (
        <article className={`${styles.checkCard} ${available ? styles.ok : styles.problem}`}>
            <span className={styles.statusDot} aria-hidden="true" />
            <div>
                <strong>{title}</strong>
                <p>{check?.detail || "Проверка не выполнена"}</p>
            </div>
            <span>{available ? "Готово" : "Требуется настройка"}</span>
        </article>
    );
}

function defaultBackupName() {
    const value = new Date();
    const pad = (number) => String(number).padStart(2, "0");
    return `CafeHelp_${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}_${pad(value.getHours())}-${pad(value.getMinutes())}.cafehelp-backup`;
}

export default function SystemPage() {
    const desktop = isTauri();
    const [status, setStatus] = useState(null);
    const [loading, setLoading] = useState(desktop);
    const [action, setAction] = useState("");
    const [message, setMessage] = useState(null);
    const [vkEnabled, setVkEnabled] = useState(false);
    const [vkGroupId, setVkGroupId] = useState("");
    const [vkGroupToken, setVkGroupToken] = useState("");

    const loadStatus = useCallback(async () => {
        if (!desktop) return;
        setLoading(true);
        try {
            const nextStatus = await invoke("get_system_status");
            setStatus(nextStatus);
            setVkEnabled(Boolean(nextStatus?.vkBot?.enabled));
            setVkGroupId(nextStatus?.vkBot?.groupId || "");
        } catch (error) {
            setMessage({ type: "error", text: String(error) });
        } finally {
            setLoading(false);
        }
    }, [desktop]);

    useEffect(() => {
        loadStatus();
    }, [loadStatus]);

    const startServices = async () => {
        setAction("start");
        setMessage(null);
        try {
            const result = await invoke("start_local_services");
            setMessage({ type: "success", text: result.message });
            await loadStatus();
        } catch (error) {
            setMessage({ type: "error", text: String(error) });
        } finally {
            setAction("");
        }
    };

    const createBackup = async () => {
        setAction("backup");
        setMessage(null);
        try {
            const { save } = await import("@tauri-apps/plugin-dialog");
            const destination = await save({
                title: "Сохранить резервную копию CafeHelp",
                defaultPath: defaultBackupName(),
                filters: [{ name: "Резервная копия CafeHelp", extensions: ["cafehelp-backup"] }]
            });
            if (!destination) return;
            const result = await invoke("create_backup", { destination });
            const megabytes = (Number(result.sizeBytes || 0) / 1024 / 1024).toFixed(1);
            setMessage({ type: "success", text: `Резервная копия создана: ${result.path} (${megabytes} МБ)` });
        } catch (error) {
            setMessage({ type: "error", text: String(error) });
        } finally {
            setAction("");
        }
    };

    const installDriver = async () => {
        setAction("driver");
        setMessage(null);
        try {
            const result = await invoke("install_printer_driver");
            setMessage({ type: "success", text: result.message });
        } catch (error) {
            setMessage({ type: "error", text: String(error) });
        } finally {
            setAction("");
        }
    };

    const saveVkBot = async () => {
        setAction("vkbot");
        setMessage(null);
        try {
            await invoke("configure_vk_bot", {
                enabled: vkEnabled,
                groupId: vkGroupId,
                groupToken: vkGroupToken.trim() || null
            });
            const result = await invoke("start_local_services");
            setVkGroupToken("");
            setMessage({ type: "success", text: result.message });
            await loadStatus();
        } catch (error) {
            setMessage({ type: "error", text: String(error) });
        } finally {
            setAction("");
        }
    };

    const coreServicesRunning = CORE_SERVICE_NAMES.every((name) =>
        status?.services?.some((service) => service.name === name && service.running)
    );

    if (!desktop) {
        return (
            <div className={styles.page}>
                <section className={styles.desktopNotice}>
                    <span>Системные инструменты</span>
                    <h1>Откройте CafeHelp через локальное приложение</h1>
                    <p>Управление контейнерами, резервные копии и установка драйвера доступны только в защищённой Tauri-оболочке.</p>
                </section>
            </div>
        );
    }

    return (
        <div className={styles.page}>
            <section className={styles.hero}>
                <div>
                    <p className={styles.kicker}>Локальная установка</p>
                    <h1>Система и резервные копии</h1>
                    <p>Проверка компонентов CafeHelp на этом компьютере и безопасное сохранение рабочих данных.</p>
                </div>
                <button type="button" className={styles.refreshButton} onClick={loadStatus} disabled={loading || Boolean(action)}>
                    {loading ? "Проверяем…" : "Обновить состояние"}
                </button>
            </section>

            {message ? <div className={message.type === "error" ? styles.errorMessage : styles.successMessage}>{message.text}</div> : null}

            <section className={styles.section} aria-labelledby="environment-title">
                <header>
                    <div>
                        <p className={styles.kicker}>Окружение</p>
                        <h2 id="environment-title">Готовность компьютера</h2>
                    </div>
                    {status ? <span>{status.platform} · {status.architecture}</span> : null}
                </header>
                <div className={styles.checkGrid}>
                    <CheckCard title="Docker" check={status?.dockerCli} />
                    <CheckCard title="Docker Engine" check={status?.dockerEngine} />
                    <CheckCard title="Docker Compose" check={status?.dockerCompose} />
                    <CheckCard title="WSL 2" check={status?.wsl} />
                </div>
            </section>

            <section className={styles.section} aria-labelledby="services-title">
                <header>
                    <div>
                        <p className={styles.kicker}>Локальные процессы</p>
                        <h2 id="services-title">Сервисы CafeHelp</h2>
                    </div>
                    <button type="button" className={styles.primaryButton} onClick={startServices} disabled={Boolean(action) || !status?.dockerEngine?.available}>
                        {action === "start" ? "Запускаем…" : "Запустить сервисы"}
                    </button>
                </header>
                <div className={styles.serviceList}>
                    {(status?.services || []).map((service) => (
                        <div key={service.name} className={styles.serviceRow}>
                            <span className={service.running ? styles.serviceUp : styles.serviceDown} aria-hidden="true" />
                            <strong>{SERVICE_LABELS[service.name] || service.name}</strong>
                            <small>{service.status}</small>
                        </div>
                    ))}
                </div>
            </section>

            <section className={styles.section} aria-labelledby="vkbot-title">
                <header>
                    <div>
                        <p className={styles.kicker}>Интеграция</p>
                        <h2 id="vkbot-title">VK-бот для клиентов</h2>
                    </div>
                    <span className={vkEnabled ? styles.integrationEnabled : styles.integrationDisabled}>
                        {vkEnabled ? "Включён" : "Выключен"}
                    </span>
                </header>

                <div className={styles.integrationIntro}>
                    Бот показывает клиенту последний заказ, историю и долги. Для работы нужен интернет и включённый Long Poll API сообщества VK.
                </div>

                <label className={styles.toggleRow}>
                    <input
                        type="checkbox"
                        checked={vkEnabled}
                        onChange={(event) => setVkEnabled(event.target.checked)}
                    />
                    <span>
                        <strong>Запускать VK-бот вместе с CafeHelp</strong>
                        <small>При выключении контейнер бота будет остановлен.</small>
                    </span>
                </label>

                <div className={styles.integrationFields}>
                    <label>
                        <span>ID сообщества VK</span>
                        <input
                            type="text"
                            inputMode="numeric"
                            autoComplete="off"
                            placeholder="Например, 123456789"
                            value={vkGroupId}
                            onChange={(event) => setVkGroupId(event.target.value)}
                        />
                    </label>
                    <label>
                        <span>Токен сообщества</span>
                        <input
                            type="password"
                            autoComplete="new-password"
                            placeholder={status?.vkBot?.groupTokenConfigured ? "Токен сохранён — оставьте поле пустым" : "Вставьте токен группы VK"}
                            value={vkGroupToken}
                            onChange={(event) => setVkGroupToken(event.target.value)}
                        />
                        <small>
                            {status?.vkBot?.groupTokenConfigured
                                ? "Токен уже настроен. Введите новый только для замены."
                                : "Токен хранится локально и не входит в резервную копию."}
                        </small>
                    </label>
                </div>

                <button
                    type="button"
                    className={styles.primaryButton}
                    onClick={saveVkBot}
                    disabled={Boolean(action) || !status?.dockerEngine?.available}
                >
                    {action === "vkbot" ? "Применяем настройки…" : "Сохранить и применить"}
                </button>
            </section>

            <div className={styles.actionsGrid}>
                <section className={styles.actionCard}>
                    <span className={styles.actionNumber}>01</span>
                    <h2>Резервная копия</h2>
                    <p>Основная и налоговая базы, изображения MinIO, модели аналитики, manifest и SHA-256 в одном архиве.</p>
                    <button type="button" className={styles.primaryButton} onClick={createBackup} disabled={Boolean(action) || !coreServicesRunning}>
                        {action === "backup" ? "Создаём копию…" : "Создать резервную копию"}
                    </button>
                </section>
                <section className={styles.actionCard}>
                    <span className={styles.actionNumber}>02</span>
                    <h2>Термопринтер</h2>
                    <p>Запуск оригинального подписанного установщика Rongta для архитектуры этого компьютера.</p>
                    <button type="button" className={styles.secondaryButton} onClick={installDriver} disabled={Boolean(action) || !status?.printerDriverAvailable}>
                        {action === "driver" ? "Открываем…" : status?.printerDriverAvailable ? "Установить драйвер" : "Драйвер не включён в сборку"}
                    </button>
                </section>
            </div>
        </div>
    );
}
