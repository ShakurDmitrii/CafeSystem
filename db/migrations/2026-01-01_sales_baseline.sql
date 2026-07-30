--
-- PostgreSQL database dump
--


-- Dumped from database version 16.12
-- Dumped by pg_dump version 16.12

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: sales; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA sales;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: client; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client (
    fullname character varying,
    clientid integer NOT NULL,
    number character varying
);


--
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
-- Name: clientdish; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.clientdish (
    dishid integer NOT NULL,
    clientid integer NOT NULL,
    dishname character varying NOT NULL
);


--
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
-- Name: consignmentnote; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.consignmentnote (
    supplierid integer NOT NULL,
    amount double precision,
    date date NOT NULL,
    consignmentid integer NOT NULL
);


--
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
-- Name: consproduct; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.consproduct (
    consignmentid integer NOT NULL,
    productid integer NOT NULL,
    gross double precision,
    quantity double precision,
    consproductid integer NOT NULL
);


--
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
-- Name: dish; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.dish (
    dishname character varying NOT NULL,
    weight double precision NOT NULL,
    firstcost double precision NOT NULL,
    price double precision NOT NULL,
    techproductid integer NOT NULL,
    dishid integer NOT NULL,
    category character varying,
    image_url character varying(1024),
    category_id integer
);


