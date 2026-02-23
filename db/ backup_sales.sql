--
-- PostgreSQL database dump
--

\restrict ngKykAFhmKcLRWh4YazsIga5VsooeQO6ISZ09JWE89UrhCqL4RBwF9jWXRq96sP

-- Dumped from database version 18.1
-- Dumped by pg_dump version 18.1

-- Started on 2026-02-23 15:05:34

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

DROP DATABASE postgres;
--
-- TOC entry 5251 (class 1262 OID 5)
-- Name: postgres; Type: DATABASE; Schema: -; Owner: -
--

CREATE DATABASE postgres WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'Russian_Russia.1251';


\unrestrict ngKykAFhmKcLRWh4YazsIga5VsooeQO6ISZ09JWE89UrhCqL4RBwF9jWXRq96sP
\connect postgres
\restrict ngKykAFhmKcLRWh4YazsIga5VsooeQO6ISZ09JWE89UrhCqL4RBwF9jWXRq96sP

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 5252 (class 0 OID 0)
-- Dependencies: 5251
-- Name: DATABASE postgres; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON DATABASE postgres IS 'default administrative connection database';


--
-- TOC entry 10 (class 2615 OID 16597)
-- Name: sales; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA sales;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 242 (class 1259 OID 16619)
-- Name: client; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client (
    fullname character varying,
    clientid integer NOT NULL,
    number character varying
);


--
-- TOC entry 256 (class 1259 OID 17261)
-- Name: client_clientid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.client ALTER COLUMN clientid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.client_clientid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 253 (class 1259 OID 16832)
-- Name: clientdish; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.clientdish (
    dishid integer NOT NULL,
    clientid integer NOT NULL,
    dishname character varying NOT NULL
);


--
-- TOC entry 268 (class 1259 OID 32923)
-- Name: clientduty; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.clientduty (
    clientid bigint NOT NULL,
    clientname character varying NOT NULL,
    number character varying,
    duty double precision,
    data date
);


--
-- TOC entry 243 (class 1259 OID 16642)
-- Name: consignmentnote; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.consignmentnote (
    supplierid integer NOT NULL,
    amount double precision,
    date date NOT NULL,
    consignmentid integer NOT NULL
);


--
-- TOC entry 258 (class 1259 OID 17326)
-- Name: consignmentnote_consignmentid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.consignmentnote ALTER COLUMN consignmentid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.consignmentnote_consignmentid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 252 (class 1259 OID 16812)
-- Name: consproduct; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.consproduct (
    consignmentid integer NOT NULL,
    productid integer NOT NULL,
    gross double precision,
    quantity double precision,
    consproductid integer CONSTRAINT consproduct_consproduct_not_null NOT NULL
);


--
-- TOC entry 259 (class 1259 OID 17363)
-- Name: consproduct_consproduct_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.consproduct ALTER COLUMN consproductid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.consproduct_consproduct_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 245 (class 1259 OID 16678)
-- Name: dish; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.dish (
    dishname character varying NOT NULL,
    weight double precision NOT NULL,
    firstcost double precision NOT NULL,
    price double precision NOT NULL,
    techproductid integer NOT NULL,
    dishid integer NOT NULL,
    category character varying
);


--
-- TOC entry 261 (class 1259 OID 32796)
-- Name: dish_dishid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.dish ALTER COLUMN dishid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.dish_dishid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 272 (class 1259 OID 49403)
-- Name: inventory_document_lines; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.inventory_document_lines (
    id integer NOT NULL,
    document_id integer NOT NULL,
    product_id integer NOT NULL,
    qty numeric NOT NULL,
    unit_price numeric,
    line_total numeric,
    batch_ref character varying
);


--
-- TOC entry 271 (class 1259 OID 49402)
-- Name: inventory_document_lines_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.inventory_document_lines ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.inventory_document_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 276 (class 1259 OID 49443)
-- Name: inventory_documents; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.inventory_documents (
    id integer NOT NULL,
    doc_type character varying(30) NOT NULL,
    doc_date timestamp without time zone NOT NULL,
    supplier_id integer NOT NULL,
    warehouse_from_id integer,
    warehouse_to_id integer,
    status character varying,
    comment text,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now()
);


--
-- TOC entry 275 (class 1259 OID 49442)
-- Name: inventory_documents_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.inventory_documents ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.inventory_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 254 (class 1259 OID 17218)
-- Name: order; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales."order" (
    shiftid integer CONSTRAINT "Order_shiftid_not_null" NOT NULL,
    "Date" date CONSTRAINT "Order_Date_not_null" NOT NULL,
    clientid integer,
    amount double precision CONSTRAINT "Order_amount_not_null" NOT NULL,
    status boolean NOT NULL,
    orderid integer NOT NULL,
    type boolean NOT NULL,
    "time" double precision NOT NULL,
    timedelay double precision,
    created_at timestamp without time zone,
    duty boolean NOT NULL,
    date_issue date,
    debt_payment_date date
);


