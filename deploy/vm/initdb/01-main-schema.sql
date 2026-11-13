--
-- PostgreSQL database dump
--

\restrict S75tquxHc6PDGBjJENHnZetE2BeosPgdIk4kjEJ44zcnCor3fGiTElih0Mg2ZVo

-- Dumped from database version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)

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


--
-- Name: prevent_duplicate_product_name(); Type: FUNCTION; Schema: sales; Owner: -
--

CREATE FUNCTION sales.prevent_duplicate_product_name() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sales.product existing
        WHERE LOWER(BTRIM(existing.productname)) = LOWER(BTRIM(NEW.productname))
          AND existing.productid <> COALESCE(NEW.productid, -1)
    ) THEN
        RAISE EXCEPTION 'Ingredient with normalized name "%" already exists', NEW.productname
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: cafehelp_schema_migration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cafehelp_schema_migration (
    id bigint NOT NULL,
    migration_name character varying(255) NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    applied_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: cafehelp_schema_migration_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cafehelp_schema_migration_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cafehelp_schema_migration_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cafehelp_schema_migration_id_seq OWNED BY public.cafehelp_schema_migration.id;


--
-- Name: client; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client (
    fullname character varying,
    clientid integer NOT NULL,
    number character varying,
    normalized_number character varying(32)
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
-- Name: client_vk_link; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client_vk_link (
    id bigint NOT NULL,
    client_id integer NOT NULL,
    vk_user_id bigint NOT NULL,
    vk_domain character varying(255),
    verified_at timestamp without time zone DEFAULT now() NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: client_vk_link_attempt; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client_vk_link_attempt (
    vk_user_id bigint NOT NULL,
    failed_attempts integer DEFAULT 0 NOT NULL,
    window_started_at timestamp without time zone DEFAULT now() NOT NULL,
    blocked_until timestamp without time zone,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT client_vk_link_attempt_nonnegative_chk CHECK ((failed_attempts >= 0))
);


--
-- Name: client_vk_link_code; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client_vk_link_code (
    id bigint NOT NULL,
    client_id integer NOT NULL,
    code_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    code_fingerprint character varying(64)
);


--
-- Name: client_vk_link_code_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.client_vk_link_code ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.client_vk_link_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: client_vk_link_event; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.client_vk_link_event (
    id bigint NOT NULL,
    client_id integer,
    vk_user_id bigint NOT NULL,
    event_type character varying(20) NOT NULL,
    vk_domain character varying(255),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT client_vk_link_event_type_chk CHECK (((event_type)::text = ANY ((ARRAY['LINKED'::character varying, 'RELINKED'::character varying, 'UNLINKED'::character varying])::text[])))
);


--
-- Name: client_vk_link_event_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.client_vk_link_event ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.client_vk_link_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: client_vk_link_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.client_vk_link ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.client_vk_link_id_seq
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
-- Name: consumable_rule; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.consumable_rule (
    product_id integer NOT NULL,
    basis character varying(24) NOT NULL,
    default_quantity numeric(18,6) NOT NULL,
    trigger_quantity numeric(18,6) DEFAULT 1 NOT NULL,
    dish_category_id integer,
    active boolean DEFAULT true NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT consumable_rule_basis_chk CHECK (((basis)::text = ANY ((ARRAY['per_person'::character varying, 'per_order'::character varying, 'per_menu_item'::character varying])::text[]))),
    CONSTRAINT consumable_rule_default_quantity_chk CHECK ((default_quantity >= (0)::numeric)),
    CONSTRAINT consumable_rule_trigger_quantity_chk CHECK ((trigger_quantity > (0)::numeric))
);


--
-- Name: debt_payment; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.debt_payment (
    id bigint NOT NULL,
    order_id integer NOT NULL,
    client_id integer NOT NULL,
    amount numeric(12,2) NOT NULL,
    remaining_after numeric(12,2) NOT NULL,
    payment_type character varying(20) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT debt_payment_amount_positive_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT debt_payment_remaining_nonnegative_chk CHECK ((remaining_after >= (0)::numeric))
);


--
-- Name: debt_payment_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.debt_payment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.debt_payment_id_seq
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
    is_paid boolean,
    cancelled_at timestamp without time zone,
    cancel_reason character varying(500),
    version integer DEFAULT 0 NOT NULL,
    debt_original_amount numeric(12,2),
    debt_remaining_amount numeric(12,2),
    inventory_consumed boolean DEFAULT false NOT NULL,
    paid_at timestamp without time zone,
    person_count integer DEFAULT 1 NOT NULL,
    cash_received numeric(14,2),
    cash_change numeric(14,2),
    discount_amount numeric(14,2) DEFAULT 0 NOT NULL,
    ready_at timestamp without time zone,
    CONSTRAINT order_cash_change_non_negative CHECK (((cash_change IS NULL) OR (cash_change >= (0)::numeric))),
    CONSTRAINT order_cash_received_non_negative CHECK (((cash_received IS NULL) OR (cash_received >= (0)::numeric))),
    CONSTRAINT order_discount_amount_non_negative CHECK ((discount_amount >= (0)::numeric)),
    CONSTRAINT order_person_count_chk CHECK (((person_count >= 1) AND (person_count <= 1000))),
    CONSTRAINT order_ready_at_after_created CHECK (((ready_at IS NULL) OR (created_at IS NULL) OR (ready_at > created_at)))
);


--
-- Name: order_consumable; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.order_consumable (
    id bigint NOT NULL,
    order_id integer NOT NULL,
    product_id integer NOT NULL,
    suggested_quantity numeric(18,6) NOT NULL,
    actual_quantity numeric(18,6) NOT NULL,
    surcharge_amount numeric(14,2) DEFAULT 0 NOT NULL,
    inventory_cost numeric(18,6),
    product_name_snapshot character varying(255) NOT NULL,
    base_unit_snapshot character varying(16) NOT NULL,
    manual_override boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT order_consumable_quantity_chk CHECK (((suggested_quantity >= (0)::numeric) AND (actual_quantity >= (0)::numeric))),
    CONSTRAINT order_consumable_surcharge_chk CHECK ((surcharge_amount >= (0)::numeric))
);


--
-- Name: order_consumable_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.order_consumable ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.order_consumable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
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
    unit_price double precision DEFAULT 0 NOT NULL,
    unit_cost double precision DEFAULT 0 NOT NULL,
    CONSTRAINT orderdish_item_target_chk CHECK (((
CASE
    WHEN (dishid IS NOT NULL) THEN 1
    ELSE 0
END +
CASE
    WHEN (set_id IS NOT NULL) THEN 1
    ELSE 0
END) = 1)),
    CONSTRAINT orderdish_unit_cost_nonnegative_chk CHECK ((unit_cost >= (0)::double precision)),
    CONSTRAINT orderdish_unit_price_nonnegative_chk CHECK ((unit_price >= (0)::double precision))
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
-- Name: preparation_stock_movements; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.preparation_stock_movements (
    id integer NOT NULL,
    movement_date timestamp without time zone DEFAULT now() NOT NULL,
    warehouse_id integer NOT NULL,
    preparation_id integer NOT NULL,
    qty_in numeric(19,6) DEFAULT 0 NOT NULL,
    qty_out numeric(19,6) DEFAULT 0 NOT NULL,
    unit_cost numeric(19,6) DEFAULT 0 NOT NULL,
    amount numeric(19,6) DEFAULT 0 NOT NULL,
    movement_type character varying(40) NOT NULL,
    source_type character varying(40),
    source_id integer,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: preparation_stock_movements_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

ALTER TABLE sales.preparation_stock_movements ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sales.preparation_stock_movements_id_seq
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
    inventory_value numeric(19,6) DEFAULT 0 NOT NULL,
    average_unit_cost numeric(19,6) DEFAULT 0 NOT NULL,
    valuation_initialized boolean DEFAULT false NOT NULL,
    valuation_updated_at timestamp without time zone DEFAULT now() NOT NULL,
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
    unit_factor numeric(18,6) NOT NULL,
    item_type character varying(24) DEFAULT 'ingredient'::character varying NOT NULL,
    CONSTRAINT product_item_type_chk CHECK (((item_type)::text = ANY ((ARRAY['ingredient'::character varying, 'consumable'::character varying, 'packaging'::character varying])::text[]))),
    CONSTRAINT product_unit_factor_positive_chk CHECK ((unit_factor > (0)::numeric))
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
    supplier_id integer NOT NULL,
    default_price numeric(19,6),
    purchase_unit character varying(16),
    purchase_unit_factor numeric(19,6),
    supplier_sku character varying(100),
    is_active boolean DEFAULT true NOT NULL,
    last_price_at timestamp without time zone
);


--
-- Name: productwarehouse; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.productwarehouse (
    warehouseid integer,
    productid integer,
    productwarehouseid integer NOT NULL,
    quantity double precision,
    inventory_value numeric(19,6) DEFAULT 0 NOT NULL,
    average_unit_cost numeric(19,6) DEFAULT 0 NOT NULL,
    valuation_initialized boolean DEFAULT false NOT NULL,
    valuation_updated_at timestamp without time zone DEFAULT now() NOT NULL
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
-- Name: salary_accrual; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.salary_accrual (
    id bigint NOT NULL,
    person_id integer NOT NULL,
    shift_id integer NOT NULL,
    worked_on date NOT NULL,
    daily_rate numeric(14,2) NOT NULL,
    amount numeric(14,2) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT salary_accrual_amount_nonnegative_chk CHECK ((amount >= (0)::numeric)),
    CONSTRAINT salary_accrual_daily_rate_nonnegative_chk CHECK ((daily_rate >= (0)::numeric))
);


--
-- Name: TABLE salary_accrual; Type: COMMENT; Schema: sales; Owner: -
--

COMMENT ON TABLE sales.salary_accrual IS 'Immutable per-shift salary accruals created when a shift is closed. The server ledger starts with shifts closed after this migration.';


--
-- Name: salary_accrual_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

CREATE SEQUENCE sales.salary_accrual_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salary_accrual_id_seq; Type: SEQUENCE OWNED BY; Schema: sales; Owner: -
--

ALTER SEQUENCE sales.salary_accrual_id_seq OWNED BY sales.salary_accrual.id;


--
-- Name: salary_payment; Type: TABLE; Schema: sales; Owner: -
--

CREATE TABLE sales.salary_payment (
    id bigint NOT NULL,
    person_id integer NOT NULL,
    entry_type character varying(20) NOT NULL,
    amount numeric(14,2) NOT NULL,
    balance_after numeric(14,2) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    author_account_id integer NOT NULL,
    related_payment_id bigint,
    comment character varying(500),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT salary_payment_amount_positive_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT salary_payment_balance_nonnegative_chk CHECK ((balance_after >= (0)::numeric)),
    CONSTRAINT salary_payment_relation_chk CHECK (((((entry_type)::text = 'PAYMENT'::text) AND (related_payment_id IS NULL)) OR (((entry_type)::text = 'REVERSAL'::text) AND (related_payment_id IS NOT NULL)))),
    CONSTRAINT salary_payment_type_chk CHECK (((entry_type)::text = ANY ((ARRAY['PAYMENT'::character varying, 'REVERSAL'::character varying])::text[])))
);


--
-- Name: TABLE salary_payment; Type: COMMENT; Schema: sales; Owner: -
--

COMMENT ON TABLE sales.salary_payment IS 'Immutable salary payment ledger. Corrections are stored as REVERSAL entries rather than deleting history.';


--
-- Name: salary_payment_id_seq; Type: SEQUENCE; Schema: sales; Owner: -
--

CREATE SEQUENCE sales.salary_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salary_payment_id_seq; Type: SEQUENCE OWNED BY; Schema: sales; Owner: -
--

ALTER SEQUENCE sales.salary_payment_id_seq OWNED BY sales.salary_payment.id;


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
    id integer NOT NULL,
    closed_at timestamp without time zone
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
    document_id integer,
    warehouse_id integer NOT NULL,
    product_id integer NOT NULL,
    qty_in numeric(14,3) DEFAULT 0,
    qty_out numeric(14,3) DEFAULT 0,
    unit_cost numeric(19,6),
    amount numeric(19,6),
    created_at timestamp without time zone DEFAULT now(),
    movement_type character varying(40),
    source_type character varying(40),
    source_id integer,
    created_by character varying(100),
    reason text
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
    CONSTRAINT tax_outbox_status_chk CHECK (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('processing'::character varying)::text, ('processed'::character varying)::text, ('failed'::character varying)::text, ('dead_letter'::character varying)::text])))
);


