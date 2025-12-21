package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.*;
import jooqdata.tables.Client;
import jooqdata.tables.Clientdish;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.records.ClientRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Long.sum;
import static jooqdata.tables.Client.CLIENT;
import static jooqdata.tables.Order.ORDER;
import static org.jooq.impl.DSL.condition;

@Service
public class ClientService {

    public static DSLContext dsl;

    public ClientService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ClientDTO> getAllClients() {
        return dsl.selectFrom(CLIENT)
                .fetch()
                .stream()
                .map(record -> {
                    ClientDTO dto = new ClientDTO();
                    dto.clientId = record.getClientid();
                    dto.fullName = record.getFullname();
                    dto.number = record.getNumber();

                    return dto;
                }).toList();
    }

    public List<ClientWithDutyDTO> getClientsWithDutyOrders(boolean duty) {
        try {
            // Получаем всех клиентов с долгами
            List<ClientDTO> clientsWithDuty = dsl.selectDistinct(
                            CLIENT.CLIENTID,
                            CLIENT.FULLNAME,
                            CLIENT.NUMBER
                    )
                    .from(CLIENT)
                    .join(ORDER).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                    .where(ORDER.DUTY.eq(duty))
                    .fetch()
                    .stream()
                    .map(record -> {
                        ClientDTO dto = new ClientDTO();
                        dto.setClientId(record.get(CLIENT.CLIENTID));
                        dto.setFullName(record.get(CLIENT.FULLNAME));
                        dto.setNumber(record.get(CLIENT.NUMBER));
                        return dto;
                    })
                    .toList();

            List<ClientWithDutyDTO> result = new ArrayList<>();

            for (ClientDTO client : clientsWithDuty) {
                List<OrderDTO> dutyOrders = dsl.selectFrom(ORDER)
                        .where(ORDER.CLIENTID.eq(client.getClientId())
                                .and(ORDER.DUTY.eq(duty)))
                        .fetch()
                        .stream()
                        .map(record -> {
                            OrderDTO order = new OrderDTO();
                            order.setOrderId(record.get(ORDER.ORDERID));
                            order.setDate(record.get(ORDER.DATE));
                            order.setAmount(record.get(ORDER.AMOUNT));
                            order.setDuty(record.get(ORDER.DUTY));
                            order.setTimeDelay(record.get(ORDER.TIMEDELAY));
                            return order;
                        })
                        .toList();

                result.add(new ClientWithDutyDTO(client, dutyOrders));
            }

            return result;
        } catch (Exception e) {
            System.err.println("Error in getClientsWithDutyOrders: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to get clients with duty orders", e);
        }
    }


    public ClientDTO createClient(ClientDTO dto) {
        try {
            System.out.println("Creating client: " + dto.getFullName());

            // Вставляем новую запись и получаем сгенерированный ID
            Integer clientId = dsl.insertInto(CLIENT)
                    .set(CLIENT.FULLNAME, dto.getFullName())
                    .set(CLIENT.NUMBER, dto.getNumber() != null ? dto.getNumber() : "")
                    .returningResult(CLIENT.CLIENTID)
                    .fetchOne()
                    .get(CLIENT.CLIENTID);

            System.out.println("Created client with ID: " + clientId);

            // Заполняем DTO полученным ID
            dto.setClientId(clientId);
            return dto;

        } catch (Exception e) {
            System.err.println("Error creating client: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create client: " + e.getMessage(), e);
        }
    }

    public List<ClientDishDTO> getDishesByClientId(int clientId) {
        return dsl.select()
                .from(Clientdish.CLIENTDISH)
                .join(Dish.DISH)
                .on(Clientdish.CLIENTDISH.DISHID.eq(Dish.DISH.DISHID))
                .where(Clientdish.CLIENTDISH.CLIENTID.eq(clientId))
                .fetch()
                .stream()
                .map(record -> {
                    ClientDishDTO dto = new ClientDishDTO();
                    dto.clientId = record.get(Clientdish.CLIENTDISH.CLIENTID);
                    dto.dishId = record.get(Dish.DISH.DISHID);
                    dto.dishName = record.get(Dish.DISH.DISHNAME);

                    return dto;
                })
                .toList();
    }

