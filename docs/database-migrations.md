# Database migrations

Backend startup applies SQL migrations before `DatabaseSchemaInitializer`.

## Locations

- Main database: `db/migrations`
- Tax database: `db/migrations-tax`

Files are applied in filename order. Use date-prefixed names, for example:

```text
2026-04-08_some_change.sql
```

## History table

Each database stores applied migration metadata in:

```sql
public.cafehelp_schema_migration
```

The runner stores the file name and SHA-256 checksum. If an already applied file is edited later, startup fails with a checksum mismatch. Add a new migration instead of changing an applied one.

Checksums for new migrations are independent of `LF`/`CRLF` line endings. The
runner also accepts historical raw-file checksums when the SQL content is the
same and only line endings differ. A UTF-8 BOM is retained for checksum
compatibility but removed before SQL execution.

## Configuration

Defaults:

```properties
app.db-migrations.enabled=true
app.db-migrations.main-location=db/migrations
app.db-migrations.tax-location=db/migrations-tax
```

Set `app.db-migrations.enabled=false` only for diagnostics or one-off recovery.

## Clean database verification

The migration set is verified by `CafehelpApplicationTests` against two
temporary PostgreSQL 16 containers:

- an empty main CafeHelp database;
- an empty tax database.

Run the verification with:

```text
gradlew test
```

Docker must be available. The test does not connect to the developer's local
PostgreSQL databases.

`DatabaseSchemaInitializer` still contains compatibility DDL for legacy
installations. Its remaining statements should be moved into versioned
migrations before the initializer is removed.