--
-- Name: COLUMN tax_outbox.locked_at; Type: COMMENT; Schema: sales; Owner: -
--

COMMENT ON COLUMN sales.tax_outbox.locked_at IS 'Claim timestamp. processing rows older than the worker lock timeout are returned to pending and delivered idempotently.';


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
-- Name: cafehelp_schema_migration id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafehelp_schema_migration ALTER COLUMN id SET DEFAULT nextval('public.cafehelp_schema_migration_id_seq'::regclass);


--
-- Name: dish_category category_id; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_category ALTER COLUMN category_id SET DEFAULT nextval('sales.dish_category_category_id_seq'::regclass);


--
-- Name: person personid; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.person ALTER COLUMN personid SET DEFAULT nextval('sales.person_personid_seq'::regclass);


--
-- Name: salary_accrual id; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_accrual ALTER COLUMN id SET DEFAULT nextval('sales.salary_accrual_id_seq'::regclass);


--
-- Name: salary_payment id; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_payment ALTER COLUMN id SET DEFAULT nextval('sales.salary_payment_id_seq'::regclass);


--
-- Name: shiftperson shiftpersonid; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.shiftperson ALTER COLUMN shiftpersonid SET DEFAULT nextval('sales.shiftperson_shiftpersonid_seq'::regclass);