--
-- Name: dish_category; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.dish_category (
    category_id integer NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: dish_category_category_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

CREATE SEQUENCE sales.dish_category_category_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dish_category_category_id_seq; Type: SEQUENCE OWNED BY; Schema: sales; Owner: -
--

ALTER SEQUENCE sales.dish_category_category_id_seq OWNED BY sales.dish_category.category_id;


--
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
-- Name: dish_set; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.dish_set (
    setid integer NOT NULL,
    setname character varying(255) NOT NULL,
    price double precision DEFAULT 0 NOT NULL,
    first_cost double precision DEFAULT 0 NOT NULL,
    image_url character varying(1000),
    CONSTRAINT dish_set_first_cost_nonnegative_chk CHECK ((first_cost >= (0)::double precision)),
    CONSTRAINT dish_set_price_nonnegative_chk CHECK ((price >= (0)::double precision))
);


--
-- Name: dish_set_item; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.dish_set_item (
    set_item_id integer NOT NULL,
    set_id integer NOT NULL,
    dish_id integer NOT NULL,
    qty integer DEFAULT 1 NOT NULL,
    CONSTRAINT dish_set_item_qty_positive_chk CHECK ((qty > 0))
);


--
-- Name: dish_set_item_set_item_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.dish_set_item ALTER COLUMN set_item_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.dish_set_item_set_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: dish_set_setid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.dish_set ALTER COLUMN setid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.dish_set_setid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
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
-- Name: inventory_shift_report; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.inventory_shift_report (
    id integer NOT NULL,
    warehouse_id integer NOT NULL,
    shift_id integer NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    applied_at timestamp without time zone,
    snapshot_available boolean DEFAULT false NOT NULL
);


--
-- Name: inventory_shift_report_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.inventory_shift_report ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.inventory_shift_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: inventory_shift_report_line; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.inventory_shift_report_line (
    id integer NOT NULL,
    report_id integer NOT NULL,
    product_id integer NOT NULL,
    product_name character varying(255) NOT NULL,
    unit character varying(50) DEFAULT 'g'::character varying NOT NULL,
    sold_qty double precision DEFAULT 0 NOT NULL,
    system_qty double precision DEFAULT 0 NOT NULL,
    actual_qty double precision,
    discrepancy_qty double precision,
    shortage_qty double precision DEFAULT 0 NOT NULL,
    shortage_flag boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    opening_qty double precision DEFAULT 0 NOT NULL,
    movement_in_qty double precision DEFAULT 0 NOT NULL,
    movement_out_qty double precision DEFAULT 0 NOT NULL,
    movement_net_qty double precision DEFAULT 0 NOT NULL,
    expected_qty double precision DEFAULT 0 NOT NULL
);


--
-- Name: inventory_shift_report_line_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.inventory_shift_report_line ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.inventory_shift_report_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: order; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales."order" (
    shiftid integer NOT NULL,
    "Date" date NOT NULL,
    clientid integer,
    amount double precision NOT NULL,
    status boolean NOT NULL,
    orderid integer NOT NULL,
    type boolean NOT NULL,
    "time" double precision NOT NULL,
    timedelay double precision,
    created_at timestamp without time zone,
    duty boolean NOT NULL,
    date_issue date,
    debt_payment_date date,
    delivery_phone character varying(50),
    delivery_address character varying(255),
    payment_type character varying(20),
    is_paid boolean
);


--
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
-- Name: orderdish; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.orderdish (
    id integer NOT NULL,
    orderid integer,
    dishid integer,
    qty integer NOT NULL,
    set_id integer,
    CONSTRAINT orderdish_item_target_chk CHECK (((
CASE
    WHEN (dishid IS NOT NULL) THEN 1
    ELSE 0
END +
CASE
    WHEN (set_id IS NOT NULL) THEN 1
    ELSE 0
END) = 1))
);


--
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
-- Name: person; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.person (
    personid integer NOT NULL,
    name character varying NOT NULL,
    salary numeric NOT NULL,
    numdays integer,
    salaryperday numeric NOT NULL,
    archived boolean DEFAULT false NOT NULL
);


--
-- Name: person_personid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

CREATE SEQUENCE sales.person_personid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: person_personid_seq; Type: SEQUENCE OWNED BY; Schema: sales; Owner: -
--

ALTER SEQUENCE sales.person_personid_seq OWNED BY sales.person.personid;


--
-- Name: preparation; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.preparation (
    preparationid integer NOT NULL,
    preparationname character varying(255) NOT NULL,
    output_weight double precision DEFAULT 1 NOT NULL,
    CONSTRAINT preparation_output_weight_positive_chk CHECK ((output_weight > (0)::double precision))
);


--
-- Name: preparation_preparationid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.preparation ALTER COLUMN preparationid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.preparation_preparationid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: preparationwarehouse; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.preparationwarehouse (
    warehouseid integer NOT NULL,
    preparationid integer NOT NULL,
    preparationwarehouseid integer NOT NULL,
    quantity double precision DEFAULT 0 NOT NULL,
    CONSTRAINT preparationwarehouse_quantity_nonnegative_chk CHECK ((quantity >= (0)::double precision))
);


--
-- Name: preparationwarehouse_preparationwarehouseid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.preparationwarehouse ALTER COLUMN preparationwarehouseid ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.preparationwarehouse_preparationwarehouseid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: product; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.product (
    supplierid integer,
    productname character varying NOT NULL,
    productprice numeric NOT NULL,
    waste double precision,
    isfavourite boolean NOT NULL,
    productid integer NOT NULL,
    image_url character varying(1024),
    unit character varying(16) NOT NULL,
    base_unit character varying(16) NOT NULL,
    unit_factor numeric(18,6) NOT NULL
);


--
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
-- Name: product_supplier; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.product_supplier (
    product_id integer NOT NULL,
    supplier_id integer NOT NULL
);


--
-- Name: productwarehouse; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.productwarehouse (
    warehouseid integer,
    productid integer,
    productwarehouseid integer NOT NULL,
    quantity double precision
);


--
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
-- Name: shift_inventory_snapshot; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.shift_inventory_snapshot (
    id integer NOT NULL,
    shift_id integer NOT NULL,
    warehouse_id integer NOT NULL,
    product_id integer NOT NULL,
    quantity double precision DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: shift_inventory_snapshot_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.shift_inventory_snapshot ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.shift_inventory_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: shiftperson; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.shiftperson (
    shiftpersonid integer NOT NULL,
    shiftid integer NOT NULL,
    personid integer
);


--
-- Name: shiftperson_shiftpersonid_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

CREATE SEQUENCE sales.shiftperson_shiftpersonid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shiftperson_shiftpersonid_seq; Type: SEQUENCE OWNED BY; Schema: sales; Owner: -
--

ALTER SEQUENCE sales.shiftperson_shiftpersonid_seq OWNED BY sales.shiftperson.shiftpersonid;


--
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
-- Name: supplier; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.supplier (
    suppliername character varying NOT NULL,
    communication character varying,
    supplierid integer NOT NULL
);


--
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
-- Name: tax_outbox; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.tax_outbox (
    id bigint NOT NULL,
    aggregate_type character varying(50) DEFAULT 'order'::character varying NOT NULL,
    aggregate_id integer NOT NULL,
    event_type character varying(60) NOT NULL,
    event_key character varying(140) NOT NULL,
    payload_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    available_at timestamp without time zone DEFAULT now() NOT NULL,
    locked_at timestamp without time zone,
    processed_at timestamp without time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT tax_outbox_attempt_nonnegative_chk CHECK ((attempt_count >= 0)),
    CONSTRAINT tax_outbox_status_chk CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'processing'::character varying, 'processed'::character varying, 'failed'::character varying, 'dead_letter'::character varying])::text[])))
);


