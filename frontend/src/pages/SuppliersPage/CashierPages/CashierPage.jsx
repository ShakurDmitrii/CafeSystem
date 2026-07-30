import { useEffect, useRef, useState } from "react";
import { API_BASE_URL } from "../../../auth";
import styles from "./CashierPage.module.css";
import DishPickerModal from "./DishPickerModal";
import InventoryShiftReport from "../../Warehouse/InventoryShiftReport";
import CashierHeader from "./cashier-page/CashierHeader";
import CashierModal from "./cashier-page/CashierModal";
import DebtNotification from "./cashier-page/DebtNotification";
import OrderComposer from "./cashier-page/OrderComposer";
import OrdersBoard from "./cashier-page/OrdersBoard";
import ShiftLobby from "./cashier-page/ShiftLobby";
import ShiftReportModal from "./cashier-page/ShiftReportModal";

const API_ORDERS = `${API_BASE_URL}/api/orders`;
const API_SHIFTS = `${API_BASE_URL}/api/shifts`;
const API_PERSONS = `${API_BASE_URL}/api/persons`;
const API_DISHES = `${API_BASE_URL}/api/dishes`;
const API_DISH_SETS = `${API_BASE_URL}/api/dish-sets`;
const API_CLIENTS = `${API_BASE_URL}/api/clients`;
const API_WAREHOUSES = `${API_BASE_URL}/warehouses`;
const API_TODAY_DEBTS = `${API_BASE_URL}/api/clients/today-debts`;
const API_OVERDUE_DEBTS = `${API_BASE_URL}/api/clients/overdue-debts`;
const API_DISH_CATEGORIES = `${API_BASE_URL}/api/dish-categories`;

const resolveShiftPersons = (shift, persons = []) => {
    if (!shift) return [];

    const ids = Array.isArray(shift.personIds) && shift.personIds.length > 0
        ? shift.personIds
            .map((id) => Number(id))
            .filter((id) => Number.isInteger(id) && id >= 0)
        : [Number(shift.personCode)].filter((id) => Number.isInteger(id) && id >= 0);
    const names = Array.isArray(shift.personNames) ? shift.personNames : [];

    return ids.reduce((acc, personId, index) => {
        if (personId == null || Number.isNaN(personId) || acc.some((person) => Number(person.personID) === personId)) {
            return acc;
        }

        const fromCatalog = persons.find((person) => Number(person.personID) === personId);
        acc.push(fromCatalog || {
            personID: personId,
            name: names[index] || shift.personName || `Сотр. #${personId}`
        });
        return acc;
    }, []);
};

const getShiftWorkersLabel = (shift, persons = []) => {
    const workers = resolveShiftPersons(shift, persons);
    return workers.length > 0
        ? workers.map((person) => person.name || `Сотр. #${person.personID}`).join(", ")
        : "Не указаны";
};

const expandOrderItemsForApi = (items = []) => {
    return items
        .filter(Boolean)
        .map((item) => {
            const itemType = item?.itemType === "set" || item?.setId != null ? "set" : "dish";
            const qty = Math.max(1, Number(item?.qty || 1));
            const dishId = Number(item?.dishId);
            const setId = Number(item?.setId);

            return {
                itemType,
                dishID: itemType === "dish" && Number.isInteger(dishId) && dishId >= 0 ? dishId : null,
                setId: itemType === "set" && Number.isInteger(setId) && setId > 0 ? setId : null,
                qty
            };
        })
        .filter((item) =>
            item.qty > 0 && (
                (item.itemType === "dish" && item.dishID != null) ||
                (item.itemType === "set" && item.setId != null)
            )
        );
};

