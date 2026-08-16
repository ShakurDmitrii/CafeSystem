package com.shakur.cafehelp.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shakur.cafehelp.config.BusinessTimeProvider;

@Component
@ConditionalOnProperty(prefix = "app.tax-worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaxOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(TaxOutboxWorker.class);

    private final TaxOutboxRelayService relayService;
    private final TaxReceiptDispatchService dispatchService;
    private final TaxReconciliationService reconciliationService;
    private final BusinessTimeProvider businessTime;
    private final int relayBatchSize;
    private final int dispatchBatchSize;
    private final int reconciliationBatchSize;
    private final int reconciliationLookbackDays;

    public TaxOutboxWorker(
            TaxOutboxRelayService relayService,
            TaxReceiptDispatchService dispatchService,
            TaxReconciliationService reconciliationService,
            BusinessTimeProvider businessTime,
            @Value("${app.tax-worker.relay-batch-size:100}") int relayBatchSize,
            @Value("${app.tax-worker.dispatch-batch-size:50}") int dispatchBatchSize,
            @Value("${app.tax-worker.reconciliation-batch-size:1000}") int reconciliationBatchSize,
            @Value("${app.tax-worker.reconciliation-lookback-days:1}") int reconciliationLookbackDays
    ) {
        this.relayService = relayService;
        this.dispatchService = dispatchService;
        this.reconciliationService = reconciliationService;
        this.businessTime = businessTime;
        this.relayBatchSize = Math.max(1, relayBatchSize);
        this.dispatchBatchSize = Math.max(1, dispatchBatchSize);
        this.reconciliationBatchSize = Math.max(1, reconciliationBatchSize);
        this.reconciliationLookbackDays = Math.max(0, Math.min(reconciliationLookbackDays, 31));
    }

    @Scheduled(
            initialDelayString = "${app.tax-worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.tax-worker.relay-delay-ms:10000}"
    )
    public void relayOutbox() {
        try {
            TaxOutboxRelayService.RelayResult result = relayService.relayPending(relayBatchSize);
            if (result.claimed() > 0) {
                log.info(
                        "Tax outbox relay: claimed={}, processed={}, failed={}",
                        result.claimed(),
                        result.processed(),
                        result.failed()
                );
            }
        } catch (Exception exception) {
            log.warn("Tax outbox relay is temporarily unavailable: {}", safeMessage(exception));
        }
    }

    @Scheduled(
            initialDelayString = "${app.tax-worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.tax-worker.dispatch-delay-ms:15000}"
    )
    public void dispatchReceipts() {
        try {
            TaxReceiptDispatchService.DispatchResult result = dispatchService.dispatchPending(dispatchBatchSize);
            if (result.claimed() > 0) {
                log.info(
                        "Tax receipt dispatch: claimed={}, sent={}, failed={}, manualRequired={}",
                        result.claimed(),
                        result.sent(),
                        result.failed(),
                        result.manualRequired()
                );
            }
        } catch (Exception exception) {
            log.warn("Tax receipt dispatch is temporarily unavailable: {}", safeMessage(exception));
        }
    }

    @Scheduled(
            initialDelayString = "${app.tax-worker.reconciliation-initial-delay-ms:60000}",
            fixedDelayString = "${app.tax-worker.reconciliation-delay-ms:3600000}"
    )
    public void reconcileReceipts() {
        for (int daysAgo = reconciliationLookbackDays; daysAgo >= 0; daysAgo--) {
            try {
                TaxReconciliationService.ReconcileResult result = reconciliationService.reconcile(
                        businessTime.today().minusDays(daysAgo),
                        reconciliationBatchSize
                );
                if (result.outboxRestored() > 0
                        || result.outboxRequeued() > 0
                        || result.unresolvedGaps() > 0) {
                    log.warn(
                            "Tax reconciliation: date={}, restored={}, requeued={}, unresolved={}",
                            result.businessDate(),
                            result.outboxRestored(),
                            result.outboxRequeued(),
                            result.unresolvedGaps()
                    );
                }
            } catch (Exception exception) {
                log.warn("Tax reconciliation is temporarily unavailable: {}", safeMessage(exception));
                return;
            }
        }
    }

    private String safeMessage(Exception exception) {
        String name = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return name;
        String normalized = message.replaceAll("(?i)(api[-_ ]?key|token|authorization)\\s*[:=]\\s*[^,;\\s]+", "$1=***");
        return name + ": " + normalized.substring(0, Math.min(300, normalized.length()));
    }
}