--
-- Name: tax_outbox_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.tax_outbox ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.tax_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: techproduct; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.techproduct (
    "DishId" integer,
    productid integer,
    waste double precision,
    weight double precision NOT NULL,
    techproductid integer NOT NULL,
    preparation_id integer,
    ingredient_preparation_id integer,
    CONSTRAINT techproduct_ingredient_chk CHECK (((
CASE
    WHEN (productid IS NOT NULL) THEN 1
    ELSE 0
END +
CASE
    WHEN (ingredient_preparation_id IS NOT NULL) THEN 1
    ELSE 0
END) = 1)),
    CONSTRAINT techproduct_owner_chk CHECK (((
CASE
    WHEN ("DishId" IS NOT NULL) THEN 1
    ELSE 0
END +
CASE
    WHEN (preparation_id IS NOT NULL) THEN 1
    ELSE 0
END) = 1))
);


--
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
-- Name: user_account; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.user_account (
    id integer NOT NULL,
    personid integer NOT NULL,
    username character varying NOT NULL,
    password_hash character varying(255),
    role character varying NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT person_role_chk CHECK (((role)::text = ANY (ARRAY[('OWNER'::character varying)::text, ('WORKER'::character varying)::text])))
);


--
-- Name: user_account_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

CREATE SEQUENCE sales.user_account_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_account_id_seq; Type: SEQUENCE OWNED BY; Schema: sales; Owner: -
--

ALTER SEQUENCE sales.user_account_id_seq OWNED BY sales.user_account.id;


--
-- Name: warehouse; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.warehouse (
    warehousename character varying NOT NULL,
    warehouseid integer NOT NULL,
    is_main boolean DEFAULT false NOT NULL
);


--
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
-- Name: dish_category category_id; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_category ALTER COLUMN category_id SET DEFAULT nextval('sales.dish_category_category_id_seq'::regclass);


--
-- Name: person personid; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.person ALTER COLUMN personid SET DEFAULT nextval('sales.person_personid_seq'::regclass);


--
-- Name: shiftperson shiftpersonid; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shiftperson ALTER COLUMN shiftpersonid SET DEFAULT nextval('sales.shiftperson_shiftpersonid_seq'::regclass);


--
-- Name: user_account id; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.user_account ALTER COLUMN id SET DEFAULT nextval('sales.user_account_id_seq'::regclass);


--
-- Name: client client_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client
    ADD CONSTRAINT client_pk PRIMARY KEY (clientid);


--
-- Name: clientdish clientdish_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.clientdish
    ADD CONSTRAINT clientdish_pk PRIMARY KEY (dishid);


--
-- Name: consignmentnote consignmentnote_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consignmentnote
    ADD CONSTRAINT consignmentnote_pk PRIMARY KEY (consignmentid);