--
-- Name: user_account id; Type: DEFAULT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.user_account ALTER COLUMN id SET DEFAULT nextval('sales.user_account_id_seq'::regclass);


--
-- Data for Name: cafehelp_schema_migration; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.cafehelp_schema_migration (id, migration_name, checksum_sha256, applied_at) FROM stdin;
1	2026-01-01_sales_baseline.sql	aa67c7442512698b22e13de34a172ec67e81463736df98642799480e03a2798a	2026-11-13 14:54:04.789034
2	2026-02-23_product_units.sql	39d95eaeb7737e34159728ca205231d1e16cc39aee9f58573821fbe5f0089a88	2026-11-13 14:54:04.881976
3	2026-03-06_images.sql	82e9930fdeca425d9cf2dcac223235d6e732200eeaac6bae94df20f47d7f148b	2026-11-13 14:54:04.887804
4	2026-03-06_user_account.sql	db77acc5079b5bc6d70adaf9f561401487fc7637458ef2f44b91e30f42a8044f	2026-11-13 14:54:04.890207
5	2026-03-10_dish_categories.sql	5323843f5afc78b08f4715626d279fcdaf2bec1f644b335975d3db34ff837307	2026-11-13 14:54:04.893226
6	2026-03-10_main_warehouse.sql	b8aeb8e4461de62aa3ac01231a32662480d011cf8bf4dd2fdd19cf5170599d8e	2026-11-13 14:54:04.900365
7	2026-03-10_product_supplier.sql	1fa756c08a602aa0296eeeaa5c6154cafd211710b12d4dd24c3d1c1277b980ea	2026-11-13 14:54:04.904108
8	2026-03-23_dish_sets.sql	aed4d7c6f0661ae32d528323d0e65f4957e2023cc8b3c94bfe9ca09e17035f5a	2026-11-13 14:54:04.907053
9	2026-03-23_preparations.sql	07ea7acc7cd11e537fb3cf0f4b465c7cc7f2cdf4c8d3f5b6f67537b029166344	2026-11-13 14:54:04.917457
10	2026-04-07_tax_outbox.sql	c9c83741b4bdfe6a3716c9e27aa6e90fb48443686b160ca4c79fadf54d80f2f6	2026-11-13 14:54:04.93183
11	2026-04-08_runtime_schema_alignment.sql	cccee97177b401ce0479375f5e71f41b99b0393a466938d08b60426ef7f6a315	2026-11-13 14:54:04.940763
12	2026-04-09_client_vk_link.sql	a551a73f2de202b5ce0971f1ca3bc9dd26e36567d21ae798020af6aec5795dda	2026-11-13 14:54:04.97165
13	2026-07-31_order_cancellation.sql	42b27c1a56bcb49c34163ce2bf45b798c1968ce14694760bce9e273ddb355d1f	2026-11-13 14:54:04.984284
14	2026-07-31_order_line_financial_snapshot.sql	21a2acb669ea667c8411dd6575216ef69beb7551987da460b61f598230252de3	2026-11-13 14:54:04.988906
15	2026-08-02_client_debt_integrity.sql	6cf54dcf0c013f74f623c71e5a700b8a21f76954eeaaa5e3c52e1d524340a19a	2026-11-13 14:54:05.000507
16	2026-08-07_employee_payroll_ledger.sql	7b23affa96175ff5770c77a142296ab122bde1e73d857d27bba074d0be97ca95	2026-11-13 14:54:05.015226
17	2026-08-07_z_catalog_integrity.sql	1a424476358031a092621a8bf9016f0da9119cf09086a5d8dfe0efe552d67770	2026-11-13 14:54:05.027797
18	2026-08-07_zz_tax_outbox_recovery.sql	086dc975dc2d4d16a72813db1b11cb2d507732fcabf6465c530e4a42a44808a3	2026-11-13 14:54:05.037032
19	2026-08-29_query_performance_indexes.sql	76c9c1f9654981b29bda41fb49ee4d504b63d9d00c5d098a3d480eef414dae14	2026-11-13 14:54:05.041725
20	2026-08-30_inventory_weighted_average_cost.sql	52a2eb1f20f4aff189e3a32d74ecfefb61f817994deb67b4cd44824603ea6d3a	2026-11-13 14:54:05.050905
21	2026-08-31_order_consumables.sql	946989ad03b84a6fff1ade5cb38c0e52c8b3a5c223717c276ce490bb26e08f8b	2026-11-13 14:54:05.069444
22	2026-09-01_cash_payment_change.sql	322bbbc152aeb9385ab175c3c680af1ac04c763057d03e01db8cc4c402b9830a	2026-11-13 14:54:05.079419
23	2026-11-10_order_discount.sql	e9255c6f1f1306b79c57374b1876b8f8cfcbd5340b87ad1d1ffc30e32680955a	2026-11-13 14:54:05.082023
24	2026-11-10_order_ready_at.sql	d36a38d990a66b353db8fe229d72bdef548d427d7e8b41d829f08b39402945a9	2026-11-13 14:54:05.084892
\.


