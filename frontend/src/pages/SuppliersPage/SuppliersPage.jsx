// src/pages/SuppliersPage/SuppliersPage.jsx
import React, { useState, useEffect } from 'react';
import styles from './SuppliersPage.module.css';
import { useNavigate } from "react-router-dom";

const SuppliersPage = () => {
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [search, setSearch] = useState('');
    const [formData, setFormData] = useState({
        name: '',
        communication: ''
    });

    // Состояния для модалки редактирования
    const [editModalOpen, setEditModalOpen] = useState(false);
    const [editingSupplier, setEditingSupplier] = useState(null);
    const [editFormData, setEditFormData] = useState({
        name: '',
        communication: ''
    });

    const navigate = useNavigate();

    // Получить всех поставщиков
    const fetchSuppliers = async () => {
        try {
            setLoading(true);
            setError('');

            const response = await fetch('http://localhost:8080/api/supplier');
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            setSuppliers(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error(err);
            setError('Failed to load suppliers: ' + err.message);
            setSuppliers([]);
        } finally {
            setLoading(false);
        }
    };

    // Создать нового поставщика
    const createSupplier = async (supplierData) => {
        try {
            const requestData = {
                supplierName: supplierData.name,
                communication: supplierData.communication
            };

            const response = await fetch('http://localhost:8080/api/supplier', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(`Backend error: ${response.status} - ${text}`);
            }

            const data = await response.json();
            setSuppliers(prev => [...prev, data]);
            return true;
        } catch (err) {
            console.error(err);
            setError('Failed to create supplier: ' + err.message);
            return false;
        }
    };

    // Обновить поставщика
    const updateSupplier = async (id, supplierData) => {
        try {
            const requestData = {
                supplierName: supplierData.name,
                communication: supplierData.communication
            };

            const response = await fetch(`http://localhost:8080/api/supplier/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const text = await response.text();
                throw new Error(`Backend error: ${response.status} - ${text}`);
            }

            const data = await response.json();

            // Обновляем поставщика в списке
            setSuppliers(prev => prev.map(s =>
                (s.supplierID === id || s.id === id) ? data : s
            ));

            return true;
        } catch (err) {
            console.error(err);
            setError('Failed to update supplier: ' + err.message);
            return false;
        }
    };

    // Удалить поставщика
    const deleteSupplier = async (id) => {
        if (!window.confirm('Are you sure you want to delete this supplier?')) return;

        try {
            const response = await fetch(`http://localhost:8080/api/supplier/${id}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            setSuppliers(prev => prev.filter(s => s.supplierID !== id && s.id !== id));
            setError('');
        } catch (err) {
            console.error(err);
            setError('Failed to delete supplier: ' + err.message);
        }
    };

    // Обработчик изменения полей формы
    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };

    // Обработчик изменения полей формы редактирования
    const handleEditInputChange = (e) => {
        const { name, value } = e.target;
        setEditFormData({ ...editFormData, [name]: value });
    };

    // Открыть модалку редактирования
    const openEditModal = (supplier) => {
        setEditingSupplier(supplier);
        setEditFormData({
            name: supplier.name || supplier.supplierName || '',
            communication: supplier.communication || ''
        });
        setEditModalOpen(true);
    };

    // Закрыть модалку редактирования
    const closeEditModal = () => {
        setEditModalOpen(false);
        setEditingSupplier(null);
        setEditFormData({ name: '', communication: '' });
    };

    // Сохранить изменения
    const handleEditSubmit = async (e) => {
        e.preventDefault();
        if (!editFormData.name.trim()) {
            alert('Please fill supplier name');
            return;
        }

        const supplierId = editingSupplier?.supplierID || editingSupplier?.id;
        if (!supplierId) {
            alert('Cannot update: Supplier ID not found');
            return;
        }

        const success = await updateSupplier(supplierId, editFormData);
        if (success) {
            alert('Supplier updated successfully!');
            closeEditModal();
        }
    };

    // Загрузить данные при монтировании
    useEffect(() => {
        fetchSuppliers();
    }, []);

    // Обработчик формы добавления
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!formData.name.trim()) {
            alert('Please fill supplier name');
            return;
        }

        const success = await createSupplier(formData);
        if (success) {
            alert('Supplier added successfully!');
            setFormData({ name: '', communication: '' });
        }
    };

    // Очистить форму добавления
    const clearForm = () => setFormData({ name: '', communication: '' });

    // Фильтрация
    const filteredSuppliers = Array.isArray(suppliers)
        ? suppliers.filter(supplier => {
            if (!supplier) return false;
            const searchLower = search.toLowerCase();
            const nameMatch = supplier.name?.toLowerCase().includes(searchLower) ||
                supplier.supplierName?.toLowerCase().includes(searchLower);
            const idMatch = supplier.supplierID?.toString().includes(search) ||
                supplier.id?.toString().includes(search);
            const communicationMatch = supplier.communication?.toLowerCase().includes(searchLower);
            return nameMatch || communicationMatch || idMatch;
        })
        : [];

    if (loading) {
        return (
            <div className={styles.loadingContainer}>
                <div className={styles.spinner}></div>
                <p>Loading suppliers...</p>
            </div>
        );
    }

    const totalSuppliers = suppliers.length;

    return (
        <div className={styles.container}>
            {/* Модальное окно редактирования */}
            {editModalOpen && (
                <div className={styles.modalOverlay} onClick={closeEditModal}>
                    <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
                        <div className={styles.modalHeader}>
                            <h3>✏️ Edit Supplier #{editingSupplier?.supplierID || editingSupplier?.id}</h3>
                            <button onClick={closeEditModal} className={styles.closeModal} aria-label="Close">
                                ×
                            </button>
                        </div>

                        <div className={styles.modalContent}>
                            <form onSubmit={handleEditSubmit}>
                                <div className={styles.formGroup}>
                                    <label htmlFor="edit-name">
                                        Supplier Name *
                                    </label>
                                    <input
                                        type="text"
                                        id="edit-name"
                                        name="name"
                                        placeholder="Enter company name"
                                        value={editFormData.name}
                                        onChange={handleEditInputChange}
                                        required
                                        className={styles.modalInput}
                                        autoFocus
                                    />
                                </div>

                                <div className={styles.formGroup}>
                                    <label htmlFor="edit-communication">
                                        Contact Information
                                    </label>
                                    <input
                                        type="text"
                                        id="edit-communication"
                                        name="communication"
                                        placeholder="Email or phone number"
                                        value={editFormData.communication}
                                        onChange={handleEditInputChange}
                                        className={styles.modalInput}
                                    />
                                </div>

                                <div className={styles.modalButtons}>
                                    <button
                                        type="button"
                                        onClick={closeEditModal}
                                        className={`${styles.submitBtn} ${styles.secondary}`}
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className={`${styles.submitBtn} ${styles.primary}`}
                                    >
                                        Save Changes
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            )}

            <div className={styles.header}>
                <h1 className={styles.title}>📦 Supplier Management</h1>
                <div className={styles.stats}>Total: {totalSuppliers}</div>
            </div>

            <div className={styles.card}>
                {error && (
                    <div className={`${styles.alert} ${styles.errorAlert}`}>
                        {error}
                        <button onClick={() => setError('')} className={styles.closeError}>×</button>
                    </div>
                )}

                <div className={styles.toolbar}>
                    <input
                        type="text"
                        placeholder="🔍 Search by name, ID or contact..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className={styles.searchInput}
                    />
                    <button onClick={fetchSuppliers} className={styles.refreshBtn}>🔄 Refresh</button>
                </div>

                {/* Форма добавления */}
                <div className={styles.addSupplierForm}>
                    <h3>➕ Add New Supplier</h3>
                    <form onSubmit={handleSubmit}>
                        <div className={styles.formGrid}>
                            <div className={styles.formGroup}>
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
                            <div className={styles.formGroup}>
                                <label htmlFor="communication">Contact Info:</label>
                                <input
                                    type="text"
                                    id="communication"
                                    name="communication"
                                    placeholder="Email or phone"
                                    value={formData.communication}
                                    onChange={handleInputChange}
                                />
                            </div>
                        </div>
                        <div className={styles.formButtons}>
                            <button type="submit" className={`${styles.submitBtn} ${styles.primary}`}>Add Supplier</button>
                            <button type="button" onClick={clearForm} className={`${styles.submitBtn} ${styles.secondary}`}>Clear Form</button>
                        </div>
                    </form>
                </div>

                {/* Карточки поставщиков */}
                <div className={styles.suppliersList}>
                    {filteredSuppliers.length > 0 ? (
                        filteredSuppliers.map(supplier => {
                            const displayId = supplier.supplierID || supplier.id || 'N/A';
                            const displayName = supplier.name || supplier.supplierName || 'N/A';
                            const displayContact = supplier.communication || 'N/A';
                            const deleteId = supplier.supplierID || supplier.id;

                            return (
                                <div key={displayId} className={styles.supplierCard}>
                                    <div className={styles.supplierHeader}>
                                        <span className={styles.supplierID}>#{displayId}</span>
                                        <span
                                            className={styles.supplierName}
                                            onClick={() => navigate(`/suppliers/${deleteId}`)}
                                        >
                                            {displayName}
                                        </span>
                                    </div>

                                    <div className={styles.contactInfo}>
                                        {displayContact !== 'N/A' ? (
                                            displayContact.includes('@') ? (
                                                <a href={`mailto:${displayContact}`} className={styles.emailLink}>📧 {displayContact}</a>
                                            ) : (
                                                <a href={`tel:${displayContact}`} className={styles.phoneLink}>📞 {displayContact}</a>
                                            )
                                        ) : <span>No contact</span>}
                                    </div>

                                    <div className={styles.actionButtons}>
                                        <button
                                            className={styles.editBtn}
                                            onClick={() => openEditModal(supplier)}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            className={styles.deleteBtn}
                                            onClick={() => deleteSupplier(deleteId)}
                                        >
                                            Delete
                                        </button>
                                    </div>
                                </div>
                            );
                        })
                    ) : (
                        <div className={styles.emptyState}>
                            {search ? (
                                <>
                                    <div className={styles.emptyIcon}>🔍</div>
                                    <h4>No suppliers found</h4>
                                    <p>No suppliers match "{search}"</p>
                                    <button onClick={() => setSearch('')} className={styles.clearSearchBtn}>Clear search</button>
                                </>
                            ) : (
                                <>
                                    <div className={styles.emptyIcon}>📦</div>
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