--
-- Name: consproduct consproduct_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consproduct
    ADD CONSTRAINT consproduct_pk PRIMARY KEY (consproductid);


--
-- Name: dish_category dish_category_name_key; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_category
    ADD CONSTRAINT dish_category_name_key UNIQUE (name);


--
-- Name: dish_category dish_category_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_category
    ADD CONSTRAINT dish_category_pkey PRIMARY KEY (category_id);


--
-- Name: dish dish_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish
    ADD CONSTRAINT dish_pk PRIMARY KEY (dishid);


--
-- Name: dish_set_item dish_set_item_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_set_item
    ADD CONSTRAINT dish_set_item_pkey PRIMARY KEY (set_item_id);


--
-- Name: dish_set dish_set_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_set
    ADD CONSTRAINT dish_set_pkey PRIMARY KEY (setid);


--
-- Name: dish dish_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish
    ADD CONSTRAINT dish_unique UNIQUE (techproductid);


--
-- Name: inventory_document_lines inventory_document_lines_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_document_lines
    ADD CONSTRAINT inventory_document_lines_pk PRIMARY KEY (id);


--
-- Name: inventory_documents inventory_documents_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_documents
    ADD CONSTRAINT inventory_documents_pk PRIMARY KEY (id);


--
-- Name: inventory_shift_report_line inventory_shift_report_line_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_shift_report_line
    ADD CONSTRAINT inventory_shift_report_line_pkey PRIMARY KEY (id);


--
-- Name: inventory_shift_report inventory_shift_report_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_shift_report
    ADD CONSTRAINT inventory_shift_report_pkey PRIMARY KEY (id);


--
-- Name: orderdish newtable_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT newtable_pk PRIMARY KEY (id);


--
-- Name: order order_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales."order"
    ADD CONSTRAINT order_pk PRIMARY KEY (orderid);


--
-- Name: person person_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.person
    ADD CONSTRAINT person_pk PRIMARY KEY (personid);


--
-- Name: preparation preparation_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparation
    ADD CONSTRAINT preparation_pkey PRIMARY KEY (preparationid);


--
-- Name: preparationwarehouse preparationwarehouse_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparationwarehouse
    ADD CONSTRAINT preparationwarehouse_pkey PRIMARY KEY (preparationwarehouseid);


--
-- Name: product product_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product
    ADD CONSTRAINT product_pk PRIMARY KEY (productid);


--
-- Name: product_supplier product_supplier_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product_supplier
    ADD CONSTRAINT product_supplier_pkey PRIMARY KEY (product_id, supplier_id);


--
-- Name: productwarehouse productwarehouse_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.productwarehouse
    ADD CONSTRAINT productwarehouse_pk PRIMARY KEY (productwarehouseid);


--
-- Name: shift_inventory_snapshot shift_inventory_snapshot_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift_inventory_snapshot
    ADD CONSTRAINT shift_inventory_snapshot_pkey PRIMARY KEY (id);


--
-- Name: shift shift_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift
    ADD CONSTRAINT shift_pk PRIMARY KEY (id);


--
-- Name: shiftperson shiftperson_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shiftperson
    ADD CONSTRAINT shiftperson_pk PRIMARY KEY (shiftpersonid);


--
-- Name: stock_movements stock_movements_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_pk PRIMARY KEY (id);


--
-- Name: supplier supplier_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier
    ADD CONSTRAINT supplier_pk PRIMARY KEY (supplierid);


--
-- Name: supplier_price_history supplier_price_history_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier_price_history
    ADD CONSTRAINT supplier_price_history_pk PRIMARY KEY (id);


--
-- Name: supplier_price_history supplier_price_history_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier_price_history
    ADD CONSTRAINT supplier_price_history_unique UNIQUE (supplier_id);


--
-- Name: supplier_price_history supplier_price_history_unique_1; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.supplier_price_history
    ADD CONSTRAINT supplier_price_history_unique_1 UNIQUE (product_id);