--
-- TOC entry 260 (class 1259 OID 17371)
-- Name: order_orderid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales."order" ALTER COLUMN orderid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.order_orderid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 263 (class 1259 OID 32831)
-- Name: orderdish; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.orderdish (
    id integer NOT NULL,
    orderid integer,
    dishid integer,
    qty integer NOT NULL
);


--
-- TOC entry 262 (class 1259 OID 32830)
-- Name: orderdish_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.orderdish ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.orderdish_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 246 (class 1259 OID 16704)
-- Name: person; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.person (
    personid integer NOT NULL,
    name character varying NOT NULL,
    salary numeric NOT NULL,
    numdays integer,
    salaryperday numeric NOT NULL
);


--
-- TOC entry 244 (class 1259 OID 16668)
-- Name: product; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.product (
    supplierid integer,
    productname character varying NOT NULL,
    productprice numeric NOT NULL,
    waste double precision,
    isfavourite boolean NOT NULL,
    productid integer NOT NULL
);


--
-- TOC entry 257 (class 1259 OID 17302)
-- Name: product_productid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.product ALTER COLUMN productid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.product_productid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 251 (class 1259 OID 16773)
-- Name: productwarehouse; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.productwarehouse (
    warehouseid integer,
    productid integer,
    productwarehouseid integer NOT NULL,
    quantity double precision
);


--
-- TOC entry 267 (class 1259 OID 32910)
-- Name: productwarehouse_productwarehouseid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.productwarehouse ALTER COLUMN productwarehouseid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.productwarehouse_productwarehouseid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 247 (class 1259 OID 16716)
-- Name: shift; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.shift (
    data date NOT NULL,
    starttime time without time zone NOT NULL,
    endtime time without time zone,
    profit numeric,
    expenses numeric,
    income double precision,
    personcode integer,
    id integer NOT NULL
);


--
-- TOC entry 264 (class 1259 OID 32857)
-- Name: shift_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.shift ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.shift_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 249 (class 1259 OID 16736)
-- Name: shiftperson; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.shiftperson (
    shiftpersonid integer NOT NULL,
    shiftid integer NOT NULL,
    personid integer
);


--
-- TOC entry 274 (class 1259 OID 49419)
-- Name: stock_movements; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.stock_movements (
    id integer NOT NULL,
    movement_date timestamp without time zone NOT NULL,
    document_id integer NOT NULL,
    warehouse_id integer NOT NULL,
    product_id integer NOT NULL,
    qty_in numeric(14,3) DEFAULT 0,
    qty_out numeric(14,3) DEFAULT 0,
    unit_cost numeric(14,2),
    amount numeric(14,2),
    created_at timestamp without time zone DEFAULT now()
);


--
-- TOC entry 273 (class 1259 OID 49418)
-- Name: stock_movements_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.stock_movements ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.stock_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 248 (class 1259 OID 16727)
-- Name: supplier; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.supplier (
    suppliername character varying NOT NULL,
    communication character varying,
    supplierid integer NOT NULL
);


--
-- TOC entry 269 (class 1259 OID 49352)
-- Name: supplier_price_history; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.supplier_price_history (
    supplier_id integer NOT NULL,
    product_id integer NOT NULL,
    id integer NOT NULL,
    price numeric NOT NULL,
    valid_from timestamp without time zone NOT NULL,
    valid_to timestamp without time zone,
    source_doc_type character varying,
    source_doc_id integer,
    created_at timestamp without time zone DEFAULT now()
);


--
-- TOC entry 270 (class 1259 OID 49366)
-- Name: supplier_price_history_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.supplier_price_history ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.supplier_price_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 255 (class 1259 OID 17242)
-- Name: supplier_supplierid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.supplier ALTER COLUMN supplierid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.supplier_supplierid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 250 (class 1259 OID 16755)
-- Name: techproduct; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.techproduct (
    "DishId" integer CONSTRAINT techproduct_techproductname_not_null NOT NULL,
    productid integer NOT NULL,
    waste double precision,
    weight double precision NOT NULL,
    techproductid integer NOT NULL
);


--
-- TOC entry 265 (class 1259 OID 32879)
-- Name: techproduct_techproductid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.techproduct ALTER COLUMN techproductid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.techproduct_techproductid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 241 (class 1259 OID 16598)
-- Name: warehouse; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.warehouse (
    warehousename character varying NOT NULL,
    warehouseid integer NOT NULL
);


