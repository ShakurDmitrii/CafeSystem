package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.*;
import jooqdata.tables.Client;
import jooqdata.tables.Clientdish;
import jooqdata.tables.Dish;
import jooqdata.tables.Order;
import jooqdata.tables.records.ClientRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Long.sum;

@Service
public class ClientService {

    public static DSLContext dsl;

    public ClientService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ClientDTO> getAllClients() {
        return dsl.selectFrom(Client.CLIENT)
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
                            Client.CLIENT.CLIENTID,
                            Client.CLIENT.FULLNAME,
                            Client.CLIENT.NUMBER
                    )
                    .from(Client.CLIENT)
                    .join(Order.ORDER).on(Order.ORDER.CLIENTID.eq(Client.CLIENT.CLIENTID))
                    .where(Order.ORDER.DUTY.eq(duty))
                    .fetch()
                    .stream()
                    .map(record -> {
                        ClientDTO dto = new ClientDTO();
                        dto.setClientId(record.get(Client.CLIENT.CLIENTID));
                        dto.setFullName(record.get(Client.CLIENT.FULLNAME));
                        dto.setNumber(record.get(Client.CLIENT.NUMBER));
                        return dto;
                    })
                    .toList();

            List<ClientWithDutyDTO> result = new ArrayList<>();

            for (ClientDTO client : clientsWithDuty) {
                List<OrderDTO> dutyOrders = dsl.selectFrom(Order.ORDER)
                        .where(Order.ORDER.CLIENTID.eq(client.getClientId())
                                .and(Order.ORDER.DUTY.eq(duty)))
                        .fetch()
                        .stream()
                        .map(record -> {
                            OrderDTO order = new OrderDTO();
                            order.setOrderId(record.get(Order.ORDER.ORDERID));
                            order.setDate(record.get(Order.ORDER.DATE));
                            order.setAmount(record.get(Order.ORDER.AMOUNT));
                            order.setDuty(record.get(Order.ORDER.DUTY));
                            order.setTimeDelay(record.get(Order.ORDER.TIMEDELAY));
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
            Integer clientId = dsl.insertInto(Client.CLIENT)
                    .set(Client.CLIENT.FULLNAME, dto.getFullName())
                    .set(Client.CLIENT.NUMBER, dto.getNumber() != null ? dto.getNumber() : "")
                    .returningResult(Client.CLIENT.CLIENTID)
                    .fetchOne()
                    .get(Client.CLIENT.CLIENTID);

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

}