--
-- Name: tax_outbox tax_outbox_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.tax_outbox
    ADD CONSTRAINT tax_outbox_pkey PRIMARY KEY (id);


--
-- Name: techproduct techproduct_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_pk PRIMARY KEY (techproductid);


--
-- Name: user_account user_account_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.user_account
    ADD CONSTRAINT user_account_pk PRIMARY KEY (id);


--
-- Name: user_account user_account_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.user_account
    ADD CONSTRAINT user_account_unique UNIQUE (personid);


--
-- Name: user_account user_account_unique_1; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.user_account
    ADD CONSTRAINT user_account_unique_1 UNIQUE (username);


--
-- Name: warehouse warehouse_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.warehouse
    ADD CONSTRAINT warehouse_pk PRIMARY KEY (warehouseid);


--
-- Name: warehouse warehouse_unique; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.warehouse
    ADD CONSTRAINT warehouse_unique UNIQUE (warehousename);


--
-- Name: dish_category_id_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX dish_category_id_idx ON sales.dish USING btree (category_id);


--
-- Name: dish_set_item_dish_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX dish_set_item_dish_idx ON sales.dish_set_item USING btree (dish_id);


--
-- Name: dish_set_item_set_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX dish_set_item_set_idx ON sales.dish_set_item USING btree (set_id);


--
-- Name: dish_set_name_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX dish_set_name_uq_idx ON sales.dish_set USING btree (lower((setname)::text));


--
-- Name: inventory_shift_report_line_report_product_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX inventory_shift_report_line_report_product_uq_idx ON sales.inventory_shift_report_line USING btree (report_id, product_id);


--
-- Name: inventory_shift_report_line_report_sort_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX inventory_shift_report_line_report_sort_idx ON sales.inventory_shift_report_line USING btree (report_id, sort_order);


--
-- Name: inventory_shift_report_wh_shift_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX inventory_shift_report_wh_shift_uq_idx ON sales.inventory_shift_report USING btree (warehouse_id, shift_id);


--
-- Name: orderdish_set_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX orderdish_set_idx ON sales.orderdish USING btree (set_id);


--
-- Name: preparation_name_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX preparation_name_uq_idx ON sales.preparation USING btree (lower((preparationname)::text));


--
-- Name: preparationwarehouse_prep_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX preparationwarehouse_prep_idx ON sales.preparationwarehouse USING btree (preparationid);


--
-- Name: preparationwarehouse_wh_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX preparationwarehouse_wh_idx ON sales.preparationwarehouse USING btree (warehouseid);


--
-- Name: shift_inventory_snapshot_shift_wh_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX shift_inventory_snapshot_shift_wh_idx ON sales.shift_inventory_snapshot USING btree (shift_id, warehouse_id);


--
-- Name: shift_inventory_snapshot_shift_wh_product_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX shift_inventory_snapshot_shift_wh_product_uq_idx ON sales.shift_inventory_snapshot USING btree (shift_id, warehouse_id, product_id);


--
-- Name: shift_open_unique_person_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX shift_open_unique_person_idx ON sales.shift USING btree (personcode) WHERE ((endtime IS NULL) AND (personcode IS NOT NULL));


--
-- Name: tax_outbox_aggregate_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX tax_outbox_aggregate_idx ON sales.tax_outbox USING btree (aggregate_type, aggregate_id);


--
-- Name: tax_outbox_created_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX tax_outbox_created_idx ON sales.tax_outbox USING btree (created_at);


--
-- Name: tax_outbox_event_key_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX tax_outbox_event_key_uq_idx ON sales.tax_outbox USING btree (event_key);


--
-- Name: tax_outbox_status_available_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX tax_outbox_status_available_idx ON sales.tax_outbox USING btree (status, available_at);


--
-- Name: techproduct_preparation_ingredient_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX techproduct_preparation_ingredient_idx ON sales.techproduct USING btree (ingredient_preparation_id);