--
-- TOC entry 266 (class 1259 OID 32895)
-- Name: warehouse_warehouseid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.warehouse ALTER COLUMN warehouseid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.warehouse_warehouseid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- TOC entry 5211 (class 0 OID 16619)
-- Dependencies: 242
-- Data for Name: client; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.client OVERRIDING SYSTEM VALUE VALUES ('Шакур Дмитрий ', 1, '89507639838');
INSERT INTO sales.client OVERRIDING SYSTEM VALUE VALUES ('Анатолий', 2, '88005353535');
INSERT INTO sales.client OVERRIDING SYSTEM VALUE VALUES ('Алена', 3, '8951423654');


--
-- TOC entry 5222 (class 0 OID 16832)
-- Dependencies: 253
-- Data for Name: clientdish; Type: TABLE DATA; Schema: sales; Owner: -
--



--
-- TOC entry 5237 (class 0 OID 32923)
-- Dependencies: 268
-- Data for Name: clientduty; Type: TABLE DATA; Schema: sales; Owner: -
--



--
-- TOC entry 5212 (class 0 OID 16642)
-- Dependencies: 243
-- Data for Name: consignmentnote; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.consignmentnote OVERRIDING SYSTEM VALUE VALUES (5, NULL, '2026-02-22', 22);


--
-- TOC entry 5221 (class 0 OID 16812)
-- Dependencies: 252
-- Data for Name: consproduct; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.consproduct OVERRIDING SYSTEM VALUE VALUES (22, 8, 0, 2, 27);
INSERT INTO sales.consproduct OVERRIDING SYSTEM VALUE VALUES (22, 9, 0, 3, 28);
INSERT INTO sales.consproduct OVERRIDING SYSTEM VALUE VALUES (22, 11, 0, 5, 29);


--
-- TOC entry 5214 (class 0 OID 16678)
-- Dependencies: 245
-- Data for Name: dish; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.dish OVERRIDING SYSTEM VALUE VALUES ('ролл', 0.2, 40, 120, 1, 4, NULL);
INSERT INTO sales.dish OVERRIDING SYSTEM VALUE VALUES ('Ролл с угрем', 0.13, 50, 150, 0, 5, NULL);
INSERT INTO sales.dish OVERRIDING SYSTEM VALUE VALUES ('ролл с лососем ', 120, 1, 190, 2, 10, NULL);