--
-- Data for Name: client; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.client (fullname, clientid, number, normalized_number) FROM stdin;
\.


--
-- Data for Name: client_vk_link; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.client_vk_link (id, client_id, vk_user_id, vk_domain, verified_at, created_at) FROM stdin;
\.


--
-- Data for Name: client_vk_link_attempt; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.client_vk_link_attempt (vk_user_id, failed_attempts, window_started_at, blocked_until, updated_at) FROM stdin;
\.


--
-- Data for Name: client_vk_link_code; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.client_vk_link_code (id, client_id, code_hash, expires_at, used_at, created_at, code_fingerprint) FROM stdin;
\.


--
-- Data for Name: client_vk_link_event; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.client_vk_link_event (id, client_id, vk_user_id, event_type, vk_domain, created_at) FROM stdin;
\.


--
-- Data for Name: clientdish; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.clientdish (dishid, clientid, dishname) FROM stdin;
\.


--
-- Data for Name: clientduty; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.clientduty (clientid, clientname, number, duty, data) FROM stdin;
\.


--
-- Data for Name: consignmentnote; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.consignmentnote (supplierid, amount, date, consignmentid) FROM stdin;
\.


--
-- Data for Name: consproduct; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.consproduct (consignmentid, productid, gross, quantity, consproductid) FROM stdin;
\.


--
-- Data for Name: consumable_rule; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.consumable_rule (product_id, basis, default_quantity, trigger_quantity, dish_category_id, active, updated_at) FROM stdin;
\.


--
-- Data for Name: debt_payment; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.debt_payment (id, order_id, client_id, amount, remaining_after, payment_type, idempotency_key, created_at) FROM stdin;
\.


--
-- Data for Name: dish; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.dish (dishname, weight, firstcost, price, techproductid, dishid, category, image_url, category_id) FROM stdin;
\.


--
-- Data for Name: dish_category; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.dish_category (category_id, name, created_at) FROM stdin;
\.


--
-- Data for Name: dish_set; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.dish_set (setid, setname, price, first_cost, image_url) FROM stdin;
\.


--
-- Data for Name: dish_set_item; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.dish_set_item (set_item_id, set_id, dish_id, qty) FROM stdin;
\.


--
-- Data for Name: inventory_document_lines; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.inventory_document_lines (id, document_id, product_id, qty, unit_price, line_total, batch_ref) FROM stdin;
\.


--
-- Data for Name: inventory_documents; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.inventory_documents (id, doc_type, doc_date, supplier_id, warehouse_from_id, warehouse_to_id, status, comment, created_by, created_at) FROM stdin;
\.


--
-- Data for Name: inventory_shift_report; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.inventory_shift_report (id, warehouse_id, shift_id, created_at, updated_at, applied_at, snapshot_available) FROM stdin;
\.


--
-- Data for Name: inventory_shift_report_line; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.inventory_shift_report_line (id, report_id, product_id, product_name, unit, sold_qty, system_qty, actual_qty, discrepancy_qty, shortage_qty, shortage_flag, sort_order, opening_qty, movement_in_qty, movement_out_qty, movement_net_qty, expected_qty) FROM stdin;
\.


--
-- Data for Name: order; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales."order" (shiftid, "Date", clientid, amount, status, orderid, type, "time", timedelay, created_at, duty, date_issue, debt_payment_date, delivery_phone, delivery_address, payment_type, is_paid, cancelled_at, cancel_reason, version, debt_original_amount, debt_remaining_amount, inventory_consumed, paid_at, person_count, cash_received, cash_change, discount_amount, ready_at) FROM stdin;
\.