    public Map<String, Object> deleteAllDutyByClientId(int clientId) {
        try {
            System.out.println("Удаление ВСЕХ долгов для клиента ID: " + clientId);

            // 1. Проверяем существование клиента
            boolean clientExists = dsl.fetchExists(
                    dsl.selectOne()
                            .from(CLIENT)
                            .where(CLIENT.CLIENTID.eq(clientId))
            );

            if (!clientExists) {
                throw new RuntimeException("Клиент с ID " + clientId + " не найден");
            }

            // 2. Получаем все заказы с долгами перед обновлением
            List<OrderDTO> dutyOrdersBefore = dsl.selectFrom(ORDER)
                    .where(ORDER.CLIENTID.eq(clientId)
                            .and(ORDER.DUTY.eq(true)))
                    .fetch()
                    .map(record -> {
                        OrderDTO order = new OrderDTO();
                        order.setOrderId(record.get(ORDER.ORDERID));
                        order.setAmount(record.get(ORDER.AMOUNT));
                        order.setDate(record.get(ORDER.DATE));
                        return order;
                    });

            if (dutyOrdersBefore.isEmpty()) {
                throw new RuntimeException("У клиента нет заказов с долгами");
            }

            // 3. Рассчитываем общую сумму долгов
            Double totalDutyAmount = dutyOrdersBefore.stream()
                    .mapToDouble(o -> o.getAmount() != null ? o.getAmount() : 0)
                    .sum();

            // 4. Обновляем ВСЕ заказы с долгами
            int updatedCount = dsl.update(ORDER)
                    .set(ORDER.DUTY, false)
                    .where(ORDER.CLIENTID.eq(clientId)
                            .and(ORDER.DUTY.eq(true)))
                    .execute();

            System.out.println("Обновлено заказов: " + updatedCount + ", сумма долгов: " + totalDutyAmount);

            // 5. Возвращаем результат
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("clientId", clientId);
            result.put("updatedOrdersCount", updatedCount);
            result.put("totalDutyAmount", totalDutyAmount);
            result.put("message", "Удалено " + updatedCount + " заказов с долгами на сумму " + totalDutyAmount + " руб.");

            return result;

        } catch (Exception e) {
            System.err.println("Ошибка при удалении долгов: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("clientId", clientId);

            return errorResult;
        }
    }

    public Map<String, Object> deleteDutyByOrderId(int orderId) {
        try {
            System.out.println("Удаление долга для заказа ID: " + orderId);

            // 1. Получаем заказ
            OrderDTO order = dsl.selectFrom(ORDER)
                    .where(ORDER.ORDERID.eq(orderId))
                    .fetchOptional()
                    .map(record -> {
                        OrderDTO dto = new OrderDTO();
                        dto.setOrderId(record.get(ORDER.ORDERID));
                        dto.setClientId(record.get(ORDER.CLIENTID));
                        dto.setAmount(record.get(ORDER.AMOUNT));
                        dto.setDate(record.get(ORDER.DATE));
                        dto.setDuty(record.get(ORDER.DUTY));
                        return dto;
                    })
                    .orElseThrow(() -> new RuntimeException("Заказ не найден"));

            // 2. Проверяем, что это долг
            if (!Boolean.TRUE.equals(order.getDuty())) {
                throw new RuntimeException("У этого заказа нет долга");
            }

            // 3. Обновляем
            dsl.update(ORDER)
                    .set(ORDER.DUTY, false)
                    .where(ORDER.ORDERID.eq(orderId))
                    .execute();

            // 4. Возвращаем результат
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderId", orderId);
            result.put("clientId", order.getClientId());
            result.put("amount", order.getAmount());
            result.put("message", "Заказ #" + orderId + " отмечен как оплаченный");

            return result;

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("orderId", orderId);

            return errorResult;
        }
    }

    // Работа с долгами


    public OrderDTO addDutyData(int orderId, LocalDate paymentDate) {
        try {
            System.out.println("Добавляю дату погашения долга " + paymentDate + " для заказа " + orderId);

            // 1. Обновляем заказ в БД
            int updated = dsl.update(Order.ORDER)
                    .set(Order.ORDER.DEBT_PAYMENT_DATE, paymentDate)
                    .where(Order.ORDER.ORDERID.eq(orderId))
                    .execute();

            if (updated == 0) {
                throw new RuntimeException("Заказ не найден или не обновлен");
            }

            // 2. Возвращаем обновленный заказ
            return dsl.selectFrom(Order.ORDER)
                    .where(Order.ORDER.ORDERID.eq(orderId))
                    .fetchOne(record -> {
                        OrderDTO dto = new OrderDTO();
                        dto.setOrderId(record.get(Order.ORDER.ORDERID));
                        dto.setDebt_payment_date(record.get(Order.ORDER.DEBT_PAYMENT_DATE));
                        // остальные поля если нужно
                        return dto;
                    });

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            throw new RuntimeException("Не удалось добавить дату погашения долга: " + e.getMessage(), e);
        }
    }



    public List<OrderDTO> getDebtsDueToday() {
        LocalDate today = LocalDate.now();
        System.out.println("=== getDebtsDueToday() ===");
        System.out.println("Сегодня: " + today);

        try {
            // Сначала выведем информацию о структуре таблицы
            System.out.println("Проверяем структуру поля DEBT_PAYMENT_DATE...");
            System.out.println("ORDER.DEBT_PAYMENT_DATE тип в jOOQ: " + ORDER.DEBT_PAYMENT_DATE.getType());
            System.out.println("ORDER.DEBT_PAYMENT_DATE тип данных SQL: " + ORDER.DEBT_PAYMENT_DATE.getDataType());

            // Выведем несколько примеров из базы
            System.out.println("\nПримеры значений DEBT_PAYMENT_DATE из базы:");
            dsl.select(ORDER.ORDERID, ORDER.DEBT_PAYMENT_DATE)
                    .from(ORDER)
                    .where(ORDER.DEBT_PAYMENT_DATE.isNotNull())
                    .limit(5)
                    .fetch()
                    .forEach(record -> {
                        Object debtDate = record.get(ORDER.DEBT_PAYMENT_DATE);
                        System.out.println("orderId=" + record.get(ORDER.ORDERID) +
                                ", debtDate=" + debtDate +
                                ", тип=" + (debtDate != null ? debtDate.getClass().getName() : "null") +
                                ", is LocalDate=" + (debtDate instanceof LocalDate) +
                                ", is LocalDateTime=" + (debtDate instanceof LocalDateTime) +
                                ", is java.sql.Date=" + (debtDate instanceof java.sql.Date) +
                                ", is java.sql.Timestamp=" + (debtDate instanceof java.sql.Timestamp));
                    });

            // Пробуем оба варианта - и DATE и TIMESTAMP сравнение
            List<OrderDTO> result = dsl.select()
                    .from(ORDER)
                    .join(CLIENT).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                    .where(ORDER.DUTY.eq(true)
                            .and(ORDER.DEBT_PAYMENT_DATE.eq(today)) // пробуем как LocalDate
                            .and(ORDER.STATUS.eq(true)))
                    .fetch(record -> {
                        OrderDTO dto = new OrderDTO();
                        dto.setOrderId(record.get(ORDER.ORDERID));
                        dto.setAmount(record.get(ORDER.AMOUNT));
                        dto.setDate(record.get(ORDER.DATE));

                        // Пробуем получить как LocalDate, если не получится - как LocalDateTime
                        Object debtDateObj = record.get(ORDER.DEBT_PAYMENT_DATE);
                        System.out.println("\n=== Обработка записи ===");
                        System.out.println("orderId: " + dto.getOrderId());
                        System.out.println("debtDateObj: " + debtDateObj);

                        if (debtDateObj != null) {
                            System.out.println("Тип debtDateObj: " + debtDateObj.getClass().getName());
                            System.out.println("toString: " + debtDateObj.toString());

                            // Проверяем все возможные типы
                            System.out.println("Проверка типов:");
                            System.out.println("  - LocalDate: " + (debtDateObj instanceof LocalDate));
                            System.out.println("  - LocalDateTime: " + (debtDateObj instanceof LocalDateTime));
                            System.out.println("  - java.sql.Date: " + (debtDateObj instanceof java.sql.Date));
                            System.out.println("  - java.sql.Timestamp: " + (debtDateObj instanceof java.sql.Timestamp));
                            System.out.println("  - java.util.Date: " + (debtDateObj instanceof java.util.Date));
                            System.out.println("  - String: " + (debtDateObj instanceof String));

                            // Преобразуем в LocalDate
                            if (debtDateObj instanceof LocalDate) {
                                dto.setDebt_payment_date((LocalDate) debtDateObj);
                            } else if (debtDateObj instanceof LocalDateTime) {
                                dto.setDebt_payment_date(((LocalDateTime) debtDateObj).toLocalDate());
                            } else if (debtDateObj instanceof java.sql.Date) {
                                dto.setDebt_payment_date(((java.sql.Date) debtDateObj).toLocalDate());
                            } else if (debtDateObj instanceof java.sql.Timestamp) {
                                dto.setDebt_payment_date(((java.sql.Timestamp) debtDateObj).toLocalDateTime().toLocalDate());
                            } else if (debtDateObj instanceof java.util.Date) {
                                dto.setDebt_payment_date(((java.util.Date) debtDateObj).toInstant()
                                        .atZone(ZoneId.systemDefault()).toLocalDate());
                            } else if (debtDateObj instanceof String) {
                                // Если это строка, пробуем распарсить
                                try {
                                    dto.setDebt_payment_date(LocalDate.parse((String) debtDateObj));
                                } catch (Exception e) {
                                    System.err.println("Не удалось распарсить строку: " + debtDateObj);
                                }
                            } else {
                                System.out.println("⚠️ Неизвестный тип: " + debtDateObj.getClass());
                            }
                        } else {
                            System.out.println("debtDateObj is NULL");
                        }

                        dto.setDuty(record.get(ORDER.DUTY));
                        dto.setStatus(record.get(ORDER.STATUS));
                        dto.setClientId(record.get(CLIENT.CLIENTID));

                        System.out.println("Итоговый debt_payment_date в DTO: " + dto.getDebt_payment_date());
                        return dto;
                    });

            System.out.println("\n✅ Результат прямого сравнения с today: " + result.size());

            // Если ничего не найдено, пробуем через SQL функцию DATE()
            if (result.isEmpty()) {
                System.out.println("\nПробуем через DATE() функцию...");

                // Сначала выведем SQL запрос для отладки
                String sql = dsl.select()
                        .from(ORDER)
                        .join(CLIENT).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                        .where(ORDER.DUTY.eq(true)
                                .and(condition("DATE({0}) = DATE(?)", ORDER.DEBT_PAYMENT_DATE, today))
                                .and(ORDER.STATUS.eq(false)))
                        .getSQL();
                System.out.println("SQL запрос: " + sql);

                result = dsl.select()
                        .from(ORDER)
                        .join(CLIENT).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                        .where(ORDER.DUTY.eq(true)
                                .and(condition("DATE({0}) = DATE(?)", ORDER.DEBT_PAYMENT_DATE, today))
                                .and(ORDER.STATUS.eq(false)))
                        .fetch(record -> {
                            OrderDTO dto = new OrderDTO();
                            dto.setOrderId(record.get(ORDER.ORDERID));
                            dto.setAmount(record.get(ORDER.AMOUNT));
                            dto.setDate(record.get(ORDER.DATE));

                            Object debtDateObj = record.get(ORDER.DEBT_PAYMENT_DATE);
                            System.out.println("Через DATE(): orderId=" + dto.getOrderId() +
                                    ", debtDateObj=" + debtDateObj +
                                    ", тип=" + (debtDateObj != null ? debtDateObj.getClass().getName() : "null"));

                            // обработка как выше
                            if (debtDateObj instanceof LocalDate) {
                                dto.setDebt_payment_date((LocalDate) debtDateObj);
                            } else if (debtDateObj instanceof LocalDateTime) {
                                dto.setDebt_payment_date(((LocalDateTime) debtDateObj).toLocalDate());
                            } else if (debtDateObj instanceof java.sql.Date) {
                                dto.setDebt_payment_date(((java.sql.Date) debtDateObj).toLocalDate());
                            } else if (debtDateObj instanceof java.sql.Timestamp) {
                                dto.setDebt_payment_date(((java.sql.Timestamp) debtDateObj).toLocalDateTime().toLocalDate());
                            }

                            dto.setDuty(record.get(ORDER.DUTY));
                            dto.setStatus(record.get(ORDER.STATUS));
                            dto.setClientId(record.get(CLIENT.CLIENTID));
                            return dto;
                        });

                System.out.println("Через DATE() найдено: " + result.size());
            }

            return result;

        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<OrderDTO> getOverdueDebts() {
        LocalDate today = LocalDate.now();
        System.out.println("=== getOverdueDebts() ===");
        System.out.println("Сегодня: " + today);

        try {
            List<OrderDTO> result = dsl.select()
                    .from(ORDER)
                    .join(CLIENT).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                    .where(ORDER.DUTY.eq(true)
                            .and(ORDER.DEBT_PAYMENT_DATE.lt(today)) // как LocalDate
                            .and(ORDER.STATUS.eq(false)))
                    .fetch(record -> {
                        OrderDTO dto = new OrderDTO();
                        dto.setOrderId(record.get(ORDER.ORDERID));
                        dto.setAmount(record.get(ORDER.AMOUNT));
                        dto.setDate(record.get(ORDER.DATE));

                        Object debtDateObj = record.get(ORDER.DEBT_PAYMENT_DATE);
                        if (debtDateObj instanceof LocalDate) {
                            dto.setDebt_payment_date((LocalDate) debtDateObj);
                        } else if (debtDateObj instanceof LocalDateTime) {
                            dto.setDebt_payment_date(((LocalDateTime) debtDateObj).toLocalDate());
                        }

                        dto.setDuty(record.get(ORDER.DUTY));
                        dto.setStatus(record.get(ORDER.STATUS));
                        dto.setClientId(record.get(CLIENT.CLIENTID));

                        System.out.println("Найден просроченный: orderId=" + dto.getOrderId());
                        return dto;
                    });

            System.out.println("✅ Найдено просроченных: " + result.size());

            // Если ничего не найдено, пробуем через DATE()
            if (result.isEmpty()) {
                result = dsl.select()
                        .from(ORDER)
                        .join(CLIENT).on(ORDER.CLIENTID.eq(CLIENT.CLIENTID))
                        .where(ORDER.DUTY.eq(true)
                                .and(condition("DATE({0}) < DATE(?)", ORDER.DEBT_PAYMENT_DATE, today))
                                .and(ORDER.STATUS.eq(false)))
                        .fetch(record -> {
                            OrderDTO dto = new OrderDTO();
                            dto.setOrderId(record.get(ORDER.ORDERID));
                            dto.setAmount(record.get(ORDER.AMOUNT));
                            dto.setDate(record.get(ORDER.DATE));

                            Object debtDateObj = record.get(ORDER.DEBT_PAYMENT_DATE);
                            if (debtDateObj instanceof LocalDate) {
                                dto.setDebt_payment_date((LocalDate) debtDateObj);
                            } else if (debtDateObj instanceof LocalDateTime) {
                                dto.setDebt_payment_date(((LocalDateTime) debtDateObj).toLocalDate());
                            }

                            dto.setDuty(record.get(ORDER.DUTY));
                            dto.setStatus(record.get(ORDER.STATUS));
                            dto.setClientId(record.get(CLIENT.CLIENTID));
                            return dto;
                        });

                System.out.println("Через DATE() найдено просроченных: " + result.size());
            }

            return result;

        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
