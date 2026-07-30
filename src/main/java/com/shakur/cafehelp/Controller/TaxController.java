package com.shakur.cafehelp.Controller;

import com.shakur.cafehelp.Service.TaxOutboxBackfillService;
import com.shakur.cafehelp.Service.TaxAdminQueryService;
import com.shakur.cafehelp.Service.TaxOutboxRelayService;
import com.shakur.cafehelp.Service.TaxReceiptDispatchService;
import com.shakur.cafehelp.Service.TaxRetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tax")
public class TaxController {

    private final TaxOutboxBackfillService backfillService;
    private final TaxOutboxRelayService relayService;
    private final TaxAdminQueryService taxAdminQueryService;
    private final TaxRetryService taxRetryService;
    private final TaxReceiptDispatchService taxReceiptDispatchService;

    public TaxController(
            TaxOutboxBackfillService backfillService,
            TaxOutboxRelayService relayService,
            TaxAdminQueryService taxAdminQueryService,
            TaxRetryService taxRetryService,
            TaxReceiptDispatchService taxReceiptDispatchService
    ) {
        this.backfillService = backfillService;
        this.relayService = relayService;
        this.taxAdminQueryService = taxAdminQueryService;
        this.taxRetryService = taxRetryService;
        this.taxReceiptDispatchService = taxReceiptDispatchService;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(taxAdminQueryService.getOverview(limit));
    }

    @GetMapping("/receipt-details")
    public ResponseEntity<?> receiptDetails(@RequestParam Integer orderId) {
        if (orderId == null || orderId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Неверный orderId"));
        }
        return ResponseEntity.ok(taxAdminQueryService.getReceiptDetails(orderId));
    }

    @PostMapping("/backfill-existing")
    public ResponseEntity<?> backfillExisting(@RequestBody(required = false) BackfillRequest request) {
        BackfillRequest req = request != null ? request : new BackfillRequest();
        TaxOutboxBackfillService.BackfillResult result = backfillService.enqueueExistingPaidOrders(
                req.getFromDate(),
                req.getToDate(),
                req.getLimit()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/relay-pending")
    public ResponseEntity<?> relayPending(@RequestBody(required = false) RelayRequest request) {
        int limit = request != null && request.getLimit() != null ? request.getLimit() : 500;
        return ResponseEntity.ok(relayService.relayPending(limit));
    }

    @PostMapping("/send-existing")
    public ResponseEntity<?> sendExisting(@RequestBody(required = false) SendExistingRequest request) {
        SendExistingRequest req = request != null ? request : new SendExistingRequest();

        TaxOutboxBackfillService.BackfillResult backfillResult = backfillService.enqueueExistingPaidOrders(
                req.getFromDate(),
                req.getToDate(),
                req.getBackfillLimit()
        );
        TaxOutboxRelayService.RelayResult relayResult = relayService.relayPending(
                req.getRelayLimit() != null ? req.getRelayLimit() : 500
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("backfill", backfillResult);
        response.put("relay", relayResult);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-one")
    public ResponseEntity<?> sendOne(@RequestBody(required = false) SendOneRequest request) {
        SendOneRequest req = request != null ? request : new SendOneRequest();
        if (req.getOrderId() == null || req.getOrderId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Нужно передать корректный orderId"));
        }

        boolean forcePending = req.getForcePending() == null || req.getForcePending();
        TaxOutboxBackfillService.SingleOrderEnqueueResult enqueueResult =
                backfillService.enqueueSinglePaidOrder(req.getOrderId(), forcePending, req.getReceiptDate());
        TaxOutboxRelayService.RelayOneResult relayResult = relayService.relayOutboxById(enqueueResult.outboxId());
        TaxReceiptDispatchService.DispatchResult dispatchResult =
                taxReceiptDispatchService.dispatchPendingByOrder(req.getOrderId(), req.getDispatchLimit());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enqueue", enqueueResult);
        response.put("relay", relayResult);
        response.put("dispatch", dispatchResult);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-jobs")
    public ResponseEntity<?> sendJobs(@RequestBody(required = false) SendJobsRequest request) {
        SendJobsRequest req = request != null ? request : new SendJobsRequest();
        if (req.getOrderId() != null && req.getOrderId() > 0) {
            return ResponseEntity.ok(taxReceiptDispatchService.dispatchPendingByOrder(req.getOrderId(), req.getLimit()));
        }
        return ResponseEntity.ok(taxReceiptDispatchService.dispatchPending(req.getLimit()));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<?> retryFailed(@RequestBody(required = false) RetryFailedRequest request) {
        RetryFailedRequest req = request != null ? request : new RetryFailedRequest();
        return ResponseEntity.ok(taxRetryService.retryFailedAndDeadLetter(req.getOutboxLimit(), req.getJobsLimit()));
    }

    public static class BackfillRequest {
        private LocalDate fromDate;
        private LocalDate toDate;
        private Integer limit;

        public LocalDate getFromDate() {
            return fromDate;
        }

        public void setFromDate(LocalDate fromDate) {
            this.fromDate = fromDate;
        }

        public LocalDate getToDate() {
            return toDate;
        }

        public void setToDate(LocalDate toDate) {
            this.toDate = toDate;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }

    public static class RelayRequest {
        private Integer limit;

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }

    public static class SendExistingRequest {
        private LocalDate fromDate;
        private LocalDate toDate;
        private Integer backfillLimit;
        private Integer relayLimit;

        public LocalDate getFromDate() {
            return fromDate;
        }

        public void setFromDate(LocalDate fromDate) {
            this.fromDate = fromDate;
        }

        public LocalDate getToDate() {
            return toDate;
        }

        public void setToDate(LocalDate toDate) {
            this.toDate = toDate;
        }

        public Integer getBackfillLimit() {
            return backfillLimit;
        }

        public void setBackfillLimit(Integer backfillLimit) {
            this.backfillLimit = backfillLimit;
        }

        public Integer getRelayLimit() {
            return relayLimit;
        }

        public void setRelayLimit(Integer relayLimit) {
            this.relayLimit = relayLimit;
        }
    }

    public static class RetryFailedRequest {
        private Integer outboxLimit;
        private Integer jobsLimit;

        public Integer getOutboxLimit() {
            return outboxLimit;
        }

        public void setOutboxLimit(Integer outboxLimit) {
            this.outboxLimit = outboxLimit;
        }

        public Integer getJobsLimit() {
            return jobsLimit;
        }

        public void setJobsLimit(Integer jobsLimit) {
            this.jobsLimit = jobsLimit;
        }
    }

    public static class SendOneRequest {
        private Integer orderId;
        private Boolean forcePending;
        private LocalDate receiptDate;
        private Integer dispatchLimit;

        public Integer getOrderId() {
            return orderId;
        }

        public void setOrderId(Integer orderId) {
            this.orderId = orderId;
        }

        public Boolean getForcePending() {
            return forcePending;
        }

        public void setForcePending(Boolean forcePending) {
            this.forcePending = forcePending;
        }

        public LocalDate getReceiptDate() {
            return receiptDate;
        }

        public void setReceiptDate(LocalDate receiptDate) {
            this.receiptDate = receiptDate;
        }

        public Integer getDispatchLimit() {
            return dispatchLimit;
        }

        public void setDispatchLimit(Integer dispatchLimit) {
            this.dispatchLimit = dispatchLimit;
        }
    }

    public static class SendJobsRequest {
        private Integer limit;
        private Integer orderId;

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public Integer getOrderId() {
            return orderId;
        }

        public void setOrderId(Integer orderId) {
            this.orderId = orderId;
        }
    }
}