export default function CashierPage() {
    const loadOrdersRef = useRef(null);
    const [orders, setOrders] = useState([]);
    const [currentOrderItems, setCurrentOrderItems] = useState([]);
    const [shiftOpen, setShiftOpen] = useState(false);
    const [currentShift, setCurrentShift] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    const [persons, setPersons] = useState([]);
    const [selectedShiftPersons, setSelectedShiftPersons] = useState([]);
    const [showShiftPersonModal, setShowShiftPersonModal] = useState(false);
    const [shiftPersonSearch, setShiftPersonSearch] = useState("");
    const [isDebt, setIsDebt] = useState(false);
    const [allDishes, setAllDishes] = useState([]);
    const [allDishSets, setAllDishSets] = useState([]);
    const [modalOpen, setModalOpen] = useState(false);
    const [orderType, setOrderType] = useState(false);
    const [allShifts, setAllShifts] = useState([]);
    const [preparationTime, setPreparationTime] = useState(30);
    const [deliveryCost, setDeliveryCost] = useState(0);
    const [paymentType, setPaymentType] = useState("cash"); // cash | transfer | unpaid
    const [deliveryPhone, setDeliveryPhone] = useState("");
    const [deliveryAddress, setDeliveryAddress] = useState("");
    const [dishCategories, setDishCategories] = useState([]);

    const [clients, setClients] = useState([]);
    const [selectedClient, setSelectedClient] = useState(null);
    const [clientSearch, setClientSearch] = useState("");
    const [showClientModal, setShowClientModal] = useState(false);
    const [showClientPickerModal, setShowClientPickerModal] = useState(false);
    const [clientPickerSearch, setClientPickerSearch] = useState("");
    const [newClient, setNewClient] = useState({ fullName: "", number: "" });

    const [debtPaymentDate, setDebtPaymentDate] = useState("");
    const [showDatePicker, setShowDatePicker] = useState(false);
    const [shiftReportOpen, setShiftReportOpen] = useState(false);
    const [shiftReportLoading, setShiftReportLoading] = useState(false);
    const [shiftReport, setShiftReport] = useState(null);
    const [showIssuedOrders, setShowIssuedOrders] = useState(false);
    const [warehouses, setWarehouses] = useState([]);
    const [inventoryReportOpen, setInventoryReportOpen] = useState(false);
    const [inventoryReportShiftId, setInventoryReportShiftId] = useState("");

    // === ДОБАВЛЕНО: Состояния для долгов ===
    const [todayDebts, setTodayDebts] = useState([]);
    const [overdueDebts, setOverdueDebts] = useState([]);
    const [showDebtNotification, setShowDebtNotification] = useState(false);

    const loadWarehouses = async () => {
        try {
            const res = await fetch(API_WAREHOUSES);
            const data = res.ok ? await res.json().catch(() => []) : [];
            setWarehouses(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error("Warehouse loading error:", err);
            setWarehouses([]);
        }
    };

    useEffect(() => {
        loadWarehouses();
    }, []);

    // При загрузке компонента восстанавливаем состояние смены из localStorage
    useEffect(() => {
        const savedShiftId = localStorage.getItem('currentShiftId');
        const savedShiftOpen = localStorage.getItem('shiftOpen') === 'true';
        const savedShiftData = localStorage.getItem('currentShiftData');

        setIsLoading(true);

        fetch(API_PERSONS)
            .then(async (r) => {
                if (!r.ok) {
                    throw new Error(`Не удалось загрузить сотрудников (${r.status})`);
                }
                const text = await r.text();
                return text ? JSON.parse(text) : [];
            })
            .then(d => setPersons(Array.isArray(d) ? d : []))
            .catch(e => console.error("Ошибка загрузки сотрудников:", e));

        fetch(API_DISHES)
            .then(async (r) => {
                if (!r.ok) {
                    throw new Error(`Не удалось загрузить блюда (${r.status})`);
                }
                const text = await r.text();
                return text ? JSON.parse(text) : [];
            })
            .then(d => setAllDishes(Array.isArray(d) ? d : []))
            .catch(e => console.error("Ошибка загрузки блюд:", e));

        fetch(API_DISH_SETS)
            .then(async (r) => {
                if (!r.ok) {
                    throw new Error(`Не удалось загрузить наборы (${r.status})`);
                }
                const text = await r.text();
                return text ? JSON.parse(text) : [];
            })
            .then(d => setAllDishSets(Array.isArray(d) ? d : []))
            .catch(e => console.error("Ошибка загрузки наборов:", e));

        fetch(API_DISH_CATEGORIES)
            .then(async (r) => {
                if (!r.ok) {
                    throw new Error(`Не удалось загрузить категории (${r.status})`);
                }
                const text = await r.text();
                return text ? JSON.parse(text) : [];
            })
            .then(d => setDishCategories(Array.isArray(d) ? d : []))
            .catch(e => console.error("Ошибка загрузки категорий блюд:", e));

        fetchShifts()
            .then((loadedShifts) => {
                // Если есть сохраненная смена, восстанавливаем ее
                if (savedShiftOpen && savedShiftId && savedShiftData) {
                    try {
                        const savedShift = JSON.parse(savedShiftData);
                        const restoredShift = Array.isArray(loadedShifts)
                            ? loadedShifts.find((shift) => Number(shift.shiftId) === Number(savedShiftId)) || savedShift
                            : savedShift;
                        setCurrentShift(restoredShift);
                        setShiftOpen(true);
                        setSelectedShiftPersons(resolveShiftPersons(restoredShift));

                        // Загружаем заказы для восстановленной смены
                        loadOrdersForShift(restoredShift.shiftId);
                    } catch (e) {
                        console.error("Ошибка восстановления смены:", e);
                        localStorage.removeItem('currentShiftId');
                        localStorage.removeItem('shiftOpen');
                        localStorage.removeItem('currentShiftData');
                    }
                }
            })
            .finally(() => setIsLoading(false));

        loadClients();

        // Устанавливаем минимальную дату как сегодня
        const today = new Date();
        today.setDate(today.getDate() + 1);
        const tomorrow = today.toISOString().split('T')[0];
        setDebtPaymentDate(tomorrow);
    }, []);

    // Сохраняем состояние смены в localStorage при изменении
    useEffect(() => {
        if (currentShift) {
            localStorage.setItem('currentShiftId', currentShift.shiftId);
            localStorage.setItem('shiftOpen', shiftOpen.toString());
            localStorage.setItem('currentShiftData', JSON.stringify(currentShift));
        }
    }, [currentShift, shiftOpen]);

    useEffect(() => {
        if (shiftOpen && currentShift) {
            setSelectedShiftPersons(resolveShiftPersons(currentShift, persons));
        }
    }, [persons, currentShift, shiftOpen]);

    // Автоматически сбрасываем чекбокс долга, если клиент был убран
    useEffect(() => {
        if (!selectedClient && isDebt) {
            setIsDebt(false);
            setShowDatePicker(false);
        }
    }, [selectedClient, isDebt]);

    useEffect(() => {
        if (!orderType) return;
        if (selectedClient?.number) {
            setDeliveryPhone(selectedClient.number);
            return;
        }
        setDeliveryPhone("");
    }, [orderType, selectedClient]);

    const requiresContactDetails = orderType;
    const effectivePhone = (deliveryPhone || "").trim() || (selectedClient?.number || "").trim();
    const effectiveAddress = (deliveryAddress || "").trim();
    const selectedShiftPersonIds = new Set(selectedShiftPersons.map((person) => Number(person.personID)));
    const filteredShiftPersons = persons.filter((person) => {
        const query = shiftPersonSearch.trim().toLowerCase();
        if (!query) return true;
        return String(person.name || "").toLowerCase().includes(query);
    });
    const currentShiftWorkersLabel = getShiftWorkersLabel(currentShift, persons);

    const toggleShiftPersonSelection = (person) => {
        if (person?.personID == null) return;
        setSelectedShiftPersons((prev) => {
            const exists = prev.some((item) => Number(item.personID) === Number(person.personID));
            if (exists) {
                return prev.filter((item) => Number(item.personID) !== Number(person.personID));
            }
            return [...prev, person];
        });
    };

    // === ДОБАВЛЕНО: Функция проверки долгов ===
    const checkDebts = async () => {
        try {
            const [todayResponse, overdueResponse] = await Promise.all([
                fetch(API_TODAY_DEBTS),
                fetch(API_OVERDUE_DEBTS)
            ]);

            const today = await todayResponse.json();
            const overdue = await overdueResponse.json();

            setTodayDebts(Array.isArray(today) ? today : []);
            setOverdueDebts(Array.isArray(overdue) ? overdue : []);

            // Показываем уведомление если есть долги
            if ((Array.isArray(today) && today.length > 0) ||
                (Array.isArray(overdue) && overdue.length > 0)) {
                setShowDebtNotification(true);
            }

        } catch (e) {
            console.error("Ошибка загрузки долгов:", e);
        }
    };

    // === ДОБАВЛЕНО: Проверка долгов при открытии смены ===
    useEffect(() => {
        if (shiftOpen && currentShift) {
            // Проверить долги при открытии смены
            checkDebts();

            // Проверять долги каждые 30 минут
            const debtInterval = setInterval(() => {
                checkDebts();
            }, 30 * 60 * 1000); // 30 минут

            return () => clearInterval(debtInterval);
        }
    }, [shiftOpen, currentShift]);

    const fetchShifts = async () => {
        try {
            const response = await fetch(API_SHIFTS);
            if (!response.ok) {
                throw new Error(`Не удалось загрузить смены (${response.status})`);
            }
            const text = await response.text();
            const data = text ? JSON.parse(text) : [];
            setAllShifts(Array.isArray(data) ? data : []);
            return data;
        } catch (e) {
            console.error("Ошибка загрузки смен:", e);
            return [];
        }
    };

    const loadClients = () => {
        fetch(API_CLIENTS)
            .then(async (r) => {
                if (!r.ok) {
                    throw new Error(`Не удалось загрузить клиентов (${r.status})`);
                }
                const text = await r.text();
                return text ? JSON.parse(text) : [];
            })
            .then(d => setClients(Array.isArray(d) ? d : []))
            .catch(e => console.error("Ошибка загрузки клиентов:", e));
    };

    // Улучшенная функция загрузки заказов
    const loadOrdersForShift = async (shiftId) => {
        if (!shiftId) return;

        try {
            const response = await fetch(API_ORDERS);
            const allOrders = await response.json();

            if (Array.isArray(allOrders)) {
                // Фильтруем заказы по shiftId
                const filtered = allOrders.filter(o => o.shiftId === shiftId);
                console.log("Загружено заказов для смены", shiftId, ":", filtered.length);

                // Сортируем заказы: сначала неготовые, потом готовые, затем выданные
                const sortedOrders = filtered.sort((a, b) => {
                    const aIssued = Boolean(a.date_issue || a.dateIssue);
                    const bIssued = Boolean(b.date_issue || b.dateIssue);

                    if (aIssued !== bIssued) {
                        return aIssued ? 1 : -1;
                    }

                    // Сначала по статусу (неготовые выше готовых) среди невыданных
                    if (!aIssued && !bIssued && a.status !== b.status) {
                        return a.status ? 1 : -1;
                    }

                    // Для неготовых сортируем по наличию задержки
                    if (!aIssued && !bIssued && !a.status && !b.status) {
                        if (a.timeDelay > 0 && b.timeDelay === 0) return -1;
                        if (a.timeDelay === 0 && b.timeDelay > 0) return 1;
                        // Новые заказы сверху
                        return b.orderId - a.orderId;
                    }

                    // Для готовых тоже новые заказы сверху
                    return b.orderId - a.orderId;
                });

                setOrders(sortedOrders);

                // Также сохраняем в localStorage для быстрого восстановления
                localStorage.setItem(`orders_shift_${shiftId}`, JSON.stringify(sortedOrders));
            }
        } catch (e) {
            console.error("Ошибка загрузки заказов", e);

            // Пробуем восстановить из localStorage
            const savedOrders = localStorage.getItem(`orders_shift_${shiftId}`);
            if (savedOrders) {
                try {
                    setOrders(JSON.parse(savedOrders));
                } catch (parseError) {
                    console.error("Ошибка парсинга сохраненных заказов:", parseError);
                }
            }
        }
    };

    // Универсальная функция загрузки заказов для текущей смены
    const loadOrders = () => {
        if (currentShift && currentShift.shiftId) {
            loadOrdersForShift(currentShift.shiftId);
        }
    };
    loadOrdersRef.current = loadOrders;

    // Периодическая загрузка заказов при открытой смене
    useEffect(() => {
        if (shiftOpen && currentShift) {
            // Сразу загружаем заказы
            loadOrdersRef.current?.();

            // Устанавливаем интервал обновления каждые 5 секунд
            const interval = setInterval(() => loadOrdersRef.current?.(), 5000);

            // Загружаем заказы при фокусировке окна
            const handleFocus = () => loadOrdersRef.current?.();
            window.addEventListener('focus', handleFocus);

            return () => {
                clearInterval(interval);
                window.removeEventListener('focus', handleFocus);
            };
        }
    }, [shiftOpen, currentShift]);

    const createShift = () => {
        if (selectedShiftPersons.length === 0) {
            alert("Выберите хотя бы одного сотрудника для открытия смены!");
            return;
        }

        setIsLoading(true);

        fetch(`${API_SHIFTS}/create`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                data: new Date().toISOString().slice(0, 10),
                startTime: new Date().toTimeString().slice(0, 8),
                endTime: null,
                income: 0,
                profit: 0,
                expenses: 0,
                personCode: Number(selectedShiftPersons[0]?.personID),
                personIds: selectedShiftPersons
                    .map((person) => Number(person.personID))
                    .filter((id) => Number.isInteger(id) && id >= 0)
            })
        })
            .then(async (r) => {
                const text = await r.text();
                const body = text ? JSON.parse(text) : null;
                if (!r.ok) {
                    throw new Error(body?.message || `Ошибка создания смены (${r.status})`);
                }
                return body;
            })
            .then(shift => {
                setCurrentShift(shift);
                setSelectedShiftPersons(resolveShiftPersons(shift, persons));
                setShiftOpen(true);
                setShowShiftPersonModal(false);
                setShiftPersonSearch("");
                loadOrdersForShift(shift.shiftId);
                fetchShifts();
            })
            .catch(e => {
                console.error("Ошибка создания смены:", e);
                alert(e.message || "Не удалось открыть смену");
            })
            .finally(() => setIsLoading(false));
    };

    const openExistingShift = (shift) => {
        setIsLoading(true);
        setCurrentShift(shift);
        setShiftOpen(true);
        setSelectedShiftPersons(resolveShiftPersons(shift, persons));

        // Загружаем заказы для смены
        loadOrdersForShift(shift.shiftId)
            .then(() => {
                console.log("Смена успешно открыта");
            })
            .catch(e => console.error("Ошибка открытия смены:", e))
            .finally(() => setIsLoading(false));
    };

    const closeShift = () => {
        if (!currentShift?.shiftId) return;
        setInventoryReportShiftId(String(currentShift.shiftId));
        setInventoryReportOpen(true);
    };

    const finalizeCloseShift = () => {
        if (!currentShift) return;

        setIsLoading(true);

        const expenses = Number(currentShift.expenses || 0);
        fetch(`${API_SHIFTS}/${currentShift.shiftId}/close?expenses=${encodeURIComponent(expenses)}`, {
            method: "POST",
        })
            .then(async (response) => {
                const text = await response.text();
                const body = text ? JSON.parse(text) : null;
                if (!response.ok) {
                    throw new Error(body?.message || `Ошибка закрытия смены (${response.status})`);
                }
                return body;
            })
            .then(() => {
                // Очищаем localStorage при закрытии смены
                localStorage.removeItem('currentShiftId');
                localStorage.removeItem('shiftOpen');
                localStorage.removeItem('currentShiftData');
                localStorage.removeItem(`orders_shift_${currentShift.shiftId}`);

                setShiftOpen(false);
                setCurrentShift(null);
                setOrders([]);
                setSelectedShiftPersons([]);
                setShowShiftPersonModal(false);
                setShiftPersonSearch("");
                setCurrentOrderItems([]);
                setSelectedClient(null);
                setIsDebt(false);
                setShowDatePicker(false);
                setInventoryReportOpen(false);
                setInventoryReportShiftId("");
                fetchShifts();
            })
            .catch(e => {
                console.error("Ошибка закрытия смены:", e);
                alert(e.message || "Не удалось закрыть смену");
            })
            .finally(() => setIsLoading(false));
    };

    const printZReport = async () => {
        if (!currentShift?.shiftId) {
            alert("Сначала откройте смену");
            return;
        }

        try {
            const res = await fetch(`${API_SHIFTS}/${currentShift.shiftId}/z-report`);
            const report = await res.json();
            if (!res.ok) {
                throw new Error(report?.message || `Ошибка Z-отчета (${res.status})`);
            }

            const workers = Array.isArray(report.workers) && report.workers.length > 0
                ? report.workers.join(", ")
                : "Не указаны";

            const lines = [];
            lines.push("Z-ОТЧЕТ");
            lines.push(`Дата: ${report.date || "-"}`);
            lines.push(`Смена №: ${report.shiftId}`);
            lines.push(`Начало: ${report.startTime || "-"}`);
            lines.push(`Конец: ${report.endTime || "-"}`);
            lines.push(`Работники: ${workers}`);
            lines.push("");
            lines.push("ЗАКАЗЫ:");

            (report.orders || []).forEach((order) => {
                lines.push(
                    `Заказ #${order.orderId} | ${order.isDelivery ? "Доставка" : "По месту"} | `
                    + `Оплата: ${order.paymentType || "-"} | Сумма: ${Number(order.orderAmount || 0).toFixed(2)} ₽`
                );
                (order.items || []).forEach((item) => {
                    lines.push(
                        `  - ${item.dishName}: ${item.qty} x ${Number(item.price || 0).toFixed(2)} = ${Number(item.sum || 0).toFixed(2)} ₽`
                    );
                });
                if (Number(order.deliveryExpense || 0) > 0) {
                    lines.push(`    Доставка: ${Number(order.deliveryExpense).toFixed(2)} ₽`);
                }
                lines.push("");
            });

            lines.push("ИТОГИ:");
            lines.push(`Заказов: ${report.totals?.ordersCount ?? 0}`);
            lines.push(`Оплачено: ${report.totals?.paidOrdersCount ?? 0}`);
            lines.push(`Не оплачено: ${report.totals?.unpaidOrdersCount ?? 0}`);
            lines.push(`Позиции блюд (шт): ${report.totals?.dishesCount ?? 0}`);
            lines.push(`Сумма по блюдам: ${Number(report.totals?.itemsAmount || 0).toFixed(2)} ₽`);
            lines.push(`Траты на доставку: ${Number(report.totals?.deliveryExpense || 0).toFixed(2)} ₽`);
            lines.push(`Общая выручка: ${Number(report.totals?.revenue || 0).toFixed(2)} ₽`);
            lines.push(`Неоплаченная сумма: ${Number(report.totals?.unpaidAmount || 0).toFixed(2)} ₽`);
            lines.push(`Себестоимость: ${Number(report.totals?.cost || 0).toFixed(2)} ₽`);
            lines.push(`Расходы смены: ${Number(report.totals?.expenses || 0).toFixed(2)} ₽`);
            lines.push(`Прибыль: ${Number(report.totals?.profit || 0).toFixed(2)} ₽`);

            const reportText = lines.join("\n");

            const w = window.open("", "_blank", "width=800,height=900");
            if (!w) {
                alert("Не удалось открыть окно печати. Разрешите всплывающие окна.");
                return;
            }
            w.document.write(`
                <html>
                  <head><title>Z-Отчет смены ${report.shiftId}</title></head>
                  <body style="font-family: Consolas, monospace; white-space: pre-wrap; padding: 16px;">
${reportText}
                  </body>
                </html>
            `);
            w.document.close();
            w.focus();
            w.print();
        } catch (e) {
            console.error("Ошибка формирования Z-отчета:", e);
            alert(e.message || "Не удалось сформировать Z-отчет");
        }
    };

    const openShiftReport = async (shiftId) => {
        setShiftReportLoading(true);
        setShiftReportOpen(true);
        try {
            const res = await fetch(`${API_SHIFTS}/${shiftId}/z-report`);
            const body = await res.json();
            if (!res.ok) {
                throw new Error(body?.message || `Ошибка загрузки отчета (${res.status})`);
            }
            setShiftReport(body);
        } catch (e) {
            console.error("Ошибка загрузки отчета смены:", e);
            setShiftReport({ error: e.message || "Не удалось загрузить отчет" });
        } finally {
            setShiftReportLoading(false);
        }
    };

    const createOrder = async () => {
        if (currentOrderItems.length === 0 || !currentShift) {
            alert("Добавьте позиции в заказ!");
            return;
        }

        if (orderItemsForApi.length === 0) {
            alert("В заказе нет позиций, которые можно отправить на кухню.");
            return;
        }

        if (isDebt && !selectedClient) {
            alert("Для долга выберите клиента!");
            return;
        }

        if (requiresContactDetails) {
            if (!effectivePhone) {
                alert(orderType ? "Введите номер телефона для доставки!" : "Введите номер телефона для перевода!");
                return;
            }
            if (!effectiveAddress) {
                alert(orderType ? "Введите адрес доставки!" : "Введите адрес!");
                return;
            }
        }

        const total = totalOrderAmount;

        try {
            let debtPayment = null;
            if (isDebt && debtPaymentDate) {
                debtPayment = debtPaymentDate;
            }

            const orderPayload = {
                clientId: selectedClient?.clientId ?? null,
                shiftId: currentShift.shiftId,
                date: new Date().toISOString().slice(0, 10),
                amount: total,
                status: false,
                time: preparationTime,
                duty: isDebt,
                type: orderType,
                deliveryPhone: requiresContactDetails ? effectivePhone : null,
                deliveryAddress: requiresContactDetails ? effectiveAddress : null,
                paymentType,
                paid: paymentType !== "unpaid",
                debt_payment_date: debtPayment,
                items: orderItemsForApi
            };

            console.log("Отправляем заказ на сервер:", orderPayload);

            const orderResponse = await fetch(API_ORDERS, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(orderPayload)
            });

            const order = await orderResponse.json();
            console.log("Создан заказ с orderId:", order.orderId);
            order.paymentType = paymentType;
            order.paid = paymentType !== "unpaid";
            order.deliveryCost = orderType ? Number(deliveryCost || 0) : 0;
            order.deliveryPhone = requiresContactDetails ? effectivePhone : "";
            order.deliveryAddress = requiresContactDetails ? effectiveAddress : "";
            if (selectedClient?.fullName) order.clientName = selectedClient.fullName;
            if (selectedClient?.number) order.clientPhone = selectedClient.number;
            order.items = currentOrderItems.map(i => ({
                dishName: i.dishName || i.name || i.setName || "Позиция",
                name: i.dishName || i.name || i.setName || "Позиция",
                itemType: i.itemType || "dish",
                qty: i.qty || 1,
                price: i.price || 0,
                sum: Number(i.price || 0) * Number(i.qty || 1)
            }));

            // Обновляем список заказов (добавляем новый заказ в начало)
            setOrders(prev => [order, ...prev]);

            // Очищаем форму
            setCurrentOrderItems([]);
            setOrderType(false);
            setDeliveryCost(0);
            setPaymentType("cash");
            setDeliveryPhone("");
            setDeliveryAddress("");
            setPreparationTime(30);
            setIsDebt(false);
            setShowDatePicker(false);
            setSelectedClient(null);

            // Сбрасываем дату погашения долга на завтра
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 1);
            setDebtPaymentDate(tomorrow.toISOString().split('T')[0]);

        } catch (e) {
            console.error("Ошибка создания заказа:", e);
            alert("Ошибка при создании заказа. Проверьте подключение к серверу.");
        }
    };

    const createNewClient = () => {
        if (!newClient.fullName.trim()) {
            alert("Введите ФИО клиента");
            return;
        }

        fetch(API_CLIENTS, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                fullName: newClient.fullName.trim(),
                number: newClient.number?.trim() || ""
            })
        })
            .then(r => r.json())
            .then(client => {
                alert("✅ Клиент создан!");
                setClients(prev => [client, ...prev]);
                setSelectedClient(client);
                setShowClientModal(false);
                setNewClient({ fullName: "", number: "" });

                if (isDebt) {
                    setShowDatePicker(true);
                }
            })
            .catch(e => {
                console.error("Ошибка создания клиента:", e);
                alert("❌ Ошибка создания клиента");
            });
    };

    const handleDebtCheckboxChange = (e) => {
        const checked = e.target.checked;

        if (checked && !selectedClient) {
            setShowClientModal(true);
        } else if (checked && selectedClient) {
            setIsDebt(true);
            setShowDatePicker(true);
        } else {
            setIsDebt(false);
            setShowDatePicker(false);
        }
    };

    const markOrderReady = (orderId) => {
        fetch(`${API_ORDERS}/${orderId}/status`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status: true })
        })
            .then(() => {
                setOrders(prev => prev.map(o =>
                    o.orderId === orderId ? { ...o, status: true } : o
                ));
            })
            .catch(e => console.error("Ошибка обновления статуса заказа:", e));
    };

    const updateOrderPayment = async (orderId, nextPaymentType) => {
        const res = await fetch(`${API_ORDERS}/${orderId}/payment`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                paymentType: nextPaymentType,
                paid: nextPaymentType !== "unpaid"
            })
        });

        let body = null;
        try {
            body = await res.json();
        } catch {
            body = null;
        }

        if (!res.ok) {
            throw new Error(body?.message || `Ошибка обновления оплаты (${res.status})`);
        }

        setOrders(prev => prev.map(o => (
            o.orderId === orderId
                ? {
                    ...o,
                    paymentType: body?.paymentType || nextPaymentType,
                    paid: body?.paid ?? (nextPaymentType !== "unpaid")
                }
                : o
        )));

        return body;
    };

    const issueOrder = async (orderId) => {
        const res = await fetch(`${API_ORDERS}/${orderId}/issue`, {
            method: "PATCH"
        });

        let body = null;
        try {
            body = await res.json();
        } catch {
            body = null;
        }

        if (!res.ok) {
            throw new Error(body?.message || `Ошибка выдачи заказа (${res.status})`);
        }

        setOrders((prev) => {
            const next = prev.map((o) => (
                o.orderId === orderId
                    ? {
                        ...o,
                        date_issue: body?.date_issue || body?.dateIssue || new Date().toISOString().slice(0, 10),
                        dateIssue: body?.dateIssue || body?.date_issue || new Date().toISOString().slice(0, 10)
                    }
                    : o
            ));
            if (currentShift?.shiftId) {
                localStorage.setItem(`orders_shift_${currentShift.shiftId}`, JSON.stringify(next));
            }
            return next;
        });

        return body;
    };

    const formatTicketMoney = (value) => {
        const num = Number(value || 0);
        const rounded = Math.round((Number.isFinite(num) ? num : 0) * 100) / 100;
        if (Math.abs(rounded) < 0.005) return "0";
        return rounded.toFixed(2).replace(/\.?0+$/, "");
    };

    const normalizeTicketItems = (rawItems) => {
        if (!Array.isArray(rawItems)) return [];
        return rawItems.map((item) => {
            const name = item?.dishName || item?.name || item?.title || item?.dish_name || "Позиция";
            const qtyRaw = item?.qty ?? item?.quantity ?? item?.count ?? item?.amount ?? 1;
            const qty = Math.max(1, Number(qtyRaw || 1));
            const priceRaw = item?.price ?? item?.cost ?? item?.unitPrice ?? item?.unit_price ?? null;
            const sumRaw = item?.sum ?? item?.total ?? item?.lineTotal ?? item?.line_total ?? null;
            let price = Number(priceRaw || 0);
            let sum = Number(sumRaw || 0);
            if (!Number.isFinite(price) && Number.isFinite(sum)) {
                price = qty ? sum / qty : sum;
            }
            if (!Number.isFinite(sum)) {
                sum = price * qty;
            }
            return {
                dishName: name,
                qty,
                price,
                sum
            };
        });
    };

    const escapeHtml = (value) => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");

    const buildOrderNumberTicketLines = (orderId) => ([
        `ЗАКАЗ №${orderId}`,
        "",
        "",
        "",
        "",
        ""
    ]);

    const printLinesInWindow = (title, lines, style = {}) => {
        const fontSize = style.fontSize || 18;
        const fontWeight = style.fontWeight || 700;
        const align = style.align || "left";
        const lineHeight = style.lineHeight || 1.35;
        const pageSize = style.pageSize || "58mm auto";
        const pageWidth = style.pageWidth || "55mm";
        const pageMargin = style.pageMargin || "0mm";
        const pagePadding = style.pagePadding
            || (typeof style.padding === "number"
                ? `${style.padding}px`
                : (style.padding || "2mm"));

        const normalized = (lines || []).map((line) => {
            if (typeof line === "string") return { text: line };
            if (line && typeof line === "object") return line;
            return { text: String(line ?? "") };
        });

        const htmlLines = normalized.map((line) => {
            const text = line.text === "" ? " " : String(line.text ?? "");
            const size = line.fontSize || fontSize;
            const weight = line.fontWeight || fontWeight;
            const lineAlign = line.align || align;
            const lh = line.lineHeight || lineHeight;
            return `
                <div style="white-space:pre-wrap; font-size:${size}px; font-weight:${weight}; line-height:${lh}; text-align:${lineAlign};">
                    ${escapeHtml(text)}
                </div>
            `;
        }).join("");

        const html = `
            <html>
                <head>
                    <meta charset="UTF-8" />
                    <title>${escapeHtml(title)}</title>
                    <style>
                        @page { size: ${pageSize}; margin: ${pageMargin}; }
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            padding: ${pagePadding};
                            width: ${pageWidth};
                            font-family: 'Courier New', monospace;
                        }
                    </style>
                </head>
                <body>
                    ${htmlLines}
                </body>
            </html>
        `;

        const w = window.open("", "_blank", "width=500,height=800");
        if (!w) {
            throw new Error("Не удалось открыть окно печати");
        }
        w.document.write(html);
        w.document.close();
        w.focus();
        w.print();
    };

    const printKitchenTicketWindow = (order, rawItems = []) => {
        const items = normalizeTicketItems(rawItems);
        const paymentRaw = (order.paymentType || "").toLowerCase();
        const paymentLabel = paymentRaw === "cash"
            ? "Наличка"
            : paymentRaw === "transfer"
                ? "Перевод"
                : "Не оплачено";
        const isDelivery = Boolean(order.type);
        let deliveryCost = Number(
            order.deliveryCost ??
            order.delivery_cost ??
            order.deliveryExpense ??
            order.delivery_expense ??
            0
        );
        if (!Number.isFinite(deliveryCost)) {
            deliveryCost = 0;
        }

        const itemRows = items.length > 0
            ? items.map((item) => {
                const qty = Number(item.qty || 0);
                const price = Number(item.price || 0);
                const sum = Number(item.sum || qty * price || 0);
                return `
                    <div style="padding:8px 0;border-bottom:1px dashed #d6d6d6;">
                        <div style="font-size:16px;font-weight:800;line-height:1.25;word-break:break-word;">
                            ${escapeHtml(item.dishName || "Позиция")}
                        </div>
                        <div style="display:flex;justify-content:space-between;gap:8px;font-size:14px;font-weight:700;margin-top:4px;">
                            <span>${escapeHtml(String(qty))} x ${escapeHtml(formatTicketMoney(price))} ₽</span>
                            <strong style="font-weight:800;">${escapeHtml(formatTicketMoney(sum))} ₽</strong>
                        </div>
                    </div>
                `;
            }).join("")
            : `<div style="padding:10px 0;font-size:15px;font-weight:700;">Состав заказа не найден</div>`;

        const contactPhone = order.deliveryPhone || order.clientPhone || order.client_number || order.clientNumber || "";
        const contactAddress = order.deliveryAddress || order.delivery_address || order.clientAddress || order.client_address || "";
        const createdAt = order.created_at || order.createdAt || "";

        const html = `
            <html>
                <head>
                    <meta charset="UTF-8" />
                    <title>${escapeHtml(`Чек заказа №${order.orderId}`)}</title>
                    <style>
                        @page { size: 58mm auto; margin: 0; }
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            width: 58mm;
                            padding: 3mm;
                            font-family: Arial, 'DejaVu Sans', sans-serif;
                            font-weight: 600;
                            color: #111;
                            background: #fff;
                            -webkit-font-smoothing: none;
                            text-rendering: geometricPrecision;
                        }
                    </style>
                </head>
                <body>
                    <div style="text-align:center;font-weight:900;font-size:22px;letter-spacing:0.03em;">ЗАКАЗ №${escapeHtml(order.orderId)}</div>
                    ${createdAt ? `<div style="margin-top:6px;font-size:13px;font-weight:700;">Время: ${escapeHtml(String(createdAt))}</div>` : ""}
                    <div style="margin:8px 0;border-top:1px solid #000;"></div>
                    ${itemRows}
                    <div style="margin-top:8px;border-top:1px solid #000;padding-top:8px;">
                        <div style="display:flex;justify-content:space-between;font-size:17px;font-weight:900;">
                            <span>ИТОГО</span>
                            <span>${escapeHtml(formatTicketMoney(order.amount))} ₽</span>
                        </div>
                        <div style="margin-top:6px;font-size:14px;font-weight:700;">Тип: ${isDelivery ? "Доставка" : "В зале"}</div>
                        ${isDelivery ? `<div style="margin-top:4px;font-size:14px;font-weight:700;">Доставка: ${escapeHtml(formatTicketMoney(deliveryCost))} ₽</div>` : ""}
                        <div style="margin-top:4px;font-size:14px;font-weight:700;">Оплата: ${escapeHtml(paymentLabel)}</div>
                        ${contactPhone ? `<div style="margin-top:6px;font-size:14px;font-weight:700;word-break:break-word;">Телефон: ${escapeHtml(contactPhone)}</div>` : ""}
                        ${contactAddress ? `<div style="margin-top:4px;font-size:14px;font-weight:700;word-break:break-word;">Адрес: ${escapeHtml(contactAddress)}</div>` : ""}
                    </div>
                </body>
            </html>
        `;

        const w = window.open("", "_blank", "width=420,height=820");
        if (!w) {
            throw new Error("Не удалось открыть окно печати");
        }
        w.document.write(html);
        w.document.close();
        w.focus();
        window.setTimeout(() => {
            w.print();
        }, 180);
    };

    const printOrderNumberTicket = async (order) => {
        const title = `Чек заказа №${order.orderId}`;
        const numberLines = buildOrderNumberTicketLines(order.orderId);
        printLinesInWindow(`${title} - Номер`, numberLines, {
            fontSize: 44,
            fontWeight: 800,
            align: "center",
            lineHeight: 1.1,
            padding: 10
        });
        return { status: "order_number_printed_only" };
    };

    const printOrderDetailsTicket = async (order, orderItems = []) => {
        let items = Array.isArray(orderItems) ? orderItems : [];
        try {
            const payloadRes = await fetch(`${API_ORDERS}/${order.orderId}/kitchen-payload`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    paymentType: order.paymentType,
                    deliveryCost: Number(
                        order.deliveryCost ??
                        order.delivery_cost ??
                        order.deliveryExpense ??
                        order.delivery_expense ??
                        0
                    ),
                    deliveryPhone: order.deliveryPhone || order.clientPhone || order.client_number || order.clientNumber || null,
                    deliveryAddress: order.deliveryAddress || order.delivery_address || order.clientAddress || order.client_address || null
                })
            });

            if (payloadRes.ok) {
                const payload = await payloadRes.json();
                const payloadItems = Array.isArray(payload?.items) ? payload.items : [];
                const payloadOrder = {
                    ...order,
                    amount: payload?.total ?? order.amount,
                    type: payload?.isDelivery ?? order.type,
                    paymentType: payload?.paymentType ?? order.paymentType,
                    deliveryPhone: payload?.deliveryPhone ?? order.deliveryPhone,
                    deliveryAddress: payload?.deliveryAddress ?? order.deliveryAddress,
                    created_at: payload?.createdAt ?? order.created_at ?? order.createdAt,
                    createdAt: payload?.createdAt ?? order.createdAt ?? order.created_at
                };
                printKitchenTicketWindow(payloadOrder, payloadItems);
                return { status: "order_details_printed_only" };
            }
        } catch (payloadError) {
            console.error("Ошибка получения payload для печати:", payloadError);
        }

        if (items.length === 0) {
            try {
                const dishesRes = await fetch(`${API_BASE_URL}/api/shifts/getDish/${order.orderId}`);
                if (dishesRes.ok) {
                    const dishes = await dishesRes.json();
                    if (Array.isArray(dishes)) {
                        items = dishes;
                    } else if (Array.isArray(dishes?.items)) {
                        items = dishes.items;
                    } else if (Array.isArray(dishes?.data)) {
                        items = dishes.data;
                    }
                }
            } catch (e) {
                console.error("Ошибка загрузки позиций для чека:", e);
            }
        }

        if (items.length === 0 && Array.isArray(order.items)) {
            items = order.items;
        }

        printKitchenTicketWindow(order, items);
        return { status: "order_details_printed_only" };
    };

    const filteredClients = clientSearch.trim()
        ? clients.filter(c =>
            c.fullName?.toLowerCase().includes(clientSearch.toLowerCase()) ||
            c.number?.includes(clientSearch))
        : clients;

    const filteredPickerClients = clientPickerSearch.trim()
        ? clients.filter(c =>
            c.fullName?.toLowerCase().includes(clientPickerSearch.toLowerCase()) ||
            c.number?.includes(clientPickerSearch))
        : clients;

    const orderItemsTotal = currentOrderItems.reduce(
        (sum, i) => sum + (Number(i.qty || 1) * Number(i.price || 0)),
        0
    );
    const totalOrderAmount = orderItemsTotal + (orderType ? Number(deliveryCost || 0) : 0);
    const orderItemsForApi = expandOrderItemsForApi(currentOrderItems);

    // Функция для разделения заказов на группы
    const getSortedOrders = () => {
        // Разделяем заказы на группы
        const cookingOrders = orders
            .filter(o => !o.status && !o.date_issue && !o.dateIssue)
            .sort((a, b) => {
                // Сначала с задержкой
                if (a.timeDelay > 0 && b.timeDelay === 0) return -1;
                if (a.timeDelay === 0 && b.timeDelay > 0) return 1;
                // Потом новые сверху
                return b.orderId - a.orderId;
            });

        const readyOrders = orders
            .filter(o => o.status && !o.date_issue && !o.dateIssue)
            .sort((a, b) => {
                // Новые заказы сверху
                return b.orderId - a.orderId;
            });

        const issuedOrders = orders
            .filter(o => o.date_issue || o.dateIssue)
            .sort((a, b) => b.orderId - a.orderId);

        return { cookingOrders, readyOrders, issuedOrders };
    };

    const sortedShifts = [...allShifts].sort((a, b) => (b.shiftId || 0) - (a.shiftId || 0));

    if (isLoading) {
        return (
            <div className={styles.page}>
                <CashierHeader
                    shiftOpen={false}
                    currentShift={null}
                    workersLabel=""
                    debtsCount={0}
                    isLoading
                />
                <div className={styles.loadingContainer}>
                    <div className={styles.spinner} aria-hidden="true" />
                    <div role="status">Подготавливаем кассу…</div>
                </div>
            </div>
        );
    }

    const { cookingOrders, readyOrders, issuedOrders } = getSortedOrders();

    return (
        <div className={styles.page}>
            <CashierHeader
                shiftOpen={shiftOpen}
                currentShift={currentShift}
                workersLabel={currentShiftWorkersLabel}
                debtsCount={todayDebts.length + overdueDebts.length}
                isLoading={isLoading}
                onShowDebts={() => setShowDebtNotification(true)}
                onPrintReport={printZReport}
                onOpenKitchen={() => {
                    if (!currentShift?.shiftId) return;
                    window.open(
                        `${window.location.origin}/kitchen-display/${currentShift.shiftId}`,
                        "_blank",
                        "noopener,noreferrer"
                    );
                }}
            />

            {!shiftOpen ? (
                <ShiftLobby
                    selectedPeople={selectedShiftPersons}
                    shifts={sortedShifts}
                    isLoading={isLoading}
                    getWorkersLabel={(shift) => getShiftWorkersLabel(shift, persons)}
                    onChoosePeople={() => setShowShiftPersonModal(true)}
                    onRemovePerson={toggleShiftPersonSelection}
                    onCreateShift={createShift}
                    onOpenShift={openExistingShift}
                    onOpenReport={openShiftReport}
                />
            ) : (
                <div className={styles.cashierWorkspace}>
                    <OrderComposer
                        selectedClient={selectedClient}
                        clientSearch={clientSearch}
                        filteredClients={filteredClients}
                        items={currentOrderItems}
                        orderType={orderType}
                        deliveryCost={deliveryCost}
                        deliveryPhone={deliveryPhone}
                        deliveryAddress={deliveryAddress}
                        paymentType={paymentType}
                        isDebt={isDebt}
                        showDatePicker={showDatePicker}
                        debtPaymentDate={debtPaymentDate}
                        preparationTime={preparationTime}
                        itemsTotal={orderItemsTotal}
                        total={totalOrderAmount}
                        isLoading={isLoading}
                        requiresContactDetails={requiresContactDetails}
                        effectivePhone={effectivePhone}
                        effectiveAddress={effectiveAddress}
                        onClientSearch={setClientSearch}
                        onSelectClient={(client) => {
                            setSelectedClient(client);
                            setClientSearch("");
                        }}
                        onClearClient={() => {
                            setSelectedClient(null);
                            setIsDebt(false);
                            setShowDatePicker(false);
                        }}
                        onCreateClient={() => setShowClientModal(true)}
                        onOpenClientPicker={() => {
                            setClientPickerSearch("");
                            setShowClientPickerModal(true);
                        }}
                        onQuantityChange={(index, delta) => {
                            setCurrentOrderItems((current) => current.map((item, itemIndex) => (
                                itemIndex === index
                                    ? { ...item, qty: Math.max(1, Number(item.qty || 1) + delta) }
                                    : item
                            )));
                        }}
                        onRemoveItem={(index) => {
                            setCurrentOrderItems((current) => current.filter((_, itemIndex) => itemIndex !== index));
                        }}
                        onOpenDishPicker={() => setModalOpen(true)}
                        onOrderTypeChange={(next) => {
                            setOrderType(next);
                            if (!next) {
                                setDeliveryCost(0);
                                if (paymentType !== "transfer") {
                                    setDeliveryPhone("");
                                    setDeliveryAddress("");
                                }
                            }
                        }}
                        onDeliveryCostChange={(value) => setDeliveryCost(Math.max(0, Number(value || 0)))}
                        onDeliveryPhoneChange={setDeliveryPhone}
                        onDeliveryAddressChange={setDeliveryAddress}
                        onPaymentTypeChange={setPaymentType}
                        onDebtChange={handleDebtCheckboxChange}
                        onDebtDateChange={setDebtPaymentDate}
                        onPreparationTimeChange={(value) => setPreparationTime(Math.max(1, parseInt(value, 10) || 30))}
                        onCreateOrder={createOrder}
                        onCloseShift={closeShift}
                    />
                    <OrdersBoard
                        orders={orders}
                        cookingOrders={cookingOrders}
                        readyOrders={readyOrders}
                        issuedOrders={issuedOrders}
                        showIssuedOrders={showIssuedOrders}
                        isLoading={isLoading}
                        onReload={loadOrders}
                        onToggleIssued={() => setShowIssuedOrders((current) => !current)}
                        cardProps={{
                            markOrderReady,
                            onPrintOrderNumber: printOrderNumberTicket,
                            onPrintOrderDetails: printOrderDetailsTicket,
                            onUpdatePayment: updateOrderPayment,
                            onIssueOrder: issueOrder
                        }}
                    />
                </div>
            )}

            {showShiftPersonModal && !shiftOpen && (
                <CashierModal
                    title="Команда смены"
                    description="Выберите всех сотрудников, которые работают сегодня."
                    onClose={() => {
                        setShowShiftPersonModal(false);
                        setShiftPersonSearch("");
                    }}
                    actions={(
                        <>
                            <button className={styles.secondaryButton} type="button" onClick={() => setShowShiftPersonModal(false)}>
                                Закрыть
                            </button>
                            <button
                                className={styles.primaryButton}
                                type="button"
                                onClick={() => setShowShiftPersonModal(false)}
                                disabled={selectedShiftPersons.length === 0 || isLoading}
                            >
                                Сохранить состав
                            </button>
                        </>
                    )}
                >
                    <label className={styles.modalField} htmlFor="shift-person-search">
                        Поиск сотрудника
                        <input
                            id="shift-person-search"
                            name="shiftPersonSearch"
                            type="search"
                            autoComplete="off"
                            placeholder="Имя сотрудника…"
                            value={shiftPersonSearch}
                            onChange={(event) => setShiftPersonSearch(event.target.value)}
                        />
                    </label>
                    <div className={styles.pickerList}>
                        {filteredShiftPersons.length === 0 ? (
                            <div className={styles.compactEmpty}>Сотрудники не найдены.</div>
                        ) : filteredShiftPersons.map((person) => {
                            const selected = selectedShiftPersonIds.has(Number(person.personID));
                            return (
                                <button
                                    key={person.personID}
                                    type="button"
                                    className={selected ? styles.pickerItemActive : ""}
                                    onClick={() => toggleShiftPersonSelection(person)}
                                >
                                    <strong>{person.name}</strong>
                                    <span>{selected ? "Выбран" : "Добавить"}</span>
                                </button>
                            );
                        })}
                    </div>
                </CashierModal>
            )}

            {showClientModal && (
                <CashierModal
                    title="Новый гость"
                    description="Карточка сразу прикрепится к текущему чеку."
                    onClose={() => {
                        setShowClientModal(false);
                        setNewClient({ fullName: "", number: "" });
                    }}
                    actions={(
                        <>
                            <button className={styles.secondaryButton} type="button" onClick={() => setShowClientModal(false)}>
                                Отмена
                            </button>
                            <button
                                className={styles.primaryButton}
                                type="button"
                                onClick={createNewClient}
                                disabled={!newClient.fullName.trim() || isLoading}
                            >
                                {isLoading ? "Создаём гостя…" : "Создать гостя"}
                            </button>
                        </>
                    )}
                >
                    <div className={styles.modalForm}>
                        <label htmlFor="new-client-name">
                            Имя гостя
                            <input
                                id="new-client-name"
                                name="fullName"
                                type="text"
                                autoComplete="name"
                                value={newClient.fullName}
                                onChange={(event) => setNewClient((current) => ({ ...current, fullName: event.target.value }))}
                                placeholder="Например, Анна Петрова"
                            />
                        </label>
                        <label htmlFor="new-client-phone">
                            Телефон
                            <input
                                id="new-client-phone"
                                name="phone"
                                type="tel"
                                inputMode="tel"
                                autoComplete="tel"
                                value={newClient.number}
                                onChange={(event) => setNewClient((current) => ({ ...current, number: event.target.value }))}
                                placeholder="+7 900 000-00-00"
                            />
                        </label>
                    </div>
                </CashierModal>
            )}

            {showClientPickerModal && (
                <CashierModal
                    title="Выбрать гостя"
                    description="Поиск работает по имени и телефону."
                    onClose={() => setShowClientPickerModal(false)}
                    actions={(
                        <button className={styles.secondaryButton} type="button" onClick={() => setShowClientPickerModal(false)}>
                            Закрыть
                        </button>
                    )}
                >
                    <label className={styles.modalField} htmlFor="client-picker-search">
                        Поиск гостя
                        <input
                            id="client-picker-search"
                            name="clientPickerSearch"
                            type="search"
                            autoComplete="off"
                            value={clientPickerSearch}
                            onChange={(event) => setClientPickerSearch(event.target.value)}
                            placeholder="Имя или телефон…"
                        />
                    </label>
                    <div className={styles.pickerList}>
                        {filteredPickerClients.length === 0 ? (
                            <div className={styles.compactEmpty}>Гости не найдены.</div>
                        ) : filteredPickerClients.map((client) => (
                            <button
                                key={client.clientId}
                                type="button"
                                onClick={() => {
                                    setSelectedClient(client);
                                    setClientSearch("");
                                    setShowClientPickerModal(false);
                                }}
                            >
                                <span>
                                    <strong>{client.fullName}</strong>
                                    <small>{client.number || `Гость #${client.clientId}`}</small>
                                </span>
                                <span>Выбрать</span>
                            </button>
                        ))}
                    </div>
                </CashierModal>
            )}

            {shiftReportOpen && (
                <ShiftReportModal
                    report={shiftReport}
                    loading={shiftReportLoading}
                    onClose={() => {
                        setShiftReportOpen(false);
                        setShiftReport(null);
                    }}
                />
            )}

            {inventoryReportOpen && (
                <CashierModal
                    title="Инвентаризация перед закрытием"
                    description="Сверьте расчётный и фактический остаток, затем закройте смену."
                    onClose={() => setInventoryReportOpen(false)}
                    extraWide
                    actions={(
                        <>
                            <button className={styles.secondaryButton} type="button" onClick={() => setInventoryReportOpen(false)}>
                                Продолжить смену
                            </button>
                            <button className={styles.closeShiftButton} type="button" onClick={finalizeCloseShift} disabled={isLoading}>
                                {isLoading ? "Закрываем смену…" : "Закрыть смену"}
                            </button>
                        </>
                    )}
                >
                    <InventoryShiftReport
                        warehouses={warehouses}
                        onApplied={loadWarehouses}
                        initialShiftId={inventoryReportShiftId}
                        lockShiftSelection
                    />
                </CashierModal>
            )}

            {showDebtNotification && (todayDebts.length > 0 || overdueDebts.length > 0) && (
                <DebtNotification
                    todayDebts={todayDebts}
                    overdueDebts={overdueDebts}
                    onClose={() => setShowDebtNotification(false)}
                />
            )}
            <DishPickerModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                dishes={allDishes}
                dishSets={allDishSets}
                categories={dishCategories}
                initialItems={currentOrderItems}
                onConfirm={(items) => setCurrentOrderItems(items)}
                disabled={isLoading}
            />
        </div>
    );
}
