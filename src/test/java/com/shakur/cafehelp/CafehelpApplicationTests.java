package com.shakur.cafehelp;

import com.shakur.cafehelp.DTO.InventoryShiftReportDTO;
import com.shakur.cafehelp.DTO.InventoryShiftReportRowDTO;
import com.shakur.cafehelp.DTO.MovementDTO;
import com.shakur.cafehelp.DTO.MovementRequestDTO;
import com.shakur.cafehelp.DTO.OrderDTO;
import com.shakur.cafehelp.DTO.OrderDishDTO;
import com.shakur.cafehelp.DTO.OrderEditRequestDTO;
import com.shakur.cafehelp.DTO.ProductWarehouseDTO;
import com.shakur.cafehelp.DTO.ShiftDTO;
import com.shakur.cafehelp.Service.InventoryShiftReportService;
import com.shakur.cafehelp.Service.MovementService;
import com.shakur.cafehelp.Service.OrderService;
import com.shakur.cafehelp.Service.ShiftService;
import com.shakur.cafehelp.Service.ShiftInventorySnapshotService;
import com.shakur.cafehelp.Service.WareHouseService;
import com.shakur.cafehelp.exception.OrderStateConflictException;
import com.shakur.cafehelp.security.JwtService;
import jooqdata.tables.records.ShiftRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @DynamicPropertySource
    static void configureDatabases(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MAIN_DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", MAIN_DATABASE::getUsername);
        registry.add("spring.datasource.password", MAIN_DATABASE::getPassword);
        registry.add("tax.datasource.url", TAX_DATABASE::getJdbcUrl);
        registry.add("tax.datasource.username", TAX_DATABASE::getUsername);
        registry.add("tax.datasource.password", TAX_DATABASE::getPassword);
    }

    @BeforeEach
    void resetBusinessData() {
        dsl.execute("""
                TRUNCATE TABLE
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
                    sales.shift
                RESTART IDENTITY CASCADE
                """);
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
    void paidOrderCannotBeEditedOrCancelledWithoutRefundFlow() {
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
        assertThatThrownBy(() -> orderService.cancelOrder(
                created.getOrderId(),
                "Нельзя",
                created.getVersion()
        ))
                .isInstanceOf(OrderStateConflictException.class)
                .hasMessageContaining("возврата");
        assertThat(wareHouseService.getAvailableQuantity(fixture.warehouseId(), fixture.productId()))
                .isEqualTo(7.0);
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
                INSERT INTO sales.person (name, salary, numdays, salaryperday, archived)
                VALUES (?, 0, 0, 0, false)
                RETURNING personid
                """,
                name
        );
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
