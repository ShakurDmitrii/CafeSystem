package com.shakur.cafehelp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shakur.cafehelp.DTO.InventoryShiftReportDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportRowDTO;
import com.shakur.cafehelp.DTO.ClientDTO;
import com.shakur.cafehelp.DTO.DebtPaymentRequestDTO;
import com.shakur.cafehelp.DTO.DishCategoryDTO;
import com.shakur.cafehelp.DTO.DishDTO;
import com.shakur.cafehelp.DTO.DishSetDTO;
import com.shakur.cafehelp.DTO.DishSetItemDTO;
import com.shakur.cafehelp.DTO.MovementDTO;
import com.shakur.cafehelp.DTO.MovementRequestDTO;
import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.DTO.OrderEditRequestDTO;
import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.ProductDTO;
import com.shakur.cafehelp.DTO.PreparationDTO;
import com.shakur.cafehelp.DTO.SalaryPaymentRequestDTO;
import com.shakur.cafehelp.DTO.SalaryReversalRequestDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import com.shakur.cafehelp.DTO.SupplierDTO;
import com.shakur.cafehelp.DTO.TechProductDTO;
import com.shakur.cafehelp.DTO.VkBotLinkConfirmRequestDTO;
import com.shakur.cafehelp.Service.ClientService;
import com.shakur.cafehelp.Service.DishCategoryService;
import com.shakur.cafehelp.Service.DishService;
import com.shakur.cafehelp.Service.DishSetService;
import com.shakur.cafehelp.Service.InventoryShiftReportService;
import com.shakur.cafehelp.Service.MovementService;
import com.shakur.cafehelp.Service.OrderService;
import com.shakur.cafehelp.Service.PayrollService;
import com.shakur.cafehelp.Service.PersonService;
import com.shakur.cafehelp.Service.PreparationService;
import com.shakur.cafehelp.Service.ProductService;
import com.shakur.cafehelp.Service.ShiftService;
import com.shakur.cafehelp.Service.ShiftInventorySnapshotService;
import com.shakur.cafehelp.Service.SupplierService;
import com.shakur.cafehelp.Service.TechProductService;
import com.shakur.cafehelp.Service.TaxOutboxRelayService;
import com.shakur.cafehelp.Service.TaxReconciliationService;
import com.shakur.cafehelp.Service.TaxReceiptDispatchService;
import com.shakur.cafehelp.Service.WareHouseService;
import com.shakur.cafehelp.Service.VkClientLinkService;
import com.shakur.cafehelp.exception.OrderStateConflictException;
import com.shakur.cafehelp.config.BusinessTimeProvider;
import com.shakur.cafehelp.security.JwtService;
import jooqdata.tables.records.ShiftRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class CafehelpApplicationTests {

    @Container
    static final PostgreSQLContainer<?> MAIN_DATABASE = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cafehelp_test")
            .withUsername("cafehelp")
            .withPassword("cafehelp");

    @Container
    static final PostgreSQLContainer<?> TAX_DATABASE = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cafehelp_tax_test")
            .withUsername("cafehelp")
            .withPassword("cafehelp");

    private static final AtomicReference<String> PARTNER_MODE = new AtomicReference<>("SUCCESS");
    private static final java.util.concurrent.CopyOnWriteArrayList<String> PARTNER_IDEMPOTENCY_KEYS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final HttpServer PARTNER_SERVER = startPartnerServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private OrderService orderService;

    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private ShiftInventorySnapshotService shiftInventorySnapshotService;

    @Autowired
    private InventoryShiftReportService inventoryShiftReportService;

    @Autowired
    private MovementService movementService;

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PersonService personService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DishCategoryService dishCategoryService;

    @Autowired
    private DishService dishService;

    @Autowired
    private DishSetService dishSetService;

    @Autowired
    private PreparationService preparationService;

    @Autowired
    private TechProductService techProductService;

    @Autowired
    private TaxOutboxRelayService taxOutboxRelayService;

    @Autowired
    private TaxReceiptDispatchService taxReceiptDispatchService;

    @Autowired
    private TaxReconciliationService taxReconciliationService;

    @Autowired
    @Qualifier("taxJdbcTemplate")
    private JdbcTemplate taxJdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VkClientLinkService vkClientLinkService;

    @Autowired
    private BusinessTimeProvider businessTime;

    @DynamicPropertySource
    static void configureDatabases(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MAIN_DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", MAIN_DATABASE::getUsername);
        registry.add("spring.datasource.password", MAIN_DATABASE::getPassword);
        registry.add("tax.datasource.url", TAX_DATABASE::getJdbcUrl);
        registry.add("tax.datasource.username", TAX_DATABASE::getUsername);
        registry.add("tax.datasource.password", TAX_DATABASE::getPassword);
        registry.add("app.tax-worker.enabled", () -> "false");
        registry.add("tax.dispatch.mode", () -> "PARTNER");
        registry.add("tax.dispatch.partner.base-url", () -> "http://127.0.0.1:" + PARTNER_SERVER.getAddress().getPort());
        registry.add("tax.dispatch.partner.send-path", () -> "/receipts");
        registry.add("tax.dispatch.partner.api-key", () -> "integration-test-key");
        registry.add("tax.dispatch.partner.timeout-ms", () -> "1000");
    }

    @BeforeEach
    void resetBusinessData() {
        PARTNER_MODE.set("SUCCESS");
        PARTNER_IDEMPOTENCY_KEYS.clear();
        dsl.execute("""
                TRUNCATE TABLE
                    sales.tax_outbox,
                    sales.salary_payment,
                    sales.salary_accrual,
                    sales.client_vk_link_event,
                    sales.client_vk_link_attempt,
                    sales.client_vk_link_code,
                    sales.client_vk_link,
                    sales.debt_payment,
                    sales.inventory_shift_report_line,
                    sales.inventory_shift_report,
                    sales.shift_inventory_snapshot,
                    sales.stock_movements,
                    sales.inventory_document_lines,
                    sales.inventory_documents,
                    sales.orderdish,
                    sales."order",
                    sales.preparationwarehouse,
                    sales.techproduct,
                    sales.preparation,
                    sales.dish,
                    sales.productwarehouse,
                    sales.product_supplier,
                    sales.product,
                    sales.supplier,
                    sales.warehouse,
                    sales.shiftperson,
                    sales.user_account,
                    sales.person,
                    sales.shift,
                    sales.client
                RESTART IDENTITY CASCADE
                """);
        taxJdbcTemplate.execute("""
                TRUNCATE TABLE
                    tax.tax_receipt_attempt,
                    tax.tax_receipt_job,
                    tax.tax_reconcile_gap,
                    tax.tax_reconcile_run
                RESTART IDENTITY CASCADE
                """);
    }

    @AfterAll
    static void stopPartnerServer() {
        PARTNER_SERVER.stop(0);
    }

    @Test
    void payrollSnapshotsRatesSupportsPartialPaymentsAndReversals() {
        int ownerPersonId = createPerson("Владелец");
        createAccount(ownerPersonId, "payroll-owner", "OWNER");
        int employeeId = createPersonWithRate("Сотрудник", "2500.00");

        closeSalaryShift(employeeId);
        dsl.update(jooqdata.tables.Person.PERSON)
                .set(jooqdata.tables.Person.PERSON.SALARYPERDAY, new BigDecimal("3000.00"))
                .where(jooqdata.tables.Person.PERSON.PERSONID.eq(employeeId))
                .execute();
        closeSalaryShift(employeeId);

        var initial = payrollService.getSummaries().stream()
                .filter(summary -> summary.personId() == employeeId)
                .findFirst()
                .orElseThrow();
        assertThat(initial.accruedShifts()).isEqualTo(2);
        assertThat(initial.accruedAmount()).isEqualByComparingTo("5500.00");
        assertThat(initial.balance()).isEqualByComparingTo("5500.00");

        SalaryPaymentRequestDTO partialRequest = salaryPayment("2000.00", "salary-partial-0001");
        var partial = payrollService.createPayment(employeeId, partialRequest, "payroll-owner");
        var repeated = payrollService.createPayment(employeeId, partialRequest, "payroll-owner");
        assertThat(repeated.paymentId()).isEqualTo(partial.paymentId());
        assertThat(partial.balanceAfter()).isEqualByComparingTo("3500.00");

        var full = payrollService.createPayment(
                employeeId,
                salaryPayment("3500.00", "salary-full-000001"),
                "payroll-owner"
        );
        assertThat(full.balanceAfter()).isZero();
        assertThatThrownBy(() -> payrollService.createPayment(
                employeeId,
                salaryPayment("0.01", "salary-overpay-01"),
                "payroll-owner"
        )).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("превышает");

        SalaryReversalRequestDTO reversalRequest = new SalaryReversalRequestDTO();
        reversalRequest.setIdempotencyKey("salary-reversal-0001");
        reversalRequest.setComment("Ошибочная сумма");
        var reversal = payrollService.reversePayment(partial.paymentId(), reversalRequest, "payroll-owner");
        assertThat(reversal.entryType()).isEqualTo("REVERSAL");
        assertThat(reversal.balanceAfter()).isEqualByComparingTo("2000.00");
        assertThat(payrollService.reversePayment(partial.paymentId(), reversalRequest, "payroll-owner").paymentId())
                .isEqualTo(reversal.paymentId());

        var history = payrollService.getPaymentHistory(employeeId, 0, 20);
        assertThat(history.totalElements()).isEqualTo(3);
        assertThat(history.items()).extracting(item -> item.entryType())
                .containsExactly("REVERSAL", "PAYMENT", "PAYMENT");
    }

    @Test
    void concurrentSalaryPaymentsCannotExceedAccruedBalance() throws Exception {
        int ownerPersonId = createPerson("Владелец");
        createAccount(ownerPersonId, "concurrent-owner", "OWNER");
        int employeeId = createPersonWithRate("Сотрудник", "100.00");
        closeSalaryShift(employeeId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> first = () -> runConcurrentSalaryPayment(
                employeeId,
                "concurrent-owner",
                "salary-concurrent-01",
                ready,
                start
        );
        Callable<Boolean> second = () -> runConcurrentSalaryPayment(
                employeeId,
                "concurrent-owner",
                "salary-concurrent-02",
                ready,
                start
        );
        var firstFuture = executor.submit(first);
        var secondFuture = executor.submit(second);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successes = (firstFuture.get(10, TimeUnit.SECONDS) ? 1 : 0)
                + (secondFuture.get(10, TimeUnit.SECONDS) ? 1 : 0);
        executor.shutdownNow();
        assertThat(successes).isEqualTo(1);
        var summary = payrollService.getSummaries().stream()
                .filter(item -> item.personId() == employeeId)
                .findFirst()
                .orElseThrow();
        assertThat(summary.balance()).isZero();
        assertThat(summary.paidAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void payrollApiRequiresOwnerAndStoresAuthenticatedAuthor() throws Exception {
        int ownerPersonId = createPerson("Владелец");
        int ownerAccountId = createAccount(ownerPersonId, "api-owner", "OWNER");
        int workerPersonId = createPerson("Работник");
        int workerAccountId = createAccount(workerPersonId, "api-worker", "WORKER");
        int employeeId = createPersonWithRate("Получатель", "800.00");
        closeSalaryShift(employeeId);

        String workerToken = "Bearer " + jwtService.generateToken(
                "api-worker", workerAccountId, workerPersonId, "WORKER"
        );
        String ownerToken = "Bearer " + jwtService.generateToken(
                "api-owner", ownerAccountId, ownerPersonId, "OWNER"
        );

        mockMvc.perform(get("/api/v1/payroll/employees").header("Authorization", workerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payroll/employees/{personId}/payments", employeeId)
                        .header("Authorization", workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 100.00, "idempotencyKey": "api-worker-payment-01"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payroll/employees/{personId}/payments", employeeId)
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 300.00, "idempotencyKey": "api-owner-payment-001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorAccountId").value(ownerAccountId))
                .andExpect(jsonPath("$.balanceAfter").value(500.0));
    }

    @Test
    void employeeCannotBeArchivedDuringOpenShiftOrWithOutstandingSalary() {
        int ownerPersonId = createPerson("Владелец");
        createAccount(ownerPersonId, "archive-owner", "OWNER");
        int employeeId = createPersonWithRate("Сотрудник", "400.00");
        ShiftDTO opened = shiftService.createShift(shiftRequest(employeeId, List.of(employeeId)));

        assertThatThrownBy(() -> personService.deletePerson(employeeId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("открытая смена");

        shiftService.closeShift(opened.shiftId, BigDecimal.ZERO);
        assertThatThrownBy(() -> personService.deletePerson(employeeId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("остаток зарплаты");

        payrollService.createPayment(
                employeeId,
                salaryPayment("400.00", "salary-before-archive"),
                "archive-owner"
        );
        assertThat(personService.deletePerson(employeeId)).isTrue();
    }

    @Test
    void indirectPreparationCycleIsRejectedBeforeItReachesRecipeCalculations() {
        int firstId = createPreparation("Заготовка A");
        int secondId = createPreparation("Заготовка B");
        int thirdId = createPreparation("Заготовка C");

        techProductService.create(preparationIngredient(firstId, secondId));
        techProductService.create(preparationIngredient(secondId, thirdId));

        assertThatThrownBy(() -> techProductService.create(preparationIngredient(thirdId, firstId)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("цикличес");
        assertThat(techProductService.getByPreparationId(thirdId)).isEmpty();
    }

    @Test
    void usedCatalogObjectsReturnBusinessConflictsInsteadOfDatabaseErrors() {
        SupplierDTO supplier = new SupplierDTO();
        supplier.setSupplierName("Поставщик для проверки");
        supplier.setCommunication("+7 999 000-00-00");
        SupplierDTO createdSupplier = supplierService.create(supplier);

        ProductDTO product = validProduct("Продукт поставщика");
        product.setSupplierId(createdSupplier.getSupplierID());
        productService.createProduct(product);

        assertThatThrownBy(() -> supplierService.delete(createdSupplier.getSupplierID()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("используется");

        DishCategoryDTO category = new DishCategoryDTO();
        category.setName("Категория с блюдом");
        DishCategoryDTO createdCategory = dishCategoryService.create(category);
        DishDTO dish = validDish("Блюдо категории");
        dish.setCategoryId(createdCategory.getCategoryId());
        dishService.createDish(dish);

        assertThatThrownBy(() -> dishCategoryService.delete(createdCategory.getCategoryId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("используется");

        int ownerPreparationId = createPreparation("Составная заготовка");
        int ingredientPreparationId = createPreparation("Вложенная заготовка");
        techProductService.create(preparationIngredient(ownerPreparationId, ingredientPreparationId));

        assertThatThrownBy(() -> preparationService.delete(ingredientPreparationId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("используется");
    }

    @Test
    void catalogRejectsInvalidNamesPricesUnitsAndRecipeNumbers() {
        ProductDTO blankProduct = validProduct("   ");
        assertThatThrownBy(() -> productService.createProduct(blankProduct))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Название");

        ProductDTO incompatibleUnits = validProduct("Неверные единицы");
        incompatibleUnits.setUnit("kg");
        incompatibleUnits.setBaseUnit("ml");
        assertThatThrownBy(() -> productService.createProduct(incompatibleUnits))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("единиц");

        DishDTO invalidDish = validDish("Блюдо без цены");
        invalidDish.setPrice(0.0);
        assertThatThrownBy(() -> dishService.createDish(invalidDish))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Цена");

        int preparationId = createPreparation("Заготовка с ошибкой");
        int productId = createStandaloneProduct("Ингредиент с ошибкой");
        TechProductDTO invalidRow = new TechProductDTO();
        invalidRow.setPreparationId(preparationId);
        invalidRow.setProductId(productId);
        invalidRow.setWeight(Double.NaN);
        invalidRow.setWaste(0.0);

        assertThatThrownBy(() -> techProductService.create(invalidRow))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Вес");
    }

    @Test
    void dishSetUsedByOrderCannotBeDeletedAndNonFinitePriceIsRejected() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);

        DishSetItemDTO setItem = new DishSetItemDTO();
        setItem.setDishId(fixture.dishId());
        setItem.setQty(1);
        DishSetDTO set = new DishSetDTO();
        set.setSetName("Набор для заказа");
        set.setPrice(450.0);
        set.setItems(List.of(setItem));
        DishSetDTO createdSet = dishSetService.create(set);

        OrderDishDTO orderItem = new OrderDishDTO();
        orderItem.setSetId(createdSet.getSetId());
        orderItem.setQty(1);
        OrderDTO order = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        order.setItems(List.of(orderItem));
        orderService.createOrder(order);

        assertThatThrownBy(() -> dishSetService.delete(createdSet.getSetId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("используется");

        DishSetDTO invalid = new DishSetDTO();
        invalid.setSetName("Набор с неверной ценой");
        invalid.setPrice(Double.NaN);
        invalid.setItems(List.of(setItem));
        assertThatThrownBy(() -> dishSetService.create(invalid))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Цена");
    }

    @Test
    void legacySalaryAndWorkedDaysColumnsAreRemoved() {
        List<String> columns = dsl.select(DSL.field(DSL.name("column_name"), String.class))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq("sales"))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("person"))
                .fetch(0, String.class);

        assertThat(columns).doesNotContain("salary", "numdays");
        assertThat(columns).contains("salaryperday");
    }

    @Test
    void paidOrderCreatesOneTaxOutboxEventInTheOrderTransaction() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);

        OrderDTO created = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));

        var outbox = dsl.fetchOne(
                """
                select aggregate_id, event_key, event_type, status, payload_json::text
                from sales.tax_outbox
                where aggregate_type = 'order' and aggregate_id = ?
                """,
                created.getOrderId()
        );
        assertThat(outbox).isNotNull();
        assertThat(outbox.get("event_key", String.class))
                .isEqualTo("order:" + created.getOrderId() + ":paid");
        assertThat(outbox.get("event_type", String.class)).isEqualTo("order_paid");
        assertThat(outbox.get("status", String.class)).isEqualTo("pending");
        assertThat(outbox.get("payload_json", String.class)).contains("Test roll");

        orderService.updateOrderPayment(created.getOrderId(), "cash", true);
        assertThat(dsl.fetchCount(
                DSL.table(DSL.name("sales", "tax_outbox")),
                DSL.field(DSL.name("aggregate_id"), Integer.class).eq(created.getOrderId())
        )).isEqualTo(1);
    }

    @Test
    void paymentTransitionCreatesOneOutboxEventAndStaleRelayLockRecovers() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), false));
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "tax_outbox")))).isZero();

        orderService.updateOrderPayment(created.getOrderId(), "cash", true);
        orderService.updateOrderPayment(created.getOrderId(), "cash", true);
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "tax_outbox")))).isEqualTo(1);

        dsl.execute(
                """
                update sales.tax_outbox
                set status = 'processing', locked_at = now() - interval '1 hour'
                where aggregate_id = ?
                """,
                created.getOrderId()
        );

        var relay = taxOutboxRelayService.relayPending(10);
        assertThat(relay.processed()).isEqualTo(1);
        assertThat(dsl.fetchValue(
                "select status from sales.tax_outbox where aggregate_id = ?",
                created.getOrderId()
        )).isEqualTo("processed");
        assertThat(taxJdbcTemplate.queryForObject(
                "select count(*) from tax.tax_receipt_job where order_id = ?",
                Integer.class,
                created.getOrderId()
        )).isEqualTo(1);

        assertThat(taxOutboxRelayService.relayPending(10).claimed()).isZero();
        assertThat(taxJdbcTemplate.queryForObject(
                "select count(*) from tax.tax_receipt_job where order_id = ?",
                Integer.class,
                created.getOrderId()
        )).isEqualTo(1);
    }

    @Test
    void paidOrderAndTaxOutboxRollbackTogetherWhenEventCannotBeStored() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        dsl.execute("""
                alter table sales.tax_outbox
                add constraint tax_outbox_test_reject_chk check (false)
                """);
        try {
            assertThatThrownBy(() -> orderService.createOrder(
                    orderRequest(fixture.shiftId(), fixture.dishId(), true)
            )).isInstanceOf(RuntimeException.class);

            assertThat(dsl.fetchCount(jooqdata.tables.Order.ORDER)).isZero();
            assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "tax_outbox")))).isZero();
            assertThat(wareHouseService.getAvailableQuantity(
                    fixture.warehouseId(),
                    fixture.productId()
            )).isEqualTo(10.0);
        } finally {
            dsl.execute("""
                    alter table sales.tax_outbox
                    drop constraint if exists tax_outbox_test_reject_chk
                    """);
        }
    }

    @Test
    void concurrentTaxRelaysClaimAnOutboxEventOnlyOnce() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));

        List<Boolean> processed = runConcurrently(
                2,
                () -> taxOutboxRelayService.relayPending(1).processed() == 1
        );

        assertThat(processed).containsExactlyInAnyOrder(true, false);
        assertThat(taxJdbcTemplate.queryForObject(
                "select count(*) from tax.tax_receipt_job",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void staleTaxReceiptJobProcessingLockReturnsToPending() {
        taxJdbcTemplate.update("""
                insert into tax.tax_receipt_job (
                    order_id, amount, payment_type, status,
                    processing_started_at, idempotency_key
                ) values (?, 100, 'cash', 'processing', now() - interval '1 hour', ?)
                """, 777, "stale-job-777");

        assertThat(taxReceiptDispatchService.recoverStaleProcessing()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject(
                "select status from tax.tax_receipt_job where order_id = 777",
                String.class
        )).isEqualTo("pending");
    }

    @Test
    void retryablePartnerFailureUsesBackoffAndStableIdempotencyKey() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO order = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        assertThat(taxOutboxRelayService.relayPending(10).processed()).isEqualTo(1);

        PARTNER_MODE.set("FAIL_500");
        var failed = taxReceiptDispatchService.dispatchPending(10);
        assertThat(failed.failed()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject(
                "select status from tax.tax_receipt_job where order_id = ?",
                String.class,
                order.getOrderId()
        )).isEqualTo("pending");
        assertThat(taxJdbcTemplate.queryForObject(
                "select attempt_count from tax.tax_receipt_job where order_id = ?",
                Integer.class,
                order.getOrderId()
        )).isEqualTo(1);

        taxJdbcTemplate.update(
                "update tax.tax_receipt_job set next_attempt_at = now() where order_id = ?",
                order.getOrderId()
        );
        PARTNER_MODE.set("SUCCESS");
        var retried = taxReceiptDispatchService.dispatchPending(10);
        assertThat(retried.sent()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject(
                "select status from tax.tax_receipt_job where order_id = ?",
                String.class,
                order.getOrderId()
        )).isEqualTo("sent");
        assertThat(PARTNER_IDEMPOTENCY_KEYS).hasSize(2).doesNotContainNull();
        assertThat(PARTNER_IDEMPOTENCY_KEYS.stream().distinct()).hasSize(1);
    }

    @Test
    void partnerTimeoutAndInvalidSuccessResponseDoNotMarkReceiptSent() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO order = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        taxOutboxRelayService.relayPending(10);

        PARTNER_MODE.set("TIMEOUT");
        var timeout = taxReceiptDispatchService.dispatchPending(10);
        assertThat(timeout.failed()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject(
                "select status from tax.tax_receipt_job where order_id = ?",
                String.class,
                order.getOrderId()
        )).isEqualTo("pending");

        taxJdbcTemplate.update(
                "update tax.tax_receipt_job set next_attempt_at = now() where order_id = ?",
                order.getOrderId()
        );
        PARTNER_MODE.set("INVALID_200");
        var invalid = taxReceiptDispatchService.dispatchPending(10);
        assertThat(invalid.failed()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject(
                "select status from tax.tax_receipt_job where order_id = ?",
                String.class,
                order.getOrderId()
        )).isEqualTo("pending");
        assertThat(taxJdbcTemplate.queryForObject(
                "select provider_receipt_id from tax.tax_receipt_job where order_id = ?",
                String.class,
                order.getOrderId()
        )).isNull();
    }

    @Test
    void openApiDocumentIsCompatibleWithCurrentSpringBootVersion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths").isMap());
    }

    @Test
    void unavailableTaxDatabaseKeepsOutboxPendingWithBackoff() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO order = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        JdbcTemplate unavailableTaxDatabase = new JdbcTemplate() {
            @Override
            public int update(String sql, Object... args) {
                throw new DataAccessResourceFailureException("tax database unavailable");
            }
        };
        TaxOutboxRelayService isolatedRelay = new TaxOutboxRelayService(
                dsl,
                unavailableTaxDatabase,
                objectMapper
        );

        var result = isolatedRelay.relayPending(10);

        assertThat(result.failed()).isEqualTo(1);
        var outbox = dsl.fetchOne(
                "select status, attempt_count, available_at, locked_at from sales.tax_outbox where aggregate_id = ?",
                order.getOrderId()
        );
        assertThat(outbox.get("status", String.class)).isEqualTo("pending");
        assertThat(outbox.get("attempt_count", Integer.class)).isEqualTo(1);
        assertThat(outbox.get("available_at", java.time.LocalDateTime.class)).isAfter(businessTime.now());
        assertThat(outbox.get("locked_at", java.time.LocalDateTime.class)).isNull();
    }

    @Test
    void reconciliationRestoresMissingPaidOrderOutboxAndRecordsRepair() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO order = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        dsl.deleteFrom(DSL.table(DSL.name("sales", "tax_outbox")))
                .where(DSL.field(DSL.name("aggregate_id"), Integer.class).eq(order.getOrderId()))
                .execute();

        var result = taxReconciliationService.reconcile(order.getDate(), 100);

        assertThat(result.sourceOrdersCount()).isEqualTo(1);
        assertThat(result.outboxRestored()).isEqualTo(1);
        assertThat(dsl.fetchValue(
                "select status from sales.tax_outbox where event_key = ?",
                "order:" + order.getOrderId() + ":paid"
        )).isEqualTo("pending");
        var gap = taxJdbcTemplate.queryForMap("""
                select reason, resolved
                from tax.tax_reconcile_gap
                where order_id = ?
                """, order.getOrderId());
        assertThat(gap.get("reason")).isEqualTo("OUTBOX_MISSING");
        assertThat(gap.get("resolved")).isEqualTo(true);
    }

    @Test
    void reconciliationRequeuesProcessedOutboxWithoutJobUntilRelayRepairsIt() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO order = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        dsl.execute("""
                update sales.tax_outbox
                set status = 'processed', processed_at = now(), updated_at = now()
                where aggregate_id = ?
                """, order.getOrderId());

        var firstRun = taxReconciliationService.reconcile(order.getDate(), 100);

        assertThat(firstRun.taxJobsMissing()).isEqualTo(1);
        assertThat(firstRun.outboxRequeued()).isEqualTo(1);
        assertThat(dsl.fetchValue(
                "select status from sales.tax_outbox where aggregate_id = ?",
                order.getOrderId()
        )).isEqualTo("pending");
        assertThat(taxJdbcTemplate.queryForObject("""
                select resolved
                from tax.tax_reconcile_gap
                where order_id = ? and reason = 'TAX_JOB_MISSING'
                order by id desc
                limit 1
                """, Boolean.class, order.getOrderId())).isFalse();

        assertThat(taxOutboxRelayService.relayPending(10).processed()).isEqualTo(1);
        var secondRun = taxReconciliationService.reconcile(order.getDate(), 100);

        assertThat(secondRun.taxJobsMissing()).isZero();
        assertThat(taxJdbcTemplate.queryForObject("""
                select resolved
                from tax.tax_reconcile_gap
                where order_id = ? and reason = 'TAX_JOB_MISSING'
                order by id desc
                limit 1
                """, Boolean.class, order.getOrderId())).isTrue();
    }

    @Test
    void reconciliationDetectsMissingImmutableRefundSnapshotForCancelledPaidOrder() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO order = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        orderService.cancelOrder(order.getOrderId(), "Ошибка кассира", order.getVersion());
        dsl.execute(
                "delete from sales.tax_outbox where event_key = ?",
                "order:" + order.getOrderId() + ":refund"
        );

        var result = taxReconciliationService.reconcile(order.getDate(), 100);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject("""
                select count(*)
                from tax.tax_reconcile_gap
                where order_id = ? and reason = 'REFUND_OUTBOX_MISSING' and resolved = false
                """, Integer.class, order.getOrderId())).isEqualTo(1);
    }

    @Test
    void clientPhoneIsNormalizedAndCannotBeDuplicated() {
        ClientDTO first = new ClientDTO();
        first.setFullName("  Иван   Петров  ");
        first.setNumber("8 (999) 123-45-67");

        ClientDTO created = clientService.createClient(first);
        assertThat(created.getFullName()).isEqualTo("Иван Петров");
        assertThat(created.getNumber()).isEqualTo("+7 999 123-45-67");

        ClientDTO duplicate = new ClientDTO();
        duplicate.setFullName("Другой Иван");
        duplicate.setNumber("+7 999 123 45 67");

        assertThatThrownBy(() -> clientService.createClient(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("телефон");
    }

    @Test
    void clientSearchFindsNameAndFormattedPhoneCaseInsensitively() {
        int clientId = createClient("Иван Петров", "+7 999 123-45-67");
        createClient("Анна Сидорова", "+7 999 765-43-21");

        assertThat(clientService.searchClients("иван"))
                .extracting(ClientDTO::getClientId)
                .containsExactly(clientId);
        assertThat(clientService.searchClients("123-45"))
                .extracting(ClientDTO::getClientId)
                .containsExactly(clientId);
    }

    @Test
    void debtRequiresExistingClientAndDueDate() {
        OrderFixture fixture = createOrderFixture(10.0, 2.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);

        assertThatThrownBy(() -> orderService.createOrder(debt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("клиент");

        debt.setClientId(999_999);
        debt.setDebt_payment_date(businessTime.today().plusDays(1));
        assertThatThrownBy(() -> orderService.createOrder(debt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");

        debt.setClientId(createClient("Должник", "+7 999 000-00-01"));
        debt.setDebt_payment_date(null);
        assertThatThrownBy(() -> orderService.createOrder(debt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("дат");
    }

    @Test
    void debtConsumesAvailableStockWhenOrderIsCreated() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);
        debt.setClientId(createClient("Должник", "+7 999 000-00-02"));
        debt.setDebt_payment_date(businessTime.today().plusDays(1));

        OrderDTO created = orderService.createOrder(debt);

        assertThat(created.getDuty()).isTrue();
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(7.0);
    }

    @Test
    void readyOrderRemainsVisibleInOverdueDebts() {
        OrderFixture fixture = createOrderFixture(10.0, 1.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);
        debt.setStatus(true);
        debt.setClientId(createClient("Просроченный гость", "+7 999 000-00-03"));
        debt.setDebt_payment_date(businessTime.today().minusDays(1));

        OrderDTO created = orderService.createOrder(debt);

        assertThat(clientService.getOverdueDebts())
                .extracting(OrderDTO::getOrderId)
                .containsExactly(created.getOrderId());
    }

    @Test
    void vkLinkCodeIsBlockedAfterRepeatedInvalidAttempts() {
        int clientId = createClient("VK гость", "+7 999 000-00-04");
        var linkCode = vkClientLinkService.createLinkCode(clientId);
        VkBotLinkConfirmRequestDTO request = new VkBotLinkConfirmRequestDTO();
        request.setVkUserId(7_001L);
        request.setVkDomain("guest");

        for (int attempt = 0; attempt < 5; attempt++) {
            request.setCode("000000".equals(linkCode.code()) ? "000001" : "000000");
            assertThatThrownBy(() -> vkClientLinkService.confirmLink(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        request.setCode(linkCode.code());
        assertThatThrownBy(() -> vkClientLinkService.confirmLink(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("попыток");
    }

    @Test
    void debtSupportsPartialFullAndIdempotentPaymentsWithoutSecondStockWriteoff() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);
        debt.setClientId(createClient("Частичная оплата", "+7 999 000-00-05"));
        debt.setDebt_payment_date(businessTime.today().plusDays(1));
        OrderDTO created = orderService.createOrder(debt);

        DebtPaymentRequestDTO firstRequest = debtPayment("10.00", "cash", "partial-payment-1");
        var first = clientService.payDebt(created.getOrderId(), firstRequest);
        var repeated = clientService.payDebt(created.getOrderId(), firstRequest);

        assertThat(first.paymentId()).isEqualTo(repeated.paymentId());
        assertThat(first.remainingAmount()).isEqualByComparingTo("10.00");
        assertThat(clientService.getDebtPaymentHistory(created.getOrderId())).hasSize(1);

        var completed = clientService.payDebt(
                created.getOrderId(),
                debtPayment("10.00", "transfer", "partial-payment-2")
        );
        assertThat(completed.fullyPaid()).isTrue();
        assertThat(completed.remainingAmount()).isZero();
        assertThat(orderService.getOrderById(created.getOrderId()).getDuty()).isFalse();
        assertThat(orderService.getOrderById(created.getOrderId()).getPaid()).isTrue();
        assertThat(clientService.getDebtPaymentHistory(created.getOrderId())).hasSize(2);
        assertThat(clientService.payDebt(created.getOrderId(), firstRequest).remainingAmount())
                .isEqualByComparingTo("10.00");
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(7.0);

        assertThatThrownBy(() -> clientService.payDebt(
                created.getOrderId(),
                debtPayment("1.00", "cash", "partial-payment-3")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("погашен");
    }

    @Test
    void concurrentDebtPaymentsCannotOverpayOrder() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 1.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);
        debt.setClientId(createClient("Конкурентный долг", "+7 999 000-00-06"));
        debt.setDebt_payment_date(businessTime.today().plusDays(1));
        int orderId = orderService.createOrder(debt).getOrderId();
        AtomicInteger requestNumber = new AtomicInteger();

        List<Boolean> results = runConcurrently(2, () -> {
            int number = requestNumber.incrementAndGet();
            try {
                clientService.payDebt(orderId, debtPayment("20.00", "cash", "concurrent-debt-" + number));
                return true;
            } catch (IllegalArgumentException | IllegalStateException expected) {
                return false;
            }
        });

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(clientService.getDebtPaymentHistory(orderId)).hasSize(1);
        assertThat(orderService.getOrderById(orderId).getDebtRemainingAmount()).isZero();
    }

    @Test
    void concurrentDebtRetriesWithSameIdempotencyKeyReturnOnePayment() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 1.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);
        debt.setClientId(createClient("Идемпотентный долг", "+7 999 000-00-12"));
        debt.setDebt_payment_date(businessTime.today().plusDays(1));
        int orderId = orderService.createOrder(debt).getOrderId();
        DebtPaymentRequestDTO request = debtPayment("10.00", "cash", "same-concurrent-key");

        List<Boolean> results = runConcurrently(2, () -> {
            var payment = clientService.payDebt(orderId, request);
            return payment.remainingAmount().compareTo(new BigDecimal("10.00")) == 0;
        });

        assertThat(results).containsExactly(true, true);
        assertThat(clientService.getDebtPaymentHistory(orderId)).hasSize(1);
        assertThat(orderService.getOrderById(orderId).getDebtRemainingAmount())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void debtDateQueriesUseConfiguredBusinessDate() {
        OrderFixture fixture = createOrderFixture(10.0, 1.0);
        int clientId = createClient("Граница даты", "+7 999 000-00-07");
        OrderDTO dueToday = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        dueToday.setDuty(true);
        dueToday.setStatus(true);
        dueToday.setClientId(clientId);
        dueToday.setDebt_payment_date(businessTime.today());
        int todayOrderId = orderService.createOrder(dueToday).getOrderId();

        assertThat(businessTime.zoneId().getId()).isEqualTo("Europe/Moscow");
        assertThat(clientService.getDebtsDueToday())
                .extracting(OrderDTO::getOrderId)
                .containsExactly(todayOrderId);
        assertThat(clientService.getOverdueDebts()).isEmpty();
    }

    @Test
    void debtPaidAfterShiftCloseDoesNotRewriteHistoricalShiftRevenue() {
        OrderFixture fixture = createOrderFixture(10.0, 1.0);
        OrderDTO debt = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        debt.setDuty(true);
        debt.setClientId(createClient("После закрытия", "+7 999 000-00-11"));
        debt.setDebt_payment_date(businessTime.today().plusDays(1));
        int orderId = orderService.createOrder(debt).getOrderId();
        shiftService.closeShift(fixture.shiftId(), BigDecimal.ZERO);

        clientService.payDebt(orderId, debtPayment("20.00", "cash", "after-close-debt"));

        @SuppressWarnings("unchecked")
        var totals = (java.util.Map<String, Object>) shiftService.buildZReport(fixture.shiftId()).get("totals");
        assertThat((Double) totals.get("revenue")).isZero();
        assertThat((Double) totals.get("unpaidAmount")).isEqualTo(20.0);
        assertThat((Integer) totals.get("paidOrdersCount")).isZero();
        assertThat((Integer) totals.get("unpaidOrdersCount")).isEqualTo(1);
    }

    @Test
    void vkCodeExpiresCannotBeReusedAndCanOnlyBeClaimedOnceConcurrently() throws Exception {
        int clientId = createClient("VK одноразовый", "+7 999 000-00-08");
        var expired = vkClientLinkService.createLinkCode(clientId);
        dsl.execute(
                "update sales.client_vk_link_code set expires_at = localtimestamp - interval '1 minute' where id = ?",
                expired.codeId()
        );
        assertThatThrownBy(() -> confirmVk(expired.code(), 8_001L))
                .isInstanceOf(IllegalArgumentException.class);

        var active = vkClientLinkService.createLinkCode(clientId);
        AtomicInteger userNumber = new AtomicInteger();
        List<Boolean> results = runConcurrently(2, () -> {
            try {
                confirmVk(active.code(), 8_100L + userNumber.incrementAndGet());
                return true;
            } catch (RuntimeException expected) {
                return false;
            }
        });

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "client_vk_link")))).isEqualTo(1);
        assertThatThrownBy(() -> confirmVk(active.code(), 8_999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vkRelinkAndUnlinkKeepOneToOneMappingAndIsolateClientHistory() {
        OrderFixture fixture = createOrderFixture(10.0, 1.0);
        int firstClient = createClient("Первый VK", "+7 999 000-00-09");
        int secondClient = createClient("Второй VK", "+7 999 000-00-10");

        OrderDTO firstOrder = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        firstOrder.setClientId(firstClient);
        int firstOrderId = orderService.createOrder(firstOrder).getOrderId();
        OrderDTO secondOrder = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        secondOrder.setClientId(secondClient);
        int secondOrderId = orderService.createOrder(secondOrder).getOrderId();

        long vkUserId = 9_001L;
        confirmVk(vkClientLinkService.createLinkCode(firstClient).code(), vkUserId);
        assertThat(vkClientLinkService.getOrderHistory(vkUserId, 20))
                .extracting(OrderDTO::getOrderId)
                .containsExactly(firstOrderId);

        confirmVk(vkClientLinkService.createLinkCode(secondClient).code(), vkUserId);
        assertThat(vkClientLinkService.getOrderHistory(vkUserId, 20))
                .extracting(OrderDTO::getOrderId)
                .containsExactly(secondOrderId);
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "client_vk_link")))).isEqualTo(1);

        assertThat(vkClientLinkService.unlink(vkUserId)).isTrue();
        assertThatThrownBy(() -> vkClientLinkService.getOrderHistory(vkUserId, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not linked");
        confirmVk(vkClientLinkService.createLinkCode(firstClient).code(), vkUserId);
        assertThat(vkClientLinkService.getOrderHistory(vkUserId, 20))
                .extracting(OrderDTO::getOrderId)
                .containsExactly(firstOrderId);
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "client_vk_link_event")))).isGreaterThanOrEqualTo(5);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void anonymousCannotReadWarehouses() throws Exception {
        mockMvc.perform(get("/warehouses"))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerCanReadWarehouses() throws Exception {
        mockMvc.perform(get("/warehouses")
                        .header("Authorization", bearerToken("WORKER")))
                .andExpect(status().isOk());
    }

    @Test
    void workerCannotCreateWarehouse() throws Exception {
        mockMvc.perform(post("/warehouses")
                        .header("Authorization", bearerToken("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseName": "Worker warehouse"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanCreateWarehouse() throws Exception {
        mockMvc.perform(post("/warehouses")
                        .header("Authorization", bearerToken("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseName": "Owner warehouse"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void insufficientStockDoesNotBlockPaidOrderAndIsReportedAsShortage() {
        OrderFixture fixture = createOrderFixture(2.0, 5.0);
        OrderDTO request = orderRequest(fixture.shiftId(), fixture.dishId(), true);

        OrderDTO created = orderService.createOrder(request);

        assertThat(created.getOrderId()).isPositive();
        assertThat(created.getPaid()).isTrue();
        assertThat(created.getItems()).hasSize(1);
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isZero();

        InventoryShiftReportDTO report = inventoryShiftReportService.getReport(
                fixture.warehouseId(),
                fixture.shiftId()
        );
        InventoryShiftReportRowDTO productRow = report.getRows().stream()
                .filter(row -> fixture.productId() == row.getProductId())
                .findFirst()
                .orElseThrow();

        assertThat(productRow.getSoldQty()).isEqualTo(5.0);
        assertThat(productRow.getExpectedQty()).isEqualTo(-3.0);
        assertThat(productRow.getSystemQty()).isZero();
        assertThat(productRow.getShortageQty()).isEqualTo(3.0);
        assertThat(productRow.getShortageFlag()).isTrue();
    }

    @Test
    void orderAndItemsRollbackTogetherOnTechnicalPersistenceError() {
        OrderFixture fixture = createOrderFixture(10.0, 2.0);
        OrderDTO request = orderRequest(fixture.shiftId(), fixture.dishId(), false);
        OrderDishDTO invalidItem = new OrderDishDTO();
        invalidItem.setDishID(Integer.MAX_VALUE);
        invalidItem.setQty(1);
        request.setItems(List.of(request.getItems().get(0), invalidItem));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class);

        assertThat(dsl.fetchCount(dsl.selectFrom(jooqdata.tables.Order.ORDER))).isZero();
        assertThat(dsl.fetchCount(dsl.selectFrom(jooqdata.tables.Orderdish.ORDERDISH))).isZero();
    }

    @Test
    void paymentConsumesAvailableStockOnlyOnceAndKeepsShortageVisible() {
        OrderFixture fixture = createOrderFixture(2.0, 5.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );

        assertThat(created.getPaid()).isFalse();
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(2.0);

        OrderDTO paid = orderService.updateOrderPayment(created.getOrderId(), "cash", true);
        OrderDTO paidAgain = orderService.updateOrderPayment(created.getOrderId(), "cash", true);

        assertThat(paid.getPaid()).isTrue();
        assertThat(paidAgain.getPaid()).isTrue();
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isZero();

        InventoryShiftReportRowDTO productRow = inventoryShiftReportService
                .getReport(fixture.warehouseId(), fixture.shiftId())
                .getRows()
                .stream()
                .filter(row -> fixture.productId() == row.getProductId())
                .findFirst()
                .orElseThrow();

        assertThat(productRow.getShortageQty()).isEqualTo(3.0);
        assertThat(productRow.getShortageFlag()).isTrue();
    }

    @Test
    void concurrentPaymentRequestsDoNotWriteOffStockTwice() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.updateOrderPayment(created.getOrderId(), "cash", true);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.updateOrderPayment(created.getOrderId(), "cash", true);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getPaid()).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS).getPaid()).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(7.0);
    }

    @Test
    void unpaidOrderCanBeEditedWithServerSideAmountRecalculation() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );

        OrderDishDTO replacementItem = new OrderDishDTO();
        replacementItem.setDishID(fixture.dishId());
        replacementItem.setQty(2);
        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setExpectedVersion(created.getVersion());
        edit.setItems(List.of(replacementItem));
        edit.setType(false);

        OrderDTO updated = orderService.replaceEditableOrder(created.getOrderId(), edit);

        assertThat(updated.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getDishID()).isEqualTo(fixture.dishId());
            assertThat(item.getQty()).isEqualTo(2);
        });
        assertThat(updated.getAmount()).isEqualTo(40.0);
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(10.0);
    }

    @Test
    void unpaidOrderCancellationIsSoftIdempotentAndExcludedFromInventoryReport() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );

        OrderDTO cancelled = orderService.cancelOrder(
                created.getOrderId(),
                "Ошибка кассира",
                created.getVersion()
        );
        OrderDTO cancelledAgain = orderService.cancelOrder(
                created.getOrderId(),
                "Повторный запрос",
                created.getVersion()
        );

        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(cancelled.getCancelReason()).isEqualTo("Ошибка кассира");
        assertThat(cancelledAgain.getCancelledAt()).isEqualTo(cancelled.getCancelledAt());
        assertThat(cancelledAgain.getCancelReason()).isEqualTo("Ошибка кассира");
        assertThat(orderService.getOrders()).isEmpty();
        assertThat(dsl.fetchCount(dsl.selectFrom(jooqdata.tables.Orderdish.ORDERDISH))).isEqualTo(1);

        InventoryShiftReportDTO report = inventoryShiftReportService.getReport(
                fixture.warehouseId(),
                fixture.shiftId()
        );
        assertThat(report.getOrdersCount()).isZero();
        assertThat(report.getRows().stream()
                .filter(row -> fixture.productId() == row.getProductId())
                .findFirst()
                .orElseThrow()
                .getSoldQty()).isZero();
    }

    @Test
    void paidOrderCannotBeEditedButCancellationCreatesOrderedRefundWithoutRestocking() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), true)
        );

        OrderDishDTO replacementItem = new OrderDishDTO();
        replacementItem.setDishID(fixture.dishId());
        replacementItem.setQty(2);
        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setExpectedVersion(created.getVersion());
        edit.setItems(List.of(replacementItem));

        assertThatThrownBy(() -> orderService.replaceEditableOrder(created.getOrderId(), edit))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("возврата");
        OrderDTO cancelled = orderService.cancelOrder(
                created.getOrderId(),
                "Ошибка кассира",
                created.getVersion()
        );

        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(7.0);
        assertThat(dsl.fetch("""
                select event_key, event_type
                from sales.tax_outbox
                where aggregate_id = ?
                order by id
                """, created.getOrderId()))
                .extracting(row -> row.get("event_key", String.class))
                .containsExactly(
                        "order:" + created.getOrderId() + ":paid",
                        "order:" + created.getOrderId() + ":refund"
                );

        assertThat(taxOutboxRelayService.relayPending(10).processed()).isEqualTo(2);
        assertThat(taxJdbcTemplate.queryForList("""
                select operation_type
                from tax.tax_receipt_job
                where order_id = ?
                order by id
                """, String.class, created.getOrderId()))
                .containsExactly("sale", "refund");

        assertThat(taxReceiptDispatchService.dispatchPending(10).sent()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject("""
                select status
                from tax.tax_receipt_job
                where order_id = ? and operation_type = 'refund'
                """, String.class, created.getOrderId())).isEqualTo("pending");
        assertThat(taxReceiptDispatchService.dispatchPending(10).sent()).isEqualTo(1);
        assertThat(taxJdbcTemplate.queryForObject("""
                select status
                from tax.tax_receipt_job
                where order_id = ? and operation_type = 'refund'
                """, String.class, created.getOrderId())).isEqualTo("sent");
    }

    @Test
    void paidCancellationAndRefundOutboxRollbackTogetherWhenCorrectionCannotBeStored() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        dsl.execute("""
                alter table sales.tax_outbox
                add constraint tax_outbox_test_reject_refund_chk
                check (event_type <> 'order_refund')
                """);
        try {
            assertThatThrownBy(() -> orderService.cancelOrder(
                    created.getOrderId(),
                    "Ошибка кассира",
                    created.getVersion()
            )).isInstanceOf(RuntimeException.class);

            assertThat(dsl.fetchValue(
                    "select cancelled_at from sales.\"order\" where orderid = ?",
                    created.getOrderId()
            )).isNull();
            assertThat(dsl.fetchCount(
                    DSL.table(DSL.name("sales", "tax_outbox")),
                    DSL.field(DSL.name("aggregate_id"), Integer.class).eq(created.getOrderId())
            )).isEqualTo(1);
        } finally {
            dsl.execute("""
                    alter table sales.tax_outbox
                    drop constraint if exists tax_outbox_test_reject_refund_chk
                    """);
        }
    }

    @Test
    void closedShiftOrderCannotBeEdited() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );
        dsl.execute(
                "UPDATE sales.shift SET endtime = localtime WHERE id = ?",
                fixture.shiftId()
        );

        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setExpectedVersion(created.getVersion());
        edit.setItems(created.getItems());

        assertThatThrownBy(() -> orderService.replaceEditableOrder(created.getOrderId(), edit))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("закрытой смены");
    }

    @Test
    void emptyShiftClosesWithZeroFinancialTotals() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);

        ShiftRecord closed = shiftService.closeShift(fixture.shiftId(), BigDecimal.ZERO);

        assertThat(closed.getEndtime()).isNotNull();
        assertThat(closed.getIncome()).isZero();
        assertThat(closed.getExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(closed.getProfit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shiftFinancialTotalsIncludeOnlyPaidActiveOrders() {
        OrderFixture fixture = createOrderFixture(20.0, 3.0);
        orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));
        orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), false));
        OrderDTO cancelled = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );
        orderService.cancelOrder(cancelled.getOrderId(), "Дубликат", cancelled.getVersion());

        ShiftRecord closed = shiftService.closeShift(
                fixture.shiftId(),
                new BigDecimal("3.00")
        );

        assertThat(closed.getIncome()).isEqualTo(20.0);
        assertThat(closed.getExpenses()).isEqualByComparingTo("3.00");
        assertThat(closed.getProfit()).isEqualByComparingTo("7.00");

        @SuppressWarnings("unchecked")
        var totals = (java.util.Map<String, Object>) shiftService
                .buildZReport(fixture.shiftId())
                .get("totals");
        assertThat(totals.get("ordersCount")).isEqualTo(2);
        assertThat(totals.get("paidOrdersCount")).isEqualTo(1);
        assertThat(totals.get("unpaidOrdersCount")).isEqualTo(1);
        assertThat((Double) totals.get("revenue")).isEqualTo(20.0);
        assertThat((Double) totals.get("unpaidAmount")).isEqualTo(20.0);
        assertThat((Double) totals.get("cost")).isEqualTo(10.0);
    }

    @Test
    void orderAmountAndClosedShiftReportUseServerFinancialSnapshot() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO request = orderRequest(fixture.shiftId(), fixture.dishId(), true);
        request.setAmount(1.0);

        OrderDTO created = orderService.createOrder(request);
        assertThat(created.getAmount()).isEqualTo(20.0);

        shiftService.closeShift(fixture.shiftId(), BigDecimal.ZERO);
        dsl.execute(
                "UPDATE sales.dish SET price = 200, firstcost = 100 WHERE dishid = ?",
                fixture.dishId()
        );

        @SuppressWarnings("unchecked")
        var totals = (java.util.Map<String, Object>) shiftService
                .buildZReport(fixture.shiftId())
                .get("totals");
        assertThat((Double) totals.get("revenue")).isEqualTo(20.0);
        assertThat((Double) totals.get("cost")).isEqualTo(10.0);
        assertThat((BigDecimal) totals.get("profit")).isEqualByComparingTo("10.00");

        @SuppressWarnings("unchecked")
        var topPositions = (List<java.util.Map<String, Object>>) shiftService
                .buildZReport(fixture.shiftId())
                .get("topPositions");
        assertThat((Double) topPositions.get(0).get("amount")).isEqualTo(20.0);
    }

    @Test
    void nullAndNegativeShiftExpensesAreRejectedWithoutClosingShift() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);

        assertThatThrownBy(() -> shiftService.closeShift(fixture.shiftId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Расходы");
        assertThatThrownBy(() -> shiftService.closeShift(
                fixture.shiftId(),
                new BigDecimal("-0.01")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("отрицательными");

        assertThat(dsl.select(jooqdata.tables.Shift.SHIFT.ENDTIME)
                .from(jooqdata.tables.Shift.SHIFT)
                .where(jooqdata.tables.Shift.SHIFT.ID.eq(fixture.shiftId()))
                .fetchOne(jooqdata.tables.Shift.SHIFT.ENDTIME)).isNull();
    }

    @Test
    void repeatedShiftCloseKeepsOriginalTimeExpensesAndTotals() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));

        ShiftRecord first = shiftService.closeShift(
                fixture.shiftId(),
                new BigDecimal("2.00")
        );
        ShiftRecord repeated = shiftService.closeShift(
                fixture.shiftId(),
                new BigDecimal("100.00")
        );

        assertThat(repeated.getEndtime()).isEqualTo(first.getEndtime());
        assertThat(repeated.getIncome()).isEqualTo(first.getIncome());
        assertThat(repeated.getExpenses()).isEqualByComparingTo(first.getExpenses());
        assertThat(repeated.getProfit()).isEqualByComparingTo(first.getProfit());
    }

    @Test
    void concurrentShiftCloseProducesOneStableResult() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return shiftService.closeShift(fixture.shiftId(), new BigDecimal("2.00"));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return shiftService.closeShift(fixture.shiftId(), new BigDecimal("5.00"));
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ShiftRecord firstResult = first.get(10, TimeUnit.SECONDS);
            ShiftRecord secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(secondResult.getEndtime()).isEqualTo(firstResult.getEndtime());
            assertThat(secondResult.getIncome()).isEqualTo(firstResult.getIncome());
            assertThat(secondResult.getExpenses())
                    .isEqualByComparingTo(firstResult.getExpenses());
            assertThat(secondResult.getProfit()).isEqualByComparingTo(firstResult.getProfit());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void orderCreationRacingWithShiftCloseIsEitherIncludedOrRejected() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var orderFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return orderService.createOrder(
                            orderRequest(fixture.shiftId(), fixture.dishId(), true)
                    );
                } catch (OrderStateConflictException exception) {
                    return null;
                }
            });
            var closeFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return shiftService.closeShift(fixture.shiftId(), BigDecimal.ZERO);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            OrderDTO created = orderFuture.get(10, TimeUnit.SECONDS);
            ShiftRecord closed = closeFuture.get(10, TimeUnit.SECONDS);

            if (created == null) {
                assertThat(closed.getIncome()).isZero();
                assertThat(dsl.fetchCount(
                        dsl.selectFrom(jooqdata.tables.Order.ORDER)
                                .where(jooqdata.tables.Order.ORDER.SHIFTID.eq(fixture.shiftId()))
                )).isZero();
            } else {
                assertThat(closed.getIncome()).isEqualTo(20.0);
                assertThat(dsl.fetchCount(
                        dsl.selectFrom(jooqdata.tables.Order.ORDER)
                                .where(jooqdata.tables.Order.ORDER.SHIFTID.eq(fixture.shiftId()))
                )).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void zReportAndInventorySnapshotRemainAvailableAfterShiftClose() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        orderService.createOrder(orderRequest(fixture.shiftId(), fixture.dishId(), true));

        var openReport = shiftService.buildZReport(fixture.shiftId());
        assertThat(openReport.get("endTime")).isNull();

        shiftService.closeShift(fixture.shiftId(), BigDecimal.ZERO);

        var closedReport = shiftService.buildZReport(fixture.shiftId());
        assertThat(closedReport.get("endTime")).isNotNull();
        @SuppressWarnings("unchecked")
        var openTotals = (java.util.Map<String, Object>) openReport.get("totals");
        @SuppressWarnings("unchecked")
        var closedTotals = (java.util.Map<String, Object>) closedReport.get("totals");
        assertThat(closedTotals.get("revenue")).isEqualTo(openTotals.get("revenue"));
        assertThat(closedTotals.get("cost")).isEqualTo(openTotals.get("cost"));

        InventoryShiftReportDTO inventoryReport = inventoryShiftReportService.getReport(
                fixture.warehouseId(),
                fixture.shiftId()
        );
        assertThat(inventoryReport.getSnapshotAvailable()).isTrue();
        assertThat(inventoryReport.getShiftEndTime()).isNotNull();
        assertThat(inventoryReport.getOrdersCount()).isEqualTo(1);
    }

    @Test
    void closedShiftRejectsNewOrdersAndPaymentOfExistingOrders() {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO unpaid = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );
        shiftService.closeShift(fixture.shiftId(), BigDecimal.ZERO);

        assertThatThrownBy(() -> orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        ))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("закрыта");
        assertThatThrownBy(() -> orderService.updateOrderPayment(
                unpaid.getOrderId(),
                "cash",
                true
        ))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("закрыта");
    }

    @Test
    void shiftCanOpenWithMultipleEmployeesAndRejectsAnEmployeeAlreadyOnShift() {
        int firstPersonId = createPerson("Первый сотрудник");
        int secondPersonId = createPerson("Второй сотрудник");

        ShiftDTO opened = shiftService.createShift(
                shiftRequest(firstPersonId, List.of(firstPersonId, secondPersonId))
        );

        assertThat(opened.personIds).containsExactly(firstPersonId, secondPersonId);
        assertThatThrownBy(() -> shiftService.createShift(
                shiftRequest(secondPersonId, List.of(secondPersonId))
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("открытая смена");
    }

    @Test
    void concurrentShiftOpeningAllowsOnlyOneShiftForTheSameEmployee() throws Exception {
        int personId = createPerson("Сотрудник параллельной смены");
        ShiftDTO request = shiftRequest(personId, List.of(personId));

        List<Boolean> results = runConcurrently(2, () -> {
            try {
                shiftService.createShift(request);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        });

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(dsl.fetchCount(
                dsl.selectFrom(jooqdata.tables.Shift.SHIFT)
                        .where(jooqdata.tables.Shift.SHIFT.ENDTIME.isNull())
                        .and(jooqdata.tables.Shift.SHIFT.PERSONCODE.eq(personId))
        )).isEqualTo(1);
    }

    @Test
    void shiftTeamCanChangeButFinancialAndClosingFieldsCannotBeInjected() {
        int firstPersonId = createPerson("Первый участник");
        int secondPersonId = createPerson("Новый участник");
        ShiftDTO opened = shiftService.createShift(
                shiftRequest(firstPersonId, List.of(firstPersonId))
        );

        ShiftDTO update = shiftRequest(secondPersonId, List.of(secondPersonId));
        update.endTime = java.time.LocalTime.NOON;
        update.income = 999_999.0;
        update.expenses = new BigDecimal("123");
        update.profit = new BigDecimal("456");

        ShiftDTO updated = shiftService.updateShift(opened.shiftId, update);

        assertThat(updated.personIds).containsExactly(secondPersonId);
        assertThat(updated.endTime).isNull();
        assertThat(updated.income).isZero();
        assertThat(updated.expenses).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updated.profit).isEqualByComparingTo(BigDecimal.ZERO);

        shiftService.closeShift(opened.shiftId, BigDecimal.ZERO);
        assertThatThrownBy(() -> shiftService.updateShift(opened.shiftId, update))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Закрытую смену");
    }

    @Test
    void staleOrderVersionIsRejectedAndHttpContractReturnsConflict() throws Exception {
        OrderFixture fixture = createOrderFixture(10.0, 3.0);
        OrderDTO created = orderService.createOrder(
                orderRequest(fixture.shiftId(), fixture.dishId(), false)
        );

        mockMvc.perform(put("/api/orders/{orderId}", created.getOrderId())
                        .header("Authorization", bearerToken("WORKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 99,
                                  "items": [
                                    {
                                      "dishID": %d,
                                      "qty": 1
                                    }
                                  ]
                                }
                                """.formatted(fixture.dishId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));

        mockMvc.perform(delete("/api/orders/{orderId}", created.getOrderId())
                        .header("Authorization", bearerToken("WORKER"))
                        .queryParam("reason", "Ошибочный заказ")
                        .queryParam("expectedVersion", String.valueOf(created.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("Ошибочный заказ"));
    }

    @Test
    void concurrentProductAdjustmentsDoNotLoseUpdates() throws Exception {
        InventoryFixture fixture = createInventoryFixture(0.0, true);

        List<Boolean> results = runConcurrently(
                8,
                () -> wareHouseService.adjustQuantity(fixture.firstWarehouseId(), fixture.productId(), 1.0)
        );

        assertThat(results).allMatch(Boolean.TRUE::equals);
        assertThat(wareHouseService.getAvailableQuantity(fixture.firstWarehouseId(), fixture.productId()))
                .isEqualTo(8.0);
    }

    @Test
    void concurrentPreparationAdjustmentsDoNotLoseUpdates() throws Exception {
        InventoryFixture fixture = createInventoryFixture(0.0, true);
        Integer preparationId = returningId(
                """
                INSERT INTO sales.preparation (preparationname, output_weight)
                VALUES (?, 1)
                RETURNING preparationid
                """,
                "Rice base"
        );
        dsl.execute(
                """
                INSERT INTO sales.preparationwarehouse (warehouseid, preparationid, quantity)
                VALUES (?, ?, 0)
                """,
                fixture.firstWarehouseId(),
                preparationId
        );

        List<Boolean> results = runConcurrently(
                8,
                () -> wareHouseService.adjustPreparationQuantity(
                        fixture.firstWarehouseId(),
                        preparationId,
                        1.0
                )
        );

        assertThat(results).allMatch(Boolean.TRUE::equals);
        assertThat(wareHouseService.getAvailablePreparationQuantity(
                fixture.firstWarehouseId(),
                preparationId
        )).isEqualTo(8.0);
    }

    @Test
    void concurrentTransfersCannotSpendTheSameSourceStockTwice() throws Exception {
        InventoryFixture fixture = createInventoryFixture(10.0, true);

        List<Boolean> results = runConcurrently(
                2,
                () -> wareHouseService.moveProduct(
                        fixture.firstWarehouseId(),
                        fixture.secondWarehouseId(),
                        fixture.productId(),
                        7.0
                )
        );

        assertThat(results.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1);
        assertThat(wareHouseService.getAvailableQuantity(fixture.firstWarehouseId(), fixture.productId()))
                .isEqualTo(3.0);
        assertThat(wareHouseService.getAvailableQuantity(fixture.secondWarehouseId(), fixture.productId()))
                .isEqualTo(7.0);
    }

    @Test
    void manualWriteoffRejectsInsufficientStockWithoutPartialMutation() {
        InventoryFixture fixture = createInventoryFixture(4.0, false);
        dsl.execute(
                """
                INSERT INTO sales.productwarehouse (warehouseid, productid, quantity)
                VALUES (?, ?, 6)
                """,
                fixture.firstWarehouseId(),
                fixture.productId()
        );

        boolean adjusted = wareHouseService.adjustQuantity(
                fixture.firstWarehouseId(),
                fixture.productId(),
                -11.0
        );

        assertThat(adjusted).isFalse();
        assertThat(wareHouseService.getAvailableQuantity(fixture.firstWarehouseId(), fixture.productId()))
                .isEqualTo(10.0);
    }

    @Test
    void insufficientMovementWriteoffCreatesNoDocumentAndKeepsStock() {
        InventoryFixture fixture = createInventoryFixture(5.0, false);
        Integer supplierId = returningId(
                """
                INSERT INTO sales.supplier (suppliername, communication)
                VALUES (?, '')
                RETURNING supplierid
                """,
                "Inventory supplier"
        );
        MovementRequestDTO request = new MovementRequestDTO();
        request.setDocType("writeoff");
        request.setFromWarehouseId(fixture.firstWarehouseId());
        request.setProductId(fixture.productId());
        request.setQuantity(6.0);
        request.setSupplierId(supplierId);

        MovementDTO result = movementService.createMovement(request);

        assertThat(result).isNull();
        assertThat(wareHouseService.getAvailableQuantity(fixture.firstWarehouseId(), fixture.productId()))
                .isEqualTo(5.0);
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "inventory_documents")))).isZero();
        assertThat(dsl.fetchCount(DSL.table(DSL.name("sales", "stock_movements")))).isZero();
    }

    @Test
    void oppositeTransfersUseStableLockOrderAndPreserveTotalStock() throws Exception {
        InventoryFixture fixture = createInventoryFixture(10.0, true);
        dsl.execute(
                """
                UPDATE sales.productwarehouse
                SET quantity = 10
                WHERE warehouseid = ? AND productid = ?
                """,
                fixture.secondWarehouseId(),
                fixture.productId()
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var forward = executor.submit(() -> {
                ready.countDown();
                start.await();
                return wareHouseService.moveProduct(
                        fixture.firstWarehouseId(),
                        fixture.secondWarehouseId(),
                        fixture.productId(),
                        3.0
                );
            });
            var backward = executor.submit(() -> {
                ready.countDown();
                start.await();
                return wareHouseService.moveProduct(
                        fixture.secondWarehouseId(),
                        fixture.firstWarehouseId(),
                        fixture.productId(),
                        4.0
                );
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(forward.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(backward.get(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        double first = wareHouseService.getAvailableQuantity(
                fixture.firstWarehouseId(),
                fixture.productId()
        );
        double second = wareHouseService.getAvailableQuantity(
                fixture.secondWarehouseId(),
                fixture.productId()
        );
        assertThat(first).isEqualTo(11.0);
        assertThat(second).isEqualTo(9.0);
        assertThat(first + second).isEqualTo(20.0);
    }

    @Test
    void nonFiniteWarehouseMutationsAreRejected() {
        InventoryFixture fixture = createInventoryFixture(5.0, true);
        ProductWarehouseDTO invalidReceipt = new ProductWarehouseDTO();
        invalidReceipt.setProductId(fixture.productId());
        invalidReceipt.setQuantity(Double.NEGATIVE_INFINITY);

        assertThat(wareHouseService.adjustQuantity(
                fixture.firstWarehouseId(),
                fixture.productId(),
                Double.NaN
        )).isFalse();
        assertThat(wareHouseService.moveProduct(
                fixture.firstWarehouseId(),
                fixture.secondWarehouseId(),
                fixture.productId(),
                Double.POSITIVE_INFINITY
        )).isFalse();
        assertThat(wareHouseService.consumeAvailableQuantity(
                fixture.firstWarehouseId(),
                fixture.productId(),
                Double.NaN
        )).isZero();
        assertThatThrownBy(() -> wareHouseService.addProductsToWarehouse(
                fixture.firstWarehouseId(),
                List.of(invalidReceipt)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(wareHouseService.getAvailableQuantity(
                fixture.firstWarehouseId(),
                fixture.productId()
        )).isEqualTo(5.0);
    }

    private String bearerToken(String role) {
        return "Bearer " + jwtService.generateToken(role.toLowerCase(), 1, 1, role);
    }

    private OrderFixture createOrderFixture(double stockQty, double requiredQty) {
        Integer warehouseId = returningId(
                """
                INSERT INTO sales.warehouse (warehousename, is_main)
                VALUES (?, true)
                RETURNING warehouseid
                """,
                "Main warehouse"
        );
        Integer productId = returningId(
                """
                INSERT INTO sales.product (
                    productname,
                    productprice,
                    waste,
                    isfavourite,
                    unit,
                    base_unit,
                    unit_factor
                )
                VALUES (?, 10, 0, false, 'g', 'g', 1)
                RETURNING productid
                """,
                "Rice"
        );
        Integer dishId = returningId(
                """
                INSERT INTO sales.dish (
                    dishname,
                    weight,
                    firstcost,
                    price,
                    techproductid
                )
                VALUES (?, 100, 10, 20, 1)
                RETURNING dishid
                """,
                "Test roll"
        );
        dsl.execute(
                """
                INSERT INTO sales.techproduct ("DishId", productid, waste, weight)
                VALUES (?, ?, 0, ?)
                """,
                dishId,
                productId,
                requiredQty
        );
        dsl.execute(
                """
                INSERT INTO sales.productwarehouse (warehouseid, productid, quantity)
                VALUES (?, ?, ?)
                """,
                warehouseId,
                productId,
                stockQty
        );
        Integer shiftId = returningId(
                """
                INSERT INTO sales.shift (data, starttime)
                VALUES (current_date, localtime - interval '1 minute')
                RETURNING id
                """
        );
        shiftInventorySnapshotService.captureSnapshotForShift(shiftId);

        return new OrderFixture(warehouseId, productId, dishId, shiftId);
    }

    private OrderDTO orderRequest(int shiftId, int dishId, boolean paid) {
        OrderDishDTO item = new OrderDishDTO();
        item.setDishID(dishId);
        item.setQty(1);

        OrderDTO order = new OrderDTO();
        order.setShiftId(shiftId);
        order.setAmount(20.0);
        order.setStatus(false);
        order.setType(false);
        order.setDuty(false);
        order.setTime(30.0);
        order.setPaid(paid);
        order.setPaymentType(paid ? "cash" : "unpaid");
        order.setItems(List.of(item));
        return order;
    }

    private int returningId(String sql, Object... bindings) {
        var record = dsl.fetchOne(sql, bindings);
        if (record == null) {
            throw new IllegalStateException("Fixture insert did not return an ID");
        }
        return record.get(0, Integer.class);
    }

    private int createPerson(String name) {
        return returningId(
                """
                INSERT INTO sales.person (name, salaryperday, archived)
                VALUES (?, 0, false)
                RETURNING personid
                """,
                name
        );
    }

    private int createPreparation(String name) {
        PreparationDTO dto = new PreparationDTO();
        dto.setPreparationName(name);
        dto.setOutputWeight(100.0);
        return preparationService.create(dto).getPreparationId();
    }

    private TechProductDTO preparationIngredient(int ownerPreparationId, int ingredientPreparationId) {
        TechProductDTO dto = new TechProductDTO();
        dto.setPreparationId(ownerPreparationId);
        dto.setIngredientPreparationId(ingredientPreparationId);
        dto.setWeight(50.0);
        dto.setWaste(0.0);
        return dto;
    }

    private ProductDTO validProduct(String name) {
        ProductDTO dto = new ProductDTO();
        dto.setProductName(name);
        dto.setProductPrice(new BigDecimal("100.00"));
        dto.setWaste(0.0);
        dto.setFavorite(false);
        dto.setUnit("kg");
        dto.setBaseUnit("g");
        dto.setUnitFactor(new BigDecimal("1000"));
        return dto;
    }

    private int createStandaloneProduct(String name) {
        return productService.createProduct(validProduct(name)).getProductId();
    }

    private DishDTO validDish(String name) {
        DishDTO dto = new DishDTO();
        dto.setDishName(name);
        dto.setWeight(200.0);
        dto.setFirstCost(0.0);
        dto.setPrice(300.0);
        return dto;
    }

    private int createPersonWithRate(String name, String dailyRate) {
        return returningId(
                """
                INSERT INTO sales.person (name, salaryperday, archived)
                VALUES (?, ?, false)
                RETURNING personid
                """,
                name,
                new BigDecimal(dailyRate)
        );
    }

    private int createAccount(int personId, String username, String role) {
        return returningId(
                """
                INSERT INTO sales.user_account (personid, username, password_hash, role, is_active)
                VALUES (?, ?, 'test-hash', ?, true)
                RETURNING id
                """,
                personId,
                username,
                role
        );
    }

    private void closeSalaryShift(int personId) {
        ShiftDTO request = shiftRequest(personId, List.of(personId));
        ShiftDTO created = shiftService.createShift(request);
        shiftService.closeShift(created.shiftId, BigDecimal.ZERO);
    }

    private SalaryPaymentRequestDTO salaryPayment(String amount, String idempotencyKey) {
        SalaryPaymentRequestDTO request = new SalaryPaymentRequestDTO();
        request.setAmount(new BigDecimal(amount));
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private boolean runConcurrentSalaryPayment(
            int personId,
            String username,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            payrollService.createPayment(personId, salaryPayment("100.00", idempotencyKey), username);
            return true;
        } catch (org.springframework.web.server.ResponseStatusException error) {
            return false;
        }
    }

    private int createClient(String fullName, String number) {
        ClientDTO client = new ClientDTO();
        client.setFullName(fullName);
        client.setNumber(number);
        return clientService.createClient(client).getClientId();
    }

    private DebtPaymentRequestDTO debtPayment(String amount, String paymentType, String idempotencyKey) {
        DebtPaymentRequestDTO request = new DebtPaymentRequestDTO();
        request.setAmount(new BigDecimal(amount));
        request.setPaymentType(paymentType);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private void confirmVk(String code, long vkUserId) {
        VkBotLinkConfirmRequestDTO request = new VkBotLinkConfirmRequestDTO();
        request.setVkUserId(vkUserId);
        request.setVkDomain("id" + vkUserId);
        request.setCode(code);
        vkClientLinkService.confirmLink(request);
    }

    private ShiftDTO shiftRequest(int mainPersonId, List<Integer> personIds) {
        ShiftDTO shift = new ShiftDTO();
        shift.data = java.time.LocalDate.now();
        shift.startTime = java.time.LocalTime.now();
        shift.personCode = mainPersonId;
        shift.personIds = personIds;
        return shift;
    }

    private InventoryFixture createInventoryFixture(double firstWarehouseQuantity, boolean createSecondWarehouseRow) {
        Integer firstWarehouseId = returningId(
                """
                INSERT INTO sales.warehouse (warehousename, is_main)
                VALUES (?, true)
                RETURNING warehouseid
                """,
                "First warehouse"
        );
        Integer secondWarehouseId = returningId(
                """
                INSERT INTO sales.warehouse (warehousename, is_main)
                VALUES (?, false)
                RETURNING warehouseid
                """,
                "Second warehouse"
        );
        Integer productId = returningId(
                """
                INSERT INTO sales.product (
                    productname,
                    productprice,
                    waste,
                    isfavourite,
                    unit,
                    base_unit,
                    unit_factor
                )
                VALUES (?, 10, 0, false, 'g', 'g', 1)
                RETURNING productid
                """,
                "Inventory rice"
        );
        dsl.execute(
                """
                INSERT INTO sales.productwarehouse (warehouseid, productid, quantity)
                VALUES (?, ?, ?)
                """,
                firstWarehouseId,
                productId,
                firstWarehouseQuantity
        );
        if (createSecondWarehouseRow) {
            dsl.execute(
                    """
                    INSERT INTO sales.productwarehouse (warehouseid, productid, quantity)
                    VALUES (?, ?, 0)
                    """,
                    secondWarehouseId,
                    productId
            );
        }
        return new InventoryFixture(firstWarehouseId, secondWarehouseId, productId);
    }

    private static HttpServer startPartnerServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/receipts", CafehelpApplicationTests::handlePartnerRequest);
            server.start();
            return server;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handlePartnerRequest(HttpExchange exchange) {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            PARTNER_IDEMPOTENCY_KEYS.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            String mode = PARTNER_MODE.get();
            if ("TIMEOUT".equals(mode)) {
                Thread.sleep(1_500);
            }

            int status = "FAIL_500".equals(mode) ? 500 : 200;
            String response = switch (mode) {
                case "FAIL_500" -> "{\"error\":\"temporary\"}";
                case "INVALID_200" -> "not-json";
                default -> "{\"receiptId\":\"fake-receipt-1\",\"receiptUrl\":\"https://example.test/receipt/1\"}";
            };
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } catch (Exception ignored) {
            // A timeout test intentionally closes the client connection before the fake server responds.
        }
    }

    private List<Boolean> runConcurrently(int taskCount, Callable<Boolean> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger threadNumber = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(
                taskCount,
                runnable -> new Thread(runnable, "inventory-test-" + threadNumber.incrementAndGet())
        );
        try {
            var futures = java.util.stream.IntStream.range(0, taskCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return operation.call();
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            java.util.ArrayList<Boolean> results = new java.util.ArrayList<>();
            for (var future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record OrderFixture(int warehouseId, int productId, int dishId, int shiftId) {
    }

    private record InventoryFixture(int firstWarehouseId, int secondWarehouseId, int productId) {
    }

}
