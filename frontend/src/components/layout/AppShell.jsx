import { useEffect, useRef, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { hasRole } from "../../auth";
import styles from "./AppShell.module.css";

const primaryNavigation = [
    { to: "/", label: "Главная", roles: ["WORKER", "OWNER"], end: true },
    { to: "/cashier", label: "Касса", roles: ["WORKER", "OWNER"] },
    { to: "/dish", label: "Меню", roles: ["WORKER", "OWNER"] },
    { to: "/clients", label: "Клиенты", roles: ["WORKER", "OWNER"] }
];

const managementNavigation = [
    { to: "/products", label: "Продукты", roles: ["OWNER"] },
    { to: "/preparations", label: "Заготовки", roles: ["OWNER"] },
    { to: "/warehouse", label: "Склады", roles: ["OWNER"] },
    { to: "/movements", label: "Движения", roles: ["OWNER"] },
    { to: "/suppliers", label: "Поставщики", roles: ["OWNER"] },
    { to: "/person", label: "Персонал", roles: ["OWNER"] },
    { to: "/consigment", label: "Накладные", roles: ["OWNER"] },
    { to: "/ml", label: "AI-аналитика", roles: ["OWNER"] },
    { to: "/tax", label: "Налог", roles: ["OWNER"] }
];

const allNavigation = [...primaryNavigation, ...managementNavigation];

function getVisibleItems(items, auth) {
    return items.filter((item) => hasRole(auth, item.roles));
}

function NavigationGroup({ title, items, auth, onNavigate }) {
    const visibleItems = getVisibleItems(items, auth);
    if (visibleItems.length === 0) return null;

    return (
        <div className={styles.navGroup}>
            <p className={styles.navLabel}>{title}</p>
            <nav className={styles.navigation} aria-label={title}>
                {visibleItems.map((item) => (
                    <NavLink
                        key={item.to}
                        to={item.to}
                        end={item.end}
                        onClick={onNavigate}
                        className={({ isActive }) => (
                            `${styles.navLink} ${isActive ? styles.navLinkActive : ""}`
                        )}
                    >
                        <span className={styles.navMarker} aria-hidden="true" />
                        <span>{item.label}</span>
                    </NavLink>
                ))}
            </nav>
        </div>
    );
}

function NavigationContent({ auth, onNavigate }) {
    return (
        <>
            <NavigationGroup
                title="Работа"
                items={primaryNavigation}
                auth={auth}
                onNavigate={onNavigate}
            />
            <NavigationGroup
                title="Управление"
                items={managementNavigation}
                auth={auth}
                onNavigate={onNavigate}
            />
        </>
    );
}

function Brand() {
    return (
        <NavLink to="/" className={styles.brand} aria-label="CafeHelp, на главную">
            <span className={styles.brandMark} aria-hidden="true">C</span>
            <span>
                <strong>CafeHelp</strong>
                <small>Управление кафе</small>
            </span>
        </NavLink>
    );
}

export default function AppShell({ auth, onLogout, children }) {
    const location = useLocation();
    const [menuOpen, setMenuOpen] = useState(false);
    const closeButtonRef = useRef(null);
    const currentPage = allNavigation.find((item) => (
        item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)
    ));
    const currentPageLabel = (
        location.pathname.startsWith("/tech-card/")
        || location.pathname.startsWith("/preparation-tech-card/")
    )
        ? "Техкарта"
        : location.pathname.startsWith("/consignment-notes/print/")
            ? "Печатная форма"
        : currentPage?.label || "CafeHelp";

    useEffect(() => {
        setMenuOpen(false);
    }, [location.pathname]);

    useEffect(() => {
        if (!menuOpen) return undefined;

        const previouslyFocusedElement = document.activeElement;
        const handleKeyDown = (event) => {
            if (event.key === "Escape") setMenuOpen(false);
        };

        closeButtonRef.current?.focus();
        document.body.style.overflow = "hidden";
        document.addEventListener("keydown", handleKeyDown);
        return () => {
            document.body.style.overflow = "";
            document.removeEventListener("keydown", handleKeyDown);
            previouslyFocusedElement?.focus();
        };
    }, [menuOpen]);

    return (
        <div className={styles.shell}>
            <a className={styles.skipLink} href="#main-content">
                Перейти к содержимому
            </a>

            <aside className={styles.sidebar}>
                <Brand />
                <div className={styles.sidebarNavigation}>
                    <NavigationContent auth={auth} />
                </div>
                <div className={styles.sidebarFooter}>
                    <span className={styles.role}>{auth.role === "OWNER" ? "Владелец" : "Сотрудник"}</span>
                    <strong>{auth.personName || auth.username}</strong>
                    <button type="button" className={styles.logoutButton} onClick={onLogout}>
                        Выйти
                    </button>
                </div>
            </aside>

            <div className={styles.workspace}>
                <header className={styles.mobileHeader}>
                    <Brand />
                    <button
                        type="button"
                        className={styles.menuButton}
                        aria-label="Открыть меню"
                        aria-expanded={menuOpen}
                        aria-controls="mobile-navigation"
                        onClick={() => setMenuOpen(true)}
                    >
                        <span aria-hidden="true" />
                        <span aria-hidden="true" />
                        <span aria-hidden="true" />
                    </button>
                </header>

                <header className={styles.topbar}>
                    <div>
                        <p className={styles.topbarLabel}>Текущий раздел</p>
                        <p className={styles.topbarTitle}>{currentPageLabel}</p>
                    </div>
                    <div className={styles.userSummary}>
                        <span className={styles.userAvatar} aria-hidden="true">
                            {(auth.personName || auth.username || "C").slice(0, 1).toUpperCase()}
                        </span>
                        <span>
                            <strong>{auth.personName || auth.username}</strong>
                            <small>{auth.role === "OWNER" ? "Владелец" : "Сотрудник"}</small>
                        </span>
                    </div>
                </header>

                <main id="main-content" className={styles.content} tabIndex="-1">
                    {children}
                </main>
            </div>

            <button
                type="button"
                className={`${styles.mobileOverlay} ${menuOpen ? styles.mobileOverlayVisible : ""}`}
                aria-label="Закрыть меню"
                aria-hidden={!menuOpen}
                tabIndex={menuOpen ? 0 : -1}
                onClick={() => setMenuOpen(false)}
            />
            <aside
                id="mobile-navigation"
                className={`${styles.mobileDrawer} ${menuOpen ? styles.mobileDrawerOpen : ""}`}
                aria-hidden={!menuOpen}
                aria-label={menuOpen ? "Меню навигации" : undefined}
                aria-modal={menuOpen ? "true" : undefined}
                inert={menuOpen ? undefined : true}
                role={menuOpen ? "dialog" : undefined}
            >
                {menuOpen ? (
                    <>
                        <div className={styles.drawerHeader}>
                            <Brand />
                            <button
                                ref={closeButtonRef}
                                type="button"
                                className={styles.closeButton}
                                aria-label="Закрыть меню"
                                onClick={() => setMenuOpen(false)}
                            >
                                <span aria-hidden="true">×</span>
                            </button>
                        </div>
                        <div className={styles.drawerNavigation}>
                            <NavigationContent auth={auth} onNavigate={() => setMenuOpen(false)} />
                        </div>
                        <div className={styles.drawerFooter}>
                            <div>
                                <strong>{auth.personName || auth.username}</strong>
                                <span>{auth.role === "OWNER" ? "Владелец" : "Сотрудник"}</span>
                            </div>
                            <button type="button" className={styles.logoutButton} onClick={onLogout}>
                                Выйти
                            </button>
                        </div>
                    </>
                ) : null}
            </aside>
        </div>
    );
}
