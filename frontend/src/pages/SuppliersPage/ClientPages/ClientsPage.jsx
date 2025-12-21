import { useState, useEffect } from "react";
import styles from "./ClientsPage.module.css";

const API_CLIENTS = "http://localhost:8080/api/clients";


export default function ClientsPage() {
    const [clients, setClients] = useState([]);
    const [dutyClients, setDutyClients] = useState([]);
    const [selectedClient, setSelectedClient] = useState(null);
    const [clientDishes, setClientDishes] = useState([]);
    const [viewMode, setViewMode] = useState("all"); // "all", "duty", "details"
    const [loading, setLoading] = useState(false);
    const [dutyLoading, setDutyLoading] = useState(false);

    // Форма создания клиента
    const [newClient, setNewClient] = useState({
        fullName: "",
        number: ""
    });

    // Форма поиска
    const [searchQuery, setSearchQuery] = useState("");

    // Загрузка данных
    useEffect(() => {
        loadClients();
        loadDutyClients();
    }, []);

    const loadClients = () => {
        setLoading(true);
        fetch(API_CLIENTS)
            .then(r => {
                if (!r.ok) {
                    throw new Error(`HTTP ${r.status}`);
                }
                return r.json();
            })
            .then(data => {
                console.log("Загружены клиенты:", data);
                setClients(Array.isArray(data) ? data : []);
                setLoading(false);
            })
            .catch(e => {
                console.error("Ошибка загрузки клиентов:", e);
                setLoading(false);
                setClients([]);
            });
    };

    const loadDutyClients = () => {
        setDutyLoading(true);
        fetch(`${API_CLIENTS}/duty`)
            .then(r => {
                if (!r.ok) {
                    throw new Error(`HTTP ${r.status}`);
                }
                return r.json();
            })
            .then(data => {
                console.log("Получены клиенты с долгами:", data);
                setDutyClients(Array.isArray(data) ? data : []);
                setDutyLoading(false);
            })
            .catch(e => {
                console.error("Ошибка загрузки должников:", e);
                setDutyClients([]);
                setDutyLoading(false);
            });
    };

    const loadClientDishes = (clientId) => {
        fetch(`${API_CLIENTS}/${clientId}/dishes`)
            .then(r => {
                if (!r.ok) {
                    throw new Error(`HTTP ${r.status}`);
                }
                return r.json();
            })
            .then(data => {
                console.log("Блюда клиента:", data);
                setClientDishes(Array.isArray(data) ? data : []);
            })
            .catch(e => {
                console.error("Ошибка загрузки блюд:", e);
                setClientDishes([]);
            });
    };

    const createClient = async () => {
        if (!newClient.fullName.trim()) {
            alert("Введите ФИО клиента");
            return;
        }

        try {
            const response = await fetch(API_CLIENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fullName: newClient.fullName.trim(),
                    number: newClient.number?.trim() || ""
                })
            });

            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${responseText || 'No response body'}`);
            }

            // Пытаемся распарсить JSON
            let data;
            try {
                data = responseText ? JSON.parse(responseText) : {};
            } catch (parseError) {
                console.error("JSON parse error:", parseError);
                data = {};
            }

            setNewClient({ fullName: "", number: "" });
            loadClients();
            alert("✅ Клиент создан успешно!");

        } catch (e) {
            console.error("Ошибка создания клиента:", e);
            alert("❌ Ошибка создания клиента: " + e.message);
        }
    };

    const handleClientSelect = (client) => {
        setSelectedClient(client);
        setViewMode("details");
        loadClientDishes(client.clientId);
    };

    // Списание всего долга клиента
    const markAllDutyAsPaid = (clientId) => {
        if (!window.confirm("Списать ВСЕ долги клиента как оплаченные?")) return;

        fetch(`${API_CLIENTS}/${clientId}/duty`, {
            method: "DELETE"
        })
            .then(r => {
                if (!r.ok) {
                    throw new Error(`HTTP ${r.status}`);
                }
                return r.json();
            })
            .then(data => {
                alert(data.message || "✅ Все долги списаны как оплаченные!");
                loadDutyClients();
                loadClients();
            })
            .catch(e => {
                console.error("Ошибка списания долгов:", e);
                alert("❌ Ошибка: " + e.message);
            });
    };

    // Списание одного конкретного заказа
    const markSingleOrderAsPaid = (orderId, clientId, orderAmount) => {
        if (!window.confirm(`Списать долг по заказу #${orderId} на сумму ${orderAmount} ₽?`)) return;

        fetch(`${API_CLIENTS}/${orderId}/One-duty`, {
            method: "DELETE",
            headers: { "Content-Type": "application/json" }
        })
            .then(r => {
                if (!r.ok) {
                    throw new Error(`HTTP ${r.status}`);
                }
                return r.json();
            })
            .then(data => {
                if (data.success) {
                    alert(data.message || `✅ Заказ #${orderId} отмечен как оплаченный`);
                    loadDutyClients();
                    loadClients();
                } else {
                    alert("❌ Ошибка: " + data.error);
                }
            })
            .catch(e => {
                console.error("Ошибка списания заказа:", e);
                alert("❌ Ошибка: " + e.message);
            });
    };

    const searchClients = () => {
        if (!searchQuery.trim()) {
            loadClients();
            return;
        }

        fetch(`${API_CLIENTS}/search?name=${encodeURIComponent(searchQuery)}`)
            .then(r => {
                if (!r.ok) {
                    throw new Error(`HTTP ${r.status}`);
                }
                return r.json();
            })
            .then(data => {
                setClients(Array.isArray(data) ? data : []);
                setViewMode("all");
            })
            .catch(e => {
                console.error("Ошибка поиска:", e);
                setClients([]);
            });
    };

    // Фильтрация клиентов
    const filteredClients = searchQuery.trim()
        ? clients.filter(c =>
            c.fullName?.toLowerCase().includes(searchQuery.toLowerCase()))
        : clients;

    // Расчет общей суммы долгов
    const totalDutyAmount = dutyClients.reduce((total, clientWithDuty) => {
        if (clientWithDuty.totalDuty !== undefined) {
            return total + (clientWithDuty.totalDuty || 0);
        } else if (clientWithDuty.dutyOrders && Array.isArray(clientWithDuty.dutyOrders)) {
            const clientTotal = clientWithDuty.dutyOrders.reduce((sum, order) =>
                sum + (order.amount || 0), 0);
            return total + clientTotal;
        }
        return total;
    }, 0);

    return (
        <div className={styles.page}>
            <header className={styles.header}>
                <h1>📋 Управление клиентами</h1>
                <div className={styles.viewControls}>
                    <button
                        className={`${styles.viewBtn} ${viewMode === "all" ? styles.active : ""}`}
                        onClick={() => setViewMode("all")}
                    >
                        Все клиенты
                    </button>
                    <button
                        className={`${styles.viewBtn} ${viewMode === "duty" ? styles.active : ""}`}
                        onClick={() => setViewMode("duty")}
                    >
                        Долги ({dutyClients.length})
                    </button>
                </div>
            </header>

            <div className={styles.searchSection}>
                <div className={styles.searchBox}>
                    <input
                        type="text"
                        placeholder="Поиск по имени..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        onKeyPress={(e) => e.key === 'Enter' && searchClients()}
                    />
                    <button onClick={searchClients}>🔍</button>
                    <button onClick={() => {
                        setSearchQuery("");
                        loadClients();
                    }}>
                        Сброс
                    </button>
                </div>
            </div>

            {viewMode === "all" && (
                <>
                    <div className={styles.createSection}>
                        <h3>➕ Создать нового клиента</h3>
                        <div className={styles.createForm}>
                            <input
                                type="text"
                                placeholder="ФИО клиента *"
                                value={newClient.fullName}
                                onChange={(e) => setNewClient({...newClient, fullName: e.target.value})}
                            />
                            <input
                                type="text"
                                placeholder="Телефон (необязательно)"
                                value={newClient.number}
                                onChange={(e) => setNewClient({...newClient, number: e.target.value})}
                            />
                            <button
                                className={styles.createBtn}
                                onClick={createClient}
                                disabled={!newClient.fullName.trim()}
                            >
                                Создать
                            </button>
                        </div>
                    </div>

                    <div className={styles.clientsSection}>
                        <h3>Все клиенты ({clients.length})</h3>
                        {loading ? (
                            <div className={styles.loading}>Загрузка...</div>
                        ) : filteredClients.length === 0 ? (
                            <div className={styles.empty}>
                                {searchQuery.trim() ? "Клиенты не найдены" : "Нет клиентов"}
                            </div>
                        ) : (
                            <div className={styles.clientsGrid}>
                                {filteredClients.map(client => (
                                    <div key={client.clientId} className={styles.clientCard}>
                                        <div className={styles.clientInfo}>
                                            <div className={styles.clientName}>
                                                <strong>{client.fullName || "Без имени"}</strong>
                                            </div>
                                            <div className={styles.clientId}>
                                                ID: {client.clientId}
                                            </div>
                                            <div className={styles.clientPhone}>
                                                📞 {client.number || "Телефон не указан"}
                                            </div>
                                        </div>
                                        <div className={styles.clientActions}>
                                            <button
                                                className={styles.detailsBtn}
                                                onClick={() => handleClientSelect(client)}
                                            >
                                                Подробнее
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </>
            )}

            {viewMode === "duty" && (
                <div className={styles.dutySection}>
                    <div className={styles.dutyHeader}>
                        <h3>💰 Клиенты с долгами</h3>
                        <div className={styles.totalDuty}>
                            Общая сумма долгов: <strong>{totalDutyAmount.toFixed(2)} ₽</strong>
                        </div>
                    </div>

                    {dutyLoading ? (
                        <div className={styles.loading}>Загрузка должников...</div>
                    ) : dutyClients.length === 0 ? (
                        <div className={styles.empty}>Нет клиентов с долгами</div>
                    ) : (
                        dutyClients.map((clientWithDuty, index) => {
                            const client = clientWithDuty.client || clientWithDuty;
                            const dutyOrders = clientWithDuty.dutyOrders || [];
                            const totalDuty = clientWithDuty.totalDuty !== undefined
                                ? clientWithDuty.totalDuty
                                : dutyOrders.reduce((sum, order) => sum + (order.amount || 0), 0);

                            return (
                                <div key={client.clientId || index} className={styles.dutyClientCard}>
                                    <div className={styles.dutyClientHeader}>
                                        <div>
                                            <h4>{client.fullName || "Без имени"}</h4>
                                            <div className={styles.clientContact}>
                                                <span>📞 {client.number || "Телефон не указан"}</span>
                                                <span>ID: {client.clientId}</span>
                                            </div>
                                        </div>
                                        <div className={styles.dutyClientTotal}>
                                            <strong className={styles.dutyAmount}>
                                                Общий долг: {totalDuty.toFixed(2)} ₽
                                            </strong>
                                            <button
                                                className={styles.payAllBtn}
                                                onClick={() => markAllDutyAsPaid(client.clientId)}
                                            >
                                                💰 Списать весь долг
                                            </button>
                                        </div>
                                    </div>

                                    {dutyOrders.length > 0 && (
                                        <div className={styles.dutyOrders}>
                                            <h5>Заказы с долгами ({dutyOrders.length}):</h5>
                                            {dutyOrders.map(order => (
                                                <div key={order.orderId} className={styles.dutyOrder}>
                                                    <div className={styles.orderMainInfo}>
                                                        <div className={styles.orderInfo}>
                                                            <span className={styles.orderId}>
                                                                Заказ #{order.orderId}
                                                            </span>
                                                            <span className={styles.orderDate}>
                                                                📅 {order.date || "Дата не указана"}
                                                            </span>
                                                        </div>
                                                        <div className={styles.orderDetails}>
                                                            <span className={styles.orderAmount}>
                                                                💰 {order.amount || 0} ₽
                                                            </span>
                                                            {order.timeDelay > 0 && (
                                                                <span className={styles.delayBadge}>
                                                                    ⏰ Задержка: {order.timeDelay} мин
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                    <button
                                                        className={styles.paySingleBtn}
                                                        onClick={() => markSingleOrderAsPaid(
                                                            order.orderId,
                                                            client.clientId,
                                                            order.amount || 0
                                                        )}
                                                    >
                                                        ✅ Долг оплачен
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            );
                        })
                    )}
                </div>
            )}

            {viewMode === "details" && selectedClient && (
                <div className={styles.detailsSection}>
                    <div className={styles.detailsHeader}>
                        <button
                            className={styles.backBtn}
                            onClick={() => setViewMode("all")}
                        >
                            ← Назад к списку
                        </button>
                        <h3>👤 Детали клиента</h3>
                    </div>

                    <div className={styles.clientDetails}>
                        <div className={styles.detailItem}>
                            <label>🆔 ID:</label>
                            <span>{selectedClient.clientId}</span>
                        </div>
                        <div className={styles.detailItem}>
                            <label>👤 ФИО:</label>
                            <span className={styles.clientName}>
                                {selectedClient.fullName || "Не указано"}
                            </span>
                        </div>
                        <div className={styles.detailItem}>
                            <label>📞 Телефон:</label>
                            <span className={styles.clientPhone}>
                                {selectedClient.number || "Не указан"}
                            </span>
                        </div>
                    </div>

                    <div className={styles.dishesSection}>
                        <h4>🍽️ Предпочитаемые блюда</h4>
                        {clientDishes.length === 0 ? (
                            <div className={styles.empty}>Нет данных о блюдах</div>
                        ) : (
                            <div className={styles.dishesList}>
                                {clientDishes.map(dish => (
                                    <div key={dish.dishId} className={styles.dishItem}>
                                        <span className={styles.dishName}>
                                            {dish.dishName || "Блюдо без названия"}
                                        </span>
                                        <span className={styles.dishId}>
                                            ID: {dish.dishId}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className={styles.actions}>
                        <button
                            className={styles.editBtn}
                            onClick={() => alert("Функция редактирования в разработке")}
                        >
                            ✏️ Редактировать
                        </button>
                        <button
                            className={styles.deleteBtn}
                            onClick={() => {
                                if (window.confirm("Удалить клиента?")) {
                                    alert("Функция удаления в разработке");
                                }
                            }}
                        >
                            🗑️ Удалить
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}