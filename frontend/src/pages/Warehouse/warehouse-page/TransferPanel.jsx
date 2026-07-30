import { getUnitLabel } from "./warehouseUtils";
import styles from "../WarehousePage.module.css";

export default function TransferPanel({ warehouses, products, form, busy, onChange, onSubmit }) {
    const selectedProduct = products.find((product) => String(product.productId) === String(form.productId));
    return (
        <form className={`${styles.operationCard} ${styles.transferCard}`} onSubmit={onSubmit}>
            <div className={styles.operationHeading}>
                <span>02 · Внутренний маршрут</span>
                <h2>Передать между складами</h2>
                <p>Остаток спишется у отправителя и появится у получателя одной операцией.</p>
            </div>
            <div className={styles.transferRoute}>
                <label className={styles.field}>
                    <span>Откуда</span>
                    <select
                        name="fromWarehouseId"
                        value={form.fromWarehouseId}
                        onChange={(event) => onChange("fromWarehouseId", event.target.value)}
                    >
                        <option value="">Выберите склад</option>
                        {warehouses.map((warehouse) => (
                            <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.warehouseName}</option>
                        ))}
                    </select>
                </label>
                <span className={styles.routeArrow} aria-hidden="true">→</span>
                <label className={styles.field}>
                    <span>Куда</span>
                    <select
                        name="toWarehouseId"
                        value={form.toWarehouseId}
                        onChange={(event) => onChange("toWarehouseId", event.target.value)}
                    >
                        <option value="">Выберите склад</option>
                        {warehouses.filter((warehouse) => String(warehouse.warehouseId) !== String(form.fromWarehouseId))
                            .map((warehouse) => (
                                <option key={warehouse.warehouseId} value={warehouse.warehouseId}>{warehouse.warehouseName}</option>
                            ))}
                    </select>
                </label>
            </div>
            <div className={styles.transferDetails}>
                <label className={styles.field}>
                    <span>Товар</span>
                    <select
                        name="productId"
                        value={form.productId}
                        disabled={!form.fromWarehouseId}
                        onChange={(event) => onChange("productId", event.target.value)}
                    >
                        <option value="">{form.fromWarehouseId ? "Выберите позицию" : "Сначала выберите отправителя"}</option>
                        {products.map((product) => (
                            <option key={product.productId} value={product.productId}>{product.productName}</option>
                        ))}
                    </select>
                </label>
                <label className={styles.field}>
                    <span>Количество {selectedProduct ? `· ${getUnitLabel(selectedProduct)}` : ""}</span>
                    <input
                        name="quantity"
                        type="text"
                        inputMode="decimal"
                        autoComplete="off"
                        placeholder="Например, 2,5…"
                        value={form.quantity}
                        onChange={(event) => onChange("quantity", event.target.value)}
                    />
                </label>
                <button className={styles.primaryButton} type="submit" disabled={busy || warehouses.length < 2}>
                    {busy ? "Проводим…" : "Провести передачу"}
                </button>
            </div>
        </form>
    );
}
