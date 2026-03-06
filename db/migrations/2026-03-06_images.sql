-- Images for ingredients and dishes
-- Run once in PostgreSQL (DBeaver/psql):
-- \i db/migrations/2026-03-06_images.sql

alter table sales.product
    add column if not exists image_url varchar(1024);

alter table sales.dish
    add column if not exists image_url varchar(1024);
