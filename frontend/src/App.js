// src/App.js
import React from 'react';
import {BrowserRouter as Router, Routes, Route, Link} from 'react-router-dom';
import './App.css';

// Импортируем страницы (создайте их позже)
import SuppliersPage from './pages/SuppliersPage/SuppliersPage';
import SupplierProductPage from "./pages/SuppliersPage/SupplierProductPage";
import ConsignmentNotePage from "./pages/SuppliersPage/ConsignmentNotePage";
import PrintConsignmentNotePage from "./pages/SuppliersPage/PrintConsignmentNotePage";


function App() {
    return (
        <Router>
            <div className="App">
                {/* Навигация */}
                <nav className="App-nav">
                    <Link to="/" className="App-link">Home</Link>
                    <Link to="/suppliers" className="App-link">Поставщики</Link>
                    <Link to="/about" className="App-link">About</Link>
                    <Link to="/consigment" className="App-link">Создать накладную</Link>

                </nav>

                {/* Контент страницы */}
                <div className="App-content">
                    <Routes>
                        <Route path="/consignment-notes/print/:id" element={<PrintConsignmentNotePage />} />
                        <Route path="/suppliers" element={<SuppliersPage/>}/>
                        <Route path="/about" element={<AboutPage/>}/>
                        <Route path="/suppliers/:id" element={<SupplierProductPage/>}/>
                        <Route path="/consigment" element={<ConsignmentNotePage/>}/>
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