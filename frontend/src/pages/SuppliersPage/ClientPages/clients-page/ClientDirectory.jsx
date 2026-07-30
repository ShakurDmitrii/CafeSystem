import styles from "../ClientsPage.module.css";
import { getInitials } from "./clientUtils";

export default function ClientDirectory({
    clients,
    loading,
    searchQuery,
    newClient,
    onSearchChange,
    onSearchSubmit,
    onResetSearch,
    onNewClientChange,
    onCreateClient,
    onSelectClient
}) {
    return (
        <div className={styles.directoryLayout}>
            <aside className={styles.createPanel}>
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.kicker}>Новая карточка</p>
                        <h2>Добавить гостя</h2>
                    </div>
                    <span className={styles.stepMark}>01</span>
                </div>
                <p className={styles.sectionLead}>
                    Имени достаточно. Телефон пригодится для быстрого поиска и связи.
                </p>
                <form className={styles.createForm} onSubmit={onCreateClient}>
                    <label htmlFor="client-name">
                        Имя гостя
                        <input
                            id="client-name"
                            name="fullName"
                            type="text"
                            autoComplete="name"
                            value={newClient.fullName}
                            onChange={(event) => onNewClientChange("fullName", event.target.value)}
                            placeholder="Например, Анна Петрова"
                            required
                        />
                    </label>
                    <label htmlFor="client-phone">
                        Телефон
                        <input
                            id="client-phone"
                            name="phone"
                            type="tel"
                            inputMode="tel"
                            autoComplete="tel"
                            value={newClient.number}
                            onChange={(event) => onNewClientChange("number", event.target.value)}
                            placeholder="+7 900 000-00-00"
                        />
                    </label>
                    <button
                        className={styles.primaryButton}
                        type="submit"
                        disabled={!newClient.fullName.trim()}
                    >
                        Создать карточку
                    </button>
                </form>
            </aside>

            <section className={styles.directoryPanel}>
                <div className={styles.sectionHeading}>
                    <div>
                        <p className={styles.kicker}>Каталог</p>
                        <h2>Гости заведения</h2>
                    </div>
                    <span className={styles.countBadge}>{clients.length}</span>
                </div>

                <form className={styles.searchForm} role="search" onSubmit={onSearchSubmit}>
                    <label className={styles.searchField} htmlFor="client-search">
                        <span className={styles.visuallyHidden}>Поиск гостей по имени</span>
                        <input
                            id="client-search"
                            name="q"
                            type="search"
                            autoComplete="off"
                            value={searchQuery}
                            onChange={(event) => onSearchChange(event.target.value)}
                            placeholder="Найти гостя по имени…"
                        />
                    </label>
                    <button className={styles.secondaryButton} type="submit">Найти</button>
                    {searchQuery && (
                        <button className={styles.textButton} type="button" onClick={onResetSearch}>
                            Сбросить
                        </button>
                    )}
                </form>

                {loading ? (
                    <div className={styles.stateCard} role="status">Загружаем гостевую книгу…</div>
                ) : clients.length === 0 ? (
                    <div className={styles.stateCard}>
                        <strong>{searchQuery ? "Никого не нашли" : "Гостевая книга пока пуста"}</strong>
                        <span>
                            {searchQuery
                                ? "Проверьте написание имени или сбросьте поиск."
                                : "Создайте первую карточку в форме слева."}
                        </span>
                    </div>
                ) : (
                    <div className={styles.clientGrid}>
                        {clients.map((client) => (
                            <article key={client.clientId} className={styles.clientCard}>
                                <div className={styles.clientStamp} aria-hidden="true">
                                    {getInitials(client.fullName)}
                                </div>
                                <div className={styles.clientCardCopy}>
                                    <span className={styles.cardId}>Гость #{client.clientId}</span>
                                    <h3>{client.fullName || "Без имени"}</h3>
                                    <p>{client.number || "Телефон не указан"}</p>
                                </div>
                                <button
                                    className={styles.cardAction}
                                    type="button"
                                    onClick={() => onSelectClient(client)}
                                    aria-label={`Открыть профиль: ${client.fullName || "гость без имени"}`}
                                >
                                    Открыть <span aria-hidden="true">→</span>
                                </button>
                            </article>
                        ))}
                    </div>
                )}
            </section>
        </div>
    );
}
