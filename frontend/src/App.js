// src/App.js
import React from 'react';
import {BrowserRouter as Router, Routes, Route, Link} from 'react-router-dom';
import './App.css';

// Импортируем страницы (создайте их позже)
import SuppliersPage from './pages/SuppliersPage/SuppliersPage';
import SupplierProductPage from "./pages/SuppliersPage/SupplierProductPage";
import ConsignmentNotePage from "./pages/SuppliersPage/ConsignmentNotePage";
import PrintConsignmentNotePage from "./pages/SuppliersPage/PrintConsignmentNotePage";
import CashierPage from "./pages/SuppliersPage/CashierPages/CashierPage";
import PersonPage from "./pages/SuppliersPage/PersonPage";
import DishPage from "./pages/SuppliersPage/CashierPages/DishPage";
import TechCardPage from "./pages/TechCard/TechCardPage";
import WarehousesPage from "./pages/Warehouse/WarehousesPage";
import ClientsPage from "./pages/SuppliersPage/ClientPages/ClientsPage";

import MlPage from "./pages/MLPanel/MlPage";


function App() {
    return (
        <Router>
            <div className="App">
                {/* Навигация */}
                <nav className="App-nav">
                    <Link to="/cashier" className="App-link">Касса</Link>
                    <Link to="/suppliers" className="App-link">Поставщики</Link>
                    <Link to="/consigment" className="App-link">Создать накладную</Link>
                    <Link to="/person" className="App-link">Персонал</Link>
                    <Link to="/dish" className="App-link">Меню</Link>
                    <Link to="/warehouse" className="App-link">Управление Складами</Link>
                    <Link to="/clients" className="App-link">Наши Клиенты</Link>
                    <Link to="/" className="App-link">Домашняя Страница</Link>
                    <Link to="/ml" className="App-link">🤖 AI Аналитика</Link>

                </nav>

                {/* Контент страницы */}
                <div className="App-content">
                    <Routes>
                        <Route path="/consignment-notes/print/:id" element={<PrintConsignmentNotePage />} />
                        <Route path="/tech-card/:dishId" element={<TechCardPage />} />
                        <Route path="/suppliers" element={<SuppliersPage/>}/>
                        <Route path="/clients" element={<ClientsPage/>}/>
                        <Route path="/suppliers/:id" element={<SupplierProductPage/>}/>
                        <Route path="/consigment" element={<ConsignmentNotePage/>}/>
                        <Route path="/cashier" element={<CashierPage />} />
                        <Route path="/person" element={<PersonPage/>}/>
                        <Route path="/dish" element={<DishPage/>}/>
                        <Route path="/warehouse" element={<WarehousesPage/>}/>
                        <Route path="/ml" element={<MlPage />} />
                    </Routes>
                </div>
            </div>
        </Router>
    );
}

// Простая страница About (можно вынести в отдельный файл)
function AboutPage() {
    return (
        <div className="page-container">
            <h1>About Page</h1>
            <p>This is a React application for managing suppliers.</p>
        </div>
    );
}

export default App;