--
-- TOC entry 5241 (class 0 OID 49403)
-- Dependencies: 272
-- Data for Name: inventory_document_lines; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (1, 1, 8, 2.0, 500.0, 1000.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (3, 3, 13, 1.0, 100.0, 100.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (4, 5, 8, 1.0, NULL, NULL, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (5, 6, 13, 1.0, 100.0, 100.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (6, 7, 13, 1.0, 150.0, 150.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (7, 8, 13, 200.0, 5.0, 1000.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (8, 9, 14, 20.0, 50.0, 1000.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (9, 10, 7, 1.0, 1200.0, 1200.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (10, 11, 7, 0.6, 1300.0, 780.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (11, 12, 13, 2.0, 5.0, 10.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (12, 13, 8, 2.0, 800.0, 1600.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (13, 14, 9, 3.0, 650.0, 1950.00, NULL);
INSERT INTO sales.inventory_document_lines OVERRIDING SYSTEM VALUE VALUES (14, 15, 11, 5.0, 1200.0, 6000.00, NULL);


--
-- TOC entry 5245 (class 0 OID 49443)
-- Dependencies: 276
-- Data for Name: inventory_documents; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (1, 'receipt', '2026-02-20 16:02:01.954626', 5, NULL, 3, 'posted', 'consignment-note:19', 'consignment-ui', '2026-02-20 16:02:01.954626');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (3, 'receipt', '2026-02-20 16:20:14.590645', 5, NULL, 3, 'posted', 'warehouse-manual-add:3', 'warehouse-ui', '2026-02-20 16:20:14.590645');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (5, 'movement', '2026-02-20 20:13:07.560648', 5, 3, 4, 'posted', NULL, NULL, '2026-02-20 20:13:07.560648');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (6, 'movement', '2026-02-20 20:15:03.141797', 5, 3, 4, 'posted', NULL, NULL, '2026-02-20 20:15:03.141797');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (7, 'receipt', '2026-02-21 20:26:00', 5, NULL, 3, 'posted', 'consignment-note:20', 'consignment-ui', '2026-02-20 20:17:22.660228');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (8, 'receipt', '2026-02-26 00:00:00', 5, NULL, 3, 'posted', 'consignment-note:21', 'consignment-ui', '2026-02-26 00:00:00');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (9, 'receipt', '2026-02-20 20:35:11.607561', 5, NULL, 3, 'posted', 'warehouse-manual-add:3', 'warehouse-ui', '2026-02-20 20:35:11.607561');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (10, 'receipt', '2026-02-20 20:41:10.443904', 2, NULL, 3, 'posted', 'warehouse-adjust-add:3', 'warehouse-ui', '2026-02-20 20:41:10.443904');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (11, 'receipt', '2026-02-20 20:41:42.737248', 2, NULL, 3, 'posted', 'warehouse-adjust-add:3', 'warehouse-ui', '2026-02-20 20:41:42.737248');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (12, 'writeoff', '2026-02-20 20:44:15.377826', 5, 3, NULL, 'posted', 'warehouse-writeoff:3', 'warehouse-ui', '2026-02-20 20:44:15.377826');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (13, 'receipt', '2026-02-22 00:00:00', 5, NULL, 3, 'posted', 'consignment-note:22', 'consignment-ui', '2026-02-22 00:00:00');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (14, 'receipt', '2026-02-22 00:00:00', 5, NULL, 3, 'posted', 'consignment-note:22', 'consignment-ui', '2026-02-22 00:00:00');
INSERT INTO sales.inventory_documents OVERRIDING SYSTEM VALUE VALUES (15, 'receipt', '2026-02-22 00:00:00', 5, NULL, 3, 'posted', 'consignment-note:22', 'consignment-ui', '2026-02-22 00:00:00');


--
-- TOC entry 5223 (class 0 OID 17218)
-- Dependencies: 254
-- Data for Name: order; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 120, true, 72, false, 30, NULL, '2025-12-21 21:26:27.312609', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 120, true, 71, true, 30, NULL, '2025-12-21 21:25:39.054996', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 2, 480, true, 73, true, 30, NULL, '2025-12-22 01:11:00.018892', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 120, true, 74, false, 30, NULL, '2025-12-22 01:18:32.232765', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 120, true, 75, true, 30, NULL, '2025-12-22 01:21:50.671196', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 2, 120, true, 77, true, 30, NULL, '2025-12-22 01:53:56.329024', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 3, 120, true, 78, true, 1, NULL, '2025-12-22 01:56:35.662084', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 600, true, 80, true, 30, NULL, '2025-12-22 02:00:30.748155', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 3, 120, true, 79, true, 30, NULL, '2025-12-22 01:57:39.466418', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 120, true, 81, true, 30, NULL, '2025-12-22 02:02:52.494993', true, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 2, 120, true, 82, true, 30, NULL, '2025-12-22 02:04:03.804028', true, NULL, '2025-12-23');
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (22, '2025-12-21', 3, 120, true, 83, false, 30, NULL, '2025-12-22 02:25:19.186272', true, NULL, '2025-12-22');
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (28, '2025-12-21', 2, 120, true, 84, true, 30, NULL, '2025-12-22 02:56:31.660648', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (28, '2025-12-24', 1, 450, true, 85, false, 30, NULL, '2025-12-24 22:51:04.057439', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (28, '2025-12-24', 1, 840, true, 86, true, 30, NULL, '2025-12-24 22:51:20.680757', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (29, '2026-02-19', 3, 570, true, 87, true, 1, NULL, '2026-02-19 20:50:56.806436', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (29, '2026-02-19', 2, 120, true, 88, true, 1, 1, '2026-02-19 20:55:44.552563', false, NULL, NULL);
INSERT INTO sales."order" OVERRIDING SYSTEM VALUE VALUES (21, '2025-12-21', 1, 120, true, 76, true, 1, 5, '2025-12-22 01:27:05.584263', false, NULL, NULL);


--
-- TOC entry 5232 (class 0 OID 32831)
-- Dependencies: 263
-- Data for Name: orderdish; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (10, 71, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (11, 72, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (12, 73, 4, 4);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (13, 74, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (14, 75, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (15, 76, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (16, 77, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (17, 78, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (18, 79, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (19, 80, 4, 5);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (20, 81, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (21, 82, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (22, 83, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (23, 84, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (24, 85, 5, 3);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (25, 86, 5, 4);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (26, 86, 4, 2);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (27, 87, 4, 1);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (28, 87, 5, 3);
INSERT INTO sales.orderdish OVERRIDING SYSTEM VALUE VALUES (29, 88, 4, 1);


--
-- TOC entry 5215 (class 0 OID 16704)
-- Dependencies: 246
-- Data for Name: person; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.person VALUES (0, 'Дмитрий', 0, 0, 1500);


--
-- TOC entry 5213 (class 0 OID 16668)
-- Dependencies: 244
-- Data for Name: product; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (2, 'Лосось', 1500, 1, true, 1);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (4, 'Нори', 250, 1, false, 2);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (3, 'Креветки ', 1000, 15, false, 3);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (2, 'Угорь', 800, 1, false, 4);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (2, 'Креветки', 1200, 5, false, 5);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (4, 'Вода', 100, 0, false, 6);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (2, 'Рис', 1000, 0, true, 7);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (5, 'Нори ', 800, 0, false, 8);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (5, 'Мидии', 650, 0, false, 9);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (5, 'Лосось', 1200, 40, false, 10);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (5, 'Рис', 120, 0, false, 11);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (2, 'Лимон', 5, 5, false, 12);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (5, 'Сахар', 100, 0, false, 13);
INSERT INTO sales.product OVERRIDING SYSTEM VALUE VALUES (5, 'Лимон', 50, 5, false, 14);


--
-- TOC entry 5220 (class 0 OID 16773)
-- Dependencies: 251
-- Data for Name: productwarehouse; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (4, 7, 13, 0.1);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 12, 16, 0);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (4, 8, 19, 1);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (4, 13, 20, 1);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 14, 21, 40);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 7, 12, 2);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 13, 17, 200);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 8, 14, 3);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 9, 22, 3);
INSERT INTO sales.productwarehouse OVERRIDING SYSTEM VALUE VALUES (3, 11, 23, 5);


--
-- TOC entry 5216 (class 0 OID 16716)
-- Dependencies: 247
-- Data for Name: shift; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-15', '23:42:15', '03:44:36', 0, 0, 0, NULL, 20);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-16', '03:44:48', '02:24:37', 0, 0, 0, NULL, 21);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:24:41', '02:26:27', 0, 0, 0, NULL, 22);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:26:29', '02:27:58', 0, 0, 0, NULL, 23);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:28:03', '02:38:37', 0, 0, 0, NULL, 24);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:38:40', '02:43:19', 0, 0, 0, NULL, 25);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:43:25', '02:49:20', 0, 0, 0, NULL, 26);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:49:24', '02:51:51', 0, 0, 0, NULL, 27);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2025-12-21', '02:51:54', '22:51:25', 0, 0, 0, NULL, 28);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2026-02-19', '20:50:13', '21:43:53', 0, 0, 0, NULL, 29);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2026-02-19', '21:51:08', '21:51:10', 0, 0, 0, NULL, 30);
INSERT INTO sales.shift OVERRIDING SYSTEM VALUE VALUES ('2026-02-19', '21:53:38', '21:53:38', 0, 0, 0, NULL, 31);


--
-- TOC entry 5218 (class 0 OID 16736)
-- Dependencies: 249
-- Data for Name: shiftperson; Type: TABLE DATA; Schema: sales; Owner: -
--



--
-- TOC entry 5243 (class 0 OID 49419)
-- Dependencies: 274
-- Data for Name: stock_movements; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (1, '2026-02-20 16:02:01.954626', 1, 3, 8, 2.000, 0.000, 500.00, 1000.00, '2026-02-20 16:02:01.954626');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (3, '2026-02-20 16:20:14.590645', 3, 3, 13, 1.000, 0.000, 100.00, 100.00, '2026-02-20 16:20:14.590645');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (4, '2026-02-20 20:13:07.560648', 5, 3, 8, 0.000, 1.000, NULL, NULL, '2026-02-20 20:13:07.560648');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (5, '2026-02-20 20:13:07.560648', 5, 4, 8, 1.000, 0.000, NULL, NULL, '2026-02-20 20:13:07.560648');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (6, '2026-02-20 20:15:03.141797', 6, 3, 13, 0.000, 1.000, 100.00, 100.00, '2026-02-20 20:15:03.141797');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (7, '2026-02-20 20:15:03.141797', 6, 4, 13, 1.000, 0.000, 100.00, 100.00, '2026-02-20 20:15:03.141797');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (8, '2026-02-21 20:26:00', 7, 3, 13, 1.000, 0.000, 150.00, 150.00, '2026-02-20 20:17:22.660228');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (9, '2026-02-26 00:00:00', 8, 3, 13, 200.000, 0.000, 5.00, 1000.00, '2026-02-26 00:00:00');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (10, '2026-02-20 20:35:11.607561', 9, 3, 14, 20.000, 0.000, 50.00, 1000.00, '2026-02-20 20:35:11.607561');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (11, '2026-02-20 20:41:10.443904', 10, 3, 7, 1.000, 0.000, 1200.00, 1200.00, '2026-02-20 20:41:10.443904');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (12, '2026-02-20 20:41:42.737248', 11, 3, 7, 0.600, 0.000, 1300.00, 780.00, '2026-02-20 20:41:42.737248');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (13, '2026-02-20 20:44:15.377826', 12, 3, 13, 0.000, 2.000, 5.00, 10.00, '2026-02-20 20:44:15.377826');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (14, '2026-02-22 00:00:00', 13, 3, 8, 2.000, 0.000, 800.00, 1600.00, '2026-02-22 00:00:00');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (15, '2026-02-22 00:00:00', 14, 3, 9, 3.000, 0.000, 650.00, 1950.00, '2026-02-22 00:00:00');
INSERT INTO sales.stock_movements OVERRIDING SYSTEM VALUE VALUES (16, '2026-02-22 00:00:00', 15, 3, 11, 5.000, 0.000, 1200.00, 6000.00, '2026-02-22 00:00:00');


--
-- TOC entry 5217 (class 0 OID 16727)
-- Dependencies: 248
-- Data for Name: supplier; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.supplier OVERRIDING SYSTEM VALUE VALUES ('OOO test', '89507639838', 2);
INSERT INTO sales.supplier OVERRIDING SYSTEM VALUE VALUES ('OOO test2', 'xsaac2004@gmail.com', 3);
INSERT INTO sales.supplier OVERRIDING SYSTEM VALUE VALUES ('OOO test3', '89507645687', 4);
INSERT INTO sales.supplier OVERRIDING SYSTEM VALUE VALUES ('ООО Державин ', 'Derzh@maaail.ru', 5);
INSERT INTO sales.supplier OVERRIDING SYSTEM VALUE VALUES ('Делаем сами ', '', 6);


--
-- TOC entry 5238 (class 0 OID 49352)
-- Dependencies: 269
-- Data for Name: supplier_price_history; Type: TABLE DATA; Schema: sales; Owner: -
--



--
-- TOC entry 5219 (class 0 OID 16755)
-- Dependencies: 250
-- Data for Name: techproduct; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.techproduct OVERRIDING SYSTEM VALUE VALUES (4, 2, 0, 0.5, 2);
INSERT INTO sales.techproduct OVERRIDING SYSTEM VALUE VALUES (5, 2, 0, 1, 3);
INSERT INTO sales.techproduct OVERRIDING SYSTEM VALUE VALUES (5, 4, 0, 10, 6);
INSERT INTO sales.techproduct OVERRIDING SYSTEM VALUE VALUES (4, 1, 0, 0.12, 7);
INSERT INTO sales.techproduct OVERRIDING SYSTEM VALUE VALUES (4, 1, 0, 15, 8);
INSERT INTO sales.techproduct OVERRIDING SYSTEM VALUE VALUES (5, 7, 0, 10, 9);


--
-- TOC entry 5210 (class 0 OID 16598)
-- Dependencies: 241
-- Data for Name: warehouse; Type: TABLE DATA; Schema: sales; Owner: -
--

INSERT INTO sales.warehouse OVERRIDING SYSTEM VALUE VALUES ('Рабочий Склад', 3);
INSERT INTO sales.warehouse OVERRIDING SYSTEM VALUE VALUES ('Склад 1', 4);


--
-- TOC entry 5253 (class 0 OID 0)
-- Dependencies: 256
-- Name: client_clientid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.client_clientid_seq', 3, true);


--
-- TOC entry 5254 (class 0 OID 0)
-- Dependencies: 258
-- Name: consignmentnote_consignmentid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.consignmentnote_consignmentid_seq', 22, true);


--
-- TOC entry 5255 (class 0 OID 0)
-- Dependencies: 259
-- Name: consproduct_consproduct_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.consproduct_consproduct_seq', 29, true);


--
-- TOC entry 5256 (class 0 OID 0)
-- Dependencies: 261
-- Name: dish_dishid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.dish_dishid_seq', 10, true);


--
-- TOC entry 5257 (class 0 OID 0)
-- Dependencies: 271
-- Name: inventory_document_lines_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.inventory_document_lines_id_seq', 14, true);


--
-- TOC entry 5258 (class 0 OID 0)
-- Dependencies: 275
-- Name: inventory_documents_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.inventory_documents_id_seq', 15, true);


--
-- TOC entry 5259 (class 0 OID 0)
-- Dependencies: 260
-- Name: order_orderid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.order_orderid_seq', 88, true);


--
-- TOC entry 5260 (class 0 OID 0)
-- Dependencies: 262
-- Name: orderdish_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.orderdish_id_seq', 29, true);


--
-- TOC entry 5261 (class 0 OID 0)
-- Dependencies: 257
-- Name: product_productid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.product_productid_seq', 14, true);


--
-- TOC entry 5262 (class 0 OID 0)
-- Dependencies: 267
-- Name: productwarehouse_productwarehouseid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.productwarehouse_productwarehouseid_seq', 23, true);


--
-- TOC entry 5263 (class 0 OID 0)
-- Dependencies: 264
-- Name: shift_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.shift_id_seq', 31, true);


--
-- TOC entry 5264 (class 0 OID 0)
-- Dependencies: 273
-- Name: stock_movements_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.stock_movements_id_seq', 16, true);


--
-- TOC entry 5265 (class 0 OID 0)
-- Dependencies: 270
-- Name: supplier_price_history_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.supplier_price_history_id_seq', 1, false);


--
-- TOC entry 5266 (class 0 OID 0)
-- Dependencies: 255
-- Name: supplier_supplierid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.supplier_supplierid_seq', 6, true);


--
-- TOC entry 5267 (class 0 OID 0)
-- Dependencies: 265
-- Name: techproduct_techproductid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.techproduct_techproductid_seq', 9, true);


--
-- TOC entry 5268 (class 0 OID 0)
-- Dependencies: 266
-- Name: warehouse_warehouseid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.warehouse_warehouseid_seq', 4, true);


--
-- TOC entry 5001 (class 2606 OID 17269)
-- Name: client client_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client
    ADD CONSTRAINT client_pk PRIMARY KEY (clientid);


--
-- TOC entry 5027 (class 2606 OID 17045)
-- Name: clientdish clientdish_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.clientdish
    ADD CONSTRAINT clientdish_pk PRIMARY KEY (dishid);


--
-- TOC entry 5003 (class 2606 OID 17334)
-- Name: consignmentnote consignmentnote_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consignmentnote
    ADD CONSTRAINT consignmentnote_pk PRIMARY KEY (consignmentid);


--
-- TOC entry 5025 (class 2606 OID 17369)
-- Name: consproduct consproduct_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consproduct
    ADD CONSTRAINT consproduct_pk PRIMARY KEY (consproductid);


--
-- TOC entry 5007 (class 2606 OID 32805)
-- Name: dish dish_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish
    ADD CONSTRAINT dish_pk PRIMARY KEY (dishid);


--
-- TOC entry 5009 (class 2606 OID 17100)
-- Name: dish dish_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish
    ADD CONSTRAINT dish_unique UNIQUE (techproductid);


--
-- TOC entry 5039 (class 2606 OID 49413)
-- Name: inventory_document_lines inventory_document_lines_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_document_lines
    ADD CONSTRAINT inventory_document_lines_pk PRIMARY KEY (id);


--
-- TOC entry 5043 (class 2606 OID 49454)
-- Name: inventory_documents inventory_documents_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_documents
    ADD CONSTRAINT inventory_documents_pk PRIMARY KEY (id);


--
-- TOC entry 5031 (class 2606 OID 32836)
-- Name: orderdish newtable_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT newtable_pk PRIMARY KEY (id);


--
-- TOC entry 5029 (class 2606 OID 17377)
-- Name: order order_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales."order"
    ADD CONSTRAINT order_pk PRIMARY KEY (orderid);


--
-- TOC entry 5011 (class 2606 OID 16952)
-- Name: person person_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.person
    ADD CONSTRAINT person_pk PRIMARY KEY (personid);


--
-- TOC entry 5005 (class 2606 OID 17310)
-- Name: product product_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product
    ADD CONSTRAINT product_pk PRIMARY KEY (productid);


--
-- TOC entry 5023 (class 2606 OID 32916)
-- Name: productwarehouse productwarehouse_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.productwarehouse
    ADD CONSTRAINT productwarehouse_pk PRIMARY KEY (productwarehouseid);


--
-- TOC entry 5013 (class 2606 OID 32866)
-- Name: shift shift_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift
    ADD CONSTRAINT shift_pk PRIMARY KEY (id);


--
-- TOC entry 5015 (class 2606 OID 32851)
-- Name: shift shift_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift
    ADD CONSTRAINT shift_unique UNIQUE (personcode);


--
-- TOC entry 5019 (class 2606 OID 16887)
-- Name: shiftperson shiftperson_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shiftperson
    ADD CONSTRAINT shiftperson_pk PRIMARY KEY (shiftpersonid);


--
-- TOC entry 5041 (class 2606 OID 49431)
-- Name: stock_movements stock_movements_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_pk PRIMARY KEY (id);


--
-- TOC entry 5017 (class 2606 OID 17250)
-- Name: supplier supplier_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier
    ADD CONSTRAINT supplier_pk PRIMARY KEY (supplierid);


--
-- TOC entry 5033 (class 2606 OID 49376)
-- Name: supplier_price_history supplier_price_history_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier_price_history
    ADD CONSTRAINT supplier_price_history_pk PRIMARY KEY (id);


--
-- TOC entry 5035 (class 2606 OID 49392)
-- Name: supplier_price_history supplier_price_history_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier_price_history
    ADD CONSTRAINT supplier_price_history_unique UNIQUE (supplier_id);


--
-- TOC entry 5037 (class 2606 OID 49381)
-- Name: supplier_price_history supplier_price_history_unique_1; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier_price_history
    ADD CONSTRAINT supplier_price_history_unique_1 UNIQUE (product_id);


--
-- TOC entry 5021 (class 2606 OID 32885)
-- Name: techproduct techproduct_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_pk PRIMARY KEY (techproductid);


--
-- TOC entry 4997 (class 2606 OID 32904)
-- Name: warehouse warehouse_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.warehouse
    ADD CONSTRAINT warehouse_pk PRIMARY KEY (warehouseid);


--
-- TOC entry 4999 (class 2606 OID 16608)
-- Name: warehouse warehouse_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.warehouse
    ADD CONSTRAINT warehouse_unique UNIQUE (warehousename);


--
-- TOC entry 5054 (class 2606 OID 17270)
-- Name: clientdish clientdish_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.clientdish
    ADD CONSTRAINT clientdish_client_fk FOREIGN KEY (clientid) REFERENCES sales.client(clientid);


--
-- TOC entry 5057 (class 2606 OID 32930)
-- Name: clientduty clientduty_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.clientduty
    ADD CONSTRAINT clientduty_client_fk FOREIGN KEY (clientid) REFERENCES sales.client(clientid);


--
-- TOC entry 5044 (class 2606 OID 17256)
-- Name: consignmentnote consignmentnote_supplier_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consignmentnote
    ADD CONSTRAINT consignmentnote_supplier_fk FOREIGN KEY (supplierid) REFERENCES sales.supplier(supplierid);


--
-- TOC entry 5052 (class 2606 OID 17335)
-- Name: consproduct consproduct_consignmentnote_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consproduct
    ADD CONSTRAINT consproduct_consignmentnote_fk FOREIGN KEY (consignmentid) REFERENCES sales.consignmentnote(consignmentid);


--
-- TOC entry 5053 (class 2606 OID 17321)
-- Name: consproduct consproduct_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consproduct
    ADD CONSTRAINT consproduct_product_fk FOREIGN KEY (productid) REFERENCES sales.product(productid);


--
-- TOC entry 5058 (class 2606 OID 49475)
-- Name: inventory_document_lines inventory_document_lines_inventory_documents_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_document_lines
    ADD CONSTRAINT inventory_document_lines_inventory_documents_fk FOREIGN KEY (document_id) REFERENCES sales.inventory_documents(id);


--
-- TOC entry 5059 (class 2606 OID 49470)
-- Name: inventory_document_lines inventory_document_lines_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_document_lines
    ADD CONSTRAINT inventory_document_lines_product_fk FOREIGN KEY (product_id) REFERENCES sales.product(productid);


--
-- TOC entry 5055 (class 2606 OID 32842)
-- Name: orderdish orderdish_dish_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT orderdish_dish_fk FOREIGN KEY (dishid) REFERENCES sales.dish(dishid);


--
-- TOC entry 5056 (class 2606 OID 32837)
-- Name: orderdish orderdish_order_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT orderdish_order_fk FOREIGN KEY (orderid) REFERENCES sales."order"(orderid);


--
-- TOC entry 5045 (class 2606 OID 17251)
-- Name: product product_supplier_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product
    ADD CONSTRAINT product_supplier_fk FOREIGN KEY (supplierid) REFERENCES sales.supplier(supplierid);


--
-- TOC entry 5050 (class 2606 OID 17311)
-- Name: productwarehouse productwarehouseid_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.productwarehouse
    ADD CONSTRAINT productwarehouseid_product_fk FOREIGN KEY (productid) REFERENCES sales.product(productid);


--
-- TOC entry 5051 (class 2606 OID 32905)
-- Name: productwarehouse productwarehouseid_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.productwarehouse
    ADD CONSTRAINT productwarehouseid_warehouse_fk FOREIGN KEY (warehouseid) REFERENCES sales.warehouse(warehouseid);


--
-- TOC entry 5046 (class 2606 OID 32852)
-- Name: shift shift_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift
    ADD CONSTRAINT shift_person_fk FOREIGN KEY (personcode) REFERENCES sales.person(personid);


--
-- TOC entry 5047 (class 2606 OID 16954)
-- Name: shiftperson shiftperson_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shiftperson
    ADD CONSTRAINT shiftperson_person_fk FOREIGN KEY (personid) REFERENCES sales.person(personid);


--
-- TOC entry 5060 (class 2606 OID 49455)
-- Name: stock_movements stock_movements_inventory_documents_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_inventory_documents_fk FOREIGN KEY (document_id) REFERENCES sales.inventory_documents(id);


--
-- TOC entry 5061 (class 2606 OID 49465)
-- Name: stock_movements stock_movements_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_product_fk FOREIGN KEY (product_id) REFERENCES sales.product(productid);


--
-- TOC entry 5062 (class 2606 OID 49460)
-- Name: stock_movements stock_movements_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES sales.warehouse(warehouseid);


--
-- TOC entry 5048 (class 2606 OID 32874)
-- Name: techproduct techproduct_dish_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_dish_fk FOREIGN KEY ("DishId") REFERENCES sales.dish(dishid);


--
-- TOC entry 5049 (class 2606 OID 17316)
-- Name: techproduct techproduct_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_product_fk FOREIGN KEY (productid) REFERENCES sales.product(productid);


-- Completed on 2026-02-23 15:05:35

--
-- PostgreSQL database dump complete
--

\unrestrict ngKykAFhmKcLRWh4YazsIga5VsooeQO6ISZ09JWE89UrhCqL4RBwF9jWXRq96sP

