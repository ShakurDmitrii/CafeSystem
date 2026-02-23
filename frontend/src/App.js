import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Link, Navigate } from 'react-router-dom';
import './App.css';
import { clearAuth, getAuth, hasRole } from "./auth";

// Страницы
import SuppliersPage from './pages/SuppliersPage/SuppliersPage';
import SupplierProductPage from "./pages/SuppliersPage/SupplierProductPage";
import ConsignmentNotePage from "./pages/ConsignmentNote/ConsignmentNotePage";
import PrintConsignmentNotePage from "./pages/ConsignmentNote/PrintConsignmentNotePage";
import CashierPage from "./pages/SuppliersPage/CashierPages/CashierPage";
import PersonPage from "./pages/SuppliersPage/PersonPage";
import DishPage from "./pages/SuppliersPage/CashierPages/DishPage";
import TechCardPage from "./pages/TechCard/TechCardPage";
import WarehousesPage from "./pages/Warehouse/WarehousesPage";
import MovementPage from "./pages/Warehouse/Movement";
import ClientsPage from "./pages/SuppliersPage/ClientPages/ClientsPage";
import MlPage from "./pages/MLPanel/MlPage";
import LoginPage from "./pages/Auth/LoginPage";

function ProtectedRoute({ auth, roles, element }) {
    if (!auth) return <Navigate to="/login" replace />;
    if (roles && roles.length > 0 && !hasRole(auth, roles)) return <Navigate to="/" replace />;
    return element;
}

function App() {
    const [burgerOpen, setBurgerOpen] = useState(false);
    const [auth, setAuth] = useState(getAuth());

    const toggleBurger = () => setBurgerOpen(!burgerOpen);
    const isOwner = hasRole(auth, ["OWNER"]);
    const isWorkerOrOwner = hasRole(auth, ["WORKER", "OWNER"]);

    React.useEffect(() => {
        const onUnauthorized = () => setAuth(null);
        window.addEventListener("auth:unauthorized", onUnauthorized);
        return () => window.removeEventListener("auth:unauthorized", onUnauthorized);
    }, []);

    const handleLogout = () => {
        clearAuth();
        setAuth(null);
        setBurgerOpen(false);
    };

    return (
        <Router>
            <div className="App">
                {auth && (
                    <header className="App-header">
                        {/* Основной Navbar */}
                        <nav className="App-nav">
                            <Link to="/" className="App-link">Домашняя</Link>
                            {isWorkerOrOwner && <Link to="/cashier" className="App-link">Касса</Link>}
                            {isWorkerOrOwner && <Link to="/dish" className="App-link">Меню</Link>}
                            {isOwner && <Link to="/warehouse" className="App-link">Склады</Link>}
                            {isOwner && <Link to="/movements" className="App-link">Движения</Link>}
                            {isWorkerOrOwner && <Link to="/clients" className="App-link">Клиенты</Link>}
                        </nav>

                        <div className="user-bar">
                            <span className="user-chip">{auth.personName || auth.username} ({auth.role})</span>
                            <button className="logout-btn" onClick={handleLogout}>Выйти</button>
                        </div>

                        {/* Бургер-меню */}
                        <div className="burger-menu">
                            <button className={`burger-button ${burgerOpen ? 'open' : ''}`} onClick={toggleBurger}>
                                <span></span>
                                <span></span>
                                <span></span>
                            </button>

                            <div className={`burger-overlay ${burgerOpen ? 'show' : ''}`} onClick={toggleBurger}></div>

                            <div className={`burger-dropdown ${burgerOpen ? 'show' : ''}`}>
                                {isOwner && <Link to="/suppliers" className="App-link" onClick={toggleBurger}>Поставщики</Link>}
                                {isOwner && <Link to="/person" className="App-link" onClick={toggleBurger}>Персонал</Link>}
                                {isOwner && <Link to="/consigment" className="App-link" onClick={toggleBurger}>Создать накладную</Link>}
                                {isOwner && <Link to="/ml" className="App-link" onClick={toggleBurger}>AI Аналитика</Link>}
                                <button className="logout-btn burger-logout" onClick={handleLogout}>Выйти</button>
                            </div>
                        </div>
                    </header>
                )}

                <main className="App-content">
                    <Routes>
                        <Route path="/login" element={auth ? <Navigate to="/" replace /> : <LoginPage onSuccess={setAuth} />} />
                        <Route path="/consignment-notes/print/:id" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<PrintConsignmentNotePage />} />} />
                        <Route path="/tech-card/:dishId" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<TechCardPage />} />} />
                        <Route path="/suppliers" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<SuppliersPage />} />} />
                        <Route path="/clients" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<ClientsPage />} />} />
                        <Route path="/suppliers/:id" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<SupplierProductPage />} />} />
                        <Route path="/consigment" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<ConsignmentNotePage />} />} />
                        <Route path="/cashier" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<CashierPage />} />} />
                        <Route path="/person" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<PersonPage />} />} />
                        <Route path="/dish" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<DishPage />} />} />
                        <Route path="/warehouse" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<WarehousesPage />} />} />
                        <Route path="/movements" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<MovementPage />} />} />
                        <Route path="/ml" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<MlPage />} />} />
                        <Route path="/" element={<ProtectedRoute auth={auth} element={<div style={{ padding: '2rem' }}><h2>Домашняя страница</h2></div>} />} />
                        <Route path="*" element={<Navigate to={auth ? "/" : "/login"} replace />} />
                    </Routes>
                </main>
            </div>
        </Router>
    );
}

export default App;