--
-- Name: techproduct_preparation_owner_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX techproduct_preparation_owner_idx ON sales.techproduct USING btree (preparation_id);


--
-- Name: clientdish clientdish_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.clientdish
    ADD CONSTRAINT clientdish_client_fk FOREIGN KEY (clientid) REFERENCES sales.client(clientid);


--
-- Name: clientduty clientduty_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.clientduty
    ADD CONSTRAINT clientduty_client_fk FOREIGN KEY (clientid) REFERENCES sales.client(clientid);


--
-- Name: consignmentnote consignmentnote_supplier_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consignmentnote
    ADD CONSTRAINT consignmentnote_supplier_fk FOREIGN KEY (supplierid) REFERENCES sales.supplier(supplierid);


--
-- Name: consproduct consproduct_consignmentnote_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consproduct
    ADD CONSTRAINT consproduct_consignmentnote_fk FOREIGN KEY (consignmentid) REFERENCES sales.consignmentnote(consignmentid);


--
-- Name: consproduct consproduct_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consproduct
    ADD CONSTRAINT consproduct_product_fk FOREIGN KEY (productid) REFERENCES sales.product(productid);


--
-- Name: dish dish_category_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish
    ADD CONSTRAINT dish_category_fk FOREIGN KEY (category_id) REFERENCES sales.dish_category(category_id) ON DELETE SET NULL;


--
-- Name: dish_set_item dish_set_item_dish_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_set_item
    ADD CONSTRAINT dish_set_item_dish_fk FOREIGN KEY (dish_id) REFERENCES sales.dish(dishid);


--
-- Name: dish_set_item dish_set_item_set_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_set_item
    ADD CONSTRAINT dish_set_item_set_fk FOREIGN KEY (set_id) REFERENCES sales.dish_set(setid) ON DELETE CASCADE;


--
-- Name: inventory_document_lines inventory_document_lines_inventory_documents_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_document_lines
    ADD CONSTRAINT inventory_document_lines_inventory_documents_fk FOREIGN KEY (document_id) REFERENCES sales.inventory_documents(id);


--
-- Name: inventory_document_lines inventory_document_lines_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_document_lines
    ADD CONSTRAINT inventory_document_lines_product_fk FOREIGN KEY (product_id) REFERENCES sales.product(productid);


--
-- Name: inventory_shift_report_line inventory_shift_report_line_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_shift_report_line
    ADD CONSTRAINT inventory_shift_report_line_product_fk FOREIGN KEY (product_id) REFERENCES sales.product(productid);


--
-- Name: inventory_shift_report_line inventory_shift_report_line_report_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_shift_report_line
    ADD CONSTRAINT inventory_shift_report_line_report_fk FOREIGN KEY (report_id) REFERENCES sales.inventory_shift_report(id) ON DELETE CASCADE;


--
-- Name: inventory_shift_report inventory_shift_report_shift_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_shift_report
    ADD CONSTRAINT inventory_shift_report_shift_fk FOREIGN KEY (shift_id) REFERENCES sales.shift(id) ON DELETE CASCADE;


--
-- Name: inventory_shift_report inventory_shift_report_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.inventory_shift_report
    ADD CONSTRAINT inventory_shift_report_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES sales.warehouse(warehouseid) ON DELETE CASCADE;


--
-- Name: orderdish orderdish_dish_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT orderdish_dish_fk FOREIGN KEY (dishid) REFERENCES sales.dish(dishid);


--
-- Name: orderdish orderdish_order_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT orderdish_order_fk FOREIGN KEY (orderid) REFERENCES sales."order"(orderid);


--
-- Name: orderdish orderdish_set_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.orderdish
    ADD CONSTRAINT orderdish_set_fk FOREIGN KEY (set_id) REFERENCES sales.dish_set(setid);


--
-- Name: preparationwarehouse preparationwarehouse_preparation_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparationwarehouse
    ADD CONSTRAINT preparationwarehouse_preparation_fk FOREIGN KEY (preparationid) REFERENCES sales.preparation(preparationid) ON DELETE CASCADE;