--
-- Data for Name: order_consumable; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.order_consumable (id, order_id, product_id, suggested_quantity, actual_quantity, surcharge_amount, inventory_cost, product_name_snapshot, base_unit_snapshot, manual_override, created_by, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: orderdish; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.orderdish (id, orderid, dishid, qty, set_id, unit_price, unit_cost) FROM stdin;
\.


--
-- Data for Name: person; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.person (personid, name, salaryperday, archived) FROM stdin;
\.


--
-- Data for Name: preparation; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.preparation (preparationid, preparationname, output_weight) FROM stdin;
\.


--
-- Data for Name: preparation_stock_movements; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.preparation_stock_movements (id, movement_date, warehouse_id, preparation_id, qty_in, qty_out, unit_cost, amount, movement_type, source_type, source_id, created_by, created_at) FROM stdin;
\.


--
-- Data for Name: preparationwarehouse; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.preparationwarehouse (warehouseid, preparationid, preparationwarehouseid, quantity, inventory_value, average_unit_cost, valuation_initialized, valuation_updated_at) FROM stdin;
\.


--
-- Data for Name: product; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.product (supplierid, productname, productprice, waste, isfavourite, productid, image_url, unit, base_unit, unit_factor, item_type) FROM stdin;
\.


--
-- Data for Name: product_supplier; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.product_supplier (product_id, supplier_id, default_price, purchase_unit, purchase_unit_factor, supplier_sku, is_active, last_price_at) FROM stdin;
\.


--
-- Data for Name: productwarehouse; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.productwarehouse (warehouseid, productid, productwarehouseid, quantity, inventory_value, average_unit_cost, valuation_initialized, valuation_updated_at) FROM stdin;
\.


--
-- Data for Name: salary_accrual; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.salary_accrual (id, person_id, shift_id, worked_on, daily_rate, amount, created_at) FROM stdin;
\.


--
-- Data for Name: salary_payment; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.salary_payment (id, person_id, entry_type, amount, balance_after, idempotency_key, author_account_id, related_payment_id, comment, created_at) FROM stdin;
\.


--
-- Data for Name: shift; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.shift (data, starttime, endtime, profit, expenses, income, personcode, id, closed_at) FROM stdin;
\.


--
-- Data for Name: shift_inventory_snapshot; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.shift_inventory_snapshot (id, shift_id, warehouse_id, product_id, quantity, created_at) FROM stdin;
\.


--
-- Data for Name: shiftperson; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.shiftperson (shiftpersonid, shiftid, personid) FROM stdin;
\.


--
-- Data for Name: stock_movements; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.stock_movements (id, movement_date, document_id, warehouse_id, product_id, qty_in, qty_out, unit_cost, amount, created_at, movement_type, source_type, source_id, created_by, reason) FROM stdin;
\.


--
-- Data for Name: supplier; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.supplier (suppliername, communication, supplierid) FROM stdin;
\.


--
-- Data for Name: supplier_price_history; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.supplier_price_history (supplier_id, product_id, id, price, valid_from, valid_to, source_doc_type, source_doc_id, created_at) FROM stdin;
\.


--
-- Data for Name: tax_outbox; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.tax_outbox (id, aggregate_type, aggregate_id, event_type, event_key, payload_json, status, available_at, locked_at, processed_at, attempt_count, last_error, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: techproduct; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.techproduct ("DishId", productid, waste, weight, techproductid, preparation_id, ingredient_preparation_id) FROM stdin;
\.


--
-- Data for Name: user_account; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.user_account (id, personid, username, password_hash, role, is_active, created_at) FROM stdin;
\.


--
-- Data for Name: warehouse; Type: TABLE DATA; Schema: sales; Owner: -
--

COPY sales.warehouse (warehousename, warehouseid, is_main) FROM stdin;
\.


--
-- Name: cafehelp_schema_migration_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.cafehelp_schema_migration_id_seq', 24, true);


--
-- Name: client_clientid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.client_clientid_seq', 1, false);


--
-- Name: client_vk_link_code_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.client_vk_link_code_id_seq', 1, false);


--
-- Name: client_vk_link_event_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.client_vk_link_event_id_seq', 1, false);


--
-- Name: client_vk_link_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.client_vk_link_id_seq', 1, false);


--
-- Name: consignmentnote_consignmentid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.consignmentnote_consignmentid_seq', 1, false);


--
-- Name: consproduct_consproduct_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.consproduct_consproduct_seq', 1, false);


--
-- Name: debt_payment_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.debt_payment_id_seq', 1, false);


--
-- Name: dish_category_category_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.dish_category_category_id_seq', 1, false);


--
-- Name: dish_dishid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.dish_dishid_seq', 1, false);


--
-- Name: dish_set_item_set_item_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.dish_set_item_set_item_id_seq', 1, false);


--
-- Name: dish_set_setid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.dish_set_setid_seq', 1, false);


--
-- Name: inventory_document_lines_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.inventory_document_lines_id_seq', 1, false);


--
-- Name: inventory_documents_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.inventory_documents_id_seq', 1, false);


--
-- Name: inventory_shift_report_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.inventory_shift_report_id_seq', 1, false);


--
-- Name: inventory_shift_report_line_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.inventory_shift_report_line_id_seq', 1, false);


--
-- Name: order_consumable_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.order_consumable_id_seq', 1, false);


--
-- Name: order_orderid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.order_orderid_seq', 1, false);


--
-- Name: orderdish_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.orderdish_id_seq', 1, false);


--
-- Name: person_personid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.person_personid_seq', 1, false);


--
-- Name: preparation_preparationid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.preparation_preparationid_seq', 1, false);


--
-- Name: preparation_stock_movements_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.preparation_stock_movements_id_seq', 1, false);


--
-- Name: preparationwarehouse_preparationwarehouseid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.preparationwarehouse_preparationwarehouseid_seq', 1, false);


--
-- Name: product_productid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.product_productid_seq', 1, false);


--
-- Name: productwarehouse_productwarehouseid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.productwarehouse_productwarehouseid_seq', 1, false);


--
-- Name: salary_accrual_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.salary_accrual_id_seq', 1, false);


--
-- Name: salary_payment_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.salary_payment_id_seq', 1, false);


--
-- Name: shift_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.shift_id_seq', 1, false);


--
-- Name: shift_inventory_snapshot_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.shift_inventory_snapshot_id_seq', 1, false);


--
-- Name: shiftperson_shiftpersonid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.shiftperson_shiftpersonid_seq', 1, false);


--
-- Name: stock_movements_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.stock_movements_id_seq', 1, false);


--
-- Name: supplier_price_history_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.supplier_price_history_id_seq', 1, false);


--
-- Name: supplier_supplierid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.supplier_supplierid_seq', 1, false);


--
-- Name: tax_outbox_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.tax_outbox_id_seq', 1, false);


--
-- Name: techproduct_techproductid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.techproduct_techproductid_seq', 1, false);


--
-- Name: user_account_id_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.user_account_id_seq', 1, false);


--
-- Name: warehouse_warehouseid_seq; Type: SEQUENCE SET; Schema: sales; Owner: -
--

SELECT pg_catalog.setval('sales.warehouse_warehouseid_seq', 1, false);


--
-- Name: cafehelp_schema_migration cafehelp_schema_migration_migration_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafehelp_schema_migration
    ADD CONSTRAINT cafehelp_schema_migration_migration_name_key UNIQUE (migration_name);


--
-- Name: cafehelp_schema_migration cafehelp_schema_migration_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafehelp_schema_migration
    ADD CONSTRAINT cafehelp_schema_migration_pkey PRIMARY KEY (id);


--
-- Name: client client_pk; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client
    ADD CONSTRAINT client_pk PRIMARY KEY (clientid);


--
-- Name: client_vk_link_attempt client_vk_link_attempt_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link_attempt
    ADD CONSTRAINT client_vk_link_attempt_pkey PRIMARY KEY (vk_user_id);


--
-- Name: client_vk_link_code client_vk_link_code_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link_code
    ADD CONSTRAINT client_vk_link_code_pkey PRIMARY KEY (id);


--
-- Name: client_vk_link_event client_vk_link_event_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link_event
    ADD CONSTRAINT client_vk_link_event_pkey PRIMARY KEY (id);


--
-- Name: client_vk_link client_vk_link_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link
    ADD CONSTRAINT client_vk_link_pkey PRIMARY KEY (id);


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
-- Name: consumable_rule consumable_rule_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consumable_rule
    ADD CONSTRAINT consumable_rule_pkey PRIMARY KEY (product_id);


--
-- Name: debt_payment debt_payment_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.debt_payment
    ADD CONSTRAINT debt_payment_pkey PRIMARY KEY (id);


--
-- Name: dish dish_catalog_values_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.dish
    ADD CONSTRAINT dish_catalog_values_chk CHECK (((dishname IS NOT NULL) AND (btrim((dishname)::text) <> ''::text) AND (price IS NOT NULL) AND (price > (0)::double precision) AND (price <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])) AND (weight IS NOT NULL) AND (weight > (0)::double precision) AND (weight <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])) AND (firstcost IS NOT NULL) AND (firstcost >= (0)::double precision) AND (firstcost <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])))) NOT VALID;


