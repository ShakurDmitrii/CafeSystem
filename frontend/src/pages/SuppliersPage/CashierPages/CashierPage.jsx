import { useEffect, useState } from "react";
import styles from "./CashierPage.module.css";
import DishModal from "./DishModal";
import OrderCard from "./OrderCard";

const API_ORDERS = "http://localhost:8080/api/orders";
const API_SHIFTS = "http://localhost:8080/api/shifts";
const API_PERSONS = "http://localhost:8080/api/persons";
const API_DISHES = "http://localhost:8080/api/dishes";
const API_CLIENTS = "http://localhost:8080/api/clients";
const API_TODAY_DEBTS = "http://localhost:8080/api/clients/today-debts";
const API_OVERDUE_DEBTS = "http://localhost:8080/api/clients/overdue-debts";

export default function CashierPage() {
    const [orders, setOrders] = useState([]);
    const [currentOrderItems, setCurrentOrderItems] = useState([]);
    const [shiftOpen, setShiftOpen] = useState(false);
    const [currentShift, setCurrentShift] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    const [persons, setPersons] = useState([]);
    const [selectedPerson, setSelectedPerson] = useState(null);
    const [isDebt, setIsDebt] = useState(false);
    const [allDishes, setAllDishes] = useState([]);
    const [modalOpen, setModalOpen] = useState(false);
    const [orderType, setOrderType] = useState(false);
    const [allShifts, setAllShifts] = useState([]);
    const [preparationTime, setPreparationTime] = useState(30);
    const [deliveryCost, setDeliveryCost] = useState(0);
    const [paymentType, setPaymentType] = useState("cash"); // cash | transfer | unpaid
    const [deliveryPhone, setDeliveryPhone] = useState("");
    const [deliveryAddress, setDeliveryAddress] = useState("");

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

    // === ДОБАВЛЕНО: Состояния для долгов ===
    const [todayDebts, setTodayDebts] = useState([]);
    const [overdueDebts, setOverdueDebts] = useState([]);
    const [showDebtNotification, setShowDebtNotification] = useState(false);

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

        fetchShifts()
            .then(() => {
                // Если есть сохраненная смена, восстанавливаем ее
                if (savedShiftOpen && savedShiftId && savedShiftData) {
                    try {
                        const shift = JSON.parse(savedShiftData);
                        setCurrentShift(shift);
                        setShiftOpen(true);
                        setSelectedPerson(persons.find(p => p.personID === shift.personCode) || null);

                        // Загружаем заказы для восстановленной смены
                        loadOrdersForShift(shift.shiftId);
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

    // Автоматически сбрасываем чекбокс долга, если клиент был убран
    useEffect(() => {
        if (!selectedClient && isDebt) {
            setIsDebt(false);
            setShowDatePicker(false);
        }
    }, [selectedClient, isDebt]);

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

                // Сортируем заказы: сначала неготовые, потом готовые
                const sortedOrders = filtered.sort((a, b) => {
                    // Сначала по статусу (неготовые выше готовых)
                    if (a.status !== b.status) {
                        return a.status ? 1 : -1;
                    }

                    // Для неготовых сортируем по наличию задержки
                    if (!a.status && !b.status) {
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

    // Периодическая загрузка заказов при открытой смене
    useEffect(() => {
        if (shiftOpen && currentShift) {
            // Сразу загружаем заказы
            loadOrders();

            // Устанавливаем интервал обновления каждые 5 секунд
            const interval = setInterval(loadOrders, 5000);

            // Загружаем заказы при фокусировке окна
            const handleFocus = () => loadOrders();
            window.addEventListener('focus', handleFocus);

            return () => {
                clearInterval(interval);
                window.removeEventListener('focus', handleFocus);
            };
        }
    }, [shiftOpen, currentShift]);

    const createShift = () => {
        if (!selectedPerson) {
            alert("Выберите сотрудника для открытия смены!");
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
                // Shift.personcode references person.personid in DB
                personCode: selectedPerson.personID
            })
        })
            .then(r => r.json())
            .then(shift => {
                setCurrentShift(shift);
                setShiftOpen(true);
                loadOrders();
                fetchShifts();
            })
            .catch(e => console.error("Ошибка создания смены:", e))
            .finally(() => setIsLoading(false));
    };

    const openExistingShift = (shift) => {
        setIsLoading(true);
        setCurrentShift(shift);
        setShiftOpen(true);

        // Устанавливаем выбранного сотрудника
        const person = persons.find(p => p.personID === shift.personCode);
        setSelectedPerson(person || null);

        // Загружаем заказы для смены
        loadOrdersForShift(shift.shiftId)
            .then(() => {
                console.log("Смена успешно открыта");
            })
            .catch(e => console.error("Ошибка открытия смены:", e))
            .finally(() => setIsLoading(false));
    };

    const closeShift = () => {
        if (!currentShift) return;

        const shiftOrders = orders.filter(o => o.shiftId === currentShift.shiftId);

        // Orders from API usually don't contain nested items; use order.amount as source of truth.
        const income = shiftOrders.reduce(
            (sum, o) => sum + Number(o.amount || 0),
            0
        );

        const totalCost = shiftOrders.reduce(
            (sum, o) => sum + (o.items || []).reduce((s, i) => s + (i.firstCost || 0) * (i.qty || 1), 0),
            0
        );

        const profit = income - totalCost - (currentShift.expenses || 0);

        setIsLoading(true);

        fetch(`${API_SHIFTS}/${currentShift.shiftId}/update`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ...currentShift,
                endTime: new Date().toTimeString().slice(0, 8),
                income,
                profit
            })
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
                setSelectedPerson(null);
                setCurrentOrderItems([]);
                setSelectedClient(null);
                setIsDebt(false);
                setShowDatePicker(false);
                fetchShifts();
            })
            .catch(e => console.error("Ошибка закрытия смены:", e))
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
            lines.push(`Позиции блюд (шт): ${report.totals?.dishesCount ?? 0}`);
            lines.push(`Сумма по блюдам: ${Number(report.totals?.itemsAmount || 0).toFixed(2)} ₽`);
            lines.push(`Траты на доставку: ${Number(report.totals?.deliveryExpense || 0).toFixed(2)} ₽`);
            lines.push(`Общая выручка: ${Number(report.totals?.revenue || 0).toFixed(2)} ₽`);

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
            alert("Добавьте блюда в заказ!");
            return;
        }

        if (!selectedClient) {
            alert("Выберите клиента!");
            return;
        }

        if (orderType) {
            if (!deliveryPhone.trim()) {
                alert("Введите номер телефона для доставки!");
                return;
            }
            if (!deliveryAddress.trim()) {
                alert("Введите адрес доставки!");
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
                clientId: selectedClient.clientId,
                shiftId: currentShift.shiftId,
                date: new Date().toISOString().slice(0, 10),
                amount: total,
                status: false,
                time: preparationTime,
                duty: isDebt,
                type: orderType,
                deliveryPhone: orderType ? deliveryPhone.trim() : null,
                deliveryAddress: orderType ? deliveryAddress.trim() : null,
                paymentType,
                paid: paymentType !== "unpaid",
                debt_payment_date: debtPayment,
                items: currentOrderItems.map(i => ({
                    dishID: i.dishId,
                    qty: i.qty || 1
                }))
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
            order.deliveryPhone = orderType ? deliveryPhone.trim() : "";
            order.deliveryAddress = orderType ? deliveryAddress.trim() : "";

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

            // Отправляем блюда заказа
            const dishPayload = currentOrderItems.map(i => ({
                dishID: i.dishId,
                qty: i.qty || 1
            }));

            fetch(`${API_ORDERS}/orderToDish?orderId=${order.orderId}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(dishPayload)
            })
                .then(r => r.json())
                .then(res => console.log("Ответ от orderToDish:", res))
                .catch(e => console.error("Ошибка отправки на orderToDish:", e));

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

    const formatTicketMoney = (value) => {
        const num = Number(value || 0);
        const rounded = Math.round((Number.isFinite(num) ? num : 0) * 100) / 100;
        if (Math.abs(rounded) < 0.005) return "0";
        return rounded.toFixed(2).replace(/\.?0+$/, "");
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

    const buildKitchenTicketLines = (order, items) => {
        const lines = [];
        lines.push(`ЗАКАЗ НА КУХНЮ №${order.orderId}`);
        if (order.created_at || order.createdAt) {
            lines.push(`Время: ${String(order.created_at || order.createdAt)}`);
        }
        lines.push("-".repeat(32));

        items.forEach((item) => {
            const qty = Number(item.qty || 0);
            const price = Number(item.price || 0);
            const sum = qty * price;
            lines.push(String(item.dishName || "Позиция"));
            lines.push(`${qty} x ${formatTicketMoney(price)} = ${formatTicketMoney(sum)} ₽`);
        });

        lines.push("-".repeat(32));
        lines.push(`ИТОГО: ${formatTicketMoney(order.amount)} ₽`);
        lines.push(order.type ? "ТИП: ДОСТАВКА" : "ТИП: В ЗАЛЕ");
        if (order.type) {
            lines.push(`ДОСТАВКА: ${formatTicketMoney(order.deliveryCost || 0)} ₽`);
            if (order.deliveryPhone || order.clientPhone) lines.push(`ТЕЛЕФОН: ${order.deliveryPhone || order.clientPhone}`);
            if (order.deliveryAddress) lines.push(`АДРЕС: ${order.deliveryAddress}`);
        }
        const paymentRaw = (order.paymentType || "").toLowerCase();
        const paymentLabel = paymentRaw === "cash"
            ? "НАЛИЧКА"
            : paymentRaw === "transfer"
                ? "ПЕРЕВОД"
                : "НЕ ОПЛАЧЕНО";
        lines.push(`ОПЛАТА: ${paymentLabel}`);
        return lines;
    };

    const printLinesInWindow = (title, lines, style = {}) => {
        const fontSize = style.fontSize || 18;
        const fontWeight = style.fontWeight || 700;
        const align = style.align || "left";
        const lineHeight = style.lineHeight || 1.35;
        const padding = style.padding || 12;

        const html = `
            <html>
                <head>
                    <meta charset="UTF-8" />
                    <title>${escapeHtml(title)}</title>
                </head>
                <body style="font-family:'Courier New', monospace; padding:${padding}px; max-width:420px;">
                    <pre style="margin:0; white-space:pre-wrap; font-size:${fontSize}px; font-weight:${fontWeight}; line-height:${lineHeight}; text-align:${align};">${escapeHtml(lines.join("\n"))}</pre>
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

    const printOrderDetailsTicket = async (order) => {
        const dishesRes = await fetch(`http://localhost:8080/api/shifts/getDish/${order.orderId}`);
        const dishes = dishesRes.ok ? await dishesRes.json() : [];
        const items = Array.isArray(dishes) ? dishes : [];

        const kitchenLines = buildKitchenTicketLines(order, items).concat(["", ""]);
        const title = `Чек заказа №${order.orderId}`;
        printLinesInWindow(`${title} - Кухня`, kitchenLines, {
            fontSize: 20,
            fontWeight: 700,
            align: "left",
            lineHeight: 1.4,
            padding: 12
        });
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

    const formatDate = (dateString) => {
        const date = new Date(dateString);
        return date.toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    };

    // Функция для разделения заказов на группы
    const getSortedOrders = () => {
        // Разделяем заказы на группы
        const cookingOrders = orders
            .filter(o => !o.status)
            .sort((a, b) => {
                // Сначала с задержкой
                if (a.timeDelay > 0 && b.timeDelay === 0) return -1;
                if (a.timeDelay === 0 && b.timeDelay > 0) return 1;
                // Потом новые сверху
                return b.orderId - a.orderId;
            });

        const readyOrders = orders
            .filter(o => o.status)
            .sort((a, b) => {
                // Новые заказы сверху
                return b.orderId - a.orderId;
            });

        return { cookingOrders, readyOrders };
    };

    // === ДОБАВЛЕНО: Компонент уведомления о долгах ===
    const DebtNotification = () => {
        if (!showDebtNotification || (todayDebts.length === 0 && overdueDebts.length === 0)) {
            return null;
        }

        const totalAmount = [...todayDebts, ...overdueDebts]
            .reduce((sum, debt) => sum + (debt.amount || 0), 0);

        return (
            <div className={styles.notificationOverlay}>
                <div className={styles.notificationModal}>
                    <div className={styles.notificationHeader}>
                        <h2>📋 Уведомление о долгах</h2>
                        <button
                            className={styles.closeBtn}
                            onClick={() => setShowDebtNotification(false)}
                        >
                            ✕
                        </button>
                    </div>

                    <div className={styles.notificationContent}>
                        {/* ПРОСРОЧЕННЫЕ ДОЛГИ */}
                        {overdueDebts.length > 0 && (
                            <div className={styles.debtSection}>
                                <h3 className={styles.overdueTitle}>
                                    ⚠️ ПРОСРОЧЕННЫЕ ДОЛГИ ({overdueDebts.length})
                                </h3>
                                {overdueDebts.map(debt => (
                                    <div key={debt.orderId} className={styles.debtItem}>
                                        <div className={styles.debtInfo}>
                                            <strong>Заказ #{debt.orderId}</strong>
                                            <div>Сумма: {debt.amount} ₽</div>
                                            {debt.debt_payment_date && (
                                                <div className={styles.overdueBadge}>
                                                    Просрочено с: {formatDate(debt.debt_payment_date)}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* ДОЛГИ НА СЕГОДНЯ */}
                        {todayDebts.length > 0 && (
                            <div className={styles.debtSection}>
                                <h3 className={styles.todayTitle}>
                                    📅 ДОЛГИ НА СЕГОДНЯ ({todayDebts.length})
                                </h3>
                                {todayDebts.map(debt => (
                                    <div key={debt.orderId} className={styles.debtItem}>
                                        <div className={styles.debtInfo}>
                                            <strong>Заказ #{debt.orderId}</strong>
                                            <div>Сумма: {debt.amount} ₽</div>
                                            {debt.debt_payment_date && (
                                                <div className={styles.todayBadge}>
                                                    Дата погашения: {formatDate(debt.debt_payment_date)}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className={styles.notificationFooter}>
                        <button
                            className={`${styles.btn} ${styles.primary}`}
                            onClick={() => setShowDebtNotification(false)}
                        >
                            Понятно
                        </button>
                        <small>
                            Всего долгов: {todayDebts.length + overdueDebts.length} на сумму {
                            totalAmount.toFixed(2)
                        } ₽
                        </small>
                    </div>
                </div>
            </div>
        );
    };

    // Показываем индикатор загрузки
    if (isLoading) {
        return (
            <div className={styles.page}>
                <header className={styles.header}>
                    <div className={styles.brand}>🍣 СушиСакура</div>
                </header>
                <div className={styles.loadingContainer}>
                    <div className={styles.spinner}></div>
                    <div>Загрузка данных...</div>
                </div>
            </div>
        );
    }

    const { cookingOrders, readyOrders } = getSortedOrders();
    const sortedShifts = [...allShifts].sort((a, b) => (b.shiftId || 0) - (a.shiftId || 0));

    return (
        <div className={styles.page}>
            <header className={styles.header}>
                <div className={styles.brand}>🍣 СушиСакура</div>
                <span className={shiftOpen ? styles.shiftOpen : styles.shiftClosed}>
                    {shiftOpen ? `Смена открыта | ID: ${currentShift?.shiftId || ''}` : "Смена закрыта"}
                </span>

                {shiftOpen && (
                    <div className={styles.headerActions}>
                        {/* === ДОБАВЛЕНО: Индикатор долгов в хедере === */}
                        {(todayDebts.length > 0 || overdueDebts.length > 0) && (
                            <button
                                className={styles.debtAlertBtn}
                                onClick={() => setShowDebtNotification(true)}
                                title="Показать уведомления о долгах"
                            >
                                ⚠️ Долги: {todayDebts.length + overdueDebts.length}
                            </button>
                        )}
                        <button
                            className={styles.zReportBtn}
                            onClick={printZReport}
                            disabled={!currentShift || isLoading}
                            title="Печать Z-отчета по смене"
                        >
                            🧾 Z-отчет
                        </button>
                    </div>
                )}
            </header>

            {!shiftOpen ? (
                <div className={styles.closedMessage}>
                    <div className={styles.selectEmployeeWrapper}>
                        <h3>Открыть новую смену</h3>
                        <select
                            className={styles.selectEmployeeBtn}
                            value={selectedPerson?.personID || ""}
                            onChange={e => {
                                const p = persons.find(x => x.personID === Number(e.target.value));
                                setSelectedPerson(p || null);
                            }}
                            disabled={isLoading}
                        >
                            <option value="">Выберите сотрудника</option>
                            {persons.map(p => (
                                <option key={p.personID} value={p.personID}>{p.name}</option>
                            ))}
                        </select>

                        {selectedPerson && (
                            <div className={styles.selectedPerson}>
                                Выбран сотрудник: <b>{selectedPerson.name}</b>
                            </div>
                        )}

                        <button
                            className={`${styles.btn} ${styles.primary}`}
                            disabled={!selectedPerson || isLoading}
                            onClick={createShift}
                        >
                            {isLoading ? "Открывается..." : "Открыть новую смену"}
                        </button>
                    </div>

                    <h2>Смены</h2>
                    {allShifts.length === 0 ? (
                        <div className={styles.empty}>Нет доступных смен</div>
                    ) : (
                        sortedShifts.map(s => (
                            <div key={s.shiftId} className={styles.orderCard}>
                                <div>
                                    <b>Смена #{s.shiftId}</b>
                                    <div>Дата: {s.data || 'Не указана'}</div>
                                    <div>Время: {s.startTime || 'Не указано'}</div>
                                    <div>Статус: {!s.endTime ? "🟢 Открыта" : "🔴 Закрыта"}</div>
                                    <div>Сумма заказов: {Number(s.income || 0).toFixed(2)} ₽</div>
                                    {s.personName && <div>Кассир: {s.personName}</div>}
                                </div>
                                {!s.endTime && (
                                    <button
                                        className={`${styles.btn} ${styles.primary}`}
                                        onClick={() => openExistingShift(s)}
                                    >
                                        Войти
                                    </button>
                                )}
                                {s.endTime && (
                                    <button
                                        className={`${styles.btn} ${styles.secondary}`}
                                        onClick={() => openShiftReport(s.shiftId)}
                                    >
                                        Отчет
                                    </button>
                                )}
                            </div>
                        ))
                    )}
                </div>
            ) : (
                <div className={styles.body}>
                    <section className={styles.orderPanel}>
                        <h2>Новый заказ</h2>

                        {/* ВЫБОР КЛИЕНТА */}
                        <div className={styles.clientSection}>
                            <h3>👤 Клиент</h3>

                            {selectedClient ? (
                                <div className={styles.selectedClientCard}>
                                    <div>
                                        <strong>{selectedClient.fullName}</strong>
                                        {selectedClient.number && (
                                            <div>📞 {selectedClient.number}</div>
                                        )}
                                        <div>ID: {selectedClient.clientId}</div>
                                    </div>
                                    <button
                                        className={`${styles.btn} ${styles.danger}`}
                                        onClick={() => {
                                            setSelectedClient(null);
                                            setIsDebt(false);
                                            setShowDatePicker(false);
                                        }}
                                    >
                                        ✕
                                    </button>
                                </div>
                            ) : (
                                <div className={styles.clientSearch}>
                                    <input
                                        type="text"
                                        placeholder="Поиск клиента по имени или телефону..."
                                        value={clientSearch}
                                        onChange={(e) => setClientSearch(e.target.value)}
                                        disabled={isLoading}
                                    />

                                    {clientSearch.trim() && (
                                        <div className={styles.clientResults}>
                                            {filteredClients.length === 0 ? (
                                                <div className={styles.noResults}>
                                                    Клиент не найден
                                                    <button
                                                        className={styles.createClientBtn}
                                                        onClick={() => setShowClientModal(true)}
                                                        disabled={isLoading}
                                                    >
                                                        ➕ Создать нового
                                                    </button>
                                                </div>
                                            ) : (
                                                filteredClients.map(client => (
                                                    <div
                                                        key={client.clientId}
                                                        className={styles.clientOption}
                                                        onClick={() => {
                                                            setSelectedClient(client);
                                                            setClientSearch("");
                                                        }}
                                                    >
                                                        <div>
                                                            <strong>{client.fullName}</strong>
                                                            {client.number && <div>📞 {client.number}</div>}
                                                        </div>
                                                        <span>→</span>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    )}

                                    <button
                                        className={`${styles.btn} ${styles.secondary}`}
                                        onClick={() => setShowClientModal(true)}
                                        disabled={isLoading}
                                    >
                                        ➕ Новый клиент
                                    </button>

                                    <button
                                        className={`${styles.btn} ${styles.secondary}`}
                                        onClick={() => {
                                            setClientPickerSearch("");
                                            setShowClientPickerModal(true);
                                        }}
                                        disabled={isLoading}
                                    >
                                        📋 Выбрать из списка
                                    </button>
                                </div>
                            )}
                        </div>

                        {/* СПИСОК БЛЮД */}
                        {currentOrderItems.length === 0 ? (
                            <div className={styles.emptyItems}>
                                Добавьте блюда в заказ
                            </div>
                        ) : (
                            currentOrderItems.map((item, idx) => (
                                <div key={idx} className={styles.item}>
                                    <span>{item.dishName}</span>

                                    <div className={styles.qtyControls}>
                                        <button
                                            onClick={() =>
                                                setCurrentOrderItems(prev =>
                                                    prev.map((it, i) => i === idx ? { ...it, qty: Math.max(1, it.qty - 1) } : it)
                                                )
                                            }
                                            disabled={isLoading}
                                        >−</button>
                                        <span>{item.qty}</span>
                                        <button
                                            onClick={() =>
                                                setCurrentOrderItems(prev =>
                                                    prev.map((it, i) => i === idx ? { ...it, qty: it.qty + 1 } : it)
                                                )
                                            }
                                            disabled={isLoading}
                                        >+</button>
                                    </div>

                                    <span>{(item.qty || 1) * item.price} ₽</span>

                                    <button
                                        className={`${styles.btn} ${styles.danger}`}
                                        onClick={() => setCurrentOrderItems(currentOrderItems.filter((_, i) => i !== idx))}
                                        disabled={isLoading}
                                    >
                                        ❌
                                    </button>
                                </div>
                            ))
                        )}

                        <button
                            className={`${styles.btn} ${styles.secondary}`}
                            onClick={() => setModalOpen(true)}
                            disabled={isLoading}
                        >
                            Добавить позицию
                        </button>

                        {/* ТИП ЗАКАЗА */}
                        <div className={styles.switchWrapper}>
                            <label className={styles.switch}>
                                <input
                                    type="checkbox"
                                    checked={orderType}
                                    onChange={() => {
                                        const next = !orderType;
                                        setOrderType(next);
                                        if (!next) {
                                            setDeliveryCost(0);
                                            setDeliveryPhone("");
                                            setDeliveryAddress("");
                                        }
                                    }}
                                    disabled={isLoading}
                                />
                                <span className={styles.slider}></span>
                            </label>
                            <span>{orderType ? "Доставка" : "По месту"}</span>
                        </div>

                        {orderType && (
                            <div className={styles.timeInput}>
                                <label>Стоимость доставки (₽):</label>
                                <input
                                    type="number"
                                    min="0"
                                    step="10"
                                    value={deliveryCost}
                                    onChange={(e) => setDeliveryCost(Math.max(0, Number(e.target.value || 0)))}
                                    disabled={isLoading}
                                />
                            </div>
                        )}

                        {orderType && (
                            <div className={styles.timeInput}>
                                <label>Телефон доставки:</label>
                                <input
                                    type="text"
                                    placeholder="+7..."
                                    value={deliveryPhone}
                                    onChange={(e) => setDeliveryPhone(e.target.value)}
                                    disabled={isLoading}
                                />
                            </div>
                        )}

                        {orderType && (
                            <div className={styles.timeInput}>
                                <label>Адрес доставки:</label>
                                <input
                                    type="text"
                                    placeholder="Улица, дом, квартира"
                                    value={deliveryAddress}
                                    onChange={(e) => setDeliveryAddress(e.target.value)}
                                    disabled={isLoading}
                                />
                            </div>
                        )}

                        <div className={styles.timeInput}>
                            <label>Тип оплаты:</label>
                            <div className={styles.paymentOptions}>
                                <label className={`${styles.paymentOption} ${paymentType === "cash" ? styles.paymentOptionActive : ""}`}>
                                    <input
                                        type="checkbox"
                                        checked={paymentType === "cash"}
                                        onChange={() => setPaymentType("cash")}
                                        disabled={isLoading}
                                    />
                                    Наличка
                                </label>
                                <label className={`${styles.paymentOption} ${paymentType === "transfer" ? styles.paymentOptionActive : ""}`}>
                                    <input
                                        type="checkbox"
                                        checked={paymentType === "transfer"}
                                        onChange={() => setPaymentType("transfer")}
                                        disabled={isLoading}
                                    />
                                    Перевод
                                </label>
                                <label className={`${styles.paymentOption} ${paymentType === "unpaid" ? styles.paymentOptionWarning : ""}`}>
                                    <input
                                        type="checkbox"
                                        checked={paymentType === "unpaid"}
                                        onChange={() => setPaymentType("unpaid")}
                                        disabled={isLoading}
                                    />
                                    Не оплачен
                                </label>
                            </div>
                        </div>

                        {/* ДОЛГ С ВЫБОРОМ ДАТЫ ПОГАШЕНИЯ */}
                        <div className={styles.debtSection}>
                            <div className={styles.debtCheckbox}>
                                <label>
                                    <input
                                        type="checkbox"
                                        checked={isDebt}
                                        onChange={handleDebtCheckboxChange}
                                        disabled={!selectedClient || isLoading}
                                    />
                                    <span style={{ color: !selectedClient ? '#999' : 'inherit' }}>
                                        {isDebt ? '✅ Долг' : 'Долг'} {!selectedClient && '(выберите клиента)'}
                                    </span>
                                </label>
                            </div>

                            {showDatePicker && isDebt && selectedClient && (
                                <div className={styles.debtDatePicker}>
                                    <label htmlFor="debtPaymentDate">
                                        📅 Дата погашения долга:
                                    </label>
                                    <input
                                        type="date"
                                        id="debtPaymentDate"
                                        value={debtPaymentDate}
                                        onChange={(e) => setDebtPaymentDate(e.target.value)}
                                        min={new Date().toISOString().split('T')[0]}
                                        className={styles.dateInput}
                                        disabled={isLoading}
                                    />
                                    <div className={styles.dateInfo}>
                                        <small>
                                            Обещанная дата: <strong>{formatDate(debtPaymentDate)}</strong>
                                        </small>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* ВРЕМЯ ПРИГОТОВЛЕНИЯ */}
                        <div className={styles.timeInput}>
                            <label>Время приготовления (минут):</label>
                            <input
                                type="number"
                                min="1"
                                max="600"
                                value={preparationTime}
                                onChange={(e) => setPreparationTime(Math.max(1, parseInt(e.target.value) || 30))}
                                disabled={isLoading}
                            />
                        </div>

                        {/* ИТОГО */}
                        <div className={styles.total}>
                            <span>ИТОГО</span>
                            <span>{totalOrderAmount} ₽</span>
                        </div>
                        {orderType && (
                            <div style={{ marginTop: "-4px", marginBottom: "10px", color: "#4b5563", fontSize: "13px" }}>
                                Блюда: {orderItemsTotal} ₽ + Доставка: {Number(deliveryCost || 0)} ₽
                            </div>
                        )}

                        {/* КНОПКИ */}
                        <button
                            className={`${styles.btn} ${styles.primary}`}
                            onClick={createOrder}
                            disabled={
                                currentOrderItems.length === 0 ||
                                !selectedClient ||
                                isLoading ||
                                (orderType && (!deliveryPhone.trim() || !deliveryAddress.trim()))
                            }
                        >
                            {!selectedClient ? "Выберите клиента" : "Создать заказ"}
                        </button>

                        <button
                            className={`${styles.btn} ${styles.danger}`}
                            onClick={closeShift}
                            disabled={isLoading}
                        >
                            {isLoading ? "Закрывается..." : "Закрыть смену"}
                        </button>
                    </section>

                    <section className={styles.ordersPanel}>
                        <div className={styles.ordersHeader}>
                            <h2>Заказы ({orders.length})</h2>
                            <div className={styles.orderStats}>
                                <span className={styles.cookingCount}>
                                    👨‍🍳 {cookingOrders.length}
                                </span>
                                <span className={styles.readyCount}>
                                    ✅ {readyOrders.length}
                                </span>
                            </div>
                            <button
                                className={`${styles.btn} ${styles.secondary}`}
                                onClick={loadOrders}
                                disabled={isLoading}
                            >
                                Обновить
                            </button>
                        </div>

                        {orders.length === 0 ? (
                            <div className={styles.empty}>
                                Нет заказов в текущей смене
                            </div>
                        ) : (
                            // Получаем отсортированные группы заказов
                            <>
                                {/* Группа неготовых заказов */}
                                {cookingOrders.length > 0 && (
                                    <div className={styles.orderGroup}>
                                        <div className={styles.groupHeader}>
                                            <h3>👨‍🍳 Готовятся ({cookingOrders.length})</h3>
                                        </div>
                                        {cookingOrders.map(o => (
                                            <OrderCard
                                                key={o.orderId}
                                                order={o}
                                                markOrderReady={markOrderReady}
                                                onPrintOrderNumber={printOrderNumberTicket}
                                                onPrintOrderDetails={printOrderDetailsTicket}
                                                onUpdatePayment={updateOrderPayment}
                                            />
                                        ))}
                                    </div>
                                )}

                                {/* Группа готовых заказов */}
                                {readyOrders.length > 0 && (
                                    <div className={styles.orderGroup}>
                                        <div className={styles.groupHeader}>
                                            <h3>✅ Готовы ({readyOrders.length})</h3>
                                        </div>
                                        {readyOrders.map(o => (
                                            <OrderCard
                                                key={o.orderId}
                                                order={o}
                                                markOrderReady={markOrderReady}
                                                onPrintOrderNumber={printOrderNumberTicket}
                                                onPrintOrderDetails={printOrderDetailsTicket}
                                                onUpdatePayment={updateOrderPayment}
                                            />
                                        ))}
                                    </div>
                                )}
                            </>
                        )}
                    </section>
                </div>
            )}

            {/* МОДАЛЬНОЕ ОКНО ДЛЯ СОЗДАНИЯ КЛИЕНТА */}
            {showClientModal && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <h3>
                            {isDebt ? "➕ Создать клиента для долга" : "➕ Создать нового клиента"}
                        </h3>
                        <div className={styles.modalContent}>
                            <input
                                type="text"
                                placeholder="ФИО клиента *"
                                value={newClient.fullName}
                                onChange={(e) => setNewClient({...newClient, fullName: e.target.value})}
                                className={styles.modalInput}
                                disabled={isLoading}
                            />
                            <input
                                type="text"
                                placeholder="Телефон (необязательно)"
                                value={newClient.number}
                                onChange={(e) => setNewClient({...newClient, number: e.target.value})}
                                className={styles.modalInput}
                                disabled={isLoading}
                            />
                        </div>
                        <div className={styles.modalActions}>
                            <button
                                className={`${styles.btn} ${styles.secondary}`}
                                onClick={() => {
                                    setShowClientModal(false);
                                    setNewClient({ fullName: "", number: "" });
                                }}
                                disabled={isLoading}
                            >
                                Отмена
                            </button>
                            <button
                                className={`${styles.btn} ${styles.primary}`}
                                onClick={createNewClient}
                                disabled={!newClient.fullName.trim() || isLoading}
                            >
                                {isLoading ? "Создание..." : (isDebt ? "Создать и отметить как долг" : "Создать")}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* МОДАЛЬНОЕ ОКНО ВЫБОРА КЛИЕНТА ИЗ ПОЛНОГО СПИСКА */}
            {showClientPickerModal && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <h3>📋 Выбор клиента</h3>

                        <div className={styles.modalContent}>
                            <input
                                type="text"
                                placeholder="Поиск по имени или телефону..."
                                value={clientPickerSearch}
                                onChange={(e) => setClientPickerSearch(e.target.value)}
                                className={styles.modalInput}
                                disabled={isLoading}
                            />

                            <div className={styles.clientPickerList}>
                                {filteredPickerClients.length === 0 ? (
                                    <div className={styles.noResults}>Клиенты не найдены</div>
                                ) : (
                                    filteredPickerClients.map(client => (
                                        <button
                                            key={client.clientId}
                                            type="button"
                                            className={styles.clientPickerItem}
                                            onClick={() => {
                                                setSelectedClient(client);
                                                setClientSearch("");
                                                setShowClientPickerModal(false);
                                            }}
                                            disabled={isLoading}
                                        >
                                            <span>
                                                <strong>{client.fullName}</strong>
                                                {client.number ? ` · ${client.number}` : ""}
                                            </span>
                                            <span>Выбрать</span>
                                        </button>
                                    ))
                                )}
                            </div>
                        </div>

                        <div className={styles.modalActions}>
                            <button
                                className={`${styles.btn} ${styles.secondary}`}
                                onClick={() => setShowClientPickerModal(false)}
                                disabled={isLoading}
                            >
                                Закрыть
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {shiftReportOpen && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal} style={{ maxWidth: "900px" }}>
                        <h3>Отчет по смене</h3>
                        {shiftReportLoading && <div style={{ marginTop: "10px" }}>Загрузка отчета...</div>}
                        {!shiftReportLoading && shiftReport?.error && (
                            <div style={{ marginTop: "10px", color: "#991b1b" }}>{shiftReport.error}</div>
                        )}
                        {!shiftReportLoading && shiftReport && !shiftReport.error && (
                            <div style={{ marginTop: "10px", display: "grid", gap: "10px" }}>
                                <div style={{ background: "#f8fafc", border: "1px solid #e5e7eb", borderRadius: "8px", padding: "10px" }}>
                                    <div><b>Смена №{shiftReport.shiftId}</b></div>
                                    <div>Дата: {shiftReport.date || "-"}</div>
                                    <div>Открытие: {shiftReport.startTime || "-"}</div>
                                    <div>Закрытие: {shiftReport.endTime || "-"}</div>
                                    <div>Работники: {(shiftReport.workers || []).join(", ") || "Не указаны"}</div>
                                </div>

                                <div style={{ background: "#eff6ff", border: "1px solid #bfdbfe", borderRadius: "8px", padding: "10px" }}>
                                    <div><b>Итоги</b></div>
                                    <div>Заказов: {shiftReport.totals?.ordersCount ?? 0}</div>
                                    <div>Сумма заказов: {Number(shiftReport.totals?.revenue || 0).toFixed(2)} ₽</div>
                                    <div style={{ color: "#92400e", fontWeight: 700 }}>
                                        Сумма доставок: {Number(shiftReport.totals?.deliveryExpense || 0).toFixed(2)} ₽
                                    </div>
                                    <div style={{ color: "#b91c1c", fontWeight: 700 }}>
                                        Заказов с задержкой: {shiftReport.totals?.delayedOrdersCount ?? 0}
                                    </div>
                                </div>

                                <div style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: "8px", padding: "10px" }}>
                                    <div><b>Популярные позиции за смену</b></div>
                                    {(shiftReport.topPositions || []).length === 0 ? (
                                        <div style={{ color: "#6b7280" }}>Нет данных</div>
                                    ) : (
                                        (shiftReport.topPositions || []).slice(0, 10).map((p, idx) => (
                                            <div key={`${p.dishName}-${idx}`}>
                                                {idx + 1}. {p.dishName} — {p.qty} шт, {Number(p.amount || 0).toFixed(2)} ₽
                                            </div>
                                        ))
                                    )}
                                </div>

                                <div style={{ background: "#f8fafc", border: "1px solid #e5e7eb", borderRadius: "8px", padding: "10px", maxHeight: "300px", overflowY: "auto" }}>
                                    <div><b>Все заказы</b></div>
                                    {(shiftReport.orders || []).length === 0 ? (
                                        <div style={{ color: "#6b7280" }}>Нет заказов</div>
                                    ) : (
                                        (shiftReport.orders || []).map((o) => (
                                            <div
                                                key={o.orderId}
                                                style={{
                                                    marginTop: "10px",
                                                    padding: "8px",
                                                    borderTop: "1px dashed #d1d5db",
                                                    borderRadius: "8px",
                                                    background: Number(o.delayMinutes || 0) > 0 ? "#fffbeb" : "#ffffff",
                                                    border: Number(o.delayMinutes || 0) > 0 ? "1px solid #fcd34d" : "1px solid #e5e7eb"
                                                }}
                                            >
                                                <div>
                                                    <b>Заказ #{o.orderId}</b> · {o.isDelivery ? "Доставка" : "По месту"} · Сумма: {Number(o.orderAmount || 0).toFixed(2)} ₽
                                                </div>
                                                <div>Клиент: {o.clientName || "—"} {o.clientPhone ? `(${o.clientPhone})` : ""}</div>
                                                <div style={{ color: Number(o.delayMinutes || 0) > 0 ? "#b45309" : "#374151", fontWeight: Number(o.delayMinutes || 0) > 0 ? 700 : 500 }}>
                                                    Задержка: {Number(o.delayMinutes || 0).toFixed(0)} мин
                                                </div>
                                                {(o.items || []).map((it, idx) => (
                                                    <div key={`${o.orderId}-${idx}`} style={{ marginLeft: "12px", color: "#374151" }}>
                                                        • {it.dishName} × {it.qty} = {Number(it.sum || 0).toFixed(2)} ₽
                                                    </div>
                                                ))}
                                            </div>
                                        ))
                                    )}
                                </div>
                            </div>
                        )}

                        <div className={styles.modalActions}>
                            <button
                                className={`${styles.btn} ${styles.secondary}`}
                                onClick={() => {
                                    setShiftReportOpen(false);
                                    setShiftReport(null);
                                }}
                            >
                                Закрыть
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* === ДОБАВЛЕНО: Модальное окно уведомления о долгах === */}
            <DebtNotification />
            <DishModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                dishes={allDishes}
                onAddDish={d => setCurrentOrderItems(prev => [...prev, { ...d, qty: 1 }])}
                disabled={isLoading}
            />
        </div>
    );
}