--
-- Name: preparationwarehouse preparationwarehouse_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparationwarehouse
    ADD CONSTRAINT preparationwarehouse_warehouse_fk FOREIGN KEY (warehouseid) REFERENCES sales.warehouse(warehouseid) ON DELETE CASCADE;


--
-- Name: product product_supplier_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product
    ADD CONSTRAINT product_supplier_fk FOREIGN KEY (supplierid) REFERENCES sales.supplier(supplierid);


--
-- Name: product_supplier product_supplier_product_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product_supplier
    ADD CONSTRAINT product_supplier_product_id_fkey FOREIGN KEY (product_id) REFERENCES sales.product(productid) ON DELETE CASCADE;


--
-- Name: product_supplier product_supplier_supplier_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.product_supplier
    ADD CONSTRAINT product_supplier_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES sales.supplier(supplierid) ON DELETE CASCADE;


--
-- Name: productwarehouse productwarehouseid_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.productwarehouse
    ADD CONSTRAINT productwarehouseid_product_fk FOREIGN KEY (productid) REFERENCES sales.product(productid);


--
-- Name: productwarehouse productwarehouseid_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.productwarehouse
    ADD CONSTRAINT productwarehouseid_warehouse_fk FOREIGN KEY (warehouseid) REFERENCES sales.warehouse(warehouseid);


--
-- Name: shift_inventory_snapshot shift_inventory_snapshot_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift_inventory_snapshot
    ADD CONSTRAINT shift_inventory_snapshot_product_fk FOREIGN KEY (product_id) REFERENCES sales.product(productid);


--
-- Name: shift_inventory_snapshot shift_inventory_snapshot_shift_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift_inventory_snapshot
    ADD CONSTRAINT shift_inventory_snapshot_shift_fk FOREIGN KEY (shift_id) REFERENCES sales.shift(id) ON DELETE CASCADE;


--
-- Name: shift_inventory_snapshot shift_inventory_snapshot_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift_inventory_snapshot
    ADD CONSTRAINT shift_inventory_snapshot_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES sales.warehouse(warehouseid) ON DELETE CASCADE;


--
-- Name: shift shift_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shift
    ADD CONSTRAINT shift_person_fk FOREIGN KEY (personcode) REFERENCES sales.person(personid);


--
-- Name: shiftperson shiftperson_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shiftperson
    ADD CONSTRAINT shiftperson_person_fk FOREIGN KEY (personid) REFERENCES sales.person(personid);


--
-- Name: stock_movements stock_movements_inventory_documents_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_inventory_documents_fk FOREIGN KEY (document_id) REFERENCES sales.inventory_documents(id);


--
-- Name: stock_movements stock_movements_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_product_fk FOREIGN KEY (product_id) REFERENCES sales.product(productid);


--
-- Name: stock_movements stock_movements_warehouse_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.stock_movements
    ADD CONSTRAINT stock_movements_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES sales.warehouse(warehouseid);


--
-- Name: techproduct techproduct_dish_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_dish_fk FOREIGN KEY ("DishId") REFERENCES sales.dish(dishid);


--
-- Name: techproduct techproduct_ingredient_preparation_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_ingredient_preparation_fk FOREIGN KEY (ingredient_preparation_id) REFERENCES sales.preparation(preparationid);


--
-- Name: techproduct techproduct_preparation_owner_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_preparation_owner_fk FOREIGN KEY (preparation_id) REFERENCES sales.preparation(preparationid) ON DELETE CASCADE;


--
-- Name: techproduct techproduct_product_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.techproduct
    ADD CONSTRAINT techproduct_product_fk FOREIGN KEY (productid) REFERENCES sales.product(productid);


--
-- Name: user_account user_account_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.user_account
    ADD CONSTRAINT user_account_person_fk FOREIGN KEY (personid) REFERENCES sales.person(personid);


--
-- PostgreSQL database dump complete
--


