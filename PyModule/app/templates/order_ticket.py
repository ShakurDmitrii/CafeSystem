from app.schemas.order_print import OrderPrintRequest


def _fmt_money(value: float) -> str:
    # Stable print format for money: round to 2 decimals and trim trailing zeros.
    rounded = round(float(value), 2)
    if abs(rounded) < 0.005:
        return "0"
    text = f"{rounded:.2f}".rstrip("0").rstrip(".")
    return text


def build_order_number_ticket(data: OrderPrintRequest) -> list[str]:
    return [
        f"ЗАКАЗ №{data.orderId}",
        "",
        "",
        "",
        "",
        "",
    ]


def build_kitchen_ticket(data: OrderPrintRequest) -> list[str]:
    lines: list[str] = []
    lines.append(f"ЗАКАЗ НА КУХНЮ №{data.orderId}")
    if data.createdAt:
        lines.append(f"Время: {data.createdAt}")
    lines.append("-" * 32)

    for item in data.items:
        lines.append(item.name)
        lines.append(f"{item.quantity:g} x {_fmt_money(item.price)} = {_fmt_money(item.sum)} ₽")

    lines.append("-" * 32)
    lines.append(f"ИТОГО: {_fmt_money(data.total)} ₽")
    if data.isDelivery:
        lines.append("ТИП: ДОСТАВКА")
        lines.append(f"ДОСТАВКА: {_fmt_money(data.deliveryCost)} ₽")
        if data.deliveryPhone:
            lines.append(f"ТЕЛЕФОН: {data.deliveryPhone}")
        if data.deliveryAddress:
            lines.append(f"АДРЕС: {data.deliveryAddress}")
    else:
        lines.append("ТИП: В ЗАЛЕ")

    payment_key = (data.paymentType or "").lower()
    if payment_key == "cash":
        payment_label = "НАЛИЧКА"
    elif payment_key == "transfer":
        payment_label = "ПЕРЕВОД"
    else:
        payment_label = "НЕ ОПЛАЧЕНО"
    lines.append(f"ОПЛАТА: {payment_label}")
    return lines
