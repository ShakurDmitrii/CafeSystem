import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { API_BASE_URL } from "../../../auth";
import styles from "./ClientsPage.module.css";
import ClientDirectory from "./clients-page/ClientDirectory";
import ClientHero from "./clients-page/ClientHero";
import ClientProfile from "./clients-page/ClientProfile";
import DebtLedger from "./clients-page/DebtLedger";
import {
    formatMoney,
    getClientDebt,
    getOrderTimestamp
} from "./clients-page/clientUtils";

const API_CLIENTS = `${API_BASE_URL}/api/clients`;
const API_ORDERS = `${API_BASE_URL}/api/orders`;

const readJson = async (response) => {
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(data?.message || data?.error || `Ошибка сервера (${response.status})`);
    }
    return data;
};

const loadOrderDishes = async (orderId) => {
    if (!orderId) return [];
    const response = await fetch(`${API_BASE_URL}/api/shifts/getDish/${orderId}`);
    const data = await readJson(response);
    return Array.isArray(data) ? data : [];
};

export default function ClientsPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const initialView = searchParams.get("view") === "duty" ? "duty" : "all";

    const [clients, setClients] = useState([]);
    const [dutyClients, setDutyClients] = useState([]);
    const [selectedClient, setSelectedClient] = useState(null);
    const [clientDishes, setClientDishes] = useState([]);
    const [clientOrders, setClientOrders] = useState([]);
    const [expandedOrders, setExpandedOrders] = useState({});
    const [orderItemsById, setOrderItemsById] = useState({});
    const [orderItemsLoadingById, setOrderItemsLoadingById] = useState({});
    const [viewMode, setViewMode] = useState(initialView);
    const [loading, setLoading] = useState(true);
    const [dutyLoading, setDutyLoading] = useState(true);
    const [clientOrdersLoading, setClientOrdersLoading] = useState(false);
    const [vkCodeLoading, setVkCodeLoading] = useState(false);
    const [vkLinkCode, setVkLinkCode] = useState(null);
    const [message, setMessage] = useState(null);
    const [newClient, setNewClient] = useState({ fullName: "", number: "" });
    const [searchQuery, setSearchQuery] = useState(searchParams.get("q") || "");

    const updateUrl = (updates) => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            Object.entries(updates).forEach(([key, value]) => {
                if (value) next.set(key, value);
                else next.delete(key);
            });
            return next;
        }, { replace: true });
    };

    const loadClients = async () => {
        setLoading(true);
        try {
            const data = await readJson(await fetch(API_CLIENTS));
            setClients(Array.isArray(data) ? data : []);
        } catch (error) {
            console.error("Ошибка загрузки клиентов:", error);
            setClients([]);
            setMessage({ type: "error", text: "Не удалось загрузить гостевую книгу. Обновите страницу." });
        } finally {
            setLoading(false);
        }
    };

    const loadDutyClients = async () => {
        setDutyLoading(true);
        try {
            const data = await readJson(await fetch(`${API_CLIENTS}/duty`));
            setDutyClients(Array.isArray(data) ? data : []);
        } catch (error) {
            console.error("Ошибка загрузки долгов:", error);
            setDutyClients([]);
            setMessage({ type: "error", text: "Не удалось проверить открытые счета." });
        } finally {
            setDutyLoading(false);
        }
    };

    useEffect(() => {
        Promise.all([loadClients(), loadDutyClients()]);
        // Первый запрос выполняется один раз при открытии страницы.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadClientProfile = async (clientId) => {
        setClientOrdersLoading(true);
        try {
            const [dishesData, ordersData] = await Promise.all([
                fetch(`${API_CLIENTS}/${clientId}/dishes`).then(readJson),
                fetch(`${API_ORDERS}/client/${clientId}`).then(readJson)
            ]);
            setClientDishes(Array.isArray(dishesData) ? dishesData : []);
            const sortedOrders = Array.isArray(ordersData) ? [...ordersData] : [];
            sortedOrders.sort((left, right) => getOrderTimestamp(right) - getOrderTimestamp(left));
            setClientOrders(sortedOrders);
        } catch (error) {
            console.error("Ошибка загрузки профиля клиента:", error);
            setClientDishes([]);
            setClientOrders([]);
            setMessage({ type: "error", text: "Часть данных гостя не загрузилась. Попробуйте открыть профиль ещё раз." });
        } finally {
            setClientOrdersLoading(false);
        }
    };

    const selectView = (view) => {
        setViewMode(view);
        setSelectedClient(null);
        setMessage(null);
        updateUrl({ view: view === "duty" ? "duty" : "" });
    };

    const handleClientSelect = (client) => {
        setSelectedClient(client);
        setViewMode("details");
        setVkLinkCode(null);
        setExpandedOrders({});
        setOrderItemsById({});
        setOrderItemsLoadingById({});
        setMessage(null);
        loadClientProfile(client.clientId);
    };

    const createClient = async (event) => {
        event.preventDefault();
        if (!newClient.fullName.trim()) return;

        setMessage(null);
        try {
            await readJson(await fetch(API_CLIENTS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fullName: newClient.fullName.trim(),
                    number: newClient.number.trim()
                })
            }));
            setNewClient({ fullName: "", number: "" });
            setSearchQuery("");
            updateUrl({ q: "" });
            await loadClients();
            setMessage({ type: "success", text: "Карточка гостя создана." });
        } catch (error) {
            console.error("Ошибка создания клиента:", error);
            setMessage({ type: "error", text: `Не удалось создать гостя: ${error.message}` });
        }
    };

    const createVkLinkCode = async () => {
        if (!selectedClient?.clientId || vkCodeLoading) return;
        setVkCodeLoading(true);
        setMessage(null);
        try {
            const data = await readJson(await fetch(
                `${API_CLIENTS}/${selectedClient.clientId}/vk-link-code`,
                { method: "POST" }
            ));
            setVkLinkCode(data);
            setMessage({ type: "success", text: "Одноразовый VK-код готов." });
        } catch (error) {
            console.error("VK link code error:", error);
            setMessage({ type: "error", text: `Не удалось выдать VK-код: ${error.message}` });
        } finally {
            setVkCodeLoading(false);
        }
    };

    const refreshDebtData = async (clientId) => {
        await Promise.all([loadDutyClients(), loadClients()]);
        if (selectedClient?.clientId === clientId) {
            await loadClientProfile(clientId);
        }
    };

    const markAllDutyAsPaid = async (clientId) => {
        if (!window.confirm("Закрыть все неоплаченные заказы этого гостя?")) return;
        setMessage(null);
        try {
            const data = await readJson(await fetch(`${API_CLIENTS}/${clientId}/duty`, {
                method: "DELETE"
            }));
            await refreshDebtData(clientId);
            setMessage({ type: "success", text: data?.message || "Все долги гостя закрыты." });
        } catch (error) {
            console.error("Ошибка списания долгов:", error);
            setMessage({ type: "error", text: `Не удалось закрыть долг: ${error.message}` });
        }
    };

    const markSingleOrderAsPaid = async (orderId, clientId, paymentAmount, paymentType = "cash") => {
        const amount = Number(paymentAmount);
        if (!Number.isFinite(amount) || amount <= 0) {
            setMessage({ type: "error", text: "Введите сумму платежа больше нуля." });
            return;
        }
        setMessage(null);
        try {
            const idempotencyKey = window.crypto?.randomUUID?.()
                || `debt-${orderId}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
            const data = await readJson(await fetch(`${API_CLIENTS}/debts/${orderId}/payments`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ amount, paymentType, idempotencyKey })
            }));
            await refreshDebtData(clientId);
            setMessage({
                type: "success",
                text: data?.fullyPaid
                    ? `Долг по заказу #${orderId} погашен.`
                    : `Платёж ${formatMoney(data?.amount)} принят. Остаток: ${formatMoney(data?.remainingAmount)}.`
            });
        } catch (error) {
            console.error("Ошибка списания заказа:", error);
            setMessage({ type: "error", text: `Не удалось закрыть заказ: ${error.message}` });
        }
    };

    const toggleOrderDetails = async (orderId) => {
        const isExpanded = Boolean(expandedOrders[orderId]);
        setExpandedOrders((current) => ({ ...current, [orderId]: !isExpanded }));

        if (isExpanded || orderItemsById[orderId] || orderItemsLoadingById[orderId]) return;

        setOrderItemsLoadingById((current) => ({ ...current, [orderId]: true }));
        try {
            const items = await loadOrderDishes(orderId);
            setOrderItemsById((current) => ({ ...current, [orderId]: items }));
        } catch (error) {
            console.error("Ошибка загрузки блюд заказа:", error);
            setOrderItemsById((current) => ({ ...current, [orderId]: [] }));
            setMessage({ type: "error", text: `Не удалось загрузить состав заказа #${orderId}.` });
        } finally {
            setOrderItemsLoadingById((current) => ({ ...current, [orderId]: false }));
        }
    };

    const filteredClients = useMemo(() => {
        const query = searchQuery.trim().toLocaleLowerCase("ru-RU");
        if (!query) return clients;
        return clients.filter((client) =>
            String(client.fullName || "").toLocaleLowerCase("ru-RU").includes(query)
        );
    }, [clients, searchQuery]);

    const totalDutyAmount = useMemo(
        () => dutyClients.reduce((total, item) => total + getClientDebt(item), 0),
        [dutyClients]
    );

    const contactCount = useMemo(
        () => clients.filter((client) => String(client.number || "").trim()).length,
        [clients]
    );

    return (
        <div className={styles.page}>
            <ClientHero
                clientsCount={clients.length}
                contactsCount={contactCount}
                debtClientsCount={dutyClients.length}
                totalDebt={totalDutyAmount}
            />

            <div className={styles.viewBar} aria-label="Разделы гостевой книги">
                <button
                    type="button"
                    className={viewMode !== "duty" ? styles.activeView : ""}
                    aria-pressed={viewMode !== "duty"}
                    onClick={() => selectView("all")}
                >
                    Гостевая книга
                    <span>{clients.length}</span>
                </button>
                <button
                    type="button"
                    className={viewMode === "duty" ? styles.activeView : ""}
                    aria-pressed={viewMode === "duty"}
                    onClick={() => selectView("duty")}
                >
                    Открытые счета
                    <span>{dutyClients.length}</span>
                </button>
            </div>

            {message && (
                <div
                    className={`${styles.message} ${message.type === "error" ? styles.messageError : styles.messageSuccess}`}
                    role={message.type === "error" ? "alert" : "status"}
                >
                    {message.text}
                </div>
            )}

            {viewMode === "all" && (
                <ClientDirectory
                    clients={filteredClients}
                    loading={loading}
                    searchQuery={searchQuery}
                    newClient={newClient}
                    onSearchChange={(value) => {
                        setSearchQuery(value);
                        updateUrl({ q: value.trim() });
                    }}
                    onSearchSubmit={(event) => event.preventDefault()}
                    onResetSearch={() => {
                        setSearchQuery("");
                        updateUrl({ q: "" });
                    }}
                    onNewClientChange={(field, value) => {
                        setNewClient((current) => ({ ...current, [field]: value }));
                    }}
                    onCreateClient={createClient}
                    onSelectClient={handleClientSelect}
                />
            )}

            {viewMode === "duty" && (
                <DebtLedger
                    dutyClients={dutyClients}
                    loading={dutyLoading}
                    totalDebt={totalDutyAmount}
                    onPayAll={markAllDutyAsPaid}
                    onPayOrder={markSingleOrderAsPaid}
                />
            )}

            {viewMode === "details" && selectedClient && (
                <ClientProfile
                    client={selectedClient}
                    dishes={clientDishes}
                    orders={clientOrders}
                    ordersLoading={clientOrdersLoading}
                    expandedOrders={expandedOrders}
                    orderItemsById={orderItemsById}
                    orderItemsLoadingById={orderItemsLoadingById}
                    vkCodeLoading={vkCodeLoading}
                    vkLinkCode={vkLinkCode}
                    onBack={() => selectView("all")}
                    onCreateVkCode={createVkLinkCode}
                    onToggleOrder={toggleOrderDetails}
                />
            )}
        </div>
    );
}