--
-- Name: CONSTRAINT dish_catalog_values_chk ON dish; Type: COMMENT; Schema: sales; Owner: -
--

COMMENT ON CONSTRAINT dish_catalog_values_chk ON sales.dish IS 'NOT VALID until legacy catalog rows are normalized; enforced for all new and updated rows.';


--
-- Name: dish_category dish_category_name_key; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.dish_category
    ADD CONSTRAINT dish_category_name_key UNIQUE (name);


--
-- Name: dish_category dish_category_name_nonblank_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.dish_category
    ADD CONSTRAINT dish_category_name_nonblank_chk CHECK (((name IS NOT NULL) AND (btrim((name)::text) <> ''::text))) NOT VALID;


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
-- Name: dish_set dish_set_catalog_values_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.dish_set
    ADD CONSTRAINT dish_set_catalog_values_chk CHECK (((setname IS NOT NULL) AND (btrim((setname)::text) <> ''::text) AND (price IS NOT NULL) AND (price > (0)::double precision) AND (price <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])) AND (first_cost IS NOT NULL) AND (first_cost >= (0)::double precision) AND (first_cost <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])))) NOT VALID;


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
-- Name: order_consumable order_consumable_order_product_uq; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.order_consumable
    ADD CONSTRAINT order_consumable_order_product_uq UNIQUE (order_id, product_id);


--
-- Name: order_consumable order_consumable_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.order_consumable
    ADD CONSTRAINT order_consumable_pkey PRIMARY KEY (id);


--
-- Name: order order_debt_amounts_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales."order"
    ADD CONSTRAINT order_debt_amounts_chk CHECK ((((duty = false) AND (COALESCE(debt_remaining_amount, (0)::numeric) = (0)::numeric)) OR ((duty = true) AND (clientid IS NOT NULL) AND (debt_payment_date IS NOT NULL) AND (debt_original_amount > (0)::numeric) AND (debt_remaining_amount > (0)::numeric) AND (debt_remaining_amount <= debt_original_amount)))) NOT VALID;


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
-- Name: preparation preparation_catalog_values_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.preparation
    ADD CONSTRAINT preparation_catalog_values_chk CHECK (((preparationname IS NOT NULL) AND (btrim((preparationname)::text) <> ''::text) AND (output_weight IS NOT NULL) AND (output_weight > (0)::double precision) AND (output_weight <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])))) NOT VALID;


--
-- Name: preparation preparation_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparation
    ADD CONSTRAINT preparation_pkey PRIMARY KEY (preparationid);


--
-- Name: preparation_stock_movements preparation_stock_movements_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparation_stock_movements
    ADD CONSTRAINT preparation_stock_movements_pkey PRIMARY KEY (id);


--
-- Name: preparationwarehouse preparationwarehouse_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparationwarehouse
    ADD CONSTRAINT preparationwarehouse_pkey PRIMARY KEY (preparationwarehouseid);


--
-- Name: product product_catalog_values_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.product
    ADD CONSTRAINT product_catalog_values_chk CHECK (((productname IS NOT NULL) AND (btrim((productname)::text) <> ''::text) AND (productprice IS NOT NULL) AND (productprice >= (0)::numeric) AND (productprice <> ALL (ARRAY['NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric])) AND (waste IS NOT NULL) AND ((waste >= (0)::double precision) AND (waste <= (100)::double precision)) AND (waste <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])) AND ((unit)::text = ANY ((ARRAY['g'::character varying, 'kg'::character varying, 'ml'::character varying, 'l'::character varying, 'pcs'::character varying])::text[])) AND ((base_unit)::text = ANY ((ARRAY['g'::character varying, 'ml'::character varying, 'pcs'::character varying])::text[])) AND ((((unit)::text = ANY ((ARRAY['g'::character varying, 'kg'::character varying])::text[])) AND ((base_unit)::text = 'g'::text)) OR (((unit)::text = ANY ((ARRAY['ml'::character varying, 'l'::character varying])::text[])) AND ((base_unit)::text = 'ml'::text)) OR (((unit)::text = 'pcs'::text) AND ((base_unit)::text = 'pcs'::text))) AND (unit_factor IS NOT NULL) AND (unit_factor > (0)::numeric) AND (unit_factor <> ALL (ARRAY['NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric])))) NOT VALID;


