import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import './App.css';
import { API_BASE_URL, clearAuth, getAuth, hasRole } from "./auth";
import AppShell from "./components/layout/AppShell";

// Страницы
import SuppliersPage from './pages/SuppliersPage/SuppliersPage';
import SupplierProductPage from "./pages/SuppliersPage/SupplierProductPage";
import ConsignmentNotePage from "./pages/ConsignmentNote/ConsignmentNotePage";
import PrintConsignmentNotePage from "./pages/ConsignmentNote/PrintConsignmentNotePage";
import CashierPage from "./pages/SuppliersPage/CashierPages/CashierPage";
import PersonPage from "./pages/SuppliersPage/PersonPage";
import DishPage from "./pages/SuppliersPage/CashierPages/DishPage";
import TechCardPage from "./pages/TechCard/TechCardPage";
import PreparationsPage from "./pages/Preparations/PreparationsPage";
import WarehousesPage from "./pages/Warehouse/WarehousesPage";
import MovementPage from "./pages/Warehouse/Movement";
import ClientsPage from "./pages/SuppliersPage/ClientPages/ClientsPage";
import MlPage from "./pages/MLPanel/MlPage";
import LoginPage from "./pages/Auth/LoginPage";
import ProductsPage from "./pages/Products/ProductsPage";
import HomePage from "./pages/Home/HomePage";
import KitchenDisplayPage from "./pages/SuppliersPage/CashierPages/KitchenDisplayPage";
import TaxPage from "./pages/Tax/TaxPage";

function ProtectedRoute({ auth, roles, element }) {
    if (!auth) return <Navigate to="/login" replace />;
    if (roles && roles.length > 0 && !hasRole(auth, roles)) return <Navigate to="/" replace />;
    return element;
}

function AppLayout({ auth, setAuth }) {
    const location = useLocation();
    const isKitchenDisplay = location.pathname.startsWith("/kitchen-display");

    const handleLogout = () => {
        clearAuth();
        setAuth(null);
    };

    const appRoutes = (
        <Routes>
                    <Route path="/login" element={auth ? <Navigate to="/" replace /> : <LoginPage onSuccess={setAuth} />} />
                    <Route path="/consignment-notes/print/:id" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<PrintConsignmentNotePage />} />} />
                    <Route path="/tech-card/:dishId" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<TechCardPage />} />} />
                    <Route path="/preparation-tech-card/:preparationId" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<TechCardPage />} />} />
                    <Route path="/kitchen-display/:shiftId" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<KitchenDisplayPage auth={auth} />} />} />
                    <Route path="/suppliers" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<SuppliersPage />} />} />
                    <Route path="/clients" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<ClientsPage />} />} />
                    <Route path="/suppliers/:id" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<SupplierProductPage />} />} />
                    <Route path="/consigment" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<ConsignmentNotePage />} />} />
                    <Route path="/cashier" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<CashierPage />} />} />
                    <Route path="/person" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<PersonPage />} />} />
                    <Route path="/dish" element={<ProtectedRoute auth={auth} roles={["WORKER", "OWNER"]} element={<DishPage />} />} />
                    <Route path="/warehouse" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<WarehousesPage />} />} />
                    <Route path="/products" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<ProductsPage />} />} />
                    <Route path="/preparations" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<PreparationsPage />} />} />
                    <Route path="/movements" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<MovementPage />} />} />
                    <Route path="/ml" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<MlPage />} />} />
                    <Route path="/tax" element={<ProtectedRoute auth={auth} roles={["OWNER"]} element={<TaxPage />} />} />
                    <Route path="/" element={<ProtectedRoute auth={auth} element={<HomePage auth={auth} />} />} />
                    <Route path="*" element={<Navigate to={auth ? "/" : "/login"} replace />} />
        </Routes>
    );

    if (auth && !isKitchenDisplay) {
        return (
            <AppShell auth={auth} onLogout={handleLogout}>
                {appRoutes}
            </AppShell>
        );
    }

    return (
        <div className="App">
            <main className={`App-content ${isKitchenDisplay ? "App-content--full" : "App-content--auth"}`}>
                {appRoutes}
            </main>
        </div>
    );
}

function App() {
    const [auth, setAuth] = useState(getAuth());

    React.useEffect(() => {
        const onUnauthorized = () => setAuth(null);
        window.addEventListener("auth:unauthorized", onUnauthorized);
        return () => window.removeEventListener("auth:unauthorized", onUnauthorized);
    }, []);

    React.useEffect(() => {
        const verifyAuth = async () => {
            if (!auth?.accessToken) return;
            try {
                const res = await fetch(`${API_BASE_URL}/api/auth/me`, {
                    headers: { Authorization: `Bearer ${auth.accessToken}` }
                });
                if (!res.ok) {
                    clearAuth();
                    setAuth(null);
                }
            } catch {
                clearAuth();
                setAuth(null);
            }
        };
        verifyAuth();
    }, [auth]);

    return (
        <Router>
            <AppLayout auth={auth} setAuth={setAuth} />
        </Router>
    );
}

export default App;
