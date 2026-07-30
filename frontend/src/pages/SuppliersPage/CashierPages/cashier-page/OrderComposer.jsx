import styles from "../CashierPage.module.css";
import { formatMoney, getInitials } from "./cashierUtils";

export default function OrderComposer({
    selectedClient,
    clientSearch,
    filteredClients,
    items,
    orderType,
    deliveryCost,
    deliveryPhone,
    deliveryAddress,
    paymentType,
    isDebt,
    showDatePicker,
    debtPaymentDate,
    preparationTime,
    itemsTotal,
    total,
    isLoading,
    requiresContactDetails,
    effectivePhone,
    effectiveAddress,
    onClientSearch,
    onSelectClient,
    onClearClient,
    onCreateClient,
    onOpenClientPicker,
    onQuantityChange,
    onRemoveItem,
    onOpenDishPicker,
    onOrderTypeChange,
    onDeliveryCostChange,
    onDeliveryPhoneChange,
    onDeliveryAddressChange,
    onPaymentTypeChange,
    onDebtChange,
    onDebtDateChange,
    onPreparationTimeChange,
    onCreateOrder,
    onCloseShift
}) {
    return (
        <section className={styles.orderComposer}>
            <div className={styles.sectionHeading}>
                <div>
                    <p className={styles.kicker}>Новый чек</p>
                    <h2>Собрать заказ</h2>
                </div>
                <span>{items.reduce((sum, item) => sum + Number(item.qty || 1), 0)}</span>
            </div>

            <div className={styles.composerBlock}>
                <div className={styles.blockHeading}>
                    <h3>Гость</h3>
                    {!selectedClient && (
                        <button type="button" onClick={onOpenClientPicker}>Из списка</button>
                    )}
                </div>

                {selectedClient ? (
                    <div className={styles.selectedClient}>
                        <span className={styles.clientInitials} aria-hidden="true">
                            {getInitials(selectedClient.fullName)}
                        </span>
                        <div>
                            <strong>{selectedClient.fullName}</strong>
                            <span>{selectedClient.number || `Гость #${selectedClient.clientId}`}</span>
                        </div>
                        <button type="button" onClick={onClearClient} aria-label="Убрать гостя из заказа">×</button>
                    </div>
                ) : (
                    <div className={styles.clientSearch}>
                        <label htmlFor="cashier-client-search">Поиск по имени или телефону</label>
                        <input
                            id="cashier-client-search"
                            name="clientSearch"
                            type="search"
                            autoComplete="off"
                            value={clientSearch}
                            onChange={(event) => onClientSearch(event.target.value)}
                            placeholder="Например, Анна или +7 900…"
                            disabled={isLoading}
                        />
                        {clientSearch.trim() && (
                            <div className={styles.clientResults}>
                                {filteredClients.length === 0 ? (
                                    <div className={styles.noResults}>
                                        <span>Гость не найден.</span>
                                        <button type="button" onClick={onCreateClient}>Создать карточку</button>
                                    </div>
                                ) : (
                                    filteredClients.slice(0, 8).map((client) => (
                                        <button
                                            key={client.clientId}
                                            type="button"
                                            className={styles.clientOption}
                                            onClick={() => onSelectClient(client)}
                                        >
                                            <span>
                                                <strong>{client.fullName}</strong>
                                                <small>{client.number || `Гость #${client.clientId}`}</small>
                                            </span>
                                            <span aria-hidden="true">→</span>
                                        </button>
                                    ))
                                )}
                            </div>
                        )}
                        <button className={styles.textButton} type="button" onClick={onCreateClient}>
                            + Новый гость
                        </button>
                    </div>
                )}
            </div>

            <div className={styles.composerBlock}>
                <div className={styles.blockHeading}>
                    <h3>Позиции</h3>
                    <button type="button" onClick={onOpenDishPicker}>Добавить</button>
                </div>
                {items.length === 0 ? (
                    <div className={styles.compactEmpty}>Добавьте блюда или наборы из меню.</div>
                ) : (
                    <div className={styles.orderLines}>
                        {items.map((item, index) => (
                            <div key={`${item.itemType || "dish"}-${item.dishId || item.setId || index}-${index}`} className={styles.orderLine}>
                                <div className={styles.orderLineCopy}>
                                    <span>{item.itemType === "set" ? "Набор" : "Блюдо"}</span>
                                    <strong>{item.dishName || item.name || item.setName || "Позиция"}</strong>
                                </div>
                                <div className={styles.qtyControls} aria-label={`Количество: ${item.dishName || "позиция"}`}>
                                    <button type="button" onClick={() => onQuantityChange(index, -1)} aria-label="Уменьшить количество">−</button>
                                    <span>{item.qty || 1}</span>
                                    <button type="button" onClick={() => onQuantityChange(index, 1)} aria-label="Увеличить количество">+</button>
                                </div>
                                <strong className={styles.lineAmount}>
                                    {formatMoney(Number(item.qty || 1) * Number(item.price || 0))}
                                </strong>
                                <button
                                    className={styles.removeLine}
                                    type="button"
                                    onClick={() => onRemoveItem(index)}
                                    aria-label={`Удалить ${item.dishName || "позицию"}`}
                                >
                                    ×
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <fieldset className={styles.fulfilmentFieldset}>
                <legend>Получение заказа</legend>
                <label className={orderType ? "" : styles.optionActive}>
                    <input
                        type="radio"
                        name="orderType"
                        checked={!orderType}
                        onChange={() => onOrderTypeChange(false)}
                    />
                    В заведении
                </label>
                <label className={orderType ? styles.optionActive : ""}>
                    <input
                        type="radio"
                        name="orderType"
                        checked={orderType}
                        onChange={() => onOrderTypeChange(true)}
                    />
                    Доставка
                </label>
            </fieldset>

            {orderType && (
                <div className={styles.deliveryFields}>
                    <label htmlFor="delivery-cost">
                        Стоимость доставки
                        <span className={styles.inputWithUnit}>
                            <input
                                id="delivery-cost"
                                name="deliveryCost"
                                type="number"
                                min="0"
                                step="10"
                                inputMode="decimal"
                                value={deliveryCost}
                                onChange={(event) => onDeliveryCostChange(event.target.value)}
                            />
                            <span>₽</span>
                        </span>
                    </label>
                    <label htmlFor="delivery-phone">
                        Телефон
                        <input
                            id="delivery-phone"
                            name="deliveryPhone"
                            type="tel"
                            inputMode="tel"
                            autoComplete="tel"
                            value={deliveryPhone}
                            onChange={(event) => onDeliveryPhoneChange(event.target.value)}
                            placeholder="+7 900 000-00-00"
                        />
                    </label>
                    <label className={styles.wideField} htmlFor="delivery-address">
                        Адрес
                        <input
                            id="delivery-address"
                            name="deliveryAddress"
                            type="text"
                            autoComplete="street-address"
                            value={deliveryAddress}
                            onChange={(event) => onDeliveryAddressChange(event.target.value)}
                            placeholder="Улица, дом, квартира…"
                        />
                    </label>
                </div>
            )}

            <fieldset className={styles.paymentFieldset}>
                <legend>Оплата</legend>
                {[
                    ["cash", "Наличные"],
                    ["transfer", "Перевод"],
                    ["unpaid", "Не оплачено"]
                ].map(([value, label]) => (
                    <label key={value} className={paymentType === value ? styles.optionActive : ""}>
                        <input
                            type="radio"
                            name="paymentType"
                            value={value}
                            checked={paymentType === value}
                            onChange={() => onPaymentTypeChange(value)}
                        />
                        {label}
                    </label>
                ))}
            </fieldset>

            <div className={styles.orderSettings}>
                <label className={styles.debtToggle}>
                    <input
                        name="isDebt"
                        type="checkbox"
                        checked={isDebt}
                        onChange={onDebtChange}
                        disabled={!selectedClient || isLoading}
                    />
                    <span>
                        <strong>Записать в долг</strong>
                        <small>{selectedClient ? "Сохранится в профиле гостя." : "Сначала выберите гостя."}</small>
                    </span>
                </label>
                <label htmlFor="preparation-time">
                    Готовить, минут
                    <input
                        id="preparation-time"
                        name="preparationTime"
                        type="number"
                        min="1"
                        max="600"
                        inputMode="numeric"
                        value={preparationTime}
                        onChange={(event) => onPreparationTimeChange(event.target.value)}
                    />
                </label>
                {showDatePicker && isDebt && selectedClient && (
                    <label className={styles.wideField} htmlFor="debt-payment-date">
                        Обещанная дата оплаты
                        <input
                            id="debt-payment-date"
                            name="debtPaymentDate"
                            type="date"
                            value={debtPaymentDate}
                            min={new Date().toISOString().split("T")[0]}
                            onChange={(event) => onDebtDateChange(event.target.value)}
                        />
                    </label>
                )}
            </div>

            <div className={styles.receiptTotal}>
                <div>
                    <span>Позиции</span>
                    <strong>{formatMoney(itemsTotal)}</strong>
                </div>
                {orderType && (
                    <div>
                        <span>Доставка</span>
                        <strong>{formatMoney(deliveryCost)}</strong>
                    </div>
                )}
                <div className={styles.grandTotal}>
                    <span>Итого</span>
                    <strong>{formatMoney(total)}</strong>
                </div>
            </div>

            <button
                className={styles.createOrderButton}
                type="button"
                onClick={onCreateOrder}
                disabled={
                    items.length === 0 ||
                    isLoading ||
                    (requiresContactDetails && (!effectivePhone || !effectiveAddress))
                }
            >
                Отправить заказ на кухню
            </button>
            <button className={styles.closeShiftButton} type="button" onClick={onCloseShift} disabled={isLoading}>
                Закрыть смену
            </button>
        </section>
    );
}