--
-- Name: CONSTRAINT product_catalog_values_chk ON product; Type: COMMENT; Schema: sales; Owner: -
--

COMMENT ON CONSTRAINT product_catalog_values_chk ON sales.product IS 'NOT VALID until legacy catalog rows are normalized; enforced for all new and updated rows.';


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
-- Name: salary_accrual salary_accrual_person_shift_uq; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_accrual
    ADD CONSTRAINT salary_accrual_person_shift_uq UNIQUE (person_id, shift_id);


--
-- Name: salary_accrual salary_accrual_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_accrual
    ADD CONSTRAINT salary_accrual_pkey PRIMARY KEY (id);


--
-- Name: salary_payment salary_payment_idempotency_uq; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_payment
    ADD CONSTRAINT salary_payment_idempotency_uq UNIQUE (idempotency_key);


--
-- Name: salary_payment salary_payment_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_payment
    ADD CONSTRAINT salary_payment_pkey PRIMARY KEY (id);


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
-- Name: supplier supplier_name_nonblank_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.supplier
    ADD CONSTRAINT supplier_name_nonblank_chk CHECK (((suppliername IS NOT NULL) AND (btrim((suppliername)::text) <> ''::text))) NOT VALID;


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
-- Name: tax_outbox tax_outbox_pkey; Type: CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.tax_outbox
    ADD CONSTRAINT tax_outbox_pkey PRIMARY KEY (id);


--
-- Name: techproduct techproduct_catalog_values_chk; Type: CHECK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE sales.techproduct
    ADD CONSTRAINT techproduct_catalog_values_chk CHECK (((weight IS NOT NULL) AND (weight > (0)::double precision) AND (weight <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])) AND (waste IS NOT NULL) AND ((waste >= (0)::double precision) AND (waste <= (100)::double precision)) AND (waste <> ALL (ARRAY['NaN'::double precision, 'Infinity'::double precision, '-Infinity'::double precision])))) NOT VALID;


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
-- Name: client_normalized_number_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX client_normalized_number_uq_idx ON sales.client USING btree (normalized_number) WHERE (normalized_number IS NOT NULL);


--
-- Name: client_vk_link_client_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX client_vk_link_client_uq_idx ON sales.client_vk_link USING btree (client_id);


--
-- Name: client_vk_link_code_client_active_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX client_vk_link_code_client_active_idx ON sales.client_vk_link_code USING btree (client_id, expires_at) WHERE (used_at IS NULL);


--
-- Name: client_vk_link_code_expires_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX client_vk_link_code_expires_idx ON sales.client_vk_link_code USING btree (expires_at);


--
-- Name: client_vk_link_code_fingerprint_active_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX client_vk_link_code_fingerprint_active_uq_idx ON sales.client_vk_link_code USING btree (code_fingerprint) WHERE ((used_at IS NULL) AND (code_fingerprint IS NOT NULL));


--
-- Name: client_vk_link_event_user_created_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX client_vk_link_event_user_created_idx ON sales.client_vk_link_event USING btree (vk_user_id, created_at DESC);


--
-- Name: client_vk_link_vk_user_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX client_vk_link_vk_user_uq_idx ON sales.client_vk_link USING btree (vk_user_id);


--
-- Name: consumable_rule_active_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX consumable_rule_active_idx ON sales.consumable_rule USING btree (active, basis);


--
-- Name: debt_payment_idempotency_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX debt_payment_idempotency_uq_idx ON sales.debt_payment USING btree (idempotency_key);


--
-- Name: debt_payment_order_created_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX debt_payment_order_created_idx ON sales.debt_payment USING btree (order_id, created_at DESC);


--
-- Name: dish_category_id_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX dish_category_id_idx ON sales.dish USING btree (category_id);


--
-- Name: dish_category_name_normalized_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX dish_category_name_normalized_uq_idx ON sales.dish_category USING btree (lower(btrim((name)::text)));


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
-- Name: ix_preparation_stock_movements_balance_date; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX ix_preparation_stock_movements_balance_date ON sales.preparation_stock_movements USING btree (warehouse_id, preparation_id, movement_date, id);


--
-- Name: ix_preparation_stock_movements_source; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX ix_preparation_stock_movements_source ON sales.preparation_stock_movements USING btree (source_type, source_id);


--
-- Name: ix_product_normalized_name; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX ix_product_normalized_name ON sales.product USING btree (lower(btrim((productname)::text)));


--
-- Name: ix_stock_movements_source; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX ix_stock_movements_source ON sales.stock_movements USING btree (source_type, source_id);


--
-- Name: ix_stock_movements_warehouse_product_date; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX ix_stock_movements_warehouse_product_date ON sales.stock_movements USING btree (warehouse_id, product_id, movement_date, id);


--
-- Name: ix_supplier_price_history_offer_date; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX ix_supplier_price_history_offer_date ON sales.supplier_price_history USING btree (supplier_id, product_id, valid_from DESC, id DESC);


--
-- Name: order_active_client_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX order_active_client_idx ON sales."order" USING btree (clientid, orderid) WHERE (cancelled_at IS NULL);


--
-- Name: order_active_date_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX order_active_date_idx ON sales."order" USING btree ("Date", orderid) WHERE (cancelled_at IS NULL);


--
-- Name: order_active_shift_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX order_active_shift_idx ON sales."order" USING btree (shiftid, orderid) WHERE (cancelled_at IS NULL);


--
-- Name: order_active_status_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX order_active_status_idx ON sales."order" USING btree (status, orderid) WHERE (cancelled_at IS NULL);


--
-- Name: order_consumable_order_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX order_consumable_order_idx ON sales.order_consumable USING btree (order_id);


--
-- Name: orderdish_order_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX orderdish_order_idx ON sales.orderdish USING btree (orderid);


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
-- Name: salary_accrual_person_worked_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX salary_accrual_person_worked_idx ON sales.salary_accrual USING btree (person_id, worked_on DESC, id DESC);


