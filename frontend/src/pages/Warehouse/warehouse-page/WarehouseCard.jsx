import { useState } from "react";
import { formatMoney, formatQuantity, getUnitLabel } from "./warehouseUtils";
import { unitLabel } from "../../../utils/units";
import styles from "../WarehousePage.module.css";

const STOCK_MODES = [
    ["in", "Приход"],
    ["out", "Списание"],
    ["revalue", "Переоценка"]
];

export default function WarehouseCard({
    warehouse,
    products,
    showZeroStock,
    busyKey,
    openPanel,
    catalogProducts,
    suppliers,
    catalogSearch,
    catalogForm,
    newProductForm,
    stockInputs,
    onEdit,
    onDelete,
    onSetMain,
    onOpenPanel,
    onClosePanel,
    onCatalogSearch,
    onCatalogForm,
    onNewProductForm,
    onCatalogSubmit,
    onNewProductSubmit,
    onStockInput,
    onAdjustStock,
    onRevalueStock
}) {
    const [activeRow, setActiveRow] = useState(null);
    const [rowMode, setRowMode] = useState("in");
    const visibleProducts = products.filter((product) => showZeroStock || Number(product.quantityBase) > 0);
    const totalValue = products.reduce(
        (sum, product) => sum + Number(product.inventoryValue ?? 0),
        0
    );
    const selectedCatalog = catalogProducts.find(
        (product) => String(product.productId) === String(catalogForm.productId)
    );

    return (
        <article className={`${styles.warehouseCard} ${warehouse.isMain ? styles.mainWarehouse : ""}`}>
            <header className={styles.warehouseHeader}>
                <div className={styles.warehouseIdentity}>
                    <span className={styles.warehouseIndex}>Склад {String(warehouse.warehouseId).padStart(2, "0")}</span>
                    <h3>{warehouse.warehouseName}</h3>
                    <div className={styles.warehouseMeta}>
                        {warehouse.isMain ? <strong>Главный</strong> : <span>Резервный</span>}
                        <span>{products.length} позиций</span>
                        <span>{formatMoney(totalValue)}</span>
                    </div>
                </div>
                <div className={styles.cardActions}>
                    {!warehouse.isMain ? (
                        <button type="button" onClick={onSetMain} disabled={busyKey === `warehouse-${warehouse.warehouseId}`}>
                            Сделать главным
                        </button>
                    ) : null}
                    <button type="button" onClick={onEdit}>Переименовать</button>
                    <button className={styles.dangerTextButton} type="button" onClick={onDelete}>Удалить</button>
                </div>
            </header>

            <div className={styles.stockToolbar}>
                <div>
                    <strong>Остаток</strong>
                    <span>Средняя цена рассчитана по фактическому остатку</span>
                </div>
                <div>
                    <button type="button" onClick={() => onOpenPanel("catalog")}>+ Приход из каталога</button>
                    <button type="button" onClick={() => onOpenPanel("new")}>+ Новый продукт</button>
                </div>
            </div>

            {openPanel === "catalog" ? (
                <div className={styles.stockPanel}>
                    <div className={styles.panelHeading}>
                        <div><strong>Приход из каталога</strong><span>Выберите существующую позицию и закупочную цену.</span></div>
                        <button type="button" onClick={onClosePanel}>Закрыть</button>
                    </div>
                    <label className={styles.field}>
                        <span>Поиск</span>
                        <input
                            name="catalogSearch"
                            autoComplete="off"
                            placeholder="Название, поставщик или ID…"
                            value={catalogSearch}
                            onChange={(event) => onCatalogSearch(event.target.value)}
                        />
                    </label>
                    <div className={styles.catalogList}>
                        {catalogProducts.slice(0, 16).map((product) => (
                            <button
                                type="button"
                                key={product.productId}
                                className={String(product.productId) === String(catalogForm.productId) ? styles.catalogItemActive : ""}
                                onClick={() => onCatalogForm({
                                    ...catalogForm,
                                    productId: String(product.productId),
                                    unitPrice: String(product.productPrice ?? "")
                                })}
                            >
                                <span>{product.productName}</span>
                                <small>#{product.productId} · {getUnitLabel(product)}</small>
                            </button>
                        ))}
                    </div>
                    <div className={styles.panelFields}>
                        <label className={styles.field}>
                            <span>Количество {selectedCatalog ? `· ${getUnitLabel(selectedCatalog)}` : ""}</span>
                            <input name="receiptQuantity" inputMode="decimal" autoComplete="off" placeholder="0,000…" value={catalogForm.quantity}
                                onChange={(event) => onCatalogForm({ ...catalogForm, quantity: event.target.value })} />
                        </label>
                        <label className={styles.field}>
                            <span>Цена прихода</span>
                            <input name="receiptPrice" inputMode="decimal" autoComplete="off" placeholder="0,00 ₽…" value={catalogForm.unitPrice}
                                onChange={(event) => onCatalogForm({ ...catalogForm, unitPrice: event.target.value })} />
                        </label>
                        <button className={styles.primaryButton} type="button" onClick={onCatalogSubmit}
                            disabled={busyKey === `catalog-${warehouse.warehouseId}`}>Провести приход</button>
                    </div>
                </div>
            ) : null}

            {openPanel === "new" ? (
                <div className={styles.stockPanel}>
                    <div className={styles.panelHeading}>
                        <div><strong>Новый продукт</strong><span>Создание карточки сразу с первым приходом.</span></div>
                        <button type="button" onClick={onClosePanel}>Закрыть</button>
                    </div>
                    <div className={styles.newProductGrid}>
                        <label className={styles.field}><span>Название</span><input name="newProductName" autoComplete="off" placeholder="Например, Молоко 3,2%…"
                            value={newProductForm.productName} onChange={(event) => onNewProductForm({ ...newProductForm, productName: event.target.value })} /></label>
                        <label className={styles.field}><span>Поставщик</span><select name="newProductSupplier" value={newProductForm.supplierId}
                            onChange={(event) => onNewProductForm({ ...newProductForm, supplierId: event.target.value })}>
                            <option value="">Выберите</option>
                            {suppliers.map((supplier) => <option key={supplier.supplierId ?? supplier.id} value={supplier.supplierId ?? supplier.id}>{supplier.supplierName ?? supplier.name}</option>)}
                        </select></label>
                        <label className={styles.field}><span>Цена</span><input name="newProductPrice" inputMode="decimal" autoComplete="off" placeholder="0,00 ₽…"
                            value={newProductForm.productPrice} onChange={(event) => onNewProductForm({ ...newProductForm, productPrice: event.target.value })} /></label>
                        <label className={styles.field}><span>Отход, %</span><input name="newProductWaste" inputMode="decimal" autoComplete="off" placeholder="Например, 5…"
                            value={newProductForm.waste} onChange={(event) => onNewProductForm({ ...newProductForm, waste: event.target.value })} /></label>
                        <label className={styles.field}><span>Количество</span><input name="newProductQuantity" inputMode="decimal" autoComplete="off" placeholder="0,000…"
                            value={newProductForm.quantity} onChange={(event) => onNewProductForm({ ...newProductForm, quantity: event.target.value })} /></label>
                        <button className={styles.primaryButton} type="button" onClick={onNewProductSubmit}
                            disabled={busyKey === `new-${warehouse.warehouseId}`}>Создать и принять</button>
                    </div>
                </div>
            ) : null}

            {visibleProducts.length ? (
                <div className={styles.stockList}>
                    {visibleProducts.map((product) => {
                        const key = `${warehouse.warehouseId}-${product.productId}`;
                        const values = stockInputs[key] ?? {};
                        const unit = unitLabel(product.unit ?? product.baseUnit);
                        const isActive = activeRow === key;
                        return (
                            <div className={styles.stockRowGroup} key={product.productId}>
                            <div className={styles.stockRow}>
                                <div className={styles.productIdentity}>
                                    <strong>{product.productName}</strong>
                                    <span>#{product.productId} · {product.supplierName}</span>
                                </div>
                                <div className={styles.balanceCell}>
                                    <span>В наличии</span>
                                    <strong className={Number(product.quantityBase) <= 0 ? styles.zeroBalance : ""}>
                                        {formatQuantity(product.quantityDisplay)} {unit}
                                    </strong>
                                </div>
                                <div className={styles.priceCell}>
                                    <span>Средняя цена за {unit}</span>
                                    <strong>{formatMoney(product.averagePrice)}</strong>
                                    <small>
                                        {product.latestPrice == null
                                            ? "приходов не было"
                                            : `последний приход: ${formatMoney(product.latestPrice)}`}
                                    </small>
                                </div>
                                <div className={styles.rowActionCell}>
                                    <button
                                        type="button"
                                        aria-expanded={isActive}
                                        onClick={() => {
                                            setActiveRow(isActive ? null : key);
                                            setRowMode("in");
                                        }}
                                    >
                                        {isActive ? "Скрыть" : "Изменить остаток"}
                                    </button>
                                </div>
                            </div>
                            {isActive && (
                                <div className={styles.rowPanel}>
                                    <div className={styles.modeSwitch} role="radiogroup" aria-label="Действие с остатком">
                                        {STOCK_MODES.map(([mode, label]) => (
                                            <button
                                                key={mode}
                                                type="button"
                                                role="radio"
                                                aria-checked={rowMode === mode}
                                                className={rowMode === mode ? styles.modeActive : ""}
                                                onClick={() => setRowMode(mode)}
                                            >
                                                {label}
                                            </button>
                                        ))}
                                    </div>
                                    {rowMode !== "revalue" && (
                                        <label className={styles.field}>
                                            <span>Количество, {unit}</span>
                                            <input name={`quantity-${key}`} inputMode="decimal" autoComplete="off" placeholder="0"
                                                value={values.quantity ?? ""} onChange={(event) => onStockInput(key, { quantity: event.target.value })} />
                                        </label>
                                    )}
                                    {rowMode !== "out" && (
                                        <label className={styles.field}>
                                            <span>{rowMode === "in" ? `Цена за ${unit}, ₽` : `Новая средняя цена за ${unit}, ₽`}</span>
                                            <input name={`price-${key}`} inputMode="decimal" autoComplete="off"
                                                placeholder={rowMode === "in" ? `по умолчанию ${formatMoney(product.averagePrice)}` : "0"}
                                                value={values.unitPrice ?? ""} onChange={(event) => onStockInput(key, { unitPrice: event.target.value })} />
                                        </label>
                                    )}
                                    <p className={styles.rowPanelHint}>
                                        {rowMode === "in" && "Добавит товар на склад и пересчитает среднюю цену."}
                                        {rowMode === "out" && "Уберёт товар со склада по текущей средней цене (порча, недостача)."}
                                        {rowMode === "revalue" && "Исправит стоимость текущего остатка без изменения количества. Попросим указать причину."}
                                    </p>
                                    <div className={styles.rowPanelActions}>
                                        <button
                                            type="button"
                                            className={rowMode === "out" ? styles.writeoffButton : styles.primaryButton}
                                            disabled={busyKey === `stock-${key}`}
                                            onClick={() => (rowMode === "revalue"
                                                ? onRevalueStock(product)
                                                : onAdjustStock(product, rowMode))}
                                        >
                                            {rowMode === "in" ? "Провести приход" : rowMode === "out" ? "Списать" : "Переоценить"}
                                        </button>
                                        <button type="button" onClick={() => setActiveRow(null)}>Отмена</button>
                                    </div>
                                </div>
                            )}
                            </div>
                        );
                    })}
                </div>
            ) : <div className={styles.cardEmpty}>Нет позиций с положительным остатком.</div>}
        </article>
    );
}
