package com.shakur.cafehelp.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationRunnerChecksumTest {

    @Test
    void acceptsLegacyRawChecksumAfterLineEndingConversion() throws Exception {
        byte[] originalCrLf = "select 1;\r\nselect 2;\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] checkoutWithLf = "select 1;\nselect 2;\n".getBytes(StandardCharsets.UTF_8);
        String legacyChecksum = sha256(originalCrLf);

        assertThat(DatabaseMigrationRunner.isChecksumCompatible(legacyChecksum, checkoutWithLf))
                .isTrue();
    }

    @Test
    void storesLineEndingIndependentChecksumForNewMigrations() {
        byte[] crLf = "select 1;\r\nselect 2;\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] lf = "select 1;\nselect 2;\n".getBytes(StandardCharsets.UTF_8);

        assertThat(DatabaseMigrationRunner.preferredChecksum(crLf))
                .isEqualTo(DatabaseMigrationRunner.preferredChecksum(lf));
    }

    @Test
    void rejectsChecksumWhenSqlContentChanged() throws Exception {
        byte[] original = "select 1;\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] changed = "select 2;\n".getBytes(StandardCharsets.UTF_8);

        assertThat(DatabaseMigrationRunner.isChecksumCompatible(sha256(original), changed))
                .isFalse();
    }

    @Test
    void removesUtf8BomBeforeExecutingMigration() {
        byte[] migration = "\uFEFFselect 1;\n".getBytes(StandardCharsets.UTF_8);

        assertThat(DatabaseMigrationRunner.sqlForExecution(migration))
                .isEqualTo("select 1;\n");
    }

    @Test
    void acceptsOnlyPinnedLegacyRuntimeAlignmentRevision() {
        assertThat(DatabaseMigrationRunner.isKnownCompatibleMigrationChecksum(
                "2026-04-08_runtime_schema_alignment.sql",
                "ce284ba9e1e9ace1714a0effb2993b578a380411ae112e860c5ca1f7335e6032",
                "cccee97177b401ce0479375f5e71f41b99b0393a466938d08b60426ef7f6a315"
        )).isTrue();
        assertThat(DatabaseMigrationRunner.isKnownCompatibleMigrationChecksum(
                "2026-04-08_runtime_schema_alignment.sql",
                "ce284ba9e1e9ace1714a0effb2993b578a380411ae112e860c5ca1f7335e6032",
                "different-current-checksum"
        )).isFalse();
    }

    private String sha256(byte[] value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value));
    }
}
