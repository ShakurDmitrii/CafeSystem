package com.shakur.cafehelp.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseMigrationRunner {

    private final JdbcTemplate mainJdbcTemplate;
    private final JdbcTemplate taxJdbcTemplate;
    private final TransactionTemplate mainTransactionTemplate;
    private final TransactionTemplate taxTransactionTemplate;
    private final boolean enabled;
    private final Path mainMigrationsPath;
    private final Path taxMigrationsPath;

    public DatabaseMigrationRunner(
            DataSource dataSource,
            @Qualifier("taxJdbcTemplate") JdbcTemplate taxJdbcTemplate,
            @Value("${app.db-migrations.enabled:true}") boolean enabled,
            @Value("${app.db-migrations.main-location:db/migrations}") String mainMigrationsLocation,
            @Value("${app.db-migrations.tax-location:db/migrations-tax}") String taxMigrationsLocation
    ) {
        this.mainJdbcTemplate = new JdbcTemplate(dataSource);
        this.taxJdbcTemplate = taxJdbcTemplate;
        this.mainTransactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.taxTransactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(taxJdbcTemplate.getDataSource()));
        this.enabled = enabled;
        this.mainMigrationsPath = Path.of(mainMigrationsLocation);
        this.taxMigrationsPath = Path.of(taxMigrationsLocation);
    }

    @PostConstruct
    public void runMigrations() {
        if (!enabled) {
            return;
        }
        migrate("main", mainJdbcTemplate, mainTransactionTemplate, mainMigrationsPath);
        migrate("tax", taxJdbcTemplate, taxTransactionTemplate, taxMigrationsPath);
    }

    private void migrate(String target, JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, Path location) {
        if (!Files.isDirectory(location)) {
            throw new IllegalStateException("Migration directory not found for " + target + " database: " + location.toAbsolutePath());
        }

        ensureHistoryTable(jdbcTemplate);

        try {
            List<Path> files;
            try (var stream = Files.list(location)) {
                files = stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".sql"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }

            for (Path file : files) {
                applyMigrationIfNeeded(target, jdbcTemplate, transactionTemplate, file);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run " + target + " database migrations from " + location.toAbsolutePath(), e);
        }
    }

    private void ensureHistoryTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                create table if not exists public.cafehelp_schema_migration (
                    id bigserial primary key,
                    migration_name varchar(255) not null unique,
                    checksum_sha256 varchar(64) not null,
                    applied_at timestamp not null default now()
                )
                """);
    }

    private void applyMigrationIfNeeded(
            String target,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Path file
    ) throws Exception {
        String migrationName = file.getFileName().toString();
        byte[] migrationBytes = Files.readAllBytes(file);
        String sql = sqlForExecution(migrationBytes);
        String checksum = preferredChecksum(migrationBytes);

        String existingChecksum = jdbcTemplate.query(
                "select checksum_sha256 from public.cafehelp_schema_migration where migration_name = ?",
                rs -> rs.next() ? rs.getString("checksum_sha256") : null,
                migrationName
        );

        if (existingChecksum != null) {
            if (!isChecksumCompatible(existingChecksum, migrationBytes)) {
                throw new IllegalStateException(
                        "Checksum mismatch for " + target + " migration " + migrationName
                                + ". Applied=" + existingChecksum + ", current=" + checksum
                );
            }
            return;
        }

        if (shouldMarkExistingBaselineAsApplied(target, jdbcTemplate, migrationName)) {
            jdbcTemplate.update(
                    "insert into public.cafehelp_schema_migration (migration_name, checksum_sha256) values (?, ?)",
                    migrationName,
                    checksum
            );
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute(sql);
            jdbcTemplate.update(
                    "insert into public.cafehelp_schema_migration (migration_name, checksum_sha256) values (?, ?)",
                    migrationName,
                    checksum
            );
        });
    }

    static String preferredChecksum(byte[] value) {
        return sha256(normalizeLineEndings(value).getBytes(StandardCharsets.UTF_8));
    }

    static String sqlForExecution(byte[] value) {
        String sql = new String(value, StandardCharsets.UTF_8);
        return sql.startsWith("\uFEFF") ? sql.substring(1) : sql;
    }

    static boolean isChecksumCompatible(String existingChecksum, byte[] value) {
        if (existingChecksum == null || existingChecksum.isBlank()) {
            return false;
        }

        String normalizedLf = normalizeLineEndings(value);
        String normalizedCrLf = normalizedLf.replace("\n", "\r\n");
        return checksumEquals(existingChecksum, sha256(value))
                || checksumEquals(existingChecksum, sha256(normalizedLf.getBytes(StandardCharsets.UTF_8)))
                || checksumEquals(existingChecksum, sha256(normalizedCrLf.getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalizeLineEndings(byte[] value) {
        return new String(value, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static boolean checksumEquals(String left, String right) {
        return left.equalsIgnoreCase(right);
    }

    private static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean shouldMarkExistingBaselineAsApplied(String target, JdbcTemplate jdbcTemplate, String migrationName) {
        if (!"main".equals(target) || !migrationName.contains("baseline")) {
            return false;
        }

        Integer existingSalesTables = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'sales'
                  and table_type = 'BASE TABLE'
                """,
                Integer.class
        );
        return existingSalesTables != null && existingSalesTables > 0;
    }
}
