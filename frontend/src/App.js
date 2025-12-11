// src/App.js
import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import './App.css';

// Импортируем страницы (создайте их позже)
import SuppliersPage from './pages/SuppliersPage/SuppliersPage';


function App() {
  return (
      <Router>
        <div className="App">
          {/* Навигация */}
          <nav className="App-nav">
            <Link to="/" className="App-link">Home</Link>
            <Link to="/suppliers" className="App-link">Suppliers</Link>
            <Link to="/about" className="App-link">About</Link>
          </nav>

          {/* Контент страницы */}
          <div className="App-content">
            <Routes>
              <Route path="/suppliers" element={<SuppliersPage />} />
              <Route path="/about" element={<AboutPage />} />

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