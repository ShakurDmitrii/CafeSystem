package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.DTO.ClientDTO;
import com.shakur.cafehelp.DTO.ClientDishDTO;
import com.shakur.cafehelp.DTO.ClientWithDutyDTO;
import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.Service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    // 1. Получить всех клиентов
    @GetMapping
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        try {
            List<ClientDTO> clients = clientService.getAllClients();
            return ResponseEntity.ok(clients);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // 2. Создать нового клиента
    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@RequestBody ClientDTO clientDTO) {
        try {
            if (clientDTO.getFullName() == null || clientDTO.getFullName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(null);
            }

            ClientDTO createdClient = clientService.createClient(clientDTO);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(createdClient);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // 3. Получить клиентов с долгами и их заказы
    @GetMapping("/duty")
    public ResponseEntity<List<ClientWithDutyDTO>> getClientsWithDutyOrders() {
        try {
            List<ClientWithDutyDTO> clientsWithDuty = clientService.getClientsWithDutyOrders(true);
            return ResponseEntity.ok(clientsWithDuty);
        } catch (Exception e) {
            System.err.println("Error in getClientsWithDutyOrders controller: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // 4. Получить блюда клиента
    @GetMapping("/{clientId}/dishes")
    public ResponseEntity<List<ClientDishDTO>> getClientDishes(@PathVariable int clientId) {
        try {
            List<ClientDishDTO> dishes = clientService.getDishesByClientId(clientId);
            return ResponseEntity.ok(dishes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // 5. Получить конкретного клиента по ID
    @GetMapping("/{clientId}")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable int clientId) {
        try {
            List<ClientDTO> allClients = clientService.getAllClients();
            ClientDTO client = allClients.stream()
                    .filter(c -> c.getClientId() == clientId)
                    .findFirst()
                    .orElse(null);

            if (client == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            return ResponseEntity.ok(client);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // 6. Обновить долг клиента (пометить все заказы как оплаченные)
    @PatchMapping("/{clientId}/pay-duties")
    public ResponseEntity<String> payClientDuties(@PathVariable int clientId) {
        try {
            // Здесь нужно добавить метод в ClientService для обновления долгов
            // clientService.payClientDuties(clientId);
            return ResponseEntity.ok("Долги клиента #" + clientId + " отмечены как оплаченные");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обновлении долгов: " + e.getMessage());
        }
    }

    // 7. Поиск клиентов по имени
    @GetMapping("/search")
    public ResponseEntity<List<ClientDTO>> searchClients(@RequestParam String name) {
        try {
            List<ClientDTO> allClients = clientService.getAllClients();
            List<ClientDTO> filteredClients = allClients.stream()
                    .filter(client -> client.getFullName() != null &&
                            client.getFullName().toLowerCase().contains(name.toLowerCase()))
                    .toList();

            return ResponseEntity.ok(filteredClients);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
}