package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ClientDTO;
import com.shakur.cafehelp.DTO.ClientDishDTO;
import jooqdata.tables.Client;
import jooqdata.tables.Clientdish;
import jooqdata.tables.Dish;
import jooqdata.tables.records.ClientRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {
    public static DSLContext dsl;
    public ClientService() {this.dsl = dsl;}

    public List<ClientDTO> getAllClients() {
        return dsl.selectFrom(Client.CLIENT)
                .fetch()
                .stream()
                .map(record ->  {
                    ClientDTO dto = new ClientDTO();
                    dto.clientId = record.getClientid();
                    dto.fullName = record.getFullname();

                    return dto;
                }).toList();
    }
    public ClientDTO createClient(ClientDTO dto) {
        ClientRecord record = dsl.newRecord(jooqdata.tables.Client.CLIENT, dto);
        record.setClientid(dto.clientId);
        record.setFullname(dto.fullName);
        record.store();
        return dto;
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
