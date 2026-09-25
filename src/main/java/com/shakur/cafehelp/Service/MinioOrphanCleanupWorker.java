package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.config.MinioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MinioOrphanCleanupWorker {

    private static final Logger log = LoggerFactory.getLogger(MinioOrphanCleanupWorker.class);

    private final MinioStorageService storageService;
    private final ImageReferenceService referenceService;
    private final MinioProperties properties;

    public MinioOrphanCleanupWorker(
            MinioStorageService storageService,
            ImageReferenceService referenceService,
            MinioProperties properties
    ) {
        this.storageService = storageService;
        this.referenceService = referenceService;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${storage.minio.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${storage.minio.cleanup-fixed-delay-ms:21600000}"
    )
    public void removeExpiredOrphans() {
        if (!properties.isCleanupEnabled()) return;
        try {
            int removed = storageService.removeExpiredOrphans(referenceService::isReferenced);
            if (removed > 0) {
                log.info("Removed {} unreferenced image object(s) from MinIO", removed);
            }
        } catch (Exception exception) {
            log.warn("MinIO orphan cleanup skipped because storage is unavailable: {}", exception.getMessage());
        }
    }
}
