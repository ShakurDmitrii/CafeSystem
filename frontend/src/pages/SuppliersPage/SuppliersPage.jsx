// src/pages/SuppliersPage/SuppliersPage.jsx
import React, {useState, useEffect} from 'react';
import './SuppliersPage.css';

const SuppliersPage = () => {
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [formData, setFormData] = useState({
        name: '',
        communication: ''
    });

    // Получить всех поставщиков
    const fetchSuppliers = async () => {
        try {
            setLoading(true);
            setError('');
            console.log('Fetching from: http://localhost:8080/api/supplier');

            const response = await fetch('http://localhost:8080/api/supplier');

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Received data:', data);

            // Гарантируем что это массив
            const suppliersArray = Array.isArray(data) ? data : [];
            setSuppliers(suppliersArray);

        } catch (err) {
            console.error('Fetch error:', err);
            setError('Failed to load suppliers: ' + err.message);
            setSuppliers([]);
        } finally {
            setLoading(false);
        }
    };

    // Создать нового поставщика
    const createSupplier = async (supplierData) => {
        try {
            // Поля для отправки на бэкенд
            const requestData = {
                supplierName: supplierData.name,
                communication: supplierData.communication
            };

            console.log('Sending to backend:', JSON.stringify(requestData, null, 2));

            const response = await fetch('http://localhost:8080/api/supplier', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestData)
            });

            console.log('Response status:', response.status);

            if (response.status === 415) {
                throw new Error('415 Unsupported Media Type - check Content-Type header');
            }

            if (!response.ok) {
                const errorText = await response.text();
                console.error('Backend error response:', errorText);
                throw new Error(`Backend error: ${response.status} - ${errorText}`);
            }

            const data = await response.json();
            console.log('Success! Created supplier:', data);

            // Добавляем нового поставщика в список
            setSuppliers(prev => [...prev, data]);
            return true;

        } catch (err) {
            console.error('Create error details:', err);
            setError('Failed to create supplier: ' + err.message);
            return false;
        }
    };

    // Удалить поставщика
    const deleteSupplier = async (id) => {
        if (!window.confirm('Are you sure you want to delete this supplier?')) return;

        try {
            const response = await fetch(`http://localhost:8080/api/supplier/${id}`, {  // 👈 Исправлен endpoint
                method: 'DELETE'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            // Удаляем из локального состояния
            setSuppliers(prev => prev.filter(s =>
                s.supplierID !== id && s.id !== id  // Проверяем оба варианта ID
            ));
            setError(''); // Очищаем ошибки если были

        } catch (err) {
            console.error('Delete error:', err);
            setError('Failed to delete supplier: ' + err.message);
        }
    };

    // Обработчик изменения полей формы
    const handleInputChange = (e) => {
        const {name, value} = e.target;

        // Если поле ID, проверяем что это число
        if (name === 'supplierId') {
            // Разрешаем только цифры
            const numericValue = value.replace(/[^\d]/g, '');
            setFormData({
                ...formData,
                [name]: numericValue
            });
        } else {
            setFormData({
                ...formData,
                [name]: value
            });
        }
    };

    // Загрузить данные при монтировании
    useEffect(() => {
        fetchSuppliers();
    }, []);

    // Обработчик формы
    const handleSubmit = async (e) => {
        e.preventDefault();

        // Валидация
        if (!formData.name.trim()) {
            alert('Please fill supplier name');
            return;
        }


        const success = await createSupplier(formData);
        if (success) {
            alert('Supplier added successfully!');
            // Очищаем форму
            setFormData({
                name: '',
                communication: ''
            });
        }
    };

    // Очистить форму
    const clearForm = () => {
        setFormData({
            supplierID: '',
            name: '',
            communication: ''
        });
    };

    // Фильтрация
    const filteredSuppliers = Array.isArray(suppliers)
        ? suppliers.filter(supplier => {
            if (!supplier) return false;

            const searchLower = search.toLowerCase();

            const nameMatch = supplier.name &&
                supplier.name.toLowerCase().includes(searchLower);

            const idMatch = supplier.supplierId &&
                supplier.supplierId.toString().includes(search);

            const communicationMatch = supplier.communication &&
                supplier.communication.toLowerCase().includes(searchLower);

            return nameMatch || communicationMatch || idMatch;
        })
        : [];

    if (loading) {
        return (
            <div className="loading-container">
                <div className="spinner"></div>
                <p>Loading suppliers...</p>
            </div>
        );
    }

    const totalSuppliers = suppliers.length;


    return (
        <div className="container">
            <div className="header">
                <h1 className="title">📦 Supplier Management</h1>
                <div className="stats">
                    <span>Total: {totalSuppliers}</span>
                </div>
            </div>

            <div className="card">
                {error && (
                    <div className="alert error-alert">
                        {error}
                        <button onClick={() => setError('')} className="close-error">×</button>
                    </div>
                )}

                <div className="toolbar">
                    <input
                        type="text"
                        placeholder="🔍 Search by name, ID or communication..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="search-input"
                    />
                    <button
                        onClick={() => fetchSuppliers()}
                        className="refresh-btn"
                        title="Refresh list"
                    >
                        🔄 Refresh
                    </button>
                </div>

                {/* Форма добавления */}
                <div className="add-supplier-form">
                    <h3>➕ Add New Supplier</h3>
                    <form onSubmit={handleSubmit}>
                        <div className="form-grid">
                            <div className="form-group">
                                <label htmlFor="name">Supplier Name: *</label>
                                <input
                                    type="text"
                                    id="name"
                                    name="name"
                                    placeholder="Company name"
                                    value={formData.name}
                                    onChange={handleInputChange}
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="communication">Contact Info:</label>
                                <input
                                    type="text"
                                    id="communication"
                                    name="communication"
                                    placeholder="Email or phone number"
                                    value={formData.communication}
                                    onChange={handleInputChange}
                                />
                            </div>
                        </div>

                        <div className="form-buttons">
                            <button type="submit" className="submit-btn primary">
                                Add Supplier
                            </button>
                            <button
                                type="button"
                                onClick={clearForm}
                                className="submit-btn secondary"
                            >
                                Clear Form
                            </button>
                        </div>
                    </form>
                </div>

                {/* Таблица поставщиков */}
                <div className="suppliers-table-container">
                    <div className="table-header">
                        <span className="search-count">
                            Found: {filteredSuppliers.length} supplier(s)
                        </span>
                        <div className="table-actions">
                            <span className="total-suppliers">
                                Total in DB: {totalSuppliers}
                            </span>
                        </div>
                    </div>

                    {filteredSuppliers.length > 0 ? (
                        <div className="table-responsive">
                            <table className="suppliers-table">
                                <thead>
                                <tr>
                                    <th width="80px">ID</th>
                                    <th>Name</th>
                                    <th>Contact Info</th>
                                    <th width="150px">Actions</th>
                                </tr>
                                </thead>
                                <tbody>
                                {filteredSuppliers.map(supplier => {
                                    // Определяем ID для отображения
                                    const displayId = supplier.supplierID || supplier.id || 'N/A';
                                    const displayName = supplier.name || supplier.supplierName || 'N/A';
                                    const displayContact = supplier.communication || 'N/A';
                                    const deleteId = supplier.supplierID || supplier.id;

                                    return (
                                        <tr key={displayId}>
                                            <td>
                                                    <span className="supplier-id">
                                                        #{displayId}
                                                    </span>
                                            </td>
                                            <td>
                                                <strong className="supplier-name">
                                                    {displayName}
                                                </strong>
                                            </td>
                                            <td>
                                                {displayContact !== 'N/A' ? (
                                                    <div className="communication">
                                                        {displayContact.includes('@') ? (
                                                            <a
                                                                href={`mailto:${displayContact}`}
                                                                className="email-link"
                                                            >
                                                                📧 {displayContact}
                                                            </a>
                                                        ) : (
                                                            <a
                                                                href={`tel:${displayContact}`}
                                                                className="phone-link"
                                                            >
                                                                📞 {displayContact}
                                                            </a>
                                                        )}
                                                    </div>
                                                ) : 'No contact'}
                                            </td>
                                            <td>
                                                <div className="action-buttons">
                                                    <button
                                                        className="btn-edit"
                                                        onClick={() => alert(`Edit supplier ${displayId}`)}
                                                    >
                                                        Edit
                                                    </button>
                                                    <button
                                                        onClick={() => deleteSupplier(deleteId)}
                                                        className="btn-delete"
                                                    >
                                                        Delete
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                        </div>
                    ) : (
                        <div className="empty-state">
                            {search ? (
                                <>
                                    <div className="empty-icon">🔍</div>
                                    <h4>No suppliers found</h4>
                                    <p>No suppliers match "{search}"</p>
                                    <button
                                        onClick={() => setSearch('')}
                                        className="clear-search-btn"
                                    >
                                        Clear search
                                    </button>
                                </>
                            ) : (
                                <>
                                    <div className="empty-icon">📦</div>
                                    <h4>No suppliers yet</h4>
                                    <p>Add your first supplier using the form above</p>
                                </>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default SuppliersPage;