--
-- Name: salary_payment_person_created_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX salary_payment_person_created_idx ON sales.salary_payment USING btree (person_id, created_at DESC, id DESC);


--
-- Name: salary_payment_single_reversal_uq; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX salary_payment_single_reversal_uq ON sales.salary_payment USING btree (related_payment_id) WHERE ((entry_type)::text = 'REVERSAL'::text);


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
-- Name: shiftperson_shift_person_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX shiftperson_shift_person_idx ON sales.shiftperson USING btree (shiftid, personid);


--
-- Name: supplier_name_normalized_uq_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX supplier_name_normalized_uq_idx ON sales.supplier USING btree (lower(btrim((suppliername)::text)));


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
-- Name: tax_outbox_processing_lock_idx; Type: INDEX; Schema: sales; Owner: -
--

CREATE INDEX tax_outbox_processing_lock_idx ON sales.tax_outbox USING btree (locked_at) WHERE ((status)::text = 'processing'::text);


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
-- Name: ux_preparationwarehouse_warehouse_preparation; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX ux_preparationwarehouse_warehouse_preparation ON sales.preparationwarehouse USING btree (warehouseid, preparationid);


--
-- Name: ux_productwarehouse_warehouse_product; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX ux_productwarehouse_warehouse_product ON sales.productwarehouse USING btree (warehouseid, productid) WHERE ((warehouseid IS NOT NULL) AND (productid IS NOT NULL));


--
-- Name: ux_supplier_price_history_active_offer; Type: INDEX; Schema: sales; Owner: -
--

CREATE UNIQUE INDEX ux_supplier_price_history_active_offer ON sales.supplier_price_history USING btree (supplier_id, product_id) WHERE (valid_to IS NULL);


--
-- Name: product trg_product_prevent_duplicate_name; Type: TRIGGER; Schema: sales; Owner: -
--

CREATE TRIGGER trg_product_prevent_duplicate_name BEFORE INSERT OR UPDATE OF productname ON sales.product FOR EACH ROW EXECUTE FUNCTION sales.prevent_duplicate_product_name();


--
-- Name: client_vk_link client_vk_link_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link
    ADD CONSTRAINT client_vk_link_client_fk FOREIGN KEY (client_id) REFERENCES sales.client(clientid) ON DELETE CASCADE;


--
-- Name: client_vk_link_code client_vk_link_code_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link_code
    ADD CONSTRAINT client_vk_link_code_client_fk FOREIGN KEY (client_id) REFERENCES sales.client(clientid) ON DELETE CASCADE;


--
-- Name: client_vk_link_event client_vk_link_event_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.client_vk_link_event
    ADD CONSTRAINT client_vk_link_event_client_fk FOREIGN KEY (client_id) REFERENCES sales.client(clientid) ON DELETE SET NULL;


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
-- Name: consumable_rule consumable_rule_dish_category_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consumable_rule
    ADD CONSTRAINT consumable_rule_dish_category_id_fkey FOREIGN KEY (dish_category_id) REFERENCES sales.dish_category(category_id) ON DELETE SET NULL;


--
-- Name: consumable_rule consumable_rule_product_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.consumable_rule
    ADD CONSTRAINT consumable_rule_product_id_fkey FOREIGN KEY (product_id) REFERENCES sales.product(productid) ON DELETE CASCADE;


--
-- Name: debt_payment debt_payment_client_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.debt_payment
    ADD CONSTRAINT debt_payment_client_fk FOREIGN KEY (client_id) REFERENCES sales.client(clientid);


--
-- Name: debt_payment debt_payment_order_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.debt_payment
    ADD CONSTRAINT debt_payment_order_fk FOREIGN KEY (order_id) REFERENCES sales."order"(orderid);


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
-- Name: order_consumable order_consumable_order_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.order_consumable
    ADD CONSTRAINT order_consumable_order_id_fkey FOREIGN KEY (order_id) REFERENCES sales."order"(orderid) ON DELETE CASCADE;


--
-- Name: order_consumable order_consumable_product_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.order_consumable
    ADD CONSTRAINT order_consumable_product_id_fkey FOREIGN KEY (product_id) REFERENCES sales.product(productid);


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
-- Name: preparation_stock_movements preparation_stock_movements_preparation_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparation_stock_movements
    ADD CONSTRAINT preparation_stock_movements_preparation_id_fkey FOREIGN KEY (preparation_id) REFERENCES sales.preparation(preparationid);


--
-- Name: preparation_stock_movements preparation_stock_movements_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.preparation_stock_movements
    ADD CONSTRAINT preparation_stock_movements_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES sales.warehouse(warehouseid);


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
-- Name: salary_accrual salary_accrual_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_accrual
    ADD CONSTRAINT salary_accrual_person_fk FOREIGN KEY (person_id) REFERENCES sales.person(personid);


--
-- Name: salary_accrual salary_accrual_shift_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_accrual
    ADD CONSTRAINT salary_accrual_shift_fk FOREIGN KEY (shift_id) REFERENCES sales.shift(id);


--
-- Name: salary_payment salary_payment_author_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_payment
    ADD CONSTRAINT salary_payment_author_fk FOREIGN KEY (author_account_id) REFERENCES sales.user_account(id);


--
-- Name: salary_payment salary_payment_person_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_payment
    ADD CONSTRAINT salary_payment_person_fk FOREIGN KEY (person_id) REFERENCES sales.person(personid);


--
-- Name: salary_payment salary_payment_related_fk; Type: FK CONSTRAINT; Schema: sales; Owner: -
--

ALTER TABLE ONLY sales.salary_payment
    ADD CONSTRAINT salary_payment_related_fk FOREIGN KEY (related_payment_id) REFERENCES sales.salary_payment(id);


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

\unrestrict S75tquxHc6PDGBjJENHnZetE2BeosPgdIk4kjEJ44zcnCor3fGiTElih0Mg2ZVo

