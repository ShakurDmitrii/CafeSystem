import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import './App.css';

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

function App() {
    const [burgerOpen, setBurgerOpen] = useState(false);

    const toggleBurger = () => setBurgerOpen(!burgerOpen);

    return (
        <Router>
            <div className="App">
                <header className="App-header">


                    {/* Основной Navbar */}
                    <nav className="App-nav">
                        <Link to="/" className="App-link">Домашняя</Link>
                        <Link to="/cashier" className="App-link">Касса</Link>
                        <Link to="/dish" className="App-link">Меню</Link>
                        <Link to="/warehouse" className="App-link">Склады</Link>
                        <Link to="/movements" className="App-link">Движения</Link>
                        <Link to="/clients" className="App-link">Клиенты</Link>
                    </nav>

                    {/* Бургер-меню */}
                    <div className="burger-menu">
                        <button className={`burger-button ${burgerOpen ? 'open' : ''}`} onClick={toggleBurger}>
                            <span></span>
                            <span></span>
                            <span></span>
                        </button>

                        <div className={`burger-overlay ${burgerOpen ? 'show' : ''}`} onClick={toggleBurger}></div>

                        <div className={`burger-dropdown ${burgerOpen ? 'show' : ''}`}>
                            <Link to="/suppliers" className="App-link" onClick={toggleBurger}>Поставщики</Link>
                            <Link to="/person" className="App-link" onClick={toggleBurger}>Персонал</Link>
                            <Link to="/consigment" className="App-link" onClick={toggleBurger}>Создать накладную</Link>
                            <Link to="/ml" className="App-link" onClick={toggleBurger}>🤖 AI Аналитика</Link>
                        </div>
                    </div>
                </header>

                <main className="App-content">
                    <Routes>
                        <Route path="/consignment-notes/print/:id" element={<PrintConsignmentNotePage />} />
                        <Route path="/tech-card/:dishId" element={<TechCardPage />} />
                        <Route path="/suppliers" element={<SuppliersPage />} />
                        <Route path="/clients" element={<ClientsPage />} />
                        <Route path="/suppliers/:id" element={<SupplierProductPage />} />
                        <Route path="/consigment" element={<ConsignmentNotePage />} />
                        <Route path="/cashier" element={<CashierPage />} />
                        <Route path="/person" element={<PersonPage />} />
                        <Route path="/dish" element={<DishPage />} />
                        <Route path="/warehouse" element={<WarehousesPage />} />
                        <Route path="/movements" element={<MovementPage />} />
                        <Route path="/ml" element={<MlPage />} />
                        <Route path="/" element={<div style={{ padding: '2rem' }}><h2>Домашняя страница</h2></div>} />
                    </Routes>
                </main>
            </div>
        </Router>
    );
}

export default App;
